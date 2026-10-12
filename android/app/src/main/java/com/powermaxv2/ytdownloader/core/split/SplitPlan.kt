package com.powermaxv2.ytdownloader.core.split

import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.trim.TrimPlan
import com.powermaxv2.ytdownloader.core.trim.TrimRange
import java.util.Locale

// The Découpe statut's arithmetic: where a long video is cut so each part fits in one WhatsApp status, and how the
// parts are named. The cut itself is the Découpeur's (TrimPlan.args, output VIDEO). Pure: unit tested in SplitPlanTest.

/** Longest status a part must fit in. WhatsApp takes 1 min per status (30 s on older versions). */
enum class SplitLength(val ms: Long, val label: String) {
    HALF_MINUTE(30_000L, "30 s"),
    MINUTE(60_000L, "1 min"),
}

object SplitPlan {
    /** More parts than this is not a status series any more: the video is refused with a clear message. */
    const val MAX_PARTS = 60

    /** A last part shorter than this (fixed lengths) is dropped: nothing to watch in it. */
    const val MIN_LAST_MS = 1_000L

    /**
     * A copied part may run a little past its end (the copy starts on the key frame before its start). Over the
     * status length by more than this, the part is cut again exactly (re-encoded).
     */
    const val TOLERANCE_MS = 250L

    /** Number of parts for a video of [durationMs]. */
    fun count(durationMs: Long, length: SplitLength): Int {
        if (durationMs <= 0L) return 0
        return ((durationMs + length.ms - 1) / length.ms).toInt()
    }

    /**
     * The parts of a video of [durationMs], in order, end to end. [equal]: all the same length (a 2:10 video
     * gives 3 × 0:43, never a last status of 10 s). Otherwise full [length] parts and the rest at the end.
     */
    fun parts(durationMs: Long, length: SplitLength, equal: Boolean): List<TrimRange> {
        val n = count(durationMs, length)
        if (n == 0) return emptyList()
        if (equal) {
            return (0 until n).map { i -> TrimRange(durationMs * i / n, durationMs * (i + 1) / n) }
        }
        val parts = (0 until n).map { i -> TrimRange(i * length.ms, minOf((i + 1) * length.ms, durationMs)) }
        return if (parts.size > 1 && parts.last().durationMs < MIN_LAST_MS) parts.dropLast(1) else parts
    }

    /** Why a video of [durationMs] cannot be cut, or null when it can. */
    fun refusal(durationMs: Long, length: SplitLength): String? = when {
        durationMs <= length.ms -> "Cette vidéo tient déjà dans un seul statut (${TrimPlan.timecode(durationMs).substringBeforeLast('.')}) : pas besoin de la couper."
        count(durationMs, length) > MAX_PARTS -> "Il faudrait ${count(durationMs, length)} statuts : $MAX_PARTS au plus. Gardez d'abord le passage voulu avec le Découpeur."
        else -> null
    }

    /** Whether a part that came out [actualMs] long must be cut again exactly to fit in [length]. */
    fun tooLong(actualMs: Long, length: SplitLength): Boolean = actualMs > length.ms + TOLERANCE_MS

    /** "1", or "01" in a series of ten or more (so the Gallery sorts the names in order). */
    fun number(index: Int, count: Int): String {
        val width = count.toString().length
        return (index + 1).toString().padStart(width, '0')
    }

    /** "vacances (statut 1 sur 3).mp4", "vacances (statut 04 sur 12).mp4". */
    fun outputName(sourceName: String, index: Int, count: Int, ext: String): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName)).ifBlank { "statut" }
        val suffix = " (statut ${number(index, count)} sur $count).${ext.lowercase(Locale.ROOT)}"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }

    /** "0:43" (whole seconds, rounded down). */
    fun short(ms: Long): String = TrimPlan.timecode(ms).substringBeforeLast('.')
}
