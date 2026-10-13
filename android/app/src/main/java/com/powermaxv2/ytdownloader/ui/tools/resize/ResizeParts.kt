package com.powermaxv2.ytdownloader.ui.tools.resize

import android.net.Uri
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.resize.CropRatio
import com.powermaxv2.ytdownloader.core.resize.Dims
import com.powermaxv2.ytdownloader.core.resize.ResizeResult
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import com.powermaxv2.ytdownloader.ui.tools.wipe.PhotoThumb
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

// The Redimensionneur's moving pieces: the frame that shows the new size over the old one, the ratio tiles, the
// looping demo of the empty screen, the shrinking photo of the progress step and the lines of the result.

/** Symbol of the tool (home card, drop zone, buttons). */
val ResizeIcon: ImageVector get() = Icons.Rounded.PhotoSizeSelectLarge

/**
 * The old picture as a dashed outline and the copy as a filled frame inside it, both to scale: when a setting
 * changes the frame springs to its new shape and size, corner handles first. [from] / [to] = sizes in pixels.
 */
@Composable
internal fun SizeFrame(from: Dims, to: Dims, modifier: Modifier = Modifier, height: Dp = 190.dp) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val terminal = skin == Skin.TERMINAL
    val box = max(1, from.longest).toFloat()
    // Shares of the old picture's longest side: the frame never shows bigger than the original.
    val w by animateFloatAsState(to.width / box, spring(dampingRatio = 0.62f, stiffness = 220f), label = "frameW")
    val h by animateFloatAsState(to.height / box, spring(dampingRatio = 0.62f, stiffness = 220f), label = "frameH")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700, delayMillis = 120, easing = FastOutSlowInEasing)) }
    val dash = rememberInfiniteTransition(label = "frameDash")
    val phase by dash.animateFloat(0f, 40f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dashPhase")
    val oldColor = Palette.TextTertiary
    val track = Palette.TextPrimary.copy(alpha = 0.05f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val unit = min(size.width, size.height) * 0.92f
        val ow = from.width / box * unit
        val oh = from.height / box * unit
        val oldTopLeft = Offset((size.width - ow) / 2, (size.height - oh) / 2)
        val radius = if (terminal) 0f else 10.dp.toPx()
        drawRoundRect(track, oldTopLeft, Size(ow, oh), CornerRadius(radius))
        drawRoundRect(
            oldColor,
            oldTopLeft,
            Size(ow, oh),
            CornerRadius(radius),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), phase)),
        )
        val p = appear.value
        val nw = (w * unit).coerceAtLeast(6f) * (0.4f + 0.6f * p)
        val nh = (h * unit).coerceAtLeast(6f) * (0.4f + 0.6f * p)
        val topLeft = Offset((size.width - nw) / 2, (size.height - nh) / 2)
        val fill = when (skin) {
            Skin.CLASSIC -> Brush.linearGradient(listOf(accent.start.copy(alpha = 0.85f), accent.end.copy(alpha = 0.85f)), topLeft, topLeft + Offset(nw, nh))
            Skin.PAPER -> Brush.linearGradient(listOf(accent.start.copy(alpha = 0.18f), accent.start.copy(alpha = 0.18f)))
            Skin.TERMINAL -> Brush.linearGradient(listOf(accent.start.copy(alpha = 0.22f), accent.start.copy(alpha = 0.22f)))
        }
        drawRoundRect(fill, topLeft, Size(nw, nh), CornerRadius(radius), alpha = p)
        drawRoundRect(accent.start, topLeft, Size(nw, nh), CornerRadius(radius), style = Stroke(2.dp.toPx()), alpha = p)
        handles(topLeft, Size(nw, nh), accent.start, p)
    }
}

/** Four L-shaped corner handles around a frame. */
private fun DrawScope.handles(topLeft: Offset, frame: Size, color: Color, alpha: Float) {
    val l = min(14.dp.toPx(), min(frame.width, frame.height) / 2.5f)
    val s = 3.5.dp.toPx()
    val o = s / 2
    val corners = listOf(
        Triple(topLeft + Offset(-o, -o), 1f, 1f),
        Triple(topLeft + Offset(frame.width + o, -o), -1f, 1f),
        Triple(topLeft + Offset(-o, frame.height + o), 1f, -1f),
        Triple(topLeft + Offset(frame.width + o, frame.height + o), -1f, -1f),
    )
    corners.forEach { (c, dx, dy) ->
        drawLine(color, c, c + Offset(l * dx, 0f), s, StrokeCap.Round, alpha = alpha)
        drawLine(color, c, c + Offset(0f, l * dy), s, StrokeCap.Round, alpha = alpha)
    }
}

/** "4032 × 3024  →  1920 × 1440": the new size in the accent, sliding in when it changes. */
@Composable
internal fun DimsLine(from: Dims?, to: Dims?, extra: String? = null, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    val bump = remember { Animatable(1f) }
    LaunchedEffect(to) {
        bump.snapTo(0.86f)
        bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(from?.toString() ?: "?", color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1)
        Text(if (terminal) "  ->  " else "  →  ", color = Palette.TextTertiary, fontSize = 14.sp)
        Text(
            to?.toString() ?: "?",
            color = accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
        )
        extra?.let { Text("  $it", color = Palette.TextTertiary, fontSize = 12.5.sp, maxLines = 1) }
    }
}

/** The shapes the photo can be cut to: each tile draws its shape, the chosen one fills with the accent. */
@Composable
internal fun RatioTiles(selected: CropRatio, onSelect: (CropRatio) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CropRatio.entries.forEachIndexed { index, ratio ->
            RatioTile(ratio, ratio == selected, index, Modifier.weight(1f)) { onSelect(ratio) }
        }
    }
}

@Composable
private fun RatioTile(ratio: CropRatio, selected: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val terminal = skin == Skin.TERMINAL
    val on by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "ratioOn")
    val radius = if (terminal) LocalTokens.current.cardRadius else 14.dp
    val idle = Palette.TextTertiary
    Column(
        modifier
            .staggeredEntrance(index, baseDelay = 60)
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(accent.start.copy(alpha = 0.10f * on + 0.03f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val (rw, rh) = if (ratio == CropRatio.NONE) 3f to 2f else ratio.w.toFloat() to ratio.h.toFloat()
            val scale = min(size.width / rw, size.height / rh) * (0.86f + 0.14f * on)
            val fw = rw * scale
            val fh = rh * scale
            val tl = Offset((size.width - fw) / 2, (size.height - fh) / 2)
            val r = if (terminal) 0f else 3.dp.toPx()
            val color = lerp(idle, accent.start, on)
            if (ratio == CropRatio.NONE) {
                drawRoundRect(color, tl, Size(fw, fh), CornerRadius(r), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
            } else {
                drawRoundRect(color.copy(alpha = 0.25f * on), tl, Size(fw, fh), CornerRadius(r))
                drawRoundRect(color, tl, Size(fw, fh), CornerRadius(r), style = Stroke(1.8.dp.toPx()))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            ratio.label,
            color = if (selected) accent.start else Palette.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Empty screen: a frame that keeps changing shape (free, square, portrait, wide) with its size written inside. */
@Composable
internal fun ResizeDemo(modifier: Modifier = Modifier) {
    val shapes = remember {
        listOf(Dims(4032, 3024) to "4032 × 3024", Dims(1080, 1080) to "1:1", Dims(1080, 1350) to "4:5", Dims(1920, 1080) to "16:9", Dims(1280, 960) to "1280 px")
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            step = (step + 1) % shapes.size
        }
    }
    val (dims, label) = shapes[step]
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SizeFrame(Dims(4032, 4032), dims, height = 150.dp)
        Text(
            label,
            color = LocalAccent.current.start,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
        )
    }
}

/** The photo being worked on, breathing smaller and back, with its handles; [uri] null = a plain frame. */
@Composable
internal fun ShrinkingPhoto(uri: Uri?, index: Int, modifier: Modifier = Modifier, side: Dp = 176.dp) {
    val accent = LocalAccent.current.start
    val loop = rememberInfiniteTransition(label = "shrink")
    val k by loop.animateFloat(1f, 0.72f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "shrinkK")
    Box(modifier.size(side), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                accent.copy(alpha = 0.35f),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))),
                cornerRadius = CornerRadius(14.dp.toPx()),
            )
        }
        Box(
            Modifier
                .size(side * 0.94f)
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                },
        ) {
            if (uri != null) PhotoThumb(uri, index, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) { handles(Offset.Zero, size, accent, 1f) }
        }
    }
}

/** One saved copy: its name, old → new size, old → new weight; slides in [index] by [index]. */
@Composable
internal fun ResultRow(result: ResizeResult, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 320)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) { PhotoThumb(result.saved.uri, index, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.name, color = Palette.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${result.from} ${if (terminal) "->" else "→"} ${result.dims}",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(bytes(result.after), color = if (result.overWeight) Palette.Danger else accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(bytes(result.before), color = Palette.TextTertiary, fontSize = 11.5.sp, maxLines = 1, textAlign = TextAlign.End)
        }
    }
}

/** A tiny badge riding on the frame ("JPG · 85"), offset from its corner. */
@Composable
internal fun FormatBadge(text: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pop = remember(text) { Animatable(0.5f) }
    LaunchedEffect(text) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f)) }
    Row(
        modifier
            .offset(y = (-2).dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(RoundedCornerShape(if (terminal) 0.dp else 50.dp))
            .background(accent.start)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ResizeIcon, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(if (terminal) text.uppercase() else text, color = Palette.OnAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
