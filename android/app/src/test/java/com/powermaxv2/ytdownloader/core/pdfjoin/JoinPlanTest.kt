package com.powermaxv2.ytdownloader.core.pdfjoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinPlanTest {
    private fun page(id: Long, source: Int = 0, w: Float = 595f, h: Float = 842f) =
        JoinPage(id, source = source, index = id.toInt(), widthPt = w, heightPt = h)

    private val pages = (1L..4L).map { page(it) }

    @Test fun movesStayInsideTheList() {
        assertEquals(listOf(2L, 1L, 3L, 4L), JoinPlan.move(pages, 2, -1).map { it.id })
        assertEquals(listOf(1L, 3L, 2L, 4L), JoinPlan.move(pages, 2, +1).map { it.id })
        assertEquals(pages, JoinPlan.move(pages, 1, -1))
        assertEquals(pages, JoinPlan.move(pages, 4, +1))
        assertEquals(pages, JoinPlan.move(pages, 99, +1))
    }

    @Test fun dropsLandAtThePlaceShown() {
        assertEquals(listOf(2L, 3L, 4L, 1L), JoinPlan.moveTo(pages, 1, 3).map { it.id })
        assertEquals(listOf(4L, 1L, 2L, 3L), JoinPlan.moveTo(pages, 4, 0).map { it.id })
        assertEquals(listOf(2L, 3L, 4L, 1L), JoinPlan.moveTo(pages, 1, 99).map { it.id })
        assertEquals(pages, JoinPlan.moveTo(pages, 2, 1))
    }

    @Test fun rotationSwapsTheSides() {
        val turned = JoinPlan.rotate(pages, 3).first { it.id == 3L }
        assertEquals(90, turned.rotation)
        assertEquals(842f, turned.outWidthPt)
        assertEquals(595f, turned.outHeightPt)
        val back = (1..3).fold(listOf(turned)) { acc, _ -> JoinPlan.rotate(acc, 3) }.single()
        assertEquals(0, back.rotation)
    }

    @Test fun copiedPagesKeepTheirOwnRotation() {
        assertEquals(90, JoinPlan.pdfRotation(0, 90))
        assertEquals(0, JoinPlan.pdfRotation(270, 90))
        assertEquals(180, JoinPlan.pdfRotation(-90, 270))
        // A broken /Rotate (not a multiple of 90) falls back to the quarter below.
        assertEquals(90, JoinPlan.pdfRotation(45, 90))
    }

    @Test fun removeDropsOnlyThatPage() {
        assertEquals(listOf(1L, 2L, 4L), JoinPlan.remove(pages, 3).map { it.id })
        assertEquals(setOf(1L, 3L), JoinPlan.toggle(setOf(1L), 3))
        assertEquals(emptySet<Long>(), JoinPlan.toggle(setOf(3L), 3))
    }

    @Test fun photosBecomeA4Pages() {
        assertEquals(842f to 595f, JoinPlan.photoPage(4032, 3024))
        assertEquals(595f to 842f, JoinPlan.photoPage(3024, 4032))
        val (x, y, w, h) = JoinPlan.photoBox(4032, 3024, 842f, 595f).toList()
        // Fitted in the height (595 − 2 × 24), centred across.
        assertEquals(547f, h, 0.01f)
        assertEquals(547f * 4032 / 3024, w, 0.01f)
        assertEquals(24f, y, 0.01f)
        assertEquals((842f - w) / 2, x, 0.01f)
    }

    @Test fun renderSizeIsCapped() {
        assertEquals(1240 to 1754, JoinPlan.renderSize(595f, 842f))
        val (w, h) = JoinPlan.renderSize(5000f, 300f)
        assertEquals(2400, w)
        assertTrue(h in 140..150)
    }

    @Test fun splitPieces() {
        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L)), JoinPlan.pieces(pages, SplitMode.EACH, emptySet()).map { p -> p.map { it.id } })
        assertEquals(listOf(listOf(1L, 2L, 3L, 4L)), JoinPlan.pieces(pages, SplitMode.CUTS, emptySet()).map { p -> p.map { it.id } })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), JoinPlan.pieces(pages, SplitMode.CUTS, setOf(1L, 3L)).map { p -> p.map { it.id } })
    }

    @Test fun partsOfEachTool() {
        val mixed = listOf(page(1, 0), page(2, 0), page(3, 1), page(4, 2))
        val names = listOf("devis.pdf", "facture.pdf", "IMG_2041.jpg")
        val join = JoinPlan.parts(JoinTool.JOIN, mixed, names).single()
        assertEquals("devis + 2 autres.pdf", join.name)
        assertEquals(4, join.pages.size)

        val removed = JoinPlan.parts(JoinTool.REMOVE, mixed, names, marked = setOf(2L)).single()
        assertEquals(listOf(1L, 3L, 4L), removed.pages.map { it.id })
        assertTrue(JoinPlan.parts(JoinTool.REMOVE, mixed, names).isEmpty())
        assertTrue(JoinPlan.parts(JoinTool.REMOVE, mixed, names, marked = mixed.map { it.id }.toSet()).isEmpty())

        val split = JoinPlan.parts(JoinTool.SPLIT, pages, listOf("devis.pdf"), SplitMode.CUTS, cuts = setOf(4L))
        assertEquals(listOf("devis (pages 1-3).pdf", "devis (page 4).pdf"), split.map { it.name })
        assertTrue(JoinPlan.parts(JoinTool.JOIN, emptyList(), names).isEmpty())
    }

    @Test fun names() {
        assertEquals("devis (modifié).pdf", JoinPlan.outputName(listOf("devis.pdf", "devis.pdf")))
        assertEquals("devis + facture.pdf", JoinPlan.outputName(listOf("devis.pdf", "facture.pdf")))
        assertEquals("devis + 2 autres.pdf", JoinPlan.outputName(listOf("devis.pdf", "facture.pdf", "IMG_2041.jpg")))
        assertEquals("v1.2 + notes.pdf", JoinPlan.outputName(listOf("v1.2.pdf", "notes.pdf")))
        assertEquals("devis (pages retirées).pdf", JoinPlan.removedName(listOf("devis.pdf")))
        assertEquals("devis (page 3).pdf", JoinPlan.pieceName(listOf("devis.pdf"), 3, 3))
        val long = JoinPlan.pieceName(listOf("a".repeat(300) + ".pdf"), 10, 12)
        assertTrue(long.length <= 120)
        assertTrue(long.endsWith(" (pages 10-12).pdf"))
        assertTrue(JoinPlan.outputName(listOf("a".repeat(300) + ".pdf", "b.pdf")).length <= 120)
        assertEquals("_x (modifié).pdf", JoinPlan.outputName(listOf("../x.pdf")))
        assertEquals("6 pages · 3 fichiers", JoinPlan.summary(6, 3))
        assertEquals("1 page · 1 fichier", JoinPlan.summary(1, 1))
    }

    @Test fun placesInWords() {
        assertEquals("page 6", JoinPlan.describePlaces(listOf(6)))
        assertEquals("pages 4 et 5", JoinPlan.describePlaces(listOf(4, 5)))
        assertEquals("pages 1 à 3", JoinPlan.describePlaces(listOf(1, 2, 3)))
        assertEquals("pages 1, 3 et 5", JoinPlan.describePlaces(listOf(5, 3, 1)))
        assertEquals("pages 1 à 3 et 7", JoinPlan.describePlaces(listOf(1, 2, 3, 7)))
        assertEquals("pages 1, 2, 4 à 6 et 9", JoinPlan.describePlaces(listOf(1, 2, 4, 5, 6, 9)))
    }

    @Test fun placementsFollowTheResult() {
        val mixed = listOf(page(1, 1), page(2, 0), page(3, 1))
        assertEquals(listOf(1 to listOf(1, 3), 0 to listOf(2)), JoinPlan.placements(mixed))
    }
}
