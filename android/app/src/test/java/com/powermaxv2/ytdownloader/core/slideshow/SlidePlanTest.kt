package com.powermaxv2.ytdownloader.core.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidePlanTest {
    private fun slides(vararg ms: Long) = ms.mapIndexed { i, d -> Slide(i.toLong(), "p$i.jpg", d) }

    private val root = "/data/cache/gulp-work"
    private fun photos(n: Int) = (1..n).map { "$root/job/slide00$it.jpg" }

    @Test fun durationsCycleAndAreWrittenTheFrenchWay() {
        assertEquals(4_000L, SlidePlan.nextDuration(3_000L))
        assertEquals(1_500L, SlidePlan.nextDuration(7_000L))
        assertEquals(3_000L, SlidePlan.nextDuration(2_500L))
        assertEquals(1_500L, SlidePlan.nextDuration(60_000L))
        assertEquals("1,5 s", SlidePlan.seconds(1_500L))
        assertEquals("3 s", SlidePlan.seconds(3_000L))
    }

    @Test fun cutsAddUpEveryPhoto() {
        val s = slides(3_000, 2_000, 1_500)
        assertEquals(0, SlidePlan.transitionFrames(s, SlideTransition.CUT))
        assertEquals(195, SlidePlan.totalFrames(s, SlideTransition.CUT))
        assertEquals(6_500L, SlidePlan.totalMs(s, SlideTransition.CUT))
    }

    @Test fun transitionsOverlapAndAreShortenedForQuickPhotos() {
        val s = slides(3_000, 3_000, 3_000)
        assertEquals(18, SlidePlan.transitionFrames(s, SlideTransition.FADE))
        assertEquals(270 - 2 * 18, SlidePlan.totalFrames(s, SlideTransition.FADE))
        assertEquals(90 - 18, SlidePlan.offsetFrames(s, SlideTransition.FADE, 1))
        assertEquals(180 - 36, SlidePlan.offsetFrames(s, SlideTransition.FADE, 2))
        // 1,5 s = 45 frames: the transition never takes more than half a photo.
        val quick = slides(3_000, 1_500)
        assertEquals(18, SlidePlan.transitionFrames(quick, SlideTransition.SLIDE))
        val tiny = slides(1_000, 1_000)
        assertEquals(15, SlidePlan.transitionFrames(tiny, SlideTransition.SLIDE))
        assertEquals(0, SlidePlan.transitionFrames(slides(3_000), SlideTransition.FADE))
    }

    @Test fun refusals() {
        assertNotNull(SlidePlan.refusal(0))
        assertNull(SlidePlan.refusal(1))
        assertNull(SlidePlan.refusal(SlidePlan.MAX_PHOTOS))
        assertTrue(SlidePlan.refusal(SlidePlan.MAX_PHOTOS + 2)!!.contains("2"))
    }

    @Test fun placementCoversOrFits() {
        // A landscape photo in a vertical frame.
        val fill = SlidePlan.placement(4000, 3000, 720, 1280, SlideFit.FILL)
        assertEquals(1280f, fill.height, 0.01f)
        assertTrue(fill.width > 720f)
        assertEquals((720f - fill.width) / 2f, fill.left, 0.01f)
        val fit = SlidePlan.placement(4000, 3000, 720, 1280, SlideFit.FIT)
        assertEquals(720f, fit.width, 0.01f)
        assertEquals(540f, fit.height, 0.01f)
        assertEquals(370f, fit.top, 0.01f)
    }

    @Test fun kenBurnsFramesAreDrawnLarger() {
        assertEquals(1440 to 2560, SlidePlan.frameSize(SlideFormat.VERTICAL, SlideTransition.KEN_BURNS))
        assertEquals(720 to 1280, SlidePlan.frameSize(SlideFormat.VERTICAL, SlideTransition.FADE))
        SlideFormat.entries.forEach { assertTrue(it.width % 2 == 0 && it.height % 2 == 0) }
    }

    @Test fun outputName() {
        assertEquals("Diaporama 03-07-2026 09h05.mp4", SlidePlan.outputName(3, 7, 2026, 9, 5))
    }

    @Test fun fadeGraphChainsXfadesAtFrameExactOffsets() {
        val s = slides(3_000, 3_000, 3_000)
        val g = SlidePlan.filterGraph(s, SlideFormat.VERTICAL, SlideTransition.FADE, music = false)
        assertTrue(g, g.contains("[0:v]scale=720:1280,fps=30,trim=end_frame=90"))
        assertTrue(g, g.contains("[v0][v1]xfade=transition=fade:duration=0.600000:offset=2.400000[x1]"))
        assertTrue(g, g.contains("[x1][v2]xfade=transition=fade:duration=0.600000:offset=4.800000[x2]"))
        assertTrue(g, g.contains("[x2]fade=t=in:st=0:d=0.400,fade=t=out:st=7.200:d=0.600[vout]"))
        assertFalse(g.contains("[aout]"))
    }

    @Test fun cutGraphConcatsAndSlideUsesSlideleft() {
        val s = slides(2_000, 2_000)
        assertTrue(SlidePlan.filterGraph(s, SlideFormat.SQUARE, SlideTransition.CUT, false).contains("[v0][v1]concat=n=2:v=1:a=0,"))
        assertTrue(SlidePlan.filterGraph(s, SlideFormat.SQUARE, SlideTransition.SLIDE, false).contains("xfade=transition=slideleft"))
    }

    @Test fun kenBurnsZoomsInThenOut() {
        val g = SlidePlan.filterGraph(slides(3_000, 3_000), SlideFormat.VERTICAL, SlideTransition.KEN_BURNS, false)
        assertTrue(g, g.contains("[0:v]zoompan=z=1+0.15*on/89:x=(iw-iw/zoom)/2:y=(ih-ih/zoom)/2:d=90:s=720x1280:fps=30"))
        assertTrue(g, g.contains("[1:v]zoompan=z=1+0.15-0.15*on/89:x=(iw-iw/zoom)*on/89"))
        // No comma or quote inside an expression: the graph splits on commas.
        assertFalse(g.contains("'"))
    }

    @Test fun singlePhotoFadesInAndOut() {
        val g = SlidePlan.filterGraph(slides(3_000), SlideFormat.VERTICAL, SlideTransition.FADE, false)
        assertTrue(g, g.endsWith("[v0]fade=t=in:st=0:d=0.400,fade=t=out:st=2.400:d=0.600[vout]"))
    }

    @Test fun musicLoopsIsTrimmedAndFadesOut() {
        val s = slides(3_000, 3_000)
        val music = "$root/job/music.mp3"
        val args = SlidePlan.args(root, photos(2), s, music, "$root/job/out/slideshow.mp4", SlideFormat.VERTICAL, SlideTransition.FADE)
        val loop = args.indexOf("-stream_loop")
        assertTrue(loop > 0)
        assertEquals("-1", args[loop + 1])
        assertEquals(music, args[args.indexOf(music)])
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertTrue(graph, graph.contains("[2:a]atrim=end=5.400,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.500,afade=t=out:st=3.900:d=1.500[aout]"))
        assertEquals(listOf("-map", "[vout]", "-map", "[aout]"), args.subList(args.indexOf("-map"), args.indexOf("-map") + 4))
        assertTrue(args.containsAll(listOf("libx264", "aac", "+faststart")))
        assertEquals("5.400", args[args.lastIndexOf("-t") + 1])
        assertEquals("$root/job/out/slideshow.mp4", args.last())
    }

    @Test fun everyInputIsWhitelistedAndImagesAreNeverPatterns() {
        val args = SlidePlan.args(root, photos(3), slides(2_000, 2_000, 2_000), null, "$root/job/out/s.mp4", SlideFormat.SQUARE, SlideTransition.CUT)
        val inputs = args.indices.filter { args[it] == "-i" }
        assertEquals(3, inputs.size)
        assertEquals(3, args.count { it == "-protocol_whitelist" })
        assertEquals(3, args.count { it == "-format_whitelist" })
        assertEquals(3, args.count { it == "none" })
        assertFalse(args.contains("-c:a"))
        assertFalse(args.contains("[aout]"))
    }

    @Test fun pathsOutsideTheWorkDirAreRefused() {
        val s = slides(2_000)
        assertThrows { SlidePlan.args(root, listOf("/sdcard/a.jpg"), s, null, "$root/job/o.mp4", SlideFormat.SQUARE, SlideTransition.CUT) }
        assertThrows { SlidePlan.args(root, photos(1), s, "$root/../x.mp3", "$root/job/o.mp4", SlideFormat.SQUARE, SlideTransition.CUT) }
        assertThrows { SlidePlan.args(root, photos(1), s, null, photos(1).first(), SlideFormat.SQUARE, SlideTransition.CUT) }
        assertThrows { SlidePlan.args(root, photos(2), s, null, "$root/job/o.mp4", SlideFormat.SQUARE, SlideTransition.CUT) }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
