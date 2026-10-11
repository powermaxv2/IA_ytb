package com.powermaxv2.ytdownloader.ui.skin.classic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
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

// Search bar and category chips of the home screen in the Gulp skin.

/**
 * Dark pill with a gradient magnifier that sways while it waits; a tool name rolls in the hint. With the focus,
 * the brand gradient starts running around the outline.
 */
@Composable
fun ClassicSearchBar(query: String, onQueryChange: (String) -> Unit, suggestions: List<String>) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.focusedState()
    val ring by animateFloatAsState(if (focused || query.isNotEmpty()) 1f else 0f, tween(320), label = "searchRing")
    val transition = rememberInfiniteTransition(label = "classicSearch")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "searchAngle")
    val sway by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "searchSway")
    val shape = CircleShape
    Row(
        Modifier
            .staggeredEntrance(1, baseDelay = 160)
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Palette.Stroke)
            .drawBehind {
                // Running gradient outline, faded in with the focus.
                if (ring > 0f) {
                    rotate(angle) {
                        drawCircle(
                            Brush.sweepGradient(listOf(accent.start, accent.end, Color.Transparent, accent.start)),
                            radius = size.maxDimension,
                            alpha = ring,
                        )
                    }
                }
            }
            .padding(1.5.dp)
            .clip(shape)
            .background(Palette.Surface)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    // Looks left and right now and then, as if searching.
                    val s = if (query.isEmpty()) sin(sway * 2f * PI.toFloat()) else 0f
                    rotationZ = s * 14f
                    translationX = s * 1.5.dp.toPx()
                }
                .clip(CircleShape)
                .background(accent.brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        val style = TextStyle(color = Palette.TextPrimary, fontSize = 16.sp, fontFamily = LocalTokens.current.body)
        SearchField(query, onQueryChange, style, accent.start, interaction, Modifier.weight(1f)) {
            RollingHint("Chercher ", suggestions, style.copy(color = Palette.TextTertiary), Palette.TextTertiary)
        }
        ClearPop(query.isNotEmpty()) {
            Box(
                Modifier
                    .bounceClick(pressedScale = 0.85f) { onQueryChange("") }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Row of pills that slides sideways; the picked one fills with the brand gradient and pops. */
@Composable
fun ClassicCategoryChips(
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
        ClassicChip("Tout", AllToolsIcon, total, selected == null, order = 0) { onSelect(null) }
        categories.forEachIndexed { i, c ->
            ClassicChip(c.label, c.icon, counts[c] ?: 0, selected == c, order = i + 1) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
private fun ClassicChip(label: String, icon: ImageVector, count: Int, picked: Boolean, order: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val fill by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "chipFill")
    val text by animateColorAsState(if (picked) Palette.OnAccent else Palette.TextSecondary, tween(220), label = "chipText")
    val transition = rememberInfiniteTransition(label = "chipGlow")
    val glow by transition.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "chipGlowA")
    Row(
        Modifier
            .staggeredEntrance(order, baseDelay = 240)
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .graphicsLayer {
                // Picked: a little bigger, overshooting on the spring.
                val s = 1f + 0.05f * fill
                scaleX = s
                scaleY = s
            }
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.Surface)
            .drawBehind {
                if (fill > 0f) drawRect(accent.horizontal(), alpha = (fill * glow).coerceIn(0f, 1f))
            }
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (picked) Palette.OnAccent else accent.start, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (picked) Color.White.copy(alpha = 0.22f) else Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", color = text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
