package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlighterTest {
    private fun typed(text: String, lang: CodeLang): List<Pair<String, TokenType>> =
        Highlighter.tokenize(text, lang).map { text.substring(it.start, it.end) to it.type }

    @Test fun languagesFromNames() {
        assertEquals(CodeLang.KOTLIN, CodeLang.fromName("MainActivity.kt"))
        assertEquals(CodeLang.KOTLIN, CodeLang.fromName("build.gradle.kts"))
        assertEquals(CodeLang.TYPESCRIPT, CodeLang.fromName("app.tsx"))
        assertEquals(CodeLang.PYTHON, CodeLang.fromName("script.py"))
        assertEquals(CodeLang.CLIKE, CodeLang.fromName("main.rs"))
        assertEquals(CodeLang.SHELL, CodeLang.fromName("Dockerfile"))
        assertEquals(CodeLang.MARKUP, CodeLang.fromName("index.HTML"))
        assertEquals(CodeLang.YAML, CodeLang.fromName("ci.yml"))
        assertEquals(CodeLang.PLAIN, CodeLang.fromName("notes.txt"))
    }

    @Test fun kotlin() {
        val code = "@Composable\nfun Hello(name: String = \"Gulp\") { // salut\n    val n = 0x1F + 2.5e3 /* bloc */\n}"
        val t = typed(code, CodeLang.KOTLIN)
        assertTrue(t.contains("@Composable" to TokenType.ANNOTATION))
        assertTrue(t.contains("fun" to TokenType.KEYWORD))
        assertTrue(t.contains("val" to TokenType.KEYWORD))
        assertTrue(t.contains("Hello" to TokenType.TYPE))
        assertTrue(t.contains("String" to TokenType.TYPE))
        assertTrue(t.contains("\"Gulp\"" to TokenType.STRING))
        assertTrue(t.contains("// salut" to TokenType.COMMENT))
        assertTrue(t.contains("/* bloc */" to TokenType.COMMENT))
        assertTrue(t.contains("0x1F" to TokenType.NUMBER))
        assertTrue(t.contains("2.5e3" to TokenType.NUMBER))
        // Identifiers holding digits are not numbers.
        assertTrue(typed("val x2 = a1", CodeLang.KOTLIN).none { it.second == TokenType.NUMBER })
    }

    @Test fun stringsWithEscapesAndTripleQuotes() {
        val t = typed("x = \"a\\\"b\" + '''multi\nline'''", CodeLang.PYTHON)
        assertEquals("\"a\\\"b\"" to TokenType.STRING, t[0])
        assertEquals("'''multi\nline'''" to TokenType.STRING, t[1])
        // An unclosed string stops at the line end.
        val u = typed("s = \"open\nnext = 1", CodeLang.JAVASCRIPT)
        assertEquals("\"open" to TokenType.STRING, u[0])
        assertTrue(u.contains("1" to TokenType.NUMBER))
    }

    @Test fun pythonAndShellComments() {
        assertTrue(typed("# note\ndef f(): pass", CodeLang.PYTHON).containsAll(listOf("# note" to TokenType.COMMENT, "def" to TokenType.KEYWORD)))
        val sh = typed("echo \$HOME \${PATH} # fin", CodeLang.SHELL)
        assertTrue(sh.containsAll(listOf("echo" to TokenType.KEYWORD, "\$HOME" to TokenType.ATTR, "\${PATH}" to TokenType.ATTR, "# fin" to TokenType.COMMENT)))
        // C: "#include" is a preprocessor line, "i--" is no comment.
        val c = typed("#include <stdio.h>\nint i; i--;", CodeLang.CLIKE)
        assertEquals("#include <stdio.h>" to TokenType.ANNOTATION, c[0])
        assertTrue(c.none { it.second == TokenType.COMMENT })
    }

    @Test fun markup() {
        val t = typed("<!-- c --><a href=\"x\" disabled>t</a><br/>", CodeLang.MARKUP)
        assertEquals(
            listOf(
                "<!-- c -->" to TokenType.COMMENT,
                "<a" to TokenType.TAG,
                "href" to TokenType.ATTR,
                "\"x\"" to TokenType.STRING,
                "disabled" to TokenType.ATTR,
                ">" to TokenType.TAG,
                "</a" to TokenType.TAG,
                ">" to TokenType.TAG,
                "<br" to TokenType.TAG,
                "/>" to TokenType.TAG,
            ),
            t,
        )
    }

    @Test fun jsonKeysAndValues() {
        val t = typed("{\"name\": \"Gulp\", \"n\": -1.5, \"ok\": true, \"x\": null}", CodeLang.JSON)
        assertEquals(
            listOf(
                "\"name\"" to TokenType.ATTR, "\"Gulp\"" to TokenType.STRING,
                "\"n\"" to TokenType.ATTR, "-1.5" to TokenType.NUMBER,
                "\"ok\"" to TokenType.ATTR, "true" to TokenType.KEYWORD,
                "\"x\"" to TokenType.ATTR, "null" to TokenType.KEYWORD,
            ),
            t,
        )
    }

    @Test fun yamlAndCss() {
        val y = typed("# conf\nname: gulp # app\nport: 8080\nlist:\n  - true\n[section]", CodeLang.YAML)
        assertTrue(y.containsAll(listOf("# conf" to TokenType.COMMENT, "name" to TokenType.ATTR, "# app" to TokenType.COMMENT, "8080" to TokenType.NUMBER, "true" to TokenType.KEYWORD, "[section]" to TokenType.TAG)))
        val css = typed("body { color: #fff; margin: 1.5em; } @media x {}", CodeLang.CSS)
        assertTrue(css.containsAll(listOf("body" to TokenType.TAG, "color" to TokenType.ATTR, "#fff" to TokenType.NUMBER, "1.5em" to TokenType.NUMBER, "@media" to TokenType.KEYWORD)))
    }

    @Test fun tokensAreOrderedAndNeverOverlap() {
        val sample = "fun a() { \"s\" /* c */ 1 }\n<x y='1'>\n# h\n" .repeat(50)
        for (lang in CodeLang.entries) {
            val tokens = Highlighter.tokenize(sample, lang)
            for (i in 1 until tokens.size) assertTrue("$lang $i", tokens[i].start >= tokens[i - 1].end)
            assertTrue(tokens.all { it.end > it.start && it.end <= sample.length })
        }
    }

    @Test fun hugeInputsStayBounded() {
        // 300k one-character numbers: capped, and no stack involved.
        val text = "1 ".repeat(400_000)
        assertEquals(Highlighter.MAX_TOKENS, Highlighter.tokenize(text, CodeLang.KOTLIN).size)
        val unclosed = "/*" + "x".repeat(100_000)
        assertEquals(1, Highlighter.tokenize(unclosed, CodeLang.JAVA).size)
    }

    @Test fun linesAndSpans() {
        val text = Highlighter.normalizeLineEnds("val a = 1\r\n\"x\ny\"\rfin\n")
        assertEquals("val a = 1\n\"x\ny\"\nfin\n", text)
        val starts = Highlighter.lineStarts(text)
        assertEquals(listOf(0, 10, 13, 16), starts.toList())
        assertEquals(9, Highlighter.lineEnd(text, starts, 0))
        assertEquals(19, Highlighter.lineEnd(text, starts, 3))
        val tokens = Highlighter.tokenize(text, CodeLang.KOTLIN)
        assertEquals(listOf(Token(0, 3, TokenType.KEYWORD), Token(8, 9, TokenType.NUMBER)), Highlighter.spansIn(tokens, 0, 9))
        // The string runs over two lines (unclosed " stops at the line end, so line 2 is "x).
        assertEquals(listOf(Token(0, 2, TokenType.STRING)), Highlighter.spansIn(tokens, 10, 12))
        assertEquals(emptyList<Token>(), Highlighter.spansIn(tokens, 5, 5))
        assertEquals(listOf(0), Highlighter.lineStarts("").toList())
    }

    @Test(timeout = 20_000)
    fun pathologicalMegabyteInputsStayLinear() {
        val inputs = listOf(
            "\${\n".repeat(330_000),
            "\"\n".repeat(500_000),
            "'\n".repeat(500_000),
            "`".repeat(1_000_000),
            "/*".repeat(500_000),
            "<a \"".repeat(250_000),
            "<!--".repeat(250_000),
            "\"\"\"".repeat(330_000),
            "a: \"\n".repeat(200_000),
            "{\"".repeat(500_000),
        )
        for (text in inputs) {
            for (lang in CodeLang.entries) Highlighter.tokenize(text, lang)
        }
    }
}
