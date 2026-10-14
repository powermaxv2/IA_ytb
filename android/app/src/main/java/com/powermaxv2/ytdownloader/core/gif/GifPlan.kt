package com.powermaxv2.ytdownloader.core.gif

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The Créateur de GIF's arithmetic: the passage of a video (or the order of photos), the size and shape of the GIF,
// how long it may last so FFmpeg stays within the phone's memory, its estimated weight, and the exact FFmpeg command.
// Pure (no Android types): unit tested in GifPlanTest.
//
// Unlike the Découpeur's and the Convertisseur's quick GIF (one palette per frame, fixed size), this one computes a
// single palette for the whole GIF (palettegen stats_mode=diff + paletteuse diff_mode=rectangle): smaller files and
// steadier colours, at the price of FFmpeg holding the frames until the palette is known, hence [FRAME_PIXEL_BUDGET].

/** Where the frames come from. */
enum class GifSource(val label: String) {
    VIDEO("Vidéo"),
    PHOTOS("Photos"),
}

/** Longest side of the GIF and how many colours its palette has. */
enum class GifSize(val label: String, val side: Int, val colors: Int) {
    SMALL("Petit", 320, 128),
    MEDIUM("Moyen", 480, 256),
    LARGE("Grand", 640, 256),
}

/** Frames per second of a GIF made from a video. */
enum class GifSmooth(val label: String, val fps: Int) {
    CHOPPY("Saccadé", 8),
    NORMAL("Normal", 12),
    SMOOTH("Fluide", 20),
}

/** Playback speed of the passage. */
enum class GifSpeed(val label: String, val factor: Float) {
    HALF("×0,5", 0.5f),
    NORMAL("×1", 1f),
    FAST("×1,5", 1.5f),
    DOUBLE("×2", 2f),
}

/** How the frames follow each other. */
enum class GifEffect(val label: String, val detail: String) {
    NORMAL("En boucle", "du début à la fin"),
    BOOMERANG("Boomerang", "aller puis retour"),
    REVERSE("À l'envers", "rembobine"),
}

/** Shape of the GIF; [ratio] is width / height, null keeps the source's. */
enum class GifShape(val label: String, val ratio: Float?) {
    ORIGINAL("Originale", null),
    SQUARE("Carrée", 1f),
    PORTRAIT("Portrait 4:5", 0.8f),
}

/** How long each photo stays, for a GIF made from photos. */
enum class GifPace(val label: String, val frameMs: Long) {
    FAST("Rapide", 150L),
    NORMAL("Normal", 400L),
    SLOW("Lent", 800L),
}

/** The passage of the video, in milliseconds from its start. */
data class GifRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Every choice of the screen. */
data class GifSettings(
    val size: GifSize = GifSize.MEDIUM,
    val smooth: GifSmooth = GifSmooth.NORMAL,
    val speed: GifSpeed = GifSpeed.NORMAL,
    val effect: GifEffect = GifEffect.NORMAL,
    val shape: GifShape = GifShape.ORIGINAL,
    val pace: GifPace = GifPace.NORMAL,
    val caption: String = "",
)

/** Part of the source kept (centred), in source pixels. */
data class GifCrop(val x: Int, val y: Int, val width: Int, val height: Int)

/** The crop of the source and the GIF's size in pixels. */
data class GifGeometry(val crop: GifCrop, val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
}

object GifPlan {
    /** Shortest passage. */
    const val MIN_MS = 500L

    /** Longest passage taken from a video. */
    const val MAX_SOURCE_MS = 15_000L

    /** Longest GIF (output time, after speed and boomerang). */
    const val MAX_OUTPUT_MS = 30_000L

    /** First passage offered: the first 3 seconds. */
    const val DEFAULT_MS = 3_000L

    /** Photos in one GIF. */
    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 40

    /** Characters of the caption. */
    const val CAPTION_MAX = 40

    /**
     * Pixels of all the frames together (width × height × frames). FFmpeg keeps every frame until the palette is
     * computed (and the reversed half of a boomerang), as RGBA: 40 M px ≈ 160 MB at worst, in FFmpeg's own process.
     */
    const val FRAME_PIXEL_BUDGET = 40_000_000L

    // MARK: - Geometry

    /**
     * The centred crop of a [sourceW] × [sourceH] picture to [shape], then shrunk so its longest side is at most
     * [side] (never enlarged). Both sides even and at least 2 px.
     */
    fun geometry(sourceW: Int, sourceH: Int, shape: GifShape, side: Int): GifGeometry {
        val crop = cover(sourceW, sourceH, shape.ratio)
        val scale = min(1.0, side.toDouble() / max(crop.width, crop.height))
        return GifGeometry(crop, even(crop.width * scale), even(crop.height * scale))
    }

    /**
     * Geometry of a GIF made from photos: the first photo's proportions ([firstW] × [firstH], any scale, e.g. its
     * thumbnail) or [shape]'s, at [side] px on the longest side (photos are always larger than a GIF).
     */
    fun photoGeometry(firstW: Int, firstH: Int, shape: GifShape, side: Int): GifGeometry {
        val longest = max(firstW, firstH).coerceAtLeast(1)
        val w = max(2, (firstW.toLong() * PHOTO_REFERENCE / longest).toInt())
        val h = max(2, (firstH.toLong() * PHOTO_REFERENCE / longest).toInt())
        return geometry(w, h, shape, side)
    }

    private const val PHOTO_REFERENCE = 4_096

    /** The largest centred part of a [sourceW] × [sourceH] picture with the proportions [ratio] (all of it when null). */
    fun cover(sourceW: Int, sourceH: Int, ratio: Float?): GifCrop {
        val sw = sourceW.coerceAtLeast(2)
        val sh = sourceH.coerceAtLeast(2)
        var cw = sw
        var ch = sh
        if (ratio != null && ratio > 0f) {
            if (sw.toDouble() / sh > ratio) cw = (sh * ratio).roundToInt().coerceIn(2, sw) else ch = (sw / ratio).roundToInt().coerceIn(2, sh)
        }
        return GifCrop((sw - cw) / 2, (sh - ch) / 2, cw, ch)
    }

    private fun even(v: Double): Int = max(2, (v / 2).roundToInt() * 2)

    // MARK: - Durations

    /** Output length of a passage of [sourceMs] at [speed], doubled by a boomerang. */
    fun outputMs(sourceMs: Long, speed: GifSpeed, effect: GifEffect): Long {
        val played = (sourceMs / speed.factor.toDouble()).roundToLong()
        return if (effect == GifEffect.BOOMERANG) played * 2 else played
    }

    /**
     * Longest passage of the video for these choices: [MAX_SOURCE_MS], no more than [MAX_OUTPUT_MS] once played, and
     * within [FRAME_PIXEL_BUDGET] for a GIF of [geometry]. Rounded down to a tenth of a second, at least [MIN_MS].
     */
    fun maxSourceMs(geometry: GifGeometry, smooth: GifSmooth, speed: GifSpeed, effect: GifEffect): Long {
        val frames = FRAME_PIXEL_BUDGET / geometry.pixels.coerceAtLeast(1L)
        val outputMs = min(MAX_OUTPUT_MS, frames * 1_000L / smooth.fps)
        val perPlay = if (effect == GifEffect.BOOMERANG) outputMs / 2.0 else outputMs.toDouble()
        val source = min(MAX_SOURCE_MS.toDouble(), perPlay * speed.factor)
        return max(MIN_MS, (floor(source / 100.0) * 100).toLong())
    }

    /** First passage for a video of [durationMs]: its first [DEFAULT_MS], within [maxMs]. */
    fun initial(durationMs: Long, maxMs: Long): GifRange = clamp(GifRange(0L, min(DEFAULT_MS, durationMs)), durationMs, maxMs)

    /** [range] inside the video, at least [MIN_MS] (when the video is) and at most [maxMs] long. */
    fun clamp(range: GifRange, durationMs: Long, maxMs: Long): GifRange {
        val total = durationMs.coerceAtLeast(0L)
        val min = MIN_MS.coerceAtMost(total)
        val start = range.startMs.coerceIn(0L, (total - min).coerceAtLeast(0L))
        var end = range.endMs.coerceIn(start + min, total)
        if (end - start > maxMs) end = start + maxMs.coerceAtLeast(min)
        return GifRange(start, end)
    }

    /** The start handle dragged to [startMs]: the end stays, unless the passage would become too long or too short. */
    fun moveStart(range: GifRange, startMs: Long, durationMs: Long, maxMs: Long): GifRange {
        val start = startMs.coerceIn((range.endMs - maxMs).coerceAtLeast(0L), (range.endMs - MIN_MS).coerceAtLeast(0L))
        return clamp(GifRange(start, range.endMs), durationMs, maxMs)
    }

    /** The end handle dragged to [endMs]: the start stays. */
    fun moveEnd(range: GifRange, endMs: Long, durationMs: Long, maxMs: Long): GifRange {
        val highest = min(range.startMs + maxMs, durationMs).coerceAtLeast(range.startMs + MIN_MS)
        val end = endMs.coerceIn(range.startMs + MIN_MS, highest)
        return clamp(GifRange(range.startMs, end), durationMs, maxMs)
    }

    // MARK: - Frames

    /**
     * Order in which [count] pictures play: as they come, backwards, or there and back without repeating the two ends
     * (0 1 2 3 2 1, so the loop is seamless).
     */
    fun order(count: Int, effect: GifEffect): List<Int> {
        if (count <= 0) return emptyList()
        val forward = (0 until count).toList()
        return when (effect) {
            GifEffect.NORMAL -> forward
            GifEffect.REVERSE -> forward.reversed()
            GifEffect.BOOMERANG -> forward + (count - 2 downTo 1).toList()
        }
    }

    /** Frames of a GIF made from a video. */
    fun videoFrames(range: GifRange, settings: GifSettings): Int =
        max(1, (outputMs(range.durationMs, settings.speed, settings.effect) * settings.smooth.fps / 1_000.0).roundToInt())

    /** Times (ms) of [count] preview pictures spread over [range], each in the middle of its share. */
    fun previewTimes(range: GifRange, count: Int): List<Long> =
        (0 until count.coerceAtLeast(0)).map { i -> range.startMs + range.durationMs * (2 * i + 1) / (2L * count) }

    /** Milliseconds each preview picture stays on screen so [count] of them last as long as the GIF's one play. */
    fun previewFrameMs(range: GifRange, count: Int, speed: GifSpeed): Long =
        max(40L, (range.durationMs / speed.factor / count.coerceAtLeast(1)).roundToLong())

    // MARK: - Weight

    /**
     * Rough weight of the GIF: frames × pixels × bytes per pixel. A video's frames look alike and the palette is
     * shared, so they compress better than photos following each other; fewer colours make a lighter file.
     */
    fun estimateBytes(geometry: GifGeometry, frames: Int, colors: Int, photos: Boolean): Long {
        val perPixel = (if (photos) 0.55 else 0.30) * (if (colors <= 128) 0.8 else 1.0)
        return (geometry.pixels * frames.coerceAtLeast(1) * perPixel).roundToLong() + 1_024L
    }

    // MARK: - Caption

    /** The caption as drawn: one line, no control characters, spaces collapsed, at most [CAPTION_MAX] characters. */
    fun cleanCaption(text: String): String = text
        .map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }
        .joinToString("")
        .replace(Regex(" {2,}"), " ")
        .trim()
        .take(CAPTION_MAX)
        .trim()

    /** Starting text size of the caption (shrunk to fit the width when drawn): 12 % of the GIF's short side. */
    fun captionSize(width: Int, height: Int): Float = max(10f, min(width, height) * 0.12f)

    // MARK: - FFmpeg

    /** The colour chain shared by both sources: one palette for the whole GIF, frames only redrawn where they change. */
    fun paletteChain(input: String, colors: Int): String =
        "$input split[pa][pb];[pa]palettegen=max_colors=$colors:stats_mode=diff[pal];" +
            "[pb][pal]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle[gif]"

    /** Filter graph for a video: speed, frame rate, crop, size, effect, caption, palette. */
    fun videoGraph(geometry: GifGeometry, settings: GifSettings, caption: Boolean): String {
        val c = geometry.crop
        val steps = buildList {
            add(if (settings.speed == GifSpeed.NORMAL) "setpts=PTS-STARTPTS" else "setpts=(PTS-STARTPTS)/${factor(settings.speed.factor)}")
            add("fps=${settings.smooth.fps}")
            add("crop=${c.width}:${c.height}:${c.x}:${c.y}")
            add("scale=${geometry.width}:${geometry.height}:flags=lanczos")
            add("setsar=1")
        }.joinToString(",")
        val graph = StringBuilder("[0:v]$steps")
        when (settings.effect) {
            GifEffect.NORMAL -> graph.append("[v]")
            GifEffect.REVERSE -> graph.append(",reverse[v]")
            GifEffect.BOOMERANG -> graph.append(",split[fw][bw];[bw]reverse[rv];[fw][rv]concat=n=2:v=1:a=0[v]")
        }
        val last = if (caption) {
            graph.append(";[1:v]format=rgba[cap];[v][cap]overlay=0:0:eof_action=repeat[t]")
            "[t]"
        } else {
            "[v]"
        }
        graph.append(";").append(paletteChain(last, settings.size.colors))
        return graph.toString()
    }

    private fun factor(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else String.format(Locale.ROOT, "%.2f", f).trimEnd('0')

    /**
     * FFmpeg arguments writing the passage [range] of the video [input] as the GIF [output], with the transparent
     * caption picture [caption] (drawn at the GIF's size) laid over it. Every path is absolute and inside [root].
     */
    fun videoArgs(
        root: String,
        input: String,
        caption: String?,
        output: String,
        range: GifRange,
        geometry: GifGeometry,
        settings: GifSettings,
    ): List<String> {
        val paths = listOfNotNull(input, caption, output)
        require(paths.all { FfmpegArgs.isInside(root, it) }) { "Chemins hors du dossier de travail" }
        require(output != input && output != caption) { "Entrée et sortie identiques" }
        require(range.durationMs > 0) { "Passage vide" }
        val args = mutableListOf("-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1")
        args += listOf("-protocol_whitelist", "file", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST)
        args += listOf("-ss", seconds(range.startMs), "-t", seconds(range.durationMs), "-i", input)
        if (caption != null) {
            args += listOf("-protocol_whitelist", "file", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST)
            args += listOf("-f", "image2", "-pattern_type", "none", "-i", caption)
        }
        args += listOf("-filter_complex", videoGraph(geometry, settings, caption != null))
        args += listOf("-map", "[gif]", "-an", "-loop", "0", "-f", "gif", output)
        return args
    }

    /**
     * FFmpeg arguments turning the numbered pictures [pattern] ("…/frame%03d.png", already sized, ordered and
     * captioned) into the GIF [output], each shown [frameMs].
     */
    fun photoArgs(root: String, pattern: String, output: String, frameMs: Long, colors: Int): List<String> {
        require(FfmpegArgs.isInside(root, pattern) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(pattern != output) { "Entrée et sortie identiques" }
        require(frameMs > 0) { "Durée vide" }
        return listOf(
            "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST,
            "-f", "image2", "-start_number", "1", "-framerate", "1000/$frameMs", "-i", pattern,
            "-filter_complex", paletteChain("[0:v]", colors),
            "-map", "[gif]", "-loop", "0", "-f", "gif", output,
        )
    }

    /** "12.345" (FFmpeg seconds). */
    fun seconds(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d.%03d", v / 1_000, v % 1_000)
    }

    /** "3,4 s", "12 s" (French decimal comma, tenths only under 10 s). */
    fun shortDuration(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0L) / 100.0).roundToLong()
        return if (tenths < 100 && tenths % 10 != 0L) "${tenths / 10},${tenths % 10} s" else "${(tenths + 5) / 10} s"
    }

    /** "0:03.4" (minutes, seconds and tenths). */
    fun timecode(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0L) / 100.0).roundToLong()
        val s = tenths / 10
        return String.format(Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, tenths % 10)
    }

    /** "chat qui saute (GIF).gif" from a video, "GIF 14-10-2026 21h05.gif" from photos. */
    fun outputName(sourceName: String?, day: Int, month: Int, year: Int, hour: Int, minute: Int): String {
        val base = sourceName?.let { SafeNames.baseOf(SafeNames.sanitize(it)) }?.takeIf { it.isNotBlank() }
        if (base == null) return String.format(Locale.ROOT, "GIF %02d-%02d-%04d %02dh%02d.gif", day, month, year, hour, minute)
        val suffix = " (GIF).gif"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }
}
