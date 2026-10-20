package com.powermaxv2.ytdownloader.core.pdfimages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImagesPlanTest {
    @Test fun a4AtEachSharpness() {
        assertEquals(1240 to 1754, PdfImagesPlan.renderSize(595f, 842f, 150))
        assertEquals(2479 to 3508, PdfImagesPlan.renderSize(595f, 842f, 300))
        val (w, h) = PdfImagesPlan.renderSize(595f, 842f, Sharpness.SCREEN.dpi)
        assertEquals(909, w)
        assertEquals(1286, h)
    }

    @Test fun hugePagesStayUnderTheCaps() {
        // A1 poster at 300 dpi would be 7016 × 9933 px.
        val (w, h) = PdfImagesPlan.renderSize(1684f, 2384f, 300)
        assertTrue(maxOf(w, h) <= PdfImagesPlan.MAX_SIDE)
        assertTrue(w.toLong() * h <= PdfImagesPlan.MAX_PIXELS + 10_000)
        // A very long strip: the side cap wins.
        val (sw, sh) = PdfImagesPlan.renderSize(200f, 14_400f, 300)
        assertEquals(PdfImagesPlan.MAX_SIDE, sh)
        assertTrue(sw >= 1)
        // A square banner: the pixel cap wins.
        val (bw, bh) = PdfImagesPlan.renderSize(1000f, 1000f, 300)
        assertTrue(bw.toLong() * bh <= PdfImagesPlan.MAX_PIXELS + 10_000)
        assertEquals(bw, bh)
    }

    @Test fun emptyPagesDoNotDivideByZero() {
        val (w, h) = PdfImagesPlan.renderSize(0f, 0f, 150)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test fun parsesTheUsualWaysOfWritingPages() {
        assertEquals(listOf(1, 2, 3, 7), PdfImagesPlan.parsePages("1-3, 7", 10))
        assertEquals(listOf(2, 4), PdfImagesPlan.parsePages(" 2 ; 4 ", 10))
        assertEquals(listOf(8, 9, 10), PdfImagesPlan.parsePages("8-", 10))
        assertEquals(listOf(1, 2, 3), PdfImagesPlan.parsePages("-3", 10))
        assertEquals(listOf(2, 3, 4, 5), PdfImagesPlan.parsePages("5-2", 10))
        assertEquals(listOf(3, 4, 5), PdfImagesPlan.parsePages("3 à 5", 10))
        assertEquals(listOf(3, 4), PdfImagesPlan.parsePages("3–4", 10))
        assertEquals(listOf(1, 2), PdfImagesPlan.parsePages("1,1,2,2-1", 10))
    }

    @Test fun refusesPagesThatDoNotExist() {
        assertNull(PdfImagesPlan.parsePages("", 10))
        assertNull(PdfImagesPlan.parsePages(" , ", 10))
        assertNull(PdfImagesPlan.parsePages("0", 10))
        assertNull(PdfImagesPlan.parsePages("11", 10))
        assertNull(PdfImagesPlan.parsePages("2-12", 10))
        assertNull(PdfImagesPlan.parsePages("deux", 10))
        assertNull(PdfImagesPlan.parsePages("1-2-3", 10))
        assertNull(PdfImagesPlan.parsePages("1", 0))
    }

    @Test fun writesPagesShort() {
        assertEquals("", PdfImagesPlan.formatPages(emptyList()))
        assertEquals("4", PdfImagesPlan.formatPages(listOf(4)))
        assertEquals("1-3, 7, 9, 10", PdfImagesPlan.formatPages(setOf(10, 9, 7, 3, 2, 1)))
        assertEquals("1-12", PdfImagesPlan.formatPages((1..12).toList()))
    }

    @Test fun parseAndFormatAgree() {
        val pages = listOf(1, 2, 3, 5, 8, 9, 10, 11)
        assertEquals(pages, PdfImagesPlan.parsePages(PdfImagesPlan.formatPages(pages), 12))
    }

    @Test fun toggleAndAll() {
        assertEquals(setOf(1, 3), PdfImagesPlan.toggle(setOf(1, 2, 3), 2))
        assertEquals(setOf(1, 2, 3), PdfImagesPlan.toggle(setOf(1, 3), 2))
        assertEquals(setOf(1, 2, 3), PdfImagesPlan.all(3))
        assertTrue(PdfImagesPlan.all(0).isEmpty())
    }

    @Test fun namesKeepTheGalleryInOrder() {
        assertEquals("Facture - page 03.jpg", PdfImagesPlan.pageName("Facture.pdf", 3, 9, "jpg"))
        assertEquals("Livre - page 007.png", PdfImagesPlan.pageName("Livre.PDF", 7, 120, "png"))
        assertEquals("Facture - p02 image 1.jpg", PdfImagesPlan.embeddedName("Facture.pdf", 2, 4, 1, "jpg"))
        assertEquals("Facture - images.zip", PdfImagesPlan.zipName("Facture.pdf"))
    }

    @Test fun namesAreSafe() {
        assertEquals("a_b_c", PdfImagesPlan.clean("a/b:c.pdf"))
        assertEquals("document", PdfImagesPlan.clean(".pdf"))
        assertEquals("document", PdfImagesPlan.clean("  "))
        assertEquals(80, PdfImagesPlan.clean("x".repeat(200) + ".pdf").length)
    }

    @Test fun manyPicturesGoInAZip() {
        assertFalse(PdfImagesPlan.zipByDefault(1))
        assertFalse(PdfImagesPlan.zipByDefault(PdfImagesPlan.MANY))
        assertTrue(PdfImagesPlan.zipByDefault(PdfImagesPlan.MANY + 1))
    }

    @Test fun smallEmbeddedPicturesAreLeftOut() {
        assertFalse(PdfImagesPlan.worthKeeping(12, 12))
        assertFalse(PdfImagesPlan.worthKeeping(47, 30))
        assertTrue(PdfImagesPlan.worthKeeping(640, 8))
        assertTrue(PdfImagesPlan.worthKeeping(48, 48))
    }

    @Test fun bigEmbeddedPicturesDecodeSmaller() {
        assertEquals(1, PdfImagesPlan.subsampling(4000, 3000))
        assertEquals(2, PdfImagesPlan.subsampling(8000, 6000))
        assertEquals(4, PdfImagesPlan.subsampling(16_000, 12_000))
    }

    @Test fun wording() {
        assertEquals("1 image", PdfImagesPlan.imagesText(1))
        assertEquals("12 images", PdfImagesPlan.imagesText(12))
        assertEquals("1 page", PdfImagesPlan.pagesText(1))
        assertEquals("3 pages", PdfImagesPlan.pagesText(3))
    }
}
