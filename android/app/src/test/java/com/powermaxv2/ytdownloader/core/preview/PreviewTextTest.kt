package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTextTest {
    @Test fun utf8IsKept() {
        val d = PreviewText.decode("Élève à Noël".toByteArray(Charsets.UTF_8))
        assertEquals("Élève à Noël", d.text)
        assertEquals("UTF-8", d.charset)
    }

    @Test fun latin1Fallback() {
        val d = PreviewText.decode("Élève".toByteArray(Charsets.ISO_8859_1))
        assertEquals("Élève", d.text)
        assertEquals("ISO-8859-1", d.charset)
    }

    @Test fun byteOrderMarks() {
        val bom8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "été".toByteArray(Charsets.UTF_8)
        assertEquals("été", PreviewText.decode(bom8).text)
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a;b".toByteArray(Charsets.UTF_16LE)
        assertEquals(PreviewText.decode(le).let { it.text to it.charset }, "a;b" to "UTF-16LE")
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "ok".toByteArray(Charsets.UTF_16BE)
        assertEquals("ok", PreviewText.decode(be).text)
        // Odd byte count after a UTF-16 cut: the half character is dropped.
        val odd = le + byteArrayOf(0x41)
        assertEquals("a;b", PreviewText.decode(odd, cut = true).text)
    }

    @Test fun aCutInsideAUtf8SequenceStaysUtf8() {
        val bytes = "abc€".toByteArray(Charsets.UTF_8) // € = E2 82 AC
        val cut = bytes.copyOf(bytes.size - 1)
        val d = PreviewText.decode(cut, cut = true)
        assertEquals("abc", d.text)
        assertEquals("UTF-8", d.charset)
        assertEquals(3, PreviewText.utf8End(cut))
        assertEquals(bytes.size, PreviewText.utf8End(bytes))
        assertEquals(2, PreviewText.utf8End("ab".toByteArray()))
        val two = "aé".toByteArray(Charsets.UTF_8)
        assertEquals(1, PreviewText.utf8End(two.copyOf(2)))
    }

    @Test fun linesAndRows() {
        assertEquals(listOf("a", "b", "c", ""), PreviewText.splitLines("a\r\nb\rc\n\n"))
        assertEquals(listOf(""), PreviewText.splitLines(""))
        assertEquals(listOf("abcd", "ef", "x"), PreviewText.rows("abcdef\nx", maxChars = 4))
    }
}
