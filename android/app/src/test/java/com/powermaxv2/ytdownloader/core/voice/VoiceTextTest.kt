package com.powermaxv2.ytdownloader.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTextTest {
    // Shape of a real Vosk 0.3 final result (vosk-model-small-fr-0.22, checked 2026-10-11).
    private val result = """
        {
          "result" : [{
              "conf" : 1.000000,
              "end" : 0.450000,
              "start" : 0.000000,
              "word" : "salut"
            }, {
              "conf" : 0.972726,
              "end" : 0.810000,
              "start" : 0.510000,
              "word" : "c'est"
            }, {
              "conf" : 0.364109,
              "end" : 1.170000,
              "start" : 0.810000,
              "word" : "moi"
            }],
          "text" : "salut c'est moi"
        }
    """.trimIndent()

    @Test fun parsesWordsWithTimesAndConfidence() {
        val words = VoiceText.parseResult(result)
        assertEquals(listOf("salut", "c'est", "moi"), words.map { it.text })
        assertEquals(510L, words[1].startMs)
        assertEquals(1170L, words[2].endMs)
        assertFalse(words[0].unsure)
        assertTrue(words[2].unsure)
    }

    @Test fun fallsBackToTextWithoutTimings() {
        val words = VoiceText.parseResult("""{"text" : "bonjour à tous"}""")
        assertEquals(listOf("bonjour", "à", "tous"), words.map { it.text })
    }

    @Test fun emptyAndBrokenResults() {
        assertTrue(VoiceText.parseResult("""{"text" : ""}""").isEmpty())
        assertTrue(VoiceText.parseResult("not json").isEmpty())
        assertTrue(VoiceText.parseResult("""{"result":[{"word":"<unk>","start":0,"end":1,"conf":1}],"text":"<unk>"}""").isEmpty())
        assertEquals("", VoiceText.parsePartial("{"))
    }

    @Test fun partial() {
        assertEquals("salut c'est", VoiceText.parsePartial("{\n  \"partial\" : \"salut c'est\"\n}"))
        assertEquals("é\"\\", VoiceText.parsePartial("""{"partial":"é\"\\"}"""))
    }

    @Test fun sentences() {
        assertEquals("Salut c'est moi.", VoiceText.sentence(listOf("salut", "c'est", "moi")))
        assertEquals("T'es où.", VoiceText.sentence(listOf("t'", "es", "où")))
        assertEquals("Éric arrive.", VoiceText.sentence(listOf("éric", "arrive")))
        assertEquals("", VoiceText.sentence(emptyList()))
    }

    @Test fun paragraphsSplitOnLongPauses() {
        val a = VoiceSentence(listOf(VoiceWord("bonjour", 0, 500)))
        val b = VoiceSentence(listOf(VoiceWord("ça", 1_000, 1_200), VoiceWord("va", 1_200, 1_500)))
        val c = VoiceSentence(listOf(VoiceWord("bisous", 6_000, 6_600)))
        assertEquals(listOf("Bonjour. Ça va.", "Bisous."), VoiceText.paragraphs(listOf(a, b, c)))
        assertEquals("Bonjour. Ça va.\n\nBisous.", VoiceText.plainText(listOf(a, b, c)))
        assertEquals(4, VoiceText.wordCount(listOf(a, b, c)))
    }

    @Test fun unsureShare() {
        val s = VoiceSentence(listOf(VoiceWord("a", 0, 1, 0.2f), VoiceWord("b", 1, 2, 0.9f)))
        assertEquals(0.5f, VoiceText.unsureShare(listOf(s)), 0.001f)
        assertEquals(0f, VoiceText.unsureShare(emptyList()), 0f)
    }

    @Test fun clockWordsSpeed() {
        assertEquals("0:07", VoiceText.clock(7_200))
        assertEquals("1:05", VoiceText.clock(65_000))
        assertEquals("1:02:03", VoiceText.clock(3_723_000))
        assertEquals("1 mot", VoiceText.words(1))
        assertEquals("12 mots", VoiceText.words(12))
        assertEquals("× 3,5", VoiceText.speed(7_000, 2_000))
        assertNull(VoiceText.speed(500, 100))
    }

    @Test fun outputName() {
        assertEquals("PTT-20261011-WA0003 (texte).txt", VoiceText.outputName("PTT-20261011-WA0003.opus"))
        assertTrue(VoiceText.outputName("../x.opus").endsWith(".txt"))
        assertFalse(VoiceText.outputName("../x.opus").contains('/'))
    }

    @Test fun fileText() {
        val s = VoiceSentence(listOf(VoiceWord("salut", 0, 500)))
        val text = VoiceText.fileText("PTT.opus", 7_000, listOf(s))
        assertTrue(text.startsWith("Vocal : PTT.opus (0:07)\n"))
        assertTrue(text.endsWith("Salut.\n"))
    }
}
