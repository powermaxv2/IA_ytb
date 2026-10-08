package com.powermaxv2.ytdownloader.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WhatsAppChatTest {
    @Test fun androidFrenchExport() {
        val text = """
            15/09/2026 18:02 - Les messages et les appels sont chiffrés de bout en bout.
            15/09/2026 21:02 - Hugo: Quelqu'un est dispo ce soir ?
            15/09/2026 21:02 - Inès: Moi !!
            sur deux lignes
            15/09/2026 21:06 - ~ Camille: Grave, 21h30 ?
        """.trimIndent()
        val m = WhatsAppChat.parse(text)!!
        assertEquals(listOf("Hugo", "Inès", "Camille"), m.map { it.author })
        assertEquals("Moi !!\nsur deux lignes", m[1].text)
        assertEquals(21, m[0].hour)
        assertEquals(4 * 60L, m[2].time - m[1].time)
    }

    @Test fun iphoneExportWithSecondsAndInvisibleMarks() {
        val text = "[15/09/2026 21:02:10] Hugo: Salut\n‎[15/09/2026 21:02:28] ‪Inès‬: Yo‎"
        val m = WhatsAppChat.parse(text)!!
        assertEquals(2, m.size)
        assertEquals(18L, m[1].time - m[0].time)
        assertEquals("Yo", m[1].text)
    }

    @Test fun usExportWithAmPm() {
        val text = "9/15/26, 11:58 PM - Hugo: late\n9/16/26, 12:03 AM - Inès: still up"
        val m = WhatsAppChat.parse(text)!!
        assertEquals(23, m[0].hour)
        assertEquals(0, m[1].hour)
        assertEquals(5 * 60L, m[1].time - m[0].time)
    }

    @Test fun yearFirst() {
        val m = WhatsAppChat.parse("2026-09-15 08:00 - A: a\n2026-09-15 08:01 - B: b")!!
        assertEquals(60L, m[1].time - m[0].time)
    }

    @Test fun notAnExport() {
        assertNull(WhatsAppChat.parse("bonjour\nceci n'est pas une discussion"))
        assertEquals(ChatExport.NOT_WHATSAPP, (ChatExport.analyse("rien", "x.txt") as ChatPhase.Failed).message)
    }

    @Test fun media() {
        assertTrue(WhatsAppChat.isMedia("<Médias omis>"))
        assertTrue(WhatsAppChat.isMedia("Ce message a été supprimé"))
        assertTrue(WhatsAppChat.isMedia("IMG-001.jpg (fichier joint)"))
        assertTrue(!WhatsAppChat.isMedia("Une photo de vacances"))
    }

    @Test fun niceNames() {
        assertEquals("Coloc", WhatsAppChat.niceName("WhatsApp Chat with Coloc.txt"))
        assertEquals("Famille", WhatsAppChat.niceName("Discussion WhatsApp avec Famille.zip"))
        assertEquals("Discussion", WhatsAppChat.niceName(null))
    }

    @Test fun repliesSkipSameAuthorAndLongGaps() {
        val text = """
            01/01/2026 10:00 - A: 1
            01/01/2026 10:01 - B: 2
            01/01/2026 10:02 - B: 3
            01/01/2026 10:05 - A: 4
            01/01/2026 20:05 - B: 5
        """.trimIndent()
        val stats = ChatStats.of(WhatsAppChat.parse(text)!!)
        val r6 = stats.replies(Gap.H6)
        assertEquals(listOf(60L, 180L), r6.all)
        val never = stats.replies(Gap.NEVER)
        assertEquals(3, never.all.size)
        assertEquals(listOf("A", "B"), stats.people.map { it.name }.sorted())
    }

    @Test fun sampleMatchesTheOriginalApp() {
        // Same seeded discussion as the original app: Inès answers fastest, Hugo slowest.
        val stats = ChatSample.stats
        val replies = stats.replies(Gap.DEFAULT)
        assertEquals(listOf("Inès", "Camille", "Théo"), replies.podium.map { it.person.name })
        assertEquals("Hugo", replies.rows.last().person.name)
        assertEquals(4, stats.people.size)
        assertTrue(stats.refrains.first().label in listOf("Ok", "Mdr"))
        assertNotNull(stats.emojis.firstOrNull())
        assertEquals(23, stats.days) // 15 September to 7 October, both included
    }

    @Test fun refrainsIgnoreCaseAndFinalPunctuation() {
        val text = "01/01/2026 10:00 - A: Ok !\n01/01/2026 10:01 - B: ok\n01/01/2026 10:02 - A: <Médias omis>\n01/01/2026 10:03 - B: <Médias omis>"
        val stats = ChatStats.of(WhatsAppChat.parse(text)!!)
        assertEquals(listOf(Refrain("Ok", 2)), stats.refrains)
        assertEquals(2, stats.mediaCount)
    }

    @Test fun emojis() {
        assertEquals(listOf("😂", "😂", "👍", "❤"), ChatStats.emojisOf("😂😂 top 👍🏽 ❤️"))
    }

    @Test fun durations() {
        assertEquals("18 s", duration(18.0))
        assertEquals("4 min 05", duration(245.0))
        assertEquals("4 min", duration(240.0))
        assertEquals("1 h", duration(3_599.6))
        assertEquals("2 h 30", duration(9_000.0))
        assertEquals("2 h", duration(7_199.0))
        assertEquals("1,5 j", duration(129_600.0))
        assertEquals("—", duration(null as Double?))
        assertEquals("42 %", percent(0.42))
    }

    @Test fun zipExport() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("notes.txt")); zip.write("autre".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("_chat.txt")); zip.write("01/01/2026 10:00 - A: a\n01/01/2026 10:01 - B: b".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        val text = ChatExport.readText(ByteArrayInputStream(bytes))
        assertTrue(text.startsWith("01/01/2026"))
    }

    @Test fun zipWithoutText() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("photo.jpg")); zip.write(1); zip.closeEntry() }
        }.toByteArray()
        val error = runCatching { ChatExport.readText(ByteArrayInputStream(bytes)) }.exceptionOrNull()
        assertEquals(ChatExport.NO_TXT_IN_ZIP, error?.message)
    }

    @Test fun cappedText() {
        val error = runCatching { ChatExport.readText(ByteArrayInputStream(ByteArray(2_000)), maxBytes = 1_000) }.exceptionOrNull()
        assertEquals(ChatExport.TOO_BIG, error?.message)
    }
}
