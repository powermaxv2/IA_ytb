package com.powermaxv2.ytdownloader.core.pdfjoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinPlanTest {
    private fun page(id: Long, w: Float = 595f, h: Float = 842f) = JoinPage(id, source = 0, index = id.toInt(), widthPt = w, heightPt = h)
    private val pages = (1L..4L).map { page(it) }

    @Test fun movesStayInsideTheList() {
        assertEquals(listOf(2L, 1L, 3L, 4L), JoinPlan.move(pages, 2, -1).map { it.id })
        assertEquals(listOf(1L, 3L, 2L, 4L), JoinPlan.move(pages, 2, +1).map { it.id })
        assertEquals(pages, JoinPlan.move(pages, 1, -1))
        assertEquals(pages, JoinPlan.move(pages, 4, +1))
        assertEquals(pages, JoinPlan.move(pages, 99, +1))
    }

    @Test fun rotationSwapsTheSides() {
        val turned = JoinPlan.rotate(pages, 3).first { it.id == 3L }
        assertEquals(90, turned.rotation)
        assertEquals(842f, turned.outWidthPt)
        assertEquals(595f, turned.outHeightPt)
        val back = (1..3).fold(listOf(turned)) { acc, _ -> JoinPlan.rotate(acc, 3) }.single()
        assertEquals(0, back.rotation)
    }

    @Test fun removeDropsOnlyThatPage() {
        assertEquals(listOf(1L, 2L, 4L), JoinPlan.remove(pages, 3).map { it.id })
    }

    @Test fun photosBecomeA4WidePages() {
        val (w, h) = JoinPlan.photoPage(4032, 3024)
        assertEquals(595f, w)
        assertEquals(446.25f, h)
    }

    @Test fun renderSizeIsCapped() {
        assertEquals(1240 to 1754, JoinPlan.renderSize(595f, 842f))
        val (w, h) = JoinPlan.renderSize(5000f, 300f)
        assertEquals(2400, w)
        assertTrue(h in 140..150)
    }

    @Test fun names() {
        assertEquals("devis.pdf", JoinPlan.outputName(listOf("devis.pdf", "devis.pdf")))
        assertEquals("devis + facture.pdf", JoinPlan.outputName(listOf("devis.pdf", "facture.pdf")))
        assertEquals("devis + 2 autres.pdf", JoinPlan.outputName(listOf("devis.pdf", "facture.pdf", "IMG_2041.jpg")))
        assertEquals("v1.2 + notes.pdf", JoinPlan.outputName(listOf("v1.2.pdf", "notes.pdf")))
        assertEquals("devis (page 3).pdf", JoinPlan.pageName("devis.pdf", 3))
        assertTrue(JoinPlan.outputName(listOf("a".repeat(300) + ".pdf", "b.pdf")).length <= 120)
        assertEquals("6 pages · 3 fichiers", JoinPlan.summary(6, 3))
        assertEquals("1 page · 1 fichier", JoinPlan.summary(1, 1))
    }
}
