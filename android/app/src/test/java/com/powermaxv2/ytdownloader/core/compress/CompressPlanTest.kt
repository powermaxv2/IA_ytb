package com.powermaxv2.ytdownloader.core.compress

import com.powermaxv2.ytdownloader.core.files.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressPlanTest {
    private val root = "/data/user/0/app/cache/gulp-work"
    private val input = "$root/abc/input.mp4"
    private val output = "$root/abc/output.mp4"

    @Test fun mediaOfFiles() {
        assertEquals(Media.IMAGE, Media.of(FileKind.IMAGE, "photo.HEIC"))
        assertEquals(Media.OTHER, Media.of(FileKind.IMAGE, "anim.gif"))
        assertEquals(Media.VIDEO, Media.of(FileKind.VIDEO, "film.mkv"))
        assertEquals(Media.PDF, Media.of(FileKind.PDF, "devis.pdf"))
        assertEquals(Media.OTHER, Media.of(FileKind.ZIP, "contrat.docx"))
    }

    @Test fun outputNamesKeepTheBase() {
        assertEquals("Vacances (compressé).mp4", CompressPlan.outputName("Vacances.mov", "mp4"))
        assertEquals("v1.2 (compressé).jpg", CompressPlan.outputName("v1.2.png", "jpg"))
        assertTrue(CompressPlan.outputName("a".repeat(300) + ".jpg", "jpg").length <= 120)
        assertEquals("webp", CompressPlan.outputExt(Media.IMAGE, "logo.png", hasAlpha = true))
        assertEquals("jpg", CompressPlan.outputExt(Media.IMAGE, "logo.png"))
        assertEquals("mp3", CompressPlan.outputExt(Media.AUDIO, "chanson.MP3"))
        assertEquals("m4a", CompressPlan.outputExt(Media.AUDIO, "voix.wav"))
    }

    @Test fun levelsGetSmaller() {
        val light = CompressPlan.image(Level.LIGHT)
        val strong = CompressPlan.image(Level.STRONG)
        assertTrue(strong.maxSide < light.maxSide && strong.quality < light.quality)
        val info = MediaInfo(durationSec = 192.0, width = 3840, height = 2160)
        val v = CompressSettings(level = Level.BALANCED)
        assertEquals(1080, CompressPlan.video(info, v).maxHeight)
        assertNull(CompressPlan.video(info, v.copy(downscale = false)).maxHeight)
        assertEquals(720, CompressPlan.video(info, v.copy(level = Level.STRONG)).maxHeight)
        // A 720p video is never scaled up to 1080p.
        assertNull(CompressPlan.video(info.copy(width = 1280, height = 720), v).maxHeight)
    }

    @Test fun sizeGoalSetsTheBitrate() {
        val info = MediaInfo(durationSec = 60.0, width = 1920, height = 1080)
        val plan = CompressPlan.video(info, CompressSettings(goal = SizeGoal.EMAIL))
        assertNull(plan.crf)
        val kbps = plan.videoKbps!! + plan.audioKbps
        // 25 MB × 0.92 over 60 s ≈ 3066 kbit/s in all.
        assertTrue(kbps in 3000..3100)
        assertEquals(1080, plan.maxHeight ?: 1080)
        val expected = (kbps * 1000.0 / 8 * 60).toLong()
        assertTrue(expected <= SizeGoal.EMAIL.bytes)
        // Less room: fewer pixels.
        val tiny = CompressPlan.video(info.copy(durationSec = 300.0), CompressSettings(goal = SizeGoal.DISCORD))
        assertTrue(tiny.maxHeight!! <= 480)
    }

    @Test fun unreachableGoals() {
        assertTrue(CompressPlan.goalReachable(Media.VIDEO, 60.0, SizeGoal.DISCORD))
        assertFalse(CompressPlan.goalReachable(Media.VIDEO, 3600.0, SizeGoal.DISCORD))
        assertTrue(CompressPlan.goalReachable(Media.VIDEO, null, SizeGoal.DISCORD))
        assertTrue(CompressPlan.goalReachable(Media.IMAGE, 3600.0, SizeGoal.DISCORD))
        val max = CompressPlan.maxSeconds(Media.VIDEO, SizeGoal.DISCORD)
        assertTrue(CompressPlan.goalReachable(Media.VIDEO, max.toDouble(), SizeGoal.DISCORD))
        assertFalse(CompressPlan.goalReachable(Media.VIDEO, max + 30.0, SizeGoal.DISCORD))
    }

    @Test fun estimatesNeverExceedTheOriginal() {
        val photo = CompressPlan.estimate(Media.IMAGE, 4_000_000, MediaInfo(width = 4000, height = 3000), "a.jpg", CompressSettings())
        assertNotNull(photo)
        assertTrue(photo!! in 300_000..4_000_000)
        val small = CompressPlan.estimate(Media.IMAGE, 50_000, MediaInfo(width = 4000, height = 3000), "a.jpg", CompressSettings())
        assertEquals(50_000L, small)
        val video = CompressPlan.estimate(Media.VIDEO, 1_200_000_000, MediaInfo(192.0, 3840, 2160), "v.mp4", CompressSettings())!!
        assertTrue(video < 1_200_000_000 / 4)
        val goal = CompressPlan.estimate(Media.VIDEO, 1_200_000_000, MediaInfo(60.0, 1920, 1080), "v.mp4", CompressSettings(goal = SizeGoal.EMAIL))!!
        assertTrue(goal <= SizeGoal.EMAIL.bytes)
        assertNull(CompressPlan.estimate(Media.IMAGE, null, MediaInfo(), "a.jpg", CompressSettings()))
        val levels = Level.entries.map { CompressPlan.estimate(Media.VIDEO, 2_000_000_000, MediaInfo(600.0, 1920, 1080), "v.mp4", CompressSettings(level = it))!! }
        assertTrue(levels[0] > levels[1] && levels[1] > levels[2])
    }

    @Test fun zipEstimate() {
        val items = listOf(
            CompressPlan.ZipEstimateItem(Media.OTHER, "notes.txt", 1_000_000, MediaInfo()),
            CompressPlan.ZipEstimateItem(Media.OTHER, "archive.zip", 1_000_000, MediaInfo()),
        )
        val zip = CompressPlan.estimateZip(items, CompressSettings())!!
        assertTrue(zip in 1_300_000..1_400_000)
        assertNull(CompressPlan.estimateZip(items + CompressPlan.ZipEstimateItem(Media.OTHER, "x", null, MediaInfo()), CompressSettings()))
        assertTrue(CompressPlan.alreadyCompressed("IMG.JPG"))
        assertFalse(CompressPlan.alreadyCompressed("notes.txt"))
    }

    @Test fun videoArgs() {
        val crf = CompressPlan.videoArgs(root, input, output, VideoPlan(crf = 28, videoKbps = null, maxHeight = 1080, audioKbps = 96))
        assertEquals(output, crf.last())
        assertTrue(crf.containsAll(listOf("-crf", "28", "libx264", "aac", "96k", "+faststart")))
        assertTrue("-protocol_whitelist" in crf && "-format_whitelist" in crf)
        assertTrue(crf.any { it.contains("min(1080") })
        val rate = CompressPlan.videoArgs(root, input, output, VideoPlan(crf = null, videoKbps = 1000, maxHeight = null, audioKbps = 64))
        assertTrue(rate.containsAll(listOf("-b:v", "1000k", "-maxrate", "1300k", "-bufsize", "2000k")))
        assertFalse("-crf" in rate)
    }

    @Test fun audioArgs() {
        val mp3 = CompressPlan.audioArgs(root, "$root/a/input.mp3", "$root/a/output.mp3", AudioPlan(112, "mp3"))
        assertTrue(mp3.containsAll(listOf("libmp3lame", "112k", "-vn")))
        val m4a = CompressPlan.audioArgs(root, "$root/a/input.wav", "$root/a/output.m4a", AudioPlan(64, "m4a"))
        assertTrue(m4a.containsAll(listOf("aac", "64k")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathsOutsideTheWorkDirAreRefused() {
        CompressPlan.videoArgs(root, "/sdcard/DCIM/film.mp4", output, VideoPlan(28, null, null, 96))
    }

    @Test(expected = IllegalArgumentException::class)
    fun dotDotIsRefused() {
        CompressPlan.audioArgs(root, input, "$root/abc/../../x.m4a", AudioPlan(64, "m4a"))
    }
}
