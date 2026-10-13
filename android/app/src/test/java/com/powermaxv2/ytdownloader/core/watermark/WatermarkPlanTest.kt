package com.powermaxv2.ytdownloader.core.watermark

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class WatermarkPlanTest {
    @Test fun labelAddsTheDateAndKeepsOneLine() {
        val style = WatermarkStyle(text = "  Copie   pour\nlocation ")
        assertEquals("Copie pour location – 13/10/2026", Watermark.label(style, "13/10/2026"))
        assertEquals("Copie pour location", Watermark.label(style.copy(withDate = false), "13/10/2026"))
        assertEquals("Copie pour location – 01/02/2026", Watermark.label(WatermarkStyle(text = ""), Watermark.dateText(1, 2, 2026)))
        assertEquals(Watermark.MAX_TEXT, Watermark.label(WatermarkStyle(text = "x".repeat(300), withDate = false), "").length)
    }

    @Test fun clampedKeepsEveryNumberInRange() {
        val wild = WatermarkStyle(opacity = 3f, angle = -400f, size = 0f, text = "y".repeat(200)).clamped()
        assertEquals(Watermark.MAX_OPACITY, wild.opacity)
        assertEquals(-90f, wild.angle)
        assertEquals(Watermark.MIN_SIZE, wild.size)
        assertEquals(Watermark.MAX_TEXT, wild.text.length)
    }

    @Test fun singleIsOneStampInTheMiddle() {
        val stamps = Watermark.stamps(1000f, 600f, 400f, 40f, WatermarkStyle(mode = WatermarkMode.SINGLE))
        assertEquals(listOf(Stamp(500f, 300f)), stamps)
    }

    @Test fun tiledCoversTheWholePageFromTheCentreOut() {
        val w = 1240f
        val h = 1754f
        val style = WatermarkStyle()
        val size = Watermark.fontSize(w, h, style)
        val textWidth = size * 14
        val stamps = Watermark.stamps(w, h, textWidth, size, style)
        assertTrue(stamps.size in 10..Watermark.MAX_STAMPS)
        // The first stamp is the centre one, then further and further away.
        assertEquals(Stamp(w / 2, h / 2), stamps.first())
        val distances = stamps.map { hypot(it.x - w / 2, it.y - h / 2) }
        assertEquals(distances.sorted(), distances)
        // Every corner and edge middle of the page has a stamp near it (no bare zone).
        val reach = hypot(textWidth, size * Watermark.ROW_GAP)
        listOf(0f to 0f, w to 0f, 0f to h, w to h, w / 2 to 0f, 0f to h / 2, w to h / 2, w / 2 to h).forEach { (x, y) ->
            assertTrue("point $x,$y", stamps.any { hypot(it.x - x, it.y - y) <= reach })
        }
        // No stamp is so far off the page that it cannot touch it.
        val half = hypot(textWidth, size) / 2
        stamps.forEach { assertTrue(it.x >= -half && it.x <= w + half && it.y >= -half && it.y <= h + half) }
    }

    @Test fun tiledRowsFollowTheSlant() {
        val style = WatermarkStyle(angle = -45f)
        val stamps = Watermark.stamps(1000f, 1000f, 200f, 20f, style)
        val centre = stamps.first()
        // The neighbour along the row (one step away, row 0) sits up and to the right at 45°.
        val step = 200f + 20f * Watermark.COL_GAP
        val along = stamps.minBy { abs(hypot(it.x - centre.x, it.y - centre.y) - step) + if (it.x > centre.x) 0f else 1e6f }
        assertEquals(-1f, (along.y - centre.y) / (along.x - centre.x), 0.01f)
    }

    @Test fun hugePagesStopAtTheCap() {
        val stamps = Watermark.stamps(100_000f, 100_000f, 30f, 3f, WatermarkStyle())
        assertEquals(Watermark.MAX_STAMPS, stamps.size)
        assertTrue(Watermark.stamps(0f, 500f, 10f, 10f, WatermarkStyle()).isEmpty())
    }

    @Test fun fontSizeFollowsTheShorterSide() {
        assertEquals(60f, Watermark.fontSize(2000f, 1000f, WatermarkStyle(size = 0.06f)), 0.001f)
        assertEquals(1000f * Watermark.MAX_SIZE, Watermark.fontSize(1000f, 3000f, WatermarkStyle(size = 1f)), 0.001f)
    }

    @Test fun outputNames() {
        assertEquals("Carte identité - filigrane.jpg", Watermark.outputName("Carte identité.HEIC", "jpg"))
        assertEquals("bulletin - filigrane.pdf", Watermark.outputName("bulletin.pdf", "pdf"))
        assertEquals("png", Watermark.photoExtension("capture.PNG", null))
        assertEquals("png", Watermark.photoExtension("x", "image/png"))
        assertEquals("jpg", Watermark.photoExtension("IMG_1.heic", "image/heic"))
    }

    @Test fun winAnsiKeepsFrenchAndReplacesTheRest() {
        assertEquals("Copie pour location – 13/10/2026 «é» €", Watermark.winAnsi("Copie pour location – 13/10/2026 «é» €"))
        assertEquals("Copie ? ??", Watermark.winAnsi("Copie ✅ 中文"))
    }

    @Test fun shownPageMapsBackToThePage() {
        fun apply(m: FloatArray, x: Float, y: Float) = floatArrayOf(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])
        // A 600 × 800 page (box at 10, 20) shown turned a quarter clockwise: 800 wide, 600 high.
        val m = Watermark.shownToPage(90, 10f, 20f, 600f, 800f)
        assertEquals(800f to 600f, Watermark.shownSize(90, 600f, 800f))
        // Shown top-left is the page's bottom-left (turning clockwise brings the left edge to the top).
        assertArrayEquals(floatArrayOf(10f, 20f), apply(m, 0f, 600f), 0.001f)
        // Shown bottom-left is the page's bottom-right.
        assertArrayEquals(floatArrayOf(610f, 20f), apply(m, 0f, 0f), 0.001f)
        // Not turned: a plain shift.
        assertArrayEquals(floatArrayOf(15f, 25f), apply(Watermark.shownToPage(0, 10f, 20f, 600f, 800f), 5f, 5f), 0.001f)
        // Half turn: the corners swap.
        assertArrayEquals(floatArrayOf(610f, 820f), apply(Watermark.shownToPage(180, 10f, 20f, 600f, 800f), 0f, 0f), 0.001f)
        // Three quarters, and -90 is the same.
        val q = Watermark.shownToPage(270, 0f, 0f, 600f, 800f)
        assertArrayEquals(q, Watermark.shownToPage(-90, 0f, 0f, 600f, 800f), 0f)
        assertArrayEquals(floatArrayOf(0f, 800f), apply(q, 0f, 0f), 0.001f)
        // Every corner of the shown page lands on a corner of the page box.
        listOf(0, 90, 180, 270).forEach { r ->
            val (sw, sh) = Watermark.shownSize(r, 600f, 800f)
            val mm = Watermark.shownToPage(r, 0f, 0f, 600f, 800f)
            listOf(0f to 0f, sw to 0f, 0f to sh, sw to sh).forEach { (x, y) ->
                val p = apply(mm, x, y)
                assertTrue("r=$r", (abs(p[0]) < 0.01f || abs(p[0] - 600f) < 0.01f) && (abs(p[1]) < 0.01f || abs(p[1] - 800f) < 0.01f))
            }
        }
    }
}
