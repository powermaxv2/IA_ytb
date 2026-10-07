package com.powermaxv2.ytdownloader.core.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROOT = "/data/user/0/app/cache/gulp-work"
private const val W = "$ROOT/abc"

class FfmpegArgsTest {
    private val input = "$W/input.mkv"
    private val out = "$W/output.mp4"
    private val prefix = listOf(
        "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
        "-protocol_whitelist", "file,pipe", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST, "-i", input,
    )
    private val defaults = ConvertOptions()
    private val firstVideo = listOf("-map", "0:v:0", "-map", "0:a:0?")
    private val scaleOriginal = "scale=trunc(iw/2)*2:trunc(ih/2)*2"
    private val scale1080 = "scale='if(gt(iw,ih),-2,trunc(min(1080,iw)/2)*2)':'if(gt(iw,ih),trunc(min(1080,ih)/2)*2,-2)'"

    private fun args(target: Target, options: ConvertOptions = defaults, output: String = out) =
        FfmpegArgs.build(ROOT, input, output, target, options)

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
        val o = "$W/out/a.mov"
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
        val o = "$W/out/a.mkv"
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
        val o = "$W/out/a.mkv"
        assertEquals(
            prefix + listOf("-map", "0:v", "-map", "0:a?", "-c", "copy", o),
            args(Target.MKV, defaults.copy(copyStreams = true, resolution = Resolution.P480), o),
        )
    }

    @Test fun webmVp9Opus() {
        val o = "$W/out/a.webm"
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
        val o = "$W/out/a.avi"
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
        val o = "$W/out/a.gif"
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
        assertEquals(prefix + audio + listOf("-c:a", "libmp3lame", "-b:a", "192k", "-ac", "2", "$W/a.mp3"), args(Target.MP3, output = "$W/a.mp3"))
        assertEquals(prefix + audio + listOf("-c:a", "aac", "-b:a", "320k", "-movflags", "+faststart", "$W/a.m4a"), args(Target.M4A, defaults.copy(audioBitrate = AudioBitrate.K320), "$W/a.m4a"))
        assertEquals(prefix + audio + listOf("-c:a", "libopus", "-b:a", "128k", "-ac", "2", "$W/a.opus"), args(Target.OPUS, defaults.copy(audioBitrate = AudioBitrate.K128), "$W/a.opus"))
        assertEquals(prefix + audio + listOf("-c:a", "libvorbis", "-b:a", "192k", "$W/a.ogg"), args(Target.OGG, output = "$W/a.ogg"))
        assertEquals(prefix + audio + listOf("-c:a", "flac", "$W/a.flac"), args(Target.FLAC, output = "$W/a.flac"))
        assertEquals(prefix + audio + listOf("-c:a", "pcm_s16le", "$W/a.wav"), args(Target.WAV, output = "$W/a.wav"))
    }

    @Test fun stillImagesFromPng() {
        val png = "$W/input.png"
        val pre = listOf(
            "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1", "-protocol_whitelist", "file,pipe",
            "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST, "-i", png,
        )
        assertEquals(pre + listOf("-frames:v", "1", "-update", "1", "-c:v", "bmp", "$W/out/photo.bmp"), FfmpegArgs.build(ROOT, png, "$W/out/photo.bmp", Target.BMP, defaults))
        assertEquals(
            pre + listOf("-frames:v", "1", "-update", "1", "-c:v", "tiff", "-compression_algo", "lzw", "$W/out/photo.tiff"),
            FfmpegArgs.build(ROOT, png, "$W/out/photo.tiff", Target.TIFF, defaults),
        )
    }

    @Test fun everyResolutionKeepsEvenSidesAndNeverUpscales() {
        Resolution.entries.filter { it.height != null }.forEach { r ->
            val f = FfmpegArgs.scale(r)
            assertTrue(f, f.contains("min(${r.height},iw)") && f.contains("min(${r.height},ih)"))
            assertTrue(f, f.contains("trunc(") && f.contains("/2)*2"))
        }
    }

    @Test fun refusesPathsOutsideTheWorkDir() {
        assertThrows { FfmpegArgs.build(ROOT, "-i", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, "video.mkv", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, input, "-f", Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, input, input, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, "/sdcard/DCIM/a.mp4", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, input, "/data/user/0/app/files/x.mp4", Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, "$W/../../files/secret.db", out, Target.MP4, defaults) }
        assertThrows { FfmpegArgs.build(ROOT, input, "$ROOT-2/x.mp4", Target.MP4, defaults) }
        // Formats made without FFmpeg.
        assertThrows { args(Target.JPG) }
        assertThrows { args(Target.HTML) }
    }

    @Test fun insideChecks() {
        assertTrue(FfmpegArgs.isInside(ROOT, "$W/input.mkv"))
        assertTrue(FfmpegArgs.isInside("$ROOT/", "$W/out/a.mp4"))
        assertFalse(FfmpegArgs.isInside(ROOT, ROOT))
        assertFalse(FfmpegArgs.isInside(ROOT, "$ROOT/"))
        assertFalse(FfmpegArgs.isInside(ROOT, "$W/./input.mkv"))
        assertFalse(FfmpegArgs.isInside(ROOT, "$W//input.mkv"))
        assertFalse(FfmpegArgs.isInside(ROOT, "$W/../abc/input.mkv"))
        assertFalse(FfmpegArgs.isInside("relative/root", "relative/root/a"))
        assertFalse(FfmpegArgs.isInside("/", "/etc/passwd"))
    }

    @Test fun onlyMediaDemuxersAreWhitelistedBeforeTheInput() {
        val a = args(Target.MP3)
        val at = a.indexOf("-format_whitelist")
        assertTrue(at >= 0 && at < a.indexOf("-i"))
        val demuxers = a[at + 1].split(',')
        assertEquals(FfmpegArgs.FORMAT_WHITELIST.split(','), demuxers)
        // Demuxers that open other files (playlists, manifests, lists) are never allowed.
        listOf("hls", "applehttp", "dash", "concat", "sdp", "rtsp", "imf", "ffmetadata", "lavfi", "tee", "data", "rawvideo").forEach {
            assertFalse(it, it in demuxers)
        }
        listOf("mov", "matroska", "mp3", "png_pipe").forEach { assertTrue(it, it in demuxers) }
        assertEquals("file,pipe", a[a.indexOf("-protocol_whitelist") + 1])
    }

    @Test fun textAndPlaylistsNeverReachFfmpeg() {
        val ascii = { t: String -> t.toByteArray(Charsets.ISO_8859_1) }
        val m3u8 = ascii("#EXTM3U\n#EXTINF:1,\nfile:///data/user/0/com.powermaxv2.ytdownloader/cache/jobs/x.mp4\n")
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(m3u8))
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(ascii("ffconcat version 1.0\nfile 'a.mp4'\n")))
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(ascii("v=0\no=- 0 0 IN IP4 127.0.0.1\n")))
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(ascii("<?xml version=\"1.0\"?><MPD></MPD>")))
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + m3u8))
        // A playlist start followed by binary bytes is still refused.
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(ascii("  #EXTM3U\n") + ByteArray(64)))
        assertEquals(FfmpegInput.TEXT_REFUSED, FfmpegInput.refusal(ascii("plain words, nothing else")))
        assertTrue(FfmpegInput.refusal(ByteArray(0)) != null)
        // Real media heads pass: ID3 MP3, Matroska, a binary container without a known signature (MPEG-TS).
        assertEquals(null, FfmpegInput.refusal(ascii("ID3") + ByteArray(64)))
        assertEquals(null, FfmpegInput.refusal(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(60)))
        assertEquals(null, FfmpegInput.refusal(ByteArray(376) { if (it % 188 == 0) 0x47 else 0 }))
    }

    @Test fun workNameExtensionComesFromTheBytesOrAMediaName() {
        val mkv = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(60)
        assertEquals("mkv", FfmpegInput.workExt(mkv, "liste.m3u8"))
        val unknownBinary = ByteArray(64) { if (it % 2 == 0) 0x47 else 0 }
        assertEquals("bin", FfmpegInput.workExt(unknownBinary, "liste.m3u8"))
        assertEquals("bin", FfmpegInput.workExt(unknownBinary, "notes.txt"))
        assertEquals("wma", FfmpegInput.workExt(unknownBinary, "chanson.WMA"))
        assertEquals("bin", FfmpegInput.workExt(unknownBinary, "clip.mp%d"))
    }

    @Test fun fixedWorkNamesUseSafeExtensions() {
        assertEquals("mkv", FfmpegArgs.safeExt("MKV"))
        assertEquals("bin", FfmpegArgs.safeExt("mp%d"))
        assertEquals("bin", FfmpegArgs.safeExt(""))
        assertEquals("bin", FfmpegArgs.safeExt("toolongext"))
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
