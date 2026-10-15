package com.powermaxv2.ytdownloader.core.textcount

import java.util.Locale
import kotlin.math.ceil

// The Compteur de texte's counting, pure (no Android): characters as the eye sees them (an emoji = 1), words
// ("aujourd'hui" and "Jean-Pierre" = 1), sentences, paragraphs, reading and speaking times, the most used words,
// and how the text fits the usual limits (SMS, X, Instagram bio…).

/** What a text holds. */
data class TextStats(
    /** Characters as the eye sees them: "é", "👍🏽" or "🇫🇷" count 1. */
    val characters: Int,
    val charactersNoSpaces: Int,
    val words: Int,
    val sentences: Int,
    val paragraphs: Int,
    /** Lines, blank ones included (0 for an empty text). */
    val lines: Int,
    val readingSeconds: Int,
    val speakingSeconds: Int,
    /** Longest word, in its original case; empty without words. */
    val longestWord: String,
    /** The most used words (stop words left out), most used first, each used at least twice. */
    val topWords: List<WordCount>,
) {
    val isEmpty: Boolean get() = characters == 0
}

data class WordCount(val word: String, val count: Int)

/** A platform limit the text is checked against. */
data class TextLimit(val label: String, val max: Int)

/** How the text fits one [limit]: [used] of [TextLimit.max] (SMS: in the units of its encoding). */
data class LimitFit(val limit: TextLimit, val used: Int) {
    val left: Int get() = limit.max - used
    val over: Boolean get() = used > limit.max
    val fraction: Float get() = if (limit.max == 0) 0f else used.toFloat() / limit.max
}

/** How many SMS the text takes: GSM alphabet (160 / 153 per part) or Unicode as soon as one emoji is in (70 / 67). */
data class SmsFit(val parts: Int, val unicode: Boolean, val units: Int, val perPart: Int) {
    /** Characters left in the last part before a new one starts. */
    val leftInPart: Int get() = if (parts == 0) perPart else parts * perPart - units
}

object TextCounter {
    /** Longest text the tool takes (a long article): counting stays instant. */
    const val MAX_LENGTH = 50_000

    /** Average silent reading speed of an adult in French, words per minute. */
    const val READING_WPM = 230

    /** Speaking speed for a speech or a voice note, words per minute. */
    const val SPEAKING_WPM = 150

    const val SAMPLE = "Salut ! On se retrouve samedi chez Léa vers 20 h ? Apporte tes jeux, j'apporte le dessert 🎂. Ce sera génial."

    val limits: List<TextLimit> = listOf(
        TextLimit("Post X", 280),
        TextLimit("Bio Instagram", 150),
        TextLimit("Titre YouTube", 100),
        TextLimit("Statut WhatsApp", 700),
    )

    fun stats(text: String): TextStats {
        if (text.isEmpty()) return TextStats(0, 0, 0, 0, 0, 0, 0, 0, "", emptyList())
        val clusters = graphemes(text)
        val words = words(text)
        return TextStats(
            characters = clusters.size,
            charactersNoSpaces = clusters.count { it.isNotBlank() },
            words = words.size,
            sentences = sentences(text),
            paragraphs = paragraphs(text),
            lines = text.split('\n').size,
            readingSeconds = seconds(words.size, READING_WPM),
            speakingSeconds = seconds(words.size, SPEAKING_WPM),
            longestWord = words.maxByOrNull { graphemes(it).size }.orEmpty(),
            topWords = topWords(words),
        )
    }

    /** How [text] fits each of [limits], counted in visible characters. */
    fun fits(text: String): List<LimitFit> {
        val count = graphemes(text).size
        return limits.map { LimitFit(it, count) }
    }

    // MARK: - Characters

    /**
     * The text cut into what the eye sees as one character: a letter with its accents, an emoji with its skin tone,
     * a family "👨‍👩‍👧" glued by zero-width joiners, a flag made of two regional letters, "\r\n".
     */
    fun graphemes(text: String): List<String> {
        val out = ArrayList<String>(text.length)
        val current = StringBuilder()
        var previous = -1
        var regionalRun = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val joins = current.isNotEmpty() && (
                isExtending(cp) ||
                    previous == ZWJ ||
                    (previous == '\r'.code && cp == '\n'.code) ||
                    (isRegional(cp) && isRegional(previous) && regionalRun % 2 == 1)
                )
            if (!joins && current.isNotEmpty()) {
                out += current.toString()
                current.setLength(0)
            }
            regionalRun = if (isRegional(cp)) (if (joins) regionalRun + 1 else 1) else 0
            current.appendCodePoint(cp)
            previous = cp
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private const val ZWJ = 0x200D

    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** Code points that belong to the character before them. */
    private fun isExtending(cp: Int): Boolean {
        if (cp == ZWJ || cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F || cp == 0x20E3) return true
        return when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }

    // MARK: - Words, sentences, paragraphs

    /** The words of [text]: letters and digits, glued through an apostrophe or a hyphen ("aujourd'hui", "Jean-Pierre", "3,5"). */
    fun words(text: String): List<String> {
        val out = ArrayList<String>()
        var start = -1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (isWordChar(c)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                val glue = c in GLUE && i + 1 < text.length && isWordChar(text[i + 1]) &&
                    (c != ',' && c != '.' || (text[i - 1].isDigit() && text[i + 1].isDigit()))
                if (!glue) {
                    out += text.substring(start, i)
                    start = -1
                }
            }
            i++
        }
        if (start >= 0) out += text.substring(start)
        return out
    }

    private val GLUE = setOf('\'', '’', '-', '‑', ',', '.')

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || Character.getType(c).toByte().let {
        it == Character.NON_SPACING_MARK || it == Character.COMBINING_SPACING_MARK
    }

    /** Sentences: ends with . ! ? … (a run of them counts once; "3.5" does not end one), plus the last one if it has no end mark. */
    fun sentences(text: String): Int {
        var count = 0
        var wordsSince = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isLetterOrDigit()) {
                wordsSince = true
            } else if (c in ENDS && wordsSince) {
                val decimal = c == '.' && i > 0 && text[i - 1].isDigit() && i + 1 < text.length && text[i + 1].isDigit()
                if (!decimal) {
                    count++
                    wordsSince = false
                }
            } else if (c == '\n' && wordsSince && i + 1 < text.length && text[i + 1] == '\n') {
                // A paragraph without a final dot still ends its sentence.
                count++
                wordsSince = false
            }
            i++
        }
        if (wordsSince) count++
        return count
    }

    private val ENDS = setOf('.', '!', '?', '…', '‽')

    /** Blocks of text separated by at least one blank line. */
    fun paragraphs(text: String): Int = text.replace("\r\n", "\n").split(Regex("\\n[ \\t]*\\n")).count { it.isNotBlank() }

    private fun seconds(words: Int, wpm: Int): Int = if (words == 0) 0 else ceil(words * 60.0 / wpm).toInt()

    private fun topWords(words: List<String>, size: Int = 5): List<WordCount> = words
        .map { it.lowercase(Locale.FRENCH) }
        .filter { it.length >= 3 && it !in STOP_WORDS && !it.all(Char::isDigit) }
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= 2 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(size)
        .map { WordCount(it.key, it.value) }

    /** Small French (and a few English) words that tell nothing about the text. */
    private val STOP_WORDS = setOf(
        "les", "des", "une", "est", "sont", "pas", "pour", "par", "avec", "dans", "sur", "que", "qui", "quoi", "mais",
        "ou", "donc", "car", "nous", "vous", "ils", "elles", "elle", "lui", "leur", "leurs", "mon", "ton", "son", "mes",
        "tes", "ses", "nos", "vos", "ces", "cet", "cette", "aux", "été", "être", "avoir", "fait", "comme", "tout", "tous",
        "toute", "toutes", "plus", "moins", "très", "aussi", "bien", "encore", "alors", "quand", "même", "sans", "sous",
        "entre", "vers", "chez", "the", "and", "you", "for", "that", "this", "with", "are", "was", "c'est", "j'ai",
    )

    // MARK: - SMS

    /** How many SMS [text] takes. */
    fun sms(text: String): SmsFit {
        val unicode = text.any { it !in GSM_BASIC && it !in GSM_EXTENDED }
        val units = if (unicode) text.length else text.length + text.count { it in GSM_EXTENDED }
        val single = if (unicode) 70 else 160
        val multi = if (unicode) 67 else 153
        val parts = when {
            units == 0 -> 0
            units <= single -> 1
            else -> (units + multi - 1) / multi
        }
        return SmsFit(parts, unicode, units, if (parts > 1) multi else single)
    }

    private val GSM_BASIC: Set<Char> = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§" +
            "¿abcdefghijklmnopqrstuvwxyzäöñüà"
        ).toSet()
    private val GSM_EXTENDED: Set<Char> = "^{}\\[~]|€\u000C".toSet()

    // MARK: - Formatting

    /** 1234 → "1 234" (narrow no-break space, as in French). */
    fun format(n: Int): String = String.format(Locale.FRENCH, "%,d", n).replace(' ', ' ').replace(' ', ' ')

    /** 0 → "0 s", 45 → "45 s", 90 → "1 min 30 s", 120 → "2 min", 3_900 → "1 h 05". */
    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3_600 -> (seconds / 60).let { m -> (seconds % 60).let { s -> if (s == 0) "$m min" else "$m min $s s" } }
        else -> "${seconds / 3_600} h ${(seconds % 3_600 / 60).toString().padStart(2, '0')}"
    }

    /** "1 mot", "12 mots", "0 mot". */
    fun plural(n: Int, one: String, many: String = one + "s"): String = "${format(n)} ${if (n <= 1) one else many}"
}
