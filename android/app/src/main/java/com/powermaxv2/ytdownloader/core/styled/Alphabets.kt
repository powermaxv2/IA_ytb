package com.powermaxv2.ytdownloader.core.styled

import java.text.BreakIterator
import java.text.Normalizer

/**
 * Unicode look-alike alphabets. Only plain letters and digits have look-alikes: an accented letter stays as typed
 * ("Soirée" → "𝐒𝐨𝐢𝐫é𝐞"), because an accent glued on a look-alike letter lands beside it on most phones. Anything without
 * a look-alike (emoji, punctuation, other scripts) stays as is.
 */
internal object Alphabets {
    /** Look-alike of an ASCII letter or digit, or null to keep it. */
    fun interface Alphabet {
        fun of(c: Char): String?
    }

    private fun cp(code: Int) = String(Character.toChars(code))

    /** Letters and digits laid out in order from [upper], [lower] and [digits]; [holes] are the letters Unicode put elsewhere. */
    private fun block(upper: Int?, lower: Int?, digits: Int? = null, holes: Map<Char, Int> = emptyMap()) = Alphabet { c ->
        holes[c]?.let { return@Alphabet cp(it) }
        when (c) {
            in 'A'..'Z' -> upper?.let { cp(it + (c - 'A')) }
            in 'a'..'z' -> lower?.let { cp(it + (c - 'a')) }
            in '0'..'9' -> digits?.let { cp(it + (c - '0')) }
            else -> null
        }
    }

    private fun table(map: Map<Char, String>) = Alphabet { map[it] }

    val SERIF_BOLD = block(0x1D400, 0x1D41A, 0x1D7CE)
    val SERIF_ITALIC = block(0x1D434, 0x1D44E, holes = mapOf('h' to 0x210E))
    val SANS_BOLD = block(0x1D5D4, 0x1D5EE, 0x1D7EC)
    val SCRIPT = block(
        0x1D49C,
        0x1D4B6,
        holes = mapOf(
            'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112, 'M' to 0x2133,
            'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134,
        ),
    )
    val SCRIPT_BOLD = block(0x1D4D0, 0x1D4EA)
    val GOTHIC = block(
        0x1D504,
        0x1D51E,
        holes = mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128),
    )
    val GOTHIC_BOLD = block(0x1D56C, 0x1D586)
    val DOUBLE = block(
        0x1D538,
        0x1D552,
        0x1D7D8,
        holes = mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124),
    )
    val TYPEWRITER = block(0x1D670, 0x1D68A, 0x1D7F6)

    /** Ⓐⓑ①: 0 is apart from 1–9. */
    val BUBBLES = Alphabet { c ->
        when (c) {
            in 'A'..'Z' -> cp(0x24B6 + (c - 'A'))
            in 'a'..'z' -> cp(0x24D0 + (c - 'a'))
            '0' -> cp(0x24EA)
            in '1'..'9' -> cp(0x2460 + (c - '1'))
            else -> null
        }
    }

    /** 🅐🅑❶: capitals only, so lower case letters take them too. */
    val BUBBLES_DARK = Alphabet { c ->
        when (c) {
            in 'A'..'Z' -> cp(0x1F150 + (c - 'A'))
            in 'a'..'z' -> cp(0x1F150 + (c - 'a'))
            '0' -> cp(0x24FF)
            in '1'..'9' -> cp(0x2776 + (c - '1'))
            else -> null
        }
    }

    val SQUARES = Alphabet { c ->
        when (c) {
            in 'A'..'Z' -> cp(0x1F130 + (c - 'A'))
            in 'a'..'z' -> cp(0x1F130 + (c - 'a'))
            else -> null
        }
    }

    val SQUARES_DARK = Alphabet { c ->
        when (c) {
            in 'A'..'Z' -> cp(0x1F170 + (c - 'A'))
            in 'a'..'z' -> cp(0x1F170 + (c - 'a'))
            else -> null
        }
    }

    /** Lower case letters as small capitals; capitals stay, as in print. Unicode has no small capital X. */
    val SMALL_CAPS = table(
        "abcdefghijklmnopqrstuvwxyz".zip("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ").associate { (a, b) -> a to b.toString() },
    )

    /** [input] with every letter and digit [alphabet] knows swapped. */
    fun map(input: String, alphabet: Alphabet): String {
        // Composed first, so "e + ◌́" typed apart is one "é" and stays whole.
        val text = Normalizer.normalize(input, Normalizer.Form.NFC)
        return buildString {
            for (c in text) append(alphabet.of(c) ?: c)
        }
    }

    /** Ｆｕｌｌ ｗｉｄｔｈ: every printable ASCII character, and the space, have a wide twin. */
    fun wide(input: String): String {
        val text = Normalizer.normalize(input, Normalizer.Form.NFC)
        return buildString {
            for (c in text) {
                when (c) {
                    ' ' -> append('　')
                    in '!'..'~' -> append((c.code + 0xFEE0).toChar())
                    else -> append(c)
                }
            }
        }
    }

    private val FLIPPED: Map<Char, String> = buildMap {
        "abcdefghijklmnopqrstuvwxyz".toList().zip(listOf("ɐ", "q", "ɔ", "p", "ǝ", "ɟ", "ƃ", "ɥ", "ᴉ", "ɾ", "ʞ", "l", "ɯ", "u", "o", "d", "b", "ɹ", "s", "ʇ", "n", "ʌ", "ʍ", "x", "ʎ", "z"))
            .forEach { (a, b) -> put(a, b) }
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList().zip(listOf("∀", "ꓭ", "Ɔ", "ꓷ", "Ǝ", "Ⅎ", "⅁", "H", "I", "ſ", "ꓘ", "˥", "W", "N", "O", "Ԁ", "Ό", "ꓤ", "S", "ꓕ", "∩", "Λ", "M", "X", "⅄", "Z"))
            .forEach { (a, b) -> put(a, b) }
        "0123456789".toList().zip(listOf("0", "Ɩ", "ᄅ", "Ɛ", "ㄣ", "ϛ", "9", "ㄥ", "8", "6")).forEach { (a, b) -> put(a, b) }
        mapOf(
            '.' to "˙", ',' to "'", '\'' to ",", '"' to "„", '?' to "¿", '!' to "¡", '(' to ")", ')' to "(",
            '[' to "]", ']' to "[", '{' to "}", '}' to "{", '<' to ">", '>' to "<", '&' to "⅋", '_' to "‾",
        ).forEach { (a, b) -> put(a, b) }
    }

    /** Turned half a circle: lines in reverse order, each read backwards with its letters flipped. Accents are dropped. */
    fun upsideDown(input: String): String = input.lines().asReversed().joinToString("\n") { line ->
        val plain = Normalizer.normalize(line, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        graphemes(plain).asReversed().joinToString("") { g -> if (g.length == 1) FLIPPED[g[0]] ?: g else g }
    }

    /** What the user sees as one character each (an emoji with its skin tone stays whole when reversed). */
    private fun graphemes(text: String): List<String> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += text.substring(start, end)
            start = end
            end = it.next()
        }
        return out
    }
}
