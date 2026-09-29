package com.tradesignal.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tradesignal.ai.MainActivity
import com.tradesignal.ai.R
import com.tradesignal.ai.capture.ScreenCapture
import com.tradesignal.ai.core.*
import com.tradesignal.ai.data.AppDatabase
import com.tradesignal.ai.data.Settings
import com.tradesignal.ai.data.SignalRecord
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service that owns the MediaProjection session and drives Live Analysis Mode:
 * capture -> (skip if unchanged) -> analyze -> update floating overlay -> repeat.
 *
 * Never simulates input. Read-only analysis of the visible screen only.
 */
class LiveAnalysisService : Service() {

    companion object {
        const val CHANNEL_ID = "trade_signal_live"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.tradesignal.ai.action.START"
        const val ACTION_STOP = "com.tradesignal.ai.action.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_ONCE = "once"

        var isRunning = false
            private set
        var lastResult: SignalResult? = null
            private set

        val listeners = mutableSetOf<(SignalResult?, Boolean) -> Unit>() // (result, analyzing)
    }

    private lateinit var capture: ScreenCapture
    private lateinit var overlay: OverlayWindow
    private lateinit var alerts: AlertManager
    private lateinit var settings: Settings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var workHandler: Handler? = null

    private var lastFingerprint: IntArray? = null
    private var currentIntervalMs = 1500L
    private var lastSignalAt = 0L
    private var running = false
    private var onceMode = false
    private var onceAttempts = 0
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        capture = ScreenCapture(this)
        overlay = OverlayWindow(this)
        alerts = AlertManager(this)
        settings = Settings(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                onceMode = intent.getBooleanExtra(EXTRA_ONCE, false)
                startForegroundNow()
                if (data != null && code == Activity.RESULT_OK) startLive(code, data)
            }
            ACTION_STOP -> stopLive()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val notif = buildNotification("Live Analysis running", "Analyzing the visible chart on this device only.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startLive(resultCode: Int, data: Intent) {
        if (running) return
        val ok = capture.start(resultCode, data)
        if (!ok) { stopSelf(); return }
        running = true
        isRunning = true
        currentIntervalMs = if (onceMode) 200L else settings.liveIntervalMs
        if (!onceMode) {
            overlay.show()
            overlay.setAnalyzing()
        }
        notifyListeners(null, true)

        val t = HandlerThread("LiveAnalysisWorker").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        workHandler = h
        h.post(loopRunnable)
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val start = System.currentTimeMillis()
            try {
                tick()
            } catch (_: Exception) {
                // never crash the loop; just keep going
            }
            val elapsed = System.currentTimeMillis() - start
            // adaptive throttling: if analysis is slow, back off; otherwise settle to configured interval
            currentIntervalMs = if (elapsed > currentIntervalMs) {
                (currentIntervalMs * 1.5).toLong().coerceAtMost(6000L)
            } else {
                val target = settings.liveIntervalMs
                if (currentIntervalMs > target) (currentIntervalMs * 0.9).toLong().coerceAtLeast(target) else target
            }
            workHandler?.postDelayed(this, currentIntervalMs.coerceAtLeast(300L))
        }
    }

    private fun tick() {
        val frame = capture.captureFrame()
        if (frame == null) {
            if (onceMode) {
                onceAttempts++
                if (onceAttempts > 20) { // ~4s of retries; give up rather than hang
                    val result = SignalResult(Signal.WAIT, 0, 0, 0, "-", "-", "-", "-", "-", "No frame received from screen capture.")
                    lastResult = result
                    notifyListeners(result, false)
                    saveRecord(result)
                    mainHandler.post { stopLive() }
                }
            }
            return
        }
        val fp = FrameFingerprint.of(frame)
        val prev = lastFingerprint
        lastFingerprint = fp
        val horizon = settings.horizonSeconds

        // expire an old signal purely on time, even without a new capture cycle
        val last = lastResult
        if (!onceMode && last != null && last.signal != Signal.WAIT && System.currentTimeMillis() - lastSignalAt > horizon * 1000L) {
            mainHandler.post { overlay.update(last.signal, last.confidence, horizon, timeFmt.format(Date(lastSignalAt)), true) }
        }

        if (!onceMode && prev != null && FrameFingerprint.distance(prev, fp) < 900) {
            return // nothing material changed; skip the expensive analysis
        }

        if (!onceMode) mainHandler.post { overlay.setAnalyzing() }
        notifyListeners(null, true)

        val analyzer: ChartAnalyzer = LocalChartAnalyzer(settings.threshold, settings.minConfidence)
        val result = analyzer.analyze(frame)

        val changed = last == null || last.signal != result.signal
        lastResult = result
        notifyListeners(result, false)

        if (result.signal != Signal.WAIT) lastSignalAt = System.currentTimeMillis()
        val ts = timeFmt.format(Date())
        if (!onceMode) mainHandler.post { overlay.update(result.signal, result.confidence, horizon, ts, false) }

        if (changed || onceMode) {
            alerts.onSignal(result.signal)
            saveRecord(result)
        }

        if (onceMode) {
            mainHandler.post { stopLive() }
        }
    }

    private fun saveRecord(r: SignalResult) {
        scope.launch {
            AppDatabase.get(applicationContext).signalDao().insert(
                SignalRecord(
                    timestamp = r.timestamp, signal = r.signal.name, confidence = r.confidence,
                    structure = r.structure, momentum = r.momentum, recent = r.recent,
                    levels = r.levels, factors = r.factors, waitReason = r.waitReason
                )
            )
        }
    }

    private fun stopLive() {
        running = false
        isRunning = false
        workHandler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        capture.stop()
        overlay.hide()
        lastFingerprint = null
        notifyListeners(null, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyListeners(result: SignalResult?, analyzing: Boolean) {
        mainHandler.post { listeners.forEach { it(result, analyzing) } }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, LiveAnalysisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_signal)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop Live Analysis", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Live Analysis", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Shows that Trade Signal AI is actively analyzing the visible chart."
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        stopLive()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
