package com.powermaxv2.ytdownloader.core.colors

// The palette of a photo, by median cut (pure Kotlin, tested on the JVM): the pixels are counted in a
// 32 × 32 × 32 grid, the busiest boxes of that grid are cut in two along their longest side until there are
// enough of them, look-alike boxes are merged, and the biggest ones become the palette.

/**
 * One colour of a photo's palette: [rgb], its [share] of the photo (0‥1), and where it shows best
 * ([x], [y] from 0 to 1, the pixel nearest to it), so the pipette can jump there.
 */
data class Swatch(val rgb: Int, val share: Float, val x: Float, val y: Float)

object PaletteExtractor {
    const val DEFAULT_COUNT = 6

    /** Look-alike colours closer than this ([ColorMath.distance]) are one colour of the palette. */
    private const val MERGE_DISTANCE = 2_600

    private class Box(val bins: IntArray, val population: Long) {
        var range = 0
        var channel = 0
    }

    /**
     * Up to [count] colours of [pixels] (ARGB, [width] × [height]; a small copy of the photo is plenty), the
     * biggest first. Transparent pixels are left out; nothing but transparent → an empty list.
     */
    fun extract(pixels: IntArray, width: Int, height: Int, count: Int = DEFAULT_COUNT): List<Swatch> {
        require(width * height <= pixels.size) { "pixels too short" }
        val hist = IntArray(BINS)
        val sumR = LongArray(BINS)
        val sumG = LongArray(BINS)
        val sumB = LongArray(BINS)
        var total = 0L
        for (i in 0 until width * height) {
            val p = pixels[i]
            if ((p ushr 24) < 128) continue
            val r = ColorMath.red(p)
            val g = ColorMath.green(p)
            val b = ColorMath.blue(p)
            val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            hist[bin]++
            sumR[bin] += r.toLong()
            sumG[bin] += g.toLong()
            sumB[bin] += b.toLong()
            total++
        }
        if (total == 0L || count <= 0) return emptyList()

        val used = (0 until BINS).filter { hist[it] > 0 }.toIntArray()
        val boxes = mutableListOf(box(used, hist))
        val target = count * 3
        while (boxes.size < target) {
            val widest = boxes
                .filter { it.bins.size > 1 }
                .maxByOrNull { it.population * (it.range + 1) }
                ?: break
            boxes.remove(widest)
            boxes += split(widest, hist)
        }

        // Average colour of each box, then look-alikes merged into the bigger one.
        data class Part(var rgb: Int, var population: Long)
        val parts = boxes.map { b ->
            var r = 0L
            var g = 0L
            var bl = 0L
            for (bin in b.bins) {
                r += sumR[bin]
                g += sumG[bin]
                bl += sumB[bin]
            }
            val n = b.population
            Part(ColorMath.rgb(((r + n / 2) / n).toInt(), ((g + n / 2) / n).toInt(), ((bl + n / 2) / n).toInt()), n)
        }.sortedByDescending { it.population }
        val kept = mutableListOf<Part>()
        for (part in parts) {
            val twin = kept.firstOrNull { ColorMath.distance(it.rgb, part.rgb) < MERGE_DISTANCE }
            if (twin != null) twin.population += part.population else kept += Part(part.rgb, part.population)
        }

        return kept
            .sortedByDescending { it.population }
            .take(count)
            .map { part ->
                val (x, y) = nearest(pixels, width, height, part.rgb)
                Swatch(part.rgb, part.population.toFloat() / total, x, y)
            }
    }

    private fun box(bins: IntArray, hist: IntArray): Box {
        var population = 0L
        val lo = intArrayOf(31, 31, 31)
        val hi = intArrayOf(0, 0, 0)
        for (bin in bins) {
            population += hist[bin]
            for (c in 0..2) {
                val v = channelOf(bin, c)
                if (v < lo[c]) lo[c] = v
                if (v > hi[c]) hi[c] = v
            }
        }
        return Box(bins, population).apply {
            // Green counts a little more: the eye tells greens apart better.
            val ranges = intArrayOf(hi[0] - lo[0], (hi[1] - lo[1]) * 6 / 5, hi[2] - lo[2])
            channel = ranges.indices.maxBy { ranges[it] }
            range = ranges[channel]
        }
    }

    /** Cuts [box] at the median pixel of its longest channel; both halves keep at least one bin. */
    private fun split(box: Box, hist: IntArray): List<Box> {
        val sorted = box.bins.sortedBy { channelOf(it, box.channel) }.toIntArray()
        val half = box.population / 2
        var acc = 0L
        var cut = 1
        for (i in sorted.indices) {
            acc += hist[sorted[i]]
            if (acc >= half) {
                cut = i + 1
                break
            }
        }
        cut = cut.coerceIn(1, sorted.size - 1)
        return listOf(box(sorted.copyOfRange(0, cut), hist), box(sorted.copyOfRange(cut, sorted.size), hist))
    }

    private fun channelOf(bin: Int, channel: Int): Int = when (channel) {
        0 -> (bin shr 10) and 31
        1 -> (bin shr 5) and 31
        else -> bin and 31
    }

    /** Centre of the pixel closest to [rgb], as fractions of the picture. */
    private fun nearest(pixels: IntArray, width: Int, height: Int, rgb: Int): Pair<Float, Float> {
        var best = Int.MAX_VALUE
        var at = 0
        for (i in 0 until width * height) {
            val p = pixels[i]
            if ((p ushr 24) < 128) continue
            val d = ColorMath.distance(p and 0xFFFFFF, rgb)
            if (d < best) {
                best = d
                at = i
                if (d == 0) break
            }
        }
        return ((at % width) + 0.5f) / width to ((at / width) + 0.5f) / height
    }

    private const val BINS = 32 * 32 * 32

    /** "#E4572E  Rouge tomate  38 %" lines, for "Partager la palette". */
    fun asText(swatches: List<Swatch>): String = swatches.joinToString("\n") { s ->
        "${ColorMath.hex(s.rgb)}  ${ColorMath.name(s.rgb)}  ${percent(s.share)} %"
    }

    /** Share as a whole percent, at least 1 for a colour that is there. */
    fun percent(share: Float): Int = (share * 100).let { if (it > 0f && it < 1f) 1 else Math.round(it) }
}
