package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNamesTest {
    @Test fun keepsOrdinaryNames() {
        assertEquals("Vacances 2024.jpg", SafeNames.sanitize("Vacances 2024.jpg"))
        assertEquals("été à Paris.mp4", SafeNames.sanitize("été à Paris.mp4"))
    }

    @Test fun replacesSeparatorsAndReservedCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i_.txt", SafeNames.sanitize("a/b\\c:d*e?f\"g<h>i|.txt"))
        assertEquals("tab_nul_.txt", SafeNames.sanitize("tab\tnul\u0000.txt"))
    }

    @Test fun neverLeavesTraversalOrHiddenNames() {
        val traversal = SafeNames.sanitize("../../data/data/x/shared_prefs/prefs.xml")
        assertFalse(traversal.contains('/'))
        assertFalse(traversal.startsWith("."))
        assertEquals("fichier", SafeNames.sanitize(".."))
        assertEquals("fichier", SafeNames.sanitize("."))
        assertEquals("bashrc", SafeNames.sanitize(".bashrc"))
        assertEquals("fichier", SafeNames.sanitize("   "))
        assertEquals("doc", SafeNames.sanitize("", fallback = "doc"))
        assertEquals("name", SafeNames.sanitize("name. . "))
    }

    @Test fun capsLengthKeepingTheExtension() {
        val long = "x".repeat(300) + ".jpeg"
        val safe = SafeNames.sanitize(long)
        assertEquals(SafeNames.MAX_LENGTH, safe.length)
        assertTrue(safe.endsWith(".jpeg"))
        val noExt = SafeNames.sanitize("y".repeat(200))
        assertEquals(SafeNames.MAX_LENGTH, noExt.length)
    }

    @Test fun extensions() {
        assertEquals("jpg", SafeNames.extensionOf("photo.JPG"))
        assertEquals("gz", SafeNames.extensionOf("a.tar.gz"))
        assertEquals("", SafeNames.extensionOf(".bashrc"))
        assertEquals("", SafeNames.extensionOf("README"))
        assertEquals("", SafeNames.extensionOf("trailing."))
        assertEquals("photo", SafeNames.baseOf("photo.jpg"))
        assertEquals(".bashrc", SafeNames.baseOf(".bashrc"))
    }

    @Test fun withExtensionReplacesTheOldOne() {
        assertEquals("Vacances.jpg", SafeNames.withExtension("Vacances.HEIC", "jpg"))
        assertEquals("son.mp3", SafeNames.withExtension("son", ".MP3"))
        assertEquals("a_b.png", SafeNames.withExtension("a/b.webp", "png"))
        assertEquals("fichier.pdf", SafeNames.withExtension("..", "pdf"))
        assertEquals("notes", SafeNames.withExtension("notes.txt", ""))
        // Junk in the extension is dropped, never written to the path.
        assertEquals("x.sh", SafeNames.withExtension("x", "../.sh"))
        val long = SafeNames.withExtension("z".repeat(500), "webp")
        assertEquals(SafeNames.MAX_LENGTH, long.length)
        assertTrue(long.endsWith(".webp"))
    }
}
