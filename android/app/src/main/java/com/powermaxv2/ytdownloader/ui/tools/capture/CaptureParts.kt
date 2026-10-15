package com.powermaxv2.ytdownloader.ui.tools.capture

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.capture.CapturePlan
import com.powermaxv2.ytdownloader.core.capture.CaptureSource
import com.powermaxv2.ytdownloader.core.capture.CaptureUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.roundToLong

// The Capture d'image's pieces: the viewfinder (the frame at the playhead inside four camera corners, a light that
// scans it while the sharpest frame is hunted), the strip of frames with its playhead, the ±1 image / ±1 s steps,
// and the sharpness gauge.

/** The frame at the playhead, framed like a camera's viewfinder, with the time and the frame number on it. */
@Composable
internal fun Viewfinder(state: CaptureUiState, source: CaptureSource) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val ratio = (source.width.toFloat() / source.height.coerceAtLeast(1)).coerceIn(0.56f, 2.2f)
    val scan = rememberInfiniteTransition(label = "captureScan")
    val sweep by scan.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "captureSweep")
    val breathe by scan.animateFloat(0.92f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "captureCorners")
    // A blink each time the hunt lands on a sharper frame.
    val blink = remember { Animatable(0f) }
    LaunchedEffect(state.hunt) {
        if (state.hunt?.moved == true) {
            blink.snapTo(0.7f)
            blink.animateTo(0f, tween(420))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(maxOf(ratio, 0.8f))
            .clip(RoundedCornerShape(if (terminal) 4.dp else LocalTokens.current.fieldRadius))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(state.preview, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) }, label = "capturePreview") { bitmap ->
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = "Image à ${CapturePlan.timecode(state.positionMs)}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(previewPlaceholder(skin)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(46.dp))
                }
            }
        }
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val color = if (state.hunting) accent.start else Color.White.copy(alpha = 0.9f)
            val arm = size.minDimension * 0.12f * if (state.hunting) breathe else 1f
            val stroke = 3.dp.toPx()
            val inset = if (state.hunting) (1f - breathe) * 40f else 0f
            listOf(
                Offset(inset, inset) to Offset(1f, 1f),
                Offset(size.width - inset, inset) to Offset(-1f, 1f),
                Offset(inset, size.height - inset) to Offset(1f, -1f),
                Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
            ).forEach { (corner, dir) ->
                drawLine(color, corner, Offset(corner.x + arm * dir.x, corner.y), strokeWidth = stroke, cap = if (terminal) StrokeCap.Square else StrokeCap.Round)
                drawLine(color, corner, Offset(corner.x, corner.y + arm * dir.y), strokeWidth = stroke, cap = if (terminal) StrokeCap.Square else StrokeCap.Round)
            }
            if (state.hunting) {
                val y = size.height * sweep
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, accent.start.copy(alpha = 0.35f), Color.Transparent), startY = y - 60f, endY = y + 60f),
                    topLeft = Offset(0f, (y - 60f).coerceAtLeast(0f)),
                    size = Size(size.width, 120f),
                )
                drawLine(accent.start, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            } else {
                // Focus cross in the middle.
                val c = center
                val r = 9.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.55f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.55f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 1.5.dp.toPx())
            }
        }
        if (blink.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blink.value)))
        Badge(
            "${CapturePlan.timecode(state.positionMs)} / ${CapturePlan.timecode(source.durationMs).substringBeforeLast('.')}",
            Modifier.align(Alignment.BottomStart),
        )
        Badge(
            if (terminal) "FRAME ${state.frameIndex + 1}" else "image ${state.frameIndex + 1}",
            Modifier.align(Alignment.BottomEnd),
        )
        if (state.hunting) Badge(if (terminal) "> SCAN…" else "Recherche de l'image la plus nette…", Modifier.align(Alignment.TopCenter), accent = true)
    }
}

private fun previewPlaceholder(skin: Skin): Brush = when (skin) {
    Skin.PAPER -> Brush.linearGradient(listOf(Color(0xFF6B4A3A), Color(0xFF2A1A12)))
    Skin.TERMINAL -> Brush.linearGradient(listOf(Color(0xFF0A1A0E), Color(0xFF020604)))
    else -> Brush.linearGradient(listOf(Color(0xFF3A1D5C), Color(0xFF0E0A1F)))
}

@Composable
private fun Badge(text: String, modifier: Modifier, accent: Boolean = false) {
    Text(
        text,
        color = if (accent) Palette.OnAccent else Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) LocalAccent.current.start else Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Frames across the video under a playhead the finger drags or taps; the playhead glows while it is held. */
@Composable
internal fun CaptureStrip(state: CaptureUiState, source: CaptureSource, actions: CaptureActions) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 4.dp else 14.dp)
    var widthPx by remember { mutableIntStateOf(1) }
    val duration = source.durationMs.coerceAtLeast(1L)
    val onScrub by rememberUpdatedState(actions.onScrub)
    val onSettle by rememberUpdatedState(actions.onSettle)
    var held by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    val grow by animateFloatAsState(if (held) 1.35f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "playheadGrow")

    fun msAt(x: Float): Long = (x / widthPx.coerceAtLeast(1) * duration).roundToLong().coerceIn(0L, duration - 1)

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(Palette.Surface2)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onScrub(msAt(offset.x))
                    onSettle()
                }
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        held = true
                        dragX = offset.x
                        onScrub(msAt(dragX))
                    },
                    onDragEnd = {
                        held = false
                        onSettle()
                    },
                    onDragCancel = { held = false },
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                    onScrub(msAt(dragX))
                }
            },
    ) {
        if (state.strip.isNotEmpty()) {
            Row(Modifier.fillMaxSize()) {
                state.strip.forEach { frame ->
                    Image(frame.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            // Film perforations while the frames load (and in the screenshot tests).
            Canvas(Modifier.fillMaxSize()) {
                val holes = 22
                val hole = Size(size.width / holes * 0.5f, 6.dp.toPx())
                val ink = if (skin == Skin.PAPER) Color(0x332A0F0A) else Color(0x33FFE3D6)
                for (i in 0 until holes) {
                    val x = size.width * (i + 0.25f) / holes
                    drawRoundRect(ink, Offset(x, 6.dp.toPx()), hole, CornerRadius(2f))
                    drawRoundRect(ink, Offset(x, size.height - 12.dp.toPx()), hole, CornerRadius(2f))
                }
                for (i in 0 until 10) {
                    val x = size.width * i / 10
                    drawRect(ink, Offset(x + 3f, 16.dp.toPx()), Size(size.width / 10 - 6f, size.height - 32.dp.toPx()))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = state.positionMs.toFloat() / duration * size.width
            drawRect(Color.Black.copy(alpha = if (skin == Skin.PAPER) 0.25f else 0.4f), Offset.Zero, Size(x, size.height))
            val w = 4.dp.toPx() * grow
            drawRect(accent.start.copy(alpha = 0.35f), Offset(x - w * 2, 0f), Size(w * 4, size.height))
            drawRoundRect(accent.start, Offset(x - w / 2, 0f), Size(w, size.height), CornerRadius(if (terminal) 0f else w / 2))
            val knob = 9.dp.toPx() * grow
            drawCircle(accent.start, knob, Offset(x, knob))
            drawCircle(Color.White, knob * 0.45f, Offset(x, knob))
            drawRoundRect(accent.start, Offset(x - knob * 0.4f, size.height - 6.dp.toPx()), Size(knob * 0.8f, 6.dp.toPx()), CornerRadius(3f), style = Stroke(2f))
        }
    }
}

/** −1 s, −1 image, +1 image, +1 s. */
@Composable
internal fun StepRow(actions: CaptureActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−1 s", "Une seconde plus tôt", Modifier.weight(1f)) { actions.onJump(-1_000L) }
        StepButton("◀ 1", "Image précédente", Modifier.weight(1f)) { actions.onStep(-1L) }
        StepButton("1 ▶", "Image suivante", Modifier.weight(1f)) { actions.onStep(1L) }
        StepButton("+1 s", "Une seconde plus tard", Modifier.weight(1f)) { actions.onJump(1_000L) }
    }
}

@Composable
private fun StepButton(text: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(
        modifier
            .height(42.dp)
            .bounceClick(pressedScale = 0.86f, onClick = onClick)
            .clip(RoundedCornerShape(if (terminal) 4.dp else LocalTokens.current.chipRadius))
            .background(Palette.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (terminal) "[$text]" else text,
            color = LocalAccent.current.start,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/** Sharpness of the frame at the playhead: a gauge that fills with a spring, its word, and the hunt's gain. */
@Composable
internal fun SharpnessGauge(state: CaptureUiState) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val level = state.sharpness ?: 0f
    val fill by animateFloatAsState(level, spring(dampingRatio = 0.62f, stiffness = 140f), label = "sharpFill")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (terminal) "NETTETÉ" else "Netteté", color = Palette.TextTertiary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            val word = when {
                state.hunting -> "…"
                state.sharpness == null -> "—"
                else -> CapturePlan.verdict(level)
            }
            Text(if (terminal) word.uppercase() else word, color = Palette.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        if (terminal) {
            val cells = 24
            val on = (fill * cells).toInt().coerceIn(0, cells)
            Text("[" + "█".repeat(on) + "·".repeat(cells - on) + "]", color = accent.start, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Palette.TextPrimary.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(accent.start.copy(alpha = 0.55f), accent.start))),
                )
            }
        }
        state.hunt?.let { hunt ->
            Spacer(Modifier.height(6.dp))
            val line = if (hunt.moved && hunt.gainPercent > 0) {
                "Image ${if (hunt.toMs > hunt.fromMs) "un peu après" else "un peu avant"} : +${hunt.gainPercent} % de netteté"
            } else {
                "C'était déjà l'image la plus nette du moment."
            }
            Text(
                if (terminal) "# $line" else line,
                color = accent.start,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer { alpha = 1f },
            )
        }
    }
}

/** A row of little photos (the result), each sliding up after the other. */
@Composable
internal fun ShotRow(shots: List<android.graphics.Bitmap>) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shots.forEachIndexed { i, shot ->
            val rise = remember { Animatable(1f) }
            LaunchedEffect(shot) {
                kotlinx.coroutines.delay(90L * i)
                rise.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 260f))
            }
            Image(
                shot.asImageBitmap(),
                contentDescription = "Photo ${i + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(if (shots.size == 1) shot.width.toFloat() / shot.height.coerceAtLeast(1) else 0.8f)
                    .graphicsLayer {
                        translationY = rise.value * 60f
                        alpha = 1f - rise.value
                        rotationZ = if (shots.size > 1) (i - (shots.size - 1) / 2f) * 2.5f * rise.value else 0f
                    }
                    .clip(RoundedCornerShape(if (terminal) 2.dp else 10.dp))
                    .background(Palette.Surface2),
            )
        }
        if (shots.isEmpty()) Spacer(Modifier.width(0.dp))
    }
}
