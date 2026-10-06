package com.powermaxv2.ytdownloader.ui.tools.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.convert.CsvTable
import com.powermaxv2.ytdownloader.core.preview.Highlighter
import com.powermaxv2.ytdownloader.core.preview.MdBlock
import com.powermaxv2.ytdownloader.core.preview.MdBlockKind
import com.powermaxv2.ytdownloader.core.preview.MdStyle
import com.powermaxv2.ytdownloader.core.preview.PreviewBody
import com.powermaxv2.ytdownloader.core.preview.PreviewText
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// Text viewers of the Aperçu: highlighted code with line numbers, wrapping text, rendered Markdown, CSV table.
// Each scrolls inside a fixed-height frame (LazyColumn: a 1 Mo file stays smooth).

/** Height of a viewer frame: about 60 % of the screen. */
@Composable
internal fun viewerHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.6f).dp.coerceIn(300.dp, 640.dp)

/** The frame every viewer draws in: a tool card, clipped. */
@Composable
internal fun ViewerFrame(modifier: Modifier = Modifier, height: Dp = viewerHeight(), content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .toolCard(),
    ) { content() }
}

/** Small strip at the top of a viewer: "KOTLIN · 42 lignes · UTF-8". */
@Composable
internal fun ViewerStrip(text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface2)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            if (terminal) "-- ${text.lowercase()}" else text,
            color = Palette.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val codeStyle = TextStyle(fontFamily = MonoFamily, fontSize = 12.5.sp, lineHeight = 19.sp)

/** Width in px of one character of [style] (monospace). */
@Composable
private fun charWidthPx(style: TextStyle): Float {
    val measurer = rememberTextMeasurer()
    return remember(style) { measurer.measure("0".repeat(20), style).size.width / 20f }
}

// MARK: - Code

@Composable
internal fun CodeViewer(body: PreviewBody.Code, modifier: Modifier = Modifier) {
    val colors = syntaxColors()
    val lines = body.lineStarts.size
    val digits = lines.toString().length.coerceAtLeast(2)
    val charW = charWidthPx(codeStyle)
    val density = LocalDensity.current
    ViewerFrame(modifier) {
        Column(Modifier.fillMaxSize()) {
            ViewerStrip("${body.lang.label} · ${lineCount(lines)} · ${body.charset}")
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gutterPx = charW * (digits + 2)
                val contentPx = gutterPx + charW * (body.longestLine + 3)
                val width = with(density) { maxOf(contentPx.toDp() + 24.dp, maxWidth) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    LazyColumn(
                        Modifier
                            .width(width)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(lines) { index ->
                            val line = remember(body, index) { highlightedLine(body, index, colors) }
                            Row(Modifier.padding(end = 12.dp)) {
                                Text(
                                    (index + 1).toString().padStart(digits),
                                    style = codeStyle,
                                    color = colors.gutter,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                                )
                                Text(line, style = codeStyle, color = colors.plain, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lineCount(n: Int): String = if (n == 1) "1 ligne" else "$n lignes"

/** Line [index] of [body] with its tokens coloured; lines over [PreviewText.MAX_LINE_CHARS] are cut with "…". */
private fun highlightedLine(body: PreviewBody.Code, index: Int, colors: SyntaxColors): AnnotatedString {
    val start = body.lineStarts[index]
    val fullEnd = Highlighter.lineEnd(body.text, body.lineStarts, index)
    val end = minOf(fullEnd, start + PreviewText.MAX_LINE_CHARS)
    val spans = Highlighter.spansIn(body.tokens, start, end)
    return buildAnnotatedString {
        append(body.text, start, end)
        if (end < fullEnd) append(" …")
        for (t in spans) {
            val style = when (t.type) {
                com.powermaxv2.ytdownloader.core.preview.TokenType.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                com.powermaxv2.ytdownloader.core.preview.TokenType.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold)
                else -> SpanStyle(color = colors.of(t.type))
            }
            addStyle(style, t.start, t.end)
        }
    }
}

// MARK: - Plain text

@Composable
internal fun TextViewer(body: PreviewBody.Text, modifier: Modifier = Modifier) {
    ViewerFrame(modifier) {
        Column(Modifier.fillMaxSize()) {
            ViewerStrip("Texte · ${lineCount(body.rows.size)} · ${body.charset}")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                items(body.rows.size) { i ->
                    Text(
                        body.rows[i].ifEmpty { " " },
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

// MARK: - Markdown

@Composable
internal fun MarkdownViewer(body: PreviewBody.Markdown, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val codeBg = Palette.Surface2
    val tertiary = Palette.TextTertiary
    ViewerFrame(modifier) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(body.blocks) { _, block ->
                MarkdownBlockView(block, accent, codeBg, tertiary, tokens.display)
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MdBlock,
    accent: androidx.compose.ui.graphics.Color,
    codeBg: androidx.compose.ui.graphics.Color,
    tertiary: androidx.compose.ui.graphics.Color,
    display: androidx.compose.ui.text.font.FontFamily,
) {
    val quoteColor = accent.copy(alpha = 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (block.indent.coerceAtMost(8) * 14).dp)
            .drawBehind {
                // One vertical bar per quote level.
                for (q in 0 until block.quote.coerceAtMost(8)) {
                    val x = q * 10.dp.toPx() + 1.5.dp.toPx()
                    drawLine(quoteColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                }
            }
            .padding(start = (block.quote.coerceAtMost(8) * 10 + if (block.quote > 0) 6 else 0).dp),
    ) {
        block.bullet?.let {
            Text(it, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        }
        when (block.kind) {
            MdBlockKind.HEADING -> Text(
                styled(block, accent, codeBg, tertiary),
                color = Palette.TextPrimary,
                fontSize = when (block.level) {
                    1 -> 24.sp
                    2 -> 20.sp
                    3 -> 17.sp
                    else -> 15.sp
                },
                lineHeight = when (block.level) {
                    1 -> 29.sp
                    2 -> 25.sp
                    else -> 21.sp
                },
                fontWeight = FontWeight.ExtraBold,
                fontFamily = display,
                modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
            )
            MdBlockKind.PARAGRAPH -> Text(
                styled(block, accent, codeBg, tertiary),
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            MdBlockKind.CODE -> Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalTokens.current.fieldRadius.coerceAtMost(12.dp)))
                    .background(codeBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    block.text.take(20_000),
                    style = codeStyle.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = Palette.TextPrimary,
                    softWrap = false,
                )
            }
            MdBlockKind.RULE -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(1.dp)
                    .background(Palette.Stroke),
            )
        }
    }
}

/** A block's text with its spans: bold, italic, code, links (accent, underlined, never opened) and their address. */
private fun styled(
    block: MdBlock,
    accent: androidx.compose.ui.graphics.Color,
    codeBg: androidx.compose.ui.graphics.Color,
    tertiary: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    append(block.text)
    for (s in block.spans) {
        if (s.start < 0 || s.end > block.text.length || s.end <= s.start) continue
        val style = when (s.style) {
            MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MdStyle.CODE -> SpanStyle(fontFamily = MonoFamily, background = codeBg, fontSize = 13.sp)
            MdStyle.LINK -> SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            MdStyle.URL -> SpanStyle(color = tertiary, fontSize = 12.sp)
        }
        addStyle(style, s.start, s.end)
    }
}

// MARK: - CSV

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TableViewer(body: PreviewBody.Table, modifier: Modifier = Modifier) {
    val table = body.table
    val style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val charW = charWidthPx(TextStyle(fontFamily = MonoFamily, fontSize = 13.sp))
    val density = LocalDensity.current
    val widths = remember(table) { columnChars(table) }
    val cellPad = 10.dp
    val colWidths = widths.map { with(density) { (charW * it).toDp() } + cellPad * 2 }
    val numberWidth = with(density) { (charW * (table.rows.size.toString().length + 1)).toDp() } + 12.dp
    val header = table.rows.firstOrNull()
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val stripe = Palette.TextPrimary.copy(alpha = 0.035f)
    val delimiter = when (table.delimiter) {
        '\t' -> "tabulation"
        ';' -> "point-virgule"
        '|' -> "barre"
        else -> "virgule"
    }
    ViewerFrame(modifier) {
        Column(Modifier.fillMaxSize()) {
            ViewerStrip("CSV · ${table.rows.size} lignes × ${table.columnCount} colonnes · séparateur $delimiter")
            if (header == null) {
                Text("Tableau vide.", color = Palette.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                return@Column
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
            ) {
                val total = numberWidth + colWidths.fold(0.dp) { a, b -> a + b }
                LazyColumn(Modifier.width(total).fillMaxHeight()) {
                    stickyHeader {
                        Row(
                            Modifier
                                .background(Palette.Surface)
                                .background(accent.copy(alpha = if (terminal) 0.10f else 0.12f)),
                        ) {
                            Spacer(Modifier.width(numberWidth))
                            for (c in widths.indices) {
                                Cell(header.getOrElse(c) { "" }, colWidths[c], cellPad, style.copy(fontWeight = FontWeight.Bold), Palette.TextPrimary)
                            }
                        }
                    }
                    items(table.rows.size - 1) { i ->
                        val row = table.rows[i + 1]
                        Row(Modifier.background(if (i % 2 == 1) stripe else androidx.compose.ui.graphics.Color.Transparent)) {
                            Text(
                                (i + 1).toString(),
                                style = style.copy(fontFamily = MonoFamily, fontSize = 11.sp),
                                color = Palette.TextTertiary,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(numberWidth)
                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                            )
                            for (c in widths.indices) {
                                Cell(row.getOrElse(c) { "" }, colWidths[c], cellPad, style, Palette.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Dp, pad: Dp, style: TextStyle, color: androidx.compose.ui.graphics.Color) {
    Text(
        text.replace('\n', ' '),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = pad, vertical = 8.dp),
    )
}

/** Width of each column in characters: its longest cell among the first rows, between 3 and 28. */
private fun columnChars(table: CsvTable): List<Int> {
    val n = table.columnCount.coerceAtMost(200)
    val out = IntArray(n) { 3 }
    for (row in table.rows.take(200)) {
        for (c in 0 until minOf(n, row.size)) {
            val len = row[c].length.coerceAtMost(28)
            if (len > out[c]) out[c] = len
        }
    }
    return out.toList()
}
