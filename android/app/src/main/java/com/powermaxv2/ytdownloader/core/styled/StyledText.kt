package com.powermaxv2.ytdownloader.core.styled

import java.text.Normalizer

// Texte stylé: the same text written several ways, ready to paste in WhatsApp.
// - WhatsApp styles are the app's own markers (*gras*, _italique_, ~barré~, ```mono```): WhatsApp draws them itself.
// - Fonts are other Unicode letters (𝐆𝐮𝐥𝐩, 𝔊𝔲𝔩𝔭, Ⓖⓤⓛⓟ…): they look styled in any app, WhatsApp or not.
// Everything is pure string work: no network, no Android.

/** One way of writing the text. */
enum class TextStyleKind(val label: String, val whatsApp: Boolean) {
    BOLD("Gras", true),
    ITALIC("Italique", true),
    STRIKE("Barré", true),
    MONO("Monospace", true),
    BOLD_ITALIC("Gras italique", true),
    QUOTE("Citation", true),

    SERIF_BOLD("Gras partout", false),
    SANS_BOLD("Gras moderne", false),
    SERIF_ITALIC("Italique partout", false),
    SCRIPT("Manuscrit", false),
    SCRIPT_BOLD("Manuscrit gras", false),
    GOTHIC("Gothique", false),
    GOTHIC_BOLD("Gothique gras", false),
    DOUBLE("Contour", false),
    TYPEWRITER("Machine à écrire", false),
    BUBBLES("Bulles", false),
    BUBBLES_DARK("Bulles noires", false),
    SQUARES("Carrés", false),
    SQUARES_DARK("Carrés noirs", false),
    WIDE("Large", false),
    SMALL_CAPS("Petites capitales", false),
    STRIKE_ALL("Barré partout", false),
    UNDERLINE("Souligné", false),
    UPSIDE_DOWN("À l'envers", false),
}

/** A variant ready to copy: what the user pastes ([text]) and what WhatsApp shows once sent ([shown]). */
data class StyledVariant(val kind: TextStyleKind, val text: String, val shown: ShownText)

/** How a variant looks in WhatsApp: the plain characters and the styling WhatsApp adds to them. */
data class ShownText(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val quote: Boolean = false,
)

object StyledText {
    /** Longest text the screen styles (WhatsApp itself stops at 65 536 characters). */
    const val MAX_LENGTH = 2_000

    /** Shown while the field is empty, so the screen is never blank. */
    const val SAMPLE = "Rendez-vous à 20h"

    val whatsAppKinds: List<TextStyleKind> = TextStyleKind.entries.filter { it.whatsApp }
    val fontKinds: List<TextStyleKind> = TextStyleKind.entries.filterNot { it.whatsApp }

    fun variants(input: String, kinds: List<TextStyleKind> = TextStyleKind.entries): List<StyledVariant> =
        kinds.map { variant(input, it) }

    fun variant(input: String, kind: TextStyleKind): StyledVariant {
        val text = apply(input, kind)
        val shown = when (kind) {
            TextStyleKind.BOLD -> ShownText(input, bold = true)
            TextStyleKind.ITALIC -> ShownText(input, italic = true)
            TextStyleKind.STRIKE -> ShownText(input, strike = true)
            TextStyleKind.MONO -> ShownText(input, mono = true)
            TextStyleKind.BOLD_ITALIC -> ShownText(input, bold = true, italic = true)
            TextStyleKind.QUOTE -> ShownText(input, quote = true)
            else -> ShownText(text)
        }
        return StyledVariant(kind, text, shown)
    }

    /** [input] written in the [kind] style. */
    fun apply(input: String, kind: TextStyleKind): String = when (kind) {
        TextStyleKind.BOLD -> markLines(input, "*", "*")
        TextStyleKind.ITALIC -> markLines(input, "_", "_")
        TextStyleKind.STRIKE -> markLines(input, "~", "~")
        TextStyleKind.MONO -> mono(input)
        TextStyleKind.BOLD_ITALIC -> markLines(input, "*_", "_*")
        TextStyleKind.QUOTE -> input.lines().joinToString("\n") { "> $it" }
        TextStyleKind.SERIF_BOLD -> Alphabets.map(input, Alphabets.SERIF_BOLD)
        TextStyleKind.SANS_BOLD -> Alphabets.map(input, Alphabets.SANS_BOLD)
        TextStyleKind.SERIF_ITALIC -> Alphabets.map(input, Alphabets.SERIF_ITALIC)
        TextStyleKind.SCRIPT -> Alphabets.map(input, Alphabets.SCRIPT)
        TextStyleKind.SCRIPT_BOLD -> Alphabets.map(input, Alphabets.SCRIPT_BOLD)
        TextStyleKind.GOTHIC -> Alphabets.map(input, Alphabets.GOTHIC)
        TextStyleKind.GOTHIC_BOLD -> Alphabets.map(input, Alphabets.GOTHIC_BOLD)
        TextStyleKind.DOUBLE -> Alphabets.map(input, Alphabets.DOUBLE)
        TextStyleKind.TYPEWRITER -> Alphabets.map(input, Alphabets.TYPEWRITER)
        TextStyleKind.BUBBLES -> Alphabets.map(input, Alphabets.BUBBLES)
        TextStyleKind.BUBBLES_DARK -> Alphabets.map(input, Alphabets.BUBBLES_DARK)
        TextStyleKind.SQUARES -> Alphabets.map(input, Alphabets.SQUARES)
        TextStyleKind.SQUARES_DARK -> Alphabets.map(input, Alphabets.SQUARES_DARK)
        TextStyleKind.WIDE -> Alphabets.wide(input)
        TextStyleKind.SMALL_CAPS -> Alphabets.map(input, Alphabets.SMALL_CAPS)
        TextStyleKind.STRIKE_ALL -> combine(input, '̶')
        TextStyleKind.UNDERLINE -> combine(input, '̲')
        TextStyleKind.UPSIDE_DOWN -> Alphabets.upsideDown(input)
    }

    /**
     * WhatsApp only styles `*x*` when the marker touches a letter on the inside and nothing glued on the outside,
     * and never across lines. So each line is wrapped on its own, with its spaces kept outside the markers.
     */
    internal fun markLines(input: String, open: String, close: String): String =
        input.lines().joinToString("\n") { line ->
            val start = line.indexOfFirst { !it.isWhitespace() }
            if (start < 0) {
                line
            } else {
                val end = line.indexOfLast { !it.isWhitespace() } + 1
                line.substring(0, start) + open + line.substring(start, end) + close + line.substring(end)
            }
        }

    /** ```text``` works across lines, but a text that already holds ``` would close it early: one block per line then. */
    private fun mono(input: String): String {
        if (input.isBlank()) return input
        return if ("```" in input) markLines(input, "```", "```") else "```$input```"
    }

    /** Adds the combining [mark] after every visible character (spaces stay plain, so the line is not one long rule). */
    private fun combine(input: String, mark: Char): String = buildString {
        val text = Normalizer.normalize(input, Normalizer.Form.NFC)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            appendCodePoint(cp)
            if (!Character.isWhitespace(cp) && Character.getType(cp) != Character.NON_SPACING_MARK.toInt()) append(mark)
            i += Character.charCount(cp)
        }
    }
}
