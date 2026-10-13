package com.powermaxv2.ytdownloader.core.quickreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class QuickRepliesTest {
    /** Tuesday 13 October 2026, 14:05. */
    private val now = LocalDateTime.of(2026, 10, 13, 14, 5)

    @Test fun clockVariablesAreFilled() {
        assertEquals("J'arrive vers 14h20", QuickVars.fill("J'arrive vers {heure+15}", emptyMap(), now))
        assertEquals("Parti à 13h55", QuickVars.fill("Parti à {heure-10}", emptyMap(), now))
        assertEquals("Bien arrivé (14h05)", QuickVars.fill("Bien arrivé ({heure})", emptyMap(), now))
        assertEquals("ce mardi 13 octobre", QuickVars.fill("ce {jour} {date}", emptyMap(), now))
        assertEquals("Bonjour / Bonsoir", QuickVars.fill("{bonjour}", emptyMap(), now) + " / " + QuickVars.fill("{Bonjour}", emptyMap(), now.withHour(20)))
        assertEquals("1h00", QuickVars.fill("{heure+60}", emptyMap(), now.withHour(0).withMinute(0)))
    }

    @Test fun askedVariablesUseTheGivenValueAndIgnoreAccentsAndCase() {
        val text = "Merci {Prénom}, à {lieu} ! Encore merci {prenom}"
        assertEquals(listOf("prenom", "lieu"), QuickVars.asked(text).map { it.key })
        assertEquals("Prénom", QuickVars.asked(text).first().name)
        assertEquals("Merci Léa, à la gare ! Encore merci Léa", QuickVars.fill(text, mapOf("prenom" to " Léa ", "lieu" to "la gare"), now))
        // Not given: left as written, so nothing is lost.
        assertEquals("Merci Léa, à {lieu} ! Encore merci Léa", QuickVars.fill(text, mapOf("prenom" to "Léa", "lieu" to "  "), now))
        assertEquals(listOf("lieu"), QuickVars.missing(text, mapOf("prenom" to "Léa")).map { it.key })
    }

    @Test fun autoVariablesAreNeverAsked() {
        val vars = QuickVars.of("{heure} {heure+15} {jour} {date} {bonjour} {demain} {prénom}")
        assertEquals(listOf(true, true, true, true, true, true, false), vars.map { it.auto })
        assertTrue(QuickVars.asked("Rien à remplir ({heure})").isEmpty())
    }

    @Test fun bracesThatAreNotVariablesStayText() {
        assertEquals("{} et { } et {\n}", QuickVars.fill("{} et { } et {\n}", emptyMap(), now))
        assertTrue(QuickVars.of("{}").isEmpty())
    }

    @Test fun piecesMarkTheVariables() {
        val pieces = QuickVars.pieces("Salut {prénom}, il est {heure}.", emptyMap(), now)
        assertEquals(listOf("Salut ", "{prénom}", ", il est ", "14h05", "."), pieces.map { it.text })
        assertFalse(pieces[1].filled)
        assertTrue(pieces[3].filled)
        assertEquals(pieces.joinToString("") { it.text }, QuickVars.fill("Salut {prénom}, il est {heure}.", emptyMap(), now))
    }

    @Test fun addPutsTheNewMessageOnTopWithAFreshId() {
        val list = QuickReplies.add(QuickReplies.starters, "", "  Je suis en réunion, je te réponds après.  ")
        assertEquals(QuickReplies.starters.size + 1, list.size)
        val added = list.first()
        assertEquals("Je suis en réunion, je te réponds après.", added.text)
        assertEquals("Je suis en réunion, je…", added.title)
        assertEquals(QuickReplies.starters.maxOf { it.id } + 1, added.id)
        assertSame(list, QuickReplies.add(list, "Vide", "   "))
    }

    @Test fun updateRemoveAndMove() {
        val list = QuickReplies.starters
        val edited = QuickReplies.update(list, 3, "Pas dispo", "Je te rappelle ce soir.")
        assertEquals(QuickReply(3, "Pas dispo", "Je te rappelle ce soir."), edited[2])
        assertEquals(list, QuickReplies.update(list, 3, "x", " "))
        assertEquals(list.size - 1, QuickReplies.remove(list, 3).size)
        assertEquals(listOf(2L, 1L, 3L), QuickReplies.move(list, 2, -1).take(3).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), QuickReplies.move(list, 1, 2).take(3).map { it.id })
        assertSame(list, QuickReplies.move(list, 1, -1))
        assertEquals(1L, QuickReplies.move(list, 1, 99).last().id)
    }

    @Test fun searchIgnoresAccentsAndMatchesEveryWord() {
        val list = QuickReplies.starters
        assertEquals(listOf(2L), QuickReplies.search(list, "ARRIVE bien").map { it.id })
        assertEquals(listOf(5L), QuickReplies.search(list, "merci").map { it.id })
        assertEquals(list, QuickReplies.search(list, "  "))
        assertTrue(QuickReplies.search(list, "karaoké").isEmpty())
    }

    @Test fun savingKeepsTabsNewLinesAndBackslashes() {
        val list = listOf(
            QuickReply(7, "Adresse", "12 rue des Lilas\n75000 Paris\tbât. B \\ code 1234"),
            QuickReply(2, "Ok", "Ok 👍"),
        )
        assertEquals(list, QuickReplies.decode(QuickReplies.encode(list)))
    }

    @Test fun firstOpeningGivesStartersButAnEmptiedListStaysEmpty() {
        assertEquals(QuickReplies.starters, QuickReplies.decode(null))
        assertTrue(QuickReplies.decode(QuickReplies.encode(emptyList())).isEmpty())
        assertEquals(listOf(4L), QuickReplies.decode("abc\tx\ty\n4\tSalut\tSalut !\n4\tDouble\tDouble\n5\tVide\t ").map { it.id })
    }

    @Test fun startersAreShortAndUseTheVariables() {
        QuickReplies.starters.forEach { assertTrue(it.title, it.text.length < 120 && it.title.length <= QuickReplies.MAX_TITLE) }
        assertTrue(QuickReplies.starters.any { QuickVars.asked(it.text).isNotEmpty() })
        assertTrue(QuickReplies.starters.any { QuickVars.of(it.text).any { v -> v.auto } })
        assertEquals(QuickReplies.starters.size, QuickReplies.starters.map { it.id }.toSet().size)
    }
}
