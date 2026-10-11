package com.powermaxv2.ytdownloader.core.sticker

import kotlin.math.abs
import kotlin.math.max

/**
 * The magic wand: which pixels are background. A flood fill spreads from seed points (the edges of the picture, or
 * where the user touched) to neighbours whose colour stays close to the colour where it started. Pure Kotlin on
 * ARGB ints, so it is tested on the JVM.
 */
object Cutout {
    /** How far a colour may drift from the seed's, 0..255 per channel (the "Tolérance" slider maps to this). */
    const val DEFAULT_TOLERANCE = 42

    /**
     * Background mask of [pixels] ([width] × [height], ARGB): true = remove. Floods from every border pixel when
     * [fromEdges], and from each of [seeds] (x, y in pixels). Already transparent pixels count as background.
     */
    fun mask(
        pixels: IntArray,
        width: Int,
        height: Int,
        tolerance: Int = DEFAULT_TOLERANCE,
        fromEdges: Boolean = true,
        seeds: List<Pair<Int, Int>> = emptyList(),
    ): BooleanArray {
        require(pixels.size == width * height) { "Taille incohérente" }
        val removed = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        fun flood(start: Int) {
            if (removed[start]) return
            val reference = pixels[start]
            var top = 0
            stack[top++] = start
            removed[start] = true
            while (top > 0) {
                val i = stack[--top]
                val x = i % width
                val y = i / width
                if (x > 0) visit(i - 1, reference, pixels, removed, tolerance)?.let { stack[top++] = it }
                if (x < width - 1) visit(i + 1, reference, pixels, removed, tolerance)?.let { stack[top++] = it }
                if (y > 0) visit(i - width, reference, pixels, removed, tolerance)?.let { stack[top++] = it }
                if (y < height - 1) visit(i + width, reference, pixels, removed, tolerance)?.let { stack[top++] = it }
            }
        }
        if (fromEdges) {
            for (x in 0 until width) {
                flood(x)
                flood((height - 1) * width + x)
            }
            for (y in 0 until height) {
                flood(y * width)
                flood(y * width + width - 1)
            }
        }
        seeds.forEach { (x, y) -> if (x in 0 until width && y in 0 until height) flood(y * width + x) }
        return removed
    }

    private fun visit(i: Int, reference: Int, pixels: IntArray, removed: BooleanArray, tolerance: Int): Int? {
        if (removed[i] || !close(pixels[i], reference, tolerance)) return null
        removed[i] = true
        return i
    }

    /** Whether two ARGB colours are within [tolerance]; a transparent pixel is close to any other transparent one. */
    fun close(a: Int, b: Int, tolerance: Int): Boolean {
        val alphaA = a ushr 24
        val alphaB = b ushr 24
        if (alphaA < 16 && alphaB < 16) return true
        if (abs(alphaA - alphaB) > tolerance) return false
        val dr = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF))
        val dg = abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return max(dr, max(dg, db)) <= tolerance
    }

    /**
     * Applies [removed] to [pixels] in place: removed pixels become transparent, and kept pixels touching the
     * background get half their alpha, which softens the cut edge.
     */
    fun apply(pixels: IntArray, width: Int, height: Int, removed: BooleanArray) {
        val edge = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            if (removed[i]) continue
            val x = i % width
            val y = i / width
            edge[i] = (x > 0 && removed[i - 1]) || (x < width - 1 && removed[i + 1]) ||
                (y > 0 && removed[i - width]) || (y < height - 1 && removed[i + width])
        }
        for (i in pixels.indices) {
            when {
                removed[i] -> pixels[i] = 0
                edge[i] -> pixels[i] = ((pixels[i] ushr 24) / 2 shl 24) or (pixels[i] and 0xFFFFFF)
            }
        }
    }

    /** Share of the picture that would be removed (0..1): a mask eating almost everything is not applied. */
    fun share(removed: BooleanArray): Float = if (removed.isEmpty()) 0f else removed.count { it }.toFloat() / removed.size
}
