package com.powermaxv2.ytdownloader.core.password

import java.security.SecureRandom
import java.util.Random
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

// The Générateur de mots de passe, without Android: random passwords, passphrases of French words and PIN codes drawn
// from a SecureRandom, their strength in bits, and an estimate for a password the user types in. Pure: tests run on
// the JVM with a seeded Random. Nothing is saved or sent: the screen keeps the last few in memory only.

/** What the tool makes. */
enum class PasswordMode(val label: String) {
    RANDOM("Solide"),
    PASSPHRASE("Facile à retenir"),
    PIN("Code PIN"),
    CHECK("Tester le mien"),
}

/** Between the words of a passphrase. */
enum class Separator(val label: String, val text: String) {
    DASH("-", "-"),
    DOT(".", "."),
    UNDERSCORE("_", "_"),
    SPACE("Espace", " "),
}

/** Every setting of the three generators (the ones of the other modes are kept when the user switches). */
data class PasswordOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** No I, l, 1, O, 0, o: nothing to misread when it is copied by hand. */
    val avoidAmbiguous: Boolean = false,
    val words: Int = 5,
    val separator: Separator = Separator.DASH,
    val capitalize: Boolean = true,
    /** A number from 0 to 99 glued to one of the words (sites that want a digit). */
    val addNumber: Boolean = true,
    val pinLength: Int = 6,
) {
    /** Same options with every number brought back into its range and at least one kind of character on. */
    fun normalized(): PasswordOptions {
        val any = lower || upper || digits || symbols
        return copy(
            length = length.coerceIn(Passwords.MIN_LENGTH, Passwords.MAX_LENGTH),
            lower = lower || !any,
            words = words.coerceIn(Passwords.MIN_WORDS, Passwords.MAX_WORDS),
            pinLength = pinLength.coerceIn(Passwords.MIN_PIN, Passwords.MAX_PIN),
        )
    }
}

/** A password made by the tool, and how many bits of chance went into it. */
data class GeneratedPassword(val text: String, val bits: Double, val mode: PasswordMode)

/** The five steps of the gauge. */
enum class Strength(val label: String, val segments: Int) {
    VERY_WEAK("Très faible", 1),
    WEAK("Faible", 2),
    FAIR("Moyen", 3),
    STRONG("Fort", 4),
    VERY_STRONG("Très fort", 5),
    ;

    companion object {
        /** Thresholds in bits: under 28 a computer finds it at once, from 80 not before the sun goes out. */
        fun of(bits: Double): Strength = when {
            bits < 28 -> VERY_WEAK
            bits < 40 -> WEAK
            bits < 60 -> FAIR
            bits < 80 -> STRONG
            else -> VERY_STRONG
        }
    }
}

/** What the gauge of a typed password shows: its bits, and what would make it better. */
data class Checked(val bits: Double, val tips: List<String>, val common: Boolean = false) {
    val strength: Strength get() = Strength.of(bits)
}

object Passwords {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val MIN_WORDS = 3
    const val MAX_WORDS = 8
    const val MIN_PIN = 4
    const val MAX_PIN = 12

    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"

    /** Symbols that every site takes and that type easily: no quotes, backslash or space. */
    const val SYMBOLS = "!#$%&*+-=?@^_~.,;:"
    const val AMBIGUOUS = "Il1O0o"

    /** Guesses per second of an attacker with graphics cards against a fast hash (the pessimistic case). */
    const val GUESSES_PER_SECOND = 1e10

    val wordCount: Int get() = WordList.words.size

    /** The generator of the app. One per process is enough; SecureRandom is thread safe. */
    val secure: Random by lazy { SecureRandom() }

    /** A new password for [mode] ([PasswordMode.CHECK] makes nothing: random mode then). */
    fun generate(mode: PasswordMode, options: PasswordOptions, random: Random = secure): GeneratedPassword {
        val o = options.normalized()
        return when (mode) {
            PasswordMode.PASSPHRASE -> passphrase(o, random)
            PasswordMode.PIN -> pin(o.pinLength, random)
            else -> randomPassword(o, random)
        }
    }

    /** The kinds of characters [options] asks for, ambiguous ones removed if asked. */
    fun classes(options: PasswordOptions): List<String> {
        val o = options.normalized()
        return buildList {
            if (o.lower) add(LOWER)
            if (o.upper) add(UPPER)
            if (o.digits) add(DIGITS)
            if (o.symbols) add(SYMBOLS)
        }.map { set -> if (o.avoidAmbiguous) set.filterNot { it in AMBIGUOUS } else set }
    }

    /** Characters drawn one by one from the chosen kinds, with at least one of each kind, then shuffled. */
    fun randomPassword(options: PasswordOptions, random: Random = secure): GeneratedPassword {
        val o = options.normalized()
        val classes = classes(o)
        val pool = classes.joinToString("")
        val chars = CharArray(o.length)
        // One of each kind first, so "chiffres" on always gives a digit; the rest from the whole pool.
        classes.forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        for (i in classes.size until o.length) chars[i] = pool[random.nextInt(pool.length)]
        shuffle(chars, random)
        return GeneratedPassword(String(chars), o.length * log2(pool.length.toDouble()), PasswordMode.RANDOM)
    }

    /** [PasswordOptions.words] words of the list, maybe capitalized, with a number glued to one of them. */
    fun passphrase(options: PasswordOptions, random: Random = secure): GeneratedPassword {
        val o = options.normalized()
        val list = WordList.words
        val words = MutableList(o.words) { list[random.nextInt(list.size)] }
        if (o.capitalize) words.replaceAll { w -> w.replaceFirstChar { it.uppercaseChar() } }
        var bits = o.words * log2(list.size.toDouble())
        if (o.addNumber) {
            val at = random.nextInt(words.size)
            words[at] = words[at] + random.nextInt(100)
            bits += log2(100.0) + log2(words.size.toDouble())
        }
        return GeneratedPassword(words.joinToString(o.separator.text), bits, PasswordMode.PASSPHRASE)
    }

    /** [length] digits, never all the same nor a straight run (1234, 9876): those are tried first. */
    fun pin(length: Int, random: Random = secure): GeneratedPassword {
        val n = length.coerceIn(MIN_PIN, MAX_PIN)
        var code: String
        do {
            code = buildString { repeat(n) { append(DIGITS[random.nextInt(10)]) } }
        } while (trivialPin(code))
        return GeneratedPassword(code, n * log2(10.0), PasswordMode.PIN)
    }

    fun trivialPin(code: String): Boolean {
        if (code.length < 2) return false
        val steps = code.zipWithNext { a, b -> b - a }.toSet()
        return steps.size == 1 && steps.first() in -1..1
    }

    private fun shuffle(chars: CharArray, random: Random) {
        for (i in chars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]
            chars[i] = chars[j]
            chars[j] = t
        }
    }

    // MARK: - Strength

    /** Rough strength of a password typed by the user: pool size × length, minus repeats, runs and known words. */
    fun check(password: String): Checked {
        if (password.isEmpty()) return Checked(0.0, emptyList())
        val folded = password.lowercase()
        val tips = mutableListOf<String>()
        if (folded in COMMON || folded.trimEnd { it.isDigit() || it in "!.?" } in COMMON) {
            return Checked(4.0, listOf("C'est l'un des mots de passe les plus utilisés : il est essayé en premier."), common = true)
        }
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { it in SYMBOLS || it in " \"'\\/()[]{}<>|`" }) pool += 33
        // Accents, emoji and other letters outside ASCII: guessers rarely try them.
        if (password.any { it.code > 127 }) pool += 60

        // Effective length: a character that repeats the one before, or continues a run (abc, 321), counts for little.
        var effective = 0.0
        password.forEachIndexed { i, c ->
            effective += when {
                i == 0 -> 1.0
                c == password[i - 1] -> 0.2
                abs(c - password[i - 1]) == 1 -> 0.35
                else -> 1.0
            }
        }
        var bits = effective * log2(max(pool, 2).toDouble())

        // A word of the list, or a known password, inside: the guesser tries those as one piece.
        val inside = (COMMON + KEYBOARD).filter { it.length >= 4 && it in folded }
        if (inside.isNotEmpty()) {
            val longest = inside.maxBy { it.length }
            bits -= longest.length * log2(max(pool, 2).toDouble()) - 10
            tips += "Évite « $longest » : il fait partie des listes que les pirates essaient."
        }
        bits = bits.coerceAtLeast(1.0)

        if (password.length < 12) tips += "Allonge-le : 12 caractères au moins, 16 c'est mieux."
        val kinds = listOf(password.any { it.isLowerCase() }, password.any { it.isUpperCase() }, password.any { it.isDigit() }, password.any { !it.isLetterOrDigit() }).count { it }
        if (kinds < 3) tips += "Mélange minuscules, majuscules, chiffres et symboles."
        if (effective < password.length - 1.5) tips += "Évite les répétitions et les suites (aaa, 1234, abcd)."
        if (Strength.of(bits) >= Strength.STRONG && tips.isEmpty()) tips += "Bien. Garde-le pour un seul site."
        return Checked(bits, tips.distinct())
    }

    /** How long a guesser needs on average for [bits] bits of chance, in French ("3 jours", "des siècles"). */
    fun crackTime(bits: Double): String {
        if (bits <= 0) return "instantané"
        // On average half of the tries: 2^(bits − 1).
        val seconds = 2.0.pow(bits - 1) / GUESSES_PER_SECOND
        val minute = 60.0
        val hour = 60 * minute
        val day = 24 * hour
        val year = 365.25 * day
        return when {
            seconds < 1 -> "instantané"
            seconds < minute -> plural(seconds, "seconde")
            seconds < hour -> plural(seconds / minute, "minute")
            seconds < day -> plural(seconds / hour, "heure")
            seconds < 31 * day -> plural(seconds / day, "jour")
            seconds < year -> "${(seconds / (30.4 * day)).toInt()} mois"
            seconds < 1_000 * year -> plural(seconds / year, "an")
            seconds < 1e6 * year -> "des milliers d'années"
            seconds < 1e9 * year -> "des millions d'années"
            else -> "des milliards d'années"
        }
    }

    /** Bits shown to the user, rounded down ("128 bits"). */
    fun bitsText(bits: Double): String = "${bits.toInt()} bits"

    private fun plural(value: Double, unit: String): String {
        val n = value.toLong().coerceAtLeast(1)
        return if (n > 1) "$n ${unit}s" else "1 $unit"
    }

    /** Most used passwords (French and worldwide lists), lowercase. */
    private val COMMON = setOf(
        "123456", "1234567", "12345678", "123456789", "1234567890", "000000", "111111", "123123", "654321",
        "password", "motdepasse", "azerty", "azertyuiop", "qwerty", "qwertyuiop", "soleil", "doudou", "loulou",
        "chouchou", "bonjour", "marseille", "nicolas", "camille", "jetaime", "iloveyou", "princesse",
        "football", "coucou", "chocolat", "admin", "welcome", "dragon", "monkey", "sunshine", "superman", "batman",
        "pokemon", "liverpool", "abc123", "letmein", "master", "secret", "toto", "test", "minecraft", "naruto",
        "chelsea", "arsenal", "psg", "paris", "france", "julien", "thomas", "marie", "nathalie", "anniversaire",
    )

    /** Keyboard rows people type as a "random" password. */
    private val KEYBOARD = setOf("azerty", "qwerty", "qsdfgh", "asdfgh", "wxcvbn", "zxcvbn", "uiop", "1234", "abcd")
}
