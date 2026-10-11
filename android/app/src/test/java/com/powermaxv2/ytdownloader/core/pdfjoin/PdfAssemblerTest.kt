package com.powermaxv2.ytdownloader.core.pdfjoin

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** The Assembleur's writer on real PDF made here: pages copied with their text, links followed, photos on A4. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class PdfAssemblerTest {
    private lateinit var dir: File

    @Before fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        dir = Files.createTempDirectory("pdfjoin").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    /** "devis.pdf": 3 A4 pages saying "Devis page N"; page 1 links to pages 2 and 3. */
    private fun devis(): File {
        val file = File(dir, "devis.pdf")
        PDDocument().use { doc ->
            val pages = (1..3).map { n ->
                PDPage(PDRectangle.A4).also { page ->
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { c ->
                        c.setNonStrokingColor(0.9f, 0.2f, 0.1f)
                        c.addRect(40f, 760f, 200f, 30f)
                        c.fill()
                        c.beginText()
                        c.setFont(PDType1Font.HELVETICA, 18f)
                        c.newLineAtOffset(40f, 700f)
                        c.showText("Devis page $n")
                        c.endText()
                    }
                }
            }
            pages[0].annotations = listOf(2, 3).map<Int, PDAnnotation> { target ->
                PDAnnotationLink().apply {
                    rectangle = PDRectangle(40f, 600f - target * 40f, 200f, 30f)
                    destination = PDPageFitDestination().apply { page = pages[target - 1] }
                }
            }
            doc.save(file)
        }
        return file
    }

    /** "facture.pdf": 2 landscape pages, the second already turned a quarter. */
    private fun facture(): File {
        val file = File(dir, "facture.pdf")
        PDDocument().use { doc ->
            repeat(2) { i ->
                val page = PDPage(PDRectangle(842f, 595f))
                if (i == 1) page.rotation = 90
                doc.addPage(page)
                PDPageContentStream(doc, page).use { c ->
                    c.beginText()
                    c.setFont(PDType1Font.HELVETICA, 18f)
                    c.newLineAtOffset(40f, 500f)
                    c.showText("Facture page ${i + 1}")
                    c.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun photo(): File {
        val file = File(dir, "photo.png")
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 120, 220))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun source(n: Int, file: File, pdf: Boolean, pages: Int) =
        JoinSource(LocalFile(Uri.parse("content://test/$n"), file.name, file.length(), if (pdf) "application/pdf" else "image/png"), pdf, file, pages)

    private fun text(doc: PDDocument, page: Int): String =
        PDFTextStripper().apply {
            startPage = page
            endPage = page
        }.getText(doc)

    @Test fun pagesAreCopiedWithTheirTextInTheOrderShown() = runBlocking {
        val sources = listOf(source(0, devis(), true, 3), source(1, facture(), true, 2), source(2, photo(), false, 1))
        val pages = listOf(
            JoinPage(1, 0, 0, 595f, 842f),
            // Page 2 of the devis is left out; page 3 comes second.
            JoinPage(3, 0, 2, 595f, 842f),
            JoinPage(5, 1, 1, 595f, 842f, rotation = 90),
            JoinPage(6, 2, 0, 842f, 595f),
        )
        val out = File(dir, "out.pdf")
        var written = 0
        PdfAssembler(sources) { error("nothing to draw") }.use { assembler ->
            assembler.write(pages, out) { written = it }
            assertEquals(0, assembler.rasterized)
        }
        assertEquals(4, written)

        PDDocument.load(out).use { doc ->
            assertEquals(4, doc.numberOfPages)
            // Text is still text.
            assertTrue(text(doc, 1).contains("Devis page 1"))
            assertTrue(text(doc, 2).contains("Devis page 3"))
            assertTrue(text(doc, 3).contains("Facture page 2"))

            // The link to page 3 follows it; the link to page 2 (not copied) is gone.
            val links = doc.getPage(0).annotations.filterIsInstance<PDAnnotationLink>()
            assertEquals(1, links.size)
            val dest = links.single().cosObject.getDictionaryObject(COSName.DEST) as COSArray
            assertSame(doc.getPage(1).cosObject, dest.getObject(0))
            assertSame(doc.getPage(0).cosObject, links.single().cosObject.getDictionaryObject(COSName.P))

            // Its own quarter turn plus the user's.
            assertEquals(180, doc.getPage(2).rotation)

            // The photo, wider than tall, sits on a landscape A4.
            val photoPage = doc.getPage(3)
            assertEquals(842f, photoPage.mediaBox.width, 0.5f)
            assertEquals(595f, photoPage.mediaBox.height, 0.5f)
            assertEquals(0, photoPage.rotation)
        }
    }

    @Test fun nothingOfTheOtherPagesComesAlong() = runBlocking {
        val sources = listOf(source(0, devis(), true, 3))
        val out = File(dir, "one.pdf")
        PdfAssembler(sources) { error("nothing to draw") }.use { it.write(listOf(JoinPage(2, 0, 1, 595f, 842f)), out) {} }
        PDDocument.load(out).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(text(doc, 1).contains("Devis page 2"))
            assertFalse(text(doc, 1).contains("Devis page 1"))
            // Only one page object in the whole file: the others did not ride along through links or /Parent.
            val pageObjects = doc.document.objects.count { (it.getObject() as? COSDictionary)?.getCOSName(COSName.TYPE) == COSName.PAGE }
            assertEquals(1, pageObjects)
        }
    }

    @Test fun onePdfPerPart() = runBlocking {
        val sources = listOf(source(0, devis(), true, 3))
        val all = (1L..3L).map { JoinPage(it, 0, it.toInt() - 1, 595f, 842f) }
        val first = File(dir, "a.pdf")
        val second = File(dir, "b.pdf")
        PdfAssembler(sources) { error("nothing to draw") }.use { assembler ->
            assembler.write(all.take(2), first) {}
            assembler.write(all.drop(2), second) {}
        }
        PDDocument.load(first).use { doc ->
            assertEquals(2, doc.numberOfPages)
            // The link to page 2 follows it inside the first part; the one to page 3 (in the other part) is dropped.
            val links = doc.getPage(0).annotations.filterIsInstance<PDAnnotationLink>()
            assertEquals(1, links.size)
            assertSame(doc.getPage(1).cosObject, (links.single().cosObject.getDictionaryObject(COSName.DEST) as COSArray).getObject(0))
        }
        PDDocument.load(second).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(text(doc, 1).contains("Devis page 3"))
            assertTrue(doc.getPage(0).annotations.isEmpty())
        }
    }

    @Test fun aPdfPdfBoxCannotReadIsDrawnInstead() = runBlocking {
        val broken = File(dir, "abime.pdf").apply { writeText("%PDF-1.4\nnot really a pdf") }
        val sources = listOf(source(0, broken, true, 1))
        val out = File(dir, "drawn.pdf")
        var drawn = 0
        PdfAssembler(sources) { page ->
            drawn++
            Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.also { assertEquals(0, page.rotation) }
        }.use { assembler ->
            assembler.write(listOf(JoinPage(1, 0, 0, 595f, 842f, rotation = 270)), out) {}
            assertEquals(1, assembler.rasterized)
        }
        assertEquals(1, drawn)
        PDDocument.load(out).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertEquals(270, doc.getPage(0).rotation)
            assertEquals(595f, doc.getPage(0).mediaBox.width, 0.5f)
        }
    }

    @Test fun aFailedWriteLeavesNoFile() {
        val sources = listOf(source(0, File(dir, "gone.png"), false, 1))
        val out = File(dir, "fail.pdf")
        val failed = runCatching { runBlocking { PdfAssembler(sources) { error("no") }.use { it.write(listOf(JoinPage(1, 0, 0, 595f, 842f)), out) {} } } }
        assertTrue(failed.isFailure)
        assertFalse(out.exists())
    }
}
