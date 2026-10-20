package com.powermaxv2.ytdownloader.core.colors

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test fun hexWritesAndReadsBack() {
        assertEquals("#E4572E", ColorMath.hex(0xE4572E))
        assertEquals("#000000", ColorMath.hex(0))
        assertEquals("#0A0B0C", ColorMath.hex(0xFF0A0B0C.toInt()))
        assertEquals(0xE4572E, ColorMath.parseHex("#e4572e"))
        assertEquals(0xE4572E, ColorMath.parseHex(" E4572E "))
        assertEquals(0xEE5522, ColorMath.parseHex("#e52"))
        assertEquals(0xE4572E, ColorMath.parseHex("0xFFE4572E"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("bonjour"))
        assertNull(ColorMath.parseHex(""))
    }

    @Test fun hslMatchesCss() {
        assertArrayEquals(intArrayOf(0, 100, 50), ColorMath.hsl(0xFF0000))
        assertArrayEquals(intArrayOf(120, 100, 50), ColorMath.hsl(0x00FF00))
        assertArrayEquals(intArrayOf(240, 100, 50), ColorMath.hsl(0x0000FF))
        assertArrayEquals(intArrayOf(0, 0, 100), ColorMath.hsl(0xFFFFFF))
        assertArrayEquals(intArrayOf(0, 0, 50), ColorMath.hsl(0x808080))
        // #E4572E = hsl(14, 77%, 54%).
        assertArrayEquals(intArrayOf(14, 77, 54), ColorMath.hsl(0xE4572E))
        // Magenta-ish hues stay positive.
        assertEquals(330, ColorMath.hsl(0xFF0080)[0])
    }

    @Test fun hsvAndCmyk() {
        assertArrayEquals(intArrayOf(14, 80, 89), ColorMath.hsv(0xE4572E))
        assertArrayEquals(intArrayOf(0, 62, 80, 11), ColorMath.cmyk(0xE4572E))
        assertArrayEquals(intArrayOf(0, 0, 0, 100), ColorMath.cmyk(0x000000))
        assertArrayEquals(intArrayOf(0, 0, 0, 0), ColorMath.cmyk(0xFFFFFF))
    }

    @Test fun formatsAreReadyToPaste() {
        val f = ColorMath.formats(0x2F6FDE)
        assertEquals(listOf("HEX", "RVB", "TSL", "CMJN"), f.map { it.label })
        assertEquals("#2F6FDE", f[0].value)
        assertEquals("rgb(47, 111, 222)", f[1].value)
        assertEquals("hsl(218, 73%, 53%)", f[2].value)
        assertEquals("79 %, 50 %, 0 %, 13 %", f[3].value)
    }

    @Test fun contrastAndTextColour() {
        assertEquals(21.0, ColorMath.contrast(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, ColorMath.contrast(0x777777, 0x777777), 0.001)
        assertTrue(ColorMath.wantsWhiteText(0x1B2A4A))
        assertFalse(ColorMath.wantsWhiteText(0xFFD60A))
        assertFalse(ColorMath.wantsWhiteText(0xFFFFFF))
    }

    @Test fun namesAreTheNearestEverydayWord() {
        assertEquals("Noir", ColorMath.name(0x050505))
        assertEquals("Blanc", ColorMath.name(0xFEFEFE))
        assertEquals("Rouge tomate", ColorMath.name(0xE4572E))
        assertEquals("Bleu ciel", ColorMath.name(0x80C8F0))
        assertEquals("Jaune", ColorMath.name(0xFFDD00))
        assertEquals("Vert", ColorMath.name(0x30A040))
    }

    @Test fun sampleAveragesAroundTheTouchAndSkipsTransparency() {
        val w = 3
        val h = 3
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[4] = 0xFFFFFFFF.toInt()
        // Centre white among black: radius 0 sees white, radius 1 the mix.
        assertEquals(0xFFFFFF, ColorMath.sampleAverage(px, w, h, 1, 1, radius = 0))
        assertEquals(ColorMath.rgb(28, 28, 28), ColorMath.sampleAverage(px, w, h, 1, 1, radius = 1))
        // Corner: only the 4 pixels inside the picture count.
        assertEquals(ColorMath.rgb(64, 64, 64), ColorMath.sampleAverage(px, w, h, 0, 0, radius = 1))
        // Transparent pixels left out.
        val glass = IntArray(4) { 0x00FF0000 }
        glass[3] = 0xFF00FF00.toInt()
        assertEquals(0x00FF00, ColorMath.sampleAverage(glass, 2, 2, 0, 0, radius = 1))
        assertEquals(0xFFFFFF, ColorMath.sampleAverage(IntArray(4), 2, 2, 0, 0))
    }

    @Test fun keptColorsStayUniqueNewestFirstAndBounded() {
        var kept = KeptColors.decode(null)
        assertTrue(kept.isEmpty())
        kept = KeptColors.add(kept, 0xE4572E)
        kept = KeptColors.add(kept, 0x2F6FDE)
        kept = KeptColors.add(kept, 0xFFE4572E.toInt())
        assertEquals(listOf(0xE4572E, 0x2F6FDE), kept)
        assertEquals("E4572E,2F6FDE", KeptColors.encode(kept))
        assertEquals(kept, KeptColors.decode("E4572E,2F6FDE,nimportequoi,"))
        repeat(20) { kept = KeptColors.add(kept, it * 0x010101) }
        assertEquals(KeptColors.MAX, kept.size)
        assertEquals(19 * 0x010101, kept.first())
        assertEquals(listOf(0x2F6FDE), KeptColors.remove(listOf(0xE4572E, 0x2F6FDE), 0xE4572E))
    }
}
