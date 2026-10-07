package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    @Test(timeout = 10_000)
    fun deepAndLongNamesAreFlaggedNotExpanded() {
        val deep = "a/".repeat(100) + "x.txt"
        val long = "a/".repeat(32_767)
        val listing = ZipNames.tree(listOf(ZipEntryInfo(deep, 1, 1, false), ZipEntryInfo(long, 1, 1, false), ZipEntryInfo("ok.txt", 1, 1, false)))
        assertEquals(3, listing.rows.size)
        assertEquals(2, listing.suspicious)
        assertNotNull(listing.rows[0].warning)
        assertNotNull(listing.rows[1].warning)
        // Labels stay short.
        assertTrue(listing.rows.all { it.label.length <= ZipNames.MAX_LABEL_CHARS + 1 && it.path.length <= ZipNames.MAX_NAME_CHARS + 1 })
        // At the limit: still a tree.
        val atLimit = "d/".repeat(ZipNames.MAX_DEPTH) + "f"
        assertNull(ZipNames.warning(atLimit))
        assertEquals(ZipNames.MAX_DEPTH + 1, ZipNames.tree(listOf(ZipEntryInfo(atLimit, 1, 1, false))).rows.size)
    }

    @Test(timeout = 10_000)
    fun rowsAreCappedImpliedFoldersIncluded() {
        // 5000 entries, each in its own 60-level folder chain: 300k implied folders without a cap.
        val entries = (0 until ZipNames.MAX_ENTRIES).map { i -> ZipEntryInfo("r$i/" + "s/".repeat(59) + "f.txt", 10, 5, false) }
        val listing = ZipNames.tree(entries)
        assertTrue(listing.rows.size <= ZipNames.MAX_ROWS)
        assertTrue(listing.truncated)
        // Under the cap: not truncated.
        val small = ZipNames.tree(listOf(ZipEntryInfo("a/b/c.txt", 1, 1, false)))
        assertEquals(3, small.rows.size)
        assertEquals(false, small.truncated)
    }

    private fun zipOf(names: List<String>, comment: String? = null): File {
        val file = File.createTempFile("gulp-test", ".zip").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            comment?.let { zip.setComment(it) }
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) zip.write("bonjour ".repeat(500).toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test fun androidPathRefusalIsRecognised() {
        assertTrue(ZipNames.isPathRefusal("Invalid zip entry path: ../../evil.sh"))
        assertEquals(false, ZipNames.isPathRefusal("invalid END header (bad central directory offset)"))
        assertEquals(false, ZipNames.isPathRefusal(null))
    }

    /** The fallback for archives Android 14+ refuses: names read from the central directory, dangerous ones flagged. */
    @Test fun centralDirectoryListsAndFlagsDangerousNames() {
        val file = zipOf(listOf("docs/", "docs/a.txt", "../../Android/data/piege.sh", "/etc/passwd", "été.txt"), comment = "un commentaire")
        val read = readOrFail(ZipCentralDirectory.read(file))
        assertEquals(5, read.totalEntries)
        assertEquals(listOf("docs/", "docs/a.txt", "../../Android/data/piege.sh", "/etc/passwd", "été.txt"), read.entries.map { it.name })
        assertTrue(read.entries[0].isDirectory)
        assertEquals(4000L, read.entries[1].size)
        assertTrue(read.entries[1].compressedSize in 1 until 4000)
        val listing = ZipNames.tree(read.entries, read.totalEntries).copy(refusedByAndroid = true)
        assertEquals(2, listing.suspicious)
        assertTrue(listing.rows.take(2).all { it.warning != null })
        assertTrue(listing.refusedByAndroid)
    }

    @Test fun centralDirectoryKeepsTheEntryCap() {
        val file = zipOf((1..30).map { "f$it.txt" })
        val read = readOrFail(ZipCentralDirectory.read(file, maxEntries = 10))
        assertEquals(10, read.entries.size)
        assertEquals(30, read.totalEntries)
    }

    @Test fun centralDirectoryRefusesWhatIsNotAZip() {
        val text = File.createTempFile("gulp-test", ".zip").apply { deleteOnExit(); writeText("pas une archive ".repeat(100)) }
        assertNull(ZipCentralDirectory.read(text))
        val empty = File.createTempFile("gulp-test", ".zip").apply { deleteOnExit() }
        assertNull(ZipCentralDirectory.read(empty))
        // Central directory cut off: the end record points past the end of the file.
        val whole = zipOf(listOf("a.txt", "b.txt")).readBytes()
        val eocd = whole.size - 22
        val cut = File.createTempFile("gulp-test", ".zip").apply { deleteOnExit() }
        cut.writeBytes(whole.copyOfRange(0, 40) + whole.copyOfRange(eocd, whole.size))
        assertNull(ZipCentralDirectory.read(cut))
    }

    private fun readOrFail(value: ZipCentralDirectory.Result?): ZipCentralDirectory.Result {
        assertNotNull(value)
        return value!!
    }
}
