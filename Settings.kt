package com.tradesignal.ai.data

import android.content.Context
import android.content.SharedPreferences

/** All user-tunable settings. Simple, non-technical names on the Settings screen. */
class Settings(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("trade_signal_ai_settings", Context.MODE_PRIVATE)

    var threshold: Int
        get() = p.getInt("threshold", 55)
        set(v) = p.edit().putInt("threshold", v).apply()

    var minConfidence: Int
        get() = p.getInt("min_confidence", 60)
        set(v) = p.edit().putInt("min_confidence", v).apply()

    var horizonSeconds: Int
        get() = p.getInt("horizon_seconds", 5)
        set(v) = p.edit().putInt("horizon_seconds", v).apply()

    var fiveSecondMode: Boolean
        get() = p.getBoolean("five_second_mode", true)
        set(v) = p.edit().putBoolean("five_second_mode", v).apply()

    var darkMode: Boolean
        get() = p.getBoolean("dark_mode", true)
        set(v) = p.edit().putBoolean("dark_mode", v).apply()

    var vibrationEnabled: Boolean
        get() = p.getBoolean("vibration", true)
        set(v) = p.edit().putBoolean("vibration", v).apply()

    var soundEnabled: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = p.edit().putBoolean("sound", v).apply()

    /** Live-mode analysis interval in milliseconds (how often we attempt a fresh capture+analyze cycle). */
    var liveIntervalMs: Long
        get() = p.getLong("live_interval_ms", 1500L)
        set(v) = p.edit().putLong("live_interval_ms", v).apply()
}
