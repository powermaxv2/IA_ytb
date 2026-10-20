package com.powermaxv2.ytdownloader.core.readaloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    private fun pieces(text: String, max: Int = SpeechText.CHUNK_MAX) =
        SpeechText.chunks(text, max).map { text.substring(it.start, it.end) }

    @Test fun kindsFromNameThenType() {
        assertEquals(SpeechSource.TEXT, SpeechText.kindOf("notes.TXT", null))
        assertEquals(SpeechSource.PDF, SpeechText.kindOf("cours.pdf", "application/octet-stream"))
        assertEquals(SpeechSource.DOCX, SpeechText.kindOf("lettre.docx", null))
        assertEquals(SpeechSource.EPUB, SpeechText.kindOf("livre", "application/epub+zip"))
        assertEquals(SpeechSource.SUBTITLES, SpeechText.kindOf("film.srt", null))
        assertEquals(SpeechSource.TEXT, SpeechText.kindOf("sans-extension", "text/plain"))
        assertNull(SpeechText.kindOf("photo.jpg", "image/jpeg"))
        assertNull(SpeechText.kindOf("inconnu", null))
    }

    @Test fun decodesUtf8AndOldWindowsText() {
        assertEquals("été", SpeechText.decode("été".toByteArray()))
        assertEquals("été", SpeechText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "été".toByteArray()))
        assertEquals("été", SpeechText.decode("été".toByteArray(charset("windows-1252"))))
        assertEquals("ça", SpeechText.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "ça".toByteArray(Charsets.UTF_16LE)))
    }

    @Test fun cutsIntoSentences() {
        assertEquals(
            listOf("Bonjour !", "Comment vas-tu ?", "Moi, ça va… merci.", "Fin"),
            pieces("Bonjour ! Comment vas-tu ? Moi, ça va… merci.\nFin"),
        )
        // A dot inside a number or an address does not end a sentence.
        assertEquals(listOf("Il coûte 3.50 € sur gulp.app ce mois-ci."), pieces("Il coûte 3.50 € sur gulp.app ce mois-ci."))
        // Closing quotes stay with their sentence.
        assertEquals(listOf("« Viens ! »", "Il vint."), pieces("« Viens ! » Il vint."))
    }

    @Test fun offsetsPointIntoTheText() {
        val text = "  Un.   Deux.\n\nTrois."
        val chunks = SpeechText.chunks(text)
        assertEquals(listOf("Un.", "Deux.", "Trois."), chunks.map { text.substring(it.start, it.end) })
        assertEquals(0, SpeechText.chunkAt(chunks, 0))
        assertEquals(1, SpeechText.chunkAt(chunks, 9))
        assertEquals(2, SpeechText.chunkAt(chunks, 999))
        assertEquals(3 + 5, SpeechText.readBefore(chunks, 2))
    }

    @Test fun longSentencesBreakAfterACommaThenBetweenWords() {
        val text = "aaaa bbbb cccc, dddd eeee ffff gggg hhhh iiii"
        val parts = pieces(text, max = 20)
        assertEquals("aaaa bbbb cccc,", parts.first())
        assertTrue(parts.all { it.length <= 20 })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        // No space at all: a hard cut.
        assertEquals(listOf("x".repeat(10), "x".repeat(10), "xxx"), pieces("x".repeat(23), max = 10))
    }

    @Test fun skipsPiecesWithoutWords() {
        assertEquals(listOf("Avant.", "Après."), pieces("Avant.\n***\n— —\nAprès."))
        assertTrue(SpeechText.chunks("   \n  ").isEmpty())
    }

    @Test fun blocksGroupWholePieces() {
        val text = "Un. Deux. Trois. Quatre."
        val chunks = SpeechText.chunks(text)
        val blocks = SpeechText.blocks(chunks, max = 10)
        assertEquals(listOf("Un. Deux.", "Trois.", "Quatre."), blocks.map { text.substring(it.start, it.end) })
        assertEquals(1, SpeechText.blocks(chunks, max = 1000).size)
        assertTrue(SpeechText.blocks(emptyList()).isEmpty())
    }

    @Test fun normalizesSpacesAndParagraphs() {
        assertEquals("Un deux\ntrois\n\nquatre", SpeechText.normalize("Un\t  deux \r\n  trois\r\n\r\n\r\n\nquatre  ​"))
        val long = "mot ".repeat(100)
        val cut = SpeechText.normalize(long, max = 50)
        assertTrue(cut.length <= 50)
        assertFalse(cut.endsWith(" "))
        assertTrue(cut.endsWith("mot"))
    }

    @Test fun markdownBecomesProse() {
        val md = """
            # Titre
            Un **gras**, un *italique* et un [lien](https://x.fr).
            - point ~~barré~~
            > citation `code`
            ```
            val x = 1
            ```
            | a | b |
            |---|---|
            | 1 | 2 |
            snake_case reste
        """.trimIndent()
        val out = SpeechText.normalize(SpeechText.fromMarkdown(md))
        assertEquals("Titre\nUn gras, un italique et un lien.\npoint barré\ncitation code\n\na, b\n1, 2\nsnake_case reste", out)
    }

    @Test fun subtitlesLoseTimingsAndTags() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n<i>Bonjour</i> à tous.\n\n2\n00:00:03,000 --> 00:00:04,000\n{\\an8}- On commence ?\n"
        assertEquals("Bonjour à tous. On commence ?", SpeechText.fromSubtitles(srt))
        val vtt = "WEBVTT\n\nNOTE une note\nsur deux lignes\n\n00:01.000 --> 00:02.000\nSalut.\n"
        assertEquals("Salut.", SpeechText.fromSubtitles(vtt))
    }

    @Test fun htmlKeepsOnlyWords() {
        val html = "<html><head><title>T</title><style>p{}</style></head><body><h1>Titre</h1><p>Un &amp; deux&nbsp;: &eacute;t&#233; &#x2014; fin</p><script>alert(1)</script><table><tr><td>a</td><td>b</td></tr></table></body></html>"
        val out = SpeechText.normalize(SpeechText.fromHtml(html))
        assertEquals("Titre\nUn & deux : été — fin\na, b,", out)
        assertEquals("&bogus; &#0;", SpeechText.entities("&bogus; &#0;"))
    }

    @Test fun durationsAndNames() {
        assertEquals(0, SpeechText.estimateSeconds(0, 1f))
        assertEquals(10, SpeechText.estimateSeconds(145, 1f))
        assertEquals(5, SpeechText.estimateSeconds(145, 2f))
        assertEquals("45 s", SpeechText.duration(45))
        assertEquals("3 min", SpeechText.duration(170))
        assertEquals("1 h 05", SpeechText.duration(3_900))
        assertEquals("Texte lu.m4a", SpeechText.outputName(null, "m4a"))
        assertEquals("Cours v1.2 (lu).mp3", SpeechText.outputName("Cours v1.2.pdf", "mp3"))
        assertEquals("_etc_passwd (lu).wav", SpeechText.outputName("/etc/passwd", "wav"))
    }
}
