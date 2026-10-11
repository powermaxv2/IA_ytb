package com.powermaxv2.ytdownloader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test fun downloaderIsTheFirstToolAndOpens() {
        val first = ToolRegistry.tools.first()
        assertEquals(ToolRegistry.DOWNLOADER, first.id)
        assertTrue(first.available)
        assertNotNull(ToolRegistry.find(ToolRegistry.DOWNLOADER))
    }

    @Test fun fileToolsFollowTheDownloader() {
        val ids = ToolRegistry.tools.map { it.id }
        assertEquals(
            listOf(ToolRegistry.DOWNLOADER, ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS, ToolRegistry.COMPRESSOR, ToolRegistry.QR_CODE, ToolRegistry.SCANNER, ToolRegistry.TRIMMER, ToolRegistry.WIPER, ToolRegistry.PDF_JOIN, ToolRegistry.VOICE_TEXT, ToolRegistry.CLEANER),
            ids.take(12),
        )
        // Stable ids: intents (Partager → Gulp) and saved state use them.
        assertEquals("converter", ToolRegistry.CONVERTER)
        assertEquals("preview", ToolRegistry.PREVIEW)
        assertEquals("chat-stats", ToolRegistry.CHAT_STATS)
        assertEquals("compressor", ToolRegistry.COMPRESSOR)
        assertEquals("qr-code", ToolRegistry.QR_CODE)
        assertEquals("scanner", ToolRegistry.SCANNER)
        assertEquals("trimmer", ToolRegistry.TRIMMER)
        assertEquals("wiper", ToolRegistry.WIPER)
        assertEquals("pdf-join", ToolRegistry.PDF_JOIN)
        assertEquals("voice-text", ToolRegistry.VOICE_TEXT)
        assertEquals("whatsapp-cleaner", ToolRegistry.CLEANER)
        listOf(ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS, ToolRegistry.COMPRESSOR, ToolRegistry.QR_CODE, ToolRegistry.SCANNER, ToolRegistry.TRIMMER, ToolRegistry.WIPER, ToolRegistry.PDF_JOIN, ToolRegistry.VOICE_TEXT, ToolRegistry.CLEANER).forEach { id ->
            val tool = ToolRegistry.find(id)
            assertNotNull(id, tool)
            tool!!
            assertTrue(tool.available)
            assertTrue(tool.platforms.isEmpty())
            assertNotNull("$id needs an icon for its home card", tool.icon)
            assertEquals(3, tool.tags.size)
        }
        assertEquals("Convertisseur", ToolRegistry.find(ToolRegistry.CONVERTER)?.name)
        assertEquals("Aperçu", ToolRegistry.find(ToolRegistry.PREVIEW)?.name)
        assertEquals("Vu, répondu", ToolRegistry.find(ToolRegistry.CHAT_STATS)?.name)
        assertEquals("Compresseur", ToolRegistry.find(ToolRegistry.COMPRESSOR)?.name)
        assertEquals("QR code", ToolRegistry.find(ToolRegistry.QR_CODE)?.name)
        assertEquals("Scanner", ToolRegistry.find(ToolRegistry.SCANNER)?.name)
        assertEquals("Découpeur", ToolRegistry.find(ToolRegistry.TRIMMER)?.name)
        assertEquals("Gomme", ToolRegistry.find(ToolRegistry.WIPER)?.name)
        assertEquals("Assembleur PDF", ToolRegistry.find(ToolRegistry.PDF_JOIN)?.name)
        assertEquals("Vocaux en texte", ToolRegistry.find(ToolRegistry.VOICE_TEXT)?.name)
        assertEquals("Nettoyeur WhatsApp", ToolRegistry.find(ToolRegistry.CLEANER)?.name)
    }

    @Test fun oneSlotStaysComingSoon() {
        assertEquals(listOf("soon-13"), ToolRegistry.upcoming.map { it.id })
        assertEquals(12, ToolRegistry.available.size)
    }

    @Test fun honestWording() {
        ToolRegistry.tools.forEach { tool ->
            val text = (listOf(tool.name, tool.tagline) + tool.tags).joinToString(" ").lowercase()
            assertTrue(tool.id, "tous les formats" !in text)
        }
    }

    @Test fun comingSoonToolsCannotBeOpened() {
        assertTrue(ToolRegistry.upcoming.isNotEmpty())
        ToolRegistry.upcoming.forEach { assertNull(it.id, ToolRegistry.find(it.id)) }
    }

    @Test fun unknownIdsAreIgnored() {
        assertNull(ToolRegistry.find(null))
        assertNull(ToolRegistry.find("../downloader"))
    }

    @Test fun idsAreUnique() {
        val ids = ToolRegistry.tools.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun counts() {
        assertEquals("1 outil prêt", readyText(1))
        assertEquals("2 outils prêts", readyText(2))
        assertEquals("03", toolNumber(3))
    }

    @Test fun downloaderIsFeaturedUntilAToolIsUsed() {
        val ready = ToolRegistry.available
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, null).id)
        // A tool removed in a later version (or an "À venir" id) falls back to the downloader too.
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, "gone").id)
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, "soon-13").id)
    }

    @Test fun lastUsedToolIsFeatured() {
        val ready = ToolRegistry.available
        assertEquals(ToolRegistry.CHAT_STATS, featuredTool(ready, ToolRegistry.CHAT_STATS).id)
        assertEquals(ToolRegistry.PREVIEW, featuredTool(ready, ToolRegistry.PREVIEW).id)
    }
}
