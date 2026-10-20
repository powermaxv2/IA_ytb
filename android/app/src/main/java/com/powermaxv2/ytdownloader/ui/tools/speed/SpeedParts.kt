package com.powermaxv2.ytdownloader.ui.tools.speed

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.speed.SpeedPlan
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// The Vitesse vidéo's moving parts, drawn natively in the three skins: the speed dial (a needle that springs to
// the chosen speed, dragged or tapped), the film strip that runs at that speed, and the before / after lengths.

/** The dial opens 240°, from bottom left (×0.25) to bottom right (×8), with ×1 at the top. */
private const val DIAL_START = 150f
private const val DIAL_SWEEP = 240f

/**
 * A speedometer: ticks for every offered speed, the needle springing (with a little overshoot) to [speed], and
 * the speed written in the middle, rolling up when faster, down when slower. Dragging or tapping it picks the
 * nearest speed ([onSpeed] null = display only).
 */
@Composable
internal fun SpeedDial(speed: Double, onSpeed: ((Double) -> Unit)?, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val ink = Palette.TextPrimary
    val muted = Palette.TextTertiary
    val tokens = LocalTokens.current
    val position by animateFloatAsState(SpeedPlan.dial(speed), spring(dampingRatio = 0.42f, stiffness = 170f), label = "dialNeedle")
    val pulse = rememberInfiniteTransition(label = "dialPulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "dialRing")
    val measurer = rememberTextMeasurer()
    val pick by rememberUpdatedState(onSpeed)
    val tickStyle = TextStyle(
        color = muted,
        fontSize = if (skin == Skin.TERMINAL) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else tokens.body,
    )
    fun choose(at: Offset, width: Float, height: Float) {
        val center = Offset(width / 2f, height * 0.58f)
        var deg = (atan2(at.y - center.y, at.x - center.x) * 180f / PI.toFloat() + 360f) % 360f
        var rel = (deg - DIAL_START + 360f) % 360f
        if (rel > DIAL_SWEEP) rel = if (rel > DIAL_SWEEP + (360f - DIAL_SWEEP) / 2f) 0f else DIAL_SWEEP
        deg = rel
        pick?.invoke(SpeedPlan.atDial(deg / DIAL_SWEEP))
    }
    val input = if (onSpeed == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) { detectTapGestures { choose(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> choose(change.position, size.width.toFloat(), size.height.toFloat()) }
            }
    }
    Box(modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().then(input)) {
            val center = Offset(size.width / 2f, size.height * 0.58f)
            val radius = minOf(size.width / 2f - 26.dp.toPx(), size.height * 0.5f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            val one = SpeedPlan.dial(1.0)
            val stroke = when (skin) {
                Skin.CLASSIC -> 14.dp.toPx()
                Skin.PAPER -> 10.dp.toPx()
                Skin.TERMINAL -> 3.dp.toPx()
            }
            // Track.
            when (skin) {
                Skin.TERMINAL -> drawArc(
                    accent.start.copy(alpha = 0.3f), DIAL_START, DIAL_SWEEP, false, arcTopLeft, arcSize,
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
                else -> drawArc(
                    ink.copy(alpha = if (skin == Skin.PAPER) 0.10f else 0.08f), DIAL_START, DIAL_SWEEP, false, arcTopLeft, arcSize,
                    style = Stroke(stroke, cap = if (skin == Skin.CLASSIC) StrokeCap.Round else StrokeCap.Butt),
                )
            }
            // Filled part: from ×1 to the needle (left = slower, right = faster).
            val from = DIAL_START + DIAL_SWEEP * minOf(one, position)
            val sweep = DIAL_SWEEP * kotlin.math.abs(position - one)
            val fill: Brush = when (skin) {
                Skin.CLASSIC -> Brush.sweepGradient(listOf(accent.start, accent.end, accent.start), center)
                Skin.PAPER -> Brush.linearGradient(listOf(accent.start, accent.start))
                Skin.TERMINAL -> Brush.linearGradient(listOf(accent.start, accent.start))
            }
            if (sweep > 0.5f) drawArc(fill, from, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = if (skin == Skin.CLASSIC) StrokeCap.Round else StrokeCap.Butt))
            // Ticks and their labels.
            (SpeedPlan.SPEEDS + 1.0).forEach { s ->
                val p = SpeedPlan.dial(s)
                val a = (DIAL_START + DIAL_SWEEP * p) * PI.toFloat() / 180f
                val near = kotlin.math.abs(p - position) < 0.02f
                val outer = radius - stroke / 2 - 4.dp.toPx()
                val inner = outer - (if (s == 1.0) 12.dp else 7.dp).toPx()
                drawLine(
                    if (near) accent.start else ink.copy(alpha = if (s == 1.0) 0.6f else 0.3f),
                    Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                    Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                    strokeWidth = (if (s == 1.0) 2.5f else 1.5f).dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val text = if (s == 1.0) "×1" else SpeedPlan.label(s).removePrefix("×")
                val layout = measurer.measure(text, if (near) tickStyle.copy(color = accent.start) else tickStyle)
                val lr = radius + stroke / 2 + 12.dp.toPx()
                val at = Offset(center.x + cos(a) * lr, center.y + sin(a) * lr)
                drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
            }
            // Needle, with a ring pulsing out of the hub faster when the speed is higher.
            val a = (DIAL_START + DIAL_SWEEP * position) * PI.toFloat() / 180f
            val tip = Offset(center.x + cos(a) * (radius - stroke - 10.dp.toPx()), center.y + sin(a) * (radius - stroke - 10.dp.toPx()))
            val ringR = 10.dp.toPx() + ring * 26.dp.toPx()
            drawCircle(accent.start.copy(alpha = (1f - ring) * 0.35f), ringR, center, style = Stroke(2.dp.toPx()))
            when (skin) {
                Skin.CLASSIC -> {
                    drawLine(Color.Black.copy(alpha = 0.25f), center + Offset(2f, 3f), tip + Offset(2f, 3f), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(accent.horizontal(), center, tip, 5.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent.brush, 11.dp.toPx(), center)
                    drawCircle(Color.White, 4.dp.toPx(), center)
                }
                Skin.PAPER -> {
                    drawLine(accent.start, center, tip, 4.dp.toPx(), StrokeCap.Butt)
                    drawCircle(ink, 10.dp.toPx(), center)
                    drawCircle(accent.start, 4.dp.toPx(), center)
                }
                Skin.TERMINAL -> {
                    drawLine(accent.start, center, tip, 2.dp.toPx(), StrokeCap.Square)
                    drawRect(accent.start, Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))
                }
            }
        }
        // The speed, written under the hub.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                speed,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "dialSpeed",
            ) { s ->
                Text(
                    SpeedPlan.label(s),
                    color = if (skin == Skin.TERMINAL) accent.start else ink,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = tokens.display,
                )
            }
            Text(
                if (skin == Skin.TERMINAL) "# ${SpeedPlan.mood(speed).lowercase()}" else SpeedPlan.mood(speed),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * A strip of film running right to left at [speed] (×1 = one cell a second), sprocket holes and all; frames of the
 * video when there are some, plain shaded cells otherwise. Fast speeds leave streaks, slow ones barely crawl.
 */
@Composable
internal fun FilmStrip(frames: List<Bitmap>, speed: Double, modifier: Modifier = Modifier, height: Int = 92) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val ink = Palette.TextPrimary
    val images: List<ImageBitmap> = remember(frames) { frames.map { it.asImageBitmap() } }
    val current by rememberUpdatedState(speed)
    val shift = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) shift.floatValue = (shift.floatValue + (now - last) / 1e9f * current.toFloat()) % 1_000f
                last = now
            }
        }
    }
    val film = when (skin) {
        Skin.CLASSIC -> Color(0xFF15151A)
        Skin.PAPER -> ink
        Skin.TERMINAL -> Palette.Background
    }
    val shape = RoundedCornerShape(if (skin == Skin.PAPER) 6.dp else LocalTokens.current.fieldRadius)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(film),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val hole = 7.dp.toPx()
            val band = 16.dp.toPx()
            val cellH = size.height - band * 2
            val cellW = cellH * 1.45f
            val gap = 6.dp.toPx()
            val step = cellW + gap
            val offset = (shift.floatValue * step) % step
            val first = -1
            val count = (size.width / step).toInt() + 3
            val base = floor(shift.floatValue).toInt()
            // Sprocket holes, moving with the film.
            val holeStep = 14.dp.toPx()
            val holeShift = (shift.floatValue * step) % holeStep
            var x = -holeShift
            while (x < size.width) {
                listOf(band / 2 - hole / 2, size.height - band / 2 - hole / 2).forEach { y ->
                    drawRoundRect(
                        if (skin == Skin.TERMINAL) accent.start.copy(alpha = 0.6f) else Color.White.copy(alpha = if (skin == Skin.PAPER) 0.9f else 0.75f),
                        Offset(x, y),
                        Size(hole, hole),
                        CornerRadius(2.dp.toPx()),
                        style = if (skin == Skin.TERMINAL) Stroke(1.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill,
                    )
                }
                x += holeStep
            }
            for (i in first until count) {
                val left = i * step - offset
                val index = ((base + i) % 1_000 + 1_000) % 1_000
                cell(left, band, cellW, cellH, index, images, skin, accent.start, accent.end)
            }
            // Speed streaks when fast.
            val streak = ((current.toFloat() - 1.5f) / 6.5f).coerceIn(0f, 1f)
            if (streak > 0f) {
                for (k in 0 until 5) {
                    val y = band + cellH * (0.15f + k * 0.18f)
                    val len = size.width * (0.25f + 0.1f * k)
                    val sx = ((shift.floatValue * 3f + k * 0.37f) % 1f) * (size.width + len) - len
                    drawLine(Color.White.copy(alpha = 0.18f * streak), Offset(sx + len, y), Offset(sx, y), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        // ▶ ×2 badge.
        Text(
            (if (skin == Skin.TERMINAL) "> " else "▶ ") + SpeedPlan.label(speed),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (skin == Skin.TERMINAL) accent.start.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** One picture of the film: a frame of the video (cropped to fill), or a shaded cell with a play mark. */
private fun DrawScope.cell(
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    index: Int,
    images: List<ImageBitmap>,
    skin: Skin,
    start: Color,
    end: Color,
) {
    if (left > size.width || left + w < 0) return
    clipRect(left, top, left + w, top + h) {
        if (images.isNotEmpty()) {
            val img = images[index % images.size]
            val scale = max(w / img.width, h / img.height)
            val dw = (img.width * scale).roundToInt()
            val dh = (img.height * scale).roundToInt()
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((left + (w - dw) / 2f).roundToInt(), (top + (h - dh) / 2f).roundToInt()),
                dstSize = IntSize(dw, dh),
            )
        } else {
            val t = (index % 6) / 5f
            val a = lerp(start, end, t)
            when (skin) {
                Skin.TERMINAL -> {
                    drawRect(start.copy(alpha = 0.10f + 0.06f * (index % 3)), Offset(left, top), Size(w, h))
                    drawRect(start.copy(alpha = 0.6f), Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
                }
                Skin.PAPER -> drawRect(if (index % 2 == 0) start else Color(0xFFF4EFE6), Offset(left, top), Size(w, h))
                Skin.CLASSIC -> drawRect(Brush.linearGradient(listOf(a, a.copy(alpha = 0.55f)), Offset(left, top), Offset(left + w, top + h)), Offset(left, top), Size(w, h))
            }
            // A little runner, one pose per cell: the strip reads as a flip book.
            val cx = left + w / 2
            val cy = top + h / 2
            val r = h * 0.12f
            val pose = (index % 4) / 3f * 2f - 1f
            val c = if (skin == Skin.PAPER && index % 2 == 1) start else Color.White
            drawCircle(c, r * 0.7f, Offset(cx + pose * r * 0.3f, cy - r * 1.6f))
            drawLine(c, Offset(cx, cy - r * 0.9f), Offset(cx, cy + r * 0.6f), r * 0.45f, StrokeCap.Round)
            drawLine(c, Offset(cx, cy + r * 0.6f), Offset(cx + pose * r, cy + r * 1.7f), r * 0.4f, StrokeCap.Round)
            drawLine(c, Offset(cx, cy + r * 0.6f), Offset(cx - pose * r, cy + r * 1.7f), r * 0.4f, StrokeCap.Round)
            drawLine(c, Offset(cx, cy - r * 0.5f), Offset(cx - pose * r * 0.9f, cy + r * 0.2f), r * 0.35f, StrokeCap.Round)
            drawLine(c, Offset(cx, cy - r * 0.5f), Offset(cx + pose * r * 0.9f, cy + r * 0.2f), r * 0.35f, StrokeCap.Round)
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f,
)

/** The video's length now and after: the "after" bar springs to its length, longer for a slow motion. */
@Composable
internal fun DurationBars(beforeMs: Long, afterMs: Long, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val longest = max(beforeMs, afterMs).coerceAtLeast(1L).toFloat()
    val after by animateFloatAsState(afterMs / longest, spring(dampingRatio = 0.6f, stiffness = 160f), label = "afterBar")
    val before by animateFloatAsState(beforeMs / longest, spring(dampingRatio = 0.8f, stiffness = 200f), label = "beforeBar")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BarRow("Avant", SpeedPlan.clock(beforeMs), before, Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.22f else 0.16f), null)
        BarRow("Après", SpeedPlan.clock(afterMs), after, accent.start, if (skin == Skin.CLASSIC) accent.horizontal() else null)
    }
}

@Composable
private fun BarRow(label: String, time: String, fraction: Float, color: Color, brush: Brush?) {
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (terminal) label.uppercase() else label,
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(if (terminal) 12.dp else 14.dp)
                .drawBehind {
                    val w = size.width * fraction.coerceIn(0.02f, 1f)
                    val r = if (skin == Skin.CLASSIC) CornerRadius(size.height / 2) else CornerRadius(if (terminal) 0f else 3.dp.toPx())
                    drawRoundRect(color.copy(alpha = 0.10f), cornerRadius = r)
                    if (brush != null) drawRoundRect(brush, size = Size(w, size.height), cornerRadius = r) else drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
                },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            time,
            color = Palette.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** The dial showing off on the first screen: it walks through a few speeds on its own. */
@Composable
internal fun DemoDial(modifier: Modifier = Modifier) {
    val tour = listOf(0.5, 2.0, 0.25, 4.0, 1.5, 8.0, 0.75)
    val transition = rememberInfiniteTransition(label = "demoDial")
    val t by transition.animateFloat(0f, tour.size.toFloat(), infiniteRepeatable(tween(tour.size * 1_500, easing = LinearEasing)), label = "demoTour")
    val speed = tour[t.toInt().coerceIn(0, tour.lastIndex)]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeedDial(speed, onSpeed = null)
        FilmStrip(emptyList(), speed, height = 72)
    }
}
