package com.powermaxv2.ytdownloader.core.qr

// The last codes scanned, kept on the phone only, never sent anywhere. Pure Kotlin.

/** A code scanned at [time] (epoch milliseconds). */
data class QrHistoryEntry(val text: String, val format: String, val time: Long) {
    val payload: QrPayload get() = QrPayload.parse(text, format)
}

object QrHistory {
    const val MAX = 8

    /** A code is not saved past this length (a whole letter in a code is not worth keeping). */
    private const val MAX_TEXT = 2_000
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'

    /** [entries] with [entry] on top: the same text scanned again moves up instead of appearing twice. */
    fun add(entries: List<QrHistoryEntry>, entry: QrHistoryEntry): List<QrHistoryEntry> =
        (listOf(entry) + entries.filterNot { it.text == entry.text }).take(MAX)

    fun encode(entries: List<QrHistoryEntry>): String = entries
        .filter { it.text.length <= MAX_TEXT }
        .joinToString(RECORD.toString()) { e ->
            listOf(e.time.toString(), e.format, e.text.replace(RECORD, ' ').replace(FIELD, ' ')).joinToString(FIELD.toString())
        }

    fun decode(stored: String?): List<QrHistoryEntry> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD, limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            parts[2].takeIf { it.isNotEmpty() }?.let { QrHistoryEntry(it, parts[1], time) }
        }.take(MAX)
    }

    /** "à l'instant", "il y a 5 min", "il y a 3 h", "hier", "il y a 4 j", then "12/09". */
    fun ago(time: Long, now: Long): String {
        val minutes = ((now - time) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            minutes < 48 * 60 -> "hier"
            minutes < 7 * 24 * 60 -> "il y a ${minutes / (24 * 60)} j"
            else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.FRANCE).format(java.util.Date(time))
        }
    }
}
