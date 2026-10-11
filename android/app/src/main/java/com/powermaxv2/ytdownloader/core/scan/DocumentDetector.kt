package com.powermaxv2.ytdownloader.core.scan

/**
 * Finds the sheet of paper in a small grey picture of a photo (pure, unit tested). A page is usually the largest
 * light area: the picture is split light / dark (Otsu threshold), the largest light blob is kept, and its four
 * extreme points (smallest / largest x + y and x − y) become the corners. Good enough for a page on a table;
 * the user fixes the rest by dragging the corners.
 */
object DocumentDetector {
    /** Side of the picture the detector works on (callers scale the photo down to about this). */
    const val WORK_SIDE = 256

    /**
     * [gray] holds [width] × [height] luminance values 0..255, row by row. Returns the page's corners in 0..1, or
     * null when nothing page-like stands out (then the editor starts from the whole photo).
     */
    fun detect(gray: IntArray, width: Int, height: Int): Quad? {
        if (width < 8 || height < 8 || gray.size < width * height) return null
        val threshold = otsu(gray, width * height) ?: return null
        val light = BooleanArray(width * height) { gray[it] > threshold }
        val blob = largestBlob(light, width, height) ?: return null
        val total = width * height
        // Too small to be the page, or the whole picture (nothing to cut).
        if (blob.size < total * 0.12f || blob.size > total * 0.97f) return null

        var tl = 0
        var br = 0
        var tr = 0
        var bl = 0
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE
        var maxDiff = Int.MIN_VALUE
        var minDiff = Int.MAX_VALUE
        for (i in 0 until blob.size) {
            val p = blob[i]
            val x = p % width
            val y = p / width
            val s = x + y
            val d = x - y
            if (s < minSum) { minSum = s; tl = p }
            if (s > maxSum) { maxSum = s; br = p }
            if (d > maxDiff) { maxDiff = d; tr = p }
            if (d < minDiff) { minDiff = d; bl = p }
        }
        fun pt(p: Int) = Pt((p % width + 0.5f) / width, (p / width + 0.5f) / height)
        val quad = Quad(pt(tl), pt(tr), pt(br), pt(bl))
        // A blob whose extreme points do not make a page (a round shape, a stain) is not a document.
        if (!quad.isValid || quad.area < 0.1f || blob.size < quad.area * total * 0.75f) return null
        return quad
    }

    /** Otsu's threshold of the first [count] values, or null for a flat picture. */
    fun otsu(gray: IntArray, count: Int): Int? {
        val hist = IntArray(256)
        for (i in 0 until count) hist[gray[i].coerceIn(0, 255)]++
        var sumAll = 0.0
        for (v in 0..255) sumAll += v.toDouble() * hist[v]
        var sumB = 0.0
        var weightB = 0
        var best = -1.0
        var threshold = -1
        for (t in 0..254) {
            weightB += hist[t]
            if (weightB == 0) continue
            val weightF = count - weightB
            if (weightF == 0) break
            sumB += t.toDouble() * hist[t]
            val meanB = sumB / weightB
            val meanF = (sumAll - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        if (threshold < 0) return null
        // Too little contrast between the two sides: no page edge to find.
        val darkMean = if (weightOf(hist, 0, threshold) == 0) return null else meanOf(hist, 0, threshold)
        val lightMean = if (weightOf(hist, threshold + 1, 255) == 0) return null else meanOf(hist, threshold + 1, 255)
        return if (lightMean - darkMean < 24) null else threshold
    }

    private fun weightOf(hist: IntArray, from: Int, to: Int): Int = (from..to).sumOf { hist[it] }

    private fun meanOf(hist: IntArray, from: Int, to: Int): Double {
        var sum = 0.0
        var n = 0
        for (v in from..to) {
            sum += v.toDouble() * hist[v]
            n += hist[v]
        }
        return sum / n
    }

    /** Pixel indexes of the largest 4-connected true area of [mask], or null when there is none. */
    private fun largestBlob(mask: BooleanArray, width: Int, height: Int): IntArray? {
        val label = IntArray(mask.size)
        val stack = IntArray(mask.size)
        var bestLabel = 0
        var bestSize = 0
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            next++
            var size = 0
            var top = 0
            stack[top++] = start
            label[start] = next
            while (top > 0) {
                val p = stack[--top]
                size++
                val x = p % width
                val y = p / width
                if (x > 0) push(mask, label, p - 1, next).let { if (it) stack[top++] = p - 1 }
                if (x < width - 1) push(mask, label, p + 1, next).let { if (it) stack[top++] = p + 1 }
                if (y > 0) push(mask, label, p - width, next).let { if (it) stack[top++] = p - width }
                if (y < height - 1) push(mask, label, p + width, next).let { if (it) stack[top++] = p + width }
            }
            if (size > bestSize) {
                bestSize = size
                bestLabel = next
            }
        }
        if (bestSize == 0) return null
        val out = IntArray(bestSize)
        var n = 0
        for (i in label.indices) if (label[i] == bestLabel) out[n++] = i
        return out
    }

    private fun push(mask: BooleanArray, label: IntArray, p: Int, id: Int): Boolean {
        if (!mask[p] || label[p] != 0) return false
        label[p] = id
        return true
    }
}
