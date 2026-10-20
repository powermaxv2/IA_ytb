package com.powermaxv2.ytdownloader.ui.tools.gif

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.gif.GifEffect
import com.powermaxv2.ytdownloader.core.gif.GifPhoto
import com.powermaxv2.ytdownloader.core.gif.GifPlan
import com.powermaxv2.ytdownloader.core.gif.GifSource
import com.powermaxv2.ytdownloader.core.gif.GifUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import kotlinx.coroutines.delay
import kotlin.math.min

// The Créateur de GIF's moving parts: the Vidéo | Photos switch, the live preview that plays the GIF before it exists
// (order, speed, shape and caption as chosen), the weight gauge, and the photo strip.

/** "Vidéo | Photos": the thumb slides under the chosen side with a spring. */
@Composable
internal fun SourceSwitch(source: GifSource, onChange: (GifSource) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val slide by animateFloatAsState(if (source == GifSource.PHOTOS) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "gifSource")
    val radius = if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val half = size.width / 2
            val brush = when (skin) {
                Skin.CLASSIC -> Brush.horizontalGradient(listOf(accent.start, accent.end), startX = slide * half, endX = slide * half + half)
                Skin.PAPER -> Brush.linearGradient(listOf(accent.start, accent.start))
                Skin.TERMINAL -> Brush.linearGradient(listOf(accent.start, accent.start))
            }
            // The thumb stretches a little while it travels (squash and stretch).
            val stretch = 1f + 0.18f * (1f - kotlin.math.abs(slide - 0.5f) * 2f).coerceIn(0f, 1f)
            val w = half * stretch
            val x = (slide * half - (w - half) / 2).coerceIn(0f, size.width - w)
            drawRoundRect(brush, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(radius.toPx()))
        }
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            GifSource.entries.forEach { value ->
                val on = value == source
                val ink by animateColorAsState(
                    when {
                        !on -> Palette.TextSecondary
                        skin == Skin.TERMINAL -> Palette.Background
                        else -> Palette.OnAccent
                    },
                    tween(200),
                    label = "gifSourceInk",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .bounceClick(pressedScale = 0.95f) { onChange(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (skin == Skin.TERMINAL) value.label.uppercase() else value.label,
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

/**
 * The GIF before it is made: the [frames] played in the chosen order and rhythm, cropped to the GIF's shape (the box
 * morphs when the shape changes), the caption outlined at the bottom, a "GIF" badge that pulses and dots that follow
 * the frame on screen. [still] freezes it on one frame (screenshot tests).
 */
@Composable
internal fun GifPreview(state: GifUiState, frames: List<Bitmap>, still: Boolean = false, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val geometry = state.geometry
    val targetRatio = geometry?.let { it.width.toFloat() / it.height } ?: 1f
    val ratio by animateFloatAsState(targetRatio.coerceIn(0.5f, 2f), spring(dampingRatio = 0.75f, stiffness = 260f), label = "gifRatio")
    val order = remember(frames.size, state.settings.effect) { GifPlan.order(frames.size, state.settings.effect) }
    val frameMs = when (state.source) {
        GifSource.VIDEO -> GifPlan.previewFrameMs(state.range, frames.size, state.settings.speed)
        GifSource.PHOTOS -> state.settings.pace.frameMs
    }
    var step by remember(order) { mutableIntStateOf(if (still) order.size / 3 else 0) }
    LaunchedEffect(order, frameMs, still) {
        if (still || order.size < 2) return@LaunchedEffect
        while (true) {
            delay(frameMs)
            step = (step + 1) % order.size
        }
    }
    val images = remember(frames) { frames.map { it.asImageBitmap() } }
    val current: ImageBitmap? = order.getOrNull(step)?.let { images.getOrNull(it) }

    val pulse = rememberInfiniteTransition(label = "gifBadge")
    val badge by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gifBadgeScale")
    val radius = if (skin == Skin.TERMINAL) 4.dp else LocalTokens.current.fieldRadius

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(
                Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1.3f)
                    .clip(RoundedCornerShape(radius))
                    .background(Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (current != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val crop = GifPlan.cover(current.width, current.height, targetRatio)
                        drawImage(
                            current,
                            srcOffset = IntOffset(crop.x, crop.y),
                            srcSize = IntSize(crop.width, crop.height),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                } else {
                    EmptyPreview()
                }
                val caption = GifPlan.cleanCaption(state.settings.caption)
                if (caption.isNotEmpty()) {
                    val density = LocalDensity.current
                    val px = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) } * 0.12f
                    val fit = (with(density) { maxWidth.toPx() } * 0.92f / (caption.length * px * 0.58f)).coerceAtMost(1f)
                    val fontSize = with(density) { (px * fit).toSp() }
                    Box(Modifier.fillMaxSize().padding(bottom = with(density) { (px * fit * 0.25f).toDp() }), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            caption,
                            style = TextStyle(
                                color = Color.Black,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                drawStyle = Stroke(width = with(density) { (px * fit * 0.14f) }),
                            ),
                            maxLines = 1,
                        )
                        Text(
                            caption,
                            style = TextStyle(color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    "GIF",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .scale(if (still) 1f else badge)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (skin == Skin.CLASSIC) accent.brush else Brush.linearGradient(listOf(accent.start, accent.start)))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (state.settings.effect != GifEffect.NORMAL && order.isNotEmpty()) {
                    val back = order.size > 1 && step > 0 && order[step] < order[step - 1]
                    Text(
                        if (back) "◀◀" else "▶",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (order.size > 1) {
            Spacer(Modifier.height(10.dp))
            FrameDots(order.size, step)
        }
    }
}

/** While nothing is drawn yet: a GIF symbol breathing over a sweeping light. */
@Composable
private fun EmptyPreview() {
    val t = rememberInfiniteTransition(label = "gifEmpty")
    val sweep by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "gifEmptySweep")
    val breathe by t.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gifEmptyBreathe")
    val highlight = Palette.TextPrimary.copy(alpha = 0.08f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sweep
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent), startX = x - 120f, endX = x + 120f))
        }
        Icon(Icons.Rounded.Gif, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(64.dp).scale(breathe))
    }
}

/** One dot per frame (at most 24 drawn); the one on screen grows and takes the accent. */
@Composable
private fun FrameDots(count: Int, current: Int) {
    val accent = LocalAccent.current.start
    val shown = min(count, 24)
    val active = if (count <= shown) current else current * shown / count
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(shown) { i ->
            val on = i == active
            val grow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "gifDot")
            Box(
                Modifier
                    .size(width = (5 + 9 * grow).dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Palette.TextTertiary.copy(alpha = 0.35f)),
            )
        }
    }
}

/**
 * Estimated weight as a bar that fills towards 8 Mo (the bar turns warm past 5 Mo), with the number rolling to its
 * new value whenever a choice changes it.
 */
@Composable
internal fun WeightGauge(bytesValue: Long, frames: Int, durationMs: Long, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shown by animateFloatAsState(bytesValue.toFloat(), tween(450, easing = FastOutSlowInEasing), label = "gifWeight")
    val fill = (shown / HEAVY_BYTES).coerceIn(0.02f, 1f)
    val heavy = bytesValue > WARN_BYTES
    val bar by animateColorAsState(if (heavy) Color(0xFFE8A13A) else accent.start, tween(300), label = "gifWeightColor")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (skin == Skin.TERMINAL) "POIDS ESTIMÉ" else "Poids estimé", color = Palette.TextTertiary, fontSize = 12.sp)
                Text(
                    "≈ ${bytes(shown.toLong())}",
                    color = Palette.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else null,
                )
            }
            Text(
                "$frames images · ${GifPlan.shortDuration(durationMs)}",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Surface2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (skin == Skin.CLASSIC && !heavy) accent.brush else Brush.linearGradient(listOf(bar, bar))),
            )
        }
    }
}

private const val WARN_BYTES = 5L * 1024 * 1024
private const val HEAVY_BYTES = 8f * 1024 * 1024

/** The photos of the GIF in order, each with ← → to move it and × to take it out, and a + tile at the end. */
@Composable
internal fun PhotoStrip(photos: List<GifPhoto>, onMove: (Long, Int) -> Unit, onRemove: (Long) -> Unit, onAdd: () -> Unit) {
    val radius = if (LocalSkin.current == Skin.TERMINAL) 4.dp else 12.dp
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(photos, key = { _, p -> p.id }) { i, photo ->
            Column(Modifier.width(84.dp).staggeredEntrance(i, key = photo.id), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(radius))
                        .background(Palette.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = photo.thumb
                    if (thumb != null) {
                        Image(thumb.asImageBitmap(), contentDescription = photo.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.Image, contentDescription = photo.name, tint = Palette.TextTertiary)
                    }
                    Text(
                        "${i + 1}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    SmallRound(Icons.Rounded.Close, "Retirer", Modifier.align(Alignment.TopEnd).padding(4.dp)) { onRemove(photo.id) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallRound(Icons.AutoMirrored.Rounded.ArrowBack, "Avant", enabled = i > 0) { onMove(photo.id, -1) }
                    SmallRound(Icons.AutoMirrored.Rounded.ArrowForward, "Après", enabled = i < photos.size - 1) { onMove(photo.id, 1) }
                }
            }
        }
        item(key = "add") {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(radius))
                    .border(1.5.dp, LocalAccent.current.start.copy(alpha = 0.6f), RoundedCornerShape(radius))
                    .bounceClick(pressedScale = 0.9f, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Ajouter des photos", tint = LocalAccent.current.start, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .size(24.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}
