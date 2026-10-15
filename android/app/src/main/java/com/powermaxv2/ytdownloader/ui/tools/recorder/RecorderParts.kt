package com.powermaxv2.ytdownloader.ui.tools.recorder

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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.recorder.Memos
import com.powermaxv2.ytdownloader.core.recorder.RecPhase
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.terminal.blinking
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Pieces of the Enregistreur vocal, drawn natively in each skin: the record button (a mic that becomes a stop
// square, rings breathing with the voice), the live bars, the rolling timer, the round play button, and the empty
// state's microphone.

/** Text in the skin's case (Terminal writes in capitals). */
@Composable
internal fun recText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

// MARK: - Record button

/**
 * The big button: a microphone when idle, a stop square while recording (the shape morphs), rings that swell with
 * [level], a slow spin of the dashed tape ring in Papier, `[ ● REC ]` in Terminal.
 */
@Composable
internal fun RecordButton(phase: RecPhase, level: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val recording = phase != RecPhase.IDLE
    val voice by animateFloatAsState(if (phase == RecPhase.RECORDING) level else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "recVoice")
    val morph by animateFloatAsState(if (recording) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 320f), label = "recMorph")
    val transition = rememberInfiniteTransition(label = "recButton")
    val ripple by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "recRipple")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "recSpin")
    val breathe by transition.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "recBreathe")
    val label = when (phase) {
        RecPhase.IDLE -> "Enregistrer"
        else -> "Arrêter et garder"
    }
    val base = modifier
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
        }
        .bounceClick(pressedScale = 0.9f, onClick = onClick)
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicRecordButton(phase, voice, morph, ripple, breathe, base)
        Skin.PAPER -> PaperRecordButton(phase, voice, morph, spin, base)
        Skin.TERMINAL -> TerminalRecordButton(phase, voice, base)
    }
}

@Composable
private fun ClassicRecordButton(phase: RecPhase, voice: Float, morph: Float, ripple: Float, breathe: Float, modifier: Modifier) {
    val accent = LocalAccent.current
    val recording = phase == RecPhase.RECORDING
    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(176.dp)) {
            val r = 44.dp.toPx()
            // Voice halo: grows with the level.
            drawCircle(accent.start.copy(alpha = 0.16f), r * (1.15f + voice * 0.75f))
            drawCircle(accent.end.copy(alpha = 0.10f), r * (1.3f + voice * 1.0f))
            // Ripples leaving the button while it records; one slow breath when idle.
            if (recording) {
                for (k in 0..2) {
                    val p = (ripple + k / 3f) % 1f
                    drawCircle(accent.start.copy(alpha = (1f - p) * 0.45f), r * (1f + p * 0.95f), style = Stroke(2.dp.toPx()))
                }
            } else if (phase == RecPhase.IDLE) {
                drawCircle(accent.start.copy(alpha = 0.22f), r * 1.12f * breathe, style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = 1f + voice * 0.08f; scaleY = 1f + voice * 0.08f }
                .clip(CircleShape)
                .background(accent.brush),
            contentAlignment = Alignment.Center,
        ) {
            MicToStop(morph, Palette.OnAccent, 40.dp.value)
        }
    }
}

@Composable
private fun PaperRecordButton(phase: RecPhase, voice: Float, morph: Float, spin: Float, modifier: Modifier) {
    val ink = Palette.TextPrimary
    val red = LocalAccent.current.start
    val recording = phase == RecPhase.RECORDING
    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            val r = 62.dp.toPx()
            // The tape ring: dashes turning while it records.
            rotate(if (recording) spin else 0f) {
                drawCircle(
                    ink.copy(alpha = 0.55f),
                    r,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))),
                )
            }
            drawCircle(red.copy(alpha = 0.12f + voice * 0.25f), r * (0.82f + voice * 0.18f))
        }
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Palette.Surface)
                .border(3.dp, ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The red dot of a tape deck, squaring into "stop".
            val corner = 50 - (morph * 32).toInt()
            val side = (38 - morph * 6).dp
            Box(
                Modifier
                    .size(side)
                    .clip(RoundedCornerShape(corner.coerceIn(0, 50)))
                    .background(red),
            )
        }
    }
}

@Composable
private fun TerminalRecordButton(phase: RecPhase, voice: Float, modifier: Modifier) {
    val accent = LocalAccent.current.start
    val radius = LocalTokens.current.cardRadius
    val recording = phase != RecPhase.IDLE
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier
                .clip(RoundedCornerShape(radius))
                .background(if (recording) accent.copy(alpha = 0.14f) else Color.Transparent)
                .border(1.dp, accent, RoundedCornerShape(radius))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("[ ", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (recording) "■" else "●",
                color = accent,
                fontSize = 22.sp,
                modifier = if (phase == RecPhase.RECORDING) Modifier.blinking(700) else Modifier,
            )
            Text(if (recording) " STOP ]" else " REC ]", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        // VU meter: 16 cells, lit up to the level.
        val cells = 16
        val lit = (voice * cells).toInt()
        Text(
            "VU " + (0 until cells).joinToString("") { if (it < lit) "█" else "·" },
            color = if (phase == RecPhase.RECORDING) accent else Palette.TextTertiary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
    }
}

/** A microphone drawn by hand that folds into a rounded stop square as [morph] goes 0 → 1. */
@Composable
private fun MicToStop(morph: Float, color: Color, sizeDp: Float) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val stroke = w * 0.085f
        // Capsule → square: width grows, height shrinks, corners square up.
        val capW = w * (0.34f + morph * 0.16f)
        val capH = w * (0.56f - morph * 0.06f)
        val top = w * (0.08f + morph * 0.17f)
        val corner = capW / 2 * (1f - morph * 0.62f)
        drawRoundRect(color, Offset((w - capW) / 2, top), Size(capW, capH), CornerRadius(corner))
        // Cradle and foot fade out as it becomes a stop square.
        val a = (1f - morph * 1.6f).coerceIn(0f, 1f)
        if (a > 0f) {
            val cradle = w * 0.30f
            drawArc(
                color.copy(alpha = color.alpha * a),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w / 2 - cradle, w * 0.30f),
                size = Size(cradle * 2, cradle * 1.25f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawLine(color.copy(alpha = color.alpha * a), Offset(w / 2, w * 0.78f), Offset(w / 2, w * 0.94f), stroke, StrokeCap.Round)
        }
    }
}

// MARK: - Live bars

/**
 * The voice as it is heard: [levels] (oldest first) drawn as bars arriving from the right, the newest one leaping
 * up. Before the first word, a faint flat line breathes.
 */
@Composable
internal fun LiveBars(levels: List<Float>, phase: RecPhase, slots: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val paused = phase == RecPhase.PAUSED
    val transition = rememberInfiniteTransition(label = "liveBars")
    val wave by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2_200, easing = LinearEasing)), label = "liveWave")
    val ink = Palette.TextPrimary
    val dim = ink.copy(alpha = 0.16f)
    if (skin == Skin.TERMINAL) {
        val glyphs = "▁▂▃▄▅▆▇█"
        val shown = (List((slots - levels.size).coerceAtLeast(0)) { 0f } + levels).takeLast(slots / 2)
        Text(
            shown.joinToString("") { glyphs[(it * (glyphs.length - 1)).toInt().coerceIn(0, glyphs.length - 1)].toString() },
            color = if (paused || phase == RecPhase.IDLE) Palette.TextTertiary else accent.start,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val gap = size.width / slots
        val w = (gap * if (skin == Skin.PAPER) 0.42f else 0.58f).coerceAtLeast(1.5f)
        val mid = size.height / 2
        if (skin == Skin.PAPER) {
            // The tape's centre line, like a ruled notebook.
            drawLine(ink.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        }
        val first = slots - levels.size
        for (i in 0 until slots) {
            val x = i * gap + (gap - w) / 2
            val v = if (i >= first) levels[i - first] else 0f
            val idle = 0.04f + 0.03f * abs(sin(wave + i * 0.35f))
            val h = ((if (i >= first) v else idle) * size.height).coerceAtLeast(w)
            val color = when {
                i < first || paused -> dim
                skin == Skin.PAPER -> ink
                else -> null
            }
            if (color != null) {
                drawRoundRect(color, Offset(x, mid - h / 2), Size(w, h), CornerRadius(w / 2))
            } else {
                // Classic: the gradient along the bars, the newest ones brightest.
                val fade = 0.35f + 0.65f * ((i - first + 1).toFloat() / levels.size.coerceAtLeast(1))
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent.start.copy(alpha = fade), accent.end.copy(alpha = fade)), mid - h / 2, mid + h / 2),
                    Offset(x, mid - h / 2),
                    Size(w, h),
                    CornerRadius(w / 2),
                )
            }
        }
    }
}

// MARK: - Timer

/** "0:42" in big digits rolling up as they change, the tenths smaller; blinks while paused. */
@Composable
internal fun RecTimer(ms: Long, phase: RecPhase, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current.start
    val color = when {
        phase == RecPhase.IDLE -> Palette.TextTertiary
        skin == Skin.PAPER -> Palette.TextPrimary
        else -> Palette.TextPrimary
    }
    val blink = if (phase == RecPhase.PAUSED) Modifier.blinking(900) else Modifier
    Row(modifier.then(blink), verticalAlignment = Alignment.Bottom) {
        if (phase == RecPhase.RECORDING) {
            Box(
                Modifier
                    .padding(bottom = 16.dp, end = 10.dp)
                    .size(10.dp)
                    .blinking(1_000)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Memos.duration(ms).forEachIndexed { i, c ->
            AnimatedContent(
                targetState = c,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it / 2 } + fadeIn(tween(120))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(100)))
                },
                label = "recDigit$i",
            ) { digit ->
                Text(
                    digit.toString(),
                    color = color,
                    fontSize = 54.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else tokens.display,
                )
            }
        }
        Text(
            "," + Memos.tenths(ms),
            color = if (phase == RecPhase.IDLE) Palette.TextTertiary else accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else tokens.display,
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
        )
    }
}

// MARK: - Small round buttons

/** A round side button of the deck (Jeter, Pause / Reprendre), springing in. */
@Composable
internal fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
    Column(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            }
            .bounceClick(pressedScale = 0.85f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape
        Box(
            Modifier
                .size(54.dp)
                .clip(shape)
                .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
                .border(if (skin == Skin.PAPER) 2.dp else 1.dp, if (skin == Skin.PAPER) Palette.TextPrimary else if (skin == Skin.TERMINAL) accent else Palette.Stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (skin == Skin.CLASSIC) Palette.TextPrimary else if (skin == Skin.PAPER) Palette.TextPrimary else accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(recText(label), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The round play / pause of a memo card; the icon turns and swaps. */
@Composable
internal fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape
    Box(
        modifier
            .size(46.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (playing) "Pause" else "Écouter"
            }
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .clip(shape)
            .then(
                when (skin) {
                    Skin.CLASSIC -> Modifier.background(accent.brush)
                    Skin.PAPER -> Modifier.background(Palette.TextPrimary)
                    Skin.TERMINAL -> Modifier.border(1.dp, accent.start, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "playIcon",
        ) { now ->
            Icon(
                if (now) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = when (skin) {
                    Skin.CLASSIC -> Palette.OnAccent
                    Skin.PAPER -> Palette.Surface
                    Skin.TERMINAL -> accent.start
                },
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** The small speed pill ("1×", "1,5×"…), turning on each tap. */
@Composable
internal fun SpeedPill(speed: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val turn = remember { Animatable(0f) }
    LaunchedEffect(speed) {
        turn.snapTo(-90f)
        turn.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f))
    }
    Box(
        Modifier
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .clip(shape)
            .border(1.dp, LocalAccent.current.start, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            Memos.speedText(speed),
            color = LocalAccent.current.start,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { rotationX = turn.value },
        )
    }
}

// MARK: - Empty state

/** No memo yet: a microphone bobbing, sound arcs leaving it in turn. */
@Composable
internal fun EmptyMemos(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val transition = rememberInfiniteTransition(label = "emptyMemos")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "emptyWave")
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 150.dp, height = 84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 150.dp, height = 84.dp)) {
                val c = center
                for (k in 0..2) {
                    val p = (wave + k / 3f) % 1f
                    val r = 26.dp.toPx() + p * 40.dp.toPx()
                    val a = (1f - p) * 0.6f
                    val col = accent.start.copy(alpha = a)
                    for (side in listOf(-1f, 1f)) {
                        drawArc(
                            col,
                            startAngle = if (side < 0) 140f else -40f,
                            sweepAngle = 80f,
                            useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .bob(0, amplitude = 4.dp, periodMs = 2_200)
                    .size(48.dp)
                    .clip(if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape)
                    .background(if (skin == Skin.CLASSIC) accent.start.copy(alpha = 0.16f) else Color.Transparent)
                    .border(if (skin == Skin.CLASSIC) 0.dp else 1.5.dp, if (skin == Skin.PAPER) Palette.TextPrimary else accent.start, if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = if (skin == Skin.PAPER) Palette.TextPrimary else accent.start, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            recText("Aucun mémo pour l'instant"),
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            recText("Touche le bouton rouge, parle, puis touche-le encore."),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

/** The "jeter" cross used by the deck. */
internal val DiscardIcon: ImageVector get() = Icons.Rounded.Close

/** A thin separator in the skin's ink, drawn in when it appears. */
@Composable
internal fun DrawnRule(modifier: Modifier = Modifier) {
    val ink = Palette.TextPrimary.copy(alpha = 0.18f)
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(ink, size = Size(size.width * draw.value, size.height)) },
    )
}

/** Width of a [RoundAction] slot, so the deck stays centred whatever shows. */
internal val SideSlot = 72.dp

@Composable
internal fun SideSpacer() = Spacer(Modifier.width(SideSlot))

internal val DeckArrangement = Arrangement.SpaceEvenly
