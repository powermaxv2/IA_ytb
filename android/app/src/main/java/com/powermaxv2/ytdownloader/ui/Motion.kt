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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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

// MARK: - Modifiers

/** Press feedback (scale down, spring back) + light haptic tick. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, pressedScale: Float = 0.96f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
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

/** Fades the element in while it rises a little, after a delay based on its index: lists appear in cascade. */
@Composable
fun Modifier.staggeredEntrance(index: Int, key: Any? = Unit, baseDelay: Long = 0L): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(baseDelay + index.coerceAtMost(12) * 40L)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
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
                durationMillis = 460
                -12f at 60
                10f at 130
                -7f at 200
                4f at 270
                -2f at 340
            },
        )
    }
    return graphicsLayer { translationX = offset.value.dp.toPx() }
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
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "shimmerX")
    val highlight = Palette.TextPrimary.copy(alpha = 0.07f)
    return drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
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
            progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    // Every other particle in the text colour: white on the dark skins, ink on Papier.
    val secondary = Palette.TextPrimary
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val maxRadius = size.minDimension * 0.9f
        drawCircle(
            color.copy(alpha = (1f - p) * 0.7f),
            radius = maxRadius * p,
            style = Stroke(width = 3.dp.toPx() * (1f - p) + 0.5f),
        )
        for (i in 0 until 8) {
            val angle = i * (2 * PI / 8) + p * 0.6
            val distance = maxRadius * (0.35f + 0.7f * p)
            val particle = center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
            val particleColor = if (i % 2 == 0) color else secondary
            drawCircle(particleColor.copy(alpha = 1f - p), radius = 2.4.dp.toPx() * (1f - p * 0.6f), center = particle)
        }
    }
}
