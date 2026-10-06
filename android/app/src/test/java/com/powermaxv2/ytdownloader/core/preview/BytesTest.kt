package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BytesTest {
    @Test fun humanSizes() {
        assertEquals("0 o", HumanSize.format(0))
        assertEquals("999 o", HumanSize.format(999))
        assertEquals("1,0 Ko", HumanSize.format(1000))
        assertEquals("1,5 Ko", HumanSize.format(1500))
        assertEquals("12,3 Mo", HumanSize.format(12_300_000))
        assertEquals("2,1 Go", HumanSize.format(2_147_483_648))
        // Never "1000,0 Ko".
        assertEquals("1,0 Mo", HumanSize.format(999_990))
        assertEquals("0 o", HumanSize.format(-5))
    }

    @Test fun exactSizes() {
        assertEquals("1 octet", HumanSize.exact(1))
        assertEquals("999 octets", HumanSize.exact(999))
        assertEquals("1\u202F234\u202F567 octets", HumanSize.exact(1_234_567))
    }

    @Test fun hexDumpOfAPngHeader() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52)
        val lines = HexDump.lines(png)
        assertEquals(2, lines.size)
        assertEquals("0000  89 50 4E 47 0D 0A 1A 0A  .PNG....", lines[0])
        assertEquals("0008  00 00 00 0D 49 48 44 52  ....IHDR", lines[1])
    }

    @Test fun hexDumpPadsTheLastLineAndStopsAtTheCap() {
        val lines = HexDump.lines("abc".toByteArray())
        assertEquals("0000  61 62 63" + " ".repeat(17) + "abc", lines.single())
        val big = ByteArray(1000) { it.toByte() }
        val capped = HexDump.lines(big)
        assertEquals(32, capped.size)
        assertTrue(capped.last().startsWith("00F8  F8 F9 FA FB FC FD FE FF"))
        assertEquals(emptyList<String>(), HexDump.lines(ByteArray(0)))
    }

    @Test fun hexOfDigest() {
        assertEquals("00ff10", HexDump.hex(byteArrayOf(0, -1, 16)))
    }

    @Test fun extensionThatLies() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals("png", ExtensionCheck.mismatch("photo.jpg", png))
        assertNull(ExtensionCheck.mismatch("photo.png", png))
        assertNull(ExtensionCheck.mismatch("photo.PNG", png))
        // No extension, or unknown bytes: nothing to say.
        assertNull(ExtensionCheck.mismatch("photo", png))
        assertNull(ExtensionCheck.mismatch("notes.txt", "hello".toByteArray()))
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)
        assertNull(ExtensionCheck.mismatch("rapport.docx", zip))
        assertNull(ExtensionCheck.mismatch("app.apk", zip))
        assertEquals("zip", ExtensionCheck.mismatch("rapport.pdf", zip))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertNull(ExtensionCheck.mismatch("a.jpeg", jpeg))
        assertEquals("jpg", ExtensionCheck.mismatch("a.gif", jpeg))
    }

    @Test fun mp4FamilyIsOneContainer() {
        // An M4A whose major brand is "mp42" sniffs as MP4 video: same container, no warning.
        val head = byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(12)
        assertNull(ExtensionCheck.mismatch("chanson.m4a", head))
        assertNull(ExtensionCheck.mismatch("clip.mov", head))
        assertEquals("mp4", ExtensionCheck.mismatch("clip.mkv", head))
    }
}
