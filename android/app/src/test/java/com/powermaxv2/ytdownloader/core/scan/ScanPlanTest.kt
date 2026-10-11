package com.powermaxv2.ytdownloader.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScanPlanTest {
    @Test fun portraitPictureFillsAnA4PageCentred() {
        val p = ScanPlan.place(1000, 1500, PageSize.A4)
        assertEquals(595.28f, p.pageWidth, 0.01f)
        assertEquals(841.89f, p.pageHeight, 0.01f)
        // Taller than A4: full height, centred across.
        assertEquals(841.89f, p.height, 0.01f)
        assertEquals((595.28f - p.width) / 2, p.x, 0.01f)
        assertEquals(0f, p.y, 0.01f)
    }

    @Test fun landscapePictureTurnsThePage() {
        val p = ScanPlan.place(1600, 1000, PageSize.LETTER)
        assertEquals(792f, p.pageWidth, 0.01f)
        assertEquals(612f, p.pageHeight, 0.01f)
        assertTrue(p.width <= p.pageWidth + 0.01f && p.height <= p.pageHeight + 0.01f)
    }

    @Test fun fitPageFollowsThePicture() {
        val p = ScanPlan.place(1000, 2000, PageSize.FIT)
        assertEquals(p.pageWidth * 2, p.pageHeight, 0.01f)
        assertEquals(0f, p.x)
        assertEquals(p.pageWidth, p.width)
    }

    @Test fun pixelsFollowTheDpiButNeverGrow() {
        val placement = ScanPlan.place(2480, 3508, PageSize.A4)
        val (w, h) = ScanPlan.targetPixels(2480, 3508, placement, ScanQuality.NORMAL)
        // 150 dpi across A4 ≈ 1240 px.
        assertTrue(w in 1235..1245)
        assertEquals(3508f / 2480f, h.toFloat() / w, 0.01f)
        val small = ScanPlan.targetPixels(400, 560, ScanPlan.place(400, 560, PageSize.A4), ScanQuality.HIGH)
        assertEquals(400 to 560, small)
    }

    @Test fun estimateGrowsWithPagesAndQuality() {
        val one = ScanPlan.estimate(1, PageSize.A4, ScanQuality.NORMAL)
        val three = ScanPlan.estimate(3, PageSize.A4, ScanQuality.NORMAL)
        assertTrue(three > one * 2)
        assertTrue(ScanPlan.estimate(3, PageSize.A4, ScanQuality.HIGH) > three)
        assertTrue(ScanPlan.estimate(3, PageSize.A4, ScanQuality.NORMAL, bwPages = 3) < three)
        // A normal A4 page weighs a few hundred kilobytes.
        assertTrue(one in 150_000L..700_000L)
    }

    @Test fun names() {
        val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 14, 32) }.time
        assertEquals("Scan 10-10-2026 14h32", ScanPlan.defaultName(date))
        assertEquals("Scan 10-10-2026 14h32.pdf", ScanPlan.fileName("  ", "Scan 10-10-2026 14h32"))
        assertEquals("Facture EDF.pdf", ScanPlan.fileName("Facture EDF.pdf", "x"))
        assertEquals("Rapport v1.2.pdf", ScanPlan.fileName("Rapport v1.2", "x"))
        assertEquals("_etc_passwd.pdf", ScanPlan.fileName("../etc/passwd", "x"))
    }

    @Test fun moveClampsAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), ScanOrder.move(list, 0, 1))
        assertEquals(listOf("a", "c", "b"), ScanOrder.move(list, 2, -1))
        assertEquals(list, ScanOrder.move(list, 0, -1))
        assertEquals(listOf("b", "c", "a"), ScanOrder.move(list, 0, 5))
        assertEquals(list, ScanOrder.move(list, 9, 1))
    }
}
