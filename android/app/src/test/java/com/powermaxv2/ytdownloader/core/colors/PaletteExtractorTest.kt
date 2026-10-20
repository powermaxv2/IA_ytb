package com.powermaxv2.ytdownloader.core.colors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteExtractorTest {
    private val opaque = 0xFF000000.toInt()

    /** [w] × [h] picture painted by [paint] (x, y → 0xRRGGBB, opaque). */
    private fun picture(w: Int, h: Int, paint: (Int, Int) -> Int) = IntArray(w * h) { opaque or paint(it % w, it / w) }

    @Test fun oneColourGivesOneSwatchOfTheWholePicture() {
        val px = picture(20, 10) { _, _ -> 0x2F6FDE }
        val palette = PaletteExtractor.extract(px, 20, 10)
        assertEquals(1, palette.size)
        assertEquals(0x2F6FDE, palette[0].rgb)
        assertEquals(1f, palette[0].share, 0.0001f)
    }

    @Test fun twoBlocksComeOutBiggestFirstWithTheirShareAndPlace() {
        // Left 70 % tomato, right 30 % navy.
        val px = picture(100, 10) { x, _ -> if (x < 70) 0xE4572E else 0x1B2A4A }
        val palette = PaletteExtractor.extract(px, 100, 10)
        assertEquals(listOf(0xE4572E, 0x1B2A4A), palette.map { it.rgb })
        assertEquals(0.7f, palette[0].share, 0.001f)
        assertEquals(0.3f, palette[1].share, 0.001f)
        assertTrue(palette[0].x < 0.7f)
        assertTrue(palette[1].x > 0.7f)
    }

    @Test fun lookAlikeShadesMergeIntoOneColour() {
        // Noisy red (±6) and a clear blue: two colours, not a dozen reds.
        val px = picture(60, 60) { x, y ->
            if (y < 40) ColorMath.rgb(220 + (x * 7 + y * 3) % 13 - 6, 40 + (x * 5 + y) % 11 - 5, 40) else 0x2F6FDE
        }
        val palette = PaletteExtractor.extract(px, 60, 60)
        assertEquals(2, palette.size)
        assertTrue(ColorMath.distance(palette[0].rgb, ColorMath.rgb(220, 40, 40)) < 400)
        assertEquals(2f / 3f, palette[0].share, 0.01f)
        assertEquals(0x2F6FDE, palette[1].rgb)
    }

    @Test fun manyColoursAreCappedAndSharesAddUp() {
        // A rainbow of 12 stripes.
        val stripes = intArrayOf(0xFF0000, 0xFF8000, 0xFFFF00, 0x80FF00, 0x00FF00, 0x00FF80, 0x00FFFF, 0x0080FF, 0x0000FF, 0x8000FF, 0xFF00FF, 0xFF0080)
        val px = picture(120, 12) { x, _ -> stripes[x / 10] }
        val palette = PaletteExtractor.extract(px, 120, 12, count = 6)
        assertEquals(6, palette.size)
        assertTrue(palette.zipWithNext().all { (a, b) -> a.share >= b.share })
        val all = PaletteExtractor.extract(px, 120, 12, count = 20)
        assertEquals(1f, all.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
        assertEquals(stripes.toSet(), all.map { it.rgb }.toSet())
    }

    @Test fun transparencyIsIgnored() {
        val px = IntArray(100) { if (it < 90) 0x00FFFFFF else opaque or 0x5BD6A6 }
        val palette = PaletteExtractor.extract(px, 10, 10)
        assertEquals(listOf(0x5BD6A6), palette.map { it.rgb })
        assertEquals(1f, palette[0].share, 0.0001f)
        assertEquals(0.95f, palette[0].y, 0.001f)
        assertTrue(PaletteExtractor.extract(IntArray(100), 10, 10).isEmpty())
    }

    @Test fun textForSharing() {
        val text = PaletteExtractor.asText(listOf(Swatch(0xE4572E, 0.384f, 0f, 0f), Swatch(0xFFFFFF, 0.004f, 0f, 0f)))
        assertEquals("#E4572E  Rouge tomate  38 %\n#FFFFFF  Blanc  1 %", text)
    }
}
