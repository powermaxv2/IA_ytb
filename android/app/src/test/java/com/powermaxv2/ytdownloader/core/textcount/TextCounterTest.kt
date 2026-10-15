package com.powermaxv2.ytdownloader.core.textcount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCounterTest {
    @Test fun emptyTextCountsNothing() {
        val s = TextCounter.stats("")
        assertTrue(s.isEmpty)
        assertEquals(0, s.words)
        assertEquals(0, s.sentences)
        assertEquals(0, s.paragraphs)
        assertEquals(0, s.lines)
        assertEquals(0, s.readingSeconds)
    }

    @Test fun emojiAccentsAndFlagsAreOneCharacterEach() {
        assertEquals(1, TextCounter.graphemes("👍🏽").size)
        assertEquals(1, TextCounter.graphemes("👨‍👩‍👧").size)
        assertEquals(2, TextCounter.graphemes("🇫🇷🇧🇪").size)
        assertEquals(1, TextCounter.graphemes("é").size)
        assertEquals(1, TextCounter.graphemes("❤️").size)
        assertEquals(1, TextCounter.graphemes("\r\n").size)
        assertEquals(5, TextCounter.stats("Été 🎉").characters)
        assertEquals(4, TextCounter.stats("Été 🎉").charactersNoSpaces)
    }

    @Test fun wordsKeepApostrophesHyphensAndDecimals() {
        assertEquals(listOf("aujourd'hui", "Jean-Pierre", "paie", "3,5", "€"), TextCounter.words("aujourd'hui Jean-Pierre paie 3,5 €").take(4) + "€")
        assertEquals(4, TextCounter.words("aujourd’hui, Jean-Pierre paie 3,5 €").size)
        assertEquals(2, TextCounter.words("salut - toi").size)
        assertEquals(2, TextCounter.words("fin.Début").size)
        assertEquals(0, TextCounter.words("🎉 !!! …").size)
    }

    @Test fun sentencesEndOnMarksNotOnDecimals() {
        assertEquals(3, TextCounter.sentences("Salut ! Ça va ? Il fait 3.5 degrés."))
        assertEquals(1, TextCounter.sentences("Quoi ?!?"))
        assertEquals(2, TextCounter.sentences("Bonjour. Sans point à la fin"))
        assertEquals(2, TextCounter.sentences("Titre\n\nUn paragraphe"))
        assertEquals(0, TextCounter.sentences("..."))
    }

    @Test fun paragraphsAndLines() {
        val text = "Un\ndeux\n\n  \nTrois\n\n\nQuatre"
        assertEquals(3, TextCounter.paragraphs(text))
        assertEquals(8, TextCounter.stats(text).lines)
        assertEquals(1, TextCounter.paragraphs("seul"))
    }

    @Test fun readingAndSpeakingTimes() {
        val text = List(230) { "mot" }.joinToString(" ")
        val s = TextCounter.stats(text)
        assertEquals(60, s.readingSeconds)
        assertEquals(92, s.speakingSeconds)
        assertEquals(1, TextCounter.stats("un").readingSeconds)
        assertEquals("0 s", TextCounter.duration(0))
        assertEquals("45 s", TextCounter.duration(45))
        assertEquals("1 min 30 s", TextCounter.duration(90))
        assertEquals("2 min", TextCounter.duration(120))
        assertEquals("1 h 05", TextCounter.duration(3_900))
    }

    @Test fun topWordsSkipSmallWordsAndSingles() {
        val s = TextCounter.stats("Le chat et le chien. Le chat dort, le chien joue avec le chat. Pour les chats !")
        assertEquals(listOf(WordCount("chat", 3), WordCount("chien", 2)), s.topWords)
        assertEquals("chien", TextCounter.stats("un chien").longestWord)
    }

    @Test fun limitsTellWhatIsLeftOrTooMuch() {
        val fits = TextCounter.fits("a".repeat(120))
        val bio = fits.first { it.limit.label == "Bio Instagram" }
        assertEquals(30, bio.left)
        assertFalse(bio.over)
        val title = fits.first { it.limit.label == "Titre YouTube" }
        assertTrue(title.over)
        assertEquals(-20, title.left)
        assertEquals(1.2f, title.fraction, 0.001f)
    }

    @Test fun smsUseGsmOrUnicodeParts() {
        assertEquals(SmsFit(0, false, 0, 160), TextCounter.sms(""))
        assertEquals(1, TextCounter.sms("é".repeat(160)).parts)
        assertEquals(2, TextCounter.sms("a".repeat(161)).parts)
        assertEquals(1, TextCounter.sms("€".repeat(80)).parts)
        assertEquals(2, TextCounter.sms("€".repeat(81)).parts)
        val emoji = TextCounter.sms("Salut 🎉")
        assertTrue(emoji.unicode)
        assertEquals(1, emoji.parts)
        assertEquals(62, emoji.leftInPart)
        assertEquals(3, TextCounter.sms("ç" + "a".repeat(140)).parts)
    }

    @Test fun numbersAreWrittenTheFrenchWay() {
        assertEquals("1 234", TextCounter.format(1234))
        assertEquals("12", TextCounter.format(12))
        assertEquals("1 mot", TextCounter.plural(1, "mot"))
        assertEquals("1 200 mots", TextCounter.plural(1200, "mot"))
        assertEquals("0 mot", TextCounter.plural(0, "mot"))
    }

    @Test fun caseChanges() {
        val text = "salut LÉA, ça va ? jean-pierre arrive.\nà demain l'ami 🎉"
        assertEquals("SALUT LÉA, ÇA VA ? JEAN-PIERRE ARRIVE.\nÀ DEMAIN L'AMI 🎉", CaseChanger.apply(text, CaseKind.UPPER))
        assertEquals("salut léa, ça va ? jean-pierre arrive.\nà demain l'ami 🎉", CaseChanger.apply(text, CaseKind.LOWER))
        assertEquals("Salut léa, ça va ? Jean-pierre arrive.\nÀ demain l'ami 🎉", CaseChanger.apply(text, CaseKind.SENTENCE))
        assertEquals("Salut Léa, Ça Va ? Jean-Pierre Arrive.\nÀ Demain L'ami 🎉", CaseChanger.apply(text, CaseKind.TITLE))
        assertEquals("sAlUt LéA", CaseChanger.apply("salut léa", CaseKind.ALTERNATE))
        assertEquals("SALUT léa", CaseChanger.apply("salut LÉA", CaseKind.SWAP))
        assertTrue(CaseChanger.isAlready("BONJOUR !", CaseKind.UPPER))
        assertFalse(CaseChanger.isAlready("Bonjour", CaseKind.UPPER))
        assertFalse(CaseChanger.isAlready("123", CaseKind.UPPER))
    }
}
