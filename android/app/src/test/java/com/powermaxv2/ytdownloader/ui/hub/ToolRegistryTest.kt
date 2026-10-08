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
        assertEquals(listOf(ToolRegistry.DOWNLOADER, ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS), ids.take(4))
        // Stable ids: intents (Partager → Gulp) and saved state use them.
        assertEquals("converter", ToolRegistry.CONVERTER)
        assertEquals("preview", ToolRegistry.PREVIEW)
        assertEquals("chat-stats", ToolRegistry.CHAT_STATS)
        listOf(ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS).forEach { id ->
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
    }

    @Test fun oneSlotStaysComingSoon() {
        assertEquals(listOf("soon-5"), ToolRegistry.upcoming.map { it.id })
        assertEquals(4, ToolRegistry.available.size)
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
}
