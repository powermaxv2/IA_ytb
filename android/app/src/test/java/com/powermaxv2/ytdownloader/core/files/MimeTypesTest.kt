package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MimeTypesTest {
    @Test fun documentsAndMediaHaveATypes() {
        assertEquals("application/pdf", MimeTypes.fromName("a.PDF"))
        assertEquals("text/markdown", MimeTypes.fromName("notes.md"))
        assertEquals("text/csv", MimeTypes.fromName("t.csv"))
        assertEquals("application/json", MimeTypes.fromName("d.json"))
        assertEquals("text/html", MimeTypes.fromName("page.html"))
        assertEquals("text/plain", MimeTypes.fromName("a.txt"))
        assertEquals("image/gif", MimeTypes.fromName("a.gif"))
        assertEquals("image/bmp", MimeTypes.fromName("a.bmp"))
        assertEquals("image/tiff", MimeTypes.fromName("a.tiff"))
        assertEquals("video/x-msvideo", MimeTypes.fromName("a.avi"))
        assertEquals("application/zip", MimeTypes.fromName("a.zip"))
        assertNull(MimeTypes.fromName("a.unknownext"))
    }

    @Test fun resolvePrefersAPreciseGivenType() {
        assertEquals("image/png", MimeTypes.resolve("image/png", "x.jpg"))
        assertEquals("text/plain", MimeTypes.resolve("text/plain; charset=utf-8", "x"))
        assertEquals("image/jpeg", MimeTypes.resolve("application/octet-stream", "x.jpg"))
        assertEquals("image/jpeg", MimeTypes.resolve(null, "x.jpeg"))
        assertEquals(MimeTypes.OCTET_STREAM, MimeTypes.resolve("", "x"))
    }
}
