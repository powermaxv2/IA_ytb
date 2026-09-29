package com.powermaxv2.ytdownloader.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether taps give a light vibration (Réglages → Vibrations). */
val LocalHaptics = compositionLocalOf { true }

/** Runs an action after asking for the permissions downloads need (notifications, storage on old Android). */
val LocalDownloadGate = staticCompositionLocalOf<(() -> Unit) -> Unit> { { action -> action() } }

fun View.tick(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

// MARK: - Background

/** Slowly drifting glow blobs + rising specks, tinted by the current accent. */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "background")
    val a by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    val b by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(19_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val c by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(23_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c",
    )
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(28_000, easing = LinearEasing)), label = "drift",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Palette.Background)
        val m = size.minDimension
        blob(accent.start.copy(alpha = 0.34f), Offset(size.width * (0.05f + 0.3f * a), size.height * (0.04f + 0.14f * b)), m * 0.95f)
        blob(accent.end.copy(alpha = 0.22f), Offset(size.width * (1f - 0.35f * b), size.height * (0.9f - 0.22f * c)), m * 0.9f)
        blob(Palette.Purple.copy(alpha = 0.17f), Offset(size.width * (0.9f - 0.25f * c), size.height * (0.28f + 0.2f * a)), m * 0.75f)
        for (i in 0 until 18) {
            val seed = (i * 0.618034f) % 1f
            val progress = (drift + seed) % 1f
            val sway = sin((progress * 6f + i).toDouble()).toFloat() * 12.dp.toPx()
            val x = ((seed * 7.3f) % 1f) * size.width + sway
            val y = size.height * (1.05f - progress * 1.1f)
            val alpha = sin(progress * PI).toFloat() * 0.32f
            drawCircle(Color.White.copy(alpha = alpha), radius = 1.2.dp.toPx() + (i % 3) * 0.7.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

// MARK: - Modifiers

/** Springy press feedback (scale down, bounce back) + light haptic tick. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, pressedScale: Float = 0.95f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "press",
    )
    val view = LocalView.current
    val haptics = LocalHaptics.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            view.tick(haptics)
            onClick()
        }
}

/** Pops the element in (fade + rise + scale) after a delay based on its index: lists appear in cascade. */
@Composable
fun Modifier.staggeredEntrance(index: Int, key: Any? = Unit, baseDelay: Long = 0L): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(baseDelay + index.coerceAtMost(14) * 45L)
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
    }
}

/** Horizontal "no" shake, played when [key] changes (error messages). */
@Composable
fun Modifier.shakeOnAppear(key: Any?): Modifier {
    val offset = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 520
                -18f at 60
                16f at 130
                -12f at 200
                9f at 270
                -5f at 340
                2f at 410
            },
        )
    }
    return graphicsLayer { translationX = offset.value.dp.toPx() }
}

/** Gentle up-and-down float. */
@Composable
fun Modifier.floating(amplitude: Dp = 4.dp, periodMs: Int = 2600): Modifier {
    val transition = rememberInfiniteTransition(label = "float")
    val value by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatValue",
    )
    return graphicsLayer { translationY = value * amplitude.toPx() }
}

/** Breathing scale. */
@Composable
fun Modifier.pulsing(min: Float = 0.94f, max: Float = 1.06f, periodMs: Int = 1400): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        min, max, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseValue",
    )
    return graphicsLayer {
        scaleX = value
        scaleY = value
    }
}

/** Moving highlight used on loading skeletons. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shimmerX")
    return drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                start = Offset(w * x - w * 0.4f, 0f),
                end = Offset(w * x, size.height),
            ),
        )
    }
}

/** Paints the content (an icon) with a gradient. */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcAtop)
    }

// MARK: - Celebration

/** Expanding ring + particles, played once when [play] becomes true (download finished). */
@Composable
fun SuccessBurst(play: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(play) {
        if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val maxRadius = size.minDimension * 0.9f
        drawCircle(
            color.copy(alpha = (1f - p) * 0.7f),
            radius = maxRadius * p,
            style = Stroke(width = 3.dp.toPx() * (1f - p) + 0.5f),
        )
        for (i in 0 until 10) {
            val angle = i * (2 * PI / 10) + p * 0.6
            val distance = maxRadius * (0.35f + 0.75f * p)
            val particle = center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
            val particleColor = if (i % 2 == 0) color else Color.White
            drawCircle(particleColor.copy(alpha = 1f - p), radius = 2.6.dp.toPx() * (1f - p * 0.6f), center = particle)
        }
    }
}
