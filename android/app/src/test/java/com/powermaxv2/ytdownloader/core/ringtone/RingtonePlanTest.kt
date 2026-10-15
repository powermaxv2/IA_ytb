package com.powermaxv2.ytdownloader.core.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingtonePlanTest {
    private val root = "/data/cache/gulp-work"
    private val input = "$root/job/input.m4a"
    private val out = "$root/job/output.mp3"

    @Test fun eachUseHasItsFolderAndLength() {
        assertEquals("Ringtones", RingtoneUse.RINGTONE.directory)
        assertEquals("Notifications", RingtoneUse.NOTIFICATION.directory)
        assertEquals("Alarms", RingtoneUse.ALARM.directory)
        RingtoneUse.entries.forEach { assertTrue(it.name, it.defaultMs in RingtonePlan.MIN_MS..it.maxMs) }
    }

    @Test fun initialPassageIsTheUsualLength() {
        assertEquals(RingRange(0, 30_000), RingtonePlan.initial(214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(0, 3_000), RingtonePlan.initial(214_000, RingtoneUse.NOTIFICATION))
        // A sound shorter than the usual length is kept whole.
        assertEquals(RingRange(0, 1_800), RingtonePlan.initial(1_800, RingtoneUse.ALARM))
    }

    @Test fun clampKeepsLimits() {
        assertEquals(RingRange(10_000, 50_000), RingtonePlan.clamp(RingRange(10_000, 90_000), 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(10_000, 18_000), RingtonePlan.clamp(RingRange(10_000, 90_000), 214_000, RingtoneUse.NOTIFICATION))
        assertEquals(RingRange(4_000, 4_500), RingtonePlan.clamp(RingRange(4_000, 4_100), 10_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(0, 10_000), RingtonePlan.clamp(RingRange(-3_000, 12_000), 10_000, RingtoneUse.RINGTONE))
    }

    @Test fun handlesStopAtTheMaxAndNeverCross() {
        val r = RingRange(60_000, 90_000)
        // The start cannot go further back than end − 40 s.
        assertEquals(RingRange(50_000, 90_000), RingtonePlan.moveStart(r, 0, 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(89_500, 90_000), RingtonePlan.moveStart(r, 120_000, 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(60_000, 100_000), RingtonePlan.moveEnd(r, 200_000, 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(60_000, 60_500), RingtonePlan.moveEnd(r, 0, 214_000, RingtoneUse.RINGTONE))
    }

    @Test fun slideKeepsTheLength() {
        val r = RingRange(10_000, 40_000)
        assertEquals(RingRange(100_000, 130_000), RingtonePlan.slide(r, 100_000, 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(184_000, 214_000), RingtonePlan.slide(r, 200_000, 214_000, RingtoneUse.RINGTONE))
        assertEquals(RingRange(0, 30_000), RingtonePlan.slide(r, -5, 214_000, RingtoneUse.RINGTONE))
    }

    @Test fun changingUseResetsTheLength() {
        val r = RingRange(62_000, 92_000)
        assertEquals(RingRange(62_000, 65_000), RingtonePlan.forUse(r, 214_000, RingtoneUse.NOTIFICATION))
        assertEquals(RingRange(62_000, 102_000), RingtonePlan.forUse(r, 214_000, RingtoneUse.ALARM))
    }

    @Test fun bestStartFindsTheLoudStretch() {
        // 100 bars over 100 s: quiet, then loud from 60 s to 80 s (the chorus), quiet again.
        val bars = FloatArray(100) { i -> if (i in 60 until 80) 0.9f else 0.2f }
        assertEquals(60_000L, RingtonePlan.bestStart(bars, 100_000, 20_000))
        // A longer passage still covers the chorus.
        val start = RingtonePlan.bestStart(bars, 100_000, 30_000)!!
        assertTrue(start in 50_000L..60_000L)
        // Never past the end.
        assertEquals(70_000L, RingtonePlan.bestStart(FloatArray(100) { if (it >= 90) 1f else 0.1f }, 100_000, 30_000))
    }

    @Test fun bestStartEdges() {
        assertNull(RingtonePlan.bestStart(FloatArray(0), 100_000, 20_000))
        assertNull(RingtonePlan.bestStart(FloatArray(10) { 1f }, 0, 20_000))
        assertEquals(0L, RingtonePlan.bestStart(FloatArray(10) { 1f }, 10_000, 20_000))
        // A flat sound: the first full window that is not an intro.
        val flat = RingtonePlan.bestStart(FloatArray(100) { 0.5f }, 100_000, 10_000)!!
        assertTrue(flat in 0L..90_000L)
    }

    @Test fun fadesShrinkOnShortPassages() {
        assertEquals(400L to 1_200L, RingtonePlan.fadesFor(RingtoneFade.SOFT, 30_000))
        assertEquals(0L to 0L, RingtonePlan.fadesFor(RingtoneFade.NONE, 30_000))
        // 3 s notification with long fades: 2 s of fades at most, in the same 2:3 proportion.
        assertEquals(800L to 1_200L, RingtonePlan.fadesFor(RingtoneFade.LONG, 3_000))
    }

    @Test fun filtersChain() {
        assertEquals(
            "afade=t=in:st=0:d=0.400,afade=t=out:st=28.800:d=1.200,aresample=44100",
            RingtonePlan.filters(30_000, RingtoneFade.SOFT, louder = false),
        )
        assertEquals("loudnorm=I=-12:TP=-1:LRA=11,aresample=44100", RingtonePlan.filters(3_000, RingtoneFade.NONE, louder = true))
    }

    @Test fun argsCutTheFirstSoundToMp3() {
        val args = RingtonePlan.args(root, input, out, RingRange(62_000, 92_000), RingtoneFade.SOFT, true, "Mon titre")
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("-ss 62.000 -i $input -t 30.000"))
        assertTrue(joined.contains("-map 0:a:0 -vn"))
        assertTrue(joined.contains("-c:a libmp3lame -b:a 192k"))
        assertTrue(args.contains("title=Mon titre"))
        assertEquals(out, args.last())
        assertTrue(joined.indexOf("-ss") < joined.indexOf("-i "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun argsRefusePathsOutsideTheWorkDir() {
        RingtonePlan.args(root, "/sdcard/x.mp3", out, RingRange(0, 1_000), RingtoneFade.NONE, false, "x")
    }

    @Test fun namesAndTitles() {
        assertEquals("Bohemian Rhapsody (sonnerie).mp3", RingtonePlan.outputName("Bohemian Rhapsody.m4a", RingtoneUse.RINGTONE))
        assertEquals("ding (notification).mp3", RingtonePlan.outputName("ding.wav", RingtoneUse.NOTIFICATION))
        assertEquals("Mon morceau", RingtonePlan.title("Mon_morceau.mp3"))
        assertFalse(RingtonePlan.title("   .mp3").isBlank())
    }

    @Test fun readouts() {
        assertEquals("1:02.0", RingtonePlan.timecode(62_000))
        assertEquals("30 s", RingtonePlan.length(30_000))
        assertEquals("3,5 s", RingtonePlan.length(3_500))
        assertEquals("1 min", RingtonePlan.length(60_000))
    }
}
