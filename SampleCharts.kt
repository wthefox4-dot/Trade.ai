package com.tradesignal.ai.core

/** Synthetic, deterministic chart images used by Test Mode (no trading app needed). */
object SampleCharts {
    class Case(val name: String, val expected: Signal, val frame: Frame)

    private const val W = 1080
    private const val H = 1400
    private const val BG = 0xFF141414.toInt()
    private const val BULL = 0xFF4AA37B.toInt()
    private const val BEAR = 0xFFE0566E.toInt()

    private class Rng(var s: Long) {
        fun next(): Float { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return (s % 1000L) / 1000f }
    }

    // each candle: close change, upper wick, lower wick (price units)
    private fun build(n: Int, seed: Long, f: (Int, Rng) -> FloatArray): List<FloatArray> {
        val r = Rng(seed)
        val out = ArrayList<FloatArray>()
        var prev = 100f
        for (i in 0 until n) {
            val t = f(i, r)
            val o = prev
            val c = o + t[0]
            out.add(floatArrayOf(o, Math.max(o, c) + t[1], Math.min(o, c) - t[2], c))
            prev = c
        }
        return out
    }

    fun all(): List<Case> = listOf(
        Case("Bullish momentum", Signal.UP, draw(build(27, 1) { i, r ->
            when {
                i == 25 -> floatArrayOf(1.1f, 0.1f, 0.1f)
                i == 26 -> floatArrayOf(0.1f, 0.2f, 0.2f)
                i % 6 == 3 -> floatArrayOf(-0.3f, 0.15f, 0.15f)
                else -> floatArrayOf(0.6f + r.next() * 0.3f, 0.1f + r.next() * 0.15f, 0.1f + r.next() * 0.15f)
            }
        })),
        Case("Bearish momentum", Signal.DOWN, draw(build(27, 2) { i, r ->
            when {
                i == 25 -> floatArrayOf(-1.1f, 0.1f, 0.1f)
                i == 26 -> floatArrayOf(-0.1f, 0.2f, 0.2f)
                i % 6 == 3 -> floatArrayOf(0.3f, 0.15f, 0.15f)
                else -> floatArrayOf(-0.6f - r.next() * 0.3f, 0.1f + r.next() * 0.15f, 0.1f + r.next() * 0.15f)
            }
        })),
        Case("Sideways market", Signal.WAIT, draw(build(27, 3) { i, r ->
            floatArrayOf((if (i % 2 == 0) 0.25f else -0.25f) + (r.next() - 0.5f) * 0.1f, 0.2f, 0.2f)
        })),
        Case("Support rejection (pullback bounce)", Signal.UP, draw(build(27, 4) { i, r ->
            when {
                i == 22 || i == 23 -> floatArrayOf(-0.35f, 0.15f, 0.1f)
                i == 24 -> floatArrayOf(0.3f, 0.05f, 1.2f)
                i == 25 -> floatArrayOf(0.7f, 0.1f, 0.3f)
                i == 26 -> floatArrayOf(0.1f, 0.2f, 0.2f)
                else -> floatArrayOf(0.5f + (r.next() - 0.5f) * 0.1f, 0.15f, 0.15f)
            }
        })),
        Case("Resistance rejection (pullback fade)", Signal.DOWN, draw(build(27, 5) { i, r ->
            when {
                i == 22 || i == 23 -> floatArrayOf(0.35f, 0.1f, 0.15f)
                i == 24 -> floatArrayOf(-0.3f, 1.2f, 0.05f)
                i == 25 -> floatArrayOf(-0.7f, 0.3f, 0.1f)
                i == 26 -> floatArrayOf(-0.1f, 0.2f, 0.2f)
                else -> floatArrayOf(-0.5f + (r.next() - 0.5f) * 0.1f, 0.15f, 0.15f)
            }
        })),
        Case("Unclear chart (few candles)", Signal.WAIT, draw(build(6, 6) { _, r ->
            floatArrayOf((r.next() - 0.5f) * 1.5f, 0.5f, 0.5f)
        }))
    )

    private fun rect(px: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, col: Int) {
        for (y in Math.max(0, y0)..Math.min(H - 1, y1)) for (x in Math.max(0, x0)..Math.min(W - 1, x1)) px[y * W + x] = col
    }

    private fun draw(cs: List<FloatArray>): Frame {
        val px = IntArray(W * H) { BG }
        val top = 450
        val bottom = 1050
        val lo = cs.minOf { it[2] }
        val hi = cs.maxOf { it[1] }
        val sc = (bottom - top) / (hi - lo)
        fun y(p: Float) = (bottom - (p - lo) * sc).toInt()
        // grid lines
        for (g in 0..4) rect(px, 0, top + g * 150, W - 1, top + g * 150, 0xFF2A2A2A.toInt())
        // candles
        for ((i, c) in cs.withIndex()) {
            val x = 130 + i * 30
            val col = if (c[3] >= c[0]) BULL else BEAR
            rect(px, x - 1, y(c[1]), x + 1, y(c[2]), col)
            val yt = y(Math.max(c[0], c[3]))
            val yb = y(Math.min(c[0], c[3]))
            rect(px, x - 8, yt, x + 8, Math.max(yb, yt + 2), col)
        }
        // distractors: indicator lines, buttons, side bar
        for (x in 60..900) {
            rect(px, x, 700 + (x / 6) % 60, x, 703 + (x / 6) % 60, 0xFF2AA050.toInt())
            rect(px, x, 800 - (x / 8) % 50, x, 802 - (x / 8) % 50, 0xFFE6A23C.toInt())
        }
        rect(px, 40, 1150, 500, 1250, 0xFFFF5A6A.toInt())
        rect(px, 540, 1150, 1000, 1250, 0xFF2ABB6A.toInt())
        rect(px, 38, 400, 46, 800, 0xFFFF5A6A.toInt())
        return Frame(W, H, px)
    }
}
