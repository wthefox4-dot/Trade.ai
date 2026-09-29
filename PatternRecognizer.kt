package com.tradesignal.ai.core

/**
 * Classic candlestick pattern recognition, built from candle geometry only.
 * This is the "trained trading expertise" baked into the app: no network call,
 * no model download — just standard technical-analysis rules evaluated on-device.
 */
object PatternRecognizer {

    data class PatternHit(val name: String, val score: Float) // score: +bullish .. -bearish, range -1..1

    /** c = last few candles (chronological), avgRange = recent average candle range. */
    fun detect(c: List<Candle>, avgRange: Float): List<PatternHit> {
        if (c.size < 3 || avgRange <= 0f) return emptyList()
        val hits = ArrayList<PatternHit>()
        val last = c[c.size - 1]
        val prev = c[c.size - 2]
        val prev2 = c[c.size - 3]

        // Doji: body is tiny relative to range -> indecision, pulls toward WAIT
        if (last.range > 0.3f * avgRange && last.body <= 0.12f * last.range) {
            hits.add(PatternHit("doji", 0f))
        }

        // Bullish engulfing: red candle then a green candle whose body fully covers it
        if (!prev.bullish && last.bullish && last.open <= prev.close && last.close >= prev.open &&
            last.body > prev.body * 1.05f) {
            hits.add(PatternHit("bullish engulfing", 0.9f))
        }
        // Bearish engulfing
        if (prev.bullish && !last.bullish && last.open >= prev.close && last.close <= prev.open &&
            last.body > prev.body * 1.05f) {
            hits.add(PatternHit("bearish engulfing", -0.9f))
        }

        // Hammer (bullish reversal after a decline): small body near top, long lower wick
        val declining = prev2.close > prev.close
        if (declining && last.lowerWick >= 2f * last.body && last.upperWick <= 0.3f * last.body.coerceAtLeast(0.1f) &&
            last.body > 0f && last.range >= 0.5f * avgRange) {
            hits.add(PatternHit("hammer", 0.7f))
        }
        // Shooting star (bearish reversal after a rise): small body near bottom, long upper wick
        val rising = prev2.close < prev.close
        if (rising && last.upperWick >= 2f * last.body && last.lowerWick <= 0.3f * last.body.coerceAtLeast(0.1f) &&
            last.body > 0f && last.range >= 0.5f * avgRange) {
            hits.add(PatternHit("shooting star", -0.7f))
        }

        // Morning star (3-candle bullish reversal): big red, small indecisive, big green closing into first body
        if (!prev2.bullish && prev2.body >= 0.8f * avgRange &&
            prev.body <= 0.35f * avgRange &&
            last.bullish && last.body >= 0.8f * avgRange &&
            last.close >= (prev2.open + prev2.close) / 2f) {
            hits.add(PatternHit("morning star", 0.85f))
        }
        // Evening star (3-candle bearish reversal)
        if (prev2.bullish && prev2.body >= 0.8f * avgRange &&
            prev.body <= 0.35f * avgRange &&
            !last.bullish && last.body >= 0.8f * avgRange &&
            last.close <= (prev2.open + prev2.close) / 2f) {
            hits.add(PatternHit("evening star", -0.85f))
        }

        // Three white soldiers / three black crows: three consecutive strong same-direction candles
        if (c.size >= 3) {
            val l3 = c.takeLast(3)
            if (l3.all { it.bullish } && l3.all { it.body >= 0.5f * avgRange } &&
                l3[1].close > l3[0].close && l3[2].close > l3[1].close) {
                hits.add(PatternHit("three white soldiers", 0.6f))
            }
            if (l3.all { !it.bullish } && l3.all { it.body >= 0.5f * avgRange } &&
                l3[1].close < l3[0].close && l3[2].close < l3[1].close) {
                hits.add(PatternHit("three black crows", -0.6f))
            }
        }

        return hits
    }
}
