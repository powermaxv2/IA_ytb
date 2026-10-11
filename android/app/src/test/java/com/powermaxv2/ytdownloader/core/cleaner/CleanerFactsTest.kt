package com.powermaxv2.ytdownloader.core.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerFactsTest {
    private fun file(id: String, size: Long, folder: String, name: String = "$id.jpg", modified: Long = 1_000L) =
        MediaFile(id, name, size, modified, folder)

    @Test fun kindsFollowWhatsAppFolders() {
        assertEquals(MediaType.IMAGES, CleanerFacts.typeOf("WhatsApp Images", "IMG-1.jpg"))
        assertEquals(MediaType.IMAGES, CleanerFacts.typeOf("WhatsApp Images/Sent", "IMG-1.jpg"))
        assertEquals(MediaType.IMAGES, CleanerFacts.typeOf("Media/WhatsApp Business Images/Private", "IMG-1.jpg"))
        assertEquals(MediaType.VIDEOS, CleanerFacts.typeOf("WhatsApp Video", "VID-1.mp4"))
        assertEquals(MediaType.VIDEOS, CleanerFacts.typeOf("WhatsApp Video Notes", "PTV-1.mp4"))
        assertEquals(MediaType.VOICE, CleanerFacts.typeOf("WhatsApp Voice Notes/202641", "PTT-1.opus"))
        assertEquals(MediaType.AUDIO, CleanerFacts.typeOf("WhatsApp Audio", "AUD-1.mp3"))
        assertEquals(MediaType.DOCUMENTS, CleanerFacts.typeOf("WhatsApp Documents", "facture.pdf"))
        assertEquals(MediaType.STICKERS, CleanerFacts.typeOf("WhatsApp Stickers", "STK-1.webp"))
        assertEquals(MediaType.GIFS, CleanerFacts.typeOf("WhatsApp Animated Gifs", "VID-1.mp4"))
        assertEquals(MediaType.STATUSES, CleanerFacts.typeOf(".Statuses", "abc.jpg"))
        // Unknown folder: the extension decides.
        assertEquals(MediaType.VOICE, CleanerFacts.typeOf("", "x.opus"))
        assertEquals(MediaType.VIDEOS, CleanerFacts.typeOf("Autre", "x.MP4"))
        assertEquals(MediaType.OTHER, CleanerFacts.typeOf("", "x.crypt14"))
    }

    @Test fun backupsAndHiddenFilesAreLeftAlone() {
        assertTrue(CleanerFacts.skippedFolder("Databases"))
        assertTrue(CleanerFacts.skippedFolder("Backups"))
        assertFalse(CleanerFacts.skippedFolder("WhatsApp Images"))
        assertFalse(CleanerFacts.skippedFolder(".Statuses"))
        assertTrue(CleanerFacts.ignored(".nomedia"))
        assertTrue(CleanerFacts.ignored(".trashed-1700000000-IMG.jpg"))
        assertFalse(CleanerFacts.ignored("IMG-20261010-WA0001.jpg"))
    }

    @Test fun sentFolderIsSpotted() {
        assertTrue(file("a", 1, "WhatsApp Images/Sent").sent)
        assertFalse(file("a", 1, "WhatsApp Images").sent)
    }

    @Test fun onlySameSizeFilesAreCompared() {
        val files = listOf(
            file("a", 5_000, "WhatsApp Images"),
            file("b", 5_000, "WhatsApp Images/Sent"),
            file("c", 7_000, "WhatsApp Images"),
            file("d", 10, "WhatsApp Images"),
            file("e", 10, "WhatsApp Images"),
        )
        val groups = CleanerFacts.sameSize(files)
        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups.first().map { it.id }.toSet())
    }

    @Test fun duplicatesKeepTheOldestReceivedCopy() {
        val files = listOf(
            file("sent", 5_000, "WhatsApp Images/Sent", modified = 100),
            file("new", 5_000, "WhatsApp Images", modified = 300),
            file("old", 5_000, "WhatsApp Images", modified = 200),
            file("other", 5_000, "WhatsApp Images", modified = 50),
        )
        val prints = mapOf("sent" to "X", "new" to "X", "old" to "X", "other" to "Y")
        val groups = CleanerFacts.duplicates(files, prints)
        assertEquals(1, groups.size)
        assertEquals("old", groups.first().keep.id)
        assertEquals(listOf("new", "sent"), groups.first().copies.map { it.id }.sorted())
        assertEquals(10_000, groups.first().wasted)
        assertEquals(setOf("new", "sent"), CleanerFacts.defaultSelection(groups))
    }

    @Test fun filesWithoutFingerprintAreNeverDuplicates() {
        val files = listOf(file("a", 5_000, "x"), file("b", 5_000, "x"))
        assertTrue(CleanerFacts.duplicates(files, mapOf("a" to "X")).isEmpty())
    }

    @Test fun biggestWasteComesFirst() {
        val files = listOf(
            file("a1", 2_000, "x"), file("a2", 2_000, "x"),
            file("b1", 9_000, "x"), file("b2", 9_000, "x"),
        )
        val groups = CleanerFacts.duplicates(files, mapOf("a1" to "A", "a2" to "A", "b1" to "B", "b2" to "B"))
        assertEquals(listOf(9_000L, 2_000L), groups.map { it.wasted })
    }

    @Test fun sharesAndBigFiles() {
        val files = listOf(
            file("v", 80_000_000, "WhatsApp Video", "v.mp4"),
            file("p1", 3_000_000, "WhatsApp Images"),
            file("p2", 6_000_000, "WhatsApp Images"),
        )
        val shares = CleanerFacts.shares(files)
        assertEquals(listOf(MediaType.VIDEOS, MediaType.IMAGES), shares.map { it.type })
        assertEquals(9_000_000, shares[1].bytes)
        assertEquals(2, shares[1].count)
        assertEquals(listOf("v", "p2"), CleanerFacts.bigFiles(files).map { it.id })
    }

    @Test fun olderThanUsesTheDate() {
        val day = 86_400_000L
        val now = 1_000 * day
        val files = listOf(
            file("old", 1, "WhatsApp Images", modified = now - 100 * day),
            file("new", 1, "WhatsApp Images", modified = now - 10 * day),
            file("unknown", 1, "WhatsApp Images", modified = 0),
        )
        assertEquals(listOf("old"), CleanerFacts.olderThan(files, MediaType.IMAGES, 90, now).map { it.id })
    }

    @Test fun wording() {
        assertEquals("Coche ce que tu veux supprimer", CleanerFacts.freeLabel(0, 0))
        assertEquals("Libérer 1,2 Go · 34 fichiers", CleanerFacts.freeLabel(1_200_000_000, 34))
        assertEquals("Libérer 500 o · 1 fichier", CleanerFacts.freeLabel(500, 1))
        assertTrue(CleanerFacts.confirmText(3, 3_000_000, 1).startsWith("Ces 3 fichiers seront supprimés"))
        assertTrue(CleanerFacts.confirmText(1, 3_000_000, 0).startsWith("Ce fichier sera supprimé"))
        val lines = CleanerFacts.doneLines(
            listOf(file("v", 400_000_000, "WhatsApp Video", "v.mp4"), file("p", 100_000_000, "WhatsApp Images")),
            failed = 1,
            before = 2_000_000_000,
        )
        assertEquals("Vidéos : 1 fichier, 400,0 Mo", lines[0])
        assertEquals("Photos : 1 fichier, 100,0 Mo", lines[1])
        assertEquals("Le dossier WhatsApp passe de 2,0 Go à 1,5 Go (25 % de moins)", lines[2])
        assertEquals("1 fichier n'a pas pu être supprimé", lines[3])
    }
}
