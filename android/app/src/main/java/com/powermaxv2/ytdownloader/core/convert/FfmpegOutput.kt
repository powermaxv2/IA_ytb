package com.powermaxv2.ytdownloader.core.convert

// Reading what FFmpeg prints: its progress (stdout, `-progress pipe:1`) and its log (stderr). Pure, unit tested.

object FfmpegProgress {
    private val durationRegex = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")

    /** Length in seconds from a log line such as "  Duration: 00:03:12.48, start: 0.000000, bitrate: …". */
    fun parseDuration(line: String): Double? {
        val m = durationRegex.find(line) ?: return null
        val (h, min, s) = m.destructured
        val total = h.toLong() * 3600 + min.toLong() * 60 + (s.toDoubleOrNull() ?: return null)
        return total.takeIf { it > 0 }
    }

    /**
     * Position reached, in seconds, from a `-progress` line ("out_time_us=12345678"; "out_time_ms" is also in
     * microseconds, a known FFmpeg quirk), or null for any other line and for "N/A".
     */
    fun parseOutTime(line: String): Double? {
        val key = line.substringBefore('=', "").trim()
        if (key != "out_time_us" && key != "out_time_ms") return null
        val us = line.substringAfter('=').trim().toLongOrNull() ?: return null
        return (us.coerceAtLeast(0) / 1_000_000.0)
    }

    /** True on the last block of `-progress` ("progress=end"). */
    fun isEnd(line: String): Boolean = line.trim() == "progress=end"

    /** 0..1 from a position and a length; null when the length is unknown. */
    fun fraction(seconds: Double, duration: Double?): Float? {
        if (duration == null || duration <= 0) return null
        return (seconds / duration).toFloat().coerceIn(0f, 1f)
    }
}

/** Short French messages for FFmpeg failures, from the last lines of its log. */
object FfmpegErrors {
    const val GENERIC = "La conversion a échoué."

    fun message(log: List<String>, target: Target?): String {
        val text = log.joinToString("\n").lowercase()
        return when {
            "no space left" in text -> "Plus assez d'espace libre sur le téléphone."
            ("matches no streams" in text || "does not contain any stream" in text) && target?.family == Family.AUDIO ->
                "Ce fichier n'a pas de son à extraire."
            "matches no streams" in text || "does not contain any stream" in text ->
                "Aucune image ni aucun son utilisable dans ce fichier."
            "invalid data found when processing input" in text || "moov atom not found" in text ->
                "Ce fichier est illisible ou abîmé."
            "decoder" in text && ("not found" in text || "unknown" in text) || "unsupported codec" in text ->
                "Ce fichier utilise un codec que Gulp ne sait pas lire."
            "could not find tag for codec" in text || "not supported in container" in text || "codec not currently supported" in text ->
                "Ce format ne peut pas contenir ces pistes : essayez MP4 ou MKV."
            "permission denied" in text -> "Gulp n'a pas pu lire ou écrire le fichier."
            "cannot allocate memory" in text || "out of memory" in text ->
                "Pas assez de mémoire : essayez une résolution plus basse."
            "error while opening encoder" in text || "error initializing output stream" in text ->
                "Ces réglages ne conviennent pas à ce fichier : essayez une autre qualité ou résolution."
            else -> GENERIC
        }
    }
}
