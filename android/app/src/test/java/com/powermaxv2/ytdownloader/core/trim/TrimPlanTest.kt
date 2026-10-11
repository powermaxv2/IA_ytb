package com.powermaxv2.ytdownloader.core.trim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimPlanTest {
    private val root = "/data/cache/gulp-work"
    private val input = "$root/job/input.mp4"
    private val out = "$root/job/output.mp4"

    @Test fun soundFilesOfferNoVideoNorGif() {
        assertEquals(listOf(TrimOutput.AUDIO, TrimOutput.RINGTONE), TrimPlan.outputs(hasVideo = false))
        assertEquals(TrimOutput.entries.toList(), TrimPlan.outputs(hasVideo = true))
    }

    @Test fun initialRangeIsTheWholeFileOrTheLimit() {
        assertEquals(TrimRange(0, 227_000), TrimPlan.initial(227_000, TrimOutput.VIDEO))
        assertEquals(TrimRange(0, 40_000), TrimPlan.initial(227_000, TrimOutput.RINGTONE))
        assertEquals(TrimRange(0, 30_000), TrimPlan.initial(227_000, TrimOutput.GIF))
        assertEquals(TrimRange(0, 12_000), TrimPlan.initial(12_000, TrimOutput.GIF))
    }

    @Test fun clampKeepsThePassageInsideTheFile() {
        assertEquals(TrimRange(0, 10_000), TrimPlan.clamp(TrimRange(-5_000, 20_000), 10_000, TrimOutput.VIDEO))
        // Never shorter than half a second.
        assertEquals(TrimRange(4_000, 4_500), TrimPlan.clamp(TrimRange(4_000, 4_100), 10_000, TrimOutput.VIDEO))
        assertEquals(TrimRange(9_500, 10_000), TrimPlan.clamp(TrimRange(10_000, 10_000), 10_000, TrimOutput.VIDEO))
        // Ringtone: 40 s at most.
        assertEquals(TrimRange(50_000, 90_000), TrimPlan.clamp(TrimRange(50_000, 145_000), 227_000, TrimOutput.RINGTONE))
        // A very short file.
        assertEquals(TrimRange(0, 300), TrimPlan.clamp(TrimRange(0, 300), 300, TrimOutput.VIDEO))
    }

    @Test fun handlesNeverCross() {
        val r = TrimRange(50_000, 145_500)
        assertEquals(TrimRange(145_000, 145_500), TrimPlan.moveStart(r, 200_000, 227_000, TrimOutput.VIDEO))
        assertEquals(TrimRange(50_000, 50_500), TrimPlan.moveEnd(r, 0, 227_000, TrimOutput.VIDEO))
        assertEquals(TrimRange(0, 145_500), TrimPlan.moveStart(r, -10, 227_000, TrimOutput.VIDEO))
        assertEquals(TrimRange(50_000, 227_000), TrimPlan.moveEnd(r, 999_000, 227_000, TrimOutput.VIDEO))
    }

    @Test fun handlesRespectTheMaximum() {
        val r = TrimRange(60_000, 80_000)
        // GIF: the start cannot go more than 30 s before the end, the end more than 30 s after the start.
        assertEquals(TrimRange(50_000, 80_000), TrimPlan.moveStart(r, 0, 227_000, TrimOutput.GIF))
        assertEquals(TrimRange(60_000, 90_000), TrimPlan.moveEnd(r, 200_000, 227_000, TrimOutput.GIF))
    }

    @Test fun videoIsCopiedUnlessExact() {
        assertTrue(TrimPlan.copies(TrimOutput.VIDEO, "mp4", hasVideo = true, exact = false))
        assertFalse(TrimPlan.copies(TrimOutput.VIDEO, "mp4", hasVideo = true, exact = true))
        assertFalse(TrimPlan.copies(TrimOutput.VIDEO, "avi", hasVideo = true, exact = false))
        assertEquals("mp4", TrimPlan.outputExt(TrimOutput.VIDEO, "MOV", hasVideo = true, exact = false))
        assertEquals("mkv", TrimPlan.outputExt(TrimOutput.VIDEO, "mkv", hasVideo = true, exact = false))
        assertEquals("mp4", TrimPlan.outputExt(TrimOutput.VIDEO, "mkv", hasVideo = true, exact = true))
        assertEquals("mp4", TrimPlan.outputExt(TrimOutput.VIDEO, "avi", hasVideo = true, exact = false))
    }

    @Test fun soundKeepsItsFormatWhenItCan() {
        assertEquals("mp3", TrimPlan.outputExt(TrimOutput.AUDIO, "mp3", hasVideo = false, exact = false))
        assertEquals("m4a", TrimPlan.outputExt(TrimOutput.AUDIO, "wma", hasVideo = false, exact = false))
        // The sound of a video is always AAC.
        assertEquals("m4a", TrimPlan.outputExt(TrimOutput.AUDIO, "mp4", hasVideo = true, exact = false))
        assertEquals("mp3", TrimPlan.outputExt(TrimOutput.RINGTONE, "mp4", hasVideo = true, exact = false))
        assertEquals("gif", TrimPlan.outputExt(TrimOutput.GIF, "mp4", hasVideo = true, exact = false))
    }

    @Test fun copiedVideoSeeksBeforeTheInput() {
        val args = TrimPlan.args(root, input, out, TrimRange(50_000, 145_500), TrimOutput.VIDEO, "mp4", hasVideo = true, exact = false)
        val i = args.indexOf("-i")
        assertEquals("-ss", args[i - 2])
        assertEquals("50.000", args[i - 1])
        assertEquals(input, args[i + 1])
        assertEquals(listOf("-t", "95.500"), args.subList(i + 2, i + 4))
        assertTrue(args.containsAll(listOf("-c", "copy", "+faststart")))
        assertEquals(out, args.last())
        // Same sandbox as the Convertisseur: only local files, only real media demuxers.
        assertTrue(args.containsAll(listOf("-protocol_whitelist", "file,pipe", "-format_whitelist")))
    }

    @Test fun exactVideoIsReencoded() {
        val args = TrimPlan.args(root, input, out, TrimRange(0, 1_000), TrimOutput.VIDEO, "mp4", hasVideo = true, exact = true)
        assertTrue(args.containsAll(listOf("libx264", "aac")))
        assertFalse(args.contains("copy"))
    }

    @Test fun ringtoneFadesAndGifIsMuted() {
        val ring = TrimPlan.args(root, input, "$root/job/output.mp3", TrimRange(0, 40_000), TrimOutput.RINGTONE, "mp4", hasVideo = true, exact = false)
        assertEquals("afade=t=in:st=0:d=0.3,afade=t=out:st=39.000:d=1", ring[ring.indexOf("-af") + 1])
        assertTrue(ring.contains("libmp3lame"))
        val gif = TrimPlan.args(root, input, "$root/job/output.gif", TrimRange(0, 5_000), TrimOutput.GIF, "mp4", hasVideo = true, exact = false)
        assertTrue(gif.contains("-an"))
        assertTrue(gif[gif.indexOf("-vf") + 1].startsWith("fps="))
        assertEquals("afade=t=in:st=0:d=0.3", TrimPlan.fades(2_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathsMustStayInTheWorkDir() {
        TrimPlan.args(root, "/sdcard/x.mp4", out, TrimRange(0, 1_000), TrimOutput.VIDEO, "mp4", hasVideo = true, exact = false)
    }

    @Test fun timesAndNames() {
        assertEquals("0:50.0", TrimPlan.timecode(50_000))
        assertEquals("1:35.5", TrimPlan.timecode(95_500))
        assertEquals("1:02:03.4", TrimPlan.timecode(3_723_400))
        assertEquals("0m50s", TrimPlan.stamp(50_000))
        assertEquals("1h02m03s", TrimPlan.stamp(3_723_999))
        assertEquals(
            "concert-lyon (0m50s-2m25s).mp4",
            TrimPlan.outputName("concert-lyon.mp4", TrimRange(50_000, 145_500), TrimOutput.VIDEO, "mp4"),
        )
        assertEquals("concert-lyon (sonnerie).mp3", TrimPlan.outputName("concert-lyon.mp4", TrimRange(0, 1), TrimOutput.RINGTONE, "mp3"))
        assertTrue(TrimPlan.outputName("a".repeat(300) + ".mp4", TrimRange(0, 1_000), TrimOutput.VIDEO, "mp4").length <= 120)
    }
}
