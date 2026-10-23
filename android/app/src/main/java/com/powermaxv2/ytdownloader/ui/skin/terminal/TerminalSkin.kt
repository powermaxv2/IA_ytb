package com.powermaxv2.ytdownloader.ui.skin.terminal

import com.powermaxv2.ytdownloader.ui.hub.HomeLink
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick
import kotlinx.coroutines.delay

// "Terminal" skin, the Gulp console: warm near-black, orange phosphor grid, one neon accent per tab,
// JetBrains Mono + Archivo Black titles.

/** Shell prompt of the header, typed once: user part in accent, path part dimmed. */
private const val PROMPT_USER = "gulp@android"
private const val PROMPT_PATH = ":~$"

// MARK: - Shared helpers

/** 0/1 square wave for blinking cursors (period ~1s). */
@Composable
internal fun rememberBlink(periodMs: Int = 1060): State<Float> {
    val transition = rememberInfiniteTransition(label = "termBlink")
    return transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "termBlinkPhase",
    )
}

/** Hides the element during the second half of each blink period. */
@Composable
internal fun Modifier.blinking(periodMs: Int = 1060): Modifier {
    val phase = rememberBlink(periodMs)
    return graphicsLayer { alpha = if (phase.value < 0.5f) 1f else 0f }
}

/**
 * Number of characters of [text] typed so far: a linear typewriter restarted when [key] changes
 * ([charMs] per character, at most [maxMs] in all, after [delayMs]).
 */
@Composable
internal fun rememberTyped(
    text: String,
    key: Any? = text,
    charMs: Int = 30,
    maxMs: Int = 1200,
    delayMs: Long = 0L,
): State<Int> {
    val typed = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (delayMs > 0L) delay(delayMs)
        typed.animateTo(text.length.toFloat(), tween((text.length * charMs).coerceIn(1, maxMs), easing = LinearEasing))
    }
    return remember(key) { derivedStateOf { typed.value.toInt() } }
}

/** Almost square panel with a 1dp outline. */
@Composable
internal fun Modifier.termPanel(): Modifier {
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    return this
        .clip(shape)
        .background(Palette.Surface)
        .border(1.dp, Palette.Stroke, shape)
}

/** Small uppercase mono label, e.g. "> LIEN"; [typed]: types itself in behind an accent block caret. */
@Composable
internal fun TermLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary, typed: Boolean = true) {
    val count by rememberTyped(text, charMs = 28)
    val typing = typed && count < text.length
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (typed) text.take(count) else text, color = color, fontSize = 12.sp, letterSpacing = 1.5.sp, maxLines = 1)
        if (typing) {
            Text("▌", color = LocalAccent.current.start, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/**
 * Terminal button: [solid] = accent fill with dark text, else accent outline. Pressing inverts the colours
 * (a short flash), the release springs back.
 */
@Composable
internal fun TermButton(
    text: String,
    modifier: Modifier = Modifier,
    solid: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    big: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current.start
    val dark = Palette.Background
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = solid != pressed
    val fill by animateColorAsState(if (filled) accent else dark, tween(if (pressed) 40 else 220), label = "termButtonFill")
    val ink by animateColorAsState(if (filled) dark else accent, tween(if (pressed) 40 else 220), label = "termButtonInk")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "termButtonAlpha")
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.buttonRadius)
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(fill)
            .border(1.dp, accent, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .defaultMinSize(minHeight = if (big) 60.dp else 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "termButton",
        ) { isLoading ->
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ANALYSE", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("…", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.blinking(600))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text,
                        color = ink,
                        fontSize = if (big) 19.sp else 14.sp,
                        fontWeight = if (big) FontWeight.Normal else FontWeight.Bold,
                        fontFamily = if (big) tokens.display else tokens.body,
                        letterSpacing = if (big) 0.5.sp else 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(it, color = ink.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// MARK: - Background

/**
 * Near-black with a faint warm grid every 28dp, an accent glow at the top, an ember glow at the bottom and
 * a CRT scanline band slowly sweeping down.
 */
@Composable
fun TerminalBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val base = Palette.Background
    // Grid half-way between the tab accent and the Gulp orange: always a little warm.
    val grid = lerp(accent, GulpColors.Orange, 0.5f).copy(alpha = 0.05f)
    val ember = GulpColors.Red.copy(alpha = 0.06f)
    val transition = rememberInfiniteTransition(label = "termScan")
    // Read in the draw phase only: the sweep redraws without recomposing.
    val sweep = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "termScanY")
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.08f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(ember, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 1.05f),
                radius = size.maxDimension * 0.6f,
            ),
        )
        val step = 28.dp.toPx()
        val stroke = 1.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), stroke)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), stroke)
            y += step
        }
        // Scanline: a soft band whose bright lower edge travels from above the top to below the bottom.
        val band = 140.dp.toPx()
        val edge = sweep.value * (size.height + band)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, accent.copy(alpha = 0.045f)),
                startY = edge - band,
                endY = edge,
            ),
            topLeft = Offset(0f, edge - band),
            size = Size(size.width, band),
        )
        drawLine(accent.copy(alpha = 0.09f), Offset(0f, edge), Offset(size.width, edge), stroke)
    }
}

// MARK: - Header

// MARK: - Bottom navigation

// MARK: - Link field

// MARK: - Empty state: how to

/** Bracketed uppercase tag, e.g. "[ JUSQU'EN 8K ]". */
@Composable
internal fun TermTag(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary, fontSize: TextUnit = 12.sp, height: Dp = 32.dp) {
    val accent = LocalAccent.current.start
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(LocalTokens.current.chipRadius))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(LocalTokens.current.chipRadius))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("[ ", color = accent, fontSize = fontSize, fontWeight = FontWeight.Bold)
        Text(text.uppercase(), color = color, fontSize = fontSize, letterSpacing = 0.5.sp, maxLines = 1)
        Text(" ]", color = accent, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}
