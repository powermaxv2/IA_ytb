package com.powermaxv2.ytdownloader.core.readaloud

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.math.roundToInt

// Text for the Lecture à voix haute, made speakable: what a file holds turned into plain sentences (Markdown signs,
// subtitle timings, HTML tags dropped), then cut into short pieces the phone's voice reads one after the other.
// Pure Kotlin: tested on the JVM.

/** What kind of file the text comes from; null from [SpeechText.kindOf] = not readable by this tool. */
enum class SpeechSource(val label: String) {
    TEXT("Texte"),
    MARKDOWN("Markdown"),
    SUBTITLES("Sous-titres"),
    HTML("Page web"),
    DOCX("Word"),
    ODT("OpenDocument"),
    EPUB("Livre EPUB"),
    PDF("PDF"),
}

/** A piece of the text read in one go: [start] until [end] in the whole text. */
data class SpeechChunk(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

object SpeechText {
    /** Longest text kept (about 3 h of speech); a longer document is cut at the last word before. */
    const val MAX_CHARS = 200_000

    /** Longest text typed or pasted in the field. */
    const val MAX_TYPED = 20_000

    /** Longest piece read in one go: short enough to follow on screen and to restart quickly on a tap. */
    const val CHUNK_MAX = 360

    /** Longest piece written in one go when the text is saved as audio (Android accepts 4000). */
    const val BLOCK_MAX = 3_000

    /** Characters read per second at speed ×1 (French, phone voices). */
    private const val CHARS_PER_SECOND = 14.5

    const val SAMPLE = "Bonjour ! Je suis la voix de ton téléphone. Colle un texte ou ouvre un fichier, et je te le lis à voix haute, phrase par phrase."

    // MARK: - Kinds

    fun kindOf(name: String, mime: String?): SpeechSource? {
        val ext = SafeNames.extensionOf(name).lowercase(Locale.ROOT)
        return when (ext) {
            "txt", "text", "log", "csv" -> SpeechSource.TEXT
            "md", "markdown" -> SpeechSource.MARKDOWN
            "srt", "vtt" -> SpeechSource.SUBTITLES
            "html", "htm", "xhtml" -> SpeechSource.HTML
            "docx" -> SpeechSource.DOCX
            "odt" -> SpeechSource.ODT
            "epub" -> SpeechSource.EPUB
            "pdf" -> SpeechSource.PDF
            else -> when {
                mime == null -> null
                mime == "application/pdf" -> SpeechSource.PDF
                mime == "text/html" -> SpeechSource.HTML
                mime == "text/markdown" -> SpeechSource.MARKDOWN
                mime == "application/epub+zip" -> SpeechSource.EPUB
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> SpeechSource.DOCX
                mime == "application/vnd.oasis.opendocument.text" -> SpeechSource.ODT
                mime.startsWith("text/") -> SpeechSource.TEXT
                else -> null
            }
        }
    }

    // MARK: - Bytes → text

    /** [bytes] as text: UTF-8 (BOM dropped) when it is valid UTF-8, else Windows-1252 (old Windows .txt files). */
    fun decode(bytes: ByteArray): String {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, start, bytes.size - start, Charset.forName("windows-1252"))
        }
    }

    // MARK: - Formats → speakable text

    /** Markdown read as prose: titles, lists and quotes keep their words, the signs around them go. */
    fun fromMarkdown(markdown: String): String {
        val out = StringBuilder()
        var fenced = false
        for (raw in markdown.lineSequence()) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                fenced = !fenced
                out.append('\n')
                continue
            }
            if (fenced) {
                // Code is not prose: one word says it is there.
                continue
            }
            var l = line.trimStart()
            if (Regex("""^([-*_]\s*){3,}$""").matches(l)) {
                out.append('\n')
                continue
            }
            l = l.replace(Regex("""^#{1,6}\s+"""), "")
                .replace(Regex("""^(>\s?)+"""), "")
                .replace(Regex("""^[-*+]\s+(\[[ xX]]\s+)?"""), "")
            if (l.startsWith("|")) {
                // Table: a separator row says nothing; a row is read cell after cell.
                if (Regex("""^\|?[\s:|-]+\|?$""").matches(l)) continue
                l = l.trim('|').split('|').joinToString(", ") { it.trim() }
            }
            l = l.replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "$1")
                .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                .replace(Regex("""<https?://[^>]+>"""), "")
                .replace(Regex("""<[^>]+>"""), "")
                .replace(Regex("""(\*\*|__|~~|`)"""), "")
                .replace(Regex("""(?<![\p{L}\p{N}])[*_](?=\S)|(?<=\S)[*_](?![\p{L}\p{N}])"""), "")
            out.append(l).append('\n')
        }
        return out.toString()
    }

    /** SRT / WebVTT subtitles as one flowing text: numbers, timings, notes and tags dropped. */
    fun fromSubtitles(subtitles: String): String {
        val words = ArrayList<String>()
        var skipBlock = false
        for (raw in subtitles.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                skipBlock = false
                continue
            }
            if (skipBlock) continue
            when {
                line.startsWith("WEBVTT") -> Unit
                line.startsWith("NOTE") || line.startsWith("STYLE") || line.startsWith("REGION") -> skipBlock = true
                line.contains("-->") -> Unit
                line.all { it.isDigit() } -> Unit
                else -> {
                    val clean = line
                        .replace(Regex("""\{\\[^}]*}"""), "")
                        .replace(Regex("""<[^>]+>"""), "")
                        .removePrefix("- ")
                        .trim()
                    if (clean.isNotEmpty()) words.add(clean)
                }
            }
        }
        return words.joinToString(" ")
    }

    /** A web page's words: scripts, styles and tags dropped, blocks on their own lines, entities decoded. */
    fun fromHtml(html: String): String {
        val text = html
            .replace(Regex("""(?is)<(script|style|head|template|svg|noscript)\b.*?</\1\s*>"""), " ")
            .replace(Regex("""(?s)<!--.*?-->"""), " ")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</?(p|div|h[1-6]|li|ul|ol|tr|table|section|article|blockquote|header|footer|pre|dd|dt|figcaption)\b[^>]*>"""), "\n")
            .replace(Regex("""(?i)</t[dh]\s*>"""), ", ")
            .replace(Regex("""<[^>]*>"""), "")
            // Nested blocks leave runs of line ends: one is enough between two blocks.
            .replace(Regex("""\n[ \t]*(\n[ \t]*)+"""), "\n")
        return entities(text)
    }

    private val NAMED = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "laquo" to "«", "raquo" to "»",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "euro" to "€", "copy" to "©", "deg" to "°",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "agrave" to "à", "acirc" to "â",
        "ccedil" to "ç", "ugrave" to "ù", "ucirc" to "û", "icirc" to "î", "iuml" to "ï", "ocirc" to "ô",
        "oelig" to "œ", "Eacute" to "É", "Egrave" to "È", "Agrave" to "À", "Ccedil" to "Ç",
    )

    /** HTML / XML entities (&amp; &#233; &#xE9; &eacute;) decoded; unknown ones are left as they are. */
    fun entities(text: String): String = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});""").replace(text) { m ->
        val name = m.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED[name]
        } ?: m.value
    }

    private fun codePoint(cp: Int): String? =
        if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null

    // MARK: - Clean, cut

    /**
     * The text as it is read and shown: line ends unified, runs of spaces made one, at most one empty line between
     * paragraphs, invisible characters dropped, and cut at the last word before [MAX_CHARS].
     */
    fun normalize(raw: String, max: Int = MAX_CHARS): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F​-‍﻿­]"""), "")
            .replace(Regex("""[\t   ]"""), " ")
            .lines()
            .map { it.replace(Regex(""" {2,}"""), " ").trim() }
        val out = StringBuilder()
        var blank = 0
        for (line in lines) {
            if (line.isEmpty()) {
                blank++
                continue
            }
            if (out.isNotEmpty()) out.append(if (blank > 0) "\n\n" else "\n")
            blank = 0
            out.append(line)
        }
        if (out.length <= max) return out.toString()
        val cut = out.lastIndexOf(" ", max).takeIf { it > max / 2 } ?: max
        return out.substring(0, cut).trimEnd()
    }

    /** Whether [text] holds something to say (a letter or a digit). */
    fun hasWords(text: CharSequence): Boolean = text.any { it.isLetterOrDigit() }

    private fun isEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '…' || c == '。' || c == '؟'
    private fun isCloser(c: Char) = c == '"' || c == '»' || c == '”' || c == '’' || c == ')' || c == ']' || c == '\'' || c == ' '

    /**
     * [text] cut in pieces read one after the other: one sentence each (ends at . ! ? … or a line end), a sentence
     * longer than [max] cut after a comma, else between two words. Pieces without a letter or digit are skipped.
     */
    fun chunks(text: String, max: Int = CHUNK_MAX): List<SpeechChunk> {
        val out = ArrayList<SpeechChunk>()
        var i = 0
        val n = text.length
        while (i < n) {
            // Skip the space before a sentence.
            while (i < n && text[i].isWhitespace()) i++
            if (i >= n) break
            var j = i
            var end = -1
            while (j < n) {
                val c = text[j]
                if (c == '\n') {
                    end = j
                    break
                }
                if (isEnd(c)) {
                    var k = j + 1
                    while (k < n) {
                        k += when {
                            isEnd(text[k]) || (isCloser(text[k]) && text[k] != ' ') -> 1
                            // French typography: "Viens ! »", a space before the closing guillemet.
                            text[k] == ' ' && k + 1 < n && (text[k + 1] == '»' || text[k + 1] == '”') -> 2
                            else -> break
                        }
                    }
                    if (k >= n || text[k].isWhitespace()) {
                        // "ça va… merci": an ellipsis followed by a small letter goes on with the sentence.
                        var next = k
                        while (next < n && text[next] == ' ') next++
                        val goesOn = text[k - 1] == '…' && next < n && text[next].isLowerCase()
                        if (!goesOn) {
                            end = k
                            break
                        }
                    }
                    j = k
                    continue
                }
                j++
            }
            if (end < 0) end = n
            addSentence(text, i, end, max, out)
            i = end
        }
        return out
    }

    private fun addSentence(text: String, from: Int, to: Int, max: Int, out: MutableList<SpeechChunk>) {
        var start = from
        while (start < to) {
            while (start < to && text[start].isWhitespace()) start++
            if (start >= to) return
            var end = to
            if (end - start > max) {
                val limit = start + max
                val comma = (limit downTo start + max / 3).firstOrNull { k -> k < to && k > start && text[k - 1] in ",;:" && text[k].isWhitespace() }
                val space = (limit downTo start + max / 3).firstOrNull { k -> k < to && text[k].isWhitespace() }
                end = comma ?: space ?: limit
            }
            var e = end
            while (e > start && text[e - 1].isWhitespace()) e--
            if (e > start && hasWords(text.subSequence(start, e))) out.add(SpeechChunk(start, e))
            start = end
        }
    }

    /**
     * [chunks] grouped into blocks of up to [max] characters (whole pieces only), for saving as audio: fewer, longer
     * calls to the voice. Each block runs from its first piece's start to its last piece's end.
     */
    fun blocks(chunks: List<SpeechChunk>, max: Int = BLOCK_MAX): List<SpeechChunk> {
        val out = ArrayList<SpeechChunk>()
        var current: SpeechChunk? = null
        for (c in chunks) {
            val cur = current
            current = when {
                cur == null -> c
                c.end - cur.start <= max -> SpeechChunk(cur.start, c.end)
                else -> {
                    out.add(cur)
                    c
                }
            }
        }
        current?.let(out::add)
        return out
    }

    /** Index of the piece holding (or following) [offset]; the last one past the end; 0 for no pieces. */
    fun chunkAt(chunks: List<SpeechChunk>, offset: Int): Int {
        if (chunks.isEmpty()) return 0
        val i = chunks.indexOfFirst { offset < it.end }
        return if (i < 0) chunks.lastIndex else i
    }

    /** Characters already read before piece [index]. */
    fun readBefore(chunks: List<SpeechChunk>, index: Int): Int = chunks.take(index.coerceIn(0, chunks.size)).sumOf { it.length }

    // MARK: - Durations, names

    /** About how long [chars] characters take to read at [rate] (×1 = normal speed), in seconds. */
    fun estimateSeconds(chars: Int, rate: Float): Int =
        if (chars <= 0) 0 else (chars / (CHARS_PER_SECOND * rate.coerceIn(0.25f, 4f))).roundToInt().coerceAtLeast(1)

    /** "45 s", "3 min", "1 h 05". */
    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3_600 -> "${(seconds + 30) / 60} min"
        else -> {
            val minutes = (seconds + 30) / 60
            "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
        }
    }

    /** "Mon document (lu).m4a" for a text from "Mon document.pdf"; "Texte lu.m4a" for a typed one. */
    fun outputName(sourceName: String?, ext: String): String {
        val base = sourceName?.let { SafeNames.baseOf(it) }?.takeIf { it.isNotBlank() }?.let { "$it (lu)" } ?: "Texte lu"
        // withExtension drops what follows the last dot: a stand-in extension keeps "v1.2 (lu)" whole.
        return SafeNames.withExtension(SafeNames.sanitize(base, "Texte lu") + ".x", ext)
    }
}
