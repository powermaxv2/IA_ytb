package com.powermaxv2.ytdownloader.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.skin.BricolageFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The Gulp identity: a play triangle dropping into a mouth that swallows it ("la gorgée").

object GulpColors {
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF8A1F)
    /** The mouth; also the ink of the light skin. */
    val Ink = Color(0xFF2A0F0A)
    val Cream = Color(0xFFFFF4EA)
    val brush: Brush get() = Brush.linearGradient(listOf(Red, Orange))
}

/** How the mark is painted: brand tile (default), or an outline for the Terminal skin. */
@Immutable
data class GulpStyle(
    /** Tile fill; null = no fill. */
    val tile: Brush? = GulpColors.brush,
    /** Tile outline; null = none. */
    val tileOutline: Color? = null,
    val drop: Color = Color.White,
    val mouth: Color = GulpColors.Ink,
) {
    companion object {
        val Brand = GulpStyle()

        /** Just the drop and the mouth, no tile (on a coloured background). */
        fun bare(drop: Color, mouth: Color) = GulpStyle(tile = null, drop = drop, mouth = mouth)

        /** Outlined tile, drop and mouth in one colour. */
        fun outline(color: Color) = GulpStyle(tile = null, tileOutline = color, drop = color, mouth = color)
    }
}

/** Grows each time a download is queued: every Gulp mark on screen swallows once. Provided by AppRoot. */
val LocalGulpPulse = staticCompositionLocalOf { 0 }

/** Value of a keyframed curve at [t] (0..1): smooth (ease-in-out) between the (time, value) points. */
private fun keyframes(t: Float, vararg points: Pair<Float, Float>): Float {
    if (t <= points.first().first) return points.first().second
    for (i in 1 until points.size) {
        val (t1, v1) = points[i]
        if (t <= t1) {
            val (t0, v0) = points[i - 1]
            val x = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
            val eased = x * x * (3f - 2f * x)
            return v0 + (v1 - v0) * eased
        }
    }
    return points.last().second
}

/** Drop offset (in 512-units), drop scale x/y and mouth stretch at [phase] of one gulp (0..1). */
private class GulpPose(phase: Float) {
    val dropY = keyframes(phase, 0f to -14f, 0.18f to -14f, 0.45f to 34f, 0.58f to 22f, 0.8f to -14f)
    val dropScaleX = keyframes(phase, 0f to 1f, 0.18f to 1f, 0.45f to 0.82f, 0.58f to 0.9f, 0.8f to 1f)
    val dropScaleY = keyframes(phase, 0f to 1f, 0.18f to 1f, 0.45f to 0.9f, 0.58f to 0.9f, 0.8f to 1f)
    val mouth = keyframes(phase, 0f to 1f, 0.3f to 1f, 0.48f to 1.55f, 0.62f to 0.8f, 0.78f to 1f)
}

/** Paints the mark in a square of side [side] at the origin, one gulp at [phase] (0..1). */
fun DrawScope.drawGulpMark(side: Float, phase: Float, style: GulpStyle = GulpStyle.Brand) {
    val u = side / 512f
    val pose = GulpPose(phase)
    style.tile?.let { drawRoundRect(it, size = Size(side, side), cornerRadius = CornerRadius(116 * u)) }
    style.tileOutline?.let {
        val w = 26 * u
        drawRoundRect(
            it,
            topLeft = Offset(w / 2, w / 2),
            size = Size(side - w, side - w),
            cornerRadius = CornerRadius(110 * u),
            style = Stroke(w),
        )
    }
    // Drop (play triangle, rounded by its thick outline), scaled around its bottom tip.
    val tipX = 256f
    val tipY = 284f
    fun px(x: Float) = (tipX + (x - tipX) * pose.dropScaleX) * u
    fun py(y: Float) = (tipY + (y - tipY) * pose.dropScaleY + pose.dropY) * u
    val drop = Path().apply {
        moveTo(px(184f), py(158f))
        lineTo(px(328f), py(158f))
        lineTo(px(256f), py(284f))
        close()
    }
    drawPath(drop, style.drop, style = Fill)
    drawPath(drop, style.drop, style = Stroke(40 * u, join = StrokeJoin.Round))
    // Mouth: a smile whose depth stretches when it swallows.
    val mouth = Path().apply {
        moveTo(158 * u, 346 * u)
        quadraticTo(256 * u, (346 + 72 * pose.mouth) * u, 354 * u, 346 * u)
    }
    drawPath(mouth, style.mouth, style = Stroke(40 * u, cap = StrokeCap.Round))
}

/**
 * The animated Gulp mark. [loop]: swallows every few seconds on its own; it also swallows once whenever
 * [gulpKey] changes (by default each time a download is queued).
 */
@Composable
fun GulpMark(
    size: Dp,
    modifier: Modifier = Modifier,
    style: GulpStyle = GulpStyle.Brand,
    loop: Boolean = true,
    gulpKey: Any? = LocalGulpPulse.current,
) {
    val loopPhase = if (loop) {
        val transition = rememberInfiniteTransition(label = "gulpLoop")
        // One gulp in the first 2.6 s of a 4.2 s cycle, then a rest.
        transition.animateFloat(0f, 1.6f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "gulpPhase")
    } else {
        null
    }
    val shot = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(gulpKey) {
        if (first) {
            first = false
        } else {
            shot.snapTo(0f)
            shot.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    Canvas(modifier.size(size)) {
        val phase = when {
            shot.value < 1f -> shot.value
            loopPhase != null -> loopPhase.value.coerceAtMost(1f)
            else -> 1f
        }
        drawGulpMark(this.size.minDimension, phase, style)
    }
}

/** "gulp" in the brand typeface, lowercase, tight. */
@Composable
fun GulpWord(color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        "gulp",
        color = color,
        fontSize = fontSize,
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.04f * fontSize.value).sp,
        modifier = modifier,
    )
}

/** Mark + word, side by side. */
@Composable
fun GulpLockup(
    markSize: Dp,
    textColor: Color,
    modifier: Modifier = Modifier,
    style: GulpStyle = GulpStyle.Brand,
    fontSize: TextUnit = (markSize.value * 0.82f).sp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GulpMark(markSize, style = style)
        Spacer(Modifier.width(markSize * 0.26f))
        GulpWord(textColor, fontSize)
    }
}

// MARK: - Launch animation

/**
 * Full-screen intro played once at launch, over [background]: the mark pops in, swallows, the letters of
 * "gulp" rise one by one, then everything zooms through and fades into the app. Calls [onFinished] at the end.
 */
@Composable
fun GulpIntro(background: Color, textColor: Color, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    val gulp = remember { Animatable(0f) }
    val letters = remember { List(4) { Animatable(0f) } }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 320f)) }
        delay(160)
        launch { gulp.animateTo(1f, tween(1000, easing = LinearEasing)) }
        delay(220)
        letters.forEachIndexed { i, a ->
            launch {
                delay(i * 70L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
            }
        }
        delay(900)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(background)
            .semantics {
                contentDescription = "Passer l'animation de lancement"
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 1f + exit.value * 0.6f
                scaleX = s
                scaleY = s
            },
        ) {
            Canvas(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val p = pop.value
                        scaleX = 0.3f + 0.7f * p
                        scaleY = 0.3f + 0.7f * p
                        rotationZ = (1f - p) * -18f
                        alpha = p.coerceIn(0f, 1f)
                    },
            ) {
                drawGulpMark(size.minDimension, gulp.value)
            }
            Spacer(Modifier.height(22.dp))
            Row {
                "gulp".forEachIndexed { i, c ->
                    Text(
                        c.toString(),
                        color = textColor,
                        fontSize = 46.sp,
                        fontFamily = BricolageFamily,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.graphicsLayer {
                            val p = letters[i].value
                            alpha = p.coerceIn(0f, 1f)
                            translationY = (1f - p) * 28.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}

/** Little preview of the mark for the skin picker and other small spots, without animation. */
@Composable
fun GulpMarkStatic(size: Dp, modifier: Modifier = Modifier, style: GulpStyle = GulpStyle.Brand) {
    Canvas(modifier.size(size)) { drawGulpMark(this.size.minDimension, 1f, style) }
}
