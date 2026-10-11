package com.powermaxv2.ytdownloader.core.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFiltersTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** A greyish photographed page: paper around 190, ink around 70, with a shadow on the left half. */
    private fun page(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val shade = if (x < w / 2) -45 else 0
        val ink = y % 10 == 5 && x % 20 in 3..15
        val v = (if (ink) 70 else 190) + shade
        rgb(v, v - 4, v - 10)
    }

    @Test fun originalChangesNothing() {
        val px = page(40, 40)
        val copy = px.copyOf()
        ScanFilters.apply(ScanFilter.ORIGINAL, px, 40, 40)
        assertArrayEquals(copy, px)
    }

    @Test fun grayHasEqualChannelsAndFullRange() {
        val px = page(40, 40)
        ScanFilters.apply(ScanFilter.GRAY, px, 40, 40)
        px.forEach { assertTrue(r(it) == g(it) && g(it) == b(it)) }
        assertEquals(0, px.minOf { r(it) })
        assertEquals(255, px.maxOf { r(it) })
    }

    @Test fun sharpMakesPaperWhiteAndKeepsInkDark() {
        val px = page(40, 40)
        ScanFilters.apply(ScanFilter.SHARP, px, 40, 40)
        // A plain paper pixel on the light half.
        val paper = px[2 * 40 + 30]
        assertTrue(r(paper) > 245)
        val ink = px[5 * 40 + 25]
        assertTrue(r(ink) < 90)
    }

    @Test fun blackAndWhiteIgnoresTheShadow() {
        val w = 80
        val h = 40
        val px = page(w, h)
        ScanFilters.apply(ScanFilter.BW, px, w, h)
        px.forEach { assertTrue(it and 0xFFFFFF == 0 || it and 0xFFFFFF == 0xFFFFFF) }
        // Paper in the shadow stays white, ink in the shadow stays black.
        assertEquals(0xFFFFFF, px[2 * w + 10] and 0xFFFFFF)
        assertEquals(0, px[5 * w + 8] and 0xFFFFFF)
        assertEquals(0, px[5 * w + 48] and 0xFFFFFF)
        // Alpha is kept.
        assertEquals(0xFF, (px[0] ushr 24))
    }

    @Test fun levelsOfAFlatPictureAreTheFullRange() {
        assertEquals(0 to 255, ScanFilters.levels(IntArray(100) { rgb(120, 120, 120) }))
    }
}
