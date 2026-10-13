package com.powermaxv2.ytdownloader.core.reader

import com.powermaxv2.ytdownloader.core.chat.ChatMessage
import com.powermaxv2.ytdownloader.core.chat.Kind
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Lecteur de discussion: a WhatsApp export read back as the chat it was, in bubbles, one day after the other,
// with a search and a jump to any month. This part only lays the messages out (pure Kotlin, tested on the JVM);
// the screen draws the rows and ChatPdf prints them.

/** One line of the reader: a day's header, or a message. */
sealed interface ReaderRow {
    /** Stable key for the list. */
    val key: String

    data class Day(val date: LocalDate, val label: String) : ReaderRow {
        override val key: String get() = "d$date"
    }

    /**
     * Message number [index] of the discussion. [mine] = written by the person picked as "moi" (right side).
     * [showAuthor] = first of a run by the same person in a group (the name is written above the bubble).
     * [last] = last of a run: the bubble gets its little tail.
     */
    data class Message(
        val index: Int,
        val message: ChatMessage,
        val mine: Boolean,
        val showAuthor: Boolean,
        val last: Boolean,
        val time: String,
    ) : ReaderRow {
        override val key: String get() = "m$index"
    }
}

/** A month that can be jumped to, and the first message written in it. */
data class ReaderMonth(val month: YearMonth, val label: String, val firstIndex: Int)

object ChatReader {
    private val FRENCH: Locale = Locale.FRENCH
    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FRENCH)
    private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", FRENCH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", FRENCH)

    /** Two messages by the same person less than this apart share their run (one name, one tail). */
    private const val RUN_SECONDS = 10 * 60

    /** The people who wrote, the most talkative first (same count: alphabetical). */
    fun authors(messages: List<ChatMessage>): List<String> = messages
        .groupingBy { it.author }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

    private val MINE = Regex("^(vous avez supprimé ce message|you deleted this message|eliminaste este mensaje)", RegexOption.IGNORE_CASE)

    /**
     * Who exported the discussion, when the export tells: WhatsApp writes "Vous avez supprimé ce message" only for
     * the phone's owner. Else the most talkative person, so that the right side is never empty.
     */
    fun guessMe(messages: List<ChatMessage>): String? =
        messages.firstOrNull { it.kind == Kind.DELETED && MINE.containsMatchIn(it.text) }?.author
            ?: authors(messages).firstOrNull()

    fun date(message: ChatMessage): LocalDate = LocalDateTime.ofEpochSecond(message.clock, 0, ZoneOffset.UTC).toLocalDate()

    /** "18:02" as the export wrote it. */
    fun time(message: ChatMessage): String {
        val t = LocalDateTime.ofEpochSecond(message.clock, 0, ZoneOffset.UTC)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /** "Mardi 15 septembre 2026". */
    fun dayLabel(date: LocalDate): String = DAY.format(date).replaceFirstChar { it.titlecase(FRENCH) }

    /** "15 sept. 2026". */
    fun shortDate(date: LocalDate): String = SHORT.format(date)

    /** The rows of the discussion, oldest first: a header at each new day, then its messages. */
    fun rows(messages: List<ChatMessage>, me: String?, group: Boolean = authors(messages).size > 2): List<ReaderRow> {
        val rows = ArrayList<ReaderRow>(messages.size + messages.size / 8)
        var day: LocalDate? = null
        messages.forEachIndexed { i, m ->
            val d = date(m)
            val newDay = d != day
            if (newDay) {
                rows.add(ReaderRow.Day(d, dayLabel(d)))
                day = d
            }
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)
            val sameAsPrev = !newDay && prev != null && prev.author == m.author && m.time - prev.time < RUN_SECONDS
            val sameAsNext = next != null && next.author == m.author && date(next) == d && next.time - m.time < RUN_SECONDS
            val mine = m.author == me
            rows.add(
                ReaderRow.Message(
                    index = i,
                    message = m,
                    mine = mine,
                    showAuthor = group && !mine && !sameAsPrev,
                    last = !sameAsNext,
                    time = time(m),
                ),
            )
        }
        return rows
    }

    /** The row showing message number [index] (0 when it is not there). */
    fun rowOf(rows: List<ReaderRow>, index: Int): Int =
        rows.indexOfFirst { it is ReaderRow.Message && it.index == index }.coerceAtLeast(0)

    /** Each month of the discussion, with its first message. */
    fun months(messages: List<ChatMessage>): List<ReaderMonth> {
        val out = ArrayList<ReaderMonth>()
        messages.forEachIndexed { i, m ->
            val month = YearMonth.from(date(m))
            if (out.lastOrNull()?.month != month) out.add(ReaderMonth(month, MONTH.format(month), i))
        }
        return out
    }

    /** Shortest search: one letter would light up the whole discussion. */
    const val MIN_QUERY = 2

    /** Messages (their index) whose text or author holds [query], accents and case ignored; oldest first. */
    fun search(messages: List<ChatMessage>, query: String): List<Int> {
        val q = fold(query).trim()
        if (q.length < MIN_QUERY) return emptyList()
        return messages.indices.filter { i ->
            val m = messages[i]
            q in fold(m.text) || q in fold(m.author) || (m.file != null && q in fold(m.file))
        }
    }

    /** Where [query] is in [text] (start, end), accents and case ignored, for the highlight. */
    fun matches(text: String, query: String): List<IntRange> {
        val q = fold(query).trim()
        if (q.length < MIN_QUERY) return emptyList()
        // fold() keeps one character per character (accents dropped, not the letters), so offsets line up.
        val t = fold(text)
        if (t.length != text.length) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val at = t.indexOf(q, from)
            if (at < 0) break
            out.add(at until at + q.length)
            from = at + q.length
        }
        return out
    }

    /** Lower case without accents, one character per character ("Élodie" → "elodie"). */
    internal fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() ?: ch
            sb.append(if (base.isSurrogate()) ch else base.lowercaseChar())
        }
        return sb.toString()
    }

    /** "1 234 messages · 3 personnes · du 15 sept. 2026 au 6 oct. 2026". */
    fun summary(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return "Aucun message"
        val people = authors(messages).size
        val first = shortDate(date(messages.first()))
        val last = shortDate(date(messages.last()))
        val count = "%,d".format(FRENCH, messages.size).replace('\u202F', ' ').replace('\u00A0', ' ')
        val span = if (first == last) "le $first" else "du $first au $last"
        return "$count message${if (messages.size > 1) "s" else ""} · $people personne${if (people > 1) "s" else ""} · $span"
    }

    /** What WhatsApp put instead of a text, said for people ("📷 Photo", "🎤 Vocal"…); null for a written message. */
    fun mediaLabel(kind: Kind): String? = when (kind) {
        Kind.TEXT -> null
        Kind.PHOTO -> "📷 Photo"
        Kind.VIDEO -> "🎬 Vidéo"
        Kind.VOICE -> "🎤 Message vocal"
        Kind.AUDIO -> "🎵 Audio"
        Kind.STICKER -> "🏷️ Sticker"
        Kind.GIF -> "🎞️ GIF"
        Kind.DOCUMENT -> "📄 Document"
        Kind.CONTACT -> "👤 Contact"
        Kind.LOCATION -> "📍 Position"
        Kind.POLL -> "📊 Sondage"
        Kind.CALL -> "📞 Appel"
        Kind.MEDIA -> "📎 Média non inclus"
        Kind.DELETED -> "🚫 Message supprimé"
    }

    /** The text a message shows under its media label (the file's line is dropped, a caption is kept). */
    fun body(message: ChatMessage): String {
        val text = message.text
        return when (message.kind) {
            Kind.TEXT, Kind.POLL, Kind.LOCATION, Kind.CALL -> text
            Kind.DELETED, Kind.MEDIA -> ""
            else -> {
                // "IMG-….jpg (fichier joint)\nLa légende" → "La légende".
                text.lines().drop(1).joinToString("\n").trim()
            }
        }
    }
}
