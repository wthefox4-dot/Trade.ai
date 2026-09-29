package com.tradesignal.ai.core

/**
 * Deterministic scoring engine. Works only on features derived from extracted candles.
 * Indicators that need data we cannot read (volume, real prices) are never faked.
 */
class SignalEngine(private val threshold: Int = 55, private val minConfidence: Int = 60) {

    private class F(val name: String, val score: Float, val weight: Int)

    fun analyze(ex: Extraction): SignalResult {
        if (ex.error != null) return wait(ex.error, "-", "-", "-", "-")
        // last visible candle is still forming -> ignore it
        val c = ex.candles.dropLast(1)
        if (c.size < 8) return wait("Insufficient visible candles.", "-", "-", "-", "-")
        val n = c.size
        val last = c[n - 1]
        val prev = c[n - 2]

        val avgRange = Math.max(c.takeLast(20).map { it.range }.average().toFloat(), 1f)
        val closes = c.map { it.close }

        // --- features (each in -1..1) ---
        val last5 = c.takeLast(5)
        var dir = 0f
        for (i in last5.indices) dir += (i + 1) * (if (last5[i].bullish) 1f else -1f)
        dir /= 15f

        var streak = 0
        for (k in c.indices.reversed()) { if (c[k].bullish == last.bullish) streak++ else break }
        val streakS = (if (last.bullish) 1f else -1f) * Math.min(streak, 4) / 4f

        val mom = ((closes[n - 1] - closes[n - 6]) / avgRange / 3f).coerceIn(-1f, 1f)
        val trend = (slope(closes.takeLast(10)) / avgRange * 4f).coerceIn(-1f, 1f)
        val sma5 = closes.takeLast(5).average().toFloat()
        val sma10 = closes.takeLast(10).average().toFloat()
        val ma = ((sma5 - sma10) / avgRange * 2f).coerceIn(-1f, 1f)

        var rsiS = 0f
        var rsiTxt = ""
        if (n >= 15) {
            val rsi = rsi(closes.takeLast(15))
            rsiS = when {
                rsi > 75f -> -0.3f
                rsi > 55f -> 0.5f
                rsi < 25f -> 0.3f
                rsi < 45f -> -0.5f
                else -> 0f
            }
            rsiTxt = "RSI~${rsi.toInt()}"
        }

        val prior10 = c.dropLast(1).takeLast(10)
        val breakout = when {
            last.close > prior10.maxOf { it.high } -> 1f
            last.close < prior10.minOf { it.low } -> -1f
            else -> 0f
        }

        val look = c.takeLast(8)
        val recentLow = look.minOf { it.low }
        val recentHigh = look.maxOf { it.high }
        fun supportRej(k: Candle) = k.range >= 0.6f * avgRange && k.lowerWick >= 0.45f * k.range &&
            k.lowerWick >= 1.5f * k.body && k.low <= recentLow + 0.3f * avgRange
        fun resistRej(k: Candle) = k.range >= 0.6f * avgRange && k.upperWick >= 0.45f * k.range &&
            k.upperWick >= 1.5f * k.body && k.high >= recentHigh - 0.3f * avgRange
        var rej = 0f
        var supportSeen = false
        var resistSeen = false
        if (supportRej(last)) { rej += 1f; supportSeen = true }
        else if (supportRej(prev) && last.bullish) { rej += 0.7f; supportSeen = true }
        if (resistRej(last)) { rej -= 1f; resistSeen = true }
        else if (resistRej(prev) && !last.bullish) { rej -= 0.7f; resistSeen = true }

        val avgBody = Math.max(c.dropLast(1).takeLast(5).map { it.body }.average().toFloat(), 0.5f)
        val ratio = last.body / avgBody
        val accel = (if (last.bullish) 1f else -1f) * (if (ratio >= 1.8f) 1f else if (ratio >= 1.3f) 0.6f else 0f)

        val patternHits = PatternRecognizer.detect(c.takeLast(5), avgRange)
        val patternScore = (patternHits.sumOf { it.score.toDouble() }).toFloat().coerceIn(-1f, 1f)
        val patternNames = patternHits.filter { it.score != 0f }.joinToString(", ") { it.name }
        val hasDoji = patternHits.any { it.name == "doji" }

        val fs = listOf(
            F("recent candle direction", dir, 14), F("candle streak", streakS, 10),
            F("momentum", mom, 14), F("short-term trend", trend, 14),
            F("MA5 vs MA10", ma, 10), F("RSI-style", rsiS, 6),
            F("breakout/breakdown", breakout, 12), F("rejection", rej, 14),
            F("acceleration", accel, 6), F("candlestick pattern", patternScore, 14)
        )
        var up = 0f
        var down = 0f
        for (f in fs) { if (f.score > 0) up += f.score * f.weight else down += -f.score * f.weight }
        val upI = Math.round(up)
        val downI = Math.round(down)

        val structure = if (trend > 0.25f) "Bullish" else if (trend < -0.25f) "Bearish" else "Sideways"
        val momentum = if (mom > 0.15f) "Positive" else if (mom < -0.15f) "Negative" else "Flat"
        val recent3 = c.takeLast(3)
        val bulls = recent3.count { it.bullish }
        val recent = if (bulls == 3) "Bullish (3/3)" else if (bulls == 0) "Bearish (3/3)" else "Mixed ($bulls/3 bullish)"
        val levels = when {
            supportSeen -> "Support rejection detected"
            resistSeen -> "Resistance rejection detected"
            breakout > 0 -> "Breakout above recent high"
            breakout < 0 -> "Breakdown below recent low"
            else -> "No key level event"
        }
        val factors = fs.filter { Math.abs(it.score) >= 0.4f }
            .joinToString(", ") { it.name + (if (it.score > 0) " +" else " -") } +
            (if (rsiTxt.isNotEmpty()) " ($rsiTxt)" else "") +
            (if (patternNames.isNotEmpty()) " [patterns: $patternNames]" else "")

        fun w(reason: String) = wait(reason, structure, momentum, recent, levels, upI, downI, factors)

        // --- WAIT gates ---
        val bodyRatio = c.takeLast(10).map { if (it.range > 0f) it.body / it.range else 0f }.average().toFloat()
        if (bodyRatio < 0.22f) return w("Chart too noisy (very small candle bodies).")
        if (hasDoji && Math.abs(trend) < 0.35f) return w("Doji / indecision candle with no clear trend.")
        val spread = closes.takeLast(15).max() - closes.takeLast(15).min()
        if (spread < 2.5f * avgRange && Math.abs(trend) < 0.25f && Math.abs(mom) < 0.3f) return w("Sideways market.")
        val priorAvg = Math.max(c.dropLast(1).takeLast(15).map { it.range }.average().toFloat(), 1f)
        if (last.range > 3.5f * priorAvg) return w("Abnormal sudden movement.")
        if (ex.gapQuality < 0.6f) return w("Chart clarity insufficient (irregular candle spacing).")

        val winner = Math.max(upI, downI)
        val loser = Math.min(upI, downI)
        if (winner - loser < 15) return w("Conflicting short-term signals (UP and DOWN scores too close).")
        if (winner < threshold) return w("No sufficiently strong setup (score $winner < $threshold).")
        val isUp = upI > downI
        if (isUp && resistSeen) return w("Support/resistance conflict (resistance rejection present).")
        if (!isUp && supportSeen) return w("Support/resistance conflict (support rejection present).")

        var conf = 30f + (winner - loser) + (winner - 50) * 0.4f
        conf *= (0.8f + 0.2f * ex.gapQuality)
        val confI = conf.toInt().coerceIn(0, 85)
        if (confI < minConfidence) return w("Confidence insufficient ($confI% < $minConfidence%).")

        return SignalResult(if (isUp) Signal.UP else Signal.DOWN, confI, upI, downI, structure, momentum, recent, levels, factors, null)
    }

    private fun wait(reason: String, s: String, m: String, r: String, l: String, u: Int = 0, d: Int = 0, f: String = "") =
        SignalResult(Signal.WAIT, 0, u, d, s, m, r, l, f, reason)

    private fun slope(v: List<Float>): Float {
        val n = v.size
        val mx = (n - 1) / 2f
        val my = v.average().toFloat()
        var num = 0f
        var den = 0f
        for (i in 0 until n) { num += (i - mx) * (v[i] - my); den += (i - mx) * (i - mx) }
        return if (den == 0f) 0f else num / den
    }

    private fun rsi(v: List<Float>): Float {
        var g = 0f
        var l = 0f
        for (i in 1 until v.size) { val d = v[i] - v[i - 1]; if (d > 0) g += d else l -= d }
        if (g == 0f && l == 0f) return 50f
        if (l == 0f) return 100f
        return 100f - 100f / (1f + g / l)
    }
}
