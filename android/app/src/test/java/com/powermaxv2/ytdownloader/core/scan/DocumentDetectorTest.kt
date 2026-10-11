package com.powermaxv2.ytdownloader.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class DocumentDetectorTest {
    private val w = 200
    private val h = 260

    /** A dark table with a light sheet whose corners are [corners] (pixels), plus a few dark "text lines". */
    private fun picture(corners: List<Pair<Float, Float>>): IntArray {
        val px = IntArray(w * h) { 60 + (it * 7) % 13 }
        for (y in 0 until h) for (x in 0 until w) {
            if (inside(corners, x + 0.5f, y + 0.5f)) px[y * w + x] = 225 + (x * 3 + y) % 9
        }
        // Text lines inside the page (darker, but they do not cut the page in two).
        for (line in 0 until 6) {
            val y = 90 + line * 16
            for (x in 70..130) px[y * w + x] = 40
        }
        return px
    }

    private fun inside(poly: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var c = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    private fun near(expected: Pair<Float, Float>, actual: Pt) {
        assertEquals(expected.first / w, actual.x, 0.03f)
        assertEquals(expected.second / h, actual.y, 0.03f)
    }

    @Test fun findsATiltedPage() {
        val corners = listOf(40f to 30f, 170f to 45f, 160f to 235f, 25f to 220f)
        val quad = DocumentDetector.detect(picture(corners), w, h)
        assertNotNull(quad)
        quad!!
        near(corners[0], quad.topLeft)
        near(corners[1], quad.topRight)
        near(corners[2], quad.bottomRight)
        near(corners[3], quad.bottomLeft)
    }

    @Test fun flatPictureHasNoPage() {
        assertNull(DocumentDetector.detect(IntArray(w * h) { 128 }, w, h))
    }

    @Test fun pageFillingEverythingIsNotCut() {
        val all = listOf(0f to 0f, w.toFloat() to 0f, w.toFloat() to h.toFloat(), 0f to h.toFloat())
        assertNull(DocumentDetector.detect(picture(all), w, h))
    }

    @Test fun smallSpotIsNotAPage() {
        val spot = listOf(90f to 120f, 110f to 120f, 110f to 140f, 90f to 140f)
        assertNull(DocumentDetector.detect(picture(spot), w, h))
    }

    @Test fun badInputIsIgnored() {
        assertNull(DocumentDetector.detect(IntArray(10), 4, 4))
        assertNull(DocumentDetector.detect(IntArray(5), 100, 100))
    }

    @Test fun otsuSplitsTwoLevels() {
        val values = IntArray(1000) { if (it < 600) 50 else 200 }
        val t = DocumentDetector.otsu(values, values.size)
        assertNotNull(t)
        assertEquals(true, t!! in 50 until 200)
        assertEquals(true, abs(t - 125) < 80)
    }
}
