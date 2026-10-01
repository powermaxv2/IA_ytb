package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Platform
import kotlin.math.max
import kotlin.math.min

// MARK: - Surfaces

fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), tint: Color = Palette.Fill): Modifier =
    this.clip(shape).background(tint).border(1.dp, Palette.Stroke, shape)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Palette.TextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

// MARK: - Buttons

/** Main call to action: accent gradient, a sheen sweeping across, glow, springy press. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "scale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "alpha")
    val transition = rememberInfiniteTransition(label = "button")
    val sheen by transition.animateFloat(
        -0.6f, 1.6f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = FastOutSlowInEasing)), label = "sheen",
    )
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = accent.start, spotColor = accent.start)
            .clip(shape)
            .background(accent.horizontal())
            .drawWithContent {
                drawContent()
                if (enabled) {
                    val w = size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent),
                            start = Offset(w * sheen - w * 0.25f, 0f),
                            end = Offset(w * sheen + w * 0.25f, size.height),
                        ),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "buttonContent",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Text(
                        text,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .bounceClick(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .glass(RoundedCornerShape(if (compact) 12.dp else 16.dp), Color.White.copy(alpha = 0.07f))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(if (compact) 16.dp else 19.dp)) }
        Text(text, color = tint, fontSize = if (compact) 12.5.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Color.White,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bounceClick(pressedScale = 0.88f, onClick = onClick)
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Palette.Stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// MARK: - Selection

/** Two or three options with a gradient pill that slides between them. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val view = LocalView.current
    val haptics = LocalHaptics.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .glass(RoundedCornerShape(15.dp))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.7f, stiffness = 380f), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = accent.start)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.horizontal()),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val color by animateColorAsState(if (isSelected) Color.White else Palette.TextSecondary, label = "segmentColor")
                val iconScale by animateFloatAsState(
                    if (isSelected) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 420f), label = "segmentIcon",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            view.tick(haptics)
                            onSelect(option)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon(option),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label(option), color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Quality / format tile: gradient outline and tint when selected. */
@Composable
fun SelectableTile(
    title: String,
    badge: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val accent = LocalAccent.current
    val background by animateColorAsState(
        if (selected) accent.start.copy(alpha = 0.18f) else Palette.Fill, tween(260), label = "tileBg",
    )
    val outline by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "tileOutline")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(background)
            .border(1.dp, Palette.Stroke, shape)
            .drawWithContent {
                drawContent()
                if (outline > 0f) {
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(accent.start, accent.end)),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        alpha = outline,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            if (highlight) MaxBadge()
            Text(
                badge,
                color = if (selected) Color.White else Palette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (selected) 0.2f else 0.08f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Text(detail, color = Palette.TextSecondary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MaxBadge() {
    val accent = LocalAccent.current
    Text(
        "MAX",
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.horizontal())
            .shimmer()
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Horizontally scrolling pills (formats, qualities). */
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val fill by animateFloatAsState(if (isSelected) 1f else 0f, tween(240), label = "chip")
            Text(
                label(option),
                color = Color.White.copy(alpha = 0.7f + 0.3f * fill),
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .bounceClick { onSelect(option) }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .drawWithContent {
                        if (fill > 0f) drawRect(Brush.horizontalGradient(listOf(accent.start, accent.end)), alpha = fill)
                        drawContent()
                    }
                    .border(1.dp, Palette.Stroke, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Items laid out in rows of [columns]; [equalHeight] makes tiles of a row the same height. */
@Composable
fun <T> TileGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    equalHeight: Boolean = true,
    content: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEachIndexed { row, chunk ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (equalHeight) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chunk.forEachIndexed { column, item ->
                    val cell = Modifier
                        .weight(1f)
                        .then(if (equalHeight) Modifier.fillMaxHeight() else Modifier)
                    content(row * columns + column, item, cell)
                }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Round checkbox: the disc grows with a spring and the tick draws itself. */
@Composable
fun AnimatedCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val progress by animateFloatAsState(
        if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "check",
    )
    Canvas(modifier.size(24.dp)) {
        val radius = size.minDimension / 2
        val p = progress.coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = 0.35f * (1f - p)), radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        if (progress > 0.01f) {
            drawCircle(Brush.linearGradient(listOf(accent.start, accent.end)), radius * min(progress, 1.08f))
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.67f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val segment = Path()
            measure.getSegment(0f, measure.length * p, segment, true)
            drawPath(segment, Color.White, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// MARK: - Progress

/** Gradient bar with a moving sheen; negative [progress] = indeterminate sweep. */
@Composable
fun GradientProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val accent = LocalAccent.current
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "progress",
    )
    val transition = rememberInfiniteTransition(label = "progressAnim")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = radius)
        val brush = Brush.horizontalGradient(listOf(accent.start, accent.end))
        if (indeterminate) {
            val w = size.width * 0.32f
            val x = (size.width + w) * phase - w
            val left = max(0f, x)
            val right = min(size.width, x + w)
            if (right > left) {
                drawRoundRect(brush, topLeft = Offset(left, 0f), size = Size(right - left, size.height), cornerRadius = radius)
            }
        } else {
            val w = max(size.height, size.width * animated)
            drawRoundRect(brush, size = Size(w, size.height), cornerRadius = radius)
            val sheenX = w * phase
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                    startX = sheenX - 36.dp.toPx(),
                    endX = sheenX,
                ),
                size = Size(w, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** Circular progress: rotating arc while waiting, filling arc while downloading. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rotation")
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "ringProgress")
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(Color.White.copy(alpha = 0.1f), radius = size.minDimension / 2 - inset, style = Stroke(stroke))
        val brush = Brush.sweepGradient(listOf(accent.start, accent.end, accent.start))
        if (progress < 0f || progress == 0f) {
            drawArc(brush, rotation, 90f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            drawArc(brush, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Success disc whose tick draws itself. */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, animate: Boolean = true) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 200f)) }
    Canvas(modifier) {
        val p = progress.value
        drawCircle(Palette.Success.copy(alpha = 0.18f), radius = size.minDimension / 2 * min(p, 1.1f))
        drawCircle(Palette.Success, radius = size.minDimension / 2 * 0.72f * min(p, 1.05f))
        val path = Path().apply {
            moveTo(size.width * 0.33f, size.height * 0.52f)
            lineTo(size.width * 0.46f, size.height * 0.64f)
            lineTo(size.width * 0.69f, size.height * 0.39f)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * p.coerceIn(0f, 1f), segment, true)
        drawPath(segment, Color.White, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// MARK: - Platform glyphs (simple generic shapes)

@Composable
fun PlatformGlyph(platform: Platform, modifier: Modifier = Modifier) {
    when (platform) {
        Platform.YOUTUBE -> Canvas(modifier) {
            val h = size.height * 0.72f
            val top = (size.height - h) / 2
            drawRoundRect(Color(0xFFFF0033), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = CornerRadius(h * 0.3f))
            val triangle = Path().apply {
                moveTo(size.width * 0.41f, top + h * 0.28f)
                lineTo(size.width * 0.41f, top + h * 0.72f)
                lineTo(size.width * 0.66f, top + h * 0.5f)
                close()
            }
            drawPath(triangle, Color.White)
        }
        Platform.INSTAGRAM -> Canvas(modifier) {
            val brush = Brush.linearGradient(
                listOf(Palette.InstaOrange, Palette.InstaPink, Palette.InstaPurple),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )
            val stroke = size.minDimension * 0.1f
            drawRoundRect(
                brush,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.minDimension * 0.28f),
                style = Stroke(stroke),
            )
            drawCircle(brush, radius = size.minDimension * 0.2f, style = Stroke(stroke))
            drawCircle(brush, radius = stroke * 0.7f, center = Offset(size.width * 0.74f, size.height * 0.26f))
        }
        Platform.TWITTER -> Canvas(modifier) {
            blackTile()
            val s = size.minDimension
            val near = s * 0.29f
            val far = s * 0.71f
            drawLine(Color.White, Offset(near, near), Offset(far, far), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(far, near), Offset(near, far), strokeWidth = s * 0.075f, cap = StrokeCap.Round)
        }
        Platform.TIKTOK -> Canvas(modifier) {
            blackTile()
            val s = size.minDimension
            val note = Path().apply {
                moveTo(s * 0.57f, s * 0.20f)
                lineTo(s * 0.57f, s * 0.62f)
                moveTo(s * 0.57f, s * 0.20f)
                cubicTo(s * 0.57f, s * 0.34f, s * 0.66f, s * 0.40f, s * 0.78f, s * 0.40f)
                addOval(Rect(Offset(s * 0.44f, s * 0.64f), s * 0.115f))
            }
            val stroke = Stroke(s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            translate(-s * 0.03f, -s * 0.025f) { drawPath(note, Color(0xFF25F4EE), style = stroke) }
            translate(s * 0.03f, s * 0.025f) { drawPath(note, Color(0xFFFE2C55), style = stroke) }
            drawPath(note, Color.White, style = stroke)
        }
        Platform.SNAPCHAT -> Canvas(modifier) {
            val s = size.minDimension
            drawRoundRect(Color(0xFFFFFC00), cornerRadius = CornerRadius(s * 0.24f))
            val ghost = Path().apply {
                moveTo(s * 0.28f, s * 0.74f)
                lineTo(s * 0.28f, s * 0.44f)
                cubicTo(s * 0.28f, s * 0.26f, s * 0.38f, s * 0.16f, s * 0.50f, s * 0.16f)
                cubicTo(s * 0.62f, s * 0.16f, s * 0.72f, s * 0.26f, s * 0.72f, s * 0.44f)
                lineTo(s * 0.72f, s * 0.74f)
                quadraticTo(s * 0.647f, s * 0.80f, s * 0.573f, s * 0.74f)
                quadraticTo(s * 0.50f, s * 0.68f, s * 0.427f, s * 0.74f)
                quadraticTo(s * 0.353f, s * 0.80f, s * 0.28f, s * 0.74f)
                close()
            }
            drawPath(ghost, Color.White)
            drawPath(ghost, Color(0xFF1A1A1A), style = Stroke(s * 0.05f, join = StrokeJoin.Round))
        }
        Platform.FACEBOOK -> Canvas(modifier) {
            // Blue disc with the white "f" running off its bottom edge, drawn on a 24-unit grid.
            val k = size.minDimension / 24f
            val disc = Path().apply { addOval(Rect(Offset(12f * k, 12f * k), 12f * k)) }
            drawPath(disc, Color(0xFF0866FF))
            val f = Path().apply {
                moveTo(9.1f * k, 25f * k)
                lineTo(9.1f * k, 15.71f * k)
                lineTo(6.63f * k, 15.71f * k)
                lineTo(6.63f * k, 12.04f * k)
                lineTo(9.1f * k, 12.04f * k)
                lineTo(9.1f * k, 10.46f * k)
                cubicTo(9.1f * k, 6.38f * k, 10.95f * k, 4.49f * k, 14.96f * k, 4.49f * k)
                cubicTo(15.6f * k, 4.49f * k, 16.9f * k, 4.6f * k, 17.57f * k, 4.78f * k)
                lineTo(17.57f * k, 8.11f * k)
                cubicTo(17.2f * k, 8.07f * k, 16.6f * k, 8.06f * k, 16.18f * k, 8.06f * k)
                cubicTo(14.37f * k, 8.06f * k, 13.45f * k, 8.75f * k, 13.45f * k, 10.75f * k)
                lineTo(13.45f * k, 12.04f * k)
                lineTo(17.37f * k, 12.04f * k)
                lineTo(16.7f * k, 15.71f * k)
                lineTo(13.45f * k, 15.71f * k)
                lineTo(13.45f * k, 25f * k)
                close()
            }
            clipPath(disc) { drawPath(f, Color.White) }
        }
        Platform.BEREAL -> BoxWithConstraints(
            modifier
                .clip(RoundedCornerShape(20))
                .background(Color(0xFF0B0B0F))
                .border(0.7.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20)),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Text(
                "Be",
                color = Color.White,
                fontSize = with(LocalDensity.current) { (side * 0.48f).toSp() },
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Platform.OTHER -> Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            modifier = modifier.gradientTint(LocalAccent.current.brush),
            tint = Color.White,
        )
    }
}

/** Near-black rounded square with a faint outline, the backdrop of the X and TikTok logos. */
private fun DrawScope.blackTile() {
    val s = size.minDimension
    val corner = CornerRadius(s * 0.24f)
    drawRoundRect(Color(0xFF0B0B0F), cornerRadius = corner)
    val stroke = s * 0.05f
    drawRoundRect(
        Color.White.copy(alpha = 0.32f),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(s - stroke, s - stroke),
        cornerRadius = corner,
        style = Stroke(stroke),
    )
}

/** Glyph that pops (overshooting spring) whenever the detected platform changes. */
@Composable
fun AnimatedPlatformGlyph(platform: Platform, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = platform,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.42f, stiffness = 380f), initialScale = 0.2f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.2f) + fadeOut(tween(120)))
        },
        label = "glyph",
        modifier = modifier,
    ) { target ->
        PlatformGlyph(target, Modifier.fillMaxSize())
    }
}

/** Number that rolls up/down when it changes. */
@Composable
fun RollingNumber(value: Int, modifier: Modifier = Modifier, color: Color = Color.White) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
        },
        label = "rolling",
        modifier = modifier,
    ) { number ->
        Text(number.toString(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
