package com.powermaxv2.ytdownloader.ui.tools.level

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.level.LevelMath
import com.powermaxv2.ytdownloader.core.level.LevelReading
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Pieces of the Niveau à bulle: the round vial (phone lying flat), the tube (phone on its edge), the big reading,
// the "DROIT" stamp and the card shown when the phone has no tilt sensor. Each is drawn natively in the three skins.

/** Home card icon: a level's tube with its bubble in the middle. */
val LevelIcon: ImageVector by lazy {
    ImageVector.Builder("Niveau", 24.dp, 24.dp, 24f, 24f).apply {
        // Tube outline (even-odd: an outer and an inner rounded rectangle).
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M5,7.5h14a4.5,4.5 0 0 1 0,9h-14a4.5,4.5 0 0 1 0,-9z" +
                    "M5,9.3a2.7,2.7 0 0 0 0,5.4h14a2.7,2.7 0 0 0 0,-5.4z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
        )
        // The bubble and the two marks either side of it.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M12,10.4a1.6,1.6 0 1 1 0,3.2a1.6,1.6 0 1 1 0,-3.2z M8.6,4h1.2v3h-1.2z M14.2,4h1.2v3h-1.2z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** Colour of the vial: the accent, turning to the success green as the reading gets straight. */
@Composable
private fun vialColor(level: Boolean): Color {
    val accent = LocalAccent.current.start
    val color by animateColorAsState(if (level) Palette.Success else accent, tween(320), label = "vialColor")
    return color
}

/** Rings that leave the centre while the phone is straight. */
@Composable
private fun rememberRipple(): Float {
    val t = rememberInfiniteTransition(label = "levelRipple")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "ripple")
    return ripple
}

/**
 * Round vial for a phone lying flat: rings, a cross, and the bubble that slides to the high side on a spring
 * (10° = the edge). Straight: the bubble locks in the centre, goes green and rings leave it.
 */
@Composable
internal fun FlatVial(reading: LevelReading.Flat?, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val level = reading?.level == true
    val color = vialColor(level)
    val springy = spring<Float>(dampingRatio = 0.55f, stiffness = 140f)
    val bx by animateFloatAsState(LevelMath.bubbleShare(reading?.tiltX ?: 0f), springy, label = "bubbleX")
    val by by animateFloatAsState(-LevelMath.bubbleShare(reading?.tiltY ?: 0f), springy, label = "bubbleY")
    val glow by animateFloatAsState(if (level) 1f else 0f, tween(400), label = "vialGlow")
    val ripple = rememberRipple()
    val ink = Palette.TextPrimary
    val surface = Palette.Surface2
    val background = Palette.Background
    Canvas(modifier.fillMaxWidth(0.84f).aspectRatio(1f)) {
        val r = size.minDimension / 2f * 0.94f
        val c = center
        val bubbleR = r * 0.16f
        val travel = r - bubbleR - r * 0.04f
        var dx = bx * travel
        var dy = by * travel
        val m = hypot(dx, dy)
        if (m > travel) {
            dx = dx / m * travel
            dy = dy / m * travel
        }
        val bubble = Offset(c.x + dx, c.y + dy)
        when (skin) {
            Skin.CLASSIC -> {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f + 0.12f * glow), surface), c, r), r, c)
                drawCircle(accent.brush, r, c, style = Stroke(3.dp.toPx()))
                listOf(0.33f, 0.66f).forEach { drawCircle(ink.copy(alpha = 0.14f), r * it, c, style = Stroke(1.dp.toPx())) }
                cross(c, r, ink.copy(alpha = 0.16f), 1.dp.toPx())
                ripples(c, bubbleR * 1.3f, r, ripple, glow, color)
                drawCircle(color.copy(alpha = 0.55f), bubbleR * 1.28f, c, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                drawCircle(Brush.radialGradient(listOf(Color.White, color.copy(alpha = 0.85f)), bubble - Offset(bubbleR * 0.3f, bubbleR * 0.3f), bubbleR * 1.4f), bubbleR, bubble)
                drawCircle(Color.White.copy(alpha = 0.8f), bubbleR * 0.24f, bubble - Offset(bubbleR * 0.38f, bubbleR * 0.38f))
            }
            Skin.PAPER -> {
                drawCircle(background, r, c)
                drawCircle(ink, r, c, style = Stroke(2.5.dp.toPx()))
                // Graduations around, like a printed dial.
                for (i in 0 until 72) {
                    val a = i / 72f * 2f * PI.toFloat()
                    val long = i % 18 == 0
                    val inner = r * if (long) 0.86f else 0.92f
                    drawLine(ink.copy(alpha = if (long) 0.8f else 0.35f), Offset(c.x + cos(a) * inner, c.y + sin(a) * inner), Offset(c.x + cos(a) * r, c.y + sin(a) * r), 1.2.dp.toPx())
                }
                drawCircle(ink.copy(alpha = 0.3f), r * 0.5f, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))))
                cross(c, r * 0.84f, ink.copy(alpha = 0.25f), 1.dp.toPx())
                ripples(c, bubbleR * 1.3f, r * 0.84f, ripple, glow, color)
                drawCircle(ink, bubbleR * 1.3f, c, style = Stroke(1.5.dp.toPx()))
                drawCircle(color, bubbleR, bubble)
                drawCircle(ink, bubbleR, bubble, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.7f), bubbleR * 0.22f, bubble - Offset(bubbleR * 0.35f, bubbleR * 0.35f))
            }
            Skin.TERMINAL -> {
                // A radar grid in phosphor green / accent.
                val step = r / 5f
                for (i in -5..5) {
                    val v = i * step
                    val half = kotlin.math.sqrt((r * r - v * v).coerceAtLeast(0f))
                    drawLine(color.copy(alpha = if (i == 0) 0.6f else 0.12f), Offset(c.x - half, c.y + v), Offset(c.x + half, c.y + v), 1.dp.toPx())
                    drawLine(color.copy(alpha = if (i == 0) 0.6f else 0.12f), Offset(c.x + v, c.y - half), Offset(c.x + v, c.y + half), 1.dp.toPx())
                }
                drawCircle(color, r, c, style = Stroke(1.dp.toPx()))
                drawCircle(color.copy(alpha = 0.5f), bubbleR * 1.3f, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
                if (glow > 0f) {
                    val s = bubbleR * 1.3f + (r - bubbleR) * ripple
                    drawRect(color.copy(alpha = (1f - ripple) * 0.5f * glow), Offset(c.x - s, c.y - s), Size(s * 2, s * 2), style = Stroke(1.dp.toPx()))
                }
                drawCircle(color, bubbleR, bubble, style = Stroke(2.dp.toPx()))
                drawCircle(color, bubbleR * 0.25f, bubble)
                drawLine(color, bubble - Offset(bubbleR * 1.6f, 0f), bubble - Offset(bubbleR * 1.1f, 0f), 1.dp.toPx())
                drawLine(color, bubble + Offset(bubbleR * 1.1f, 0f), bubble + Offset(bubbleR * 1.6f, 0f), 1.dp.toPx())
            }
        }
    }
}

/**
 * Tube for a phone standing on an edge: the bubble slides to the high end (5° = the end), between two marks; a
 * dashed line shows the true horizontal. The whole tube turns with the edge that is down ([LevelReading.Edge.snap]).
 */
@Composable
internal fun EdgeVial(reading: LevelReading.Edge?, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val level = reading?.level == true
    val color = vialColor(level)
    val share by animateFloatAsState(LevelMath.bubbleShare(reading?.angle ?: 0f, range = 5f), spring(dampingRatio = 0.5f, stiffness = 120f), label = "tubeBubble")
    val horizon by animateFloatAsState(reading?.angle ?: 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "horizon")
    val turn by animateFloatAsState((reading?.snap ?: 0).toFloat(), spring(dampingRatio = 0.75f, stiffness = 160f), label = "tubeTurn")
    val glow by animateFloatAsState(if (level) 1f else 0f, tween(400), label = "tubeGlow")
    val ripple = rememberRipple()
    val ink = Palette.TextPrimary
    val surface = Palette.Surface2
    val background = Palette.Background
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val c = center
        val sideways = ((turn / 90f).toInt() % 2) != 0
        val length = (if (sideways) size.height else size.width) * 0.9f
        val thick = 58.dp.toPx().coerceAtMost(length / 3f)
        rotate(turn, c) {
            // True horizontal, behind the tube.
            rotate(horizon, c) {
                drawLine(
                    color.copy(alpha = 0.45f),
                    Offset(c.x - size.maxDimension, c.y),
                    Offset(c.x + size.maxDimension, c.y),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }
            val left = c.x - length / 2
            val top = c.y - thick / 2
            val radius = if (skin == Skin.TERMINAL) 4.dp.toPx() else thick / 2
            val bw = thick * 1.5f
            val bh = thick * 0.6f
            val travel = length / 2 - bw / 2 - thick * 0.2f
            val bxc = c.x + share * travel
            val markGap = bw / 2 + 5.dp.toPx()
            when (skin) {
                Skin.CLASSIC -> {
                    drawRoundRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f + 0.15f * glow), surface, color.copy(alpha = 0.25f)), top, top + thick), Offset(left, top), Size(length, thick), CornerRadius(radius))
                    drawRoundRect(accent.brush, Offset(left, top), Size(length, thick), CornerRadius(radius), style = Stroke(3.dp.toPx()))
                }
                Skin.PAPER -> {
                    drawRoundRect(background, Offset(left, top), Size(length, thick), CornerRadius(radius))
                    drawRoundRect(ink, Offset(left, top), Size(length, thick), CornerRadius(radius), style = Stroke(2.5.dp.toPx()))
                    for (i in -10..10) {
                        val x = c.x + i * travel / 10f
                        val h = if (i % 5 == 0) thick * 0.22f else thick * 0.12f
                        drawLine(ink.copy(alpha = 0.45f), Offset(x, top + thick), Offset(x, top + thick - h), 1.dp.toPx())
                    }
                }
                Skin.TERMINAL -> {
                    drawRoundRect(color, Offset(left, top), Size(length, thick), CornerRadius(radius), style = Stroke(1.dp.toPx()))
                    for (i in -10..10) {
                        val x = c.x + i * travel / 10f
                        drawLine(color.copy(alpha = 0.25f), Offset(x, top + 4.dp.toPx()), Offset(x, top + thick - 4.dp.toPx()), 1.dp.toPx())
                    }
                }
            }
            // The two marks the bubble must sit between.
            listOf(-1f, 1f).forEach { side ->
                val x = c.x + side * markGap
                drawLine(if (skin == Skin.PAPER) ink else color, Offset(x, top - 6.dp.toPx()), Offset(x, top + thick + 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
            }
            if (glow > 0f) {
                val grow = ripple * thick * 0.9f
                drawRoundRect(
                    color.copy(alpha = (1f - ripple) * 0.55f * glow),
                    Offset(c.x - markGap - grow, top - grow / 2),
                    Size((markGap + grow) * 2, thick + grow),
                    CornerRadius(if (skin == Skin.TERMINAL) 0f else (thick + grow) / 2),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            val bTop = Offset(bxc - bw / 2, c.y - bh / 2)
            when (skin) {
                Skin.CLASSIC -> {
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White, color.copy(alpha = 0.85f)), bTop.y, bTop.y + bh), bTop, Size(bw, bh), CornerRadius(bh / 2))
                    drawRoundRect(Color.White.copy(alpha = 0.75f), bTop + Offset(bw * 0.18f, bh * 0.16f), Size(bw * 0.3f, bh * 0.2f), CornerRadius(bh * 0.1f))
                }
                Skin.PAPER -> {
                    drawRoundRect(color, bTop, Size(bw, bh), CornerRadius(bh / 2))
                    drawRoundRect(ink, bTop, Size(bw, bh), CornerRadius(bh / 2), style = Stroke(2.dp.toPx()))
                }
                Skin.TERMINAL -> {
                    drawRect(color.copy(alpha = 0.25f), bTop, Size(bw, bh))
                    drawRect(color, bTop, Size(bw, bh), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.cross(c: Offset, r: Float, color: Color, width: Float) {
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), width)
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), width)
}

private fun DrawScope.ripples(c: Offset, from: Float, to: Float, t: Float, glow: Float, color: Color) {
    if (glow <= 0f) return
    listOf(t, (t + 0.5f) % 1f).forEach { p ->
        drawCircle(color.copy(alpha = (1f - p) * 0.5f * glow), from + (to - from) * p, c, style = Stroke(2.dp.toPx()))
    }
}

/** The big angle, the advice, and for a shelf the millimetres per metre. Pops the "DROIT" stamp when straight. */
@Composable
internal fun LevelReadout(reading: LevelReading?, frozen: Boolean, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val level = reading?.level == true
    val color = vialColor(level)
    val angle = when (reading) {
        is LevelReading.Flat -> reading.tilt
        is LevelReading.Edge -> reading.angle
        null -> 0f
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (reading == null) "–,–°" else if (terminal) "ANGLE=${LevelMath.degrees(angle)}" else LevelMath.degrees(angle),
                color = if (reading == null) Palette.TextTertiary else color,
                fontSize = if (terminal) 40.sp else 64.sp,
                lineHeight = if (terminal) 46.sp else 68.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = if (terminal) FontFamily.Monospace else LocalTokens.current.display,
                letterSpacing = if (terminal) 0.sp else (-2).sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StraightStamp(level)
            AnimatedVisibility(frozen, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                FrozenPill()
            }
        }
        Spacer(Modifier.height(8.dp))
        val advice = reading?.let { LevelMath.advice(it) } ?: "Pose le téléphone"
        Text(
            if (terminal) "# ${advice.uppercase()}" else advice,
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        val detail = when (reading) {
            is LevelReading.Flat -> "Gauche-droite ${LevelMath.degrees(reading.tiltX)} · haut-bas ${LevelMath.degrees(reading.tiltY)} · ${LevelMath.mmPerMetre(reading.tilt)} mm par mètre"
            is LevelReading.Edge -> "Soit ${LevelMath.mmPerMetre(reading.angle)} mm de différence sur 1 mètre"
            null -> "Le niveau attend la première mesure du capteur."
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (terminal) "# $detail" else detail,
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** "DROIT" (or "[ OK ]") stamped with a bounce the moment the phone is straight, dimmed otherwise. */
@Composable
private fun StraightStamp(level: Boolean) {
    val skin = LocalSkin.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(level) {
        if (level) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
        }
    }
    val tilt by animateFloatAsState(if (level) -6f else 0f, spring(dampingRatio = 0.4f), label = "stampTilt")
    val color = if (level) Palette.Success else Palette.TextTertiary
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 2.dp else 10.dp)
    Row(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = if (skin == Skin.PAPER) tilt else 0f
            }
            .clip(shape)
            .background(if (level && skin == Skin.CLASSIC) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(if (skin == Skin.PAPER) 2.dp else 1.dp, color, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level && skin != Skin.TERMINAL) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            when (skin) {
                Skin.TERMINAL -> if (level) "[ OK ]" else "[ .. ]"
                else -> if (level) "DROIT" else "PAS ENCORE"
            },
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else null,
        )
    }
}

/** "Figé": the measure is held, so the phone can be read once taken back. */
@Composable
private fun FrozenPill() {
    val t = rememberInfiniteTransition(label = "frozen")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "frozenPulse")
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .clip(RoundedCornerShape(if (terminal) 2.dp else 10.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp).graphicsLayer { alpha = pulse })
        Spacer(Modifier.width(4.dp))
        Text(if (terminal) "HOLD" else "FIGÉ", color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
    }
}

/**
 * Shown instead of the vial when the phone cannot measure a tilt: a phone that rocks, searching, with a question
 * mark: the bubble of its tube keeps crossing without stopping.
 */
@Composable
internal fun NoSensorArt(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val ink = Palette.TextPrimary
    val t = rememberInfiniteTransition(label = "noSensor")
    val rock by t.animateFloat(-12f, 12f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rock")
    val dot by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "searchDot")
    Canvas(modifier.size(150.dp)) {
        val c = center
        val w = size.width * 0.38f
        val h = size.height * 0.66f
        rotate(rock, c) {
            val r = if (terminal) 3.dp.toPx() else 14.dp.toPx()
            drawRoundRect(accent.copy(alpha = 0.12f), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(r))
            drawRoundRect(if (terminal) accent else ink, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(r), style = Stroke(2.5.dp.toPx()))
            // Empty tube on the screen, its bubble lost.
            val tw = w * 0.7f
            val th = w * 0.22f
            drawRoundRect(accent, Offset(c.x - tw / 2, c.y - th / 2), Size(tw, th), CornerRadius(th / 2), style = Stroke(1.5.dp.toPx()))
            val x = c.x - tw / 2 + th / 2 + (tw - th) * dot
            drawCircle(accent.copy(alpha = sin(dot * PI.toFloat())), th * 0.32f, Offset(x, c.y))
        }
    }
}
