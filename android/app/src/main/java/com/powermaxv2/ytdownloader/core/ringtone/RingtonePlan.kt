package com.powermaxv2.ytdownloader.core.ringtone

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The Sonneries tool's arithmetic: what a cut is for (ringtone, notification, alarm) and its limits, the passage
// (clamped, moved by its handles, or guessed: the loudest stretch, usually the chorus), the fades, and the exact
// FFmpeg command. Pure (no Android types): unit tested in RingtonePlanTest.

/** What the cut sound becomes on the phone: each one lands in its own system folder and list. */
enum class RingtoneUse(
    val label: String,
    /** Folder of the shared storage ("Ringtones", "Notifications", "Alarms"), as Android's sound pickers read it. */
    val directory: String,
    val defaultMs: Long,
    val maxMs: Long,
    /** "sonnerie" in "titre (sonnerie).mp3". */
    val tag: String,
) {
    RINGTONE("Sonnerie", "Ringtones", 30_000L, 40_000L, "sonnerie"),
    NOTIFICATION("Notification", "Notifications", 3_000L, 8_000L, "notification"),
    ALARM("Réveil", "Alarms", 40_000L, 60_000L, "réveil"),
}

/** How the sound comes in and goes out. */
enum class RingtoneFade(val label: String, val inMs: Long, val outMs: Long) {
    NONE("Sec", 0L, 0L),
    SOFT("Doux", 400L, 1_200L),
    LONG("Long", 2_000L, 3_000L),
}

/** The kept passage, in milliseconds from the start of the file. */
data class RingRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object RingtonePlan {
    /** Shortest passage. */
    const val MIN_MS = 500L

    /** Bars of the waveform drawn and searched. */
    const val BARS = 160

    /** The passage first offered for a sound of [durationMs]: the [use]'s usual length, from the start. */
    fun initial(durationMs: Long, use: RingtoneUse): RingRange = clamp(RingRange(0L, use.defaultMs), durationMs, use)

    /**
     * [range] brought inside the file and the [use]'s limits: start ≥ 0, end ≤ [durationMs], at least [MIN_MS] long
     * (when the file is), at most [RingtoneUse.maxMs] (the end moves back).
     */
    fun clamp(range: RingRange, durationMs: Long, use: RingtoneUse): RingRange {
        val total = durationMs.coerceAtLeast(0L)
        val min = MIN_MS.coerceAtMost(total)
        val start = range.startMs.coerceIn(0L, (total - min).coerceAtLeast(0L))
        var end = range.endMs.coerceIn(start + min, total)
        if (end - start > use.maxMs) end = start + use.maxMs
        return RingRange(start, end)
    }

    /** The start handle dragged to [startMs]: the end stays, the passage keeps between [MIN_MS] and the max. */
    fun moveStart(range: RingRange, startMs: Long, durationMs: Long, use: RingtoneUse): RingRange {
        val lowest = (range.endMs - use.maxMs).coerceAtLeast(0L)
        val start = startMs.coerceIn(lowest, (range.endMs - MIN_MS).coerceAtLeast(lowest))
        return clamp(RingRange(start, range.endMs), durationMs, use)
    }

    /** The end handle dragged to [endMs]: the start stays, the passage keeps between [MIN_MS] and the max. */
    fun moveEnd(range: RingRange, endMs: Long, durationMs: Long, use: RingtoneUse): RingRange {
        val highest = (range.startMs + use.maxMs).coerceAtMost(durationMs)
        val end = endMs.coerceIn(range.startMs + MIN_MS, highest.coerceAtLeast(range.startMs + MIN_MS))
        return clamp(RingRange(range.startMs, end), durationMs, use)
    }

    /** The whole passage slid so it starts at [startMs] (its length kept as far as the file allows). */
    fun slide(range: RingRange, startMs: Long, durationMs: Long, use: RingtoneUse): RingRange {
        val length = range.durationMs
        val start = startMs.coerceIn(0L, (durationMs - length).coerceAtLeast(0L))
        return clamp(RingRange(start, start + length), durationMs, use)
    }

    /** The [use] changed: same start, its usual length (or what is left of the file). */
    fun forUse(range: RingRange, durationMs: Long, use: RingtoneUse): RingRange =
        clamp(RingRange(range.startMs, range.startMs + use.defaultMs), durationMs, use)

    /**
     * The most catching passage of [lengthMs] in a sound of [durationMs] whose loudness is [bars] (0..1 each, spread
     * evenly): the window with the most energy, which in a song is most often the chorus. The very first and last
     * bars count a bit less (intros and outros fade). Null when there is no waveform.
     */
    fun bestStart(bars: FloatArray, durationMs: Long, lengthMs: Long): Long? {
        val n = bars.size
        if (n == 0 || durationMs <= 0L) return null
        if (lengthMs >= durationMs) return 0L
        val window = ((lengthMs.toDouble() / durationMs) * n).roundToInt().coerceIn(1, n)
        var best = -1.0
        var bestIndex = 0
        var sum = 0.0
        val weight = DoubleArray(n) { i ->
            val v = bars[i].coerceIn(0f, 1f).toDouble()
            val edge = minOf(i, n - 1 - i).toDouble() / n
            v * v * (if (edge < 0.06) 0.75 else 1.0)
        }
        for (i in 0 until n) {
            sum += weight[i]
            if (i >= window) sum -= weight[i - window]
            if (i >= window - 1) {
                val from = i - window + 1
                if (sum > best + 1e-9) {
                    best = sum
                    bestIndex = from
                }
            }
        }
        val start = (bestIndex.toDouble() / n * durationMs).roundToLong()
        return start.coerceIn(0L, (durationMs - lengthMs).coerceAtLeast(0L))
    }

    /** Fades (ms) that fit a passage of [durationMs]: together they never take more than two thirds of it. */
    fun fadesFor(fade: RingtoneFade, durationMs: Long): Pair<Long, Long> {
        val total = fade.inMs + fade.outMs
        if (total == 0L) return 0L to 0L
        val room = durationMs * 2 / 3
        if (total <= room) return fade.inMs to fade.outMs
        return (fade.inMs * room / total) to (fade.outMs * room / total)
    }

    /** The audio filters: louder (EBU R128 to -12 LUFS) when asked, then the fades, then back to 44.1 kHz. */
    fun filters(durationMs: Long, fade: RingtoneFade, louder: Boolean): String {
        val (fadeIn, fadeOut) = fadesFor(fade, durationMs)
        return buildList {
            if (louder) add("loudnorm=I=-12:TP=-1:LRA=11")
            if (fadeIn > 0) add("afade=t=in:st=0:d=${seconds(fadeIn)}")
            if (fadeOut > 0) add("afade=t=out:st=${seconds(durationMs - fadeOut)}:d=${seconds(fadeOut)}")
            add("aresample=44100")
        }.joinToString(",")
    }

    /**
     * FFmpeg arguments writing the passage [range] of [input] as an MP3 [output] for [use]. Both paths are absolute
     * and inside [root] (the tools' work dir), like the Découpeur's. The seek comes before the input (fast), only
     * the first sound stream is kept (a video gives its sound), and the MP3 is titled [title].
     */
    fun args(
        root: String,
        input: String,
        output: String,
        range: RingRange,
        fade: RingtoneFade,
        louder: Boolean,
        title: String,
    ): List<String> {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        require(range.durationMs > 0) { "Passage vide" }
        return listOf(
            "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST,
            "-ss", seconds(range.startMs), "-i", input, "-t", seconds(range.durationMs),
            "-map", "0:a:0", "-vn", "-sn", "-dn", "-map_metadata", "-1",
            "-af", filters(range.durationMs, fade, louder),
            "-c:a", "libmp3lame", "-b:a", "192k", "-ac", "2",
            "-id3v2_version", "3", "-metadata", "title=$title",
            output,
        )
    }

    /** The sound's name in the phone's list: the file name without extension, tidied. */
    fun title(sourceName: String): String =
        SafeNames.baseOf(SafeNames.sanitize(sourceName)).replace('_', ' ').replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "Gulp" }

    /** "Bohemian Rhapsody (sonnerie).mp3". */
    fun outputName(sourceName: String, use: RingtoneUse): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName)).ifBlank { "son" }
        val suffix = " (${use.tag}).mp3"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }

    /** "12.345" (FFmpeg seconds). */
    fun seconds(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d.%03d", v / 1_000, v % 1_000)
    }

    /** "0:50.0", "12:03.4" (tenths of a second). */
    fun timecode(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0L) / 100.0).roundToLong()
        val totalSeconds = tenths / 10
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d.%d", m, s, tenths % 10)
    }

    /** "30 s", "3,5 s", "1 min". */
    fun length(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0L) / 100.0).roundToLong()
        if (tenths >= 600 && tenths % 600 == 0L) return "${tenths / 600} min"
        return if (tenths % 10 == 0L) "${tenths / 10} s" else "${tenths / 10},${tenths % 10} s"
    }
}
