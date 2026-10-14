package com.powermaxv2.ytdownloader.core.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LevelMathTest {
    private val g = 9.81f

    /** Phone lying flat, its right side raised by [right]° and its top by [top]°. */
    private fun flat(right: Float = 0f, top: Float = 0f): Gravity {
        val r = Math.toRadians(right.toDouble())
        val t = Math.toRadians(top.toDouble())
        return Gravity((g * sin(r)).toFloat(), (g * sin(t)).toFloat(), (g * cos(r) * cos(t)).toFloat())
    }

    /** Phone standing upright on its bottom edge, its right side raised by [right]°. */
    private fun upright(right: Float = 0f): Gravity {
        val r = Math.toRadians(right.toDouble())
        return Gravity((g * sin(r)).toFloat(), (g * cos(r)).toFloat(), 0f)
    }

    @Test fun flatPhoneOnATableReadsZeroAndLevel() {
        val reading = LevelMath.read(flat(), LevelMode.FLAT) as LevelReading.Flat
        assertEquals(0f, reading.tiltX, 0.01f)
        assertEquals(0f, reading.tiltY, 0.01f)
        assertTrue(reading.level)
        assertEquals("C'est de niveau", LevelMath.advice(reading))
    }

    @Test fun flatTiltHasTheRightSideAndSize() {
        val reading = LevelMath.read(flat(right = 3f), LevelMode.FLAT) as LevelReading.Flat
        assertEquals(3f, reading.tiltX, 0.05f)
        assertEquals(0f, reading.tiltY, 0.05f)
        assertFalse(reading.level)
        // Right side higher: it slopes down to the left, and the bubble goes right.
        assertEquals("Ça penche vers la gauche", LevelMath.advice(reading))
        assertTrue(LevelMath.bubbleShare(reading.tiltX) > 0f)
        val top = LevelMath.read(flat(top = -2f), LevelMode.FLAT) as LevelReading.Flat
        assertEquals(-2f, top.tiltY, 0.05f)
        assertEquals("Ça penche vers le haut de l'écran", LevelMath.advice(top))
    }

    @Test fun toleranceIsHalfADegree() {
        assertTrue(LevelMath.read(flat(right = 0.4f), LevelMode.FLAT)!!.level)
        assertFalse(LevelMath.read(flat(right = 0.6f), LevelMode.FLAT)!!.level)
        assertTrue(LevelMath.read(upright(0.3f), LevelMode.EDGE)!!.level)
        assertFalse(LevelMath.read(upright(0.8f), LevelMode.EDGE)!!.level)
    }

    @Test fun edgeReadsTheSlopeOfTheEdgeThatTouches() {
        val reading = LevelMath.read(upright(2f), LevelMode.EDGE) as LevelReading.Edge
        assertEquals(2f, reading.angle, 0.05f)
        assertEquals(0, reading.snap)
        assertEquals("Le côté droit est trop haut", LevelMath.advice(reading))
        val other = LevelMath.read(upright(-1.5f), LevelMode.EDGE) as LevelReading.Edge
        assertEquals(-1.5f, other.angle, 0.05f)
        assertEquals("Le côté gauche est trop haut", LevelMath.advice(other))
    }

    @Test fun edgeSnapsToTheEdgeThatIsDown() {
        // Lying on its left long edge: "up" is the screen's right.
        val sideways = LevelMath.read(Gravity(g, 0.2f, 0f), LevelMode.EDGE) as LevelReading.Edge
        assertEquals(90, sideways.snap)
        assertTrue(kotlin.math.abs(sideways.angle) < 2f)
        val upsideDown = LevelMath.read(Gravity(0.1f, -g, 0f), LevelMode.EDGE) as LevelReading.Edge
        assertEquals(180, upsideDown.snap)
        assertEquals(270, (LevelMath.read(Gravity(-g, 0f, 0f), LevelMode.EDGE) as LevelReading.Edge).snap)
    }

    @Test fun autoModeFollowsHowThePhoneIsHeldWithAMargin() {
        assertEquals(LevelMode.FLAT, LevelMath.resolve(LevelMode.AUTO, flat()))
        assertEquals(LevelMode.EDGE, LevelMath.resolve(LevelMode.AUTO, upright()))
        // 45° from flat: keeps whatever it was.
        val half = flat(top = 45f)
        assertEquals(LevelMode.FLAT, LevelMath.resolve(LevelMode.AUTO, half, LevelMode.FLAT))
        assertEquals(LevelMode.EDGE, LevelMath.resolve(LevelMode.AUTO, half, LevelMode.EDGE))
        assertEquals(LevelMode.EDGE, LevelMath.resolve(LevelMode.AUTO, flat(top = 60f), LevelMode.FLAT))
        // A chosen mode is never changed.
        assertEquals(LevelMode.FLAT, LevelMath.resolve(LevelMode.FLAT, upright()))
        assertEquals(LevelMode.EDGE, LevelMath.resolve(LevelMode.EDGE, flat()))
    }

    @Test fun calibrationMakesTheCurrentPositionZero() {
        // A camera bump lifts the top by 1.2°.
        val bump = flat(top = 1.2f)
        val zero = LevelMath.calibrate(bump, LevelMode.FLAT, Calibration())
        assertNotNull(zero)
        val reading = LevelMath.read(bump, LevelMode.FLAT, zero!!) as LevelReading.Flat
        assertEquals(0f, reading.tiltY, 0.01f)
        assertTrue(reading.level)
        // The edge zero is kept apart from the flat one.
        val both = LevelMath.calibrate(upright(0.9f), LevelMode.EDGE, zero)!!
        assertEquals(zero.flatY, both.flatY, 0f)
        assertEquals(0f, (LevelMath.read(upright(0.9f), LevelMode.EDGE, both) as LevelReading.Edge).angle, 0.01f)
    }

    @Test fun tooTiltedIsNoZero() {
        assertNull(LevelMath.calibrate(flat(right = 12f), LevelMode.FLAT, Calibration()))
        assertNull(LevelMath.calibrate(upright(20f), LevelMode.EDGE, Calibration()))
    }

    @Test fun calibrationSurvivesSavingAndRefusesJunk() {
        val c = Calibration(0.5f, -1.25f, 2f)
        assertEquals(c, Calibration.decode(c.encode()))
        assertEquals(Calibration(), Calibration.decode(null))
        assertEquals(Calibration(), Calibration.decode("abc"))
        assertEquals(Calibration(), Calibration.decode("1;2"))
        assertEquals(Calibration(), Calibration.decode("40;0;0"))
        assertFalse(Calibration().isSet)
        assertTrue(c.isSet)
    }

    @Test fun screenRotationBringsValuesIntoScreenAxes() {
        val raw = Gravity(1f, 2f, 3f)
        assertEquals(raw, LevelMath.toScreen(raw, 0))
        assertEquals(Gravity(-2f, 1f, 3f), LevelMath.toScreen(raw, 90))
        assertEquals(Gravity(-1f, -2f, 3f), LevelMath.toScreen(raw, 180))
        assertEquals(Gravity(2f, -1f, 3f), LevelMath.toScreen(raw, 270))
        // A phone held in landscape (turned left), the display turned with it: reads like portrait upright.
        val landscape = Gravity(g, 0f, 0f)
        val reading = LevelMath.read(LevelMath.toScreen(landscape, 90), LevelMode.EDGE) as LevelReading.Edge
        assertEquals(0, reading.snap)
    }

    @Test fun brokenValuesGiveNoReading() {
        assertNull(LevelMath.read(Gravity(0f, 0f, 0f), LevelMode.FLAT))
        assertNull(LevelMath.read(Gravity(Float.NaN, 9f, 0f), LevelMode.EDGE))
    }

    @Test fun wordsAndNumbers() {
        assertEquals("0,4°", LevelMath.degrees(0.43f))
        assertEquals("2,0°", LevelMath.degrees(-1.96f))
        assertEquals("12,5°", LevelMath.degrees(12.5f))
        assertEquals(17, LevelMath.mmPerMetre(1f))
        assertEquals(0, LevelMath.mmPerMetre(0f))
        assertEquals(1f, LevelMath.bubbleShare(30f), 0f)
        assertEquals(-0.5f, LevelMath.bubbleShare(-5f), 0.0001f)
        assertEquals(0, LevelMath.snap(-44f))
        assertEquals(90, LevelMath.snap(46f))
        assertEquals(170f, LevelMath.wrap(-190f), 0.001f)
    }

    @Test fun smoothingCalmsShakesButFollowsRealMoves() {
        val lp = LowPass()
        val start = lp.push(Gravity(0f, 0f, g))
        assertEquals(g, start.z, 0f)
        // A little shake moves it a little.
        val shake = lp.push(Gravity(1f, 0f, g))
        assertTrue(shake.x in 0.05f..0.2f)
        // Settles on the new value.
        var last = shake
        repeat(80) { last = lp.push(Gravity(1f, 0f, g)) }
        assertEquals(1f, last.x, 0.01f)
        lp.reset()
        assertEquals(5f, lp.push(Gravity(5f, 0f, 0f)).x, 0f)
    }
}
