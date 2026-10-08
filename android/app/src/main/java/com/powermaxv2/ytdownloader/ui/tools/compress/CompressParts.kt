package com.powermaxv2.ytdownloader.ui.tools.compress

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.compress.Media
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.kindIcon
import kotlin.math.roundToInt

// Pieces of the Compresseur drawn in the three skins: the before / after bars, the "−74 %" counter, the
// "chacun / un seul ZIP" switch, file rows and switch rows.

internal fun Media.icon(): ImageVector = kindIcon(
    when (this) {
        Media.IMAGE -> FileKind.IMAGE
        Media.VIDEO -> FileKind.VIDEO
        Media.AUDIO -> FileKind.AUDIO
        Media.PDF -> FileKind.PDF
        Media.OTHER -> FileKind.OTHER
    },
)

internal fun bytes(value: Long): String = Fmt.bytes(value.toDouble())

/**
 * Two bars, "Avant" full and "Après" at [after] / [before]: the "Après" bar grows (or shrinks) with a spring when
 * the value changes, so the gain is seen, not read. [estimated] puts "≈" before the value.
 */
@Composable
internal fun SizeBars(before: Long?, after: Long?, estimated: Boolean, modifier: Modifier = Modifier) {
    val ratio = if (before != null && after != null && before > 0) (after.toFloat() / before).coerceIn(0f, 1f) else 0f
    val grown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grown.animateTo(1f, tween(650, delayMillis = 150, easing = FastOutSlowInEasing)) }
    val shown by animateFloatAsState(ratio, spring(dampingRatio = 0.7f, stiffness = 180f), label = "afterBar")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SizeBar("Avant", before?.let(::bytes) ?: "?", grown.value, old = true)
        SizeBar(
            "Après",
            after?.let { (if (estimated) "≈ " else "") + bytes(it) } ?: "…",
            shown * grown.value,
            old = false,
        )
    }
}

@Composable
private fun SizeBar(label: String, value: String, fraction: Float, old: Boolean) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val terminal = skin == Skin.TERMINAL
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (terminal) label.uppercase() else label,
            color = Palette.TextSecondary,
            fontSize = if (terminal) 11.5.sp else 13.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        val track = Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.08f else 0.07f)
        val oldFill = Palette.TextTertiary
        Canvas(
            Modifier
                .weight(1f)
                .height(if (terminal) 12.dp else 14.dp),
        ) {
            val r = if (terminal) 0f else size.height / 2
            val w = size.width * fraction
            if (terminal) {
                // Phosphor cells: filled blocks, then a dotted rest.
                val cell = 7.dp.toPx()
                val gap = 2.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    val filled = x + cell <= w + 0.5f
                    drawRect(
                        if (filled) (if (old) oldFill else accent.start) else track,
                        topLeft = Offset(x, if (filled) 0f else size.height * 0.35f),
                        size = Size(minOf(cell, size.width - x), if (filled) size.height else size.height * 0.3f),
                    )
                    x += cell + gap
                }
            } else {
                drawRoundRect(track, cornerRadius = CornerRadius(r))
                if (w > 0.5f) {
                    val brush = when {
                        old -> Brush.linearGradient(listOf(oldFill, oldFill))
                        skin == Skin.PAPER -> Brush.linearGradient(listOf(accent.start, accent.start))
                        else -> Brush.horizontalGradient(listOf(accent.start, accent.end), endX = size.width)
                    }
                    drawRoundRect(brush, size = Size(maxOf(w, size.height), size.height), cornerRadius = CornerRadius(r))
                }
            }
        }
        Text(
            value,
            color = if (old) Palette.TextPrimary else accent.start,
            fontSize = if (terminal) 12.5.sp else 13.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(86.dp),
        )
    }
}

/** "−74 %" counting up from 0 with a little overshoot, in the skin's display face. */
@Composable
internal fun GainCounter(percent: Int, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val count = remember { Animatable(0f) }
    LaunchedEffect(percent) { count.animateTo(percent.toFloat(), tween(1100, delayMillis = 250, easing = FastOutSlowInEasing)) }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val text = "−${count.value.roundToInt()} %"
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = if (skin == Skin.TERMINAL) 68.sp else 74.sp,
        lineHeight = 80.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
        brush = if (skin == Skin.CLASSIC) accent.horizontal() else null,
    )
    Text(
        text,
        color = when (skin) {
            Skin.CLASSIC -> Color.Unspecified
            Skin.PAPER -> accent.start
            Skin.TERMINAL -> Palette.TextPrimary
        },
        style = style,
        maxLines = 1,
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    )
}

/** Two-way switch "Alléger chacun | Un seul ZIP": the thumb slides under the chosen side. */
@Composable
internal fun ModeSwitch(zip: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val slide by animateFloatAsState(if (zip) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f), label = "modeThumb")
    val radius: Dp = if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp
    val shape = RoundedCornerShape(radius)
    val paperThumb = Palette.Surface
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(4.dp),
    ) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val half = size.width / 2
            val brush = when (skin) {
                Skin.CLASSIC -> Brush.horizontalGradient(listOf(accent.start, accent.end), startX = slide * half, endX = slide * half + half)
                Skin.PAPER -> Brush.linearGradient(listOf(paperThumb, paperThumb))
                Skin.TERMINAL -> Brush.linearGradient(listOf(accent.start, accent.start))
            }
            drawRoundRect(brush, topLeft = Offset(slide * half, 0f), size = Size(half, size.height), cornerRadius = CornerRadius(radius.toPx()))
        }
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            listOf(false to "Alléger chacun", true to "Un seul ZIP").forEach { (value, label) ->
                val on = value == zip
                val ink by animateColorAsState(
                    when {
                        !on -> Palette.TextSecondary
                        skin == Skin.CLASSIC -> Palette.OnAccent
                        skin == Skin.TERMINAL -> Palette.Background
                        else -> Palette.TextPrimary
                    },
                    tween(200),
                    label = "modeInk",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .bounceClick(pressedScale = 0.95f) { onChange(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (skin == Skin.TERMINAL) label.uppercase() else label,
                        color = ink,
                        fontSize = if (skin == Skin.TERMINAL) 12.sp else 14.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Canvas behind the switch labels, as big as the box (matchParentSize needs a BoxScope receiver). */
private fun Modifier.matchParentSizeCompat(): Modifier = this
    .fillMaxWidth()
    .height(40.dp)

/** A chosen file in the list: icon, name, size, and a × to take it out ([onRemove] null = no ×). */
@Composable
internal fun FileRow(name: String, size: Long?, media: Media, index: Int, onRemove: (() -> Unit)?) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = name, baseDelay = 60)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(media.icon(), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            if (terminal) name.lowercase() else name,
            color = Palette.TextPrimary,
            fontSize = 14.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(size?.let(::bytes) ?: "", color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1)
        onRemove?.let {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .bounceClick(pressedScale = 0.85f, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Retirer $name", tint = Palette.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A setting that is on or off, with a line saying why it matters. */
@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (terminal) "> $title" else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(if (terminal) "# $subtitle" else subtitle, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = accent.start,
                checkedBorderColor = accent.start,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.TextPrimary.copy(alpha = 0.06f),
                uncheckedBorderColor = Palette.TextPrimary.copy(alpha = 0.18f),
            ),
        )
    }
}

/** A line of the "Gulp sait alléger" card. */
@Composable
internal fun SupportRow(media: Media, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(media.icon(), size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(if (terminal) detail.lowercase() else detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
    }
}

/** Hint under a block, or a warning in the danger colour. */
@Composable
internal fun Hint(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) "# $text" else text,
        color = if (warning) Palette.Danger else Palette.TextTertiary,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = modifier,
    )
}
