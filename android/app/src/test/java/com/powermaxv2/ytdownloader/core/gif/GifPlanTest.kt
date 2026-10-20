package com.powermaxv2.ytdownloader.core.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifPlanTest {
    private val root = "/data/user/0/app/cache/gulp-work"

    @Test fun originalShapeKeepsTheWholePictureAndShrinksTheLongestSide() {
        val g = GifPlan.geometry(1920, 1080, GifShape.ORIGINAL, 480)
        assertEquals(GifCrop(0, 0, 1920, 1080), g.crop)
        assertEquals(480, g.width)
        assertEquals(270, g.height)
    }

    @Test fun squareAndPortraitCropTheCentre() {
        val square = GifPlan.geometry(1920, 1080, GifShape.SQUARE, 480)
        assertEquals(GifCrop(420, 0, 1080, 1080), square.crop)
        assertEquals(480 to 480, square.width to square.height)
        val portrait = GifPlan.geometry(1080, 1920, GifShape.PORTRAIT, 640)
        assertEquals(1080, portrait.crop.width)
        assertEquals(1350, portrait.crop.height)
        assertEquals((1920 - 1350) / 2, portrait.crop.y)
        assertEquals(512 to 640, portrait.width to portrait.height)
    }

    @Test fun neverEnlargesASmallVideoAndKeepsEvenSides() {
        val g = GifPlan.geometry(301, 199, GifShape.ORIGINAL, 640)
        assertEquals(300, g.width)
        assertEquals(198, g.height)
    }

    @Test fun photoGeometryUsesTheFirstPhotoProportionsAtFullSide() {
        val g = GifPlan.photoGeometry(240, 320, GifShape.ORIGINAL, 480)
        assertEquals(360 to 480, g.width to g.height)
        assertEquals(480 to 480, GifPlan.photoGeometry(240, 320, GifShape.SQUARE, 480).let { it.width to it.height })
    }

    @Test fun boomerangDoublesAndSpeedDividesTheOutput() {
        assertEquals(3_000L, GifPlan.outputMs(3_000, GifSpeed.NORMAL, GifEffect.NORMAL))
        assertEquals(6_000L, GifPlan.outputMs(3_000, GifSpeed.HALF, GifEffect.NORMAL))
        assertEquals(3_000L, GifPlan.outputMs(3_000, GifSpeed.DOUBLE, GifEffect.BOOMERANG))
        assertEquals(2_000L, GifPlan.outputMs(3_000, GifSpeed.FAST, GifEffect.REVERSE))
    }

    @Test fun maxSourceFollowsTheMemoryBudgetAndTheCaps() {
        val small = GifPlan.geometry(1920, 1080, GifShape.ORIGINAL, 320)
        // 320 × 180 at 12 i/s fits 15 s easily.
        assertEquals(GifPlan.MAX_SOURCE_MS, GifPlan.maxSourceMs(small, GifSmooth.NORMAL, GifSpeed.NORMAL, GifEffect.NORMAL))
        // Slow motion doubles the output: 30 s of GIF is 15 s of video at ×0,5... capped by 30 s of output.
        assertEquals(15_000L, GifPlan.maxSourceMs(small, GifSmooth.NORMAL, GifSpeed.HALF, GifEffect.NORMAL))
        assertEquals(7_500L, GifPlan.maxSourceMs(small, GifSmooth.NORMAL, GifSpeed.HALF, GifEffect.BOOMERANG))
        // 640 × 640 at 20 i/s: 40 M / 409 600 = 97 frames = 4,85 s.
        val big = GifPlan.geometry(1080, 1080, GifShape.SQUARE, 640)
        assertEquals(4_800L, GifPlan.maxSourceMs(big, GifSmooth.SMOOTH, GifSpeed.NORMAL, GifEffect.NORMAL))
        assertEquals(2_400L, GifPlan.maxSourceMs(big, GifSmooth.SMOOTH, GifSpeed.NORMAL, GifEffect.BOOMERANG))
        // Every case leaves at least the shortest passage.
        val huge = GifGeometry(GifCrop(0, 0, 8000, 8000), 8000, 8000)
        assertEquals(GifPlan.MIN_MS, GifPlan.maxSourceMs(huge, GifSmooth.SMOOTH, GifSpeed.NORMAL, GifEffect.BOOMERANG))
    }

    @Test fun passageStaysInsideTheVideoAndTheLimit() {
        assertEquals(GifRange(0, 3_000), GifPlan.initial(60_000, 15_000))
        assertEquals(GifRange(0, 2_000), GifPlan.initial(2_000, 15_000))
        assertEquals(GifRange(0, 2_400), GifPlan.initial(60_000, 2_400))
        assertEquals(GifRange(10_000, 15_000), GifPlan.clamp(GifRange(10_000, 40_000), 60_000, 5_000))
        assertEquals(GifRange(59_500, 60_000), GifPlan.clamp(GifRange(70_000, 80_000), 60_000, 5_000))
    }

    @Test fun handlesKeepTheMinimumAndTheMaximum() {
        val r = GifRange(10_000, 13_000)
        assertEquals(GifRange(12_500, 13_000), GifPlan.moveStart(r, 12_900, 60_000, 5_000))
        assertEquals(GifRange(8_000, 13_000), GifPlan.moveStart(r, 0, 60_000, 5_000))
        assertEquals(GifRange(10_000, 15_000), GifPlan.moveEnd(r, 50_000, 60_000, 5_000))
        assertEquals(GifRange(10_000, 10_500), GifPlan.moveEnd(r, 9_000, 60_000, 5_000))
    }

    @Test fun framesPlayForwardBackwardOrThereAndBack() {
        assertEquals(listOf(0, 1, 2, 3), GifPlan.order(4, GifEffect.NORMAL))
        assertEquals(listOf(3, 2, 1, 0), GifPlan.order(4, GifEffect.REVERSE))
        assertEquals(listOf(0, 1, 2, 3, 2, 1), GifPlan.order(4, GifEffect.BOOMERANG))
        assertEquals(listOf(0, 1), GifPlan.order(2, GifEffect.BOOMERANG))
        assertEquals(emptyList<Int>(), GifPlan.order(0, GifEffect.BOOMERANG))
    }

    @Test fun videoFramesAndPreviewTiming() {
        val settings = GifSettings(smooth = GifSmooth.NORMAL, speed = GifSpeed.HALF, effect = GifEffect.BOOMERANG)
        // 2 s at ×0,5 = 4 s, there and back = 8 s, 12 i/s.
        assertEquals(96, GifPlan.videoFrames(GifRange(0, 2_000), settings))
        assertEquals(listOf(1_250L, 1_750L, 2_250L, 2_750L), GifPlan.previewTimes(GifRange(1_000, 3_000), 4))
        assertEquals(1_000L, GifPlan.previewFrameMs(GifRange(1_000, 3_000), 4, GifSpeed.HALF))
    }

    @Test fun estimateGrowsWithPixelsFramesAndColours() {
        val g = GifPlan.geometry(1920, 1080, GifShape.ORIGINAL, 480)
        val base = GifPlan.estimateBytes(g, 36, 256, photos = false)
        assertTrue(base in 1_000_000L..1_600_000L)
        assertTrue(GifPlan.estimateBytes(g, 36, 128, photos = false) < base)
        assertTrue(GifPlan.estimateBytes(g, 72, 256, photos = false) > base)
        assertTrue(GifPlan.estimateBytes(g, 36, 256, photos = true) > base)
    }

    @Test fun captionIsOneCleanShortLine() {
        assertEquals("Quand le café fait effet", GifPlan.cleanCaption("  Quand le\ncafé \t fait   effet "))
        assertEquals(GifPlan.CAPTION_MAX, GifPlan.cleanCaption("x".repeat(100)).length)
        assertEquals("", GifPlan.cleanCaption("\n\t "))
        assertEquals(57.6f, GifPlan.captionSize(480, 480), 0.01f)
    }

    @Test fun videoGraphChainsSpeedCropEffectCaptionAndPalette() {
        val g = GifPlan.geometry(1920, 1080, GifShape.SQUARE, 480)
        val graph = GifPlan.videoGraph(g, GifSettings(speed = GifSpeed.DOUBLE, effect = GifEffect.BOOMERANG, smooth = GifSmooth.SMOOTH, size = GifSize.MEDIUM), caption = true)
        assertTrue(graph.startsWith("[0:v]setpts=(PTS-STARTPTS)/2,fps=20,crop=1080:1080:420:0,scale=480:480:flags=lanczos,setsar=1"))
        assertTrue(graph.contains("split[fw][bw];[bw]reverse[rv];[fw][rv]concat=n=2:v=1:a=0[v]"))
        assertTrue(graph.contains("[v][cap]overlay=0:0:eof_action=repeat[t];[t] split[pa][pb]"))
        assertTrue(graph.contains("palettegen=max_colors=256:stats_mode=diff"))
        assertTrue(graph.endsWith("paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle[gif]"))
        val plain = GifPlan.videoGraph(g, GifSettings(speed = GifSpeed.FAST, size = GifSize.SMALL), caption = false)
        assertTrue(plain.startsWith("[0:v]setpts=(PTS-STARTPTS)/1.5,"))
        assertTrue(plain.contains("[v];[v] split"))
        assertTrue(plain.contains("max_colors=128"))
        assertFalse(plain.contains("reverse"))
        assertFalse(plain.contains("overlay"))
        assertTrue(GifPlan.videoGraph(g, GifSettings(effect = GifEffect.REVERSE), caption = false).contains("setsar=1,reverse[v]"))
    }

    @Test fun videoArgsSeekBeforeTheInputAndStayInTheWorkDir() {
        val g = GifPlan.geometry(1280, 720, GifShape.ORIGINAL, 480)
        val args = GifPlan.videoArgs(root, "$root/a/input.mp4", "$root/a/caption.png", "$root/a/out/output.gif", GifRange(1_500, 4_000), g, GifSettings())
        val input = args.indexOf("$root/a/input.mp4")
        assertEquals("-i", args[input - 1])
        assertEquals(listOf("-ss", "1.500", "-t", "2.500"), args.subList(input - 5, input - 1))
        assertTrue(args.containsAll(listOf("-pattern_type", "none", "$root/a/caption.png")))
        assertEquals(listOf("-map", "[gif]", "-an", "-loop", "0", "-f", "gif", "$root/a/out/output.gif"), args.takeLast(8))
        assertThrows { GifPlan.videoArgs(root, "/sdcard/x.mp4", null, "$root/a/o.gif", GifRange(0, 1_000), g, GifSettings()) }
        assertThrows { GifPlan.videoArgs(root, "$root/a/x.mp4", null, "$root/a/x.mp4", GifRange(0, 1_000), g, GifSettings()) }
        assertThrows { GifPlan.videoArgs(root, "$root/a/x.mp4", null, "$root/a/o.gif", GifRange(1_000, 1_000), g, GifSettings()) }
    }

    @Test fun photoArgsReadTheNumberedFramesAtThePace() {
        val args = GifPlan.photoArgs(root, "$root/b/frame%03d.png", "$root/b/out/output.gif", 400, 256)
        assertTrue(args.containsAll(listOf("-start_number", "1", "-framerate", "1000/400", "$root/b/frame%03d.png")))
        assertTrue(args.contains("[0:v] split[pa][pb];[pa]palettegen=max_colors=256:stats_mode=diff[pal];[pb][pal]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle[gif]"))
        assertEquals("$root/b/out/output.gif", args.last())
        assertThrows { GifPlan.photoArgs(root, "$root/../frame%03d.png", "$root/b/o.gif", 400, 256) }
    }

    @Test fun namesAndDurations() {
        assertEquals("chat qui saute (GIF).gif", GifPlan.outputName("chat qui saute.mp4", 14, 10, 2026, 21, 5))
        assertEquals("GIF 14-10-2026 21h05.gif", GifPlan.outputName(null, 14, 10, 2026, 21, 5))
        assertEquals("3,4 s", GifPlan.shortDuration(3_400))
        assertEquals("3 s", GifPlan.shortDuration(3_000))
        assertEquals("12 s", GifPlan.shortDuration(12_300))
        assertEquals("0,2 s", GifPlan.shortDuration(150))
        assertEquals("1:02.5", GifPlan.timecode(62_500))
        assertEquals("1.050", GifPlan.seconds(1_050))
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("IllegalArgumentException attendue")
    }
}
