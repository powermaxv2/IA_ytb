package com.powermaxv2.ytdownloader.ui.tools.timer

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Pieces of Minuteur et chrono: the "Minuteurs | Chrono" switch, the digits that roll like a flip clock, the ring
// that empties as a timer runs (and flashes when it rings), the stopwatch dial with its sweeping hand, and the
// small round buttons of the cards. Each piece is drawn in the skin's own way.

@Composable
internal fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

// MARK: - Minuteurs | Chrono

/** Two-way switch: the thumb slides under the chosen side with a spring; each side may carry a small count. */
@Composable
internal fun TimerTabs(watch: Boolean, timersBadge: Int, watchRunning: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val slide by animateFloatAsState(if (watch) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f), label = "timerTab")
    val radius: Dp = if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(4.dp),
    ) {
        val paperThumb = Palette.Surface
        Canvas(Modifier.fillMaxSize()) {
            val half = size.width / 2
            val brush = when (skin) {
                Skin.CLASSIC -> Brush.horizontalGradient(listOf(accent.start, accent.end), startX = slide * half, endX = slide * half + half)
                Skin.PAPER -> SolidColor(paperThumb)
                Skin.TERMINAL -> SolidColor(accent.start)
            }
            drawRoundRect(brush, topLeft = Offset(slide * half, 0f), size = Size(half, size.height), cornerRadius = CornerRadius(radius.toPx()))
        }
        Row(Modifier.fillMaxSize()) {
            listOf(false to "Minuteurs", true to "Chrono").forEach { (value, label) ->
                val on = value == watch
                val ink by animateColorAsState(
                    when {
                        !on -> Palette.TextSecondary
                        skin == Skin.CLASSIC -> Palette.OnAccent
                        skin == Skin.TERMINAL -> Palette.Background
                        else -> Palette.TextPrimary
                    },
                    tween(200),
                    label = "timerTabInk",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .bounceClick(pressedScale = 0.95f) { onChange(value) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        skinText(label),
                        color = ink,
                        fontSize = if (skin == Skin.TERMINAL) 12.5.sp else 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.sp,
                        maxLines = 1,
                    )
                    val dot = if (value) watchRunning else timersBadge > 0
                    if (dot) {
                        Box(Modifier.width(7.dp))
                        LiveDot(ink, if (value) null else timersBadge)
                    }
                }
            }
        }
    }
}

/** A breathing dot (something runs), or a small counter. */
@Composable
private fun LiveDot(color: Color, count: Int?) {
    val t = rememberInfiniteTransition(label = "liveDot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveDotAlpha")
    if (count == null) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = a)))
    } else {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text("$count", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: - Rolling digits

/**
 * [text] where each changed character rolls in from below (up when [down] is false) like a flip clock; the ":" and
 * "." stay still. Each position animates on its own, so only the digits that change move.
 */
@Composable
internal fun RollingDigits(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    down: Boolean = true,
    weight: FontWeight = FontWeight.ExtraBold,
) {
    val tokens = LocalTokens.current
    val family = if (LocalSkin.current == Skin.TERMINAL) FontFamily.Monospace else tokens.display
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, c ->
            if (!c.isDigit()) {
                Text(c.toString(), color = color, fontSize = size, fontWeight = weight, fontFamily = family)
            } else {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        val dir = if (down) 1 else -1
                        (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { h -> -dir * h } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { h -> dir * h } + fadeOut(tween(160)))
                    },
                    label = "digit${text.length - i}",
                ) { d ->
                    Text(d.toString(), color = color, fontSize = size, fontWeight = weight, fontFamily = family)
                }
            }
        }
    }
}

// MARK: - Countdown ring

/**
 * The ring of a timer: the track, the remaining part ([progress] = share gone) eaten clockwise, a bright head at
 * its tip. While [ringing] the whole ring flashes in the danger colour and the [content] shakes.
 */
@Composable
internal fun CountdownRing(
    progress: Float,
    running: Boolean,
    ringing: Boolean,
    modifier: Modifier = Modifier,
    stroke: Dp = 9.dp,
    content: @Composable () -> Unit,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val track = Palette.Stroke
    val danger = Palette.Danger
    val ink = Palette.TextPrimary
    val shown by animateFloatAsState(progress, tween(if (running) 260 else 500, easing = LinearEasing), label = "ringProgress")
    val t = rememberInfiniteTransition(label = "ring")
    val flash by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "ringFlash")
    val wobble by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "ringWobble")
    val glow by t.animateFloat(0.25f, 0.7f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ringGlow")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2 + 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val left = 1f - shown
            val color = if (ringing) danger.copy(alpha = 0.45f + 0.55f * flash) else accent.start
            when (skin) {
                Skin.TERMINAL -> {
                    // 48 square segments, lit for the time left.
                    val n = 48
                    val lit = if (ringing) n else (left * n).let { kotlin.math.ceil(it).toInt() }
                    val r = arcSize.width / 2
                    val c = center
                    for (i in 0 until n) {
                        val a = (-90f + i * 360f / n) * (PI / 180f).toFloat()
                        val on = i < lit
                        val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
                        drawRect(
                            if (on) color else track,
                            topLeft = Offset(p.x - w * 0.35f, p.y - w * 0.35f),
                            size = Size(w * 0.7f, w * 0.7f),
                        )
                    }
                }
                else -> {
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(if (skin == Skin.PAPER) w * 0.45f else w))
                    if (running && !ringing && skin == Skin.CLASSIC) {
                        drawArc(accent.start.copy(alpha = glow * 0.35f), -90f, -360f * left, false, topLeft, arcSize, style = Stroke(w * 2.2f, cap = StrokeCap.Round))
                    }
                    val brush = if (skin == Skin.CLASSIC && !ringing) Brush.sweepGradient(listOf(accent.end, accent.start, accent.end)) else SolidColor(if (skin == Skin.PAPER && !ringing) ink else color)
                    val sweep = if (ringing) 360f else 360f * left
                    if (sweep > 0.5f) {
                        rotate(-90f) {
                            drawArc(brush, 0f, -sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                        }
                    }
                    // The head: a dot at the tip that travels with the time left.
                    if (running && !ringing && left > 0f) {
                        val a = (-90f - 360f * left) * (PI / 180f).toFloat()
                        val r = arcSize.width / 2
                        drawCircle(if (skin == Skin.PAPER) accent.start else Color.White, w * 0.42f, Offset(center.x + r * cos(a), center.y + r * sin(a)))
                    }
                }
            }
        }
        Box(Modifier.rotate(if (ringing) wobble * 3f else 0f).scale(if (ringing) 1f + 0.04f * flash else 1f)) { content() }
    }
}

// MARK: - Stopwatch dial

/**
 * A watch face: 60 ticks (every 5th longer), a hand sweeping once a minute, a dot on the rim for each lap and a
 * small hand counting the minutes; [content] sits in the middle.
 */
@Composable
internal fun StopwatchDial(elapsedMs: Long, laps: List<Long>, running: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tick = Palette.TextTertiary
    val ink = Palette.TextPrimary
    val stroke = Palette.Stroke
    val success = Palette.Success
    val t = rememberInfiniteTransition(label = "dial")
    val breathe by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dialBreathe")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 - 4f
            val c = center
            if (skin != Skin.TERMINAL) drawCircle(stroke, r, c, style = Stroke(if (skin == Skin.PAPER) 1.5f else 3f))
            // Ticks.
            for (i in 0 until 60) {
                val long = i % 5 == 0
                val a = (i * 6f - 90f) * (PI / 180f).toFloat()
                val outer = r - 6f
                val inner = outer - if (long) 22f else 10f
                val p1 = Offset(c.x + inner * cos(a), c.y + inner * sin(a))
                val p2 = Offset(c.x + outer * cos(a), c.y + outer * sin(a))
                if (skin == Skin.TERMINAL) {
                    if (long) drawRect(tick, Offset(p2.x - 4f, p2.y - 4f), Size(8f, 8f)) else drawRect(tick.copy(alpha = 0.5f), Offset(p2.x - 2f, p2.y - 2f), Size(4f, 4f))
                } else {
                    drawLine(if (long) ink.copy(alpha = 0.7f) else tick.copy(alpha = 0.6f), p1, p2, strokeWidth = if (long) 4f else 2f, cap = StrokeCap.Round)
                }
            }
            // Laps on the rim, where the hand was.
            laps.takeLast(20).forEachIndexed { i, total ->
                val a = ((total % 60_000) / 60_000f * 360f - 90f) * (PI / 180f).toFloat()
                val rr = r - 40f
                drawCircle(success.copy(alpha = 0.4f + 0.6f * (i + 1) / laps.takeLast(20).size), 6f, Offset(c.x + rr * cos(a), c.y + rr * sin(a)))
            }
            // Seconds trail: the minute so far, drawn as a thin arc.
            val secFrac = (elapsedMs % 60_000) / 60_000f
            val trail = r - 18f
            if (skin == Skin.CLASSIC) {
                drawArc(
                    Brush.sweepGradient(listOf(accent.start.copy(alpha = 0f), accent.start.copy(alpha = 0.5f))),
                    -90f, secFrac * 360f, false,
                    Offset(c.x - trail, c.y - trail), Size(trail * 2, trail * 2),
                    style = Stroke(10f, cap = StrokeCap.Butt),
                )
            }
            // Minute hand (one turn an hour), short.
            val minFrac = (elapsedMs % 3_600_000) / 3_600_000f
            rotate(minFrac * 360f, c) {
                drawLine(ink.copy(alpha = 0.35f), c, Offset(c.x, c.y - r * 0.32f), strokeWidth = 8f, cap = StrokeCap.Round)
            }
            // Second hand with its counterweight.
            rotate(secFrac * 360f, c) {
                val color = if (skin == Skin.PAPER) ink else accent.start
                drawLine(color, Offset(c.x, c.y + r * 0.14f), Offset(c.x, c.y - (r - 14f)), strokeWidth = if (skin == Skin.TERMINAL) 5f else 4f, cap = if (skin == Skin.TERMINAL) StrokeCap.Square else StrokeCap.Round)
                drawCircle(color, if (skin == Skin.TERMINAL) 7f else 9f, Offset(c.x, c.y - (r - 14f)))
            }
            drawCircle(if (running) accent.start.copy(alpha = breathe) else ink.copy(alpha = 0.5f), 10f, c)
        }
        content()
    }
}

// MARK: - Buttons

/** A round icon button of a card; [strong] fills it with the accent. */
@Composable
internal fun RoundAction(icon: ImageVector, label: String, strong: Boolean = false, danger: Boolean = false, size: Dp = 46.dp, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(tokens.chipRadius) else CircleShape
    val fill = when {
        strong && danger -> SolidColor(Palette.Danger)
        strong && skin == Skin.CLASSIC -> accent.brush
        strong -> SolidColor(if (skin == Skin.PAPER) Palette.TextPrimary else accent.start)
        else -> SolidColor(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
    }
    val tint = when {
        strong && skin == Skin.TERMINAL -> Palette.Background
        strong && skin == Skin.PAPER -> Palette.Surface
        strong -> Palette.OnAccent
        danger -> Palette.Danger
        else -> Palette.TextPrimary
    }
    Box(
        Modifier
            .size(size)
            .bounceClick(pressedScale = 0.86f, onClick = onClick)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (strong) Color.Transparent else Palette.Stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** A chip of a preset duration or of a name idea. */
@Composable
internal fun PickChip(text: String, selected: Boolean, index: Int, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val bg by animateColorAsState(
        when {
            !selected -> if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2
            skin == Skin.PAPER -> Palette.TextPrimary
            else -> accent.start
        },
        tween(220),
        label = "pickBg",
    )
    val ink = when {
        !selected -> Palette.TextPrimary
        skin == Skin.PAPER -> Palette.Surface
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.OnAccent
    }
    val pop by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "pickPop")
    Box(
        Modifier
            .staggeredEntrance(index, baseDelay = 30)
            .scale(pop)
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else Palette.Stroke, shape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(skinText(text), color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** One column of the duration picker: − value + ; the value rolls up or down with the change. */
@Composable
internal fun DurationColumn(value: Int, unit: String, onChange: (Int) -> Unit, max: Int, modifier: Modifier = Modifier) {
    var last by remember { mutableIntStateOf(value) }
    val down = value >= last
    SideEffect { last = value }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RoundAction(Icons.Rounded.Add, "Plus", size = 38.dp) { onChange(if (value >= max) 0 else value + 1) }
        RollingDigits("%02d".format(value), 34.sp, Palette.TextPrimary, down = down)
        Text(skinText(unit), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        RoundAction(Icons.Rounded.Remove, "Moins", size = 38.dp) { onChange(if (value <= 0) max else value - 1) }
    }
}

/** Little bell drawn for the ringing state and the empty screen; it swings when [swing]. */
@Composable
internal fun Bell(modifier: Modifier = Modifier, swing: Boolean = true, color: Color = LocalAccent.current.start) {
    val t = rememberInfiniteTransition(label = "bell")
    val a by t.animateFloat(-18f, 18f, infiniteRepeatable(tween(if (swing) 140 else 1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bellAngle")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        rotate(if (swing) a else a / 6f, Offset(w / 2, h * 0.1f)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                cubicTo(w * 0.22f, h * 0.12f, w * 0.24f, h * 0.45f, w * 0.2f, h * 0.62f)
                lineTo(w * 0.1f, h * 0.76f)
                lineTo(w * 0.9f, h * 0.76f)
                lineTo(w * 0.8f, h * 0.62f)
                cubicTo(w * 0.76f, h * 0.45f, w * 0.78f, h * 0.12f, w * 0.5f, h * 0.12f)
                close()
            }
            drawPath(path, color)
            drawCircle(color, w * 0.09f, Offset(w * 0.5f, h * 0.86f))
            drawCircle(color, w * 0.06f, Offset(w * 0.5f, h * 0.08f))
        }
    }
}
