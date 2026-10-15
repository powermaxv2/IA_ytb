package com.powermaxv2.ytdownloader.core.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedPlanTest {
    private val root = "/data/cache/gulp-work"
    private val input = "$root/job/input.mp4"
    private val out = "$root/job/output.mp4"

    @Test fun speedsGoFromQuarterToEightTimes() {
        assertEquals(0.25, SpeedPlan.SPEEDS.first(), 0.0)
        assertEquals(8.0, SpeedPlan.SPEEDS.last(), 0.0)
        assertFalse(1.0 in SpeedPlan.SPEEDS)
        assertEquals(SpeedPlan.SPEEDS.sorted(), SpeedPlan.SPEEDS)
        assertTrue(SpeedPlan.DEFAULT_SPEED in SpeedPlan.SPEEDS)
    }

    @Test fun labelsUseFrenchDecimals() {
        assertEquals("×0,25", SpeedPlan.label(0.25))
        assertEquals("×0,5", SpeedPlan.label(0.5))
        assertEquals("×1,5", SpeedPlan.label(1.5))
        assertEquals("×2", SpeedPlan.label(2.0))
        assertEquals("Ralenti", SpeedPlan.mood(0.75))
        assertEquals("Accéléré", SpeedPlan.mood(1.25))
    }

    @Test fun lengthOfTheResult() {
        assertEquals(454_000L, SpeedPlan.outputMs(227_000, 0.5))
        assertEquals(908_000L, SpeedPlan.outputMs(227_000, 0.25))
        assertEquals(28_375L, SpeedPlan.outputMs(227_000, 8.0))
        assertEquals(0L, SpeedPlan.outputMs(-5, 2.0))
    }

    @Test fun dialIsLogarithmicAndSnaps() {
        assertEquals(0f, SpeedPlan.dial(0.25), 1e-6f)
        assertEquals(1f, SpeedPlan.dial(8.0), 1e-6f)
        // ×1 sits exactly between ×0.25 and ×4 on a log scale.
        assertEquals(SpeedPlan.dial(4.0) / 2f, SpeedPlan.dial(1.0), 1e-6f)
        SpeedPlan.SPEEDS.forEach { assertEquals(it, SpeedPlan.atDial(SpeedPlan.dial(it)), 0.0) }
        assertEquals(0.25, SpeedPlan.atDial(-3f), 0.0)
        assertEquals(8.0, SpeedPlan.atDial(9f), 0.0)
        assertEquals(2.0, SpeedPlan.nearest(2.2), 0.0)
        assertEquals(0.75, SpeedPlan.nearest(0.8), 0.0)
    }

    @Test fun atempoIsChainedWithinItsLimits() {
        SpeedPlan.SPEEDS.forEach { speed ->
            val chain = SpeedPlan.atempoChain(speed)
            assertTrue(chain.all { it in 0.5..2.0 })
            assertEquals(speed, chain.fold(1.0) { a, b -> a * b }, 1e-9)
        }
        assertEquals(listOf(0.5, 0.5), SpeedPlan.atempoChain(0.25))
        assertEquals(listOf(2.0, 2.0, 2.0), SpeedPlan.atempoChain(8.0))
        assertEquals(listOf(2.0, 1.5), SpeedPlan.atempoChain(3.0))
        assertEquals(listOf(0.75), SpeedPlan.atempoChain(0.75))
    }

    @Test fun soundFilters() {
        assertEquals("atempo=0.5,atempo=0.5", SpeedPlan.audioFilter(0.25, SpeedSound.NATURAL, 44_100))
        assertEquals("asetrate=88200,aresample=44100", SpeedPlan.audioFilter(2.0, SpeedSound.TAPE, 44_100))
        assertEquals("asetrate=36000,aresample=48000", SpeedPlan.audioFilter(0.75, SpeedSound.TAPE, null))
        assertEquals("asetrate=24000,aresample=48000", SpeedPlan.audioFilter(0.5, SpeedSound.TAPE, 3))
        assertNull(SpeedPlan.audioFilter(2.0, SpeedSound.MUTE, 44_100))
    }

    @Test fun frameRateIsTheSourcesUpToSixty() {
        assertEquals(30.0, SpeedPlan.outputFps(null), 0.0)
        assertEquals(29.97, SpeedPlan.outputFps(29.97), 0.0)
        assertEquals(60.0, SpeedPlan.outputFps(240.0), 0.0)
        assertEquals(30.0, SpeedPlan.outputFps(0.0), 0.0)
        assertEquals("setpts=PTS/0.5,fps=29.97,scale=trunc(iw/2)*2:trunc(ih/2)*2", SpeedPlan.videoFilter(0.5, 29.97))
        assertEquals("setpts=PTS/0.75,fps=30,scale=trunc(iw/2)*2:trunc(ih/2)*2", SpeedPlan.videoFilter(0.75, null))
    }

    @Test fun argsWithSound() {
        val args = SpeedPlan.args(root, input, out, 2.0, SpeedSound.NATURAL, hasAudio = true, sourceFps = 30.0, sampleRate = 48_000)
        assertEquals(out, args.last())
        assertEquals(input, args[args.indexOf("-i") + 1])
        assertEquals("setpts=PTS/2,fps=30,scale=trunc(iw/2)*2:trunc(ih/2)*2", args[args.indexOf("-vf") + 1])
        assertEquals("atempo=2", args[args.indexOf("-af") + 1])
        assertEquals("0:a:0", args[args.indexOf("-af") - 1])
        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertTrue("+faststart" in args)
        assertFalse("-an" in args)
    }

    @Test fun argsMutedOrWithoutSoundTrack() {
        val muted = SpeedPlan.args(root, input, out, 4.0, SpeedSound.MUTE, hasAudio = true, sourceFps = 60.0, sampleRate = 48_000)
        assertTrue("-an" in muted)
        assertFalse("-af" in muted)
        val silent = SpeedPlan.args(root, input, out, 0.5, SpeedSound.NATURAL, hasAudio = false, sourceFps = null, sampleRate = null)
        assertTrue("-an" in silent)
        assertFalse("0:a:0" in silent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun argsStayInTheWorkDir() {
        SpeedPlan.args(root, "/sdcard/DCIM/a.mp4", out, 2.0, SpeedSound.NATURAL, true, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun argsRefuseUnchangedSpeed() {
        SpeedPlan.args(root, input, out, 1.0, SpeedSound.NATURAL, true, null, null)
    }

    @Test fun outputNames() {
        assertEquals("concert (ralenti ×0,5).mp4", SpeedPlan.outputName("concert.mov", 0.5))
        assertEquals("concert (accéléré ×4).mp4", SpeedPlan.outputName("concert.mp4", 4.0))
        assertEquals("video (accéléré ×2).mp4", SpeedPlan.outputName("", 2.0))
        assertTrue(SpeedPlan.outputName("a".repeat(300) + ".mp4", 8.0).length <= 120)
    }

    @Test fun clockAndDecimals() {
        assertEquals("3:47", SpeedPlan.clock(227_000))
        assertEquals("1:02:03", SpeedPlan.clock(3_723_000))
        assertEquals("0:00", SpeedPlan.clock(-4))
        assertEquals("1.33333", SpeedPlan.decimal(4.0 / 3))
        assertEquals("2", SpeedPlan.decimal(2.0))
    }
}
