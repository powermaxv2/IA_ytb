package com.powermaxv2.ytdownloader.core.voice

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale

/** One recognised word, with its place in the sound (milliseconds) and how sure the model is (0..1). */
data class VoiceWord(val text: String, val startMs: Long, val endMs: Long, val confidence: Float = 1f) {
    /** Below this the word is shown dimmed: the model hesitated. */
    val unsure: Boolean get() = confidence < UNSURE

    companion object {
        const val UNSURE = 0.6f
    }
}

/** One stretch of speech between two pauses (what Vosk returns as a final result): written as one sentence. */
data class VoiceSentence(val words: List<VoiceWord>) {
    val startMs: Long get() = words.firstOrNull()?.startMs ?: 0L
    val endMs: Long get() = words.lastOrNull()?.endMs ?: 0L
    val text: String get() = VoiceText.sentence(words.map { it.text })
}

/**
 * Turns what Vosk says into text a person reads: Vosk gives lower-case words without punctuation, one result per
 * stretch of speech. Each stretch becomes a sentence (capital letter, final full stop), French spacing kept. Pure,
 * unit tested (no org.json: it is a stub in plain unit tests).
 */
object VoiceText {
    /** Words of a final result `{"result":[{"conf":..,"end":..,"start":..,"word":".."}],"text":".."}`; without timings, from "text". */
    fun parseResult(json: String): List<VoiceWord> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        val result = root["result"] as? List<*>
        if (result != null) {
            val words = result.mapNotNull { item ->
                val w = item as? Map<*, *> ?: return@mapNotNull null
                val text = (w["word"] as? String)?.trim().orEmpty()
                if (text.isEmpty() || text == "<unk>" || text == "[unk]") return@mapNotNull null
                VoiceWord(
                    text = text,
                    startMs = ((w["start"] as? Double ?: 0.0) * 1000).toLong(),
                    endMs = ((w["end"] as? Double ?: 0.0) * 1000).toLong(),
                    confidence = (w["conf"] as? Double ?: 1.0).toFloat().coerceIn(0f, 1f),
                )
            }
            if (words.isNotEmpty()) return words
        }
        return plainWords(root["text"] as? String)
    }

    /** The words being heard right now (`{"partial":".."}`), shown live and replaced by the final result. */
    fun parsePartial(json: String): String {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return ""
        return (root["partial"] as? String).orEmpty().trim()
    }

    private fun plainWords(text: String?): List<VoiceWord> =
        text.orEmpty().split(' ').map { it.trim() }.filter { it.isNotEmpty() && it != "<unk>" && it != "[unk]" }
            .map { VoiceWord(it, 0L, 0L) }

    /** "bonjour ça va" → "Bonjour ça va." ; "t' es où" → "T'es où." (Vosk splits elisions in some models). */
    fun sentence(words: List<String>): String {
        if (words.isEmpty()) return ""
        val joined = buildString {
            words.forEach { word ->
                if (isNotEmpty() && !endsWith("'") && !endsWith("’") && !word.startsWith("-")) append(' ')
                append(word)
            }
        }.replace(Regex("\\s+"), " ").trim()
        if (joined.isEmpty()) return ""
        val capital = joined.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
        return if (capital.last() in ".!?…") capital else "$capital."
    }

    /** The whole transcript, one paragraph; a pause longer than [PARAGRAPH_GAP_MS] starts a new one. */
    fun paragraphs(sentences: List<VoiceSentence>): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var lastEnd = -1L
        sentences.filter { it.words.isNotEmpty() }.forEach { s ->
            val timed = s.startMs > 0 || s.endMs > 0
            if (current.isNotEmpty() && timed && lastEnd >= 0 && s.startMs - lastEnd > PARAGRAPH_GAP_MS) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(s.text)
            lastEnd = s.endMs
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    fun plainText(sentences: List<VoiceSentence>): String = paragraphs(sentences).joinToString("\n\n")

    fun wordCount(sentences: List<VoiceSentence>): Int = sentences.sumOf { it.words.size }

    /** Share of the words the model was unsure of, 0..1 (0 for an empty transcript). */
    fun unsureShare(sentences: List<VoiceSentence>): Float {
        val words = sentences.flatMap { it.words }
        if (words.isEmpty()) return 0f
        return words.count { it.unsure }.toFloat() / words.size
    }

    /** "1:05", "0:07", "1:02:03". */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 500) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** "12 mots", "1 mot". */
    fun words(count: Int): String = if (count <= 1) "$count mot" else "$count mots"

    /** How much faster than the sound it went: "× 3,4" (null when too short to say). */
    fun speed(soundMs: Long, tookMs: Long): String? {
        if (soundMs < 1_000 || tookMs <= 0) return null
        val x = soundMs.toDouble() / tookMs
        return "× " + String.format(Locale.FRENCH, "%.1f", x)
    }

    /** Name of the saved text: "PTT-20261011-WA0003.opus" → "PTT-20261011-WA0003 (texte).txt". */
    fun outputName(sourceName: String): String {
        val base = SafeNames.baseOf(sourceName).ifBlank { "vocal" }
        return SafeNames.withExtension("$base (texte).txt", "txt")
    }

    /** Text of the saved .txt: a short header (file, length) and the transcript. */
    fun fileText(sourceName: String, durationMs: Long, sentences: List<VoiceSentence>): String = buildString {
        append("Vocal : ").append(sourceName).append(" (").append(clock(durationMs)).append(")\n")
        append("Transcrit hors ligne par Gulp. Relis-le : la reconnaissance peut se tromper.\n\n")
        append(plainText(sentences)).append('\n')
    }

    /** Pause between two stretches of speech that starts a new paragraph. */
    const val PARAGRAPH_GAP_MS = 2_500L
}

/** Just enough JSON for Vosk's results: objects, arrays, strings (with escapes), numbers, true/false/null. */
internal object MiniJson {
    fun parse(text: String): Any? = runCatching { Parser(text).run { val v = value(); v } }.getOrNull()

    private class Parser(val s: String) {
        var i = 0

        fun value(): Any? {
            skip()
            if (i >= s.length) throw IllegalArgumentException("end")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skip()
            if (peek() == '}') { i++; return map }
            while (true) {
                skip()
                val key = str()
                skip()
                expect(':')
                map[key] = value()
                skip()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("object")
                }
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skip()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += value()
                skip()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("array")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = s.getOrNull(i++) ?: throw IllegalArgumentException("string")
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = s.getOrNull(i++)) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            out.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        null -> throw IllegalArgumentException("escape")
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw IllegalArgumentException(w)
            i += w.length
            return v
        }

        private fun expect(c: Char) {
            if (s.getOrNull(i) != c) throw IllegalArgumentException("expected $c")
            i++
        }

        private fun peek(): Char? = s.getOrNull(i)

        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
