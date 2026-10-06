package com.powermaxv2.ytdownloader.core.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun rows(text: String, delimiter: Char? = null) =
        (if (delimiter == null) Csv.parse(text) else Csv.parse(text, delimiter)).rows

    @Test fun simpleTable() {
        assertEquals(listOf(listOf("nom", "âge"), listOf("Léa", "31"), listOf("Tom", "28")), rows("nom,âge\nLéa,31\nTom,28\n"))
    }

    @Test fun lastLineWithoutLineEnd() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\n1,2"))
    }

    @Test fun crlfAndCrLineEnds() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\r\n1,2\r\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\r1,2\r"))
    }

    @Test fun quotedFieldsWithDelimitersQuotesAndLineBreaks() {
        val text = "titre,note\r\n\"Bonjour, monde\",\"Il a dit \"\"salut\"\"\"\r\n\"deux\r\nlignes\",x\r\n"
        assertEquals(
            listOf(listOf("titre", "note"), listOf("Bonjour, monde", "Il a dit \"salut\""), listOf("deux\r\nlignes", "x")),
            rows(text),
        )
    }

    @Test fun emptyFieldsAndQuotedEmpty() {
        assertEquals(listOf(listOf("a", "", "c"), listOf("", "", "")), rows("a,,c\n,\"\",\n"))
        assertEquals(listOf(listOf("x", "")), rows("x,\"\""))
    }

    @Test fun blankLinesAreSkippedUnlessAsked() {
        assertEquals(listOf(listOf("a"), listOf("b")), rows("a\n\n\nb\n"))
        assertEquals(listOf(listOf("a"), listOf(""), listOf("b")), Csv.parse("a\n\nb\n", ',', skipBlankLines = false).rows)
    }

    @Test fun byteOrderMarkIsDropped() {
        assertEquals(listOf(listOf("id", "nom"), listOf("1", "Zoé")), rows("﻿id;nom\n1;Zoé\n"))
    }

    @Test fun unclosedQuoteTakesTheRest() {
        assertEquals(listOf(listOf("a", "b,c\nd")), rows("a,\"b,c\nd"))
    }

    @Test fun textAfterAClosingQuoteIsKept() {
        assertEquals(listOf(listOf("ab", "c")), rows("\"a\"b,c"))
    }

    @Test fun quoteInsideAnUnquotedFieldIsLiteral() {
        assertEquals(listOf(listOf("5\" écran", "ok")), rows("5\" écran,ok"))
    }

    @Test fun delimiterDetection() {
        assertEquals(',', Csv.detectDelimiter("a,b,c\n1,2,3\n"))
        assertEquals(';', Csv.detectDelimiter("nom;prix\nPain;1,20\nLait;0,95\n"))
        assertEquals('\t', Csv.detectDelimiter("a\tb\n1\t2\n"))
        assertEquals('|', Csv.detectDelimiter("a|b|c\n1|2|3"))
        // Commas inside quotes do not count.
        assertEquals(';', Csv.detectDelimiter("\"a,b,c\";d\n\"e,f\";g\n"))
        // Nothing found: comma.
        assertEquals(',', Csv.detectDelimiter("seule colonne\nvaleur\n"))
        assertEquals(',', Csv.detectDelimiter(""))
        // French Excel: ";" with decimal commas on every line.
        val table = Csv.parse("produit;prix\nPain;1,20\nLait;0,95\n")
        assertEquals(';', table.delimiter)
        assertEquals(listOf("Pain", "1,20"), table.rows[1])
    }

    @Test fun maxRowsTruncates() {
        val t = Csv.parse("a\nb\nc\nd\n", ',', maxRows = 2)
        assertEquals(listOf(listOf("a"), listOf("b")), t.rows)
        assertTrue(t.truncated)
        assertFalse(Csv.parse("a\nb\n", ',', maxRows = 2).truncated)
        assertEquals(2, Csv.parse("a,b\nc\n").columnCount)
    }

    @Test fun writeQuotesOnlyWhenNeeded() {
        assertEquals("a,b\r\n", Csv.write(listOf(listOf("a", "b"))))
        assertEquals("\"x,y\",\"say \"\"hi\"\"\",\"two\nlines\",\" pad\"\r\n", Csv.write(listOf(listOf("x,y", "say \"hi\"", "two\nlines", " pad"))))
        assertEquals("a;\"b;c\"\r\n", Csv.write(listOf(listOf("a", "b;c")), ';'))
    }

    @Test fun roundTrip() {
        val data = listOf(listOf("id", "texte"), listOf("1", "Bonjour, \"ami\"\nà bientôt"), listOf("2", ""))
        assertEquals(data, Csv.parse(Csv.write(data)).rows)
    }

    @Test fun textDecoding() {
        assertEquals("été", TextDecode.decode("été".toByteArray(Charsets.UTF_8)))
        assertEquals("été", TextDecode.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "été".toByteArray(Charsets.UTF_8)))
        assertEquals("été", TextDecode.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "été".toByteArray(Charsets.UTF_16LE)))
        // Windows-1252 (old Excel exports): "é" is 0xE9, not valid UTF-8.
        assertEquals("prénom;€", TextDecode.decode(byteArrayOf(0x70, 0x72, 0xE9.toByte(), 0x6E, 0x6F, 0x6D, 0x3B, 0x80.toByte())))
    }
}
