package com.powermaxv2.ytdownloader.core.recorder

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * One saved memo of the Enregistreur vocal: the .m4a in Gulp's private folder ([fileName]), its name without the
 * extension ([title]), its length, weight, date, and the outline of its sound ([wave], 0..1 per bar).
 */
data class MemoInfo(
    val fileName: String,
    val durationMs: Long,
    val size: Long,
    val createdAt: Long,
    val wave: List<Float> = emptyList(),
) {
    val title: String get() = SafeNames.baseOf(fileName)
}

/** Pure helpers of the Enregistreur vocal (names, times, sizes, sound levels, the .wave side file). Unit tested. */
object Memos {
    const val EXTENSION = "m4a"

    /** Longest name a memo can be given (without ".m4a"). */
    const val MAX_TITLE = 60

    /** Most points kept for the outline of a memo, however long it is. */
    const val MAX_WAVE = 240

    /** Quietest level drawn (dB below full scale); anything quieter is a flat line. */
    private const val FLOOR_DB = 48f

    private val months = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

    /** "Mémo 14 oct. 19h20": the name a memo gets when it is saved. */
    fun defaultTitle(at: LocalDateTime): String =
        "Mémo ${at.dayOfMonth} ${months[at.monthValue - 1]} ${hour(at)}"

    /** The name typed by the user, made safe for a file: null when nothing is left. */
    fun cleanTitle(input: String): String? {
        val one = input.replace(Regex("\\s+"), " ").trim()
        if (one.isEmpty()) return null
        val safe = SafeNames.sanitize(one, fallback = "").take(MAX_TITLE).trim().trimEnd('.')
        return safe.ifEmpty { null }
    }

    /** "Mémo 14 oct. 19h20" → "Mémo 14 oct. 19h20.m4a". */
    fun fileName(title: String): String = SafeNames.sanitize("$title.$EXTENSION", fallback = "Mémo.$EXTENSION")

    /** [name], or "<base> (2).m4a", "<base> (3).m4a"… when a memo already has it (case ignored). */
    fun unique(name: String, taken: Collection<String>): String {
        val used = taken.map { it.lowercase(Locale.ROOT) }.toSet()
        if (name.lowercase(Locale.ROOT) !in used) return name
        val base = SafeNames.baseOf(name)
        val ext = SafeNames.extensionOf(name)
        var n = 2
        while (true) {
            val candidate = SafeNames.sanitize(if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            if (candidate.lowercase(Locale.ROOT) !in used) return candidate
            n++
        }
    }

    /** 7 400 ms → "0:07", 723 000 → "12:03", 3 723 000 → "1:02:03". */
    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Tenths of the running timer: 7 450 ms → "4". */
    fun tenths(ms: Long): String = ((ms.coerceAtLeast(0) / 100) % 10).toString()

    /** 48 200 → "47 Ko", 1 260 000 → "1,2 Mo". */
    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes o"
        bytes < 1024L * 1024 -> "${(bytes / 1024.0).roundToInt()} Ko"
        else -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0))
    }

    /** "Aujourd'hui, 19h20", "Hier, 08h05", "12 oct., 10h00", "12 oct. 2025" for another year. */
    fun whenText(createdAt: Long, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), zone)
        val day: LocalDate = at.toLocalDate()
        val today = now.toLocalDate()
        return when {
            day == today -> "Aujourd'hui, ${hour(at)}"
            day == today.minusDays(1) -> "Hier, ${hour(at)}"
            day.year == today.year -> "${day.dayOfMonth} ${months[day.monthValue - 1]}, ${hour(at)}"
            else -> "${day.dayOfMonth} ${months[day.monthValue - 1]} ${day.year}"
        }
    }

    private fun hour(at: LocalDateTime) = "%02dh%02d".format(at.hour, at.minute)

    /**
     * MediaRecorder's peak amplitude since the last read (0..32767) as a level to draw, 0..1 on a decibel scale:
     * whispering still moves the bars, shouting does not flatten them.
     */
    fun level(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val db = 20f * log10(amplitude.coerceAtMost(32767) / 32767f)
        return ((db + FLOOR_DB) / FLOOR_DB).coerceIn(0f, 1f)
    }

    /** [levels] in [bars] bars, each the loudest of its share (an empty list stays empty; a short one is kept). */
    fun downsample(levels: List<Float>, bars: Int): List<Float> {
        if (bars <= 0 || levels.isEmpty()) return emptyList()
        if (levels.size <= bars) return levels
        return List(bars) { i ->
            val from = (i.toLong() * levels.size / bars).toInt()
            val to = ((i + 1).toLong() * levels.size / bars).toInt().coerceAtLeast(from + 1)
            var max = 0f
            for (j in from until to) if (levels[j] > max) max = levels[j]
            max
        }
    }

    /** Side file of a memo: its length (8 bytes), then one byte (0..255) per bar of its outline. */
    fun encodeMeta(durationMs: Long, wave: List<Float>): ByteArray {
        val bars = downsample(wave, MAX_WAVE)
        val buffer = ByteBuffer.allocate(8 + bars.size).putLong(durationMs.coerceAtLeast(0))
        bars.forEach { buffer.put((it.coerceIn(0f, 1f) * 255f).roundToInt().toByte()) }
        return buffer.array()
    }

    /** Reads [encodeMeta]; null for a missing or broken file. */
    fun decodeMeta(bytes: ByteArray?): Pair<Long, List<Float>>? {
        if (bytes == null || bytes.size < 8) return null
        val buffer = ByteBuffer.wrap(bytes)
        val duration = buffer.long
        if (duration < 0) return null
        val wave = List((bytes.size - 8).coerceAtMost(MAX_WAVE)) { (buffer.get().toInt() and 0xFF) / 255f }
        return duration to wave
    }

    /** Newest first, then by name. */
    fun sorted(memos: List<MemoInfo>): List<MemoInfo> =
        memos.sortedWith(compareByDescending<MemoInfo> { it.createdAt }.thenBy { it.fileName.lowercase(Locale.ROOT) })

    /** "Aucun mémo", "1 mémo · 0:42", "3 mémos · 12:05". */
    fun countText(memos: List<MemoInfo>): String = when (memos.size) {
        0 -> "Aucun mémo"
        1 -> "1 mémo · ${duration(memos[0].durationMs)}"
        else -> "${memos.size} mémos · ${duration(memos.sumOf { it.durationMs })}"
    }

    /** Where a tap at [fraction] (0..1) of the outline sends the playback, in ms. */
    fun seekTo(fraction: Float, durationMs: Long): Long = (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0)).toLong()

    /** Playback speeds offered, in order (a tap moves to the next one). */
    val speeds = listOf(1f, 1.5f, 2f, 0.75f)

    fun nextSpeed(current: Float): Float = speeds[(speeds.indexOf(current).coerceAtLeast(0) + 1) % speeds.size]

    /** 1.5 → "1,5×", 1 → "1×". */
    fun speedText(speed: Float): String =
        (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"
}
