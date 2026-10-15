package com.powermaxv2.ytdownloader.core.timer

// Minuteur et chrono, the pure part: named countdowns and one stopwatch with laps, all kept as wall-clock times
// (System.currentTimeMillis) so they keep counting while the app is closed, the screen is off or the phone reboots.
// Tested by TimersTest; the alarms and notifications live in TimerAlarms.

/** One named countdown. Running: [endsAt] is set. Paused or waiting: [leftMs] is what remains. */
data class TimerItem(
    val id: Long,
    val name: String,
    val durationMs: Long,
    val endsAt: Long? = null,
    val leftMs: Long = durationMs,
) {
    val running: Boolean get() = endsAt != null

    /** Time left at [now], never below zero. */
    fun left(now: Long): Long = endsAt?.let { (it - now).coerceAtLeast(0) } ?: leftMs

    /** Reached zero while running: it rings until the user stops it. */
    fun ringing(now: Long): Boolean = endsAt != null && now >= endsAt

    /** Started once and not back to its full duration. */
    fun touched(now: Long): Boolean = running || left(now) != durationMs

    /** Share of the time already gone, 0 → 1. */
    fun progress(now: Long): Float =
        if (durationMs <= 0) 1f else (1f - left(now).toFloat() / durationMs).coerceIn(0f, 1f)
}

/** The stopwatch: [startedAt] is set while it runs; [stoppedMs] is the time gathered before that. Laps are totals. */
data class Stopwatch(
    val startedAt: Long? = null,
    val stoppedMs: Long = 0,
    /** Total time at each "Tour", oldest first. */
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = startedAt != null

    fun elapsed(now: Long): Long = stoppedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    val idle: Boolean get() = !running && stoppedMs == 0L && laps.isEmpty()
}

/** One line of the laps list. */
data class Lap(val number: Int, val splitMs: Long, val totalMs: Long, val best: Boolean, val worst: Boolean)

object Timers {
    const val MAX_COUNT = 12
    const val MAX_NAME = 24
    const val MAX_LAPS = 99

    /** Longest countdown: 23 h 59 min 59 s. */
    const val MAX_DURATION_MS = (23 * 3600 + 59 * 60 + 59) * 1000L

    /** Durations offered in one touch, in seconds. */
    val presets: List<Int> = listOf(60, 3 * 60, 5 * 60, 10 * 60, 15 * 60, 25 * 60, 45 * 60, 3600)

    /** Names offered when the field is empty. */
    val nameIdeas: List<String> = listOf("Pâtes", "Œufs", "Four", "Lessive", "Sieste", "Parking", "Thé", "Pause")

    // MARK: - Timers

    fun add(list: List<TimerItem>, name: String, durationMs: Long, now: Long, start: Boolean = true): List<TimerItem> {
        if (list.size >= MAX_COUNT) return list
        val duration = durationMs.coerceIn(1000, MAX_DURATION_MS)
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        val clean = name.trim().take(MAX_NAME).ifEmpty { defaultName(list) }
        val item = TimerItem(id, clean, duration, endsAt = if (start) now + duration else null)
        return list + item
    }

    /** "Minuteur 2" for the second timer without a name. */
    fun defaultName(list: List<TimerItem>): String {
        val used = list.map { it.name }.toSet()
        var n = list.size + 1
        while ("Minuteur $n" in used) n++
        return "Minuteur $n"
    }

    fun start(list: List<TimerItem>, id: Long, now: Long): List<TimerItem> = list.map {
        if (it.id != id || it.running) it
        else {
            val left = if (it.leftMs <= 0) it.durationMs else it.leftMs
            it.copy(endsAt = now + left, leftMs = left)
        }
    }

    fun pause(list: List<TimerItem>, id: Long, now: Long): List<TimerItem> = list.map {
        if (it.id != id || !it.running || it.ringing(now)) it else it.copy(endsAt = null, leftMs = it.left(now))
    }

    /** Back to the full duration, stopped (also how a ringing timer is silenced). */
    fun reset(list: List<TimerItem>, id: Long): List<TimerItem> = list.map {
        if (it.id != id) it else it.copy(endsAt = null, leftMs = it.durationMs)
    }

    /** One more minute: a ringing timer starts again for one minute ("snooze"). */
    fun plusMinute(list: List<TimerItem>, id: Long, now: Long): List<TimerItem> = list.map {
        when {
            it.id != id -> it
            it.ringing(now) -> it.copy(endsAt = now + 60_000)
            it.running -> it.copy(endsAt = (it.endsAt!! + 60_000).coerceAtMost(now + MAX_DURATION_MS))
            else -> it.copy(leftMs = (it.leftMs + 60_000).coerceAtMost(MAX_DURATION_MS))
        }
    }

    fun remove(list: List<TimerItem>, id: Long): List<TimerItem> = list.filterNot { it.id == id }

    fun rename(list: List<TimerItem>, id: Long, name: String): List<TimerItem> = list.map {
        if (it.id != id) it else it.copy(name = name.trim().take(MAX_NAME).ifEmpty { it.name })
    }

    /** The running timer that rings next, if any. */
    fun next(list: List<TimerItem>, now: Long): TimerItem? =
        list.filter { it.running && !it.ringing(now) }.minByOrNull { it.endsAt!! }

    // MARK: - Stopwatch

    fun startWatch(w: Stopwatch, now: Long): Stopwatch = if (w.running) w else w.copy(startedAt = now)

    fun pauseWatch(w: Stopwatch, now: Long): Stopwatch =
        if (!w.running) w else Stopwatch(startedAt = null, stoppedMs = w.elapsed(now), laps = w.laps)

    fun lap(w: Stopwatch, now: Long): Stopwatch =
        if (!w.running || w.laps.size >= MAX_LAPS) w else w.copy(laps = w.laps + w.elapsed(now))

    fun resetWatch(): Stopwatch = Stopwatch()

    /** Laps newest first, with the fastest and the slowest marked once there are at least two. */
    fun laps(w: Stopwatch): List<Lap> {
        val splits = w.laps.mapIndexed { i, total -> total - (if (i == 0) 0 else w.laps[i - 1]) }
        val best = if (splits.size >= 2) splits.indices.minByOrNull { splits[it] } else null
        val worst = if (splits.size >= 2) splits.indices.maxByOrNull { splits[it] } else null
        return splits.indices.reversed().map { i ->
            Lap(i + 1, splits[i], w.laps[i], best = i == best && best != worst, worst = i == worst && best != worst)
        }
    }

    /** The laps as text to paste. */
    fun lapsText(w: Stopwatch): String = laps(w).reversed().joinToString("\n") {
        "Tour ${it.number} : ${watch(it.splitMs)} (total ${watch(it.totalMs)})"
    }

    // MARK: - Formatting

    /** "4:05", "12:00", "1:02:03": time left of a countdown, rounded up so 0:00 shows only at the end. */
    fun clock(ms: Long): String {
        val total = ((ms.coerceAtLeast(0) + 999) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "00:07.42", "12:00.00", "1:02:03.40": stopwatch time to the hundredth, rounded down. */
    fun watch(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        val cs = (v % 1000) / 10
        val total = v / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
    }

    /** "1 min", "1 h 30", "45 s", "2 min 30": a duration in words for the chips and notifications. */
    fun words(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 && m > 0 -> "$h h ${"%02d".format(m)}"
            h > 0 -> "$h h"
            m > 0 && s > 0 -> "$m min ${"%02d".format(s)}"
            m > 0 -> "$m min"
            else -> "$s s"
        }
    }

    /** Splits [ms] into hours, minutes, seconds (for the duration picker). */
    fun parts(ms: Long): Triple<Int, Int, Int> {
        val total = (ms / 1000).toInt()
        return Triple(total / 3600, (total % 3600) / 60, total % 60)
    }

    fun ofParts(h: Int, m: Int, s: Int): Long = ((h.coerceIn(0, 23) * 3600L) + m.coerceIn(0, 59) * 60L + s.coerceIn(0, 59)) * 1000L

    // MARK: - Storage (one line per timer: id|duration|endsAt|left|name)

    fun encode(list: List<TimerItem>): String = list.joinToString("\n") {
        listOf(it.id, it.durationMs, it.endsAt ?: -1, it.leftMs, it.name.replace('\n', ' ').replace('|', '/')).joinToString("|")
    }

    fun decode(raw: String?): List<TimerItem> = raw.orEmpty().lines().mapNotNull { line ->
        val p = line.split('|', limit = 5)
        if (p.size < 5) return@mapNotNull null
        val id = p[0].toLongOrNull() ?: return@mapNotNull null
        val duration = p[1].toLongOrNull()?.takeIf { it in 1000..MAX_DURATION_MS } ?: return@mapNotNull null
        val endsAt = p[2].toLongOrNull()?.takeIf { it >= 0 }
        val left = p[3].toLongOrNull()?.coerceIn(0, duration) ?: duration
        TimerItem(id, p[4].take(MAX_NAME).ifBlank { "Minuteur" }, duration, endsAt, left)
    }.distinctBy { it.id }.take(MAX_COUNT)

    fun encodeWatch(w: Stopwatch): String = listOf(w.startedAt ?: -1, w.stoppedMs, w.laps.joinToString(",")).joinToString("|")

    fun decodeWatch(raw: String?): Stopwatch {
        val p = raw.orEmpty().split('|')
        if (p.size < 3) return Stopwatch()
        val laps = p[2].split(',').mapNotNull { it.toLongOrNull()?.takeIf { v -> v >= 0 } }
        return Stopwatch(
            startedAt = p[0].toLongOrNull()?.takeIf { it >= 0 },
            stoppedMs = p[1].toLongOrNull()?.coerceAtLeast(0) ?: 0,
            laps = laps.take(MAX_LAPS),
        )
    }
}
