package com.tradesignal.ai.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tradesignal.ai.data.AppDatabase
import com.tradesignal.ai.data.Settings
import com.tradesignal.ai.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.btnBack.setOnClickListener { finish() }

        // threshold: 40..80 mapped from seek 0..40
        binding.seekThreshold.progress = (settings.threshold - 40).coerceIn(0, 40)
        binding.tvThresholdVal.text = settings.threshold.toString()
        binding.seekThreshold.setOnSeekBarChangeListener(simple { p ->
            val v = 40 + p; settings.threshold = v; binding.tvThresholdVal.text = v.toString()
        })

        // min confidence 40..80
        binding.seekConfidence.progress = (settings.minConfidence - 40).coerceIn(0, 40)
        binding.tvConfidenceVal.text = "${settings.minConfidence}%"
        binding.seekConfidence.setOnSeekBarChangeListener(simple { p ->
            val v = 40 + p; settings.minConfidence = v; binding.tvConfidenceVal.text = "$v%"
        })

        // horizon 3..28 seconds
        binding.seekHorizon.progress = (settings.horizonSeconds - 3).coerceIn(0, 25)
        binding.tvHorizonVal.text = "${settings.horizonSeconds} sec"
        binding.seekHorizon.setOnSeekBarChangeListener(simple { p ->
            val v = 3 + p; settings.horizonSeconds = v; binding.tvHorizonVal.text = "$v sec"
        })

        // live interval 0.5s..5.0s in 0.1s steps (progress 0..45)
        binding.seekInterval.progress = (((settings.liveIntervalMs - 500) / 100).toInt()).coerceIn(0, 45)
        binding.tvIntervalVal.text = "${settings.liveIntervalMs / 1000.0} sec"
        binding.seekInterval.setOnSeekBarChangeListener(simple { p ->
            val v = 500L + p * 100L; settings.liveIntervalMs = v; binding.tvIntervalVal.text = "${v / 1000.0} sec"
        })

        binding.switchFiveSec.isChecked = settings.fiveSecondMode
        binding.switchFiveSec.setOnCheckedChangeListener { _, checked ->
            settings.fiveSecondMode = checked
            settings.horizonSeconds = if (checked) 5 else settings.horizonSeconds
        }

        binding.switchVibration.isChecked = settings.vibrationEnabled
        binding.switchVibration.setOnCheckedChangeListener { _, checked -> settings.vibrationEnabled = checked }

        binding.switchSound.isChecked = settings.soundEnabled
        binding.switchSound.setOnCheckedChangeListener { _, checked -> settings.soundEnabled = checked }

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch { AppDatabase.get(this@SettingsActivity).signalDao().clearAll() }
        }
    }

    private fun simple(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
