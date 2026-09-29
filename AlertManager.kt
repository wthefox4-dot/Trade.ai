package com.tradesignal.ai.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tradesignal.ai.core.Signal
import com.tradesignal.ai.data.Settings

class AlertManager(private val context: Context) {
    private val settings = Settings(context)

    fun onSignal(signal: Signal) {
        if (signal == Signal.WAIT) return // no aggressive alert on WAIT
        if (settings.vibrationEnabled) vibrate(signal)
        if (settings.soundEnabled) playTone()
    }

    private fun vibrate(signal: Signal) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = if (signal == Signal.UP) longArrayOf(0, 60) else longArrayOf(0, 60, 60, 60)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
        }
    }

    private fun playTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            mp.setDataSource(context, uri)
            mp.setOnCompletionListener { it.release() }
            mp.prepare()
            mp.start()
        } catch (_: Exception) { /* best-effort only */ }
    }
}
