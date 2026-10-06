package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArchivesTest {
    @Test fun suspiciousNames() {
        assertNull(ZipNames.warning("docs/readme.txt"))
        assertNull(ZipNames.warning("a..b/c...txt"))
        assertNotNull(ZipNames.warning("../../etc/passwd"))
        assertNotNull(ZipNames.warning("docs/../../x"))
        assertNotNull(ZipNames.warning("/etc/passwd"))
        assertNotNull(ZipNames.warning("C:/Windows/evil.dll"))
        assertNotNull(ZipNames.warning("dir\\file.txt"))
        assertNotNull(ZipNames.warning("..\\x"))
        assertNotNull(ZipNames.warning("bad\u0000name"))
        assertEquals("bad\uFFFDname", ZipNames.display("bad\u0000name"))
    }

    @Test fun treeWithImpliedFoldersAndOrder() {
        val listing = ZipNames.tree(
            listOf(
                ZipEntryInfo("b.txt", 10, 5, false),
                ZipEntryInfo("src/main/App.kt", 300, 120, false),
                ZipEntryInfo("src/", 0, 0, true),
                ZipEntryInfo("README.md", 50, 30, false),
                ZipEntryInfo("src/build.gradle", 40, 20, false),
                ZipEntryInfo("../evil.sh", 7, 7, false),
            ),
        )
        assertEquals(
            listOf("../evil.sh", "src", "src/main", "src/main/App.kt", "src/build.gradle", "b.txt", "README.md"),
            listing.rows.map { it.path },
        )
        assertEquals(listOf(0, 0, 1, 2, 1, 0, 0), listing.rows.map { it.depth })
        assertEquals("main", listing.rows[2].label)
        assertEquals(1, listing.suspicious)
        assertEquals(10L + 300 + 50 + 40 + 7, listing.totalSize)
        assertEquals(5L + 120 + 30 + 20 + 7, listing.totalCompressed)
        assertEquals(5, listing.files)
        assertNotNull(listing.rows.first().warning)
    }
}
