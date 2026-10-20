package com.powermaxv2.ytdownloader.core.collage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollagePlanTest {
    private fun overlaps(a: CollageCell, b: CollageCell) =
        a.left < b.right - 0.01f && b.left < a.right - 0.01f && a.top < b.bottom - 0.01f && b.top < a.bottom - 0.01f

    @Test fun sizesKeepTheShapeAndTheLongSide() {
        assertEquals(2048 to 2048, Collage.size(CollageFormat.SQUARE))
        assertEquals(1638 to 2048, Collage.size(CollageFormat.PORTRAIT))
        assertEquals(1152 to 2048, Collage.size(CollageFormat.STORY))
        assertEquals(2048 to 1152, Collage.size(CollageFormat.LANDSCAPE))
        assertEquals("1152 × 2048", Collage.sizeLabel(CollageFormat.STORY))
    }

    @Test fun rowsAreEvenWithTheShortOnesFirst() {
        assertEquals(listOf(1, 1), Collage.rows(2, 1000f, 2000f))
        assertEquals(listOf(2), Collage.rows(2, 1000f, 1000f))
        assertEquals(listOf(2, 2), Collage.rows(4, 1000f, 1000f))
        assertEquals(listOf(2, 3), Collage.rows(5, 1000f, 1000f))
        assertEquals(listOf(3, 3, 3), Collage.rows(9, 1000f, 1000f))
        // A wide picture gets more columns than a tall one.
        assertTrue(Collage.rows(6, 2000f, 1000f).size < Collage.rows(6, 1000f, 2000f).size)
        for (n in 1..9) assertEquals(n, Collage.rows(n, 1152f, 2048f).sum())
    }

    @Test fun everyLayoutGivesOneCellPerPhotoInsideThePicture() {
        for (layout in CollageLayout.entries) for (format in CollageFormat.entries) for (n in Collage.MIN_PHOTOS..Collage.MAX_PHOTOS) {
            val (w, h) = Collage.size(format, 1000)
            val cells = Collage.cells(n, CollageOptions(layout = layout, format = format), w, h)
            assertEquals("$layout $format $n", n, cells.size)
            assertEquals((0 until n).toList(), cells.map { it.index })
            cells.forEach { c ->
                assertTrue("$layout $format $n $c", c.width > 0f && c.height > 0f)
                if (layout != CollageLayout.SCATTER) {
                    assertTrue("$layout $format $n $c", c.left >= -0.01f && c.top >= -0.01f && c.right <= w + 0.01f && c.bottom <= h + 0.01f)
                    assertEquals(0f, c.rotation, 0f)
                }
            }
            if (layout != CollageLayout.SCATTER) {
                for (i in cells.indices) for (j in i + 1 until cells.size) assertFalse("$layout $format $n $i/$j", overlaps(cells[i], cells[j]))
            }
        }
    }

    @Test fun gridWithoutGapsFillsTheWholePicture() {
        for (n in 2..9) {
            val cells = Collage.cells(n, CollageOptions(spacing = CollageSpacing.NONE), 1000, 1000)
            assertEquals("$n", 1_000_000f, cells.sumOf { (it.width * it.height).toDouble() }.toFloat(), 5f)
        }
    }

    @Test fun gapsAreKeptBetweenAndAroundThePhotos() {
        val cells = Collage.cells(4, CollageOptions(spacing = CollageSpacing.WIDE), 1000, 1000)
        val gap = Collage.gap(CollageSpacing.WIDE, 1000, 1000)
        assertEquals(30f, gap, 0.001f)
        assertEquals(gap, cells[0].left, 0.01f)
        assertEquals(gap, cells[0].top, 0.01f)
        assertEquals(gap, cells[1].left - cells[0].right, 0.01f)
        assertEquals(gap, cells[2].top - cells[0].bottom, 0.01f)
        assertEquals(1000f - gap, cells[3].right, 0.01f)
    }

    @Test fun heroIsTheBiggestAndOnTopOfATallPicture() {
        val (w, h) = Collage.size(CollageFormat.STORY, 1000)
        val cells = Collage.cells(4, CollageOptions(layout = CollageLayout.HERO, format = CollageFormat.STORY), w, h)
        val big = cells[0]
        cells.drop(1).forEach {
            assertTrue(big.width * big.height > it.width * it.height)
            assertTrue(it.top >= big.bottom)
        }
        val wide = Collage.cells(4, CollageOptions(layout = CollageLayout.HERO, format = CollageFormat.LANDSCAPE), 1000, 562)
        wide.drop(1).forEach { assertTrue(it.left >= wide[0].right) }
    }

    @Test fun stripIsAColumnWhenTallAndARowWhenWide() {
        val column = Collage.cells(3, CollageOptions(layout = CollageLayout.STRIP), 1000, 1000)
        assertEquals(column[0].left, column[2].left, 0.01f)
        assertTrue(column[1].top > column[0].top)
        val row = Collage.cells(3, CollageOptions(layout = CollageLayout.STRIP, format = CollageFormat.LANDSCAPE), 1000, 562)
        assertEquals(row[0].top, row[2].top, 0.01f)
        assertTrue(row[1].left > row[0].left)
    }

    @Test fun scatterIsTiltedStableAndChangesWithTheSeed() {
        val a = Collage.cells(5, CollageOptions(layout = CollageLayout.SCATTER, seed = 3), 1000, 1000)
        val again = Collage.cells(5, CollageOptions(layout = CollageLayout.SCATTER, seed = 3), 1000, 1000)
        val other = Collage.cells(5, CollageOptions(layout = CollageLayout.SCATTER, seed = 4), 1000, 1000)
        assertEquals(a, again)
        assertNotEquals(a, other)
        a.forEach { assertTrue(kotlin.math.abs(it.rotation) in 2.5f..8.5f) }
        // Neighbours lean opposite ways.
        assertTrue(a[0].rotation * a[1].rotation < 0f)
        val print = Collage.printPhoto(a[0])
        assertTrue(print.top - a[0].top < a[0].bottom - print.bottom)
    }

    @Test fun cropKeepsTheCellShapeCentred() {
        val wide = Collage.crop(4000, 3000, 100f, 100f)
        assertEquals(CollageCrop(500, 0, 3500, 3000), wide)
        val tall = Collage.crop(3000, 4000, 200f, 100f)
        assertEquals(3000, tall.width)
        assertEquals(1500, tall.height)
        assertEquals(1250, tall.top)
        val same = Collage.crop(1600, 900, 160f, 90f)
        assertEquals(CollageCrop(0, 0, 1600, 900), same)
    }

    @Test fun orderMovesSwapsAndShuffles() {
        val order = listOf(0, 1, 2, 3)
        assertEquals(listOf(1, 2, 0, 3), Collage.move(order, 0, 2))
        assertEquals(listOf(3, 1, 2, 0), Collage.swap(order, 0, 3))
        assertEquals(order, Collage.swap(order, 1, 1))
        assertEquals(order, Collage.move(order, 0, 9))
        val shuffled = Collage.shuffle(order, 1)
        assertNotEquals(order, shuffled)
        assertEquals(order, shuffled.sorted())
        assertEquals(listOf(5), Collage.shuffle(listOf(5), 1))
    }

    @Test fun backgroundsAndRounding() {
        assertEquals(0xFF7F7F7F.toInt(), Collage.mix(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())))
        assertEquals(0xFF7F007F.toInt(), Collage.mix(listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())))
        assertEquals(0xFF202020.toInt(), Collage.mix(emptyList()))
        assertEquals(0xFFFFFFFF.toInt(), Collage.backgroundColor(CollageBackground.WHITE, 0))
        assertEquals(123, Collage.backgroundColor(CollageBackground.COLOR, 123))
        assertEquals(0f, Collage.radius(CollageOptions(rounded = false), 1000, 2000), 0f)
        assertEquals(28f, Collage.radius(CollageOptions(rounded = true), 1000, 2000), 0.01f)
    }

    @Test fun outputName() {
        assertEquals("collage-4-photos.jpg", Collage.outputName(4))
    }
}
