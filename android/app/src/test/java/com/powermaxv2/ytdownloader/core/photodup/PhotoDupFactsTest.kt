package com.powermaxv2.ytdownloader.core.photodup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDupFactsTest {
    private fun photo(id: Long, size: Long = 2_000_000, taken: Long = id * 1_000, w: Int = 4000, h: Int = 3000, album: String = "Camera") =
        Photo(id, "IMG_$id.jpg", size, taken, w, h, album)

    /** A [w] × [h] image: a left-to-right grey ramp with a bright square, slightly moved by [shift]. */
    private fun picture(w: Int, h: Int, shift: Int = 0, noise: Int = 0): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        var v = 40 + 160 * x / w
        if (x in (w / 4 + shift) until (w / 2 + shift) && y in h / 3 until 2 * h / 3) v = 250
        v = (v + if (noise > 0) (i * 31 % (2 * noise + 1)) - noise else 0).coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test fun samePictureAtAnySizeHasTheSameFingerprint() {
        val big = PhotoDupFacts.dHash(picture(360, 320), 360, 320)
        val small = PhotoDupFacts.dHash(picture(90, 80), 90, 80)
        assertTrue(PhotoDupFacts.distance(big, small) <= 2)
        // A recompressed copy (a little noise) is still the same picture.
        val noisy = PhotoDupFacts.dHash(picture(180, 160, noise = 6), 180, 160)
        assertTrue(PhotoDupFacts.distance(big, noisy) <= PhotoDupFacts.SIMILAR_MAX)
    }

    @Test fun differentPicturesAreFarApart() {
        val ramp = PhotoDupFacts.dHash(picture(180, 160), 180, 160)
        // Mirrored ramp: dark on the right.
        val mirrored = picture(180, 160).let { px -> IntArray(px.size) { i -> px[(i / 180) * 180 + 179 - i % 180] } }
        val other = PhotoDupFacts.dHash(mirrored, 180, 160)
        assertTrue(PhotoDupFacts.distance(ramp, other) > 20)
    }

    @Test fun cellsAverageTheirPixels() {
        val white = IntArray(18 * 16) { 0xFFFFFFFF.toInt() }
        val cells = PhotoDupFacts.greyCells(white, 18, 16)
        assertEquals(PhotoDupFacts.HASH_W * PhotoDupFacts.HASH_H, cells.size)
        assertTrue(cells.all { it == 255 })
        // A flat picture has nothing to compare.
        assertTrue(PhotoDupFacts.flat(PhotoDupFacts.dHash(cells)))
        assertFalse(PhotoDupFacts.flat(PhotoDupFacts.dHash(picture(90, 80), 90, 80)))
    }

    @Test fun distanceCountsBits() {
        assertEquals(0, PhotoDupFacts.distance(0x0FL, 0x0FL))
        assertEquals(4, PhotoDupFacts.distance(0x0FL, 0x00L))
        assertEquals(64, PhotoDupFacts.distance(0L, -1L))
    }

    @Test fun onlySameSizePhotosAreHashed() {
        val groups = PhotoDupFacts.sameSize(listOf(photo(1, 5_000_000), photo(2, 5_000_000), photo(3, 6_000_000), photo(4, 100), photo(5, 100)))
        assertEquals(listOf(setOf(1L, 2L)), groups.map { g -> g.map { it.id }.toSet() })
    }

    @Test fun exactCopiesKeepTheFirstSaved() {
        val photos = listOf(photo(1, taken = 5_000), photo(2, taken = 1_000, album = "WhatsApp Images"), photo(3, taken = 9_000, album = "Download"))
        val groups = PhotoDupFacts.groups(photos, mapOf(1L to "A", 2L to "A", 3L to "A"), emptyMap())
        assertEquals(1, groups.size)
        assertEquals(DupKind.EXACT, groups[0].kind)
        assertEquals(2L, groups[0].keep.id)
        assertEquals(listOf(1L, 3L), groups[0].others.map { it.id })
        assertEquals(4_000_000L, groups[0].wasted)
        assertEquals(setOf(1L, 3L), PhotoDupFacts.defaultSelection(groups))
        assertEquals("La plus ancienne", PhotoDupFacts.keepReason(groups[0]))
    }

    @Test fun lookAlikesKeepTheSharpestAndAreNotPreChecked() {
        val full = photo(1, size = 3_000_000, w = 4000, h = 3000)
        val whatsapp = photo(2, size = 300_000, w = 1600, h = 1200, album = "WhatsApp Images")
        val unrelated = photo(3)
        val prints = mapOf(1L to 0x00FF_00FF_00FF_00FFL, 2L to 0x00FF_00FF_00FF_00F0L, 3L to 0x7777_1111_7777_1111L)
        val groups = PhotoDupFacts.groups(listOf(whatsapp, full, unrelated), emptyMap(), prints)
        assertEquals(1, groups.size)
        assertEquals(DupKind.SIMILAR, groups[0].kind)
        assertEquals(1L, groups[0].keep.id)
        assertEquals(listOf(2L), groups[0].others.map { it.id })
        assertEquals("La plus nette", PhotoDupFacts.keepReason(groups[0]))
        assertTrue(PhotoDupFacts.defaultSelection(groups).isEmpty())
    }

    @Test fun aCropIsNotALookAlike() {
        val photoA = photo(1, w = 4000, h = 3000)
        val square = photo(2, w = 3000, h = 3000)
        val turned = photo(3, w = 3000, h = 4000)
        assertFalse(PhotoDupFacts.sameShape(photoA, square))
        assertTrue(PhotoDupFacts.sameShape(photoA, turned))
        val prints = mapOf(1L to 0x1234_5678_9ABC_DEF0L, 2L to 0x1234_5678_9ABC_DEF0L)
        assertTrue(PhotoDupFacts.groups(listOf(photoA, square), emptyMap(), prints).isEmpty())
    }

    @Test fun anExactCopyIsNotCountedTwice() {
        val a = photo(1, taken = 1_000)
        val b = photo(2, taken = 2_000)
        val small = photo(3, size = 400_000, w = 1600, h = 1200)
        val print = 0x0F0F_0F0F_0F0F_0F0FL
        val groups = PhotoDupFacts.groups(listOf(a, b, small), mapOf(1L to "X", 2L to "X"), mapOf(1L to print, 2L to print, 3L to print))
        assertEquals(listOf(DupKind.EXACT, DupKind.SIMILAR), groups.map { it.kind })
        assertEquals(listOf(2L), groups[0].others.map { it.id })
        assertEquals(setOf(1L, 3L), groups[1].photos.map { it.id }.toSet())
    }

    @Test fun groupsDoNotChainForever() {
        // 1 ~ 2 and 2 ~ 3, but 1 and 3 are 8 bits apart: 3 is not glued to 1's group.
        val prints = mapOf(1L to 0L, 2L to 0xFL, 3L to 0xFFL)
        val photos = listOf(photo(1, taken = 1), photo(2, taken = 2), photo(3, taken = 3))
        val groups = PhotoDupFacts.groups(photos, emptyMap(), prints.mapValues { it.value or 0x5555_0000_0000_0000L })
        assertEquals(listOf(setOf(1L, 2L)), groups.map { g -> g.photos.map { it.id }.toSet() })
    }

    @Test fun wording() {
        assertEquals("Coche les photos à supprimer", PhotoDupFacts.deleteLabel(0, 0))
        assertEquals("Supprimer 1 photo · 2.0 Mo".replace('.', ','), PhotoDupFacts.deleteLabel(2_000_000, 1))
        assertEquals("4032 × 3024", PhotoDupFacts.dimensions(Photo(1, "a", 1, 0, 4032, 3024, "Camera")))
        assertTrue(PhotoDupFacts.confirmText(3, 6_000_000, toTrash = true, keptGroups = 1).contains("corbeille"))
        assertTrue(PhotoDupFacts.confirmText(1, 6_000_000, toTrash = false, keptGroups = 0).startsWith("Cette photo sera supprimée"))
        val lines = PhotoDupFacts.doneLines(listOf(photo(1), photo(2, album = "WhatsApp Images")), failed = 1, toTrash = false, before = 40_000_000)
        assertTrue(lines.any { it.startsWith("La galerie passe de 40,0 Mo à 36,0 Mo (10 %") })
        assertTrue(lines.last() == "1 photo n'a pas pu être supprimée")
    }

    @Test fun aWholeGroupCheckedIsSpotted() {
        val groups = PhotoDupFacts.groups(listOf(photo(1), photo(2)), mapOf(1L to "A", 2L to "A"), emptyMap())
        assertFalse(PhotoDupFacts.wholeGroupChecked(groups, setOf(2L)))
        assertTrue(PhotoDupFacts.wholeGroupChecked(groups, setOf(1L, 2L)))
    }
}
