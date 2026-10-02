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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Platform
import kotlin.math.max
import kotlin.math.min

// MARK: - Surfaces

/** Card surface: solid dark fill and a hairline outline. */
fun Modifier.panel(shape: Shape = RoundedCornerShape(24.dp), color: Color = Palette.Surface): Modifier =
    this.clip(shape).background(color).border(1.dp, Palette.Stroke, shape)

/** Title of a block inside a card ("Qualité", "Format"…). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

/** Big screen title with an optional line under it. */
@Composable
fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 1)
        subtitle?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Accent-tinted rounded square holding an icon (settings rows, card headers). */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val accent = LocalAccent.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(accent.tint()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(size * 0.53f))
    }
}

// MARK: - Buttons

/** Main action of a screen: accent gradient, big and easy to hit; [subtitle] repeats the chosen options. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "scale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = accent.start, spotColor = accent.start)
            .clip(shape)
            .background(accent.horizontal())
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
            label = "buttonContent",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                        Text(
                            text,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    subtitle?.let {
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Secondary action: dark filled button with an outline. */
@Composable
fun TonalButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    tint: Color = Color.White,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val iconSize = if (compact) 16.dp else 18.dp
    Row(
        modifier
            .bounceClick(enabled = enabled && !loading, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .panel(RoundedCornerShape(14.dp), Palette.Surface2)
            .height(if (compact) 36.dp else 44.dp)
            .padding(horizontal = if (compact) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(iconSize), color = LocalAccent.current.start, strokeWidth = 2.dp)
        } else {
            icon?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize)) }
        }
        Text(text, color = tint, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Icon-only button (settings, close, delete…). */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: Shape = CircleShape,
    tint: Color = Color.White.copy(alpha = 0.8f),
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .size(size)
            .panel(shape, Palette.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
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
            .panel(RoundedCornerShape(16.dp), Palette.Surface2)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.8f, stiffness = 420f), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(accent.horizontal()),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val color by animateColorAsState(if (isSelected) Color.White else Palette.TextSecondary, label = "segmentColor")
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
                    Icon(icon(option), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(label(option), color = color, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Quality / format tile: accent outline, tint and a check when selected. */
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
        if (selected) accent.start.copy(alpha = 0.14f) else Color.Transparent, tween(220), label = "tileBg",
    )
    val outline by animateColorAsState(if (selected) accent.start else Palette.Stroke, tween(220), label = "tileOutline")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(Palette.Surface2)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The title is never squeezed: only the spacer stretches, so "2160p" stays whole next to the check.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            CheckDot(selected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (highlight) MaxBadge()
            Pill(badge, selected)
        }
        Text(detail, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Small accent disc with a tick, shown on the selected tile. */
@Composable
private fun CheckDot(visible: Boolean) {
    val scale by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "checkDot")
    if (scale <= 0.01f) return
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(LocalAccent.current.start),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Pill(text: String, selected: Boolean) {
    Text(
        text,
        color = if (selected) Color.White else Palette.TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.08f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun MaxBadge() {
    Text(
        "MAX",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(LocalAccent.current.horizontal())
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Choice between a few options (formats, qualities): outlined pills, the selected one tinted with the accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(label(option), option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val background by animateColorAsState(if (selected) accent.tint(0.2f) else Color.Transparent, tween(200), label = "chipBg")
    val outline by animateColorAsState(if (selected) accent.start.copy(alpha = 0.75f) else Palette.Stroke, tween(200), label = "chipLine")
    Box(
        Modifier
            .bounceClick(onClick = onClick)
            .height(36.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, outline, CircleShape)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Palette.TextSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
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
        if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 480f), label = "check",
    )
    Canvas(modifier.size(24.dp)) {
        val radius = size.minDimension / 2
        val p = progress.coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = 0.35f * (1f - p)), radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        if (progress > 0.01f) {
            drawCircle(accent.start, radius * min(progress, 1.05f))
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

// MARK: - Information

/** Feature chip of the empty screens: a Material [icon] or a [platform] logo; [dimmed] marks something unavailable. */
@Composable
fun FeatureChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, platform: Platform? = null, dimmed: Boolean = false) {
    Row(
        modifier
            .graphicsLayer { alpha = if (dimmed) 0.5f else 1f }
            .height(34.dp)
            .panel(CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (platform != null) {
            PlatformGlyph(platform, Modifier.size(17.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(16.dp))
        }
        Text(text, color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Numbered step of a short how-to. */
@Composable
fun StepRow(number: Int, text: AnnotatedString, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.tint(0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = accent.start, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One-line notice (engine problem, integrity alert) with an optional ✕. */
@Composable
fun NoticeBanner(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .panel(RoundedCornerShape(18.dp), Palette.Surface)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        onDismiss?.let { IconCircleButton(Icons.Rounded.Close, size = 30.dp, onClick = it) }
    }
}

// MARK: - Progress

/** Gradient bar; negative [progress] = indeterminate sweep. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
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
        }
    }
}

/** Green disc whose tick draws itself (finished download). */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, animate: Boolean = true) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f)) }
    Canvas(modifier) {
        val p = progress.value
        drawCircle(Palette.Success, radius = size.minDimension / 2 * min(p, 1.04f))
        val path = Path().apply {
            moveTo(size.width * 0.29f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.66f)
            lineTo(size.width * 0.72f, size.height * 0.37f)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * p.coerceIn(0f, 1f), segment, true)
        drawPath(segment, Palette.Background, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
