package com.powermaxv2.ytdownloader.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {
    /** [matrix] as ARGB pixels like the saved PNG: [cell] px per module, 4-module quiet zone, an orange logo square. */
    private fun pixels(matrix: QrMatrix, cell: Int = 6, invert: Boolean = false): Triple<IntArray, Int, Int> {
        val side = (matrix.size + 8) * cell
        val dark = if (invert) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val light = if (invert) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        val out = IntArray(side * side) { light }
        val hole = matrix.logoSpan()
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            val logo = hole != null && x in hole && y in hole
            val color = when {
                logo -> 0xFFFF6A25.toInt()
                matrix[x, y] -> dark
                else -> continue
            }
            for (dy in 0 until cell) for (dx in 0 until cell) out[((y + 4) * cell + dy) * side + (x + 4) * cell + dx] = color
        }
        return Triple(out, side, side)
    }

    @Test fun createdCodesReadBackWithTheLogo() {
        listOf(
            "https://gulp.app",
            "WIFI:T:WPA;S:Maison-5G;P:soleil-2024;;",
            QrDraft(kind = QrKind.CONTACT, name = "Camille Martin", phone = "+33 6 12 34 56 78", email = "camille@exemple.fr").encoded()!!,
            "Été 2026 : on se retrouve à 18 h ! 🎉",
        ).forEach { text ->
            val matrix = QrCodec.encode(text)
            assertNotNull(text, matrix)
            assertTrue(text, matrix!!.strong)
            assertNotNull(matrix.logoSpan())
            val (px, w, h) = pixels(matrix)
            val read = QrCodec.decodePicture(px, w, h)
            assertEquals(text, read?.text)
            assertEquals("QR_CODE", read?.format)
        }
    }

    @Test fun longTextGetsNoLogoAndStillReads() {
        val text = "Gulp ".repeat(120)
        val matrix = QrCodec.encode(text)!!
        assertFalse(matrix.strong)
        assertNull(matrix.logoSpan())
        val (px, w, h) = pixels(matrix, cell = 4)
        assertEquals(text, QrCodec.decodePicture(px, w, h)?.text)
    }

    @Test fun lightOnDarkCodesRead() {
        val matrix = QrCodec.encode("https://gulp.app/sombre")!!
        val (px, w, h) = pixels(matrix, invert = true)
        assertEquals("https://gulp.app/sombre", QrCodec.decodePicture(px, w, h)?.text)
    }

    @Test fun limits() {
        assertNull(QrCodec.encode(""))
        assertNull(QrCodec.encode("x".repeat(QrCodec.MAX_CHARS + 1)))
        assertNotNull(QrCodec.encode("x".repeat(QrCodec.MAX_CHARS)))
        val blank = IntArray(100 * 100) { 0xFFFFFFFF.toInt() }
        assertNull(QrCodec.decodePicture(blank, 100, 100))
    }

    @Test fun finderPatternsAreInTheCorners() {
        val m = QrCodec.encode("abc")!!
        assertTrue(m.isFinder(0, 0))
        assertTrue(m.isFinder(m.size - 1, 6))
        assertTrue(m.isFinder(3, m.size - 4))
        assertFalse(m.isFinder(m.size - 1, m.size - 1))
        // The logo hole stays clear of the finder patterns.
        val span = m.logoSpan()!!
        assertTrue(span.first > 7 && span.last < m.size - 8)
    }
}
