package com.tradesignal.ai.core

enum class Signal { UP, DOWN, WAIT }

/** Raw ARGB pixels of a screenshot. Pure data so the vision code can be unit-tested off-device. */
class Frame(val w: Int, val h: Int, val px: IntArray)

/** Prices are in chart-pixel units (higher value = higher price). No real prices are invented. */
data class Candle(
    val x: Float,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val bullish: Boolean
) {
    val body: Float get() = Math.abs(close - open)
    val range: Float get() = high - low
    val upperWick: Float get() = high - Math.max(open, close)
    val lowerWick: Float get() = Math.min(open, close) - low
}

data class Extraction(val candles: List<Candle>, val gapQuality: Float, val error: String?)

data class SignalResult(
    val signal: Signal,
    val confidence: Int,
    val upScore: Int,
    val downScore: Int,
    val structure: String,
    val momentum: String,
    val recent: String,
    val levels: String,
    val factors: String,
    val waitReason: String?,
    val timestamp: Long = System.currentTimeMillis()
)
