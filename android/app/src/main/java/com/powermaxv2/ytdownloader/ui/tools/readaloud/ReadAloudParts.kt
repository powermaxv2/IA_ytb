package com.powermaxv2.ytdownloader.ui.tools.readaloud

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Moving parts of the Lecture à voix haute: the sound bars that dance while the phone speaks, the round play
// button with its rings, the "karaoké" card where the word being said is lit, and the strip that shows (and
// sets) where the reading is. Each one drawn natively in the three skins.

// MARK: - Sound bars

/** Bars that dance while [playing] (each at its own pace), and lie down, breathing softly, when the voice is quiet. */
@Composable
internal fun SoundBars(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 28) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val ink = Palette.TextPrimary
    val transition = rememberInfiniteTransition(label = "soundBars")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "soundTime")
    val energy by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "soundEnergy")
    if (skin == Skin.TERMINAL) {
        val levels = "▁▂▃▄▅▆▇█"
        val line = buildString {
            for (i in 0 until bars) {
                val v = barLevel(i, time, energy)
                append(levels[(v * (levels.length - 1)).toInt().coerceIn(0, levels.length - 1)])
            }
        }
        Text(line, color = accent.start, fontSize = 18.sp, letterSpacing = 1.sp, maxLines = 1, modifier = modifier)
        return
    }
    Canvas(modifier.height(if (skin == Skin.PAPER) 40.dp else 48.dp)) {
        val gap = size.width / bars
        val w = if (skin == Skin.PAPER) 2.dp.toPx() else gap * 0.55f
        val brush = Brush.verticalGradient(listOf(accent.end, accent.start))
        for (i in 0 until bars) {
            val h = size.height * (0.12f + 0.88f * barLevel(i, time, energy))
            val x = gap * i + (gap - w) / 2
            val top = (size.height - h) / 2
            if (skin == Skin.PAPER) {
                drawRect(ink.copy(alpha = 0.35f + 0.65f * energy), Offset(x, top), Size(w, h))
            } else {
                drawRoundRect(brush, Offset(x, top), Size(w, h), CornerRadius(w / 2))
            }
        }
    }
}

/** Height 0..1 of bar [i]: three waves at different speeds, a centre bump, and a slow breath when quiet. */
private fun barLevel(i: Int, t: Float, energy: Float): Float {
    val a = 2f * PI.toFloat() * t
    val wave = 0.5f + 0.25f * sin(a * 3 + i * 0.7f) + 0.15f * sin(a * 5 - i * 1.3f) + 0.1f * sin(a * 7 + i * 2.1f)
    val centre = 1f - abs(i - 14f) / 20f
    val idle = 0.08f + 0.05f * sin(a + i * 0.35f)
    return (idle * (1f - energy) + wave * centre * energy).coerceIn(0f, 1f)
}

// MARK: - Play button

/** The big play / pause button; rings spread from it while the voice speaks. */
@Composable
internal fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val label = if (playing) "Pause" else "Écouter"
    val base = modifier
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
        }
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .bounceClick(enabled = enabled, pressedScale = 0.88f, onClick = onClick)
    if (skin == Skin.TERMINAL) {
        val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
        Box(
            base
                .clip(shape)
                .background(if (playing) accent.start else Color.Transparent)
                .border(1.dp, accent.start, shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (playing) "[ ❚❚ PAUSE ]" else "[ ▶ LIRE ]",
                color = if (playing) Palette.Background else accent.start,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "playRings")
    val ring by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing)), label = "playRing")
    val rings by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "playRingsOn")
    val paper = skin == Skin.PAPER
    val ink = Palette.TextPrimary
    Box(
        base
            .size(112.dp)
            .drawBehind {
                if (rings > 0f) {
                    for (k in 0..1) {
                        val p = (ring + k * 0.5f) % 1f
                        val r = size.minDimension / 2 * (0.62f + 0.38f * p)
                        val color = if (paper) ink else accent.start
                        drawCircle(color.copy(alpha = (1f - p) * 0.45f * rings), r, style = Stroke(if (paper) 1.5.dp.toPx() else 3.dp.toPx()))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .then(if (paper) Modifier.background(ink) else Modifier.background(accent.brush)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.4f) + fadeOut(tween(90)))
                },
                label = "playIcon",
            ) { on ->
                Icon(
                    if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (paper) Palette.Background else Palette.OnAccent,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

// MARK: - Karaoké

/**
 * The sentence being read, big, with the word being said lit by a marker that glides from word to word; the
 * sentences before and after, faded, can be touched to jump there. Sentences slide up as the reading goes on.
 */
@Composable
internal fun KaraokeCard(
    previous: String?,
    current: String,
    next: String?,
    index: Int,
    word: IntRange?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    Column(modifier.fillMaxWidth().heightIn(min = 180.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val down = targetState > initialState
                (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 260f)) { h -> if (down) h / 2 else -h / 2 } + fadeIn(tween(240))) togetherWith
                    (slideOutVertically(tween(220)) { h -> if (down) -h / 2 else h / 2 } + fadeOut(tween(160)))
            },
            label = "karaoke",
        ) { _ ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                previous?.let { Faded(it, onPrevious) }
                LitSentence(current, word, skin)
                next?.let { Faded(it, onNext) }
            }
        }
    }
}

@Composable
private fun Faded(text: String, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) "  $text" else text,
        color = Palette.TextTertiary,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick),
    )
}

/** [text] with [word] lit: a rounded marker in the accent (Gulp), a highlighter stroke (Papier), an inverse block (Terminal). */
@Composable
private fun LitSentence(text: String, word: IntRange?, skin: Skin) {
    val accent = LocalAccent.current
    val ink = Palette.TextPrimary
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val target: Rect? = remember(layout, word, text) {
        val l = layout ?: return@remember null
        val w = word ?: return@remember null
        if (w.first < 0 || w.last >= text.length || w.isEmpty()) return@remember null
        runCatching {
            val a = l.getBoundingBox(w.first)
            val line = l.getLineForOffset(w.first)
            val lastOnLine = minOf(w.last, l.getLineEnd(line, visibleEnd = true) - 1).coerceAtLeast(w.first)
            val b = l.getBoundingBox(lastOnLine)
            Rect(a.left, a.top, b.right, b.bottom)
        }.getOrNull()
    }
    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target == null) {
            shown.animateTo(0f, tween(180))
            return@LaunchedEffect
        }
        val spec = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
        if (shown.value < 0.05f) {
            left.snapTo(target.left)
            top.snapTo(target.top)
            right.snapTo(target.right)
            bottom.snapTo(target.bottom)
        }
        coroutineScope {
            launch { left.animateTo(target.left, spec) }
            launch { top.animateTo(target.top, spec) }
            launch { right.animateTo(target.right, spec) }
            launch { bottom.animateTo(target.bottom, spec) }
            launch { shown.animateTo(1f, tween(160)) }
        }
    }
    val litColor = when (skin) {
        Skin.CLASSIC -> Palette.OnAccent
        Skin.PAPER -> ink
        Skin.TERMINAL -> Palette.Background
    }
    val annotated = buildAnnotatedString {
        if (word == null || word.first !in text.indices) {
            append(text)
        } else {
            val end = (word.last + 1).coerceAtMost(text.length)
            append(text.substring(0, word.first))
            withStyle(SpanStyle(color = litColor, fontWeight = FontWeight.ExtraBold)) { append(text.substring(word.first, end)) }
            append(text.substring(end))
        }
    }
    Text(
        annotated,
        color = ink,
        fontSize = if (skin == Skin.PAPER) 23.sp else 21.sp,
        lineHeight = if (skin == Skin.PAPER) 31.sp else 29.sp,
        fontWeight = if (skin == Skin.PAPER) FontWeight.Bold else FontWeight.SemiBold,
        fontFamily = if (skin == Skin.TERMINAL) LocalTokens.current.body else LocalTokens.current.display,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (shown.value <= 0f) return@drawBehind
                val padX = 4.dp.toPx()
                val padY = 1.dp.toPx()
                val topLeft = Offset(left.value - padX, top.value - padY)
                val size = Size(right.value - left.value + padX * 2, bottom.value - top.value + padY * 2)
                when (skin) {
                    Skin.CLASSIC -> drawRoundRect(
                        Brush.horizontalGradient(listOf(accent.start, accent.end), startX = topLeft.x, endX = topLeft.x + size.width),
                        topLeft,
                        size,
                        CornerRadius(8.dp.toPx()),
                        alpha = shown.value,
                    )
                    // A highlighter stroke over the lower two thirds of the word, slightly tilted feel.
                    Skin.PAPER -> drawRoundRect(
                        accent.start.copy(alpha = 0.28f * shown.value),
                        Offset(topLeft.x, topLeft.y + size.height * 0.38f),
                        Size(size.width, size.height * 0.56f),
                        CornerRadius(3.dp.toPx()),
                    )
                    Skin.TERMINAL -> drawRect(accent.start.copy(alpha = shown.value), topLeft, size)
                }
            },
    )
}

// MARK: - Progress

/** Where the reading is (0..1), as a bar with a knob; a touch on it jumps to that place. */
@Composable
internal fun ReadingStrip(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val track = Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.15f else 0.12f)
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 140f), label = "readingStrip")
    val transition = rememberInfiniteTransition(label = "knob")
    val glow by transition.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "knobGlow")
    if (skin == Skin.TERMINAL) {
        val cells = 26
        val full = (shown * cells).toInt().coerceIn(0, cells)
        Text(
            "[" + "#".repeat(full) + "-".repeat(cells - full) + "] ${(shown * 100).toInt()}%",
            color = accent.start,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = modifier.pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } },
        )
        return
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = if (skin == Skin.PAPER) 3.dp.toPx() else 6.dp.toPx()
        val y = center.y - h / 2
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        val w = size.width * shown
        if (skin == Skin.PAPER) {
            drawRoundRect(accent.start, Offset(0f, y), Size(w, h), CornerRadius(h / 2))
            drawCircle(accent.start, 6.dp.toPx(), Offset(w, center.y))
        } else {
            drawRoundRect(Brush.horizontalGradient(listOf(accent.start, accent.end)), Offset(0f, y), Size(w, h), CornerRadius(h / 2))
            drawCircle(accent.end.copy(alpha = glow), 11.dp.toPx(), Offset(w, center.y))
            drawCircle(Color.White, 6.dp.toPx(), Offset(w, center.y))
        }
    }
}

/** A small round button (previous / next sentence). */
@Composable
internal fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape
    Box(
        Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick)
            .size(54.dp)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, if (skin == Skin.TERMINAL) accent.copy(alpha = 0.6f) else Palette.Stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (skin == Skin.TERMINAL) accent else Palette.TextPrimary, modifier = Modifier.size(28.dp))
    }
}

/** A row of [content] centred with a breath of space. */
@Composable
internal fun CenteredRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}
