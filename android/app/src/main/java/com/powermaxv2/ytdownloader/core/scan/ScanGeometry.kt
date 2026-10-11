package com.powermaxv2.ytdownloader.core.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point in 0..1 of the photo's width / height (independent of the photo's resolution). */
data class Pt(val x: Float, val y: Float) {
    fun clamped(): Pt = Pt(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * The four corners of the page inside a photo, clockwise from top-left, in 0..1 coordinates. Pure: the Scanner
 * crops and straightens the photo along it (perspective), the editor lets the user drag its corners.
 */
data class Quad(val topLeft: Pt, val topRight: Pt, val bottomRight: Pt, val bottomLeft: Pt) {
    val points: List<Pt> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** This quad with corner [index] (0 = top-left, clockwise) moved to [to] (kept inside the photo). */
    fun with(index: Int, to: Pt): Quad {
        val p = to.clamped()
        return when (index) {
            0 -> copy(topLeft = p)
            1 -> copy(topRight = p)
            2 -> copy(bottomRight = p)
            3 -> copy(bottomLeft = p)
            else -> this
        }
    }

    /** Whether the four corners make a real page (convex, not crossed, not flat). */
    val isValid: Boolean
        get() {
            val pts = points
            var sign = 0
            for (i in 0 until 4) {
                val a = pts[i]
                val b = pts[(i + 1) % 4]
                val c = pts[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                if (abs(cross) < 1e-6f) return false
                val s = if (cross > 0) 1 else -1
                if (sign == 0) sign = s else if (s != sign) return false
            }
            return area >= MIN_AREA
        }

    /** Area in 0..1 (shoelace). */
    val area: Float
        get() {
            val pts = points
            var sum = 0f
            for (i in 0 until 4) {
                val a = pts[i]
                val b = pts[(i + 1) % 4]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2f
        }

    /** Index of the corner closest to [p] when within [radius] (0..1 units, measured on a [w] × [h] photo), else -1. */
    fun nearestCorner(p: Pt, radius: Float, w: Float = 1f, h: Float = 1f): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        points.forEachIndexed { i, c ->
            val d = hypot((c.x - p.x) * w, (c.y - p.y) * h)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return if (bestDist <= radius * max(w, h)) best else -1
    }

    companion object {
        /** The whole photo. */
        val FULL = Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))

        /** Smallest page the editor accepts: 2 % of the photo. */
        const val MIN_AREA = 0.02f

        /**
         * The corners of four unordered points, clockwise from top-left: top-left has the smallest x + y,
         * bottom-right the largest, top-right the largest x − y, bottom-left the smallest.
         */
        fun ordered(points: List<Pt>): Quad {
            require(points.size == 4)
            val tl = points.minBy { it.x + it.y }
            val br = points.maxBy { it.x + it.y }
            val tr = points.maxBy { it.x - it.y }
            val bl = points.minBy { it.x - it.y }
            return Quad(tl, tr, br, bl)
        }
    }
}

/** Pure sizes of the straightened page. */
object ScanGeometry {
    /** Longest side of a straightened page (about 300 dpi on A4: plenty for text). */
    const val MAX_OUTPUT_SIDE = 3000

    /**
     * Pixel size of the page cut out of a [width] × [height] photo along [quad]: the longest of the opposite edges,
     * so nothing is squeezed, capped at [maxSide] on the longest side.
     */
    fun outputSize(quad: Quad, width: Int, height: Int, maxSide: Int = MAX_OUTPUT_SIDE): Pair<Int, Int> {
        fun len(a: Pt, b: Pt) = hypot((a.x - b.x) * width, (a.y - b.y) * height)
        var w = max(len(quad.topLeft, quad.topRight), len(quad.bottomLeft, quad.bottomRight))
        var h = max(len(quad.topLeft, quad.bottomLeft), len(quad.topRight, quad.bottomRight))
        val longest = max(w, h)
        if (longest > maxSide) {
            val k = maxSide / longest
            w *= k
            h *= k
        }
        return max(1, w.roundToInt()) to max(1, h.roundToInt())
    }

    /** Size after turning by [rotation] degrees (a multiple of 90). */
    fun rotated(w: Int, h: Int, rotation: Int): Pair<Int, Int> = if (normalize(rotation) % 180 == 0) w to h else h to w

    /** [rotation] brought into 0, 90, 180 or 270. */
    fun normalize(rotation: Int): Int = ((rotation % 360) + 360) % 360 / 90 * 90
}
