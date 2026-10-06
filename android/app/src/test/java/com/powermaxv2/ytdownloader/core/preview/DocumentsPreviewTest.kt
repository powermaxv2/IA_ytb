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
}
