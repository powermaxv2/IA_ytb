package com.powermaxv2.ytdownloader.core.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegArgsTest {
    private val input = "/data/user/0/app/cache/gulp-work/abc/Vacances.mkv"
    private val out = "/data/user/0/app/cache/gulp-work/abc/out/Vacances.mp4"
    private val prefix = listOf(
        "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
        "-protocol_whitelist", "file,pipe", "-i", input,
    )
    private val defaults = ConvertOptions()
    private val firstVideo = listOf("-map", "0:v:0", "-map", "0:a:0?")
    private val scaleOriginal = "scale=trunc(iw/2)*2:trunc(ih/2)*2"
    private val scale1080 = "scale='if(gt(iw,ih),-2,trunc(min(1080,iw)/2)*2)':'if(gt(iw,ih),trunc(min(1080,ih)/2)*2,-2)'"

    private fun args(target: Target, options: ConvertOptions = defaults, output: String = out) =
        FfmpegArgs.build(input, output, target, options)

    @Test fun mp4H264Aac() {
        assertEquals(
            prefix + firstVideo + listOf(
                "-vf", scale1080,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", out,
            ),
            args(Target.MP4, defaults.copy(resolution = Resolution.P1080)),
        )
    }

    @Test fun movIsH264InQuickTime() {
        val o = "/w/out/a.mov"
        assertEquals(
            prefix + firstVideo + listOf(
                "-vf", scaleOriginal,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "320k", "-movflags", "+faststart", o,
            ),
            args(Target.MOV, defaults.copy(quality = Quality.HIGH, audioBitrate = AudioBitrate.K320), o),
        )
    }

    @Test fun mkvH265() {
        val o = "/w/out/a.mkv"
        assertEquals(
            prefix + firstVideo + listOf(
                "-vf", FfmpegArgs.scale(Resolution.P720),
                "-c:v", "libx265", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", o,
            ),
            args(Target.MKV, ConvertOptions(Resolution.P720, Quality.LIGHT, AudioBitrate.K128), o),
        )
    }

    @Test fun mkvCopyRemuxesWithoutEncoding() {
        val o = "/w/out/a.mkv"
        assertEquals(
            prefix + listOf("-map", "0:v", "-map", "0:a?", "-c", "copy", o),
            args(Target.MKV, defaults.copy(copyStreams = true, resolution = Resolution.P480), o),
        )
    }

    @Test fun webmVp9Opus() {
        val o = "/w/out/a.webm"
        assertEquals(
            prefix + firstVideo + listOf(
                "-vf", scaleOriginal,
                "-c:v", "libvpx-vp9", "-crf", "33", "-b:v", "0",
                "-deadline", "good", "-cpu-used", "5", "-row-mt", "1", "-pix_fmt", "yuv420p",
                "-c:a", "libopus", "-b:a", "192k", "-ac", "2", o,
            ),
            args(Target.WEBM, defaults, o),
        )
    }

    @Test fun aviMpeg4Mp3() {
        val o = "/w/out/a.avi"
        assertEquals(
            prefix + firstVideo + listOf(
                "-vf", FfmpegArgs.scale(Resolution.P480),
                "-c:v", "mpeg4", "-vtag", "xvid", "-q:v", "5", "-pix_fmt", "yuv420p",
                "-c:a", "libmp3lame", "-b:a", "192k", "-ac", "2", o,
            ),
            args(Target.AVI, defaults.copy(resolution = Resolution.P480), o),
        )
    }

    @Test fun gifPaletteFrameByFrameAndCapped() {
        val o = "/w/out/a.gif"
        assertEquals(
            prefix + listOf(
                "-map", "0:v:0", "-an", "-t", "30",
                "-vf",
                "fps=12,scale='if(gt(iw,ih),min(480,iw),-2)':'if(gt(iw,ih),-2,min(480,ih))':flags=lanczos," +
                    "split[a][b];[a]palettegen=stats_mode=single[p];[b][p]paletteuse=new=1:dither=bayer:bayer_scale=4",
                "-loop", "0", o,
            ),
            args(Target.GIF, defaults, o),
        )
        assertEquals(30, FfmpegArgs.maxSeconds(Target.GIF))
        assertEquals(null, FfmpegArgs.maxSeconds(Target.MP4))
    }

    @Test fun audioTargets() {
        val audio = listOf("-map", "0:a:0")
        assertEquals(prefix + audio + listOf("-c:a", "libmp3lame", "-b:a", "192k", "-ac", "2", "/w/a.mp3"), args(Target.MP3, output = "/w/a.mp3"))
        assertEquals(prefix + audio + listOf("-c:a", "aac", "-b:a", "320k", "-movflags", "+faststart", "/w/a.m4a"), args(Target.M4A, defaults.copy(audioBitrate = AudioBitrate.K320), "/w/a.m4a"))
        assertEquals(prefix + audio + listOf("-c:a", "libopus", "-b:a", "128k", "-ac", "2", "/w/a.opus"), args(Target.OPUS, defaults.copy(audioBitrate = AudioBitrate.K128), "/w/a.opus"))
        assertEquals(prefix + audio + listOf("-c:a", "libvorbis", "-b:a", "192k", "/w/a.ogg"), args(Target.OGG, output = "/w/a.ogg"))
        assertEquals(prefix + audio + listOf("-c:a", "flac", "/w/a.flac"), args(Target.FLAC, output = "/w/a.flac"))
        assertEquals(prefix + audio + listOf("-c:a", "pcm_s16le", "/w/a.wav"), args(Target.WAV, output = "/w/a.wav"))
    }

    @Test fun stillImagesFromPng() {
        val png = "/w/photo.png"
        val pre = listOf("-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1", "-protocol_whitelist", "file,pipe", "-i", png)
        assertEquals(pre + listOf("-frames:v", "1", "-update", "1", "-c:v", "bmp", "/w/out/photo.bmp"), FfmpegArgs.build(png, "/w/out/photo.bmp", Target.BMP, defaults))
        assertEquals(
            pre + listOf("-frames:v", "1", "-update", "1", "-c:v", "tiff", "-compression_algo", "lzw", "/w/out/photo.tiff"),
            FfmpegArgs.build(png, "/w/out/photo.tiff", Target.TIFF, defaults),
        )
    }

    @Test fun everyResolutionKeepsEvenSidesAndNeverUpscales() {
        Resolution.entries.filter { it.height != null }.forEach { r ->
            val f = FfmpegArgs.scale(r)
            assertTrue(f, f.contains("min(${r.height},iw)") && f.contains("min(${r.height},ih)"))
            assertTrue(f, f.contains("trunc(") && f.contains("/2)*2"))
        }
    }

    @Test fun refusesRelativePathsAndOptionLookingNames() {
        assertThrows { FfmpegArgs.build("-i", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build("video.mkv", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(input, "-f", Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(input, input, Target.MP4, defaults) }
        // Formats made without FFmpeg.
        assertThrows { args(Target.JPG) }
        assertThrows { args(Target.HTML) }
    }

    @Test fun noArgumentStartsWithDashAfterTheOutputPosition() {
        Target.entries.filter { it.family == Family.VIDEO || it.family == Family.AUDIO }.forEach { t ->
            val a = args(t, output = "/w/out/x.${t.ext}")
            assertEquals("/w/out/x.${t.ext}", a.last())
            assertFalse(a.contains("-f"))
        }
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
