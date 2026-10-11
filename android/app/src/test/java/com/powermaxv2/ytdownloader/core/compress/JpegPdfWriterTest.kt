package com.powermaxv2.ytdownloader.core.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class JpegPdfWriterTest {
    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private fun write(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        JpegPdfWriter(out).use { writer ->
            repeat(pages) { writer.addPage(fakeJpeg, 100, 141, 595f, 842f) }
            writer.finish()
        }
        return out.toByteArray()
    }

    @Test fun structure() {
        val bytes = write(3)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4\n"))
        assertTrue(text.endsWith("%%EOF\n"))
        assertTrue("/Count 3" in text)
        assertEquals(3, Regex("/Type /Page /Parent").findAll(text).count())
        assertEquals(3, Regex("/Filter /DCTDecode /Length 7").findAll(text).count())
        assertTrue("/MediaBox [0 0 595.00 842.00]" in text)
    }

    @Test fun xrefOffsetsPointAtTheirObjects() {
        val bytes = write(2)
        val text = String(bytes, Charsets.ISO_8859_1)
        val startxref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.substring(startxref).startsWith("xref\n"))
        val lines = text.substring(startxref).lines()
        val count = lines[1].split(" ")[1].toInt()
        // 1 catalog + 1 page tree + 3 objects per page, plus the free entry 0.
        assertEquals(1 + 2 + 3 * 2, count)
        for (id in 1 until count) {
            val offset = lines[2 + id].substring(0, 10).toInt()
            assertTrue("object $id", text.substring(offset).startsWith("$id 0 obj\n"))
            assertEquals(20, lines[2 + id].length + 1)
        }
    }

    @Test fun pictureCanSitInsideALargerPage() {
        val out = ByteArrayOutputStream()
        JpegPdfWriter(out).use { writer ->
            writer.addPage(fakeJpeg, 100, 100, 595f, 842f, 0f, 123.5f, 595f, 595f)
            writer.finish()
        }
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue("/MediaBox [0 0 595.00 842.00]" in text)
        assertTrue("q 595.00 0 0 595.00 0.00 123.50 cm /Im0 Do Q" in text)
    }

    @Test(expected = IllegalStateException::class)
    fun emptyPdfIsRefused() {
        JpegPdfWriter(ByteArrayOutputStream()).use { it.finish() }
    }
}
