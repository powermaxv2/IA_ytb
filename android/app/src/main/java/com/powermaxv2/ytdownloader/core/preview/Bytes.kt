package com.powermaxv2.ytdownloader.core.preview

import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale

// Small pure helpers of the Aperçu: sizes, hex dump, kind names, extension checks. Unit tested (plain JUnit).

/** Sizes for people: "0 o", "980 o", "1,5 Ko", "12,3 Mo" (powers of 1000, like Android's own file manager). */
object HumanSize {
    private val units = listOf("o", "Ko", "Mo", "Go", "To")

    fun format(bytes: Long): String {
        if (bytes < 1000) return "${bytes.coerceAtLeast(0)} o"
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1000 && i < units.lastIndex) {
            v /= 1000
            i++
        }
        // 999.95 Ko would print "1000,0 Ko": move up a unit instead.
        if (v >= 999.95 && i < units.lastIndex) {
            v /= 1000
            i++
        }
        return String.format(Locale.FRANCE, "%.1f %s", v, units[i])
    }

    /** The exact count, grouped by thousands: "1 234 567 octets". */
    fun exact(bytes: Long): String {
        val digits = bytes.coerceAtLeast(0).toString()
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        return if (bytes == 1L) "1 octet" else "$grouped octets"
    }
}

/** Hex dump of the first bytes of a file, as on a hex editor: offset, bytes, printable ASCII. */
object HexDump {
    /** Bytes shown in the info sheet. */
    const val MAX_BYTES = 256

    /**
     * Lines of `0000  89 50 4E 47 0D 0A 1A 0A  .PNG....` ([perLine] bytes each, the last line padded so the ASCII
     * column stays aligned). At most [max] bytes of [bytes] are shown.
     */
    fun lines(bytes: ByteArray, perLine: Int = 8, max: Int = MAX_BYTES): List<String> {
        require(perLine > 0)
        val count = minOf(bytes.size, max)
        val out = ArrayList<String>((count + perLine - 1) / perLine)
        var offset = 0
        while (offset < count) {
            val end = minOf(offset + perLine, count)
            val sb = StringBuilder()
            sb.append(String.format(Locale.ROOT, "%04X", offset)).append("  ")
            for (i in offset until offset + perLine) {
                if (i < end) {
                    sb.append(String.format(Locale.ROOT, "%02X", bytes[i].toInt() and 0xFF))
                } else {
                    sb.append("  ")
                }
                sb.append(' ')
            }
            sb.append(' ')
            for (i in offset until end) sb.append(printable(bytes[i]))
            out.add(sb.toString().trimEnd())
            offset = end
        }
        return out
    }

    /** Printable ASCII as itself, anything else as a dot. */
    fun printable(b: Byte): Char {
        val c = b.toInt() and 0xFF
        return if (c in 0x20..0x7E) c.toChar() else '.'
    }

    /** Lowercase hex of [bytes] (a SHA-256 digest…). */
    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) append(String.format(Locale.ROOT, "%02x", b.toInt() and 0xFF))
    }
}

/** French names of the kinds, as the badge and the info sheet show them. */
object KindNames {
    fun label(kind: FileKind): String = when (kind) {
        FileKind.IMAGE -> "Image"
        FileKind.VIDEO -> "Vidéo"
        FileKind.AUDIO -> "Son"
        FileKind.PDF -> "PDF"
        FileKind.TEXT -> "Texte"
        FileKind.CODE -> "Code"
        FileKind.JSON -> "JSON"
        FileKind.MARKDOWN -> "Markdown"
        FileKind.CSV -> "CSV"
        FileKind.ZIP -> "Archive ZIP"
        FileKind.OTHER -> "Autre"
    }
}

/**
 * Whether a file's extension lies about its content (a ".jpg" that is a PNG). Extensions of the same container
 * are the same family: ".m4a" holding an "mp42" MP4 box, ".webm" read as Matroska, ".docx" being a ZIP are fine.
 */
object ExtensionCheck {
    private val families: List<Set<String>> = listOf(
        setOf("jpg", "jpeg", "jpe", "jfif"),
        setOf("png", "apng"),
        setOf("gif"),
        setOf("webp"),
        setOf("bmp", "dib"),
        setOf("tif", "tiff", "dng"),
        setOf("heic", "heif", "avif"),
        // ISO base media (ftyp): videos, M4A audio, 3GP, QuickTime.
        setOf("mp4", "m4v", "m4a", "m4b", "m4p", "mov", "qt", "3gp", "3g2", "f4v", "f4a"),
        setOf("mkv", "webm", "mka", "mk3d"),
        setOf("avi"),
        setOf("wav", "wave"),
        setOf("mp3", "mp2", "aac"),
        setOf("flac"),
        setOf("ogg", "oga", "ogv", "opus", "spx"),
        setOf("pdf", "ai"),
        setOf(
            "zip", "apk", "jar", "aar", "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "xapk", "apks",
            "kmz", "cbz", "war", "ipa", "whl", "nupkg", "vsix", "xpi", "3mf", "sketch",
        ),
    )

    /**
     * The extension the bytes ([head]) say the file should have, when [name]'s extension belongs to another
     * family; null when they agree, when the name has no extension, or when the bytes match no known signature.
     */
    fun mismatch(name: String, head: ByteArray): String? {
        val ext = SafeNames.extensionOf(name)
        if (ext.isEmpty()) return null
        val sniffed = FileKind.sniff(head) ?: return null
        val family = families.firstOrNull { sniffed.ext in it } ?: return null
        return if (ext in family) null else sniffed.ext
    }
}
