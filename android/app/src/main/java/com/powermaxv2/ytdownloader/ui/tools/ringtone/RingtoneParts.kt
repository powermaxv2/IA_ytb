package com.powermaxv2.ytdownloader.ui.tools.ringtone

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.ringtone.RingUiState
import com.powermaxv2.ytdownloader.core.ringtone.RingtonePlan
import com.powermaxv2.ytdownloader.core.ringtone.RingtoneUse
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin

// The Sonneries tool's moving parts: the waveform editor (bars that dance while the passage plays, two handles, the
// kept part that slides as a whole, a "refrain" flag), the three uses with their own little animation (a bell that
// swings, a ping that ripples, a clock that rattles), and the phone that rings on the result.

private const val NUDGE_MS = 100L

/** The icon of each use. */
internal fun RingtoneUse.icon(): ImageVector = when (this) {
    RingtoneUse.RINGTONE -> Icons.Rounded.RingVolume
    RingtoneUse.NOTIFICATION -> Icons.Rounded.NotificationsActive
    RingtoneUse.ALARM -> Icons.Rounded.Alarm
}

/** Ink on Papier, light elsewhere: the bars outside the passage. */
@Composable
private fun quietBar(): Color = when (LocalSkin.current) {
    Skin.PAPER -> Palette.TextPrimary.copy(alpha = 0.22f)
    else -> Palette.TextPrimary.copy(alpha = 0.18f)
}

/**
 * The waveform with the kept passage: drag a handle to move one end, drag inside to slide the whole passage, tap
 * to move the closest end there. While the preview plays, the bars under the playhead jump.
 */
@Composable
internal fun WaveEditor(state: RingUiState, actions: RingtoneActions, modifier: Modifier = Modifier) {
    val source = state.source ?: return
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val duration = source.durationMs.coerceAtLeast(1L)
    var widthPx by remember { mutableIntStateOf(1) }
    val range by rememberUpdatedState(state.range)
    val onStart by rememberUpdatedState(actions.onStart)
    val onEnd by rememberUpdatedState(actions.onEnd)
    val onSlide by rememberUpdatedState(actions.onSlide)
    /** -1 start handle, +1 end handle, 2 the whole passage, 0 nothing. */
    var held by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableStateOf(0f) }
    var grabOffsetMs by remember { mutableStateOf(0L) }

    // Glide the passage when it jumps (the refrain button, a new use) instead of snapping.
    val startX by animateFloatAsState(range.startMs.toFloat() / duration, spring(dampingRatio = 0.8f, stiffness = if (held != 0) 10_000f else 260f), label = "ringStart")
    val endX by animateFloatAsState(range.endMs.toFloat() / duration, spring(dampingRatio = 0.8f, stiffness = if (held != 0) 10_000f else 260f), label = "ringEnd")

    val motion = rememberInfiniteTransition(label = "ringWave")
    val phase by motion.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ringDance")
    val grow = remember { Animatable(0f) }
    LaunchedEffect(state.wave) { if (state.wave != null) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) } }

    fun msAt(x: Float): Long = (x / widthPx.coerceAtLeast(1) * duration).roundToLong().coerceIn(0L, duration)

    val shape = RoundedCornerShape(if (terminal) 4.dp else 16.dp)
    val quiet = quietBar()
    val handleColor = if (skin == Skin.PAPER) Palette.TextPrimary else accent.start
    Box(
        modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(shape)
            .background(Palette.Surface2)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val ms = msAt(offset.x)
                    if (abs(ms - range.startMs) <= abs(ms - range.endMs)) onStart(ms) else onEnd(ms)
                }
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val sx = range.startMs.toFloat() / duration * widthPx
                        val ex = range.endMs.toFloat() / duration * widthPx
                        val grip = 22.dp.toPx()
                        held = when {
                            abs(offset.x - sx) <= grip && abs(offset.x - sx) <= abs(offset.x - ex) -> -1
                            abs(offset.x - ex) <= grip -> 1
                            offset.x in sx..ex -> 2
                            abs(offset.x - sx) <= abs(offset.x - ex) -> -1
                            else -> 1
                        }
                        dragX = offset.x
                        grabOffsetMs = msAt(offset.x) - range.startMs
                    },
                    onDragEnd = { held = 0 },
                    onDragCancel = { held = 0 },
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                    when (held) {
                        -1 -> onStart(msAt(dragX))
                        1 -> onEnd(msAt(dragX))
                        2 -> onSlide(msAt(dragX) - grabOffsetMs)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(vertical = 14.dp)) {
            val bars = state.wave ?: placeholderWave
            val n = bars.size
            val step = size.width / n
            val sx = startX * size.width
            val ex = endX * size.width
            val play = state.playheadMs?.let { it.toFloat() / duration * size.width }
            val g = if (state.wave == null) 1f else grow.value
            for (i in 0 until n) {
                val cx = i * step + step / 2
                val inside = cx in sx..ex
                var v = bars[i].coerceIn(0.05f, 1f)
                if (state.wave == null) v *= 0.35f
                // Grow from the middle when the waveform arrives, left to right.
                val reveal = ((g * 1.4f - i.toFloat() / n) * 3f).coerceIn(0f, 1f)
                if (play != null && inside) {
                    val near = (1f - abs(cx - play) / (size.width * 0.12f)).coerceIn(0f, 1f)
                    v = (v + near * 0.35f * (0.6f + 0.4f * sin(phase + i * 0.9f))).coerceAtMost(1f)
                }
                val h = v * size.height * reveal
                val color = when {
                    !inside -> quiet
                    play != null && cx <= play -> accent.end
                    else -> accent.start
                }
                if (terminal) {
                    drawRect(color, topLeft = Offset(i * step + step * 0.15f, (size.height - h) / 2), size = Size(step * 0.7f, h))
                } else {
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * step + step * 0.18f, (size.height - h) / 2),
                        size = Size(step * 0.64f, h),
                        cornerRadius = CornerRadius(step * 0.32f),
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val sx = startX * size.width
            val ex = endX * size.width
            val dim = if (skin == Skin.PAPER) Color(0x1A2A0F0A) else Color.Black.copy(alpha = 0.32f)
            drawRect(dim, topLeft = Offset.Zero, size = Size(sx, size.height))
            drawRect(dim, topLeft = Offset(ex, 0f), size = Size(size.width - ex, size.height))
            val stroke = 2.5.dp.toPx()
            drawRoundRect(
                handleColor,
                topLeft = Offset(sx, stroke / 2),
                size = Size((ex - sx).coerceAtLeast(1f), size.height - stroke),
                cornerRadius = CornerRadius(if (terminal) 2f else 12.dp.toPx()),
                style = Stroke(stroke),
            )
            if (held == 2) drawRect(handleColor.copy(alpha = 0.10f), topLeft = Offset(sx, 0f), size = Size(ex - sx, size.height))
            val handleW = 12.dp.toPx()
            listOf(sx to (held == -1), ex to (held == 1)).forEach { (x, active) ->
                val k = if (active) 1.3f else 1f
                val w = handleW * k
                val h = size.height * 0.42f * k
                drawRoundRect(handleColor, topLeft = Offset(x - w / 2, (size.height - h) / 2), size = Size(w, h), cornerRadius = CornerRadius(if (terminal) 2f else w / 2))
                drawLine(Color.White.copy(alpha = 0.9f), Offset(x, size.height / 2 - h / 5), Offset(x, size.height / 2 + h / 5), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            state.playheadMs?.let { ms ->
                val px = ms.toFloat() / duration * size.width
                drawLine(Palette_white, Offset(px, 4.dp.toPx()), Offset(px, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Palette_white, radius = 4.dp.toPx(), center = Offset(px, 4.dp.toPx()))
            }
        }
        // The refrain flag: where the catchiest passage starts.
        state.bestMs?.let { best ->
            val onIt = abs(best - state.range.startMs) < 600
            val bob = rememberInfiniteTransition(label = "flag")
            val y by bob.animateFloat(0f, -3f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flagBob")
            Text(
                if (terminal) "*REFRAIN" else "★ refrain",
                color = if (onIt) Palette.OnAccent else accent.start,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = (best.toFloat() / duration * widthPx).coerceAtMost(widthPx - 70.dp.toPx()) + 4.dp.toPx()
                        translationY = 4.dp.toPx() + y
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (onIt) accent.start else Palette.Surface)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

private val Palette_white = Color.White

/** A gentle hill shown while the waveform is decoded (and in screenshot tests without one). */
private val placeholderWave = FloatArray(RingtonePlan.BARS) { i -> 0.35f + 0.25f * sin(i * 0.21f).let { abs(it) } }

/** Start, length and end of the passage, with nudges of a tenth of a second. */
@Composable
internal fun RangeReadout(state: RingUiState, actions: RingtoneActions) {
    val accent = LocalAccent.current
    val r = state.range
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        TimeBlock("Début", RingtonePlan.timecode(r.startMs), Alignment.Start, Modifier.weight(1f)) {
            Nudge(Icons.Rounded.Remove, "Début plus tôt") { actions.onStart(r.startMs - NUDGE_MS) }
            Nudge(Icons.Rounded.Add, "Début plus tard") { actions.onStart(r.startMs + NUDGE_MS) }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            SmallLabel("Durée")
            Text(RingtonePlan.length(r.durationMs), color = accent.start, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text("max ${RingtonePlan.length(state.use.maxMs)}", color = Palette.TextTertiary, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(18.dp))
        }
        TimeBlock("Fin", RingtonePlan.timecode(r.endMs), Alignment.End, Modifier.weight(1f)) {
            Nudge(Icons.Rounded.Remove, "Fin plus tôt") { actions.onEnd(r.endMs - NUDGE_MS) }
            Nudge(Icons.Rounded.Add, "Fin plus tard") { actions.onEnd(r.endMs + NUDGE_MS) }
        }
    }
}

@Composable
private fun TimeBlock(label: String, value: String, align: Alignment.Horizontal, modifier: Modifier, nudges: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = align) {
        SmallLabel(label)
        Text(value, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { nudges() }
    }
}

@Composable
private fun SmallLabel(text: String) {
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

/** Round play / stop button; a ring of sound pulses out of it while it plays. */
@Composable
internal fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val pulse = rememberInfiniteTransition(label = "playPulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "playRing")
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(4.dp) else CircleShape
    val fill = when (skin) {
        Skin.CLASSIC -> Modifier.background(accent.brush, shape)
        Skin.PAPER -> Modifier.background(Palette.TextPrimary, shape)
        Skin.TERMINAL -> Modifier.background(accent.start.copy(alpha = 0.12f), shape).border(1.dp, accent.start, shape)
    }
    Box(modifier.size(size + 20.dp), contentAlignment = Alignment.Center) {
        if (playing) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(p, (p + 0.5f) % 1f).forEach { q ->
                    drawCircle(accent.start.copy(alpha = (1f - q) * 0.5f), radius = this.size.minDimension / 2 * (0.6f + 0.4f * q), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Box(
            Modifier
                .size(size)
                .bounceClick(pressedScale = 0.88f, onClick = onClick)
                .then(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Arrêter l'écoute" else "Écouter le passage",
                tint = if (skin == Skin.TERMINAL) accent.start else if (skin == Skin.PAPER) Palette.Background else Palette.OnAccent,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** The use's icon, alive: the bell swings, the notification pings, the alarm rattles. */
@Composable
internal fun UseGlyph(use: RingtoneUse, active: Boolean, tint: Color, size: Dp = 26.dp) {
    val motion = rememberInfiniteTransition(label = "useGlyph")
    val t by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "useT")
    val accent = LocalAccent.current
    Box(Modifier.size(size + 14.dp), contentAlignment = Alignment.Center) {
        if (active && use == RingtoneUse.NOTIFICATION) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(accent.start.copy(alpha = (1f - t) * 0.45f), radius = this.size.minDimension / 2 * (0.4f + 0.6f * t), style = Stroke(1.5.dp.toPx()))
            }
        }
        Icon(
            use.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    if (!active) return@graphicsLayer
                    when (use) {
                        // A swing that dies out, then a pause.
                        RingtoneUse.RINGTONE -> rotationZ = if (t < 0.5f) 18f * sin(t * 2 * PI * 4).toFloat() * (1f - t * 2) else 0f
                        RingtoneUse.NOTIFICATION -> { val s = 1f + 0.12f * sin(t * PI).toFloat(); scaleX = s; scaleY = s }
                        RingtoneUse.ALARM -> {
                            translationX = if (t < 0.6f) 2.5f * sin(t * 2 * PI * 12).toFloat() else 0f
                            rotationZ = if (t < 0.6f) 6f * sin(t * 2 * PI * 12).toFloat() else 0f
                        }
                    }
                },
        )
    }
}

/** The three uses side by side; the chosen one fills and its icon comes alive. */
@Composable
internal fun UseTiles(selected: RingtoneUse, onSelect: (RingtoneUse) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RingtoneUse.entries.forEach { use -> UseTile(use, use == selected, Modifier.weight(1f)) { onSelect(use) } }
    }
}

@Composable
private fun UseTile(use: RingtoneUse, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val terminal = skin == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 4.dp else tokens.fieldRadius)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) { if (selected) { pop.snapTo(0.9f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) } }
    val fill by animateColorAsState(
        when {
            !selected -> Palette.Surface2
            skin == Skin.CLASSIC -> accent.tint(0.22f)
            skin == Skin.PAPER -> Palette.TextPrimary
            else -> accent.start.copy(alpha = 0.14f)
        },
        tween(220),
        label = "useFill",
    )
    val outline = if (selected) (if (skin == Skin.PAPER) Palette.TextPrimary else accent.start) else Color.Transparent
    val ink = when {
        selected && skin == Skin.PAPER -> Palette.Background
        selected -> if (terminal) accent.start else Palette.TextPrimary
        else -> Palette.TextSecondary
    }
    Column(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .bounceClick(pressedScale = 0.93f, onClick = onClick)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, outline, shape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UseGlyph(use, selected, if (selected && skin != Skin.PAPER) accent.start else ink)
        Text(if (terminal) use.label.uppercase() else use.label, color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        Text(
            "≤ ${RingtonePlan.length(use.maxMs)}",
            color = if (selected && skin == Skin.PAPER) Palette.Background.copy(alpha = 0.7f) else Palette.TextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** A phone that rings: it shakes in bursts while sound waves leave both sides, the use's icon on its screen. */
@Composable
internal fun RingingPhone(use: RingtoneUse, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val motion = rememberInfiniteTransition(label = "ringPhone")
    val t by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ringT")
    val body = if (skin == Skin.PAPER) Palette.TextPrimary else Palette.TextPrimary.copy(alpha = 0.92f)
    val screen = if (skin == Skin.CLASSIC) accent.start else if (skin == Skin.PAPER) Palette.Background else Palette.Surface2
    Box(modifier.size(150.dp, 132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Waves: three arcs on each side, leaving the phone one after the other.
            for (k in 0 until 3) {
                val q = (t * 1.5f + k / 3f) % 1f
                val r = 36.dp.toPx() + q * 30.dp.toPx()
                val a = (1f - q) * 0.8f
                listOf(-30f to 60f, 150f to 60f).forEach { (start, sweep) ->
                    drawArc(
                        accent.start.copy(alpha = a),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            val shake = if (t < 0.45f) sin(t * 2 * PI * 9).toFloat() * 9f * (1f - t / 0.45f) else 0f
            rotate(shake, pivot = center) {
                val w = 50.dp.toPx()
                val h = 88.dp.toPx()
                val tl = Offset(center.x - w / 2, center.y - h / 2)
                drawRoundRect(body, topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(if (skin == Skin.TERMINAL) 4f else 10.dp.toPx()))
                drawRoundRect(
                    screen,
                    topLeft = tl + Offset(4.dp.toPx(), 9.dp.toPx()),
                    size = Size(w - 8.dp.toPx(), h - 22.dp.toPx()),
                    cornerRadius = CornerRadius(if (skin == Skin.TERMINAL) 2f else 6.dp.toPx()),
                )
                drawCircle(screen, radius = 3.dp.toPx(), center = Offset(center.x, tl.y + h - 6.5.dp.toPx()))
            }
        }
        val shake = if (t < 0.45f) sin(t * 2 * PI * 9).toFloat() * 9f * (1f - t / 0.45f) else 0f
        Icon(
            use.icon(),
            contentDescription = null,
            tint = if (skin == Skin.CLASSIC) Palette.OnAccent else accent.start,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = shake * 1.6f },
        )
    }
}

/** Equalizer bars that dance while the cut runs. */
@Composable
internal fun Equalizer(modifier: Modifier = Modifier, bars: Int = 9) {
    val accent = LocalAccent.current
    val motion = rememberInfiniteTransition(label = "eq")
    val t by motion.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "eqT")
    Canvas(modifier.width(120.dp).height(40.dp)) {
        val step = size.width / bars
        for (i in 0 until bars) {
            val v = 0.25f + 0.75f * abs(sin(t + i * 0.7f) * sin(t * 0.5f + i * 1.3f))
            val h = v * size.height
            drawRoundRect(
                Brush.verticalGradient(listOf(accent.end, accent.start)),
                topLeft = Offset(i * step + step * 0.2f, size.height - h),
                size = Size(step * 0.6f, h),
                cornerRadius = CornerRadius(step * 0.3f),
            )
        }
    }
}
