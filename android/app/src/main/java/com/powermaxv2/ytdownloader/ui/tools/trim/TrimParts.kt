package com.powermaxv2.ytdownloader.ui.tools.trim

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.trim.TrimPlan
import com.powermaxv2.ytdownloader.core.trim.TrimSource
import com.powermaxv2.ytdownloader.core.trim.TrimUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.abs
import kotlin.math.roundToLong

// The Découpeur's timeline: frames of the video (or the sound's waveform) under two handles that the finger drags,
// the outside of the passage dimmed, and a light that sweeps through the kept part; then the start / length / end
// read-out with ±0.1 s nudges.

private const val NUDGE_MS = 100L

@Composable
internal fun TrimTimeline(state: TrimUiState, source: TrimSource, actions: TrimActions) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 4.dp else 14.dp)
    var widthPx by remember { mutableIntStateOf(1) }
    val duration = source.durationMs.coerceAtLeast(1L)
    val range by rememberUpdatedState(state.range)
    val onStart by rememberUpdatedState(actions.onStart)
    val onEnd by rememberUpdatedState(actions.onEnd)
    val onSettle by rememberUpdatedState(actions.onSettle)
    /** Which handle the finger holds: -1 start, +1 end, 0 none. */
    var held by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableStateOf(0f) }

    val sweep = rememberInfiniteTransition(label = "trimSweep")
    val light by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "trimLight")

    fun msAt(x: Float): Long = (x / widthPx.coerceAtLeast(1) * duration).roundToLong().coerceIn(0L, duration)

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(Palette.Surface2)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    // A tap moves the closest handle there.
                    val ms = msAt(offset.x)
                    if (abs(ms - range.startMs) <= abs(ms - range.endMs)) onStart(ms) else onEnd(ms)
                    onSettle()
                }
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val startX = range.startMs.toFloat() / duration * widthPx
                        val endX = range.endMs.toFloat() / duration * widthPx
                        held = if (abs(offset.x - startX) <= abs(offset.x - endX)) -1 else 1
                        dragX = offset.x
                    },
                    onDragEnd = {
                        held = 0
                        onSettle()
                    },
                    onDragCancel = { held = 0 },
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                    if (held < 0) onStart(msAt(dragX)) else if (held > 0) onEnd(msAt(dragX))
                }
            },
    ) {
        // Under the handles: frames, waveform, or a ruler while they load (and for the screenshot tests).
        val frames = state.strip.frames
        val wave = state.strip.wave
        when {
            frames.isNotEmpty() -> Row(Modifier.fillMaxSize()) {
                frames.forEach { frame ->
                    Image(frame.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            else -> Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
                val bars = wave ?: FloatArray(0)
                if (bars.isNotEmpty()) {
                    val step = size.width / bars.size
                    bars.forEachIndexed { i, v ->
                        val h = (v.coerceIn(0.04f, 1f)) * size.height
                        drawRoundRect(
                            accent.start.copy(alpha = 0.85f),
                            topLeft = Offset(i * step + step * 0.2f, (size.height - h) / 2),
                            size = Size(step * 0.6f, h),
                            cornerRadius = CornerRadius(step * 0.3f),
                        )
                    }
                } else {
                    // Ruler: a tick every 1/40 of the length, a taller one every 1/8.
                    val ticks = 40
                    for (i in 0..ticks) {
                        val x = size.width * i / ticks
                        val tall = i % 5 == 0
                        val h = if (tall) size.height * 0.7f else size.height * 0.3f
                        drawLine(tickColor(skin), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = if (tall) 3f else 2f)
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val sx = range.startMs.toFloat() / duration * size.width
            val ex = range.endMs.toFloat() / duration * size.width
            val dim = Color.Black.copy(alpha = if (skin == Skin.PAPER) 0.38f else 0.55f)
            drawRect(dim, topLeft = Offset.Zero, size = Size(sx, size.height))
            drawRect(dim, topLeft = Offset(ex, 0f), size = Size(size.width - ex, size.height))
            // Light sweeping through the kept passage.
            val span = (ex - sx).coerceAtLeast(1f)
            val lx = sx + span * light
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), startX = lx - 40f, endX = lx + 40f),
                topLeft = Offset(sx, 0f),
                size = Size(span, size.height),
            )
            val stroke = 3.dp.toPx()
            val r = if (terminal) 2.dp.toPx() else 10.dp.toPx()
            drawRoundRect(
                accent.start,
                topLeft = Offset(sx + stroke / 2, stroke / 2),
                size = Size((ex - sx - stroke).coerceAtLeast(1f), size.height - stroke),
                cornerRadius = CornerRadius(r),
                style = Stroke(stroke),
            )
            val handleW = 14.dp.toPx()
            val handleH = size.height * 0.46f
            listOf(sx to (held < 0), ex to (held > 0)).forEach { (x, active) ->
                val grow = if (active) 1.25f else 1f
                val w = handleW * grow
                val h = handleH * grow
                drawRoundRect(
                    accent.start,
                    topLeft = Offset(x - w / 2, (size.height - h) / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(if (terminal) 2f else w / 2.5f),
                )
                drawLine(Color.White.copy(alpha = 0.9f), Offset(x, size.height / 2 - h / 5), Offset(x, size.height / 2 + h / 5), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/** Ruler ticks: faint ink on Papier, faint light elsewhere. */
private fun tickColor(skin: Skin): Color = when (skin) {
    Skin.PAPER -> Color(0x332A0F0A)
    else -> Color(0x33FFE3D6)
}

/** Start, length and end of the passage, with nudges of a tenth of a second for the handles. */
@Composable
internal fun TimeReadout(state: TrimUiState, actions: TrimActions) {
    val accent = LocalAccent.current
    val range = state.range
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        TimeBlock("Début", TrimPlan.timecode(range.startMs), Alignment.Start, Modifier.weight(1f)) {
            Nudge(Icons.Rounded.Remove, "Début plus tôt") { actions.onStart(range.startMs - NUDGE_MS); actions.onSettle() }
            Nudge(Icons.Rounded.Add, "Début plus tard") { actions.onStart(range.startMs + NUDGE_MS); actions.onSettle() }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Durée")
            Text(TrimPlan.timecode(range.durationMs), color = accent.start, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            Spacer(Modifier.height(30.dp))
        }
        TimeBlock("Fin", TrimPlan.timecode(range.endMs), Alignment.End, Modifier.weight(1f)) {
            Nudge(Icons.Rounded.Remove, "Fin plus tôt") { actions.onEnd(range.endMs - NUDGE_MS) }
            Nudge(Icons.Rounded.Add, "Fin plus tard") { actions.onEnd(range.endMs + NUDGE_MS) }
        }
    }
}

@Composable
private fun TimeBlock(
    label: String,
    value: String,
    align: Alignment.Horizontal,
    modifier: Modifier,
    nudges: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = align) {
        Label(label)
        Text(value, color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { nudges() }
    }
}

@Composable
private fun Label(text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(if (terminal) text.uppercase() else text, color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
}

@Composable
private fun Nudge(icon: ImageVector, description: String, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(
        Modifier
            .size(30.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .clip(RoundedCornerShape(if (terminal) 4.dp else LocalTokens.current.chipRadius))
            .background(Palette.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = LocalAccent.current.start, modifier = Modifier.size(18.dp))
    }
}
