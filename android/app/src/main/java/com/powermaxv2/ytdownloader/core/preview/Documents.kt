package com.powermaxv2.ytdownloader.core.preview

import com.powermaxv2.ytdownloader.core.convert.Nesting
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.json.JSONException
import org.json.JSONTokener

// Markdown and JSON as the Aperçu shows them. The Markdown tree is walked with an explicit stack (no recursion),
// after the same nesting check as the converter; JSON is only re-indented when org.json accepts it.

enum class MdBlockKind { HEADING, PARAGRAPH, CODE, RULE }

enum class MdStyle { BOLD, ITALIC, CODE, LINK, URL }

/** A styled stretch of a block's text. */
data class MdSpan(val start: Int, val end: Int, val style: MdStyle)

/**
 * One block of rendered Markdown: [level] 1..6 for headings, [quote] how many ">" it sits in, [indent] its list
 * depth, [bullet] "•" or "3." on the first paragraph of a list item.
 */
data class MdBlock(
    val kind: MdBlockKind,
    val text: String,
    val spans: List<MdSpan> = emptyList(),
    val level: Int = 0,
    val quote: Int = 0,
    val indent: Int = 0,
    val bullet: String? = null,
)

/** The document is nested too deeply to lay out (shown as plain text instead). */
class TooDeep : Exception(Nesting.MESSAGE)

object MarkdownBlocks {
    /** Most blocks laid out; the rest of a huge document is dropped (the 1 Mo cap already applies). */
    const val MAX_BLOCKS = 20_000

    /**
     * Blocks of [markdown]: raw HTML is kept as literal text, links are styled and followed by their address
     * (never opened from here), images become "[image : alt]". Throws [TooDeep] for hostile nesting.
     */
    fun parse(markdown: String): List<MdBlock> {
        if (Nesting.markdownDepth(markdown) > Nesting.MAX_DEPTH) throw TooDeep()
        val document = try {
            Parser.builder().build().parse(markdown)
        } catch (e: StackOverflowError) {
            throw TooDeep()
        }
        return blocks(document)
    }

    private class Frame(val node: Node, val quote: Int, val indent: Int, val bullet: String?)

    private fun blocks(document: Node): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val stack = ArrayDeque<Frame>()
        pushChildren(stack, document, 0, 0)
        while (stack.isNotEmpty() && out.size < MAX_BLOCKS) {
            val f = stack.removeLast()
            when (val n = f.node) {
                is Heading -> inline(n).let { (text, spans) ->
                    out.add(MdBlock(MdBlockKind.HEADING, text, spans, level = n.level.coerceIn(1, 6), quote = f.quote, indent = f.indent))
                }
                is Paragraph -> inline(n).let { (text, spans) ->
                    out.add(MdBlock(MdBlockKind.PARAGRAPH, text, spans, quote = f.quote, indent = f.indent, bullet = f.bullet))
                }
                is FencedCodeBlock -> out.add(code(n.literal, f))
                is IndentedCodeBlock -> out.add(code(n.literal, f))
                is HtmlBlock -> out.add(code(n.literal, f))
                is ThematicBreak -> out.add(MdBlock(MdBlockKind.RULE, "", quote = f.quote, indent = f.indent))
                is BlockQuote -> pushChildren(stack, n, f.quote + 1, f.indent)
                is BulletList, is OrderedList -> {
                    val ordered = n as? OrderedList
                    @Suppress("DEPRECATION")
                    var number = ordered?.startNumber ?: 1
                    val items = ArrayList<Frame>()
                    var item = n.firstChild
                    while (item != null) {
                        val bullet = if (ordered != null) "${number++}." else "•"
                        items.add(Frame(item, f.quote, f.indent + 1, bullet))
                        item = item.next
                    }
                    for (i in items.indices.reversed()) stack.addLast(items[i])
                }
                is ListItem -> {
                    // The first child carries the bullet; the following ones are only indented.
                    val children = ArrayList<Node>()
                    var c = n.firstChild
                    while (c != null) {
                        children.add(c)
                        c = c.next
                    }
                    if (children.isEmpty()) out.add(MdBlock(MdBlockKind.PARAGRAPH, "", quote = f.quote, indent = f.indent, bullet = f.bullet))
                    for (i in children.indices.reversed()) {
                        val child = children[i]
                        val nested = child is BulletList || child is OrderedList
                        stack.addLast(Frame(child, f.quote, if (nested) f.indent else f.indent, if (i == 0 && !nested) f.bullet else null))
                    }
                }
                else -> pushChildren(stack, n, f.quote, f.indent)
            }
        }
        return out
    }

    private fun pushChildren(stack: ArrayDeque<Frame>, parent: Node, quote: Int, indent: Int) {
        val children = ArrayList<Node>()
        var c = parent.firstChild
        while (c != null) {
            children.add(c)
            c = c.next
        }
        for (i in children.indices.reversed()) stack.addLast(Frame(children[i], quote, indent, null))
    }

    private fun code(literal: String?, f: Frame): MdBlock =
        MdBlock(MdBlockKind.CODE, literal.orEmpty().trimEnd('\n'), quote = f.quote, indent = f.indent)

    /** Text and spans of a block's inline content, walked with an explicit stack (enter / exit events). */
    private fun inline(block: Node): Pair<String, List<MdSpan>> {
        val sb = StringBuilder()
        val spans = ArrayList<MdSpan>()
        // Pair(node, entering): on exit, the span opened at entry is closed.
        val stack = ArrayDeque<Pair<Node, Boolean>>()
        val starts = HashMap<Node, Int>()
        fun pushChildren(parent: Node) {
            val list = ArrayList<Node>()
            var c = parent.firstChild
            while (c != null) {
                list.add(c)
                c = c.next
            }
            for (i in list.indices.reversed()) stack.addLast(list[i] to true)
        }
        pushChildren(block)
        while (stack.isNotEmpty()) {
            val (node, entering) = stack.removeLast()
            if (!entering) {
                val start = starts.remove(node) ?: continue
                when (node) {
                    is StrongEmphasis -> spans.add(MdSpan(start, sb.length, MdStyle.BOLD))
                    is Emphasis -> spans.add(MdSpan(start, sb.length, MdStyle.ITALIC))
                    is Link -> {
                        spans.add(MdSpan(start, sb.length, MdStyle.LINK))
                        val url = node.destination.orEmpty()
                        if (url.isNotEmpty() && url != sb.substring(start)) {
                            val s = sb.length
                            sb.append(" (").append(url).append(')')
                            spans.add(MdSpan(s, sb.length, MdStyle.URL))
                        }
                    }
                    is Image -> {
                        sb.append(']')
                        spans.add(MdSpan(start, sb.length, MdStyle.ITALIC))
                    }
                }
                continue
            }
            when (node) {
                is Text -> sb.append(node.literal)
                is Code -> {
                    val s = sb.length
                    sb.append(node.literal)
                    spans.add(MdSpan(s, sb.length, MdStyle.CODE))
                }
                is HtmlInline -> sb.append(node.literal)
                is SoftLineBreak -> sb.append(' ')
                is HardLineBreak -> sb.append('\n')
                is StrongEmphasis, is Emphasis, is Link, is Image -> {
                    if (node is Image) sb.append("[image : ")
                    starts[node] = if (node is Image) sb.length - "[image : ".length else sb.length
                    stack.addLast(node to false)
                    pushChildren(node)
                }
                else -> pushChildren(node)
            }
        }
        return sb.toString() to spans
    }
}

object JsonPretty {
    /** Indentation stops growing past this depth (a deep file would otherwise be mostly spaces). */
    const val MAX_INDENT_DEPTH = 32

    /** Longest re-indented text, in characters (16 Mo of heap in a StringBuilder). */
    const val MAX_OUTPUT_CHARS = 8 * 1024 * 1024

    /** Re-indented text may be at most this many times the source (or [MIN_OUTPUT_ROOM] for a small file). */
    const val MAX_GROWTH = 4

    private const val MIN_OUTPUT_ROOM = 64 * 1024

    /** Most characters [reindent] may write for a source of [length] characters. */
    fun outputLimit(length: Int): Int = minOf(MAX_OUTPUT_CHARS.toLong(), maxOf(MAX_GROWTH.toLong() * length, MIN_OUTPUT_ROOM.toLong())).toInt()

    /**
     * [text] re-indented with [indent] spaces when it is one valid JSON value (checked with org.json, after the
     * nesting check); null when it is not (invalid, cut, JSON Lines, too deep) or when the indentation would
     * blow it up past [outputLimit]: show it as it is. The original text is kept token for token (key order,
     * number spelling, escapes): only whitespace changes.
     */
    fun format(text: String, indent: Int = 2): String? {
        if (Nesting.jsonDepth(text) > Nesting.MAX_DEPTH) return null
        val valid = try {
            val tokener = JSONTokener(text)
            tokener.nextValue()
            // Nothing but whitespace after the value (JSON Lines and trailing junk are not one value).
            tokener.nextClean() == '\u0000'
        } catch (e: JSONException) {
            false
        } catch (e: StackOverflowError) {
            false
        } catch (e: RuntimeException) {
            false
        }
        return if (valid) reindent(text, indent) else null
    }

    /**
     * Whitespace outside strings replaced by newlines and indentation (no parsing, no recursion). Indentation
     * stops at [MAX_INDENT_DEPTH] levels; null as soon as the result would pass [outputLimit] characters.
     */
    fun reindent(text: String, indent: Int = 2): String? {
        val limit = outputLimit(text.length)
        val sb = StringBuilder(minOf(text.length + text.length / 4, limit))
        var depth = 0
        var inString = false
        var escaped = false
        var i = 0
        fun newline() {
            sb.append('\n')
            repeat(minOf(depth, MAX_INDENT_DEPTH) * indent) { sb.append(' ') }
        }
        while (i < text.length) {
            if (sb.length > limit) return null
            val c = text[i]
            if (inString) {
                sb.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> {
                    inString = true
                    sb.append(c)
                }
                '{', '[' -> {
                    // Empty containers stay on one line: {} and [].
                    var j = i + 1
                    while (j < text.length && text[j].isWhitespace()) j++
                    val close = if (c == '{') '}' else ']'
                    if (j < text.length && text[j] == close) {
                        sb.append(c).append(close)
                        i = j + 1
                        continue
                    }
                    sb.append(c)
                    depth++
                    newline()
                }
                '}', ']' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    newline()
                    sb.append(c)
                }
                ',' -> {
                    sb.append(c)
                    newline()
                }
                ':' -> sb.append(": ")
                ' ', '\t', '\n', '\r' -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return if (sb.length > limit) null else sb.toString()
    }
}
