package com.powermaxv2.ytdownloader.core.trim

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.convert.Formats
import com.powermaxv2.ytdownloader.core.convert.Quality
import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.roundToLong

// The Découpeur's arithmetic: what can come out of a video or a sound, the passage kept (clamped to the file and to
// the limits of each output) and the exact FFmpeg command. Pure (no Android types): unit tested in TrimPlanTest.

/** What the Découpeur writes from the kept passage. */
enum class TrimOutput(val label: String, val detail: String) {
    /** The passage as a video, streams copied (instant, same quality) unless the cut must be exact. */
    VIDEO("Vidéo", "même qualité"),

    /** Only the sound: copied when the source is already a sound file Gulp can keep as is, else AAC (M4A). */
    AUDIO("Son seul", "M4A ou d'origine"),

    /** MP3 with a short fade in and out, 40 s at most. */
    RINGTONE("Sonnerie", "MP3 · 40 s max"),

    /** Animated GIF, muted, 30 s at most. */
    GIF("GIF", "muet · 30 s max"),
}

/** The kept passage, in milliseconds from the start of the file. */
data class TrimRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object TrimPlan {
    /** Shortest passage. */
    const val MIN_MS = 500L

    const val RINGTONE_MAX_MS = 40_000L
    val GIF_MAX_MS: Long = Formats.GIF_MAX_SECONDS * 1_000L

    /** Sound files whose stream FFmpeg can cut and write back in the same container. */
    private val copyableAudio = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    /** Video containers a copied passage can stay in ("mov" / "m4v" / "3gp" become MP4: same streams). */
    private val copyableVideo = mapOf("mp4" to "mp4", "m4v" to "mp4", "mov" to "mp4", "3gp" to "mp4", "mkv" to "mkv", "webm" to "webm")

    /** The outputs offered for a file with or without pictures. */
    fun outputs(hasVideo: Boolean): List<TrimOutput> =
        if (hasVideo) TrimOutput.entries.toList() else listOf(TrimOutput.AUDIO, TrimOutput.RINGTONE)

    /** Longest passage for [output], or null. */
    fun maxMs(output: TrimOutput): Long? = when (output) {
        TrimOutput.RINGTONE -> RINGTONE_MAX_MS
        TrimOutput.GIF -> GIF_MAX_MS
        else -> null
    }

    /** First passage shown for a file of [durationMs]: all of it, or its start when [output] has a limit. */
    fun initial(durationMs: Long, output: TrimOutput): TrimRange = clamp(TrimRange(0L, durationMs), durationMs, output)

    /**
     * [range] brought inside the file and the limits of [output]: start ≥ 0, end ≤ [durationMs], at least
     * [MIN_MS] long (when the file is), at most [maxMs] long (the end moves back to the start + max).
     */
    fun clamp(range: TrimRange, durationMs: Long, output: TrimOutput): TrimRange {
        val total = durationMs.coerceAtLeast(0L)
        val min = MIN_MS.coerceAtMost(total)
        val start = range.startMs.coerceIn(0L, (total - min).coerceAtLeast(0L))
        var end = range.endMs.coerceIn(start + min, total)
        maxMs(output)?.let { max -> if (end - start > max) end = start + max }
        return TrimRange(start, end)
    }

    /** The start handle dragged to [startMs]: the end stays, the start stops [MIN_MS] before it (and within the max). */
    fun moveStart(range: TrimRange, startMs: Long, durationMs: Long, output: TrimOutput): TrimRange {
        val lowest = maxMs(output)?.let { range.endMs - it } ?: 0L
        val start = startMs.coerceIn(lowest.coerceAtLeast(0L), (range.endMs - MIN_MS).coerceAtLeast(0L))
        return clamp(TrimRange(start, range.endMs), durationMs, output)
    }

    /** The end handle dragged to [endMs]: the start stays, the end stops [MIN_MS] after it (and within the max). */
    fun moveEnd(range: TrimRange, endMs: Long, durationMs: Long, output: TrimOutput): TrimRange {
        val highest = maxMs(output)?.let { range.startMs + it } ?: durationMs
        val end = endMs.coerceIn(range.startMs + MIN_MS, highest.coerceAtMost(durationMs).coerceAtLeast(range.startMs + MIN_MS))
        return clamp(TrimRange(range.startMs, end), durationMs, output)
    }

    /** Whether the streams are copied (no re-encoding: instant and lossless). */
    fun copies(output: TrimOutput, sourceExt: String, hasVideo: Boolean, exact: Boolean): Boolean {
        val ext = sourceExt.lowercase(Locale.ROOT)
        return when (output) {
            TrimOutput.VIDEO -> !exact && ext in copyableVideo
            TrimOutput.AUDIO -> !hasVideo && ext in copyableAudio
            TrimOutput.RINGTONE, TrimOutput.GIF -> false
        }
    }

    /** Extension of the result. */
    fun outputExt(output: TrimOutput, sourceExt: String, hasVideo: Boolean, exact: Boolean): String {
        val ext = sourceExt.lowercase(Locale.ROOT)
        return when (output) {
            TrimOutput.VIDEO -> if (copies(output, ext, hasVideo, exact)) copyableVideo.getValue(ext) else "mp4"
            TrimOutput.AUDIO -> if (copies(output, ext, hasVideo, exact)) ext else "m4a"
            TrimOutput.RINGTONE -> "mp3"
            TrimOutput.GIF -> "gif"
        }
    }

    /**
     * FFmpeg arguments writing the passage [range] of [input] into [output] as [kind]. Both paths are absolute and
     * inside [root] (the tools' work dir), like the Convertisseur's: FFmpeg never touches anything else. The seek
     * (`-ss`) comes before the input, so FFmpeg jumps straight to the passage.
     */
    fun args(
        root: String,
        input: String,
        output: String,
        range: TrimRange,
        kind: TrimOutput,
        sourceExt: String,
        hasVideo: Boolean,
        exact: Boolean,
    ): List<String> {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        require(range.durationMs > 0) { "Passage vide" }
        val copy = copies(kind, sourceExt, hasVideo, exact)
        val ext = outputExt(kind, sourceExt, hasVideo, exact)
        val head = listOf(
            "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST,
            "-ss", seconds(range.startMs), "-i", input, "-t", seconds(range.durationMs),
        )
        val mp4 = listOf("-movflags", "+faststart")
        val body = when (kind) {
            TrimOutput.VIDEO -> if (copy) {
                listOf("-map", "0:v:0", "-map", "0:a?", "-c", "copy", "-avoid_negative_ts", "make_zero") + (if (ext == "mp4") mp4 else emptyList())
            } else {
                listOf(
                    "-map", "0:v:0", "-map", "0:a:0?",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                    "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                    "-c:a", "aac", "-b:a", "192k",
                ) + mp4
            }
            TrimOutput.AUDIO -> if (copy) {
                listOf("-map", "0:a:0", "-vn", "-c:a", "copy") + (if (ext == "m4a") mp4 else emptyList())
            } else {
                listOf("-map", "0:a:0", "-vn", "-c:a", "aac", "-b:a", "192k") + mp4
            }
            TrimOutput.RINGTONE -> listOf("-map", "0:a:0", "-vn", "-af", fades(range.durationMs), "-c:a", "libmp3lame", "-b:a", "192k", "-ac", "2")
            TrimOutput.GIF -> listOf("-map", "0:v:0", "-an", "-vf", FfmpegArgs.gifFilter(Quality.MEDIUM), "-loop", "0")
        }
        return head + body + output
    }

    /** Fade in over 0.3 s, fade out over the last second (only the fade in for a passage under 3 s). */
    fun fades(durationMs: Long): String {
        val fadeIn = "afade=t=in:st=0:d=0.3"
        if (durationMs < 3_000L) return fadeIn
        return "$fadeIn,afade=t=out:st=${seconds(durationMs - 1_000L)}:d=1"
    }

    /** "12.345" (FFmpeg seconds). */
    fun seconds(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d.%03d", v / 1_000, v % 1_000)
    }

    /** "0:50.0", "12:03.4", "1:02:03.4" (tenths of a second). */
    fun timecode(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0L) / 100.0).roundToLong()
        val totalSeconds = tenths / 10
        val h = totalSeconds / 3_600
        val m = (totalSeconds / 60) % 60
        val s = totalSeconds % 60
        val t = tenths % 10
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d.%d", h, m, s, t)
        } else {
            String.format(Locale.ROOT, "%d:%02d.%d", m, s, t)
        }
    }

    /** "0m50s", "1h02m03s": the passage's bounds in a file name. */
    fun stamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1_000
        val h = total / 3_600
        val m = (total / 60) % 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, s) else String.format(Locale.ROOT, "%dm%02ds", m, s)
    }

    /** "concert (0m50s-2m25s).mp4", "concert (sonnerie).mp3". */
    fun outputName(sourceName: String, range: TrimRange, kind: TrimOutput, ext: String): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName)).ifBlank { "passage" }
        val tag = if (kind == TrimOutput.RINGTONE) "sonnerie" else "${stamp(range.startMs)}-${stamp(range.endMs)}"
        val suffix = " ($tag).$ext"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }
}
