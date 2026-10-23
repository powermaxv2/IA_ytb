package com.powermaxv2.ytdownloader.ui.skin.paper

import com.powermaxv2.ytdownloader.ui.hub.HomeLink
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.RollingNumber
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.brand.LocalGulpPulse
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick

// "Papier" skin: Gulp's editorial look. White cards on cream paper, ink of the logo's mouth, one flat accent
// per tab, Bricolage Grotesque.

/** Text colour on the ink (selected segment, badges): the white of the cards. */
private val PaperInkText = Color.White

/** Paper-coloured fill on the ink bar (selected tab). */
private val PaperCream = GulpColors.Cream

// MARK: - Surfaces

/** White card with a very soft, warm (brown) shadow and no outline. */
@Composable
internal fun Modifier.paperCard(shape: Shape = RoundedCornerShape(LocalTokens.current.cardRadius)): Modifier {
    val warm = LocalTokens.current.cardShadowColor
    return this
        .shadow(6.dp, shape, ambientColor = warm, spotColor = warm)
        .clip(shape)
        .background(Palette.Surface)
}

/** Small ink square button (secondary actions next to the accent button), cream icon. */
@Composable
internal fun PaperInkButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = Palette.TextPrimary
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .size(60.dp)
            .shadow(8.dp, shape, ambientColor = ink, spotColor = ink)
            .clip(shape)
            .background(ink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = PaperCream, modifier = Modifier.size(24.dp))
    }
}

/** Big flat accent button: bold label, coloured shadow, press scale; [subtitle] repeats the chosen options. */
@Composable
internal fun PaperButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current.start
    val onAccent = Palette.OnAccent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "paperPress")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "paperAlpha")
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(LocalTokens.current.buttonRadius)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = accent.copy(alpha = 0.7f), spotColor = accent.copy(alpha = 0.7f))
            .clip(shape)
            .background(accent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
            label = "paperButton",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = onAccent, strokeWidth = 2.5.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        icon?.let { Icon(it, contentDescription = null, tint = onAccent, modifier = Modifier.size(22.dp)) }
                        Text(text, color = onAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    subtitle?.let {
                        Text(it, color = onAccent.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// MARK: - Background

/**
 * Cream paper with a faint Gulp watermark (a big drop above a big smile, the logo's two strokes) that drifts
 * slowly in the lower right corner. Barely visible: the content stays on top.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier) {
    val ink = Palette.TextPrimary.copy(alpha = 0.035f)
    val transition = rememberInfiniteTransition(label = "paperWatermark")
    val drift by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "paperDrift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * 0.9f
            val u = side / 512f
            val left = size.width - side * 0.72f
            val top = size.height - side * 1.05f
            // The drop floats a little more than the smile, as if about to be swallowed.
            translate(left, top + drift * 14.dp.toPx()) {
                val drop = Path().apply {
                    moveTo(184f * u, 158f * u)
                    lineTo(328f * u, 158f * u)
                    lineTo(256f * u, 284f * u)
                    close()
                }
                drawPath(drop, ink)
                drawPath(drop, ink, style = Stroke(40f * u, join = StrokeJoin.Round))
            }
            translate(left, top - drift * 4.dp.toPx()) {
                val smile = Path().apply {
                    moveTo(158f * u, 346f * u)
                    quadraticTo(256f * u, 418f * u, 354f * u, 346f * u)
                }
                drawPath(smile, ink, style = Stroke(40f * u, cap = StrokeCap.Round))
            }
        }
    }
}

// MARK: - Header

/**
 * The logo's smile in the accent, under the title: it draws itself from left to right on each tab ([grow])
 * and deepens for a moment when a download is queued ([swallow], like the mark's mouth).
 */
@Composable
internal fun PaperSmileUnderline(grow: () -> Float, swallow: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val stroke = 4.dp.toPx()
        val half = stroke / 2
        val depth = (size.height - stroke) * (1f + 0.6f * swallow())
        val smile = Path().apply {
            moveTo(half, half)
            // A quadratic curve reaches half of its control point's depth.
            quadraticTo(size.width / 2, half + depth * 2f, size.width - half, half)
        }
        clipRect(right = size.width * grow().coerceIn(0f, 1f)) {
            drawPath(smile, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

// MARK: - Bottom navigation

/** Gulp's drop (a small rounded play triangle pointing down), centred on [center], [side] wide. */
private fun DrawScope.drawPaperDrop(center: Offset, side: Float, color: Color) {
    val h = side * 0.875f
    val drop = Path().apply {
        moveTo(center.x - side / 2, center.y - h / 2)
        lineTo(center.x + side / 2, center.y - h / 2)
        lineTo(center.x, center.y + h / 2)
        close()
    }
    drawPath(drop, color)
    drawPath(drop, color, style = Stroke(side * 0.28f, join = StrokeJoin.Round))
}

// MARK: - Link field

// MARK: - Empty state: how to
