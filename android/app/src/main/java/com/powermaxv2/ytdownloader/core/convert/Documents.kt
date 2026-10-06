package com.powermaxv2.ytdownloader.core.convert

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

// Text documents: Markdown → HTML page, CSV ⇄ JSON. No Android UI; org.json comes with Android (tests run these
// under Robolectric).

/** A document conversion refused for a reason the user can act on (message in French, shown as is). */
class DocumentError(message: String) : Exception(message)

object MarkdownHtml {
    /**
     * A standalone HTML page from [markdown]: raw HTML in the source is shown as text (escapeHtml), links and
     * images with dangerous schemes (javascript:…) are neutralised (sanitizeUrls), and the page forbids scripts and
     * remote fetches (Content-Security-Policy: only inline styles and data: images). [title] goes in the tab name.
     * A source nested too deeply (blockquotes, lists) is refused instead of overflowing the stack.
     */
    fun render(markdown: String, title: String): String {
        if (Nesting.markdownDepth(markdown) > Nesting.MAX_DEPTH) throw DocumentError(Nesting.MESSAGE)
        val parser = Parser.builder().build()
        val renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build()
        val body = try {
            renderer.render(parser.parse(markdown))
        } catch (e: StackOverflowError) {
            throw DocumentError(Nesting.MESSAGE)
        }
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">\n")
            append("<title>").append(escape(title)).append("</title>\n")
            append("<style>\n").append(CSS).append("</style>\n")
            append("</head>\n<body>\n<main>\n")
            append(body)
            append("</main>\n</body>\n</html>\n")
        }
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val CSS = """
        :root { color-scheme: light dark; }
        body { margin: 0; font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #2a0f0a; background: #fbf1e7; }
        main { max-width: 46em; margin: 0 auto; padding: 2em 1.2em 4em; }
        h1, h2, h3 { line-height: 1.25; margin: 1.6em 0 .5em; }
        h1 { font-size: 2em; margin-top: .4em; }
        a { color: #e2401f; }
        code, pre { font: 0.9em/1.5 ui-monospace, "JetBrains Mono", Menlo, Consolas, monospace; background: rgba(42,15,10,.07); border-radius: 6px; }
        code { padding: .1em .35em; }
        pre { padding: 1em; overflow-x: auto; }
        pre code { background: none; padding: 0; }
        blockquote { margin: 1em 0; padding: .2em 1em; border-left: 4px solid #ff8a1f; color: #7a6058; }
        img { max-width: 100%; height: auto; }
        hr { border: 0; border-top: 1px solid rgba(42,15,10,.2); margin: 2em 0; }
        @media (prefers-color-scheme: dark) {
          body { color: #fff4ea; background: #120a08; }
          a { color: #ff8a1f; }
          code, pre { background: rgba(255,227,214,.08); }
          blockquote { color: rgba(255,227,214,.64); }
          hr { border-top-color: rgba(255,227,214,.2); }
        }
    """.trimIndent() + "\n"
}

object CsvJson {
    /** Most bytes of a CSV / JSON / Markdown file the converter reads into memory. */
    const val MAX_BYTES: Int = 20 * 1024 * 1024

    /**
     * CSV → JSON: the first row names the fields, every other row becomes an object (values kept as text, nothing
     * guessed). Blank or repeated column names get "colonne N" / "nom (2)". Pretty-printed.
     */
    fun csvToJson(text: String): String {
        val table = Csv.parse(text)
        if (table.rows.isEmpty()) throw DocumentError("Ce CSV est vide.")
        val header = headerNames(table.rows.first(), table.columnCount)
        val array = JSONArray()
        table.rows.drop(1).forEach { row ->
            val obj = JSONObject()
            header.forEachIndexed { index, name -> obj.put(name, row.getOrElse(index) { "" }) }
            array.put(obj)
        }
        return array.toString(2) + "\n"
    }

    /** Field names from the header row, padded to [width] columns, unique and never blank. */
    fun headerNames(row: List<String>, width: Int): List<String> {
        val used = HashSet<String>()
        return (0 until maxOf(width, row.size)).map { index ->
            val base = row.getOrNull(index)?.trim().orEmpty().ifEmpty { "colonne ${index + 1}" }
            var name = base
            var n = 2
            while (!used.add(name)) name = "$base (${n++})"
            name
        }
    }

    /**
     * JSON → CSV: a list of flat objects (or JSON Lines, one object per line) becomes a table whose columns are
     * every key met, in order of appearance. Nested objects / lists are written as JSON text; null as an empty
     * cell; a list of plain values gives one "valeur" column; a single object gives one row.
     */
    fun jsonToCsv(text: String): String {
        val items = readItems(text.removePrefix("﻿"))
        if (items.isEmpty()) throw DocumentError("Ce JSON ne contient aucune ligne à mettre en tableau.")
        val columns = LinkedHashSet<String>()
        val scalarColumn = "valeur"
        items.forEach { item ->
            if (item is JSONObject) {
                val keys = item.keys()
                while (keys.hasNext()) columns.add(keys.next())
            } else {
                columns.add(scalarColumn)
            }
        }
        val header = columns.toList()
        val rows = ArrayList<List<String>>(items.size + 1)
        rows.add(header)
        items.forEach { item ->
            rows.add(
                header.map { column ->
                    when {
                        item is JSONObject -> cell(if (item.has(column)) item.opt(column) else null)
                        column == scalarColumn -> cell(item)
                        else -> ""
                    }
                },
            )
        }
        return Csv.write(rows)
    }

    private fun readItems(text: String): List<Any?> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw DocumentError("Ce JSON est vide.")
        // org.json parses nested values recursively: refuse absurd nesting before it overflows the stack.
        if (Nesting.jsonDepth(trimmed) > Nesting.MAX_DEPTH) throw DocumentError(Nesting.MESSAGE)
        return try {
            parseItems(trimmed)
        } catch (e: StackOverflowError) {
            throw DocumentError(Nesting.MESSAGE)
        }
    }

    private fun parseItems(trimmed: String): List<Any?> {
        val whole = try {
            val tokener = JSONTokener(trimmed)
            val value = tokener.nextValue()
            // Something after the first value: maybe JSON Lines.
            if (tokener.nextClean() != '\u0000') null else value
        } catch (e: JSONException) {
            null
        }
        return when (whole) {
            is JSONArray -> (0 until whole.length()).map { whole.opt(it) }
            is JSONObject -> listOf(whole)
            null -> readLines(trimmed)
            else -> throw DocumentError("Ce JSON ne contient qu'une valeur : rien à mettre en tableau.")
        }
    }

    /** JSON Lines: one value per non-blank line. */
    private fun readLines(text: String): List<Any?> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, line ->
            try {
                JSONTokener(line).nextValue()
            } catch (e: JSONException) {
                throw DocumentError("JSON invalide (ligne ${index + 1}) : vérifiez le fichier.")
            }
        }
        .toList()

    private fun cell(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }
}

/** Nesting limits of the document parsers (pure, unit tested). */
object Nesting {
    /** Deepest nesting accepted (real files stay far below; thousands overflow a thread's stack). */
    const val MAX_DEPTH = 200

    const val MESSAGE = "Ce fichier est imbriqué beaucoup trop profondément : Gulp refuse de le convertir."

    /** Deepest [ / { nesting of JSON text, brackets inside strings ignored. */
    fun jsonDepth(text: String): Int {
        var depth = 0
        var max = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> {
                    depth++
                    if (depth > max) max = depth
                }
                ']', '}' -> if (depth > 0) depth--
            }
        }
        return max
    }

    /**
     * Rough deepest block nesting of Markdown: on each line, the container markers before the text
     * (">" quotes, "-", "*", "+" or "1." list items) plus indentation in steps of two spaces. Generous on
     * purpose: it only has to catch hostile files, not to measure real ones exactly.
     */
    fun markdownDepth(text: String): Int {
        var max = 0
        for (line in text.lineSequence()) {
            var depth = 0
            var spaces = 0
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == ' ' -> spaces++
                    c == '\t' -> spaces += 4
                    c == '>' -> depth++
                    (c == '-' || c == '*' || c == '+') && i + 1 < line.length && line[i + 1] == ' ' -> depth++
                    c.isDigit() -> {
                        var j = i
                        while (j < line.length && line[j].isDigit()) j++
                        if (j < line.length && (line[j] == '.' || line[j] == ')')) {
                            depth++
                            i = j
                        } else {
                            break
                        }
                    }
                    else -> break
                }
                i++
            }
            val total = depth + spaces / 2
            if (total > max) max = total
        }
        return max
    }
}
