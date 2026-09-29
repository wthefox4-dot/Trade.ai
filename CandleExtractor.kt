package com.tradesignal.ai.core

/**
 * Finds candlesticks in a screenshot using colour + shape only (no fixed coordinates).
 * 1. classify pixels as bull (green family) / bear (red family)
 * 2. erode with a 5x3 element: removes thin indicator lines, wicks, text, keeps candle bodies
 * 3. connected components = candle bodies; wicks are re-attached from the original mask
 * 4. geometric / regularity filters reject buttons, bars, labels and outliers
 */
object CandleExtractor {
    private const val NONE: Byte = 0
    private const val BULL: Byte = 1
    private const val BEAR: Byte = 2

    private class Blob(
        val cx: Float, val bw: Int, val bodyTop: Int, val bodyBottom: Int,
        val wickTop: Int, val wickBottom: Int, val bull: Boolean
    ) {
        val cy: Float get() = (wickTop + wickBottom) / 2f
    }

    fun extract(f: Frame): Extraction {
        val w = f.w
        val h = f.h
        val px = f.px
        if (w < 200 || h < 200) return fail("Screenshot too small.")

        var sum = 0L
        var cnt = 0
        var i = 0
        while (i < px.size) {
            val p = px[i]
            sum += ((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255)
            cnt++
            i += 37
        }
        if (cnt == 0 || sum / (cnt * 3L) < 4) {
            return fail("Screen capture returned a black frame (the app may block screen capture).")
        }

        val cls = ByteArray(w * h)
        for (k in px.indices) cls[k] = classify(px[k])

        // horizontal erosion (runs >= 5)
        val hm = ByteArray(w * h)
        for (y in 0 until h) {
            var x = 0
            val row = y * w
            while (x < w) {
                val c = cls[row + x]
                if (c == NONE) { x++; continue }
                var e = x
                while (e + 1 < w && cls[row + e + 1] == c) e++
                if (e - x + 1 >= 5) for (t in x + 2..e - 2) hm[row + t] = c
                x = e + 1
            }
        }
        // vertical erosion (needs 3 rows)
        val em = ByteArray(w * h)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 0 until w) {
                val c = hm[row + x]
                if (c != NONE && hm[row - w + x] == c && hm[row + w + x] == c) em[row + x] = c
            }
        }

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val blobs = ArrayList<Blob>()
        val maxBodyW = 0.045f * w
        val maxBodyH = 0.10f * h
        for (start in em.indices) {
            val c = em[start]
            if (c == NONE || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            while (head < tail) {
                val cur = queue[head++]
                val cx = cur % w
                val cy = cur / w
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                if (cx > 0 && !visited[cur - 1] && em[cur - 1] == c) { visited[cur - 1] = true; queue[tail++] = cur - 1 }
                if (cx < w - 1 && !visited[cur + 1] && em[cur + 1] == c) { visited[cur + 1] = true; queue[tail++] = cur + 1 }
                if (cy > 0 && !visited[cur - w] && em[cur - w] == c) { visited[cur - w] = true; queue[tail++] = cur - w }
                if (cy < h - 1 && !visited[cur + w] && em[cur + w] == c) { visited[cur + w] = true; queue[tail++] = cur + w }
            }
            val bw = maxX - minX + 1 + 4
            val bodyTop = minY - 1
            val bodyBottom = maxY + 1
            val bh = bodyBottom - bodyTop + 1
            val xc = (minX + maxX) / 2
            if (bw < 5 || bw > maxBodyW || bh < 3 || bh > maxBodyH) continue
            if (xc < 0.075f * w) continue
            // re-attach wicks from the original mask
            var wt = bodyTop
            var gaps = 0
            var y = bodyTop - 1
            while (y >= 0 && gaps <= 3) {
                if (hit(cls, w, xc, y, c)) { wt = y; gaps = 0 } else gaps++
                y--
            }
            var wb = bodyBottom
            gaps = 0
            y = bodyBottom + 1
            while (y < h && gaps <= 3) {
                if (hit(cls, w, xc, y, c)) { wb = y; gaps = 0 } else gaps++
                y++
            }
            blobs.add(Blob(xc.toFloat(), bw, bodyTop, bodyBottom, wt, wb, c == BULL))
        }

        if (blobs.size < 8) return fail("Chart could not be analyzed reliably (too few candles found).")

        val medCy = median(blobs.map { it.cy })
        var kept = blobs.filter { Math.abs(it.cy - medCy) <= 0.30f * h }
        if (kept.size < 8) return fail("Chart could not be analyzed reliably.")
        val medW = median(kept.map { it.bw.toFloat() })
        kept = kept.filter { it.bw >= medW / 2.5f && it.bw <= medW * 2.5f }.sortedBy { it.cx }

        // one candle per horizontal slot
        val slots = ArrayList<Blob>()
        for (b in kept) {
            val last = slots.lastOrNull()
            if (last != null && b.cx - last.cx < 0.5f * medW) {
                if (Math.abs(b.cy - medCy) < Math.abs(last.cy - medCy)) slots[slots.size - 1] = b
            } else slots.add(b)
        }
        if (slots.size < 8) return fail("Chart could not be analyzed reliably.")

        val gaps = ArrayList<Float>()
        for (k in 1 until slots.size) gaps.add(slots[k].cx - slots[k - 1].cx)
        val medGap = median(gaps)
        val good = gaps.count { it >= 0.7f * medGap && it <= 1.4f * medGap }
        val quality = good.toFloat() / gaps.size
        if (quality < 0.5f) return fail("Candle spacing irregular; chart not reliable.")

        val candles = slots.map { b ->
            val hi = (h - b.wickTop).toFloat()
            val lo = (h - b.wickBottom).toFloat()
            val bodyHi = (h - b.bodyTop).toFloat()
            val bodyLo = (h - b.bodyBottom).toFloat()
            if (b.bull) Candle(b.cx, bodyLo, bodyHi, hi, lo, true)
            else Candle(b.cx, bodyHi, bodyLo, hi, lo, false)
        }
        return Extraction(candles, quality, null)
    }

    private fun hit(cls: ByteArray, w: Int, x: Int, y: Int, c: Byte): Boolean {
        val i = y * w + x
        return cls[i] == c || (x > 0 && cls[i - 1] == c) || (x < w - 1 && cls[i + 1] == c)
    }

    private fun fail(msg: String) = Extraction(emptyList(), 0f, msg)

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        return s[s.size / 2]
    }

    private fun classify(p: Int): Byte {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val mx = maxOf(r, maxOf(g, b))
        val mn = minOf(r, minOf(g, b))
        if (mx < 80) return NONE
        val d = mx - mn
        if (d * 100 < mx * 25) return NONE
        var hue: Float = when (mx) {
            r -> 60f * (((g - b).toFloat() / d) % 6f)
            g -> 60f * ((b - r).toFloat() / d + 2f)
            else -> 60f * ((r - g).toFloat() / d + 4f)
        }
        if (hue < 0f) hue += 360f
        return when {
            hue in 95f..170f -> BULL
            hue >= 330f || hue <= 15f -> BEAR
            else -> NONE
        }
    }
}
