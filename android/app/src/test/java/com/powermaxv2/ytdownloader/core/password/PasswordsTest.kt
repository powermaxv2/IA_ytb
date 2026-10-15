package com.powermaxv2.ytdownloader.core.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log2

class PasswordsTest {
    private fun seeded(seed: Long = 42) = Random(seed)

    @Test fun randomHasTheLengthAndOneOfEachKind() {
        repeat(200) { i ->
            val p = Passwords.randomPassword(PasswordOptions(length = 8), seeded(i.toLong())).text
            assertEquals(8, p.length)
            assertTrue(p, p.any { it in Passwords.LOWER })
            assertTrue(p, p.any { it in Passwords.UPPER })
            assertTrue(p, p.any { it in Passwords.DIGITS })
            assertTrue(p, p.any { it in Passwords.SYMBOLS })
        }
    }

    @Test fun onlyTheChosenKinds() {
        val p = Passwords.randomPassword(PasswordOptions(length = 40, upper = false, symbols = false), seeded()).text
        assertTrue(p.all { it in Passwords.LOWER || it in Passwords.DIGITS })
    }

    @Test fun nothingAmbiguousWhenAsked() {
        repeat(50) { i ->
            val p = Passwords.randomPassword(PasswordOptions(length = 64, avoidAmbiguous = true), seeded(i.toLong())).text
            assertTrue(p, p.none { it in Passwords.AMBIGUOUS })
        }
    }

    @Test fun noKindTurnsLowercaseBackOn() {
        val o = PasswordOptions(lower = false, upper = false, digits = false, symbols = false)
        val p = Passwords.randomPassword(o, seeded()).text
        assertTrue(p.all { it in Passwords.LOWER })
    }

    @Test fun lengthIsClamped() {
        assertEquals(Passwords.MIN_LENGTH, Passwords.randomPassword(PasswordOptions(length = 2), seeded()).text.length)
        assertEquals(Passwords.MAX_LENGTH, Passwords.randomPassword(PasswordOptions(length = 500), seeded()).text.length)
    }

    @Test fun bitsOfARandomPassword() {
        val g = Passwords.randomPassword(PasswordOptions(length = 20), seeded())
        val pool = 26 + 26 + 10 + Passwords.SYMBOLS.length
        assertTrue(abs(g.bits - 20 * log2(pool.toDouble())) < 1e-9)
        assertEquals(Strength.VERY_STRONG, Strength.of(g.bits))
    }

    @Test fun passphraseWordsSeparatorAndNumber() {
        val g = Passwords.passphrase(PasswordOptions(words = 4, separator = Separator.DOT, capitalize = true, addNumber = true), seeded())
        val parts = g.text.split('.')
        assertEquals(4, parts.size)
        assertTrue(parts.all { it.first().isUpperCase() })
        assertEquals(1, parts.count { w -> w.last().isDigit() })
        val words = Passwords.wordCount.toDouble()
        assertTrue(abs(g.bits - (4 * log2(words) + log2(100.0) + 2.0)) < 1e-9)
    }

    @Test fun plainPassphrase() {
        val g = Passwords.passphrase(PasswordOptions(words = 6, separator = Separator.SPACE, capitalize = false, addNumber = false), seeded())
        val parts = g.text.split(' ')
        assertEquals(6, parts.size)
        assertTrue(parts.all { it.all { c -> c in 'a'..'z' } })
    }

    @Test fun wordListIsCleanAndBig() {
        val list = WordList.words
        assertTrue(list.size >= 1000)
        assertEquals(list.size, list.toSet().size)
        assertTrue(list.all { it.matches(Regex("[a-z]{3,9}")) })
    }

    @Test fun pinIsDigitsAndNeverTrivial() {
        repeat(500) { i ->
            val g = Passwords.pin(4, seeded(i.toLong()))
            assertEquals(4, g.text.length)
            assertTrue(g.text.all { it.isDigit() })
            assertFalse(g.text, Passwords.trivialPin(g.text))
        }
        assertTrue(Passwords.trivialPin("1111"))
        assertTrue(Passwords.trivialPin("1234"))
        assertTrue(Passwords.trivialPin("9876"))
        assertFalse(Passwords.trivialPin("1357"))
    }

    @Test fun generateFollowsTheMode() {
        val o = PasswordOptions()
        assertEquals(PasswordMode.PIN, Passwords.generate(PasswordMode.PIN, o, seeded()).mode)
        assertEquals(PasswordMode.PASSPHRASE, Passwords.generate(PasswordMode.PASSPHRASE, o, seeded()).mode)
        assertEquals(PasswordMode.RANDOM, Passwords.generate(PasswordMode.CHECK, o, seeded()).mode)
    }

    @Test fun secureGeneratorDiffers() {
        val a = Passwords.generate(PasswordMode.RANDOM, PasswordOptions())
        val b = Passwords.generate(PasswordMode.RANDOM, PasswordOptions())
        assertTrue(a.text != b.text)
    }

    @Test fun commonPasswordsAreVeryWeak() {
        listOf("azerty", "123456", "Soleil", "motdepasse!", "doudou2").forEach {
            val c = Passwords.check(it)
            assertTrue(it, c.common)
            assertEquals(it, Strength.VERY_WEAK, c.strength)
        }
    }

    @Test fun checkRanksPasswords() {
        assertEquals(0.0, Passwords.check("").bits, 0.0)
        assertTrue(Passwords.check("aaaaaaaa").strength <= Strength.WEAK)
        assertTrue(Passwords.check("abcdefgh").strength <= Strength.WEAK)
        assertTrue(Passwords.check("Tq7#vLp2!xR9@mZw").strength == Strength.VERY_STRONG)
        assertTrue(Passwords.check("azerty2024!").strength <= Strength.FAIR)
        assertTrue(Passwords.check("short").tips.any { "Allonge" in it })
    }

    @Test fun crackTimes() {
        assertEquals("instantané", Passwords.crackTime(10.0))
        assertEquals("des milliards d'années", Passwords.crackTime(128.0))
        assertEquals("54 secondes", Passwords.crackTime(40.0))
        assertEquals("1 jour", Passwords.crackTime(51.0))
    }

    @Test fun strengthThresholds() {
        assertEquals(Strength.VERY_WEAK, Strength.of(10.0))
        assertEquals(Strength.WEAK, Strength.of(30.0))
        assertEquals(Strength.FAIR, Strength.of(50.0))
        assertEquals(Strength.STRONG, Strength.of(70.0))
        assertEquals(Strength.VERY_STRONG, Strength.of(90.0))
    }
}
