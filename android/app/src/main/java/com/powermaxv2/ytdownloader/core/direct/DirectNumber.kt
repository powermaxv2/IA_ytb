package com.powermaxv2.ytdownloader.core.direct

import java.net.URLEncoder

// "Écrire sans enregistrer": a phone number typed or pasted any way people write it ("06 12 34 56 78",
// "+33 (0)6.12.34.56.78", "0033612345678"), turned into the international number WhatsApp's own link wants
// (wa.me/33612345678). The chat then opens in WhatsApp without adding the number to the contacts.
// Pure Kotlin: tested on the JVM.

/** A country the number can be read in when it is written without its "+code". */
data class Country(
    /** ISO 3166 code ("FR"), as Locale gives it. */
    val iso: String,
    val name: String,
    /** International code without "+" ("33"). */
    val code: String,
    val flag: String,
    /** Digits of a national number once the trunk "0" is dropped (9 in France); null = not checked. */
    val nationalLength: IntRange? = null,
    /** The "0" in front of national numbers is dropped for the international form (not in Italy). */
    val trunkZero: Boolean = true,
    /** First digits of mobile numbers (national form, without the trunk 0); empty = not checked. */
    val mobile: List<String> = emptyList(),
)

object Countries {
    val all: List<Country> = listOf(
        Country("FR", "France", "33", "🇫🇷", 9..9, mobile = listOf("6", "7")),
        Country("BE", "Belgique", "32", "🇧🇪", 8..9, mobile = listOf("4")),
        Country("CH", "Suisse", "41", "🇨🇭", 9..9, mobile = listOf("7")),
        Country("LU", "Luxembourg", "352", "🇱🇺", 6..11, trunkZero = false),
        Country("CA", "Canada", "1", "🇨🇦", 10..10, trunkZero = false),
        Country("US", "États-Unis", "1", "🇺🇸", 10..10, trunkZero = false),
        Country("MA", "Maroc", "212", "🇲🇦", 9..9, mobile = listOf("6", "7")),
        Country("DZ", "Algérie", "213", "🇩🇿", 8..9, mobile = listOf("5", "6", "7")),
        Country("TN", "Tunisie", "216", "🇹🇳", 8..8, trunkZero = false),
        Country("SN", "Sénégal", "221", "🇸🇳", 9..9, trunkZero = false),
        Country("CI", "Côte d'Ivoire", "225", "🇨🇮", 10..10, trunkZero = false),
        Country("CM", "Cameroun", "237", "🇨🇲", 9..9, trunkZero = false),
        Country("ES", "Espagne", "34", "🇪🇸", 9..9, trunkZero = false),
        Country("PT", "Portugal", "351", "🇵🇹", 9..9, trunkZero = false),
        Country("IT", "Italie", "39", "🇮🇹", 6..11, trunkZero = false),
        Country("DE", "Allemagne", "49", "🇩🇪", 6..13),
        Country("GB", "Royaume-Uni", "44", "🇬🇧", 9..10, mobile = listOf("7")),
        Country("RE", "La Réunion", "262", "🇷🇪", 9..9),
        Country("GP", "Guadeloupe", "590", "🇬🇵", 9..9),
        Country("MQ", "Martinique", "596", "🇲🇶", 9..9),
        Country("GF", "Guyane", "594", "🇬🇫", 9..9),
    )

    val france: Country get() = all.first()

    fun byIso(iso: String?): Country? = all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }

    /** The phone's country when it is in the list, else France. */
    fun default(localeCountry: String?): Country = byIso(localeCountry) ?: france

    /** The country of an international number ("33612…" → France), the longest code first; null if unknown. */
    fun ofInternational(digits: String): Country? = all
        .filter { digits.startsWith(it.code) }
        .maxByOrNull { it.code.length }
}

/** What a typed number gives. */
sealed interface DirectNumber {
    /** Nothing typed yet. */
    data object Empty : DirectNumber

    /** Not a usable number; [reason] is shown under the field. */
    data class Bad(val reason: String) : DirectNumber

    /**
     * A number WhatsApp can open: [digits] is the international form without "+" ("33612345678"),
     * [pretty] the same number written for people ("+33 6 12 34 56 78"), [country] when its code is known.
     * [maybeLandline] = it looks like a fixed line, which usually has no WhatsApp.
     */
    data class Ok(val digits: String, val pretty: String, val country: Country?, val maybeLandline: Boolean = false) : DirectNumber
}

object DirectNumbers {
    /** WhatsApp numbers are E.164: at most 15 digits, and none is shorter than 8 with its code. */
    private const val MIN_DIGITS = 8
    private const val MAX_DIGITS = 15

    /** Longest message put in the link (WhatsApp's own limit is much higher; this keeps the link sane). */
    const val MAX_MESSAGE = 1_000

    /** "+33 (0)6 12" → the "(0)" people write between the code and the number is not dialled. */
    private val TRUNK_IN_BRACKETS = Regex("\\(\\s*0\\s*\\)")
    private val LETTERS = Regex("[A-Za-z]")

    /**
     * Reads [input] as a number; a number written without "+" or "00" is read in [country].
     * Spaces, dots, dashes, brackets and slashes are ignored; letters make it invalid.
     */
    fun parse(input: String, country: Country): DirectNumber {
        val raw = input.trim().removePrefix("tel:").removePrefix("TEL:").replace(TRUNK_IN_BRACKETS, "")
        if (raw.isBlank()) return DirectNumber.Empty
        if (LETTERS.containsMatchIn(raw)) return DirectNumber.Bad("Un numéro ne contient que des chiffres.")
        val plus = raw.trimStart().startsWith("+")
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return DirectNumber.Bad("Écris un numéro de téléphone.")
        val international = when {
            plus -> digits
            digits.startsWith("00") -> digits.drop(2)
            else -> national(digits, country) ?: return DirectNumber.Bad(lengthHint(country))
        }
        if (international.startsWith("0")) return DirectNumber.Bad("Après l'indicatif, le numéro ne commence pas par 0.")
        if (international.length < MIN_DIGITS) return DirectNumber.Bad("Il manque des chiffres.")
        if (international.length > MAX_DIGITS) return DirectNumber.Bad("Il y a trop de chiffres.")
        val known = Countries.ofInternational(international)
        if (known != null) {
            val rest = international.removePrefix(known.code)
            val length = known.nationalLength
            // A national number with its trunk 0 kept after the code ("+33 06…") is the most common slip.
            if (rest.startsWith("0") && known.trunkZero) return DirectNumber.Bad("Avec +${known.code}, enlève le 0 du début du numéro.")
            if (length != null && rest.length !in length && (known.code == country.code || plus || digits.startsWith("00"))) {
                return DirectNumber.Bad(lengthHint(known))
            }
        }
        return DirectNumber.Ok(international, pretty(international), known, maybeLandline(international, known))
    }

    /** National digits ("0612345678") → international ("33612345678") in [country], or null when the length is wrong. */
    private fun national(digits: String, country: Country): String? {
        if (country.code == "1") {
            // North America: "1 514 555 0199" or "514 555 0199".
            val rest = if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
            return if (rest.length == 10) "1$rest" else null
        }
        val rest = if (country.trunkZero && digits.startsWith("0")) digits.drop(1) else digits
        if (country.trunkZero && rest.startsWith("0")) return null
        country.nationalLength?.let { if (rest.length !in it) return null }
        return country.code + rest
    }

    private fun lengthHint(country: Country): String = when (country.iso) {
        "FR" -> "Un numéro français a 10 chiffres, par exemple 06 12 34 56 78."
        "BE" -> "Un numéro belge a 9 ou 10 chiffres, par exemple 0470 12 34 56."
        "CH" -> "Un numéro suisse a 10 chiffres, par exemple 079 123 45 67."
        else -> "Ce numéro n'a pas le bon nombre de chiffres pour ${country.name}. Ajoute +indicatif s'il est d'un autre pays."
    }

    private fun maybeLandline(digits: String, country: Country?): Boolean {
        if (country == null || country.mobile.isEmpty()) return false
        val rest = digits.removePrefix(country.code)
        return country.mobile.none { rest.startsWith(it) }
    }

    /** "33612345678" → "+33 6 12 34 56 78"; other countries in groups of 2 or 3 after their code. */
    fun pretty(digits: String): String {
        val country = Countries.ofInternational(digits) ?: return "+$digits"
        val rest = digits.removePrefix(country.code)
        val groups = when {
            country.code == "1" && rest.length == 10 -> listOf(rest.take(3), rest.substring(3, 6), rest.drop(6))
            country.nationalLength == 9..9 && country.trunkZero -> listOf(rest.take(1)) + rest.drop(1).chunked(2)
            rest.length % 3 == 1 && rest.length > 4 -> rest.dropLast(4).chunked(3) + rest.takeLast(4)
            else -> rest.chunked(3)
        }
        return "+${country.code} " + groups.joinToString(" ")
    }

    /** Numbers in a pasted text (an SMS, a signature, an ad): "+", "00" or a digit, then digits and separators. */
    private val CANDIDATE = Regex("(?:\\+|\\b)\\d[\\d .\\-()/\\u00A0\\u202F]{5,22}\\d")

    /** The first thing in [text] that reads as a valid number in [country], as it was written; null if none. */
    fun find(text: String, country: Country): String? = CANDIDATE.findAll(text)
        .map { it.value.trim() }
        .firstOrNull { parse(it, country) is DirectNumber.Ok }

    /** WhatsApp's own "click to chat" link, with the [message] typed in advance when there is one. */
    fun link(digits: String, message: String = ""): String {
        val text = message.trim().take(MAX_MESSAGE)
        if (text.isEmpty()) return "https://wa.me/$digits"
        // URLEncoder writes spaces as "+", which WhatsApp would show as "+": use %20.
        return "https://wa.me/$digits?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}

/** The last numbers opened, newest first, saved as one line each. */
object DirectRecents {
    const val MAX = 8

    fun add(recents: List<String>, digits: String): List<String> = (listOf(digits) + recents.filter { it != digits }).take(MAX)

    fun remove(recents: List<String>, digits: String): List<String> = recents.filter { it != digits }

    fun encode(recents: List<String>): String = recents.joinToString("\n")

    /** Only what still reads as a number is kept (a damaged pref gives an empty list, not a crash). */
    fun decode(saved: String?): List<String> = saved.orEmpty()
        .split('\n')
        .map { it.trim() }
        .filter { it.length in 8..15 && it.all(Char::isDigit) }
        .distinct()
        .take(MAX)
}
