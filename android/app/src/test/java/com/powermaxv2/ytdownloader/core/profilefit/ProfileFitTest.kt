package com.powermaxv2.ytdownloader.core.profilefit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ProfileFitTest {
    private fun corners(b: FitBox) = listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom)

    @Test fun circleKeepsEveryCornerInsideTheRound() {
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 1080 to 1080, 1080 to 2400, 5000 to 800)) {
            val side = 1080
            val box = ProfileFit.placement(w, h, side, FitShape.CIRCLE, FitMargin.TIGHT)
            corners(box).forEach { (x, y) ->
                val d = hypot(x - side / 2.0, y - side / 2.0)
                assertTrue("$w×$h corner at $d", d <= side / 2.0 + 0.01)
            }
            // Tight: the corners touch the round.
            assertEquals(side / 2.0, hypot(box.left - side / 2.0, box.top - side / 2.0), 0.5)
        }
    }

    @Test fun placementIsCentredAndKeepsTheRatio() {
        val box = ProfileFit.placement(3000, 4000, 1000, FitShape.CIRCLE, FitMargin.TIGHT)
        // Diagonal 5000 → 1000: scale 0.2.
        assertEquals(600f, box.width, 0.01f)
        assertEquals(800f, box.height, 0.01f)
        assertEquals(200f, box.left, 0.01f)
        assertEquals(100f, box.top, 0.01f)
        assertEquals(box.width / box.height, 3f / 4f, 0.001f)
    }

    @Test fun squareFillsTheLongSide() {
        val box = ProfileFit.placement(3000, 4000, 1000, FitShape.SQUARE, FitMargin.TIGHT)
        assertEquals(1000f, box.height, 0.01f)
        assertEquals(750f, box.width, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(125f, box.left, 0.01f)
    }

    @Test fun marginShrinksThePhoto() {
        val tight = ProfileFit.placement(1000, 1000, 1000, FitShape.SQUARE, FitMargin.TIGHT)
        val wide = ProfileFit.placement(1000, 1000, 1000, FitShape.SQUARE, FitMargin.WIDE)
        assertEquals(1000f, tight.width, 0.01f)
        assertEquals(800f, wide.width, 0.01f)
        assertEquals(100f, wide.left, 0.01f)
        assertTrue(ProfileFit.placement(1000, 1000, 1000, FitShape.CIRCLE, FitMargin.NORMAL).width < ProfileFit.placement(1000, 1000, 1000, FitShape.CIRCLE, FitMargin.TIGHT).width)
    }

    @Test fun coverFillsTheSquare() {
        val box = ProfileFit.cover(4000, 3000, 90)
        assertEquals(90f, box.height, 0.01f)
        assertEquals(120f, box.width, 0.01f)
        assertEquals(-15f, box.left, 0.01f)
        assertEquals(0f, box.top, 0.01f)
    }

    @Test fun whatsAppHidesTheCornersAndTheLongSide() {
        // A square photo loses its four corners: 1 − π/4.
        assertEquals(0.2146f, ProfileFit.hiddenByWhatsApp(1000, 1000), 0.001f)
        // A 3:4 portrait: 1 − (π/4 · 9/12).
        assertEquals(0.4110f, ProfileFit.hiddenByWhatsApp(3000, 4000), 0.001f)
        assertEquals(ProfileFit.hiddenByWhatsApp(3000, 4000), ProfileFit.hiddenByWhatsApp(4000, 3000), 0.0001f)
        assertTrue(ProfileFit.hiddenByWhatsApp(1080, 2400) > 0.6f)
        assertEquals("41 %", ProfileFit.percent(ProfileFit.hiddenByWhatsApp(3000, 4000)))
    }

    @Test fun gulpShowsTheWholePhotoInTheRound() {
        assertEquals(1f, ProfileFit.shownByGulp(FitShape.CIRCLE, 3000, 4000), 0f)
        // In the square, a square photo shows π/4 of itself; a long one shows more (only its ends are nibbled).
        assertEquals(0.7854f, ProfileFit.shownByGulp(FitShape.SQUARE, 1000, 1000), 0.001f)
        val portrait = ProfileFit.shownByGulp(FitShape.SQUARE, 3000, 4000)
        assertTrue(portrait > 0.7854f && portrait < 1f)
        assertTrue(ProfileFit.shownByGulp(FitShape.SQUARE, 1000, 5000) > 0.95f)
    }

    @Test fun backdropColorIsTheDarkenedMeanOfOpaquePixels() {
        val red = 0xFFC80000.toInt()
        val blue = 0xFF0000C8.toInt()
        val clear = 0x00FFFFFF
        val color = ProfileFit.backdropColor(intArrayOf(red, blue, clear))
        assertEquals(0xFF, color ushr 24)
        assertEquals(82, (color shr 16) and 0xFF) // 100 × 0.82
        assertEquals(0, (color shr 8) and 0xFF)
        assertEquals(82, color and 0xFF)
        assertEquals(0xFF202020.toInt(), ProfileFit.backdropColor(IntArray(0)))
        assertEquals(0xFF202020.toInt(), ProfileFit.backdropColor(intArrayOf(clear)))
    }

    @Test fun blurSpreadsADotAndKeepsAFlatPicture() {
        val w = 9
        val h = 9
        val flat = IntArray(w * h) { 0xFF336699.toInt() }
        assertTrue(ProfileFit.blur(flat, w, h, 2).all { it == 0xFF336699.toInt() })

        val dot = IntArray(w * h) { 0xFF000000.toInt() }
        dot[4 * w + 4] = 0xFFFFFFFF.toInt()
        val out = ProfileFit.blur(dot, w, h, 1, passes = 1)
        val centre = (out[4 * w + 4] shr 16) and 0xFF
        val near = (out[3 * w + 4] shr 16) and 0xFF
        val far = (out[0] shr 16) and 0xFF
        assertTrue(centre in 1 until 255)
        assertEquals(centre, near)
        assertEquals(0, far)
        // The source is left alone.
        assertEquals(0xFFFFFFFF.toInt(), dot[4 * w + 4])
        assertEquals(0xFF, out[0] ushr 24)
    }

    @Test fun savedNameEndsInProfilJpg() {
        assertEquals("vacances-profil.jpg", ProfileFit.outputName("vacances.heic"))
        assertEquals("IMG_2024-profil.jpg", ProfileFit.outputName("IMG_2024.JPG"))
        assertEquals("a_b-profil.jpg", ProfileFit.outputName("a/b.png"))
        assertEquals("photo-profil.jpg", ProfileFit.outputName(""))
        assertTrue(ProfileFit.outputName("x".repeat(300) + ".jpg").let { it.length <= 120 && it.endsWith(".jpg") })
    }

    @Test fun defaultsShowTheWholePhotoOnABlur() {
        val o = FitOptions()
        assertEquals(FitShape.CIRCLE, o.shape)
        assertEquals(Backdrop.BLUR, o.backdrop)
        assertEquals(FitMargin.NORMAL, o.margin)
    }
}
