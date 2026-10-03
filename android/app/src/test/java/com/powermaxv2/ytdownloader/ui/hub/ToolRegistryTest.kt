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
