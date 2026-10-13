package com.powermaxv2.ytdownloader.core.direct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectNumberTest {
    private val fr = Countries.france
    private fun ok(input: String, country: Country = fr) = DirectNumbers.parse(input, country) as DirectNumber.Ok

    @Test fun frenchNumbersWrittenEveryWay() {
        listOf(
            "06 12 34 56 78", "0612345678", "06.12.34.56.78", "06-12-34-56-78", "+33 6 12 34 56 78",
            "+33 (0)6 12 34 56 78", "0033 6 12 34 56 78", "+33612345678", "tel:+33612345678", " 06 12 34 56 78 ",
        ).forEach { assertEquals(it, "33612345678", ok(it).digits) }
    }

    @Test fun prettyForm() {
        assertEquals("+33 6 12 34 56 78", ok("0612345678").pretty)
        assertEquals("+1 514 555 0199", ok("+1 514 555 0199").pretty)
        assertEquals("+32 470 123 456", ok("+32 470 12 34 56").pretty)
        assertEquals("France", ok("0612345678").country?.name)
    }

    @Test fun nationalNumbersAreReadInTheChosenCountry() {
        assertEquals("32470123456", ok("0470 12 34 56", Countries.byIso("BE")!!).digits)
        assertEquals("41791234567", ok("079 123 45 67", Countries.byIso("CH")!!).digits)
        assertEquals("15145550199", ok("514 555 0199", Countries.byIso("CA")!!).digits)
        assertEquals("15145550199", ok("1 514 555 0199", Countries.byIso("US")!!).digits)
        // Italy keeps its 0.
        assertEquals("390612345678", ok("06 1234 5678", Countries.byIso("IT")!!).digits)
        assertEquals("212612345678", ok("0612345678", Countries.byIso("MA")!!).digits)
    }

    @Test fun aForeignNumberWithItsCodeIgnoresTheChosenCountry() {
        assertEquals("447911123456", ok("+44 7911 123456").digits)
        assertEquals("Royaume-Uni", ok("+44 7911 123456").country?.name)
        // Unknown code: still opened, written plainly.
        val far = ok("+81 90 1234 5678")
        assertEquals("819012345678", far.digits)
        assertEquals("+819012345678", far.pretty)
        assertNull(far.country)
    }

    @Test fun badNumbersSayWhy() {
        assertEquals(DirectNumber.Empty, DirectNumbers.parse("   ", fr))
        assertTrue(DirectNumbers.parse("06 12 34", fr) is DirectNumber.Bad)
        assertTrue(DirectNumbers.parse("06 12 34 56 78 9", fr) is DirectNumber.Bad)
        assertTrue(DirectNumbers.parse("appelle-moi", fr) is DirectNumber.Bad)
        assertTrue(DirectNumbers.parse("+33 06 12 34 56 78", fr) is DirectNumber.Bad)
        assertTrue(DirectNumbers.parse("+1234567890123456", fr) is DirectNumber.Bad)
        val reason = (DirectNumbers.parse("06 12", fr) as DirectNumber.Bad).reason
        assertTrue(reason, "10 chiffres" in reason)
    }

    @Test fun landlinesAreFlagged() {
        assertTrue(ok("01 23 45 67 89").maybeLandline)
        assertFalse(ok("07 12 34 56 78").maybeLandline)
        assertFalse(ok("+81 90 1234 5678").maybeLandline)
    }

    @Test fun findsANumberInAPastedText() {
        assertEquals("06 12 34 56 78", DirectNumbers.find("Salut c'est Léa, mon num : 06 12 34 56 78 bisous", fr))
        assertEquals("+33 6 12 34 56 78", DirectNumbers.find("Tél. +33 6 12 34 56 78\nMerci", fr))
        assertNull(DirectNumbers.find("RDV le 12/10 à 18h, code 1234", fr))
        assertNull(DirectNumbers.find("", fr))
    }

    @Test fun linkWithAndWithoutMessage() {
        assertEquals("https://wa.me/33612345678", DirectNumbers.link("33612345678"))
        assertEquals("https://wa.me/33612345678", DirectNumbers.link("33612345678", "   "))
        assertEquals(
            "https://wa.me/33612345678?text=Bonjour%20%C3%A0%20vous%20%26%20merci%20%3F",
            DirectNumbers.link("33612345678", "Bonjour à vous & merci ?"),
        )
    }

    @Test fun defaultCountryComesFromThePhone() {
        assertEquals("BE", Countries.default("be").iso)
        assertEquals("FR", Countries.default("JP").iso)
        assertEquals("FR", Countries.default(null).iso)
    }

    @Test fun recentsStayShortNewestFirstAndClean() {
        var r = emptyList<String>()
        (1..10).forEach { r = DirectRecents.add(r, "3361234567$it".take(11)) }
        assertEquals(DirectRecents.MAX, r.size)
        r = DirectRecents.add(r, r.last())
        assertEquals(r.first(), DirectRecents.decode(DirectRecents.encode(r)).first())
        assertEquals(listOf("33612345678"), DirectRecents.decode("33612345678\nabc\n\n12\n33612345678"))
        assertEquals(emptyList<String>(), DirectRecents.decode(null))
        assertEquals(listOf("33612345678"), DirectRecents.remove(listOf("33612345678", "32470123456"), "32470123456"))
    }
}
