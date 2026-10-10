package com.powermaxv2.ytdownloader.core.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a page is cleaned before it goes into the PDF. */
enum class ScanFilter(val label: String) {
    /** The photo as it is (only cropped and straightened). */
    ORIGINAL("Original"),

    /** Colours kept, paper made white and ink darker. */
    SHARP("Net"),

    /** Shades of grey, contrast stretched. */
    GRAY("Gris"),

    /** Pure black ink on white paper (lightest PDF, best for text). */
    BW("N & B"),
    ;

    companion object {
        val DEFAULT = SHARP
    }
}

/**
 * The cleaning filters on raw ARGB pixels (pure, unit tested; the Android side only moves pixels in and out of
 * bitmaps). Every filter works in place on [pixels] of [width] × [height].
 */
object ScanFilters {
    fun apply(filter: ScanFilter, pixels: IntArray, width: Int, height: Int) {
        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.SHARP -> sharp(pixels)
            ScanFilter.GRAY -> gray(pixels)
            ScanFilter.BW -> blackAndWhite(pixels, width, height)
        }
    }

    fun luma(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    /** Dark and light ends of the picture: the 2nd and 98th percentile of the luminance. */
    fun levels(pixels: IntArray): Pair<Int, Int> {
        val hist = IntArray(256)
        for (c in pixels) hist[luma(c)]++
        val n = pixels.size
        var acc = 0
        var lo = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= n * 0.02) { lo = v; break }
        }
        acc = 0
        var hi = 255
        for (v in 255 downTo 0) {
            acc += hist[v]
            if (acc >= n * 0.02) { hi = v; break }
        }
        if (hi - lo < 16) return 0 to 255
        return lo to hi
    }

    private fun stretch(v: Int, lo: Int, hi: Int): Int = ((v - lo) * 255f / (hi - lo)).roundToInt().coerceIn(0, 255)

    private fun sharp(pixels: IntArray) {
        val (lo, hi) = levels(pixels)
        // The paper (light end) goes a bit past white so it comes out clean; the ink is pushed down with a curve.
        val top = max(lo + 16, hi - 12)
        val curve = IntArray(256) { v ->
            val s = stretch(v, lo, top) / 255f
            (255 * s * s * (3 - 2 * s) * 0.5f + 255 * s * 0.5f).roundToInt().coerceIn(0, 255)
        }
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = (c and 0xFF000000.toInt()) or
                (curve[(c shr 16) and 0xFF] shl 16) or
                (curve[(c shr 8) and 0xFF] shl 8) or
                curve[c and 0xFF]
        }
    }

    private fun gray(pixels: IntArray) {
        val (lo, hi) = levels(pixels)
        for (i in pixels.indices) {
            val c = pixels[i]
            val v = stretch(luma(c), lo, hi)
            pixels[i] = (c and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
        }
    }

    /**
     * Adaptive threshold (Bradley): a pixel is ink when it is clearly darker than the mean of its neighbourhood
     * (a window of 1/16 of the page), so shadows and uneven light do not turn whole areas black.
     */
    private fun blackAndWhite(pixels: IntArray, width: Int, height: Int) {
        val lum = IntArray(width * height) { luma(pixels[it]) }
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var row = 0L
            for (x in 0 until width) {
                row += lum[y * width + x]
                integral[(y + 1) * (width + 1) + x + 1] = integral[y * (width + 1) + x + 1] + row
            }
        }
        val half = max(4, max(width, height) / 32)
        for (y in 0 until height) {
            val y0 = max(0, y - half)
            val y1 = min(height - 1, y + half)
            for (x in 0 until width) {
                val x0 = max(0, x - half)
                val x1 = min(width - 1, x + half)
                val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * (width + 1) + x1 + 1] - integral[y0 * (width + 1) + x1 + 1] -
                    integral[(y1 + 1) * (width + 1) + x0] + integral[y0 * (width + 1) + x0]
                val i = y * width + x
                val ink = lum[i] * count * 100 < sum * (100 - BW_MARGIN)
                pixels[i] = (pixels[i] and 0xFF000000.toInt()) or if (ink) 0 else 0xFFFFFF
            }
        }
    }

    /** How much darker than its surroundings (in %) a pixel must be to count as ink. */
    private const val BW_MARGIN = 12
}
