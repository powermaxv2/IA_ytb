package com.powermaxv2.ytdownloader.core.reader

import com.powermaxv2.ytdownloader.core.chat.Kind
import com.powermaxv2.ytdownloader.core.chat.WhatsAppChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatReaderTest {
    private val export = """
        15/09/2026 18:02 - Les messages et les appels sont chiffrés de bout en bout.
        15/09/2026 18:02 - Inès: Qui vient ce soir ?
        15/09/2026 18:03 - Inès: Il reste de la place
        15/09/2026 18:05 - Hugo: Moi !
        15/09/2026 18:40 - Hugo: Élodie aussi
        16/09/2026 09:00 - Camille: IMG-20260916-WA0001.jpg (fichier joint)
        La vue du balcon
        16/09/2026 09:01 - Hugo: Vous avez supprimé ce message
        02/10/2026 21:15 - Inès: <Médias omis>
        02/10/2026 21:16 - Camille: elodie arrive
    """.trimIndent()
    private val messages = WhatsAppChat.parse(export)!!

    @Test fun authorsByCount() {
        assertEquals(listOf("Hugo", "Inès", "Camille"), ChatReader.authors(messages))
    }

    @Test fun meIsWhoDeletedTheirOwnMessageElseTheMostTalkative() {
        assertEquals("Hugo", ChatReader.guessMe(messages))
        val noHint = messages.filter { it.kind != Kind.DELETED }
        assertEquals("Inès", ChatReader.guessMe(noHint.filter { it.author != "Hugo" }))
    }

    @Test fun rowsHaveADayHeaderEachDayAndRuns() {
        val rows = ChatReader.rows(messages, me = "Hugo")
        val days = rows.filterIsInstance<ReaderRow.Day>()
        assertEquals(listOf(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 10, 2)), days.map { it.date })
        assertEquals("Mardi 15 septembre 2026", days.first().label)
        val msgs = rows.filterIsInstance<ReaderRow.Message>()
        assertEquals(messages.size, msgs.size)
        // Inès writes twice in a row: her name once, the tail on the second.
        assertTrue(msgs[0].showAuthor)
        assertFalse(msgs[0].last)
        assertFalse(msgs[1].showAuthor)
        assertTrue(msgs[1].last)
        // Mine: right side, never named.
        assertTrue(msgs[2].mine)
        assertFalse(msgs[2].showAuthor)
        // 35 minutes later: a new run.
        assertTrue(msgs[2].last)
        assertEquals("18:05", msgs[2].time)
        assertEquals(rows.indexOf(msgs[4]), ChatReader.rowOf(rows, 4))
    }

    @Test fun twoPeopleAreNeverNamed() {
        val duo = messages.filter { it.author != "Camille" }
        assertTrue(ChatReader.rows(duo, me = "Hugo").filterIsInstance<ReaderRow.Message>().none { it.showAuthor })
    }

    @Test fun monthsToJumpTo() {
        val months = ChatReader.months(messages)
        assertEquals(listOf("sept. 2026", "oct. 2026"), months.map { it.label })
        assertEquals(listOf(0, 6), months.map { it.firstIndex })
    }

    @Test fun searchIgnoresAccentsAndCase() {
        assertEquals(listOf(3, 7), ChatReader.search(messages, "ELODIE"))
        assertEquals(listOf(4), ChatReader.search(messages, "balcon"))
        assertEquals(emptyList<Int>(), ChatReader.search(messages, "e"))
        assertEquals(listOf(0..5), ChatReader.matches("Élodie aussi", "elodie"))
        assertEquals(listOf(0..1, 2..3, 4..5), ChatReader.matches("hahaha", "ha"))
    }

    @Test fun mediaAndCaptions() {
        val photo = messages[4]
        assertEquals(Kind.PHOTO, photo.kind)
        assertEquals("IMG-20260916-WA0001.jpg", photo.file)
        assertEquals("La vue du balcon", ChatReader.body(photo))
        assertEquals("📷 Photo", ChatReader.mediaLabel(photo.kind))
        assertEquals("", ChatReader.body(messages[6]))
        assertEquals(null, ChatReader.mediaLabel(Kind.TEXT))
    }

    @Test fun summaryLine() {
        assertEquals("8 messages · 3 personnes · du 15 sept. 2026 au 2 oct. 2026", ChatReader.summary(messages))
        assertEquals("Aucun message", ChatReader.summary(emptyList()))
    }

    @Test fun foldKeepsOneCharacterPerCharacter() {
        listOf("Élodie 😂 ça", "Œuvre", "ñandú").forEach { assertEquals(it.length, ChatReader.fold(it).length) }
        assertEquals("elodie", ChatReader.fold("ÉLODIE"))
    }
}
