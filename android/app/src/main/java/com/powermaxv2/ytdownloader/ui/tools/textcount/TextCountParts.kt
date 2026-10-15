package com.powermaxv2.ytdownloader.ui.tools.textcount

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.textcount.CaseChanger
import com.powermaxv2.ytdownloader.core.textcount.CaseKind
import com.powermaxv2.ytdownloader.core.textcount.LimitFit
import com.powermaxv2.ytdownloader.core.textcount.SmsFit
import com.powermaxv2.ytdownloader.core.textcount.TextCounter
import com.powermaxv2.ytdownloader.core.textcount.TextStats
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.tools.chat.CountUpText
import com.powermaxv2.ytdownloader.ui.tools.chat.StatBar
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlin.math.roundToInt

// Pieces of the Compteur de texte: the big word count with its reading ring, the tiles that roll their numbers,
// the case chips (each written in its own case), the "ça rentre ?" gauges and the words that come back.

/** Text in the skin's case (Terminal writes in capitals). Never used on the case chips, which show their own case. */
@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** A light band sweeping across the card each time [taps] grows: the text just changed case. */
@Composable
internal fun Modifier.caseShine(taps: Int): Modifier {
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps > 0) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val color = LocalAccent.current.start
    return drawWithContent {
        drawContent()
        val p = sweep.value
        if (p < 1f) {
            val band = size.width * 0.45f
            val x = -band + (size.width + band * 2) * p
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.22f), Color.Transparent),
                    start = Offset(x - band, 0f),
                    end = Offset(x + band, size.height),
                ),
            )
        }
    }
}

// MARK: - Hero

/** "128 mots", counting up, beside a ring that fills with the reading time (one full turn = 5 min). */
@Composable
internal fun HeroCount(stats: TextStats, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                CountUpText(
                    target = stats.words.toDouble(),
                    format = { TextCounter.format((it ?: 0.0).roundToInt()) },
                    style = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, fontFamily = tokens.display, lineHeight = 56.sp),
                    color = Palette.TextPrimary,
                    durationMs = 700,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    skinText(if (stats.words <= 1) "mot" else "mots"),
                    color = Palette.TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            TimeLine(Icons.AutoMirrored.Rounded.MenuBook, "Lecture", TextCounter.duration(stats.readingSeconds))
            Spacer(Modifier.height(6.dp))
            TimeLine(Icons.Rounded.RecordVoiceOver, "À voix haute", TextCounter.duration(stats.speakingSeconds))
        }
        Spacer(Modifier.width(12.dp))
        ReadingRing(stats.readingSeconds / 300f, if (terminal) "${stats.readingSeconds}S" else TextCounter.duration(stats.readingSeconds), accent.start)
    }
}

@Composable
private fun TimeLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(skinText(label), color = Palette.TextSecondary, fontSize = 13.5.sp)
        Spacer(Modifier.width(6.dp))
        RollingText(value, TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold), Palette.TextPrimary)
    }
}

/** A ring filling to [fraction] of a turn (springs as the text grows), its tip breathing, the time in its middle. */
@Composable
private fun ReadingRing(fraction: Float, label: String, color: Color) {
    val skin = LocalSkin.current
    val grown by androidx.compose.animation.core.animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        spring(dampingRatio = 0.7f, stiffness = 120f),
        label = "ringFill",
    )
    val breath = rememberInfiniteTransition(label = "ringBreath")
    val glow by breath.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1_100, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "ringGlow")
    val track = Palette.TextPrimary.copy(alpha = 0.08f)
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = 9.dp.toPx()
            val cap = if (skin == Skin.CLASSIC) StrokeCap.Round else StrokeCap.Butt
            drawArc(track, 0f, 360f, false, style = Stroke(stroke))
            drawArc(color, -90f, 360f * grown, false, style = Stroke(stroke, cap = cap))
            if (grown > 0.01f) {
                val angle = Math.toRadians((-90f + 360f * grown).toDouble())
                val r = size.minDimension / 2f
                val tip = Offset(center.x + r * kotlin.math.cos(angle).toFloat(), center.y + r * kotlin.math.sin(angle).toFloat())
                drawCircle(color.copy(alpha = 0.35f * glow), radius = stroke * 1.1f, center = tip)
            }
        }
        Text(label, color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// MARK: - Tiles

/** Characters, without spaces, sentences, paragraphs: four tiles whose numbers roll when they change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatGrid(stats: TextStats, modifier: Modifier = Modifier) {
    val tiles = listOf(
        "Caractères" to stats.characters,
        "Sans espaces" to stats.charactersNoSpaces,
        "Phrases" to stats.sentences,
        "Paragraphes" to stats.paragraphs,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .toolCard()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        RollingText(TextCounter.format(value), TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display), Palette.TextPrimary)
        Text(skinText(label), color = Palette.TextTertiary, fontSize = 12.5.sp, maxLines = 1)
    }
}

/** A value that slides up when it grows, down when it shrinks. */
@Composable
private fun RollingText(value: String, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState.length > initialState.length || (targetState.length == initialState.length && targetState > initialState)
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { if (up) it else -it } + fadeIn(tween(160))) togetherWith
                (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(120)))
        },
        label = "rollingText",
    ) { shown ->
        Text(shown, style = style, color = color, maxLines = 1)
    }
}

// MARK: - Case

/** One chip per case, each written in that case; the one matching the text is filled, the one just tapped pops. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaseChips(text: String, last: CaseKind?, enabled: Boolean, onCase: (CaseKind) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaseKind.entries.forEach { kind ->
            val current = enabled && (kind == last || (last == null && kind in matching(text)))
            CaseChip(kind, current, enabled) { onCase(kind) }
        }
    }
}

/** The cases the text already is in, most telling first (a sentence in capitals is "MAJUSCULES", not "Chaque Mot"). */
private fun matching(text: String): List<CaseKind> =
    listOf(CaseKind.UPPER, CaseKind.LOWER, CaseKind.SENTENCE, CaseKind.TITLE).firstOrNull { CaseChanger.isAlready(text, it) }?.let(::listOf).orEmpty()

@Composable
private fun CaseChip(kind: CaseKind, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.84f)
            pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
        }
    }
    val fill by animateColorAsState(
        when {
            !selected -> Color.Transparent
            skin == Skin.CLASSIC -> accent.tint(0.22f)
            skin == Skin.PAPER -> Palette.TextPrimary
            else -> accent.start
        },
        tween(200),
        label = "caseFill",
    )
    val ink = when {
        !selected -> if (enabled) Palette.TextPrimary else Palette.TextTertiary
        skin == Skin.PAPER -> Color.White
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.TextPrimary
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .bounceClick(pressedScale = 0.88f, onClick = onClick)
            .height(40.dp)
            .clip(shape)
            .background(if (skin == Skin.PAPER && !selected) Palette.Surface2 else Color.Transparent)
            .background(fill)
            .border(1.dp, if (selected) accent.start.copy(alpha = 0.8f) else Palette.Stroke, shape)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.label, color = ink, fontSize = 14.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

// MARK: - Limits

/** Each platform's limit as a gauge ("reste 40" or "20 de trop" in red), and how many SMS the text takes. */
@Composable
internal fun LimitsCard(fits: List<LimitFit>, sms: SmsFit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SmsRow(sms)
        fits.forEachIndexed { index, fit ->
            val color by animateColorAsState(if (fit.over) Palette.Danger else accent, tween(250), label = "limitColor")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(skinText(fit.limit.label), color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    RollingText(
                        skinText(if (fit.over) "${TextCounter.format(-fit.left)} de trop" else "reste ${TextCounter.format(fit.left)}"),
                        TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                        if (fit.over) Palette.Danger else Palette.TextSecondary,
                    )
                    Text(" / ${TextCounter.format(fit.limit.max)}", color = Palette.TextTertiary, fontSize = 12.5.sp)
                }
                StatBar(fit.fraction, color, delayMs = 80 * index, height = 8.dp)
            }
        }
    }
}

/** "1 SMS" with a little bubble per part (popping in), and why (an emoji switches to the 70-character alphabet). */
@Composable
private fun SmsRow(sms: SmsFit) {
    val accent = LocalAccent.current.start
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(skinText("SMS"), color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                skinText(
                    when {
                        sms.parts == 0 -> "Vide"
                        sms.unicode -> "Emoji ou signe spécial : ${sms.perPart} caractères par SMS"
                        else -> "Alphabet SMS : ${sms.perPart} caractères par SMS"
                    },
                ),
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(sms.parts.coerceAtMost(6)) { SmsBubble(it, accent) }
            if (sms.parts > 6) Text("+${sms.parts - 6}", color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        RollingText(skinText("${sms.parts} SMS"), TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), Palette.TextPrimary)
    }
}

@Composable
private fun SmsBubble(index: Int, color: Color) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
    }
    val shape = if (LocalSkin.current == Skin.CLASSIC) RoundedCornerShape(6.dp, 6.dp, 6.dp, 2.dp) else RoundedCornerShape(2.dp)
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(width = 14.dp, height = 11.dp)
            .clip(shape)
            .background(color),
    )
}

// MARK: - Words

/** The words that come back most (bars growing in cascade) and the longest word. */
@Composable
internal fun WordsCard(stats: TextStats, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val top = stats.topWords.firstOrNull()?.count ?: 1
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.topWords.forEachIndexed { index, word ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                StatBar(word.count.toFloat() / top, accent.copy(alpha = 1f - index * 0.12f), Modifier.weight(1f), delayMs = 120 * index, height = 8.dp)
                Spacer(Modifier.width(10.dp))
                Text("×${word.count}", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (stats.longestWord.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skinText("Mot le plus long"), color = Palette.TextTertiary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Text(
                    "${stats.longestWord} · ${TextCounter.graphemes(stats.longestWord).size}",
                    color = Palette.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
