package com.powermaxv2.ytdownloader.core.textcount

import java.util.Locale

/** A way to change the case of a text, with its [label] written in that case. */
enum class CaseKind(val label: String) {
    UPPER("MAJUSCULES"),
    LOWER("minuscules"),
    SENTENCE("Début de phrase"),
    TITLE("Chaque Mot"),
    ALTERNATE("aLtErNé"),
    SWAP("iNVERSÉ"),
}

/** Changes the case of a text; accents, emoji, spaces and line breaks stay where they were. */
object CaseChanger {
    private val fr = Locale.FRENCH

    fun apply(text: String, kind: CaseKind): String = when (kind) {
        CaseKind.UPPER -> text.uppercase(fr)
        CaseKind.LOWER -> text.lowercase(fr)
        CaseKind.SENTENCE -> sentence(text)
        CaseKind.TITLE -> title(text)
        CaseKind.ALTERNATE -> alternate(text)
        CaseKind.SWAP -> swap(text)
    }

    /** True when [kind] would leave [text] as it is (the chip of the current case is shown as picked). */
    fun isAlready(text: String, kind: CaseKind): Boolean = text.any { it.isLetter() } && apply(text, kind) == text

    /** "SALUT. ÇA VA ?" → "Salut. Ça va ?": lower case, capital after . ! ? … and at each line start. */
    private fun sentence(text: String): String {
        val out = StringBuilder(text.length)
        var capital = true
        for (c in text.lowercase(fr)) {
            if (capital && c.isLetter()) {
                out.append(c.titlecase(fr))
                capital = false
                continue
            }
            out.append(c)
            if (c in ".!?…\n") capital = true else if (c.isLetterOrDigit()) capital = false
        }
        return out.toString()
    }

    /** "jean-pierre a l'été" → "Jean-Pierre A L'été": capital at the start of each word and after a hyphen. */
    private fun title(text: String): String {
        val out = StringBuilder(text.length)
        var previous: Char? = null
        for (c in text.lowercase(fr)) {
            val starts = previous == null || previous.isWhitespace() || previous == '-' || previous in "(«\"“/"
            out.append(if (starts && c.isLetter()) c.titlecase(fr) else c.toString())
            previous = c
        }
        return out.toString()
    }

    /** "bonjour toi" → "bOnJoUr tOi": letters only alternate, spaces and signs don't count. */
    private fun alternate(text: String): String {
        val out = StringBuilder(text.length)
        var upper = false
        for (c in text) {
            if (c.isLetter()) {
                out.append(if (upper) c.uppercase(fr) else c.lowercase(fr))
                upper = !upper
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    private fun swap(text: String): String = buildString(text.length) {
        for (c in text) append(if (c.isUpperCase()) c.lowercase(fr) else if (c.isLowerCase()) c.uppercase(fr) else c.toString())
    }
}
