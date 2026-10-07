package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is Android's: these run under Robolectric (commonmark is plain Java). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DocumentsPreviewTest {
    @Test fun markdownBlocks() {
        val md = """
            # Titre *penché*

            Un **gras** et du `code`, un [lien](https://ex.com) <b>brut</b>.

            > Citation

            - un
            - deux
              1. sous
            ---
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        val blocks = MarkdownBlocks.parse(md)
        val h = blocks[0]
        assertEquals(MdBlockKind.HEADING, h.kind)
        assertEquals(1, h.level)
        assertEquals("Titre penché", h.text)
        assertEquals(listOf(MdSpan(6, 12, MdStyle.ITALIC)), h.spans)

        val p = blocks[1]
        assertEquals("Un gras et du code, un lien (https://ex.com) <b>brut</b>.", p.text)
        assertTrue(p.spans.contains(MdSpan(3, 7, MdStyle.BOLD)))
        assertTrue(p.spans.contains(MdSpan(14, 18, MdStyle.CODE)))
        assertTrue(p.spans.contains(MdSpan(23, 27, MdStyle.LINK)))
        assertTrue(p.spans.contains(MdSpan(27, 44, MdStyle.URL)))

        assertEquals(1, blocks[2].quote)
        assertEquals("Citation", blocks[2].text)
        assertEquals("•" to 1, blocks[3].bullet to blocks[3].indent)
        assertEquals("deux", blocks[4].text)
        assertEquals("1." to 2, blocks[5].bullet to blocks[5].indent)
        assertEquals(MdBlockKind.RULE, blocks[6].kind)
        assertEquals(MdBlock(MdBlockKind.CODE, "val x = 1"), blocks[7])
    }

    @Test fun markdownImagesAndDeepInlines() {
        val b = MarkdownBlocks.parse("![chat](c.png)").single()
        assertEquals("[image : chat]", b.text)
        assertEquals(listOf(MdSpan(0, 14, MdStyle.ITALIC)), b.spans)
        // Thousands of nested emphasis markers: walked without recursion.
        val bomb = "*_".repeat(20_000) + "x" + "_*".repeat(20_000)
        MarkdownBlocks.parse(bomb)
    }

    @Test(expected = TooDeep::class)
    fun markdownTooDeepIsRefused() {
        MarkdownBlocks.parse(">".repeat(100_000) + " x")
    }

    @Test fun jsonIsReindentedKeepingTheText() {
        assertEquals(
            "{\n  \"b\": 1,\n  \"a\": [\n    true,\n    \"x, y\"\n  ],\n  \"e\": {},\n  \"u\": \"https://a/b\"\n}",
            JsonPretty.format("{\"b\":1,\"a\":[true,\"x, y\"],\"e\":{ },\"u\":\"https://a/b\"}"),
        )
        assertEquals("[\n  1\n]", JsonPretty.format("[1]\n"))
    }

    @Test fun invalidOrDeepJsonStaysRaw() {
        assertNull(JsonPretty.format("{\"a\": "))
        assertNull(JsonPretty.format("{\"a\":1}\n{\"a\":2}"))
        assertNull(JsonPretty.format("[".repeat(100_000)))
        assertNull(JsonPretty.format("{\"a\":1} junk"))
    }

    @Test fun reindentHandlesEscapedQuotes() {
        assertEquals("{\n  \"k\": \"a\\\"{,\"\n}", JsonPretty.reindent("{\"k\":\"a\\\"{,\"}"))
    }

    /** A 1 Mio JSON nested 150 deep full of scalars would grow ~150 times when re-indented: it stays raw. */
    @Test fun reindentIsCappedInMemory() {
        val depth = 150
        val body = "1,".repeat(500_000) + "1"
        val bomb = "[".repeat(depth) + body + "]".repeat(depth)
        assertNull(JsonPretty.reindent(bomb))
        assertNull(JsonPretty.format(bomb))
        // Indentation stops growing at MAX_INDENT_DEPTH levels.
        val deep = "[".repeat(40) + "1" + "]".repeat(40)
        val pretty = JsonPretty.reindent(deep)!!
        val widest = pretty.lines().maxOf { line -> line.length - line.trimStart().length }
        assertEquals(JsonPretty.MAX_INDENT_DEPTH * 2, widest)
        assertTrue(JsonPretty.outputLimit(1024 * 1024) <= JsonPretty.MAX_OUTPUT_CHARS)
        assertEquals(4 * 1024 * 1024, JsonPretty.outputLimit(1024 * 1024))
        // An ordinary file still fits.
        val normal = "[" + (1..20_000).joinToString(",") { "{\"id\":$it,\"nom\":\"n$it\"}" } + "]"
        assertTrue(JsonPretty.format(normal) != null)
    }
}
