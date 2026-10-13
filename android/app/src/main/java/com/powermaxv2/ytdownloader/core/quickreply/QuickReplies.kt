package com.powermaxv2.ytdownloader.core.quickreply

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

// "Réponses rapides": messages written once ("J'arrive vers {heure+15}", "Merci {prénom} !") and sent again in one
// touch. {heure}, {date}, {jour} and {bonjour} are filled from the clock; any other {mot} is asked for before sending.
// Everything stays in the app's preferences. Pure Kotlin: tested on the JVM.

/** One ready-made message; [id] stays the same when it is edited or moved. */
data class QuickReply(val id: Long, val title: String, val text: String)

/** A {variable} of a message: filled by Gulp ([auto]) or asked to the user, both under the same folded [key]. */
data class QuickVar(
    /** As written between the braces ("Prénom"). */
    val name: String,
    /** Folded name ("prenom"): "{Prénom}" and "{prenom}" are the same variable. */
    val key: String,
    val auto: Boolean,
)

object QuickVars {
    /** "{prénom}", "{heure+15}": a name of 1 to 24 characters, on one line, without braces. */
    private val TOKEN = Regex("\\{([^{}\\n]{1,24})\\}")
    private val TIME_SHIFT = Regex("heure\\s*([+-])\\s*(\\d{1,3})")

    /** The variables Gulp fills itself, as the chips of the editor offer them. */
    val autos: List<String> = listOf("heure", "heure+15", "jour", "date", "bonjour")

    /** Variables the editor offers to ask for, besides the [autos]. */
    val suggested: List<String> = listOf("prénom", "lieu")

    /** The variables of [text], each once, in reading order. */
    fun of(text: String): List<QuickVar> = TOKEN.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .map { QuickVar(it, fold(it), isAuto(fold(it))) }
        .distinctBy { it.key }
        .toList()

    /** The variables of [text] the user has to fill. */
    fun asked(text: String): List<QuickVar> = of(text).filterNot { it.auto }

    /** The asked variables of [text] with no value yet in [values] (keys folded). */
    fun missing(text: String, values: Map<String, String>): List<QuickVar> = asked(text).filter { values[it.key].isNullOrBlank() }

    /**
     * [text] with its variables replaced: auto ones from [now], asked ones from [values] (folded keys). An asked
     * variable without a value is left as written, braces included, so nothing is silently lost.
     */
    fun fill(text: String, values: Map<String, String>, now: LocalDateTime): String = TOKEN.replace(text) { match ->
        val key = fold(match.groupValues[1].trim())
        autoValue(key, now) ?: values[key]?.trim()?.takeIf { it.isNotEmpty() } ?: match.value
    }

    /** Pieces of [text] for drawing it with its variables highlighted. */
    fun pieces(text: String, values: Map<String, String>, now: LocalDateTime): List<Piece> {
        val out = mutableListOf<Piece>()
        var last = 0
        for (match in TOKEN.findAll(text)) {
            if (match.range.first > last) out += Piece(text.substring(last, match.range.first), null)
            val key = fold(match.groupValues[1].trim())
            val auto = autoValue(key, now)
            val given = values[key]?.trim()?.takeIf { it.isNotEmpty() }
            out += Piece(auto ?: given ?: match.value, QuickVar(match.groupValues[1].trim(), key, auto != null), filled = auto != null || given != null)
            last = match.range.last + 1
        }
        if (last < text.length) out += Piece(text.substring(last), null)
        return out
    }

    /** A run of text; [variable] is set when it comes from a {variable}, [filled] when it got a value. */
    data class Piece(val text: String, val variable: QuickVar?, val filled: Boolean = false)

    private fun isAuto(key: String): Boolean = autoValue(key, LocalDateTime.of(2000, 1, 1, 12, 0)) != null

    /** The value of an auto variable at [now], or null when [key] is not one. */
    fun autoValue(key: String, now: LocalDateTime): String? = when (key) {
        "heure" -> hour(now)
        "jour" -> now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)
        "date" -> "${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)}"
        "bonjour" -> if (now.hour in 5..17) "Bonjour" else "Bonsoir"
        "demain" -> now.plusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)
        else -> TIME_SHIFT.matchEntire(key)?.let { m ->
            val minutes = m.groupValues[2].toLong()
            hour(if (m.groupValues[1] == "+") now.plusMinutes(minutes) else now.minusMinutes(minutes))
        }
    }

    /** 14:05 → "14h05", the way it is written in French messages. */
    private fun hour(time: LocalDateTime): String = "${time.hour}h" + time.minute.toString().padStart(2, '0')

    /** "Prénom " → "prenom": no accents, lower case, spaces trimmed. */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
}

object QuickReplies {
    const val MAX_COUNT = 60
    const val MAX_TEXT = 1_000
    const val MAX_TITLE = 40

    /** What the tool holds the first time it opens: a few messages people send every day, to change or delete. */
    val starters: List<QuickReply> = listOf(
        QuickReply(1, "En route", "J'arrive vers {heure+15} 🚗"),
        QuickReply(2, "Bien arrivé", "Bien arrivé ✅ ({heure})"),
        QuickReply(3, "Occupé", "Je ne peux pas répondre pour l'instant, je te rappelle dès que possible."),
        QuickReply(4, "Salut", "{bonjour} {prénom} ! 👋"),
        QuickReply(5, "Merci", "Merci beaucoup {prénom}, c'est adorable 🙏"),
        QuickReply(6, "On se voit ?", "On se retrouve à {lieu} ce {jour} ?"),
    )

    /** The title shown for [text] when the user gave none: its first words. */
    fun titleOf(title: String, text: String): String {
        title.trim().takeIf { it.isNotEmpty() }?.let { return it.take(MAX_TITLE) }
        val firstLine = text.trim().lineSequence().firstOrNull().orEmpty()
        return if (firstLine.length <= 24) firstLine else firstLine.take(24).substringBeforeLast(' ').ifBlank { firstLine.take(24) } + "…"
    }

    /** A new message on top of [list]; [list] unchanged when the text is blank or the list is full. */
    fun add(list: List<QuickReply>, title: String, text: String): List<QuickReply> {
        val body = text.trim().take(MAX_TEXT)
        if (body.isEmpty() || list.size >= MAX_COUNT) return list
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        return listOf(QuickReply(id, titleOf(title, body), body)) + list
    }

    /** [list] with message [id] rewritten, in place; a blank text leaves it as it was. */
    fun update(list: List<QuickReply>, id: Long, title: String, text: String): List<QuickReply> {
        val body = text.trim().take(MAX_TEXT)
        if (body.isEmpty()) return list
        return list.map { if (it.id == id) it.copy(title = titleOf(title, body), text = body) else it }
    }

    fun remove(list: List<QuickReply>, id: Long): List<QuickReply> = list.filterNot { it.id == id }

    /** [list] with message [id] moved [by] places (negative = up), stopped at both ends. */
    fun move(list: List<QuickReply>, id: Long, by: Int): List<QuickReply> {
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return list
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return list
        val out = list.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /** The messages whose title or text holds every word of [query]; accents and case ignored. */
    fun search(list: List<QuickReply>, query: String): List<QuickReply> {
        val words = QuickVars.fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return list
        return list.filter { reply ->
            val text = QuickVars.fold(reply.title + " " + reply.text)
            words.all { it in text }
        }
    }

    // MARK: - Saving: one message per line, "id \t title \t text", with \\ \t \n escaped.

    fun encode(list: List<QuickReply>): String = list.joinToString("\n") { "${it.id}\t${escape(it.title)}\t${escape(it.text)}" }

    /** [saved] read back; null (never saved) gives the [starters], damaged lines are skipped. */
    fun decode(saved: String?): List<QuickReply> {
        if (saved == null) return starters
        return saved.split('\n').mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val text = unescape(parts[2]).take(MAX_TEXT)
            if (text.isBlank()) null else QuickReply(id, titleOf(unescape(parts[1]), text), text)
        }.distinctBy { it.id }.take(MAX_COUNT)
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(text[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
