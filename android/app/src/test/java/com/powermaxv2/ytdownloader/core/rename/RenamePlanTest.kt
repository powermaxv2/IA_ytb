package com.powermaxv2.ytdownloader.core.rename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RenamePlanTest {
    private val paris = TimeZone.getTimeZone("Europe/Paris")

    // 2026-10-13 18:42:07 in Paris (UTC+2).
    private val oct13 = 1_791_909_727_000L

    private fun file(name: String, taken: Long? = null, modified: Long? = null) = RenameSource("k:$name", name, taken, modified)

    private fun names(files: List<RenameSource>, rule: RenameRule, taken: Set<String> = emptySet()) =
        RenamePlan.preview(files, rule, taken, paris).rows.map { it.newName }

    @Test fun textAndPaddedCounterKeepEachExtension() {
        val files = listOf(file("IMG_1.jpg"), file("IMG_2.HEIC"), file("clip.mp4"))
        val rule = RenameRule(listOf(NameBlock.Text("Vacances_"), NameBlock.Counter()))
        assertEquals(listOf("Vacances_001.jpg", "Vacances_002.HEIC", "Vacances_003.mp4"), names(files, rule))
    }

    @Test fun counterStartStepAndDigits() {
        val block = NameBlock.Counter(start = 10, step = 5, digits = 2)
        assertEquals("10", RenamePlan.counter(block, 0))
        assertEquals("20", RenamePlan.counter(block, 2))
        assertEquals("1000", RenamePlan.counter(NameBlock.Counter(digits = 3), 999))
        assertEquals("000001", RenamePlan.counter(NameBlock.Counter(digits = 40), 0))
        assertEquals("-1", RenamePlan.counter(NameBlock.Counter(start = 0, step = -1, digits = 1), 1))
    }

    @Test fun datesUsePhotoDateFirstThenFileDate() {
        assertEquals("2026-10-13", RenamePlan.formatDate(oct13, NameDateFormat.DAY, paris))
        assertEquals("20261013", RenamePlan.formatDate(oct13, NameDateFormat.COMPACT, paris))
        assertEquals("20261013_184207", RenamePlan.formatDate(oct13, NameDateFormat.STAMP, paris))
        assertEquals("13-10-2026", RenamePlan.formatDate(oct13, NameDateFormat.FRENCH, paris))
        assertEquals("2026-10", RenamePlan.formatDate(oct13, NameDateFormat.MONTH, paris))
        val dayBefore = oct13 - 86_400_000L
        val rule = RenameRule(listOf(NameBlock.Date(), NameBlock.Text("_"), NameBlock.Counter(digits = 2)))
        val files = listOf(file("a.jpg", taken = oct13, modified = dayBefore), file("b.pdf", modified = dayBefore), file("c.txt"))
        assertEquals(listOf("2026-10-13_01.jpg", "2026-10-12_02.pdf", "sans-date_03.txt"), names(files, rule))
    }

    @Test fun findReplaceThenCase() {
        val files = listOf(file("img_0042 copie.JPG"), file("IMG_0043.jpg"))
        val replace = RenameRule(find = "IMG_", replace = "Photo ", ignoreCase = true)
        assertEquals(listOf("Photo 0042 copie.JPG", "Photo 0043.jpg"), names(files, replace))
        val exact = RenameRule(find = "IMG_", replace = "", ignoreCase = false)
        assertEquals(listOf("img_0042 copie.JPG", "0043.jpg"), names(files, exact))
        val title = RenameRule(find = "_", replace = " ", case = NameCase.TITLE)
        assertEquals(listOf("Img 0042 Copie.JPG", "Img 0043.jpg"), names(files, title))
        assertEquals("ÉTÉ À ROME", RenamePlan.applyCase("été à Rome", NameCase.UPPER))
        assertEquals("été à rome", RenamePlan.applyCase("ÉTÉ À ROME", NameCase.LOWER))
    }

    @Test fun ordersByNameNaturallyOrByDate() {
        val files = listOf(file("IMG_10.jpg", taken = 3), file("IMG_2.jpg", taken = 1), file("img_1.jpg"))
        val byName = RenamePlan.sort(files, RenameOrder.NAME).map { it.name }
        assertEquals(listOf("img_1.jpg", "IMG_2.jpg", "IMG_10.jpg"), byName)
        val byDate = RenamePlan.sort(files, RenameOrder.DATE).map { it.name }
        assertEquals(listOf("IMG_2.jpg", "IMG_10.jpg", "img_1.jpg"), byDate)
        val rule = RenameRule(listOf(NameBlock.Counter(digits = 1)), order = RenameOrder.NAME)
        val rows = RenamePlan.preview(files, rule, zone = paris).rows
        assertEquals(listOf("img_1.jpg" to "1.jpg", "IMG_2.jpg" to "2.jpg", "IMG_10.jpg" to "3.jpg"), rows.map { it.source.name to it.newName })
    }

    @Test fun clashesAreCaughtIgnoringCase() {
        val files = listOf(file("a.jpg"), file("b.JPG"), file("c.png"))
        val rule = RenameRule(listOf(NameBlock.Text("photo")))
        val preview = RenamePlan.preview(files, rule, zone = paris)
        assertEquals(listOf(RowStatus.CLASH, RowStatus.CLASH, RowStatus.READY), preview.rows.map { it.status })
        assertEquals(2, preview.clashes)
        assertEquals(listOf("photo.png"), preview.ready.map { it.newName })
    }

    @Test fun namesOfOtherFilesInTheFolderBlock() {
        val files = listOf(file("a.jpg"))
        val preview = RenamePlan.preview(files, RenameRule(listOf(NameBlock.Text("Photo"))), taken = setOf("photo.jpg"), zone = paris)
        assertEquals(RowStatus.CLASH, preview.rows.single().status)
    }

    @Test fun aKeptNameStillBlocksOthers() {
        // "b" keeps its name (SAME), so "a" cannot become "b".
        val files = listOf(file("a.jpg"), file("b.jpg"))
        val rule = RenameRule(find = "a", replace = "b")
        val rows = RenamePlan.preview(files, rule, zone = paris).rows
        assertEquals(listOf(RowStatus.CLASH, RowStatus.SAME), rows.map { it.status })
    }

    @Test fun swapsAreAllowedAndGoThroughATemporaryName() {
        val files = listOf(file("a.jpg"), file("b.jpg"), file("c.jpg"))
        // a → b, b → c, c → d: every name but "d" is still held when its turn comes.
        val rule = RenameRule(listOf(NameBlock.Counter(start = 2, digits = 1)))
        val preview = RenamePlan.preview(listOf(file("1.jpg"), file("2.jpg"), file("3.jpg")), rule, zone = paris)
        assertEquals(listOf("2.jpg", "3.jpg", "4.jpg"), preview.rows.map { it.newName })
        assertTrue(preview.rows.all { it.status == RowStatus.READY })
        assertEquals(setOf("k:1.jpg", "k:2.jpg"), RenamePlan.needsDetour(preview.rows))
        // A change of case only also takes the detour (the phone's storage ignores case).
        val lower = RenamePlan.preview(files.map { it.copy(name = it.name.uppercase()) }, RenameRule(case = NameCase.LOWER), zone = paris)
        assertEquals(3, RenamePlan.needsDetour(lower.rows).size)
    }

    @Test fun unchangedEmptyAndForbiddenCharacters() {
        val files = listOf(file("garde.jpg"), file("vide.jpg"), file("x.txt"))
        val same = RenamePlan.preview(files.take(1), RenameRule(), zone = paris)
        assertEquals(RowStatus.SAME, same.rows.single().status)
        assertEquals(1, same.unchanged)
        val empty = RenamePlan.preview(files.drop(1).take(1), RenameRule(find = "vide", replace = ""), zone = paris)
        assertEquals(RowStatus.EMPTY, empty.rows.single().status)
        assertEquals("vide.jpg", empty.rows.single().newName)
        val slashes = names(files.drop(2), RenameRule(listOf(NameBlock.Text("..a/b:c "), NameBlock.Original)))
        assertEquals(listOf("a_b_c x.txt"), slashes)
    }

    @Test fun filesWithoutExtensionAndDottedNames() {
        val files = listOf(file("README"), file("archive.tar.gz"), file(".bashrc"))
        val rule = RenameRule(listOf(NameBlock.Original, NameBlock.Text("-v2")))
        assertEquals(listOf("README-v2", "archive.tar-v2.gz", "bashrc-v2"), names(files, rule))
    }

    @Test fun copiesAreCountedForFilesThatCannotBeRenamed() {
        val files = listOf(file("a.jpg").copy(renamable = false), file("b.jpg"))
        val preview = RenamePlan.preview(files, RenameRule(listOf(NameBlock.Original, NameBlock.Text("_ok"))), zone = paris)
        assertEquals(1, preview.copies)
        assertEquals(2, preview.ready.size)
    }

    @Test fun presetsAllProduceNames() {
        val files = listOf(file("IMG_0001.jpg", taken = oct13), file("IMG_0002.jpg", taken = oct13))
        RenameRule.presets.forEach { (label, rule) ->
            val preview = RenamePlan.preview(files, rule, zone = paris)
            assertEquals(label, 2, preview.ready.size)
        }
    }

    @Test fun exifDates() {
        assertEquals(oct13, RenamePlan.parseExifDate("2026:10:13 18:42:07", paris))
        assertEquals(oct13, RenamePlan.parseExifDate("2026-10-13T18:42:07", paris))
        assertEquals(null, RenamePlan.parseExifDate("0000:00:00 00:00:00", paris))
        assertEquals(null, RenamePlan.parseExifDate("    ", paris))
        assertEquals(null, RenamePlan.parseExifDate(null, paris))
    }

    @Test fun naturalOrder() {
        val sorted = listOf("b10", "B2", "a", "b02", "b1").sortedWith(NaturalOrder)
        assertEquals(listOf("a", "b1", "B2", "b02", "b10"), sorted)
    }
}
