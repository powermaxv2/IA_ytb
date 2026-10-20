package com.powermaxv2.ytdownloader.ui.tools.password

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.password.Passwords
import com.powermaxv2.ytdownloader.core.password.Strength
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.paper.paperCard
import com.powermaxv2.ytdownloader.ui.staggeredEntrance

// Pieces of the Générateur de mots de passe: the password that scrambles like a slot machine before it settles, the
// five-step strength gauge filling segment by segment, the rolling bits counter, the dice that spins at each new
// draw, and the lines of the "Derniers créés" list. Each one is drawn in the three skins.

/** Text in the skin's case (Terminal writes in capitals). */
@Composable
internal fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** Colour of each step of the gauge, the same in every skin so "rouge = faible" reads everywhere. */
internal fun strengthColor(strength: Strength): Color = when (strength) {
    Strength.VERY_WEAK -> Color(0xFFE5484D)
    Strength.WEAK -> Color(0xFFFF8A1F)
    Strength.FAIR -> Color(0xFFF2B705)
    Strength.STRONG -> Color(0xFF30A46C)
    Strength.VERY_STRONG -> Color(0xFF12A594)
}

/** Glyphs that flicker in the slots before a character settles. */
private const val GLYPHS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789#$%&*+=?@"

/**
 * The password in a monospaced font, digits and symbols in their own colours. When [generation] changes, every slot
 * spins through random glyphs and they settle from left to right, like a slot machine.
 */
@Composable
internal fun ScrambledPassword(text: String, generation: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val spin = remember { Animatable(1f) }
    LaunchedEffect(generation, text.length) {
        spin.snapTo(0f)
        spin.animateTo(1f, tween(420 + text.length * 26, easing = LinearEasing))
    }
    val digit = if (skin == Skin.PAPER) Palette.WebIndigo else Palette.WebCyan
    val symbol = accent.start
    val ink = Palette.TextPrimary
    val size = when {
        text.length <= 14 -> 30.sp
        text.length <= 24 -> 23.sp
        text.length <= 40 -> 18.sp
        else -> 15.sp
    }
    val p = spin.value
    // Slots settle one after the other; the four extra steps leave the first ones spinning a little at the start.
    val settled = (p * (text.length + 4)).toInt() - 4
    val frame = (p * 60).toInt()
    val shown = buildAnnotatedString {
        text.forEachIndexed { i, c ->
            val final = i < settled || p >= 1f
            val ch = if (final || c == ' ') c else GLYPHS[(i * 31 + frame * 17 + c.code) % GLYPHS.length]
            val color = when {
                !final -> ink.copy(alpha = 0.35f)
                ch.isDigit() -> digit
                ch.isLetter() -> ink
                else -> symbol
            }
            withStyle(SpanStyle(color = color, fontWeight = if (final) FontWeight.Bold else FontWeight.Normal)) { append(ch) }
        }
    }
    Text(
        shown,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        lineHeight = size * 1.3f,
        letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.5.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Mot de passe : $text" },
    )
}

/**
 * Five segments filling one after the other in the colour of the [strength] (Terminal: a `[██████░░░░]` bar), with
 * a sheen running over them once the password is "Très fort".
 */
@Composable
internal fun StrengthGauge(strength: Strength?, modifier: Modifier = Modifier) {
    val color by animateColorAsState(strength?.let(::strengthColor) ?: Palette.Stroke, tween(380), label = "gaugeColor")
    val filled = strength?.segments ?: 0
    when (LocalSkin.current) {
        Skin.TERMINAL -> {
            val cells by animateFloatAsState(filled * 2f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "gaugeCells")
            val n = cells.toInt().coerceIn(0, 10)
            Text(
                "[" + "█".repeat(n) + "░".repeat(10 - n) + "]",
                color = color,
                fontSize = 18.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                modifier = modifier,
            )
        }
        else -> {
            val paper = LocalSkin.current == Skin.PAPER
            val sheen = rememberInfiniteTransition(label = "gaugeSheen")
            val sweep by sheen.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "gaugeSweep")
            Row(modifier.fillMaxWidth().height(if (paper) 12.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { i ->
                    val fill by animateFloatAsState(
                        if (i < filled) 1f else 0f,
                        tween(260, delayMillis = if (i < filled) i * 90 else (4 - i) * 40),
                        label = "gaugeSegment$i",
                    )
                    val shape = RoundedCornerShape(if (paper) 3.dp else 99.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(if (paper) Color.Transparent else Palette.TextPrimary.copy(alpha = 0.08f))
                            .then(if (paper) Modifier.border(1.5.dp, Palette.TextPrimary, shape) else Modifier)
                            .drawWithContent {
                                drawRect(color, size = size.copy(width = size.width * fill))
                                if (strength == Strength.VERY_STRONG && fill >= 1f) {
                                    val x = (sweep * 5f - i) * size.width
                                    drawRect(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                            startX = x - size.width * 0.6f,
                                            endX = x + size.width * 0.6f,
                                        ),
                                    )
                                }
                                drawContent()
                            },
                    )
                }
            }
        }
    }
}

/** A whole number whose digits roll up as it changes ("128"). */
@Composable
internal fun RollingNumber(value: Int, color: Color, modifier: Modifier = Modifier) {
    val text = value.coerceAtLeast(0).toString()
    val family = LocalTokens.current.display
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)))
                },
                label = "bits${text.length - index}",
            ) { digit ->
                Text(digit.toString(), color = color, fontSize = 26.sp, fontFamily = family, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

/** The gauge with its words under it: "Très fort", the rolling bits, and how long a computer would need. */
@Composable
internal fun StrengthBlock(bits: Double?, modifier: Modifier = Modifier) {
    val strength = bits?.let { Strength.of(it) }
    val color = strength?.let(::strengthColor) ?: Palette.TextTertiary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StrengthGauge(strength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = strength,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 320f)) { it / 2 } + fadeIn(tween(200))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(140)))
                },
                label = "strengthLabel",
                modifier = Modifier.weight(1f),
            ) { s ->
                Text(
                    skinText(s?.label ?: "Rien à mesurer"),
                    color = s?.let(::strengthColor) ?: Palette.TextTertiary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    maxLines = 1,
                )
            }
            if (bits != null) {
                RollingNumber(bits.toInt(), color)
                Text(
                    skinText(" bits"),
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (bits != null) {
            val time = Passwords.crackTime(bits)
            Text(
                if (LocalSkin.current == Skin.TERMINAL) "# TROUVÉ PAR UN ORDINATEUR EN : ${time.uppercase()}" else "Un ordinateur le trouverait en : $time",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** Square button with a die that turns a full round at every new draw ([generation]). */
@Composable
internal fun DiceButton(generation: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val turn = remember { Animatable(0f) }
    LaunchedEffect(generation) {
        if (generation > 0) turn.animateTo(turn.targetValue + 360f, spring(dampingRatio = 0.55f, stiffness = 140f))
    }
    val base = modifier
        .size(64.dp)
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "Nouveau mot de passe"
        }
        .bounceClick(pressedScale = 0.88f, onClick = onClick)
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.cardRadius else 20.dp)
    val box = when (skin) {
        Skin.CLASSIC -> base.clip(shape).background(accent.tint(0.18f)).border(1.dp, accent.start.copy(alpha = 0.5f), shape)
        Skin.PAPER -> base.paperCard(shape)
        Skin.TERMINAL -> base.clip(shape).border(1.dp, accent.start, shape)
    }
    Box(box, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Casino,
            contentDescription = null,
            tint = if (skin == Skin.PAPER) Palette.TextPrimary else accent.start,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { rotationZ = turn.value },
        )
    }
}

/** A line of "Derniers créés": the password, small, and a copy icon. */
@Composable
internal fun HistoryRow(text: String, index: Int, onCopy: () -> Unit) {
    val accent = LocalAccent.current.start
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = text, baseDelay = 40)
            .bounceClick(pressedScale = 0.98f, onClick = onCopy)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent.copy(alpha = 1f - index * 0.15f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = Palette.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = Palette.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

/** A thin halo pulsing behind the password card each time a new one comes ([generation]). */
@Composable
internal fun Modifier.drawPulse(generation: Int): Modifier {
    val accent = LocalAccent.current.start
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(generation) {
        if (generation > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(700))
        }
    }
    return drawWithContent {
        val p = pulse.value
        if (p < 1f) {
            drawCircle(
                accent.copy(alpha = (1f - p) * 0.22f),
                radius = size.maxDimension * (0.2f + p * 0.6f),
                center = Offset(size.width / 2, size.height * 0.35f),
            )
        }
        drawContent()
    }
}
