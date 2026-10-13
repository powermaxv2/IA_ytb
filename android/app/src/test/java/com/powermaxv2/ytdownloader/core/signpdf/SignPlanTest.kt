package com.powermaxv2.ytdownloader.core.signpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignPlanTest {
    private val a4 = 595f / 842f
    private val sig = 3f

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-3f) =
        assertEquals(expected.toDouble(), actual.toDouble(), eps.toDouble())

    @Test fun startBoxSitsBottomRightInsideThePage() {
        val box = SignPlan.startBox(sig, a4, captioned = true)
        assertNear(SignPlan.START_WIDTH, box.width)
        assertTrue(box.x > 0.5f)
        assertTrue(box.y > 0.6f)
        val h = SignPlan.footprintHeight(box.width, sig, a4, captioned = true)
        assertTrue(box.x + box.width <= 1f)
        assertTrue(box.y + h <= 1f)
    }

    @Test fun dragStopsAtTheEdges() {
        val box = SignPlan.startBox(sig, a4, captioned = false)
        val far = SignPlan.moved(box, 5f, 5f, sig, a4, captioned = false)
        assertNear(1f - far.width, far.x)
        assertNear(1f - SignPlan.footprintHeight(far.width, sig, a4, false), far.y)
        val back = SignPlan.moved(box, -5f, -5f, sig, a4, captioned = false)
        assertNear(0f, back.x)
        assertNear(0f, back.y)
    }

    @Test fun resizeKeepsTheCornerAndTheLimits() {
        val box = SignBox(0.2f, 0.3f, 0.3f)
        val bigger = SignPlan.resized(box, 0.1f, sig, a4, captioned = false)
        assertNear(0.2f, bigger.x)
        assertNear(0.3f, bigger.y)
        assertNear(0.4f, bigger.width)
        val tiny = SignPlan.resized(box, -1f, sig, a4, captioned = false)
        assertNear(SignPlan.MIN_WIDTH, tiny.width)
        // No wider than the room left on the right.
        val huge = SignPlan.resized(box, 5f, sig, a4, captioned = false)
        assertNear(0.8f, huge.width)
    }

    @Test fun pinchScalesAroundTheCentre() {
        val box = SignBox(0.3f, 0.4f, 0.2f)
        val twice = SignPlan.scaled(box, 2f, sig, a4, captioned = false)
        assertNear(0.4f, twice.width)
        assertNear(box.x + box.width / 2, twice.x + twice.width / 2)
    }

    @Test fun aTallSignatureShrinksToFitAWidePage() {
        val landscape = 842f / 595f
        val box = SignPlan.clamp(SignBox(0f, 0f, 0.9f), 0.5f, landscape, captioned = true)
        assertTrue(SignPlan.footprintHeight(box.width, 0.5f, landscape, true) <= 1.0001f)
    }

    @Test fun captionJoinsInitialsAndDate() {
        val day = LocalDate.of(2026, 10, 13)
        assertNull(SignPlan.caption(SignStamp(), day))
        assertEquals("le 13/10/2026", SignPlan.caption(SignStamp(date = true), day))
        assertEquals("M.G.", SignPlan.caption(SignStamp(initials = " M.G. "), day))
        assertEquals("MG, le 13/10/2026", SignPlan.caption(SignStamp(date = true, initials = "MG"), day))
    }

    @Test fun cleanTextKeepsWhatPdfFontsCanWrite() {
        assertEquals("Éloïse", SignPlan.cleanText("Éloïse"))
        assertEquals("MG", SignPlan.cleanText("M😀G"))
        assertEquals("a b", SignPlan.cleanText(" a \n  b "))
        assertEquals("ABCDEF", SignStamp(initials = "ABCDEFGH").cleanInitials)
    }

    @Test fun unrotatedPageFlipsY() {
        val frame = PdfPageFrame(0f, 0f, 595f, 842f, 0)
        assertEquals(SignPoint(10f, 832f), SignPlan.toPdf(10f, 10f, frame))
        val m = SignPlan.imageMatrix(SignRect(100f, 700f, 200f, 50f), frame)
        // Bottom-left of the picture at (100, 842 − 750), 200 wide, 50 high.
        assertEquals(PdfMatrix(200f, 0f, 0f, 50f, 100f, 92f), m)
    }

    @Test fun cropBoxOriginIsAdded() {
        val frame = PdfPageFrame(20f, 30f, 500f, 700f, 0)
        assertEquals(SignPoint(20f, 730f), SignPlan.toPdf(0f, 0f, frame))
    }

    @Test fun everyRotationKeepsThePictureUpright() {
        for (rotation in listOf(0, 90, 180, 270, -90, 450)) {
            val frame = PdfPageFrame(0f, 0f, 600f, 800f, rotation)
            val rect = SignRect(50f, 60f, 120f, 40f)
            val m = SignPlan.imageMatrix(rect, frame)
            // The picture's corners land on the shown rectangle's corners.
            val bl = m.apply(0f, 0f)
            val tr = m.apply(1f, 1f)
            assertEquals("$rotation", SignPlan.toPdf(rect.left, rect.bottom, frame), bl)
            assertEquals("$rotation", SignPlan.toPdf(rect.right, rect.top, frame), tr)
            // Area is kept (no squashing).
            assertNear(120f * 40f, kotlin.math.abs(m.a * m.d - m.b * m.c))
        }
    }

    @Test fun quarterTurnSwapsTheShownSize() {
        val frame = PdfPageFrame(0f, 0f, 600f, 800f, 90)
        assertNear(800f, frame.shownWidth)
        assertNear(600f, frame.shownHeight)
        // Top-left as shown = bottom-left of the PDF page turned clockwise.
        assertEquals(SignPoint(0f, 0f), SignPlan.toPdf(0f, 0f, frame))
        // Top-right as shown (800, 0) = PDF (0, 800): the top-left of the unturned page.
        assertEquals(SignPoint(0f, 800f), SignPlan.toPdf(800f, 0f, frame))
        assertEquals(0, PdfPageFrame(0f, 0f, 1f, 1f, 45).quarter)
    }

    @Test fun textMatrixRunsAlongTheShownBaseline() {
        val frame = PdfPageFrame(0f, 0f, 600f, 800f, 90)
        val m = SignPlan.textMatrix(100f, 200f, 12f, frame)
        // Shown "right" on a page turned 90° clockwise is PDF "up".
        assertNear(0f, m.a)
        assertNear(12f, m.b)
        assertNear(-12f, m.c)
        assertNear(0f, m.d)
    }

    @Test fun captionRectSitsUnderTheSignature() {
        val frame = PdfPageFrame(0f, 0f, 600f, 800f, 0)
        val (s, c) = SignPlan.shownRects(SignBox(0.5f, 0.5f, 0.25f), 3f, frame)
        assertNear(300f, s.left)
        assertNear(400f, s.top)
        assertNear(150f, s.width)
        assertNear(50f, s.height)
        assertTrue(c.top > s.bottom)
        assertNear(150f * SignPlan.CAPTION_RATIO, c.height)
    }

    @Test fun paraphStaysInTheCorner() {
        val frame = PdfPageFrame(0f, 0f, 600f, 800f, 0)
        val r = SignPlan.paraphRect(frame)
        assertTrue(r.right < 600f && r.bottom < 800f)
        assertTrue(r.left > 450f && r.top > 700f)
    }

    @Test fun fitFontRespectsHeightAndWidth() {
        assertNear(8.6f, SignPlan.fitFont(SignRect(0f, 0f, 1000f, 10f), 2f))
        assertNear(5f, SignPlan.fitFont(SignRect(0f, 0f, 50f, 10f), 10f))
    }

    @Test fun simplifyDropsJitterButKeepsEnds() {
        val stroke = listOf(SignPoint(0f, 0f), SignPoint(0.0001f, 0f), SignPoint(0.0002f, 0f), SignPoint(0.1f, 0f), SignPoint(0.1001f, 0f))
        val out = SignPlan.simplify(stroke)
        assertEquals(listOf(SignPoint(0f, 0f), SignPoint(0.1f, 0f), SignPoint(0.1001f, 0f)), out)
    }

    @Test fun aTapIsNotASignature() {
        assertFalse(SignPlan.isSignature(emptyList()))
        assertFalse(SignPlan.isSignature(listOf(listOf(SignPoint(0.5f, 0.5f)))))
        assertTrue(SignPlan.isSignature(listOf(listOf(SignPoint(0.1f, 0.5f), SignPoint(0.3f, 0.4f), SignPoint(0.5f, 0.6f)))))
    }

    @Test fun trimCutsToTheInkAndKeepsItsShape() {
        val strokes = listOf(listOf(SignPoint(0.25f, 0.25f), SignPoint(0.75f, 0.75f)))
        // Pad 2 wide for 1 high: the ink is 1 × 0.5 in square units.
        val ink = SignPlan.trim(strokes, padAspect = 2f, strokeWidth = 0.02f, maxSide = 1000)
        assertNotNull(ink)
        ink!!
        assertEquals(1000, ink.width)
        assertTrue(ink.aspect > 1.8f && ink.aspect < 2f)
        val start = ink.map(SignPoint(0.25f, 0.25f))
        val end = ink.map(SignPoint(0.75f, 0.75f))
        assertNear(ink.strokeWidth * SignPlan.TRIM_PAD, start.x, 0.5f)
        assertNear(ink.width - ink.strokeWidth * SignPlan.TRIM_PAD, end.x, 1.5f)
        assertNear(ink.strokeWidth * SignPlan.TRIM_PAD, start.y, 0.5f)
        assertNull(SignPlan.trim(emptyList(), 2f, 0.02f, 1000))
    }
}
