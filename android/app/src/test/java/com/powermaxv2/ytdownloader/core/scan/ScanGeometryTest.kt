package com.powermaxv2.ytdownloader.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGeometryTest {
    @Test fun fullPhotoIsAValidQuad() {
        assertTrue(Quad.FULL.isValid)
        assertEquals(1f, Quad.FULL.area, 1e-6f)
    }

    @Test fun crossedOrFlatQuadsAreRefused() {
        // Top corners swapped: the frame crosses itself.
        val crossed = Quad(Pt(1f, 0f), Pt(0f, 0f), Pt(1f, 1f), Pt(0f, 1f))
        assertFalse(crossed.isValid)
        val flat = Quad(Pt(0f, 0f), Pt(0.5f, 0f), Pt(1f, 0f), Pt(0.2f, 0f))
        assertFalse(flat.isValid)
        val tiny = Quad(Pt(0.5f, 0.5f), Pt(0.55f, 0.5f), Pt(0.55f, 0.55f), Pt(0.5f, 0.55f))
        assertFalse(tiny.isValid)
    }

    @Test fun movingACornerStaysInsideThePhoto() {
        val moved = Quad.FULL.with(2, Pt(1.4f, -0.2f))
        assertEquals(Pt(1f, 0f), moved.bottomRight)
        assertSame(Quad.FULL, Quad.FULL.with(7, Pt(0.5f, 0.5f)))
    }

    @Test fun orderedCornersAreClockwiseFromTopLeft() {
        val q = Quad.ordered(listOf(Pt(0.9f, 0.85f), Pt(0.1f, 0.1f), Pt(0.12f, 0.9f), Pt(0.88f, 0.08f)))
        assertEquals(Pt(0.1f, 0.1f), q.topLeft)
        assertEquals(Pt(0.88f, 0.08f), q.topRight)
        assertEquals(Pt(0.9f, 0.85f), q.bottomRight)
        assertEquals(Pt(0.12f, 0.9f), q.bottomLeft)
        assertTrue(q.isValid)
    }

    @Test fun nearestCornerWithinReach() {
        val q = Quad.FULL
        assertEquals(0, q.nearestCorner(Pt(0.03f, 0.02f), radius = 0.08f))
        assertEquals(2, q.nearestCorner(Pt(0.97f, 0.99f), radius = 0.08f))
        assertEquals(-1, q.nearestCorner(Pt(0.5f, 0.5f), radius = 0.08f))
        // Measured in pixels: on a wide photo a small x gap is a big distance.
        assertEquals(-1, q.nearestCorner(Pt(0.2f, 0f), radius = 0.05f, w = 1000f, h = 500f))
    }

    @Test fun outputKeepsTheLongestEdges() {
        // A page photographed from below: the top edge looks shorter than the bottom one.
        val q = Quad(Pt(0.3f, 0.1f), Pt(0.7f, 0.1f), Pt(0.9f, 0.9f), Pt(0.1f, 0.9f))
        val (w, h) = ScanGeometry.outputSize(q, 1000, 1000)
        assertEquals(800, w)
        assertTrue(h in 820..830)
    }

    @Test fun outputIsCapped() {
        val (w, h) = ScanGeometry.outputSize(Quad.FULL, 8000, 6000, maxSide = 3000)
        assertEquals(3000, w)
        assertEquals(2250, h)
    }

    @Test fun rotation() {
        assertEquals(0, ScanGeometry.normalize(360))
        assertEquals(270, ScanGeometry.normalize(-90))
        assertEquals(90, ScanGeometry.normalize(450))
        assertEquals(300 to 200, ScanGeometry.rotated(200, 300, 90))
        assertEquals(200 to 300, ScanGeometry.rotated(200, 300, 180))
    }
}
