package com.powermaxv2.ytdownloader.core.convert

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is Android's: these run under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DocumentsTest {
    @Test fun csvToJsonUsesTheHeader() {
        val json = JSONArray(CsvJson.csvToJson("nom;âge;ville\nLéa;31;Nice\nTom;28\n"))
        assertEquals(2, json.length())
        val lea = json.getJSONObject(0)
        assertEquals("Léa", lea.getString("nom"))
        assertEquals("31", lea.getString("âge"))
        assertEquals("Nice", lea.getString("ville"))
        // Missing cells are empty text.
        assertEquals("", json.getJSONObject(1).getString("ville"))
        // Field order follows the columns.
        assertEquals(listOf("nom", "âge", "ville"), lea.keys().asSequence().toList())
    }

    @Test fun headerNamesAreUniqueAndNeverBlank() {
        assertEquals(listOf("a", "colonne 2", "a (2)", "colonne 4"), CsvJson.headerNames(listOf("a", " ", "a"), 4))
    }

    @Test fun emptyCsvIsRefused() {
        assertRefused { CsvJson.csvToJson("\n\n") }
    }

    @Test fun jsonToCsvTakesEveryKeyInOrder() {
        val csv = CsvJson.jsonToCsv("""[{"nom":"Léa","age":31},{"nom":"Tom","ville":"Nice, France","tags":["a","b"],"x":null}]""")
        assertEquals(
            "nom,age,ville,tags,x\r\nLéa,31,,,\r\nTom,,\"Nice, France\",\"[\"\"a\"\",\"\"b\"\"]\",\r\n",
            csv,
        )
    }

    @Test fun jsonLinesAndSingleObjectAndScalars() {
        assertEquals("a\r\n1\r\n2\r\n", CsvJson.jsonToCsv("{\"a\":1}\n{\"a\":2}\n"))
        assertEquals("a,b\r\n1,true\r\n", CsvJson.jsonToCsv("{\"a\":1,\"b\":true}"))
        assertEquals("valeur\r\n1\r\ndeux\r\n", CsvJson.jsonToCsv("[1,\"deux\"]"))
        assertEquals("a\r\n1\r\n", CsvJson.jsonToCsv("﻿[{\"a\":1}]"))
    }

    @Test fun badJsonIsRefusedWithAClearMessage() {
        assertRefused { CsvJson.jsonToCsv("") }
        assertRefused { CsvJson.jsonToCsv("[]") }
        assertRefused { CsvJson.jsonToCsv("42") }
        assertRefused { CsvJson.jsonToCsv("{\"a\":") }
    }

    @Test fun csvJsonRoundTrip() {
        val csv = "id,texte\r\n1,\"Bonjour, \"\"ami\"\"\"\r\n2,é\r\n"
        assertEquals(csv, CsvJson.jsonToCsv(CsvJson.csvToJson(csv)))
    }

    @Test fun markdownPageIsStandaloneAndSafe() {
        val html = MarkdownHtml.render(
            "# Titre\n\nDu **gras**, [un lien](https://example.org) et [piège](javascript:alert(1)).\n\n<script>alert('x')</script>\n",
            "Notes <de> \"cours\"",
        )
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta charset=\"utf-8\">"))
        assertTrue(html.contains("<title>Notes &lt;de&gt; &quot;cours&quot;</title>"))
        assertTrue(html.contains("<h1>Titre</h1>"))
        assertTrue(html.contains("<strong>gras</strong>"))
        assertTrue(html.contains("href=\"https://example.org\""))
        // Raw HTML is shown as text, dangerous links are neutralised, scripts are forbidden by the page itself.
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("Content-Security-Policy"))
        // No remote fetches from the page: images only as data: URIs.
        assertTrue(html.contains("img-src data:;"))
        assertFalse(html.contains("https:;"))
    }

    @Test fun deeplyNestedJsonIsRefusedNotCrashing() {
        assertRefused { CsvJson.jsonToCsv("[".repeat(100_000) + "]".repeat(100_000)) }
        assertRefused { CsvJson.jsonToCsv("{\"a\":".repeat(50_000) + "1" + "}".repeat(50_000)) }
        // Brackets inside strings do not count.
        assertEquals("a\r\n[[[[[[\r\n", CsvJson.jsonToCsv("[{\"a\":\"[[[[[[\"}]"))
        assertEquals(150, Nesting.jsonDepth("[".repeat(150) + "]".repeat(150)))
        assertEquals(1, Nesting.jsonDepth("[\"\\\"[[\"]"))
    }

    @Test fun deeplyNestedMarkdownIsRefusedNotCrashing() {
        assertRefused { MarkdownHtml.render(">".repeat(100_000) + " fond", "x") }
        assertRefused { MarkdownHtml.render((0 until 5_000).joinToString("\n") { "  ".repeat(it) + "- item" }, "x") }
        // Inline nesting the pre-check does not see: either it renders or the overflow becomes a DocumentError.
        try {
            MarkdownHtml.render("*_".repeat(5_000) + "x" + "_*".repeat(5_000), "x")
        } catch (e: DocumentError) {
            assertEquals(Nesting.MESSAGE, e.message)
        }
        assertEquals(3, Nesting.markdownDepth("> > - texte"))
        assertEquals(2, Nesting.markdownDepth("- a\n  - b"))
        assertEquals(0, Nesting.markdownDepth("        code indenté"))
        assertEquals(2, Nesting.markdownDepth("1. un\n   1) deux"))
        assertTrue(MarkdownHtml.render("> citation\n\n- a\n  - b\n", "x").contains("<blockquote>"))
    }

    private fun assertRefused(block: () -> Unit) {
        try {
            block()
        } catch (e: DocumentError) {
            assertTrue(e.message!!.isNotBlank())
            return
        }
        throw AssertionError("DocumentError attendue")
    }

    private fun refusal(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: DocumentError) {
        e.message
    }

    @Test fun tooManyColumnsAreRefusedBeforeBuilding() {
        // A header of commas: thousands of columns from a few kilobytes.
        val wide = ",".repeat(CsvJson.MAX_COLUMNS + 10) + "\n" + "x\n".repeat(3)
        assertEquals(CsvJson.TOO_WIDE, refusal { CsvJson.csvToJson(wide) })
        // Many distinct keys, one per object.
        val keys = "[" + (1..CsvJson.MAX_COLUMNS + 1).joinToString(",") { "{\"k$it\":1}" } + "]"
        assertEquals(CsvJson.TOO_WIDE, refusal { CsvJson.jsonToCsv(keys) })
        // At the limit it still converts.
        val ok = "[" + (1..50).joinToString(",") { "{\"k$it\":1}" } + "]"
        assertEquals(51, CsvJson.jsonToCsv(ok).trimEnd().lines().size)
    }

    @Test fun tooManyCellsAreRefused() {
        assertEquals(CsvJson.TOO_BIG, refusal { CsvJson.checkSize(5_000, 1_001) })
        assertEquals(null, refusal { CsvJson.checkSize(2_500, 2_000) })
        // One object with 2 000 keys, then 2 500 empty ones (JSON Lines): 5 M cells from a ~30 ko file.
        val wide = "{" + (1..CsvJson.MAX_COLUMNS).joinToString(",") { "\"k$it\":1" } + "}"
        val json = wide + "\n" + "{}\n".repeat(2_500)
        assertEquals(CsvJson.TOO_BIG, refusal { CsvJson.jsonToCsv(json) })
    }

    @Test fun jsonFromCsvIsCappedInSize() {
        // Long column names repeated on every row: a 2 Mo CSV would give a JSON of ~800 Mo.
        val header = (1..100).joinToString(",") { "c$it" + "x".repeat(4_000) }
        val csv = header + "\n" + ",\n".repeat(2_000)
        assertTrue(CsvJson.jsonSizeEstimate(2_000, header.split(','), csv.length) > CsvJson.MAX_JSON_CHARS)
        assertEquals(CsvJson.TOO_BIG, refusal { CsvJson.csvToJson(csv) })
    }
}
