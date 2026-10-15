package com.powermaxv2.ytdownloader.core.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePlanTest {
    @Test fun frameRateFromTheFrameCountElseCaptureRateElseThirty() {
        assertEquals(25f, CapturePlan.fps(250, 10_000, null), 0.001f)
        assertEquals(120f, CapturePlan.fps(null, 10_000, 120f), 0.001f)
        assertEquals(30f, CapturePlan.fps(null, 10_000, null), 0.001f)
        assertEquals(30f, CapturePlan.fps(0, 10_000, Float.NaN), 0.001f)
        assertEquals(240f, CapturePlan.fps(100_000, 1_000, null), 0.001f)
    }

    @Test fun framesAndTimesMatch() {
        // 30 fps: frame 0 spans 0..33 ms, its middle is 17 ms.
        assertEquals(0L, CapturePlan.frameIndex(0, 30f))
        assertEquals(0L, CapturePlan.frameIndex(33, 30f))
        assertEquals(1L, CapturePlan.frameIndex(34, 30f))
        assertEquals(17L, CapturePlan.msOfFrame(0, 30f))
        assertEquals(30L, CapturePlan.frameIndex(CapturePlan.msOfFrame(30, 30f), 30f))
        // 10 s at 30 fps: frames 0..299.
        assertEquals(299L, CapturePlan.lastIndex(10_000, 30f))
    }

    @Test fun snapLandsOnTheMiddleOfAFrameInsideTheVideo() {
        assertEquals(17L, CapturePlan.snap(5, 30f, 10_000))
        assertEquals(CapturePlan.msOfFrame(299, 30f), CapturePlan.snap(50_000, 30f, 10_000))
        assertTrue(CapturePlan.snap(50_000, 30f, 10_000) < 10_000)
    }

    @Test fun stepsMoveOneImageAndStopAtTheEnds() {
        val at = CapturePlan.msOfFrame(10, 25f)
        assertEquals(CapturePlan.msOfFrame(11, 25f), CapturePlan.step(at, 1, 25f, 10_000))
        assertEquals(CapturePlan.msOfFrame(9, 25f), CapturePlan.step(at, -1, 25f, 10_000))
        assertEquals(CapturePlan.msOfFrame(0, 25f), CapturePlan.step(at, -50, 25f, 10_000))
        assertEquals(CapturePlan.msOfFrame(249, 25f), CapturePlan.step(at, 10_000, 25f, 10_000))
    }

    @Test fun jumpsOfOneSecond() {
        assertEquals(CapturePlan.snap(1_500, 30f, 10_000), CapturePlan.jump(500, 1_000, 30f, 10_000))
        assertEquals(17L, CapturePlan.jump(500, -1_000, 30f, 10_000))
    }

    @Test fun aroundIsCentredAndSlidesInwardAtTheEdges() {
        val mid = CapturePlan.around(CapturePlan.msOfFrame(100, 30f), 5, 1, 30f, 10_000)
        assertEquals((98L..102L).map { CapturePlan.msOfFrame(it, 30f) }, mid)
        val start = CapturePlan.around(0, 5, 3, 30f, 10_000)
        assertEquals(listOf(0L, 3L, 6L, 9L, 12L).map { CapturePlan.msOfFrame(it, 30f) }, start)
        val end = CapturePlan.around(9_999, 3, 2, 30f, 10_000)
        assertEquals(listOf(295L, 297L, 299L).map { CapturePlan.msOfFrame(it, 30f) }, end)
        // A 3-frame video gives its 3 frames once.
        assertEquals(3, CapturePlan.around(0, 11, 1, 30f, 100).size)
    }

    @Test fun lumaOfPrimaries() {
        val l = CapturePlan.luma(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt()))
        assertEquals(listOf(255, 0, 76, 149), l.toList())
    }

    @Test fun crispEdgesScoreHigherThanBlur() {
        val w = 32
        val h = 32
        val crisp = IntArray(w * h) { i -> if ((i % w) / 4 % 2 == 0) 0 else 255 }
        // The same stripes, softened: ramps instead of steps.
        val soft = IntArray(w * h) { i ->
            val x = i % w
            val phase = x % 8
            listOf(0, 64, 128, 192, 255, 192, 128, 64)[phase]
        }
        val flat = IntArray(w * h) { 128 }
        val sCrisp = CapturePlan.sharpness(crisp, w, h)
        val sSoft = CapturePlan.sharpness(soft, w, h)
        assertTrue(sCrisp > sSoft * 3)
        assertTrue(sSoft > 0.0)
        assertEquals(0.0, CapturePlan.sharpness(flat, w, h), 0.0)
        assertEquals(0.0, CapturePlan.sharpness(IntArray(4), 2, 2), 0.0)
    }

    @Test fun pickKeepsThePlayheadUnlessClearlyBeaten() {
        val frames = listOf(10L, 20L, 30L)
        assertEquals(30L, CapturePlan.pick(frames, listOf(50.0, 80.0, 200.0), 20L))
        // 20 is within 3 % of the best: the user's frame stays.
        assertEquals(20L, CapturePlan.pick(frames, listOf(50.0, 98.0, 100.0), 20L))
        assertEquals(0, CapturePlan.best(listOf(5.0, 5.0)))
    }

    @Test fun gainAndGauge() {
        assertEquals(50, CapturePlan.gainPercent(100.0, 150.0))
        assertEquals(0, CapturePlan.gainPercent(100.0, 90.0))
        assertEquals(100, CapturePlan.gainPercent(0.0, 5.0))
        assertEquals(0f, CapturePlan.level(0.0), 0f)
        assertEquals(0.5f, CapturePlan.level(120.0), 0.001f)
        assertEquals("Floue", CapturePlan.verdict(0.1f))
        assertEquals("Un peu floue", CapturePlan.verdict(0.3f))
        assertEquals("Nette", CapturePlan.verdict(0.6f))
        assertEquals("Très nette", CapturePlan.verdict(0.9f))
    }

    @Test fun timecodesAndNames() {
        assertEquals("0:12.345", CapturePlan.timecode(12_345))
        assertEquals("1:02:03.040", CapturePlan.timecode(3_723_040))
        assertEquals("0m12s345", CapturePlan.stamp(12_345))
        assertEquals("concert lyon (2m05s010).jpg", CapturePlan.outputName("concert lyon.mp4", 125_010, CaptureFormat.JPG))
        assertEquals("fichier (0m00s017).png", CapturePlan.outputName("", 17, CaptureFormat.PNG))
    }

    @Test fun fitNeverEnlarges() {
        assertEquals(320 to 180, CapturePlan.fit(1920, 1080, 320))
        assertEquals(180 to 320, CapturePlan.fit(1080, 1920, 320))
        assertEquals(200 to 100, CapturePlan.fit(200, 100, 320))
    }
}
