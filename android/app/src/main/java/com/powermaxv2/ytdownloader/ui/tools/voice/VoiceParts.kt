package com.powermaxv2.ytdownloader.ui.tools.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.voice.VoiceSentence
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Drawn pieces of Vocaux en texte: the voice bubble that turns into text, the waveform the words come out of, the
// listening ring, and the transcript with its unsure words dimmed.

/** A stable pseudo waveform (when the real one is not known yet): the same voice note always gets the same bars. */
internal fun sampleWave(seed: Int, bars: Int = 36): List<Float> = List(bars) { i ->
    val a = sin((i + seed % 7) * 0.55) * 0.35 + sin((i * 1.7 + seed % 13) * 0.9) * 0.25
    (0.35 + abs(a)).toFloat().coerceIn(0.12f, 1f)
}

/**
 * Bars of a sound. [progress] 0..1 lights the bars already heard (accent) and draws the playhead there; [live]
 * makes the bars around the playhead dance like a level meter.
 */
@Composable
internal fun VoiceWave(
    bars: List<Float>,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    live: Boolean = false,
    height: Float = 56f,
) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val dim = Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.16f else 0.14f)
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val phase by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "voiceWavePhase")
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 90f), label = "voiceWaveProgress")
    val brush = if (skin == Skin.CLASSIC) accent.horizontal() else Brush.horizontalGradient(listOf(accent.start, accent.start))
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        if (bars.isEmpty()) return@Canvas
        val n = bars.size
        val gap = size.width / n
        val w = (gap * if (skin == Skin.TERMINAL) 0.8f else 0.56f).coerceAtLeast(1.5f)
        val head = shown * size.width
        bars.forEachIndexed { i, v ->
            val x = i * gap + (gap - w) / 2
            val near = abs(x - head) < gap * 3
            val dance = if (live && near) (0.55f + 0.45f * abs(sin(phase + i * 0.8f))) else 1f
            val h = (v * dance * size.height).coerceAtLeast(w)
            val top = (size.height - h) / 2
            val heard = x + w / 2 <= head
            if (skin == Skin.TERMINAL) {
                // Pixel bars: stacked blocks.
                val block = w
                var y = size.height / 2 + h / 2 - block
                while (y >= top - 0.5f) {
                    drawRect(if (heard) accent.start else dim, Offset(x, y), Size(w, block * 0.78f))
                    y -= block
                }
            } else if (heard) {
                drawRoundRect(brush, Offset(x, top), Size(w, h), CornerRadius(w / 2))
            } else {
                drawRoundRect(dim, Offset(x, top), Size(w, h), CornerRadius(w / 2))
            }
        }
        if (progress in 0.001f..0.999f) {
            drawLine(accent.start, Offset(head, 0f), Offset(head, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(accent.start, 4.dp.toPx(), Offset(head, 0f))
        }
    }
}

/**
 * The loop of the idle screen: a WhatsApp-like voice bubble plays, its bars fly up and settle as words in a text
 * bubble, then it starts again. Pure animation, no state.
 */
@Composable
internal fun BubbleToText(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val transition = rememberInfiniteTransition(label = "bubbleToText")
    val t by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart),
        label = "bubbleToTextT",
    )
    val words = listOf("Salut", "!", "Je", "serai", "là", "vers", "20", "h,", "garde-moi", "une", "place", "🍿")
    val play = (t / 0.35f).coerceIn(0f, 1f)
    val write = ((t - 0.3f) / 0.5f).coerceIn(0f, 1f)
    val shownWords = (write * words.size).toInt()
    val bubbleShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    val textShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    val voiceColor = if (skin == Skin.PAPER) Color(0xFFDCF8C6) else Palette.TextPrimary.copy(alpha = 0.08f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The voice note.
        Row(
            Modifier
                .clip(if (terminal) RoundedCornerShape(2.dp) else bubbleShape)
                .background(voiceColor)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .width(230.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.start),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            VoiceWave(sampleWave(3, 28), Modifier.weight(1f), progress = play, live = play in 0.01f..0.99f, height = 28f)
            Spacer(Modifier.width(8.dp))
            Text("0:07", color = Palette.TextSecondary, fontSize = 11.sp, fontFamily = if (terminal) FontFamily.Monospace else null)
        }
        // The text it becomes.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val appear by animateFloatAsState(if (write > 0f) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "bubbleAppear")
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = 0.6f + 0.4f * appear
                        scaleY = 0.6f + 0.4f * appear
                        alpha = appear
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                    }
                    .clip(if (terminal) RoundedCornerShape(2.dp) else textShape)
                    .background(if (skin == Skin.CLASSIC) accent.horizontal() else Brush.horizontalGradient(listOf(accent.start, accent.start)))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .width(230.dp),
            ) {
                val text = words.take(shownWords.coerceAtLeast(1)).joinToString(" ").replace(" !", " !").replace(" ,", ",")
                Text(
                    (if (terminal) "> " else "") + text + if (write < 1f) " ▍" else "",
                    color = Palette.OnAccent,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (terminal) FontFamily.Monospace else null,
                )
            }
        }
    }
}

/** A round ring that fills with [progress] (-1 = turning), around a pulsing microphone. */
@Composable
internal fun ListeningRing(progress: Float, modifier: Modifier = Modifier, size: Int = 132) {
    val accent = LocalAccent.current
    val track = Palette.TextPrimary.copy(alpha = 0.08f)
    val transition = rememberInfiniteTransition(label = "voiceRing")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "voiceRingSpin")
    val pulse by transition.animateFloat(
        1f,
        1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 1_100
                1f at 0
                1.12f at 180 using FastOutSlowInEasing
                0.98f at 420
                1f at 1_100
            },
        ),
        label = "voiceRingPulse",
    )
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 80f), label = "voiceRingProgress")
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val brush = Brush.sweepGradient(listOf(accent.start, accent.end, accent.start))
            if (progress < 0f) {
                drawArc(brush, spin, 80f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = if (terminal) StrokeCap.Butt else StrokeCap.Round))
            } else {
                drawArc(brush, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = if (terminal) StrokeCap.Butt else StrokeCap.Round))
            }
            // Sound ripples.
            for (k in 0..2) {
                val f = ((spin / 360f) + k / 3f) % 1f
                drawCircle(accent.start.copy(alpha = (1f - f) * 0.18f), radius = this.size.minDimension * (0.18f + 0.22f * f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size((size * 0.42f).dp)
                .scale(pulse)
                .clip(if (terminal) RoundedCornerShape(4.dp) else CircleShape)
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.horizontal() else Brush.horizontalGradient(listOf(accent.start, accent.start))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size((size * 0.22f).dp))
        }
    }
}

/**
 * The transcript: finished sentences, then the words being heard in the accent with a blinking caret. Unsure words
 * are dimmed. Each new word pops in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranscriptText(sentences: List<VoiceSentence>, partial: String, modifier: Modifier = Modifier, writing: Boolean = false) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val font = if (terminal) FontFamily.Monospace else LocalTokens.current.body
    data class Token(val text: String, val color: Color, val key: String)
    val tokens = ArrayList<Token>()
    sentences.forEachIndexed { s, sentence ->
        val written = sentence.text.split(' ').filter { it.isNotEmpty() }
        written.forEachIndexed { w, word ->
            val unsure = sentence.words.getOrNull(w)?.unsure == true
            tokens += Token(word, if (unsure) Palette.TextTertiary else Palette.TextPrimary, "s$s-$w")
        }
    }
    partial.split(' ').filter { it.isNotEmpty() }.forEachIndexed { w, word ->
        tokens += Token(word, accent.start, "p${sentences.size}-$w")
    }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tokens.forEach { token -> PopWord(token.text, token.color, font, token.key) }
        if (writing) Caret(accent.start)
    }
}

@Composable
private fun PopWord(text: String, color: Color, font: FontFamily, key: String) {
    var seen by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) { seen = 1 }
    val scale by animateFloatAsState(if (seen == 1) 1f else 0.4f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "popWord")
    val alpha by animateFloatAsState(if (seen == 1) 1f else 0f, tween(180), label = "popWordAlpha")
    Text(
        text,
        color = color,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        fontFamily = font,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
    )
}

@Composable
private fun Caret(color: Color) {
    val transition = rememberInfiniteTransition(label = "caret")
    val blink by transition.animateFloat(1f, 0f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "caretBlink")
    Box(
        Modifier
            .padding(top = 4.dp)
            .width(3.dp)
            .height(19.dp)
            .graphicsLayer { alpha = blink }
            .background(color),
    )
}

/** A small rounded label with an icon: "0:42", "86 mots", "× 3,1". */
@Composable
internal fun StatChip(text: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.GraphicEq) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        modifier
            .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .background(accent.start.copy(alpha = 0.12f))
            .border(1.dp, accent.start.copy(alpha = if (terminal) 0.6f else 0.2f), RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(if (terminal) text.uppercase() else text, color = Palette.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = if (terminal) FontFamily.Monospace else null)
    }
}
