package com.powermaxv2.ytdownloader.core.convert

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

// CSV reading and writing (RFC 4180, plus what spreadsheets really produce: ";" from French Excel, tabs, CR or LF
// line ends, a byte order mark). Pure: used by the Convertisseur (CSV ⇄ JSON) and reusable by the Aperçu.

/** A parsed table: [rows] of cells (the first row is usually the header). [truncated] = stopped at maxRows. */
data class CsvTable(val rows: List<List<String>>, val delimiter: Char, val truncated: Boolean) {
    /** Widest row (rows may have different lengths). */
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

object Csv {
    /** Delimiters tried by [detectDelimiter], in order of preference when they tie. */
    val DELIMITERS = charArrayOf(',', ';', '\t', '|')

    /**
     * The delimiter of [text]: the candidate found most often outside quotes on the first lines (up to 20 records),
     * preferring one that gives every line the same count. Comma when nothing is found.
     */
    fun detectDelimiter(text: String): Char {
        val perLine = ArrayList<IntArray>()
        var counts = IntArray(DELIMITERS.size)
        var inQuotes = false
        var lineHasContent = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        while (i < text.length && perLine.size < 20) {
            val c = text[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (lineHasContent) perLine.add(counts)
                counts = IntArray(DELIMITERS.size)
                lineHasContent = false
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                i++
                continue
            } else if (!inQuotes) {
                val k = DELIMITERS.indexOf(c)
                if (k >= 0) counts[k]++
            }
            lineHasContent = true
            i++
        }
        if (lineHasContent && perLine.size < 20) perLine.add(counts)
        var best = ','
        var bestScore = 0
        DELIMITERS.forEachIndexed { k, d ->
            val first = perLine.firstOrNull()?.get(k) ?: 0
            if (first == 0) return@forEachIndexed
            // Present the same number of times on every line beats more frequent but irregular.
            val consistent = perLine.all { it[k] == first }
            val score = first + if (consistent) 1_000_000 else 0
            if (score > bestScore) {
                best = d
                bestScore = score
            }
        }
        return best
    }

    /**
     * Parses [text] (RFC 4180): quoted fields may hold the delimiter, line breaks and doubled quotes (""); line
     * ends may be CRLF, LF or CR; a leading byte order mark is dropped; an unclosed quote takes the rest of the text.
     * Blank lines are skipped when [skipBlankLines]. Stops after [maxRows] rows (then [CsvTable.truncated]).
     */
    fun parse(
        text: String,
        delimiter: Char = detectDelimiter(text),
        maxRows: Int = Int.MAX_VALUE,
        skipBlankLines: Boolean = true,
    ): CsvTable {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        // The field started with a quote (text after its closing quote is kept as is, leniently).
        var quotedField = false
        var i = if (text.startsWith('﻿')) 1 else 0
        var truncated = false

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            quotedField = false
        }

        fun endRow(): Boolean {
            endField()
            val blank = row.size == 1 && row[0].isEmpty()
            if (!(blank && skipBlankLines)) {
                if (rows.size >= maxRows) {
                    truncated = true
                    return false
                }
                rows.add(row)
            }
            row = ArrayList()
            return true
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty() && !quotedField) {
                        inQuotes = true
                        quotedField = true
                    } else {
                        field.append(c)
                    }
                    delimiter -> endField()
                    '\r', '\n' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        if (!endRow()) return CsvTable(rows, delimiter, truncated = true)
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        // Last record without a line end.
        if (field.isNotEmpty() || row.isNotEmpty() || quotedField) {
            if (!endRow()) return CsvTable(rows, delimiter, truncated = true)
        }
        return CsvTable(rows, delimiter, truncated)
    }

    /** One field, quoted when it holds the delimiter, a quote, a line break or edge spaces. */
    fun escape(value: String, delimiter: Char = ','): String {
        val needs = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(' ') || value.endsWith(' ')
        return if (needs) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /** Writes [rows] as CSV with CRLF line ends (RFC 4180). */
    fun write(rows: List<List<String>>, delimiter: Char = ','): String = buildString {
        rows.forEach { row ->
            row.forEachIndexed { index, value ->
                if (index > 0) append(delimiter)
                append(escape(value, delimiter))
            }
            append("\r\n")
        }
    }
}

/** Text files of unknown encoding: UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252 (old Excel). */
object TextDecode {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
