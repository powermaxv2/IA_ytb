package com.powermaxv2.ytdownloader.ui.tools.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Motion of "Vu, répondu": numbers that count up, bars that grow, the crown that drops on the podium.
// Everything starts from zero when the screen (or the threshold) changes, like the original app.

/** Ease-out quart, the curve of the original app's counters. */
internal val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

/** Ease-out "expo" of the original app's bars and cards. */
internal val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Colours given to the people, by rank of messages (the original app's). */
private val PersonColors = listOf(
    Color(0xFF1F9D6B), Color(0xFFE4572E), Color(0xFF4C5FD5), Color(0xFFD99A1E), Color(0xFFC2408F), Color(0xFF1B8AA6),
    Color(0xFF7A8B2B), Color(0xFF7F5AB5), Color(0xFFD9633F), Color(0xFF3A7D44), Color(0xFF9B4DCA), Color(0xFFC47F17),
)

internal fun personColor(index: Int): Color = PersonColors[index.mod(PersonColors.size)]

internal val Gold = Color(0xFFF0B940)

/** A value going from where it was to [target] once [delayMs] passed (starts at 0 the first time). */
@Composable
internal fun rememberGrow(target: Float, delayMs: Int = 0, durationMs: Int = 1100): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (delayMs > 0) delay(delayMs.toLong())
        value.animateTo(target, tween(durationMs, easing = EaseOutExpo))
    }
    return value.value
}

/** A number counting up to [target] (null → [format] of null right away). */
@Composable
internal fun CountUpText(
    target: Double?,
    format: (Double?) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = 1400,
) {
    val progress = remember { Animatable(0f) }
    val from = remember { doubleArrayOf(0.0) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = EaseOutQuart))
        from[0] = target ?: 0.0
    }
    val shown = target?.let { from[0] + (it - from[0]) * progress.value }
    Text(format(shown), style = style, color = color, modifier = modifier, maxLines = 1, softWrap = false)
}

/**
 * A horizontal bar filled to [fraction] (0..1) in [color], growing after [delayMs]. Classic: rounded with a
 * light gradient; Papier: flat, square ends, on an ink track; Terminal: a row of █ and ░.
 */
@Composable
internal fun StatBar(fraction: Float, color: Color, modifier: Modifier = Modifier, delayMs: Int = 0, height: Dp = 12.dp) {
    val grown = rememberGrow(fraction.coerceIn(0f, 1f), delayMs)
    when (LocalSkin.current) {
        Skin.TERMINAL -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val cells = (maxWidth.value / 7.4f).toInt().coerceIn(4, 60)
            val filled = (grown * cells).roundToInt()
            Text(
                "█".repeat(filled) + "░".repeat(cells - filled),
                color = color,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Skin.PAPER -> Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(grown)
                    .fillMaxHeight()
                    .background(color),
            )
        }
        Skin.CLASSIC -> Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(Palette.Surface2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(grown)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
            )
        }
    }
}

/** Pops in with a spring (scale 0.6 → 1, a little overshoot) after [delayMs]. */
@Composable
internal fun Modifier.popIn(delayMs: Int, key: Any? = Unit): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = 0.6f + 0.4f * v
        scaleX = s
        scaleY = s
        translationY = (1f - v) * 10.dp.toPx()
    }
}

/** Slides in from the side ([fromRight]) after [delayMs]. */
@Composable
internal fun Modifier.slideIn(delayMs: Int, fromRight: Boolean, key: Any? = Unit): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(600, easing = EaseOutExpo))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v
        translationX = (1f - v) * (if (fromRight) 30.dp else (-30).dp).toPx()
    }
}

/** Gold crown of the fastest: drops in spinning, after [delayMs]. */
@Composable
internal fun Crown(modifier: Modifier = Modifier, delayMs: Int = 1100) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    Canvas(
        modifier.graphicsLayer {
            val v = p.value
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * (-40).dp.toPx()
            rotationZ = (1f - v) * -30f
        },
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.88f)
            lineTo(0f, h * 0.19f)
            lineTo(w * 0.25f, h * 0.44f)
            lineTo(w * 0.5f, 0f)
            lineTo(w * 0.75f, h * 0.44f)
            lineTo(w, h * 0.19f)
            lineTo(w * 0.92f, h * 0.88f)
            close()
        }
        drawPath(path, Gold)
    }
}

/** Ten gold sparks bursting out once, after [delayMs]. */
@Composable
internal fun Sparks(modifier: Modifier = Modifier, delayMs: Int = 1300, radius: Dp = 48.dp) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(1000, easing = EaseOutQuart))
    }
    Canvas(modifier) {
        val v = p.value
        if (v <= 0f || v >= 1f) return@Canvas
        val r = radius.toPx() * v
        for (k in 0 until 10) {
            val a = k / 10f * 2f * PI.toFloat()
            drawCircle(
                Gold.copy(alpha = 1f - v),
                radius = 3.dp.toPx() * (1f - v * 0.7f),
                center = center + Offset(cos(a) * r, sin(a) * r),
            )
        }
    }
}

/** Three dots bouncing in turn (someone is typing). */
@Composable
internal fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "typingPhase")
    Canvas(modifier) {
        val d = size.height / 2.2f
        for (i in 0 until 3) {
            val phase = ((t - i * 0.15f) % 1f + 1f) % 1f
            val up = if (phase < 0.3f) sin(phase / 0.3f * PI.toFloat()) else 0f
            drawCircle(
                color.copy(alpha = 0.4f + 0.6f * up),
                radius = d / 2,
                center = Offset(d / 2 + i * d * 1.6f, size.height / 2 - up * d * 0.7f),
            )
        }
    }
}
