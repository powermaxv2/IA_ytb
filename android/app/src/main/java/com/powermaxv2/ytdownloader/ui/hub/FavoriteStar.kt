package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.tick
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The star of a tool on the home screen: a touch pins the tool in "Favoris" at the top, a second one takes it off.
// Starring it spins the star in with an overshoot and throws a ring of sparks; each skin draws it its own way.

/** Name of the tool just starred: its star, drawn anew in "Favoris", plays the starring instead of sitting still. */
internal val LocalJustStarred = compositionLocalOf<String?> { null }

/** Gold of a starred tool in the Gulp skin. */
private val Gold = Color(0xFFFFC53D)

/** Star of [toolName]: hollow, or filled when [starred]; [onToggle] flips it. */
@Composable
fun FavoriteStar(starred: Boolean, toolName: String, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val pop = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    // Only a change plays: the first drawing of the screen keeps the stars still.
    val fresh = starred && LocalJustStarred.current == toolName
    var shown by remember { mutableStateOf(if (fresh) false else starred) }
    LaunchedEffect(starred) {
        if (starred == shown) return@LaunchedEffect
        shown = starred
        if (starred) {
            burst.snapTo(0f)
            pop.snapTo(0.35f)
            spin.snapTo(-144f)
            coroutineScope {
                launch { burst.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
                launch { spin.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
                pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
            }
        } else {
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
    }
    // A starred tool's star glints now and then (classic and paper): a slow breath of its size.
    val transition = rememberInfiniteTransition(label = "star")
    val glint by transition.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "starGlint",
    )
    val label = if (starred) "Retirer $toolName des favoris" else "Ajouter $toolName aux favoris"
    Box(
        modifier
            .size(size + 8.dp)
            .semantics { contentDescription = label }
            .toggleable(
                value = starred,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
            ) {
                view.tick(haptics)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        val sparks = when (skin) {
            Skin.CLASSIC -> Gold
            Skin.PAPER -> accent.start
            Skin.TERMINAL -> accent.start
        }
        // Ring of sparks behind the star, thrown outwards when it gets starred.
        Canvas(Modifier.size(size + 8.dp)) {
            val p = burst.value
            if (p < 1f) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val reach = this.size.minDimension * (0.25f + 0.55f * p)
                repeat(8) { i ->
                    val a = (i / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2
                    val r = (if (i % 2 == 0) 2.2f else 1.5f).dp.toPx() * (1f - p * 0.6f)
                    drawCircle(sparks.copy(alpha = 1f - p), r, center + Offset(cos(a) * reach, sin(a) * reach))
                }
            }
        }
        val layer = Modifier.graphicsLayer {
            val s = pop.value * (if (starred && skin != Skin.TERMINAL) glint else 1f)
            scaleX = s
            scaleY = s
            rotationZ = spin.value
        }
        when (skin) {
            Skin.TERMINAL -> Text(
                if (starred) "[*]" else "[ ]",
                color = if (starred) accent.start else Palette.TextTertiary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = layer,
            )
            Skin.CLASSIC -> Canvas(layer.size(size * 0.62f)) {
                if (starred) {
                    drawStar(Brush.linearGradient(listOf(Gold, accent.end), Offset.Zero, Offset(this.size.width, this.size.height)), Fill)
                } else {
                    drawStar(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.35f))), Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
                }
            }
            Skin.PAPER -> {
                val ink = Palette.TextPrimary
                Canvas(layer.size(size * 0.62f)) {
                    if (starred) drawStar(Brush.linearGradient(listOf(accent.start, accent.start)), Fill)
                    drawStar(Brush.linearGradient(listOf(ink, ink)), Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** Spinning dashed halo around the "Favoris" title star, so the section reads as alive. */
@Composable
fun FavoritesGlyph(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "favGlyph")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "favGlyphTurn")
    if (skin == Skin.TERMINAL) {
        Text("[*]", color = accent.start, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = modifier)
        return
    }
    val fill = if (skin == Skin.CLASSIC) Brush.linearGradient(listOf(Gold, accent.end)) else Brush.linearGradient(listOf(accent.start, accent.start))
    Canvas(modifier.size(size).graphicsLayer { rotationZ = turn }) { drawStar(fill, Fill) }
}

/** A five-pointed star filling the drawing area. */
private fun DrawScope.drawStar(brush: Brush, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val cx = size.width / 2
    val cy = size.height / 2 + size.height * 0.04f
    val outer = size.minDimension / 2
    val inner = outer * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = cx + (cos(a) * r).toFloat()
        val y = cy + (sin(a) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, brush, style = style)
}
