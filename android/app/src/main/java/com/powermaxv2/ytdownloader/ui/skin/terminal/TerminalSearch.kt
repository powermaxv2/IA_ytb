package com.powermaxv2.ytdownloader.ui.skin.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.hub.ClearPop
import com.powermaxv2.ytdownloader.ui.hub.RollingHint
import com.powermaxv2.ytdownloader.ui.hub.SearchField
import com.powermaxv2.ytdownloader.ui.hub.ToolCategory
import com.powermaxv2.ytdownloader.ui.hub.focusedState
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance

// Search bar and category chips of the home screen in the Terminal skin: a `grep` prompt and `--flags`.

/** "$ grep -i" prompt; the hint types tool names behind a blinking block caret, the outline glows with the focus. */
@Composable
fun TerminalSearchBar(query: String, onQueryChange: (String) -> Unit, suggestions: List<String>, onSubmit: () -> Unit = {}) {
    val accent = LocalAccent.current.start
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.focusedState()
    val glow by animateFloatAsState(if (focused || query.isNotEmpty()) 1f else 0f, tween(260), label = "termSearchGlow")
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    val prompt by rememberTyped("$ grep -i ", key = Unit, charMs = 40, delayMs = 400L)
    Row(
        Modifier
            .staggeredEntrance(1, baseDelay = 200)
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                // Phosphor halo under the panel while searching.
                if (glow > 0f) drawRect(accent.copy(alpha = 0.10f * glow), topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()), size = size.copy(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()))
            }
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, accent.copy(alpha = 0.35f + 0.65f * glow), shape)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$ grep -i ".take(prompt), color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        val style = TextStyle(
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            fontFamily = LocalTokens.current.body,
            shadow = Shadow(accent.copy(alpha = 0.5f * glow), Offset.Zero, 8f),
        )
        SearchField(query, onQueryChange, style, accent, interaction, Modifier.weight(1f), onSubmit = onSubmit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingHint("", suggestions.map { it.lowercase() }, style.copy(color = Palette.TextTertiary, shadow = null), Palette.TextTertiary, quote = { "\"$it\"" })
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(width = 8.dp, height = 15.dp).blinking().background(accent))
            }
        }
        ClearPop(query.isNotEmpty()) {
            Text(
                "[x]",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .semantics {
                        contentDescription = "Effacer"
                        role = Role.Button
                    }
                    .bounceClick(pressedScale = 0.85f) { onQueryChange("") }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

/** "--tout  --medias(3)  --fichiers(5)": the picked flag inverts (accent block, dark text), like a selection. */
@Composable
fun TerminalCategoryChips(
    categories: List<ToolCategory>,
    counts: Map<ToolCategory, Int>,
    total: Int,
    selected: ToolCategory?,
    onSelect: (ToolCategory?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // Room for the picked chip, which grows a little, so the scroll edge does not cut it.
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TerminalFlag("tout", "Tout", total, selected == null, order = 0) { onSelect(null) }
        categories.forEachIndexed { i, c ->
            TerminalFlag(flagName(c.label), c.label, counts[c] ?: 0, selected == c, order = i + 1) { onSelect(if (selected == c) null else c) }
        }
    }
}

/** "Médias" → "medias". */
private fun flagName(label: String): String = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()

@Composable
private fun TerminalFlag(name: String, spokenLabel: String, count: Int, picked: Boolean, order: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    val dark = Palette.Background
    val fill by animateColorAsState(if (picked) accent else Palette.Surface, tween(160), label = "termFlagFill")
    val ink by animateColorAsState(if (picked) dark else accent, tween(160), label = "termFlagInk")
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val line = "--$name($count)"
    val typed by rememberTyped(line, key = name, charMs = 26, delayMs = 500L + order * 110L)
    Row(
        Modifier
            .staggeredEntrance(order, baseDelay = 260)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = picked
                contentDescription = "$spokenLabel, $count outils"
            }
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .height(36.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, accent.copy(alpha = if (picked) 1f else 0.45f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Typed in at first, then stays whole; the width is reserved so the row does not jump while typing.
        Box {
            Text(line, color = ink.copy(alpha = 0f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(line.take(typed), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        if (picked) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = 7.dp, height = 13.dp).blinking().background(dark))
        }
    }
}
