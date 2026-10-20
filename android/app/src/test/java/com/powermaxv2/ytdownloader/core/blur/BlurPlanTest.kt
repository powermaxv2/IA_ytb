package com.powermaxv2.ytdownloader.core.blur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurPlanTest {
    /** A picture whose every pixel is different: a detail that any hiding must wipe. */
    private fun noisy(w: Int, h: Int) = IntArray(w * h) { i ->
        val v = (i * 37 + (i / w) * 91) and 0xFF
        (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or ((v * 3) and 0xFF)
    }

    /** Detail left in [box]: summed difference of the red channel between each pixel and its right neighbour. */
    private fun detail(pixels: IntArray, w: Int, box: PixelBox): Long {
        var sum = 0L
        for (y in box.top until box.bottom) for (x in box.left until box.right - 1) {
            sum += kotlin.math.abs(((pixels[y * w + x] shr 16) and 0xFF) - ((pixels[y * w + x + 1] shr 16) and 0xFF))
        }
        return sum
    }

    @Test fun dragInAnyDirectionGivesTheSameZoneInsideThePhoto() {
        val a = BlurPlan.fromDrag(1, 0.2f, 0.3f, 0.6f, 0.5f, ZoneShape.RECT)
        val b = BlurPlan.fromDrag(1, 0.6f, 0.5f, 0.2f, 0.3f, ZoneShape.RECT)
        assertEquals(a, b)
        assertEquals(0.2f, a.x, 1e-6f)
        assertEquals(0.4f, a.w, 1e-6f)
        val out = BlurPlan.fromDrag(2, -0.5f, 0.9f, 0.3f, 1.7f, ZoneShape.OVAL)
        assertTrue(out.x >= 0f && out.right <= 1f && out.y >= 0f && out.bottom <= 1f)
        assertEquals(ZoneShape.OVAL, out.shape)
    }

    @Test fun aTapStillMakesAZoneOfTheSmallestSize() {
        val tap = BlurPlan.fromDrag(1, 0.99f, 0.5f, 0.99f, 0.5f, ZoneShape.RECT)
        assertEquals(BlurPlan.MIN_SIDE, tap.w, 1e-6f)
        assertEquals(BlurPlan.MIN_SIDE, tap.h, 1e-6f)
        assertEquals(1f, tap.right, 1e-6f)
    }

    @Test fun moveAndResizeStopAtTheEdges() {
        val zone = BlurZone(1, 0.5f, 0.5f, 0.3f, 0.2f)
        val moved = BlurPlan.move(zone, 0.9f, -0.9f)
        assertEquals(0.7f, moved.x, 1e-6f)
        assertEquals(0f, moved.y, 1e-6f)
        val big = BlurPlan.resize(zone, 1f, 1f)
        assertEquals(1f, big.right, 1e-6f)
        assertEquals(1f, big.bottom, 1e-6f)
        val small = BlurPlan.resize(zone, -1f, -1f)
        assertEquals(BlurPlan.MIN_SIDE, small.w, 1e-6f)
        assertEquals(0.5f, small.x, 1e-6f)
    }

    @Test fun theLastDrawnZoneIsOnTop() {
        val under = BlurZone(1, 0.1f, 0.1f, 0.5f, 0.5f)
        val over = BlurZone(2, 0.3f, 0.3f, 0.5f, 0.5f)
        assertEquals(2, BlurPlan.hit(listOf(under, over), 0.4f, 0.4f)?.id)
        assertEquals(1, BlurPlan.hit(listOf(under, over), 0.15f, 0.15f)?.id)
        assertNull(BlurPlan.hit(listOf(under, over), 0.95f, 0.05f))
        assertEquals(2, BlurPlan.hit(listOf(under, over), 0.82f, 0.5f, slop = 0.03f)?.id)
    }

    @Test fun faceZoneCoversTheFaceAroundTheEyes() {
        // Eyes 100 px apart, between them at (500, 400) on a 1000 × 1000 photo.
        val face = BlurPlan.faceZone(7, 500f, 400f, 100f, 1000, 1000)
        assertEquals(ZoneShape.OVAL, face.shape)
        assertEquals(0.5f, face.centerX, 1e-4f)
        assertTrue("under the eyes", face.centerY > 0.4f)
        assertTrue(face.contains(0.45f, 0.4f) && face.contains(0.55f, 0.4f))
        assertTrue("chin", face.contains(0.5f, 0.55f))
        assertEquals(0.26f, face.w, 1e-4f)
        // A face at the edge stays inside the photo.
        val edge = BlurPlan.faceZone(8, 5f, 5f, 100f, 1000, 1000)
        assertTrue(edge.x >= 0f && edge.y >= 0f)
    }

    @Test fun facesAlreadyHiddenAreNotAddedTwice() {
        val mine = BlurZone(1, 0.4f, 0.3f, 0.2f, 0.3f)
        val found = listOf(
            BlurZone(0, 0.45f, 0.35f, 0.1f, 0.1f, ZoneShape.OVAL),
            BlurZone(0, 0.05f, 0.05f, 0.1f, 0.1f, ZoneShape.OVAL),
            BlurZone(0, 0.06f, 0.06f, 0.1f, 0.1f, ZoneShape.OVAL),
        )
        val added = BlurPlan.newFaces(listOf(mine), found, nextId = 10)
        assertEquals(listOf(10), added.map { it.id })
        assertEquals(0.05f, added.single().x, 1e-6f)
    }

    @Test fun boxIsAlwaysAtLeastOnePixelInsideThePicture() {
        assertEquals(PixelBox(0, 0, 25, 10), BlurPlan.box(BlurZone(1, 0f, 0f, 0.25f, 0.1f), 100, 100))
        val last = BlurPlan.box(BlurZone(1, 1f, 1f, 0f, 0f), 100, 50)
        assertEquals(PixelBox(99, 49, 100, 50), last)
    }

    @Test fun strongerHidesWithBiggerCells() {
        val box = PixelBox(0, 0, 200, 200)
        val light = BlurPlan.cell(BlurStrength.LIGHT, 1000, 800, box)
        val medium = BlurPlan.cell(BlurStrength.MEDIUM, 1000, 800, box)
        val strong = BlurPlan.cell(BlurStrength.STRONG, 1000, 800, box)
        assertTrue(light < medium && medium < strong)
        // A zone is never more than [cells] squares across.
        assertEquals(50, strong)
        assertTrue(BlurPlan.cell(BlurStrength.LIGHT, 10, 10, PixelBox(0, 0, 2, 2)) >= 3)
    }

    @Test fun pixelsMakeSquaresOfTheMeanColour() {
        val w = 4
        val px = intArrayOf(
            0xFF000000.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
            0xFF0000FF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        )
        val out = BlurPlan.pixelate(px, w, 2, 2)
        assertEquals(0xFF00007F.toInt(), out[0])
        assertEquals(out[0], out[5])
        assertEquals(0xFFFFFFFF.toInt(), out[2])
        // Edges smaller than a cell are averaged on what is there.
        val odd = BlurPlan.pixelate(IntArray(3) { 0xFF102030.toInt() }, 3, 1, 2)
        assertTrue(odd.all { it == 0xFF102030.toInt() })
    }

    @Test fun everyEffectWipesTheDetailAndOnlyInsideTheZone() {
        val w = 120
        val h = 90
        val zone = BlurZone(1, 0.25f, 0.2f, 0.5f, 0.5f)
        val box = BlurPlan.box(zone, w, h)
        for (effect in BlurEffect.entries) {
            val original = noisy(w, h)
            val pixels = original.copyOf()
            BlurPlan.apply(pixels, w, h, listOf(zone), effect, BlurStrength.STRONG)
            val before = detail(original, w, box)
            val after = detail(pixels, w, box)
            assertTrue("$effect: $before → $after", after <= before / 10)
            // Outside the zone nothing moved.
            for (y in 0 until h) for (x in 0 until w) {
                val inside = x in box.left until box.right && y in box.top until box.bottom
                if (!inside) assertEquals("$effect at $x,$y", original[y * w + x], pixels[y * w + x])
            }
        }
        val bar = noisy(w, h)
        BlurPlan.apply(bar, w, h, listOf(zone), BlurEffect.BAR, BlurStrength.LIGHT)
        assertEquals(BlurPlan.BAR_COLOR, bar[(box.top + 3) * w + box.left + 3])
    }

    @Test fun anOvalLeavesItsCornersAlone() {
        val w = 40
        val h = 40
        val original = noisy(w, h)
        val region = original.copyOf()
        BlurPlan.hideRegion(region, w, h, BlurEffect.BAR, 8, ZoneShape.OVAL)
        assertEquals(original[0], region[0])
        assertEquals(original[w - 1], region[w - 1])
        assertEquals(BlurPlan.BAR_COLOR, region[20 * w + 20])
        assertFalse(region.contentEquals(original))
    }

    @Test fun wording() {
        assertEquals("Aucune zone", BlurPlan.zonesText(0))
        assertEquals("1 zone cachée", BlurPlan.zonesText(1))
        assertEquals("3 zones cachées", BlurPlan.zonesText(3))
        assertEquals("vacances-floute.jpg", BlurPlan.outputName("vacances.heic"))
        assertEquals("photo-floute.jpg", BlurPlan.outputName(""))
    }
}
