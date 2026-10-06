package com.powermaxv2.ytdownloader.core.preview

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// Text files in the Aperçu: decoding the first Mo and cutting it into lines the screen can draw. Pure.

/** Text decoded for display, with the charset that worked ("UTF-8", "UTF-16LE", "ISO-8859-1"). */
data class DecodedText(val text: String, val charset: String)

object PreviewText {
    /** Most bytes read from a text file (the banner "Aperçu limité au premier Mo" shows when there is more). */
    const val MAX_BYTES = 1024 * 1024

    /** Longest piece of a line drawn as one row: longer lines are split (wrapping text) or cut (code). */
    const val MAX_LINE_CHARS = 2000

    /**
     * UTF-8 (with or without BOM) or UTF-16 with a BOM; anything that is not valid UTF-8 is read as ISO-8859-1.
     * [cut] = the bytes stop in the middle of the file: a UTF-8 sequence broken by the cut is dropped first, so
     * the cut alone never sends a UTF-8 file to the fallback.
     */
    fun decode(bytes: ByteArray, cut: Boolean = false): DecodedText {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            val end = if (cut) utf8End(bytes) else bytes.size
            return DecodedText(String(bytes, 3, (end - 3).coerceAtLeast(0), Charsets.UTF_8), "UTF-8")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            val len = (bytes.size - 2) and 1.inv()
            return DecodedText(String(bytes, 2, len, Charsets.UTF_16LE), "UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            val len = (bytes.size - 2) and 1.inv()
            return DecodedText(String(bytes, 2, len, Charsets.UTF_16BE), "UTF-16BE")
        }
        val end = if (cut) utf8End(bytes) else bytes.size
        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, end))
                .toString()
            DecodedText(text, "UTF-8")
        } catch (e: CharacterCodingException) {
            DecodedText(String(bytes, Charsets.ISO_8859_1), "ISO-8859-1")
        }
    }

    /**
     * Length of [bytes] without a UTF-8 sequence left incomplete at the end (a lead byte whose continuation
     * bytes were cut off). At most 3 bytes are dropped.
     */
    fun utf8End(bytes: ByteArray): Int {
        val n = bytes.size
        var i = n - 1
        var continuation = 0
        while (i >= 0 && continuation < 3 && (bytes[i].toInt() and 0xC0) == 0x80) {
            continuation++
            i--
        }
        if (i < 0) return n
        val lead = bytes[i].toInt() and 0xFF
        val needed = when {
            lead and 0x80 == 0 -> 0
            lead and 0xE0 == 0xC0 -> 1
            lead and 0xF0 == 0xE0 -> 2
            lead and 0xF8 == 0xF0 -> 3
            else -> return n
        }
        return if (continuation < needed) i else n
    }

    /**
     * Rows of a wrapping text view: one per line (CR, LF or CRLF), lines longer than [maxChars] split into
     * several rows so that no single row is huge.
     */
    fun rows(text: String, maxChars: Int = MAX_LINE_CHARS): List<String> {
        val out = ArrayList<String>()
        for (line in splitLines(text)) {
            if (line.length <= maxChars) {
                out.add(line)
            } else {
                var i = 0
                while (i < line.length) {
                    out.add(line.substring(i, minOf(i + maxChars, line.length)))
                    i += maxChars
                }
            }
        }
        return out
    }

    /** Lines of [text], split on CRLF, LF or a lone CR (a final line break gives no empty last line). */
    fun splitLines(text: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == '\r') {
                out.add(text.substring(start, i))
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                start = i + 1
            }
            i++
        }
        if (start < text.length) out.add(text.substring(start))
        if (out.isEmpty()) out.add("")
        return out
    }
}
