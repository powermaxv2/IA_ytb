package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.hub.AllToolsIcon
import com.powermaxv2.ytdownloader.ui.hub.ClearPop
import com.powermaxv2.ytdownloader.ui.hub.RollingHint
import com.powermaxv2.ytdownloader.ui.hub.SearchField
import com.powermaxv2.ytdownloader.ui.hub.ToolCategory
import com.powermaxv2.ytdownloader.ui.hub.focusedState
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.PI
import kotlin.math.sin

// Search bar and category chips of the home screen in the Papier skin: an index card and ink tabs.

/** White card with an ink magnifier that circles a little; the accent pen underlines the field with the focus. */
@Composable
fun PaperSearchBar(query: String, onQueryChange: (String) -> Unit, suggestions: List<String>, onSubmit: () -> Unit = {}) {
    val accent = LocalAccent.current.start
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.focusedState()
    val line by animateFloatAsState(if (focused || query.isNotEmpty()) 1f else 0f, tween(420), label = "paperSearchLine")
    val orbit = if (query.isEmpty()) {
        val transition = rememberInfiniteTransition(label = "paperSearch")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "paperOrbit").value
    } else {
        0f
    }
    Column(Modifier.staggeredEntrance(1, baseDelay = 160)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .paperCard(RoundedCornerShape(18.dp))
                .drawBehind {
                    // Pen stroke drawn from the left under the text when the field is in use.
                    if (line > 0f) {
                        val y = size.height - 7.dp.toPx()
                        val x0 = 62.dp.toPx()
                        drawLine(accent, Offset(x0, y), Offset(x0 + (size.width - x0 - 20.dp.toPx()) * line, y), 3.dp.toPx(), StrokeCap.Round)
                    }
                }
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.TextPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = GulpColors.Cream,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            // Small circles, like a loupe moving over the page.
                            if (query.isEmpty()) {
                                val a = orbit * 2f * PI.toFloat()
                                translationX = sin(a) * 2.dp.toPx()
                                translationY = sin(a + PI.toFloat() / 2) * 2.dp.toPx()
                            }
                        },
                )
            }
            Spacer(Modifier.width(12.dp))
            val style = TextStyle(color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = LocalTokens.current.display)
            SearchField(query, onQueryChange, style, accent, interaction, Modifier.weight(1f), onSubmit = onSubmit) {
                RollingHint("Chercher ", suggestions, style.copy(color = Palette.TextTertiary, fontWeight = FontWeight.Normal), Palette.TextTertiary)
            }
            ClearPop(query.isNotEmpty()) {
                Box(
                    Modifier
                        .bounceClick(pressedScale = 0.85f) { onQueryChange("") }
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Palette.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = Palette.TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Ink tabs that slide sideways: the picked one is stamped in ink, with its count in the accent. */
@Composable
fun PaperCategoryChips(
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
            // Room for the stamped (tilted, bigger) chip, so the scroll edge does not cut it.
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaperChip("Tout", AllToolsIcon, total, selected == null, order = 0) { onSelect(null) }
        categories.forEachIndexed { i, c ->
            PaperChip(c.label, c.icon, counts[c] ?: 0, selected == c, order = i + 1) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
private fun PaperChip(label: String, icon: ImageVector, count: Int, picked: Boolean, order: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    val ink = Palette.TextPrimary
    val stamp by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "paperChipStamp")
    val fill by animateColorAsState(if (picked) ink else Palette.Surface, tween(200), label = "paperChipFill")
    val text by animateColorAsState(if (picked) GulpColors.Cream else ink, tween(200), label = "paperChipText")
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .staggeredEntrance(order, baseDelay = 240)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = picked
                contentDescription = "$label, $count outils"
            }
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .graphicsLayer {
                // Stamped: slight tilt that settles with a bounce.
                rotationZ = stamp * (if (order % 2 == 0) -2f else 2f)
                val s = 1f + 0.04f * stamp
                scaleX = s
                scaleY = s
            }
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, ink.copy(alpha = if (picked) 1f else 0.14f), shape)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (picked) GulpColors.Cream else accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count", color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
    }
}
