package com.powermaxv2.ytdownloader.core.styled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyledTextTest {
    private fun style(text: String, kind: TextStyleKind) = StyledText.apply(text, kind)

    @Test fun whatsAppMarkers() {
        assertEquals("*Salut*", style("Salut", TextStyleKind.BOLD))
        assertEquals("_Salut_", style("Salut", TextStyleKind.ITALIC))
        assertEquals("~Salut~", style("Salut", TextStyleKind.STRIKE))
        assertEquals("```Salut```", style("Salut", TextStyleKind.MONO))
        assertEquals("*_Salut_*", style("Salut", TextStyleKind.BOLD_ITALIC))
        assertEquals("> Salut", style("Salut", TextStyleKind.QUOTE))
    }

    @Test fun markersHugTheWordsAndLeaveSpacesOutside() {
        // "* Salut *" would not be bold in WhatsApp.
        assertEquals("  *Salut toi*  ", style("  Salut toi  ", TextStyleKind.BOLD))
    }

    @Test fun eachLineIsMarkedOnItsOwn() {
        // WhatsApp never styles across a line break.
        assertEquals("*Ligne 1*\n\n*Ligne 2*", style("Ligne 1\n\nLigne 2", TextStyleKind.BOLD))
        assertEquals("> a\n> b", style("a\nb", TextStyleKind.QUOTE))
    }

    @Test fun monoKeepsOneBlockUnlessTheTextHasBackticks() {
        assertEquals("```a\nb```", style("a\nb", TextStyleKind.MONO))
        assertEquals("```x ``` y```", style("x ``` y", TextStyleKind.MONO))
    }

    @Test fun blankTextStaysBlank() {
        TextStyleKind.entries.filter { it != TextStyleKind.QUOTE }.forEach { kind ->
            assertEquals(kind.name, "", style("", kind))
        }
        assertEquals("   ", style("   ", TextStyleKind.BOLD))
    }

    @Test fun mathAlphabets() {
        assertEquals("𝐆𝐮𝐥𝐩 𝟐𝟎", style("Gulp 20", TextStyleKind.SERIF_BOLD))
        assertEquals("𝗚𝘂𝗹𝗽", style("Gulp", TextStyleKind.SANS_BOLD))
        assertEquals("𝙶𝚞𝚕𝚙 𝟷", style("Gulp 1", TextStyleKind.TYPEWRITER))
        assertEquals("𝔾𝕦𝕝𝕡 𝟘", style("Gulp 0", TextStyleKind.DOUBLE))
        assertEquals("𝕲𝖚𝖑𝖕", style("Gulp", TextStyleKind.GOTHIC_BOLD))
        assertEquals("𝓖𝓾𝓵𝓹", style("Gulp", TextStyleKind.SCRIPT_BOLD))
    }

    @Test fun lettersUnicodePutElsewhere() {
        // Reserved holes of the blocks: the real letters live in "Letterlike Symbols".
        assertEquals("ℎ", style("h", TextStyleKind.SERIF_ITALIC))
        assertEquals("ℋℯ𝓁𝓁ℴ", style("Hello", TextStyleKind.SCRIPT))
        assertEquals("ℭℌℑℜℨ", style("CHIRZ", TextStyleKind.GOTHIC))
        assertEquals("ℂℍℕℙℚℝℤ", style("CHNPQRZ", TextStyleKind.DOUBLE))
        TextStyleKind.entries.filterNot { it.whatsApp }.forEach { kind ->
            val out = style("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", kind)
            // No unassigned code point slipped in.
            var i = 0
            while (i < out.length) {
                val cp = out.codePointAt(i)
                assertTrue("${kind.name} U+${cp.toString(16)}", Character.isDefined(cp))
                i += Character.charCount(cp)
            }
        }
    }

    @Test fun bubblesAndSquares() {
        assertEquals("Ⓖⓤⓛⓟ ⓪①⑨", style("Gulp 019", TextStyleKind.BUBBLES))
        assertEquals("🅖🅤🅛🅟 ⓿❶", style("Gulp 01", TextStyleKind.BUBBLES_DARK))
        assertEquals("🄶🅄🄻🄿", style("Gulp", TextStyleKind.SQUARES))
        assertEquals("🅶🆄🅻🅿", style("Gulp", TextStyleKind.SQUARES_DARK))
    }

    @Test fun accentedLettersStayAsTyped() {
        // A look-alike letter with a glued accent shows the accent beside it on most phones: é stays é.
        assertEquals("𝐒𝐨𝐢𝐫é𝐞", style("Soirée", TextStyleKind.SERIF_BOLD))
        assertEquals("Ⓒ'ⓔⓢⓣ ⓛ'éⓣéⓢ", style("C'est l'étés", TextStyleKind.BUBBLES))
        // Typed apart (e + ◌́), it is composed first and kept whole too.
        assertEquals("é", style("e\u0301", TextStyleKind.GOTHIC))
        assertEquals("ｃａｆé", style("café", TextStyleKind.WIDE))
    }

    @Test fun emojiAndOtherScriptsStay() {
        assertEquals("𝐎𝐊 🎉 привет", style("OK 🎉 привет", TextStyleKind.SERIF_BOLD))
    }

    @Test fun wideAndSmallCaps() {
        assertEquals("Ｇｕｌｐ！　２０", style("Gulp! 20", TextStyleKind.WIDE))
        assertEquals("Gᴜʟᴘ ᴇxᴛʀᴀ", style("Gulp extra", TextStyleKind.SMALL_CAPS))
    }

    @Test fun combiningLines() {
        assertEquals("a̶b̶ c̶", style("ab c", TextStyleKind.STRIKE_ALL))
        assertEquals("a̲", style("a", TextStyleKind.UNDERLINE))
        // Precomposed accents first, so the accent and the rule stack on one letter.
        assertEquals("é̶", style("é", TextStyleKind.STRIKE_ALL))
    }

    @Test fun upsideDown() {
        assertEquals("¡dln⅁", style("Gulp!", TextStyleKind.UPSIDE_DOWN))
        assertEquals("ǝɯɯoɔ", style("comme", TextStyleKind.UPSIDE_DOWN))
        // Lines swap too, and emoji stay whole.
        assertEquals("q\n🎉 ɐ", style("a 🎉\nb", TextStyleKind.UPSIDE_DOWN))
        assertEquals("ǝʇǝ", style("été", TextStyleKind.UPSIDE_DOWN))
    }

    @Test fun variantsShowWhatWhatsAppDraws() {
        val bold = StyledText.variant("Salut", TextStyleKind.BOLD)
        assertEquals("*Salut*", bold.text)
        assertEquals(ShownText("Salut", bold = true), bold.shown)
        val gothic = StyledText.variant("Salut", TextStyleKind.GOTHIC)
        assertEquals(gothic.text, gothic.shown.text)
        assertFalse(gothic.shown.bold)
    }

    @Test fun everyStyleIsListedOnce() {
        val kinds = StyledText.whatsAppKinds + StyledText.fontKinds
        assertEquals(TextStyleKind.entries.size, kinds.toSet().size)
        assertEquals(6, StyledText.whatsAppKinds.size)
        assertTrue(StyledText.fontKinds.size >= 15)
        assertEquals(TextStyleKind.entries.size, TextStyleKind.entries.map { it.label }.toSet().size)
    }
}
