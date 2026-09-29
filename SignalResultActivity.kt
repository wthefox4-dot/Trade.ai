package com.tradesignal.ai.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tradesignal.ai.MainActivity
import com.tradesignal.ai.core.Signal
import com.tradesignal.ai.databinding.ActivitySignalResultBinding
import java.text.SimpleDateFormat
import java.util.*

class SignalResultActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SIGNAL = "signal"
        const val EXTRA_CONF = "conf"
        const val EXTRA_STRUCTURE = "structure"
        const val EXTRA_MOMENTUM = "momentum"
        const val EXTRA_RECENT = "recent"
        const val EXTRA_LEVELS = "levels"
        const val EXTRA_FACTORS = "factors"
        const val EXTRA_WAIT_REASON = "wait_reason"
        const val EXTRA_HORIZON = "horizon"
    }

    private lateinit var binding: ActivitySignalResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignalResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val signal = Signal.valueOf(intent.getStringExtra(EXTRA_SIGNAL) ?: Signal.WAIT.name)
        val conf = intent.getIntExtra(EXTRA_CONF, 0)
        val horizon = intent.getIntExtra(EXTRA_HORIZON, 5)
        val waitReason = intent.getStringExtra(EXTRA_WAIT_REASON)

        when (signal) {
            Signal.UP -> { binding.tvBigSignal.text = "UP ↑"; binding.tvBigSignal.setTextColor(getColor(com.tradesignal.ai.R.color.up_green)) }
            Signal.DOWN -> { binding.tvBigSignal.text = "DOWN ↓"; binding.tvBigSignal.setTextColor(getColor(com.tradesignal.ai.R.color.down_red)) }
            Signal.WAIT -> { binding.tvBigSignal.text = "WAIT ⏸"; binding.tvBigSignal.setTextColor(getColor(com.tradesignal.ai.R.color.wait_gray)) }
        }
        binding.tvHorizon.text = "$horizon SECOND SIGNAL"
        binding.tvTimestamp.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (signal == Signal.WAIT) {
            binding.tvConfBig.text = "Confidence: Insufficient"
            binding.tvWaitReason.visibility = android.view.View.VISIBLE
            binding.tvWaitReason.text = "Reason: ${waitReason ?: "Setup not strong enough."}"
        } else {
            binding.tvConfBig.text = "Confidence: $conf%"
            binding.tvWaitReason.visibility = android.view.View.GONE
        }

        binding.tvStructure.text = intent.getStringExtra(EXTRA_STRUCTURE) ?: "-"
        binding.tvMomentum.text = intent.getStringExtra(EXTRA_MOMENTUM) ?: "-"
        binding.tvRecent.text = intent.getStringExtra(EXTRA_RECENT) ?: "-"
        binding.tvLevels.text = intent.getStringExtra(EXTRA_LEVELS) ?: "-"
        binding.tvFactors.text = (intent.getStringExtra(EXTRA_FACTORS) ?: "").ifBlank { "—" }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAnalyzeAgain.setOnClickListener {
            finish()
            startActivity(Intent(this, MainActivity::class.java).also {
                it.putExtra("auto_analyze", true)
                it.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }
}
