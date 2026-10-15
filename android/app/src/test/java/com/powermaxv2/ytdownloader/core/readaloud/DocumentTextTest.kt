package com.powermaxv2.ytdownloader.core.readaloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentTextTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test fun wordParagraphsWithoutFieldCodes() {
        val xml = """<w:document><w:body>
            <w:p><w:r><w:t>Bonjour</w:t></w:r><w:r><w:tab/><w:t xml:space="preserve">l&apos;équipe</w:t></w:r></w:p>
            <w:p><w:r><w:instrText>HYPERLINK "https://x"</w:instrText></w:r><w:r><w:t>Lien</w:t></w:r><w:r><w:delText>effacé</w:delText></w:r></w:p>
            </w:body></w:document>"""
        val text = SpeechText.normalize(DocumentText.docx(zip("word/document.xml" to xml)))
        assertEquals("Bonjour l'équipe\nLien", text)
    }

    @Test fun openDocumentParagraphs() {
        val xml = """<office:document-content><office:body><office:text>
            <text:h>Titre</text:h><text:p>Un<text:s/>deux<office:annotation><text:p>note</text:p></office:annotation></text:p>
            </office:text></office:body></office:document-content>"""
        assertEquals("Titre\nUn deux", SpeechText.normalize(DocumentText.odt(zip("content.xml" to xml))))
    }

    @Test fun epubFollowsTheSpine() {
        val book = zip(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<package><manifest>
                <item id="c2" href="Text/ch%202.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="css" href="style.css" media-type="text/css"/>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml"/>
                </manifest><spine><itemref idref="c1"/><itemref idref="nav" linear="no"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/Text/ch1.xhtml" to "<html><body><h1>Chapitre 1</h1><p>Il était une fois.</p></body></html>",
            "OEBPS/Text/ch 2.xhtml" to "<html><body><h1>Chapitre 2</h1><p>Fin.</p></body></html>",
            "OEBPS/nav.xhtml" to "<html><body>Sommaire</body></html>",
        )
        assertEquals("Chapitre 1\nIl était une fois.\n\nChapitre 2\nFin.", SpeechText.normalize(DocumentText.epub(book)))
    }

    @Test fun epubWithoutSpineReadsPagesInNameOrder() {
        val book = zip("b.html" to "<p>Deux</p>", "a.xhtml" to "<p>Un</p>")
        assertEquals("Un\n\nDeux", SpeechText.normalize(DocumentText.epub(book)))
    }

    @Test fun hrefsResolveInsideTheZip() {
        assertEquals("OEBPS/Text/a.xhtml", DocumentText.resolve("OEBPS/", "Text/a.xhtml#p3"))
        assertEquals("Text/a.xhtml", DocumentText.resolve("OEBPS/", "../Text/a.xhtml"))
        assertEquals("a.xhtml", DocumentText.resolve("", "../../a.xhtml"))
        assertEquals("OEBPS/a+b.xhtml", DocumentText.resolve("OEBPS/", "a+b.xhtml"))
    }

    @Test fun refusesMissingParts() {
        try {
            DocumentText.docx(zip("other.xml" to "<x/>"))
            fail()
        } catch (e: DocumentTextError) {
            assertTrue(e.message!!.contains("Word"))
        }
        try {
            DocumentText.epub(zip("cover.jpg" to "x"))
            fail()
        } catch (e: DocumentTextError) {
            assertTrue(e.message!!.contains("aucune page"))
        }
    }
}
