package com.powermaxv2.ytdownloader.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every number checked by hand on small discussions written like real exports. */
class ChatStatsTest {
    private fun stats(text: String) = ChatStats.of(WhatsAppChat.parse(text.trimIndent())!!)

    @Test fun sameMinuteAnswersAreTwentySecondsNotZero() {
        // Android: minutes only. B, A and B again answer within the minute, then B two minutes later.
        val s = stats(
            """
            01/01/2026 10:00 - A: salut
            01/01/2026 10:00 - B: yo
            01/01/2026 10:00 - A: ça va ?
            01/01/2026 10:02 - B: oui
            """,
        )
        assertTrue(s.toTheMinute)
        val r = s.replies(Gap.H6)
        assertEquals(listOf(20L, 20L, 120L), r.all)
        val a = r.rows.first { it.person.name == "A" }
        val b = r.rows.first { it.person.name == "B" }
        assertEquals(20.0, a.average!!, 0.0)
        assertEquals(70.0, b.average!!, 0.0) // (20 + 120) / 2
        assertEquals(70.0, b.median!!, 0.0)
        assertEquals(120L, b.slowest)
        assertEquals(0.5, b.under1min!!, 0.0)
    }

    @Test fun secondsOfAnIphoneExportAreKept() {
        val s = stats("[01/01/2026 10:00:00] A: salut\n[01/01/2026 10:00:00] B: yo\n[01/01/2026 10:00:42] A: ok")
        assertFalse(s.toTheMinute)
        assertEquals(listOf(0L, 42L), s.replies(Gap.H6).all)
    }

    @Test fun rankingIsByAverage() {
        // A: 60 s and 3 × 1 min → avg 60; B: 0-like quick ones but one very slow → avg higher than A.
        val s = stats(
            """
            01/01/2026 10:00 - A: 1
            01/01/2026 10:01 - B: 2
            01/01/2026 10:02 - A: 3
            01/01/2026 10:03 - B: 4
            01/01/2026 10:04 - A: 5
            01/01/2026 12:04 - B: 6
            01/01/2026 12:05 - A: 7
            """,
        )
        val r = s.replies(Gap.H6)
        assertEquals(listOf("A", "B"), r.rows.map { it.person.name })
        assertEquals(60.0, r.rows[0].average!!, 0.0)
        assertEquals((60 + 60 + 7_200) / 3.0, r.rows[1].average!!, 0.001)
        assertEquals(listOf("A", "B"), r.podium.map { it.person.name })
    }

    @Test fun conversationsStartersAndLastWords() {
        val s = stats(
            """
            01/01/2026 10:00 - A: hello
            01/01/2026 10:05 - B: hey
            01/01/2026 20:00 - B: ce soir ?
            01/01/2026 20:01 - A: ok
            01/01/2026 20:02 - B: top
            02/01/2026 09:00 - A: debout ?
            """,
        )
        val r = s.replies(Gap.H6)
        assertEquals(3, r.conversations)
        val a = r.rows.first { it.person.name == "A" }
        val b = r.rows.first { it.person.name == "B" }
        assertEquals(2, a.starts)
        assertEquals(1, b.starts)
        assertEquals(1, a.lastWords) // the very last message
        assertEquals(2, b.lastWords)
        assertEquals(2.0, r.messagesPerConversation, 0.0)
        assertEquals("A", r.starter?.person?.name)
        assertEquals(1, s.replies(Gap.NEVER).conversations)
    }

    @Test fun androidExportWithMedia() {
        val s = stats(
            """
            15/09/2026 21:02 - Inès: STK-20260915-WA0001.webp (fichier joint)
            15/09/2026 21:02 - Inès: ‎STK-20260915-WA0002.webp (fichier joint)
            15/09/2026 21:03 - Hugo: IMG-20260915-WA0003.jpg (fichier joint)
            Regarde ça
            15/09/2026 21:04 - Hugo: PTT-20260915-WA0004.opus (file attached)
            15/09/2026 21:05 - Inès: VID-20260915-WA0005.mp4 (file attached)
            15/09/2026 21:06 - Hugo: Inès.vcf (fichier joint)
            15/09/2026 21:07 - Inès: Facture.pdf (fichier joint)
            """,
        )
        val ines = s.people.first { it.name == "Inès" }
        val hugo = s.people.first { it.name == "Hugo" }
        assertEquals(2, ines.stickers)
        assertEquals(1, ines.count(Kind.VIDEO))
        assertEquals(1, ines.count(Kind.DOCUMENT))
        assertEquals(1, hugo.count(Kind.PHOTO))
        assertEquals(1, hugo.count(Kind.VOICE))
        assertEquals(1, hugo.count(Kind.CONTACT))
        assertEquals(0, ines.words + hugo.words)
        assertEquals(7, s.mediaCount)
        assertTrue(s.stickersKnown)
    }

    @Test fun androidExportWithoutMediaCannotTellStickers() {
        val s = stats("01/01/2026 10:00 - A: <Médias omis>\n01/01/2026 10:01 - B: <Media omitted>\n01/01/2026 10:02 - B: ok")
        assertEquals(2, s.count(Kind.MEDIA))
        assertEquals(0, s.stickers)
        assertFalse(s.stickersKnown)
    }

    @Test fun iphoneExportFrenchAndEnglish() {
        val text = """
            [15/09/2026 18:02:41] Coloc: ‎Les messages et les appels sont chiffrés de bout en bout. Aucun tiers, pas même WhatsApp, ne peut les lire ou les écouter.
            ‎[15/09/2026 18:02:50] Coloc: ‎Inès a créé le groupe « Coloc »
            ‎[15/09/2026 18:03:00] Coloc: ‎Inès a ajouté Hugo
            [15/09/2026 21:02:10] Hugo: Salut
            ‎[15/09/2026 21:02:28] Inès: ‎sticker omis
            ‎[15/09/2026 21:02:30] Inès: ‎image absente
            ‎[15/09/2026 21:03:00] Hugo: ‎audio omis
            ‎[15/09/2026 21:03:10] Hugo: ‎GIF omis
            ‎[15/09/2026 21:04:00] Inès: ‎Appel vocal manqué
            ‎[15/09/2026 21:05:00] Hugo: ‎sticker omitted
            ‎[15/09/2026 21:05:30] Inès: ‎<attached: 00000045-STICKER-2026-09-15-21-05-30.webp>
            ‎[15/09/2026 21:06:00] Hugo: ‎Ce message a été supprimé.
            ‎[15/09/2026 21:07:00] Inès: ‎Facture.pdf • 3 pages ‎document omis
            [15/09/2026 21:08:00] Hugo: Appel vidéo ce soir ?
        """
        val s = stats(text)
        assertEquals(listOf("Hugo", "Inès"), s.people.map { it.name }.sorted())
        val ines = s.people.first { it.name == "Inès" }
        val hugo = s.people.first { it.name == "Hugo" }
        assertEquals(2, ines.stickers)
        assertEquals(1, hugo.stickers)
        assertEquals(1, ines.count(Kind.PHOTO))
        assertEquals(1, ines.count(Kind.CALL))
        assertEquals(1, ines.count(Kind.DOCUMENT))
        assertEquals(1, hugo.count(Kind.VOICE))
        assertEquals(1, hugo.count(Kind.GIF))
        assertEquals(1, hugo.deleted)
        assertEquals(2, hugo.texts) // "Salut" and the typed "Appel vidéo ce soir ?"
        assertEquals(1 + 4, hugo.words)
        assertEquals(11, s.messages.size)
    }

    @Test fun androidNoticesWithAColonAreNotPeople() {
        val s = stats(
            """
            15/09/2026 18:02 - Les messages et les appels sont chiffrés de bout en bout.
            15/09/2026 18:03 - Inès a changé le sujet de « Coloc » en « Soirée : samedi »
            15/09/2026 18:04 - Hugo changed the subject from "a" to "b: c"
            15/09/2026 18:05 - Inès: on dit quoi : 21h ?
            15/09/2026 18:06 - Hugo: ok
            """,
        )
        assertEquals(listOf("Inès", "Hugo"), s.messages.map { it.author })
        assertEquals("on dit quoi : 21h ?", s.messages[0].text)
    }

    @Test fun editedAndDeleted() {
        val s = stats(
            """
            01/01/2026 10:00 - A: Salut à tous <Ce message a été modifié>
            01/01/2026 10:01 - B: This message was deleted
            01/01/2026 10:02 - B: Hello <This message was edited>
            01/01/2026 10:03 - A: Vous avez supprimé ce message
            """,
        )
        assertEquals("Salut à tous", s.messages[0].text)
        assertTrue(s.messages[0].edited)
        assertEquals(2, s.edited)
        assertEquals(2, s.count(Kind.DELETED))
        assertEquals(4, s.words)
        assertEquals(0, s.mediaCount) // deleted messages are not media
    }

    @Test fun moreNumbersPerPerson() {
        val s = stats(
            """
            05/01/2026 01:30 - A: Tu dors ? 😴
            05/01/2026 01:31 - A: allo
            05/01/2026 01:32 - A: regarde https://example.com 😴😂
            05/01/2026 09:00 - B: Non
            07/01/2026 09:00 - B: Bon
            """,
        )
        val a = s.people.first { it.name == "A" }
        val b = s.people.first { it.name == "B" }
        assertEquals(3, a.longestBurst)
        assertEquals(3, a.night)
        assertEquals(1, a.links)
        assertEquals(3, a.emojis)
        assertEquals("😴", a.favoriteEmoji)
        assertEquals((2 + 1 + 2) / 3.0, a.wordsPerMessage!!, 0.001) // emojis and "?" are not words
        assertEquals(2, b.activeDays)
        assertEquals(3, s.days) // 5, 6 and 7 January
        assertEquals(2, s.activeDays)
        assertEquals(1, s.longestDayStreak)
        assertEquals(DayCount(java.time.LocalDate.of(2026, 1, 5).toEpochDay(), 4), s.busiestDay)
        assertEquals(2 * 86_400L, s.longestSilence)
        assertEquals(4, s.weekdays[0]) // Monday 5 January 2026
        assertEquals(1, s.weekdays[2])
    }

    @Test fun kinds() {
        assertEquals(Kind.STICKER, WhatsAppChat.kindOf("STK-20260101-WA0000.webp (fichier joint)"))
        assertEquals(Kind.MEDIA, WhatsAppChat.kindOf("<Médias omis>"))
        assertEquals(Kind.POLL, WhatsAppChat.kindOf("SONDAGE :\nOn mange où ?\nOPTION : Pizza (2 votes)"))
        assertEquals(Kind.LOCATION, WhatsAppChat.kindOf("localisation : https://maps.google.com/?q=48.8,2.3"))
        assertEquals(Kind.TEXT, WhatsAppChat.kindOf("Une image absente de mon téléphone depuis hier, bizarre non ?"))
        assertEquals(Kind.TEXT, WhatsAppChat.kindOf("Appel vidéo ce soir ?"))
        assertNull(WhatsAppChat.parse("pas un export"))
    }

    @Test fun sampleHasStickersAndNoZeroSeconds() {
        val s = ChatSample.stats
        assertTrue(s.stickers > 0)
        assertTrue(s.toTheMinute)
        val r = s.replies(Gap.DEFAULT)
        assertTrue(r.all.none { it == 0L })
        assertTrue(r.rows.all { (it.average ?: 1.0) > 0.0 })
    }

    @Test fun nightsLeftOut() {
        // Asked at 1:00, answered at 9:00: 8 h, or 2 h without the night (0 h to 7 h).
        val s = stats(
            """
            01/01/2026 01:00 - A: dodo ?
            01/01/2026 09:00 - B: oui
            """,
        )
        assertEquals(8 * 3_600L, s.replies(ReplyOptions(Gap.H24)).all.first())
        assertEquals(2 * 3_600L, s.replies(ReplyOptions(Gap.H24, nights = true)).all.first())
        // Chatting at 2 a.m. still counts.
        val night = stats("01/01/2026 02:00 - A: t'es là ?\n01/01/2026 02:03 - B: oui")
        assertEquals(180L, night.replies(ReplyOptions(Gap.H24, nights = true)).all.first())
    }

    @Test fun realWaitStartsAtTheFirstMessageOfTheRun() {
        val s = stats(
            """
            01/01/2026 10:00 - A: hé
            01/01/2026 10:20 - A: tu es là ?
            01/01/2026 10:25 - B: oui
            """,
        )
        assertEquals(listOf(300L), s.replies(ReplyOptions()).all)
        assertEquals(listOf(1_500L), s.replies(ReplyOptions(realWait = true)).all)
    }

    @Test fun pairsRecordsAndUnanswered() {
        val s = stats(
            """
            01/01/2026 10:00 - A: a
            01/01/2026 10:01 - B: b
            01/01/2026 10:02 - A: c
            01/01/2026 10:05 - B: d
            01/01/2026 10:06 - C: e
            01/01/2026 10:16 - A: on mange où ?
            02/01/2026 18:00 - B: salut
            02/01/2026 18:00 - B: ça va ? un très long message avec beaucoup de mots pour le record
            """,
        )
        val r = s.replies(ReplyOptions(Gap.H6))
        val ab = r.pairs.first { it.from.name == "A" && it.to.name == "B" }
        assertEquals(2, ab.count)
        assertEquals(120.0, ab.average!!, 0.01) // 60 s and 180 s
        assertEquals(1, r.rows.first { it.person.name == "A" }.unanswered)
        assertEquals(0, r.rows.first { it.person.name == "B" }.unanswered) // last message of the export: not counted
        assertEquals(60L, r.fastest!!.seconds)
        assertEquals("A", r.slowest!!.person)
        assertEquals("B", s.longestMessage!!.person)
        assertEquals("B", s.longestMonologue!!.person)
        assertEquals(2, s.longestMonologue!!.value)
        assertEquals(listOf("A", "B"), r.talkativePairs.first().first.toList().map { it.name }.sorted())
    }

    @Test fun trimmedMeanLeavesExtremesOut() {
        val a = List(9) { 60L } + listOf(100_000L)
        assertEquals(60.0, ChatStats.trimmedMean(a)!!, 0.01)
        assertEquals(10_054.0, ChatStats.mean(a)!!, 0.01)
    }

    @Test fun wordsAndSites() {
        val s = stats(
            """
            01/01/2026 10:00 - A: grave mdr regarde https://youtu.be/abc
            01/01/2026 10:01 - B: mdr
            01/01/2026 10:02 - A: grave mdr les https://www.tiktok.com/@x et https://youtube.com/watch?v=1
            """,
        )
        assertEquals(listOf("YouTube" to 2, "TikTok" to 1), s.sites)
        assertEquals(listOf("grave" to 2, "mdr" to 2), s.people.first { it.name == "A" }.topWords.sortedBy { it.first })
    }

    @Test fun periodsByMonthForLongDiscussions() {
        val s = stats("15/01/2026 10:00 - A: a\n15/01/2026 10:01 - B: b\n02/04/2026 10:00 - A: c\n02/04/2026 10:05 - B: d")
        assertTrue(!s.weekly)
        assertEquals(listOf("janv.", "févr.", "mars", "avr."), s.periods.map { it.label })
        assertEquals(listOf(2, 0, 0, 2), s.periods.map { it.messages })
        val r = s.replies(Gap.DEFAULT)
        assertEquals(4, r.perPeriod.size)
        assertEquals(300.0, r.perPeriod.last()!!, 0.01)
        assertNull(r.perPeriod[1])
    }
}
