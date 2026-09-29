package com.tradesignal.ai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.tradesignal.ai.capture.ScreenCapture
import com.tradesignal.ai.core.Signal
import com.tradesignal.ai.core.SignalResult
import com.tradesignal.ai.data.Settings
import com.tradesignal.ai.databinding.ActivityMainBinding
import com.tradesignal.ai.service.LiveAnalysisService
import com.tradesignal.ai.ui.HistoryActivity
import com.tradesignal.ai.ui.SettingsActivity
import com.tradesignal.ai.ui.SignalResultActivity
import com.tradesignal.ai.ui.TestModeActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private var pendingAction: PendingAction = PendingAction.NONE

    private enum class PendingAction { NONE, ANALYZE_ONCE, START_LIVE }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            when (pendingAction) {
                PendingAction.ANALYZE_ONCE -> launchService(result.resultCode, result.data!!, once = true)
                PendingAction.START_LIVE -> launchService(result.resultCode, result.data!!, once = false)
                PendingAction.NONE -> {}
            }
        } else {
            Toast.makeText(this, "Screen capture permission was not granted.", Toast.LENGTH_SHORT).show()
        }
        pendingAction = PendingAction.NONE
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canDrawOverlays()) requestScreenCapture(PendingAction.START_LIVE)
        else Toast.makeText(this, "Overlay permission is required for Live Analysis.", Toast.LENGTH_SHORT).show()
    }

    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnAnalyzeNow.setOnClickListener {
            Toast.makeText(this, getString(R.string.capture_permission_msg), Toast.LENGTH_SHORT).show()
            requestScreenCapture(PendingAction.ANALYZE_ONCE)
        }

        binding.btnStartLive.setOnClickListener {
            if (!canDrawOverlays()) {
                Toast.makeText(this, getString(R.string.overlay_permission_msg), Toast.LENGTH_LONG).show()
                val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            } else {
                requestScreenCapture(PendingAction.START_LIVE)
            }
        }

        binding.btnStopLive.setOnClickListener {
            startService(Intent(this, LiveAnalysisService::class.java).setAction(LiveAnalysisService.ACTION_STOP))
        }

        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnTestMode.setOnClickListener { startActivity(Intent(this, TestModeActivity::class.java)) }

        LiveAnalysisService.listeners.add(::onServiceUpdate)

        if (intent?.getBooleanExtra("auto_analyze", false) == true) {
            Toast.makeText(this, getString(R.string.capture_permission_msg), Toast.LENGTH_SHORT).show()
            requestScreenCapture(PendingAction.ANALYZE_ONCE)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    override fun onDestroy() {
        LiveAnalysisService.listeners.remove(::onServiceUpdate)
        super.onDestroy()
    }

    private fun requestScreenCapture(action: PendingAction) {
        pendingAction = action
        val capture = ScreenCapture(this)
        screenCaptureLauncher.launch(capture.requestIntent(this))
    }

    private fun launchService(resultCode: Int, data: Intent, once: Boolean) {
        val intent = Intent(this, LiveAnalysisService::class.java)
            .setAction(LiveAnalysisService.ACTION_START)
            .putExtra(LiveAnalysisService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(LiveAnalysisService.EXTRA_RESULT_DATA, data)
            .putExtra(LiveAnalysisService.EXTRA_ONCE, once)
        ActivityCompat.startForegroundService(this, intent)
        refreshUiState()
    }

    private fun onServiceUpdate(result: SignalResult?, analyzing: Boolean) {
        runOnUiThread {
            refreshUiState()
            if (result != null && !analyzing) {
                binding.tvSignal.text = labelFor(result.signal)
                binding.tvConfidence.text = if (result.signal == Signal.WAIT)
                    "Confidence: Insufficient" else "Confidence: ${result.confidence}%"
                binding.tvAnalysis.text = result.waitReason ?: "${result.structure} structure, ${result.momentum.lowercase()} momentum. ${result.levels}."

                if (!LiveAnalysisService.isRunning) {
                    val i = Intent(this, SignalResultActivity::class.java)
                    i.putExtra(SignalResultActivity.EXTRA_SIGNAL, result.signal.name)
                    i.putExtra(SignalResultActivity.EXTRA_CONF, result.confidence)
                    i.putExtra(SignalResultActivity.EXTRA_STRUCTURE, result.structure)
                    i.putExtra(SignalResultActivity.EXTRA_MOMENTUM, result.momentum)
                    i.putExtra(SignalResultActivity.EXTRA_RECENT, result.recent)
                    i.putExtra(SignalResultActivity.EXTRA_LEVELS, result.levels)
                    i.putExtra(SignalResultActivity.EXTRA_FACTORS, result.factors)
                    i.putExtra(SignalResultActivity.EXTRA_WAIT_REASON, result.waitReason)
                    i.putExtra(SignalResultActivity.EXTRA_HORIZON, settings.horizonSeconds)
                    startActivity(i)
                }
            }
        }
    }

    private fun labelFor(s: Signal) = when (s) {
        Signal.UP -> "UP ↑"
        Signal.DOWN -> "DOWN ↓"
        Signal.WAIT -> "WAIT ⏸"
    }

    private fun refreshUiState() {
        val running = LiveAnalysisService.isRunning
        binding.tvCaptureStatus.text = if (running) "ON" else "OFF"
        binding.tvLiveBadge.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnStartLive.visibility = if (running) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnStopLive.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(this)
}
