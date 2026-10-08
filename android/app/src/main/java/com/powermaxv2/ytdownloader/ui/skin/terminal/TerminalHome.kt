package com.powermaxv2.ytdownloader.ui.skin.terminal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.hub.GulpTool
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.hub.toolNumber
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance

// Home screen of the hub in the Terminal skin: `ls ~/outils` in a console, each tool a line of the listing.

private const val HOME_USER = "gulp@android"
private const val HOME_COMMAND = ":~$ ls outils/"

/** Typed prompt and command, "OUTILS_" in big letters with a glowing cursor, then the count as a log line. */
@Composable
fun TerminalHomeHeader(ready: Int, upcoming: Int, onSettings: () -> Unit) {
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val prompt by rememberTyped(HOME_USER + HOME_COMMAND, key = Unit, charMs = 34, delayMs = 150L)
    val title = "OUTILS"
    val count by rememberTyped(title, key = Unit, charMs = 70, delayMs = 700L)
    val markStyle = remember(accent) { GulpStyle.outline(accent) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GulpMark(18.dp, style = markStyle)
                Spacer(Modifier.width(8.dp))
                Text(HOME_USER.take(prompt), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(
                    HOME_COMMAND.take((prompt - HOME_USER.length).coerceAtLeast(0)),
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(3.dp))
                Box(
                    Modifier
                        .size(width = 7.dp, height = 13.dp)
                        .blinking()
                        .background(accent),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title.take(count),
                    color = Palette.TextPrimary,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontFamily = tokens.display,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    "_",
                    color = accent,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontFamily = tokens.display,
                    maxLines = 1,
                    style = TextStyle(shadow = Shadow(color = accent.copy(alpha = 0.7f), offset = Offset.Zero, blurRadius = 18f)),
                    modifier = Modifier.blinking(),
                )
            }
            Spacer(Modifier.height(4.dp))
            TermLabel("> $ready PRÊT · $upcoming EN COMPILATION", color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .padding(top = 2.dp)
                .bounceClick(pressedScale = 0.9f, onClick = onSettings)
                .size(48.dp)
                .termPanel(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Menu, contentDescription = "Réglages", tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

/** "telechargeur-video" for "Téléchargeur vidéo": the name as a console would list it. */
private fun slug(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')

/** A tool that is out: accent-outlined block with its listing line, a blinking "PRÊT", tags and the "OUVRIR" button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalToolCard(tool: GulpTool, index: Int, onOpen: () -> Unit) {
    val accent = LocalAccent.current.start
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    val line = "./" + slug(tool.name)
    val typed by rememberTyped(line, key = tool.id, charMs = 30, delayMs = 500L + index * 150L)
    Column(
        Modifier
            .staggeredEntrance(index, baseDelay = 300)
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, accent, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[${toolNumber(index + 1)}]", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                line.take(typed),
                color = Palette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(7.dp)
                    .blinking(900)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(6.dp))
            Text("PRÊT", color = accent, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(tool.name.uppercase(), color = Palette.TextPrimary, fontSize = 22.sp, lineHeight = 27.sp, fontFamily = LocalTokens.current.display)
        Spacer(Modifier.height(4.dp))
        Text("# ${tool.tagline}", color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val icon = tool.icon
        if (tool.platforms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tool.platforms.forEachIndexed { i, platform ->
                    Box(
                        Modifier
                            .bob(i, amplitude = 2.dp)
                            .size(36.dp)
                            .termPanel(),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlatformGlyph(platform, Modifier.size(19.dp))
                    }
                }
            }
        } else if (icon != null) {
            // A tool that is not about a website: its symbol in phosphor, floating in its cell.
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .bob(index, amplitude = 2.dp)
                    .size(40.dp)
                    .termPanel(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        if (tool.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tool.tags.forEach { TermTag(it, fontSize = 11.sp, height = 28.dp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        TermButton("OUVRIR >", Modifier.fillMaxWidth(), big = true, subtitle = "./${slug(tool.name)} --start", onClick = onOpen)
    }
}

/** "À venir": dimmed listing lines, each with a block progress bar that scans back and forth ("en compilation"). */
@Composable
fun TerminalComingSoon(tools: List<GulpTool>, firstNumber: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TermLabel(
            "> À VENIR",
            modifier = Modifier
                .padding(top = 8.dp, bottom = 2.dp)
                .staggeredEntrance(firstNumber, baseDelay = 300),
        )
        tools.forEachIndexed { i, tool -> TerminalSoonLine(tool, firstNumber + i) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 6.dp)
                .staggeredEntrance(firstNumber + tools.size, baseDelay = 300),
        ) {
            Text("# d'autres outils arrivent avec les mises à jour", color = Palette.TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TerminalSoonLine(tool: GulpTool, number: Int) {
    val accent = LocalAccent.current.start
    val cells = 10
    val transition = rememberInfiniteTransition(label = "compile")
    // A 3-cell bright block runs along the bar, out of step from one line to the next.
    val scan by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800 + number * 170, easing = LinearEasing)), label = "compileScan",
    )
    Row(
        Modifier
            .staggeredEntrance(number, baseDelay = 300)
            .fillMaxWidth()
            .termPanel()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("[${toolNumber(number)}]", color = Palette.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.name.lowercase(), color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row {
                val head = (scan * (cells + 3)).toInt() - 3
                repeat(cells) { i ->
                    val lit = i in head until head + 3
                    Text(
                        if (lit) "▓" else "░",
                        color = if (lit) accent else Palette.TextTertiary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.graphicsLayer { alpha = if (lit) 1f else 0.7f },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("EN COMPIL.", color = Palette.TextTertiary, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.blinking(1400))
    }
}

/** Group title of the home screen, typed like a log line ("> DERNIER UTILISÉ"). */
@Composable
fun TerminalHomeSection(title: String, order: Int) {
    TermLabel(
        "> " + title.uppercase(),
        modifier = Modifier
            .padding(top = 4.dp)
            .staggeredEntrance(order, baseDelay = 300),
    )
}

/**
 * Small cell of a tool (half a card): its number and phosphor symbol, the name, and "> ouvrir" with a blinking
 * caret. A tap inverts the cell for a flash (via the press bounce) and opens the tool.
 */
@Composable
fun TerminalToolBubble(tool: GulpTool, index: Int, order: Int, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val accent = LocalAccent.current.start
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    val line = slug(tool.name)
    val typed by rememberTyped(line, key = tool.id, charMs = 30, delayMs = 500L + order * 120L)
    Column(
        modifier
            .staggeredEntrance(order, baseDelay = 300)
            .bounceClick(pressedScale = 0.94f, onClick = onOpen)
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[${toolNumber(index + 1)}]", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .bob(order, amplitude = 2.dp)
                    .size(34.dp)
                    .termPanel(),
                contentAlignment = Alignment.Center,
            ) {
                val icon = tool.icon
                val platform = tool.platforms.firstOrNull()
                when {
                    tool.platforms.isEmpty() && icon != null -> Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
                    platform != null -> PlatformGlyph(platform, Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tool.name.uppercase(),
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontFamily = LocalTokens.current.display,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text("./" + line.take(typed), color = Palette.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("> OUVRIR", color = accent, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(width = 7.dp, height = 13.dp)
                    .blinking()
                    .background(accent),
            )
        }
    }
}
