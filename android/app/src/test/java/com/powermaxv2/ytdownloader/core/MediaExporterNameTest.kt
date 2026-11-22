package com.powermaxv2.ytdownloader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaExporterNameTest {
    @Test fun safeNameStripsBidiControlsAndLeadingDots() {
        val spoofed = "facture\u202Efdp.exe"
        val clean = MediaExporter.safeName(spoofed)
        assertFalse('\u202E' in clean)
        assertEquals("hidden.mp4", MediaExporter.safeName("...hidden.mp4"))
        assertEquals("media", MediaExporter.safeName("   ...   "))
    }

    @Test fun numberedNamePreservesExtensionAndHandlesExtensionlessNames() {
        assertEquals("video (1).mp4", MediaExporter.numberedName("video.mp4", 1))
        assertEquals("archive.tar (2).gz", MediaExporter.numberedName("archive.tar.gz", 2))
        // Regression test: previously produced "archive (1)." with a trailing dot on Android 7-9.
        assertEquals("archive (1)", MediaExporter.numberedName("archive", 1))
    }
}
