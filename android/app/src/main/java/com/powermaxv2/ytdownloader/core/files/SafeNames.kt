package com.powermaxv2.ytdownloader.core.files

import java.util.Locale

/**
 * File names that come from outside (a shared file, a picked document, a ZIP entry) are untrusted:
 * every name that becomes part of a path on the phone goes through [sanitize] first. Pure, unit tested.
 */
object SafeNames {
    /** Longest name we ever write (extension included). */
    const val MAX_LENGTH = 120

    /** Longest extension kept when a name is shortened (".markdown" fits, a 40-char "extension" does not). */
    private const val MAX_EXTENSION = 10

    /**
     * Path separators, reserved and control characters, and the bidirectional controls (U+200E/U+200F,
     * U+202A–U+202E, U+2066–U+2069) that could show "facture\u202Efdp.exe" as "facture" + "exe.pdf".
     */
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F\u200E\u200F\u202A-\u202E\u2066-\u2069]""")

    /**
     * A safe single path segment: path separators, reserved, control and bidi control characters become `_`,
     * leading dots are stripped (no hidden files, no `..`), surrounding blanks and trailing dots are trimmed, and the name is
     * cut to [MAX_LENGTH] characters keeping its extension. Empty result → [fallback].
     */
    fun sanitize(name: String, fallback: String = "fichier"): String {
        val cleaned = name
            .replace(forbidden, "_")
            .trim()
            .trimStart('.')
            .trimEnd('.', ' ')
            .trim()
        if (cleaned.isEmpty()) return fallback
        return shorten(cleaned)
    }

    /** Lowercase extension without the dot ("photo.JPG" → "jpg"), "" when there is none (".bashrc" has none). */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /** The name without its extension ("photo.jpg" → "photo", "archive.tar.gz" → "archive.tar"). */
    fun baseOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    /**
     * [base] (a file name, its own extension dropped) with the extension [ext] ("png" or ".png"), sanitized:
     * `withExtension("Vacances.HEIC", "jpg")` → "Vacances.jpg". The extension is kept even when the base is
     * shortened to fit [MAX_LENGTH].
     */
    fun withExtension(base: String, ext: String): String {
        val cleanExt = ext.trim().trimStart('.').lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }.take(MAX_EXTENSION)
        val stem = sanitize(baseOf(sanitize(base, fallback = "fichier")), fallback = "fichier")
        if (cleanExt.isEmpty()) return stem
        val room = MAX_LENGTH - cleanExt.length - 1
        return stem.take(room).trimEnd('.', ' ').ifEmpty { "fichier" } + "." + cleanExt
    }

    private fun shorten(name: String): String {
        if (name.length <= MAX_LENGTH) return name
        val ext = extensionOf(name)
        if (ext.isEmpty() || ext.length > MAX_EXTENSION) return name.take(MAX_LENGTH).trimEnd('.', ' ')
        val room = MAX_LENGTH - ext.length - 1
        return baseOf(name).take(room).trimEnd('.', ' ') + "." + ext
    }
}
