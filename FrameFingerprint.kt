package com.tradesignal.ai.core

/**
 * Cheap perceptual fingerprint of a frame, used to decide whether the chart has changed
 * enough to be worth a full re-analysis. Avoids reprocessing when nothing material moved.
 */
object FrameFingerprint {
    private const val GRID = 24

    fun of(f: Frame): IntArray {
        val out = IntArray(GRID * GRID)
        val cellW = f.w / GRID
        val cellH = f.h / GRID
        if (cellW == 0 || cellH == 0) return out
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                val x = gx * cellW + cellW / 2
                val y = gy * cellH + cellH / 2
                val p = f.px[(y.coerceIn(0, f.h - 1)) * f.w + x.coerceIn(0, f.w - 1)]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                out[gy * GRID + gx] = (r + g + b) / 3
            }
        }
        return out
    }

    /** 0 = identical, higher = more different. */
    fun distance(a: IntArray, b: IntArray): Int {
        var d = 0
        for (i in a.indices) d += Math.abs(a[i] - b[i])
        return d
    }
}
