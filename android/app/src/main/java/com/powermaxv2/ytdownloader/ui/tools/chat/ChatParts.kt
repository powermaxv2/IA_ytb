package com.powermaxv2.ytdownloader.ui.tools.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.Buckets
import com.powermaxv2.ytdownloader.core.chat.ChatStats
import com.powermaxv2.ytdownloader.core.chat.Gap
import com.powermaxv2.ytdownloader.core.chat.ReplyStats
import com.powermaxv2.ytdownloader.core.chat.duration
import com.powermaxv2.ytdownloader.core.chat.percent
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// The cards of "Vu, répondu", drawn natively in the three skins: the animated chat on the empty screen, then the
// source bar, the four big numbers, the podium, the race, the volume donut, the delays, the hours, the refrains
// and the emojis.

private val Numbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE)

internal fun count(n: Number?): String = if (n == null) "—" else Numbers.format(n.toDouble().roundToInt())

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

internal fun day(epochSeconds: Long): String = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC).format(DayFormat)

internal fun initial(name: String): String =
    name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

internal val terminal: Boolean @Composable get() = LocalSkin.current == Skin.TERMINAL

/** Card title in the skin's display font (uppercase on Terminal). */
@Composable
private fun CardTitle(text: String, sub: String? = null) {
    val term = terminal
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (term) text.uppercase() else text,
            color = Palette.TextPrimary,
            fontSize = if (term) 17.sp else 22.sp,
            lineHeight = if (term) 22.sp else 26.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = if (term) 0.sp else (-0.4).sp,
        )
        sub?.let {
            Text(
                if (term) "# $it" else it,
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** A result card: eyebrow, title, then [content]; it rises in after the cards above it. */
@Composable
internal fun StatCard(eyebrow: String, title: String, index: Int, key: Any?, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = key, baseDelay = 120)
            .toolCard()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockLabel(eyebrow)
            CardTitle(title, sub)
        }
        content()
    }
}

/** Little coloured dot of a person. */
@Composable
internal fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(if (terminal) RoundedCornerShape(1.dp) else CircleShape)
            .background(color),
    )
}

// MARK: - Empty screen: the animated chat

private class DemoLine(
    val who: String,
    val color: Color,
    val text: String,
    val time: String,
    val me: Boolean = false,
    val chip: String? = null,
    val typing: Boolean = false,
)

private val DemoScript = listOf(
    DemoLine("Hugo", personColor(1), "Quelqu'un est dispo ce soir ?", "21:02"),
    DemoLine("", Color.Unspecified, "", "", typing = true),
    DemoLine("Inès", personColor(0), "Moi !! 🙋‍♀️", "21:02", me = true, chip = "répondu en 18 s"),
    DemoLine("Camille", personColor(2), "Grave, 21h30 ?", "21:06", chip = "répondu en 4 min"),
    DemoLine("", Color.Unspecified, "", "", typing = true),
    DemoLine("Théo", personColor(3), "Ok", "21:41", chip = "répondu en 35 min"),
)

private const val DEMO_STEP_MS = 1500

/**
 * A little phone where a group chat plays by itself, each answer stamped with its delay, then clears and starts
 * over. Driven by an infinite transition: one step every 1.5 s.
 */
@Composable
internal fun ChatDemo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chatDemo")
    val cycle = DemoScript.size + 1
    val t by transition.animateFloat(
        0f,
        cycle.toFloat(),
        infiniteRepeatable(tween(cycle * DEMO_STEP_MS, easing = LinearEasing)),
        label = "chatDemoStep",
    )
    val step = t.toInt() // 0..size: the last step clears the phone
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = if (skin == Skin.CLASSIC) -1.2f else 0f }
            .toolCard()
            .heightIn(min = 320.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Speaker slot of the phone.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = 56.dp, height = 5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Palette.Stroke),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
        DemoScript.forEachIndexed { i, line ->
            val shown = if (line.typing) step == i else step in i until DemoScript.size
            line.chip?.let { chip ->
                AnimatedVisibility(
                    visible = shown,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.6f),
                    exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 },
                ) {
                    Text(
                        if (skin == Skin.TERMINAL) "[⏱ ${chip.uppercase()}]" else "⏱ $chip",
                        color = LocalAccent.current.start,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.chipRadius))
                            .background(LocalAccent.current.tint(0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(if (line.me) Alignment.End else Alignment.Start),
                enter = fadeIn(tween(160)) + scaleIn(
                    spring(dampingRatio = 0.55f, stiffness = 380f),
                    initialScale = 0.6f,
                    transformOrigin = TransformOrigin(if (line.me) 1f else 0f, 1f),
                ),
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 },
            ) {
                Bubble(line)
            }
        }
        }
    }
}

@Composable
private fun Bubble(line: DemoLine) {
    val skin = LocalSkin.current
    val radius = if (skin == Skin.TERMINAL) 4.dp else 14.dp
    val shape = RoundedCornerShape(radius)
    val fill = when {
        line.me && skin == Skin.TERMINAL -> LocalAccent.current.start.copy(alpha = 0.14f)
        line.me -> LocalAccent.current.tint(if (skin == Skin.PAPER) 0.16f else 0.22f)
        else -> Palette.Surface2
    }
    Column(
        Modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(fill)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, Palette.Stroke, shape) else Modifier)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
    ) {
        if (line.typing) {
            TypingDots(Palette.TextSecondary, Modifier.size(width = 30.dp, height = 12.dp))
            return@Column
        }
        Text(line.who, color = line.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(line.text, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 18.sp)
        Text(
            line.time + if (line.me) " ✓✓" else "",
            color = Palette.TextTertiary,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

// MARK: - Source bar

/** Which discussion, its dates, and the "new conversation after" threshold. */
@Composable
internal fun SourceCard(stats: ChatStats, name: String, sample: Boolean, gap: Gap, onGap: (Gap) -> Unit, key: Any?) {
    val accent = LocalAccent.current.start
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, key = key, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pill = if (sample) "Exemple" else "Ta discussion"
            val pillColor = if (sample) Gold else accent
            Text(
                pill.uppercase(),
                color = pillColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .popIn(200, key)
                    .clip(RoundedCornerShape(LocalTokens.current.chipRadius))
                    .background(pillColor.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${day(stats.first)} → ${day(stats.last)} · ${stats.days} jours" +
                if (stats.activeDays < stats.days) ", dont ${stats.activeDays} actifs" else "",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
        )
        if (stats.toTheMinute) {
            Text(
                "Export à la minute près : une réponse dans la même minute compte pour 20 s (en moyenne), pas 0 s.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        BlockLabel("Nouvelle conversation après")
        OptionChips(Gap.entries, gap, { it.label }, onGap)
    }
}

// MARK: - The six big numbers

@Composable
internal fun KpiGrid(stats: ChatStats, replies: ReplyStats, key: Any?) {
    val top = stats.people.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(
                "Temps de réponse moyen",
                replies.average,
                { duration(it) },
                "médiane ${duration(replies.median)}",
                index = 0,
                key = key,
                highlight = true,
                modifier = Modifier.weight(1f),
            )
            Kpi("Messages", stats.messages.size.toDouble(), { count(it) }, "≈ ${count(stats.perDay)} par jour", 1, key, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(
                "Participants",
                stats.people.size.toDouble(),
                { count(it) },
                top?.let { "${it.name} parle le plus" } ?: "—",
                2,
                key,
                modifier = Modifier.weight(1f),
            )
            Kpi(
                "Réponses mesurées",
                replies.all.size.toDouble(),
                { count(it) },
                replies.under5min?.let { "${percent(it)} en moins de 5 min" } ?: "—",
                3,
                key,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(
                "Conversations",
                replies.conversations.toDouble(),
                { count(it) },
                replies.starter?.let { "${it.person.name} en lance le plus" } ?: "—",
                4,
                key,
                modifier = Modifier.weight(1f),
            )
            Kpi(
                "Stickers",
                if (stats.stickersKnown) stats.stickers.toDouble() else null,
                { count(it) },
                if (stats.stickersKnown) {
                    stats.people.filter { it.stickers > 0 }.maxByOrNull { it.stickers }?.let { "${it.name} en envoie le plus" } ?: "aucun envoyé"
                } else {
                    "non distingués dans cet export"
                },
                5,
                key,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Kpi(
    label: String,
    value: Double?,
    format: (Double?) -> String,
    sub: String,
    index: Int,
    key: Any?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.cardRadius)
    val base = modifier
        .fillMaxHeight()
        .staggeredEntrance(index, key = key, baseDelay = 100)
    val box = when {
        highlight && skin == Skin.CLASSIC -> base.clip(shape).background(accent.brush)
        highlight && skin == Skin.PAPER -> base.clip(shape).background(accent.start)
        highlight -> base.clip(shape).background(accent.start.copy(alpha = 0.10f)).border(1.dp, accent.start, shape)
        else -> base.toolCard()
    }
    val onFill = highlight && skin != Skin.TERMINAL
    val ink = if (onFill) Palette.OnAccent else Palette.TextPrimary
    val soft = if (onFill) Palette.OnAccent.copy(alpha = 0.8f) else Palette.TextSecondary
    Column(box.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            color = if (highlight && skin == Skin.TERMINAL) accent.start else soft,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 2,
        )
        CountUpText(
            value,
            format,
            TextStyle(
                fontSize = if (skin == Skin.TERMINAL) 24.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = tokens.display,
                letterSpacing = (-0.5).sp,
            ),
            if (highlight && skin == Skin.TERMINAL) accent.start else ink,
        )
        Text(sub, color = soft, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - Podium

private val PodiumHeights = listOf(110.dp, 150.dp, 80.dp)

/** The three fastest (average, at least 3 answers): 2nd, 1st, 3rd; blocks grow, avatars pop, the crown drops. */
@Composable
internal fun Podium(replies: ReplyStats, key: Any?) {
    val top = replies.podium
    if (top.isEmpty()) {
        Text("Pas assez de réponses pour un podium.", color = Palette.TextSecondary, fontSize = 14.sp)
        return
    }
    val order = listOf(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    val delays = listOf(250, 50, 450)
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEachIndexed { i, row ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (row == null) return@Column
                val rank = top.indexOf(row) + 1
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .popIn(delays[i] + 500, key)
                            .size(54.dp)
                            .clip(if (skin == Skin.TERMINAL) RoundedCornerShape(6.dp) else CircleShape)
                            .background(personColor(row.person.colorIndex)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initial(row.person.name),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = LocalTokens.current.display,
                        )
                    }
                    if (rank == 1) {
                        Crown(
                            Modifier
                                .offset(y = (-38).dp)
                                .size(width = 34.dp, height = 23.dp),
                        )
                        Sparks(Modifier.size(54.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    row.person.name,
                    color = Palette.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(duration(row.average), color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val grow = rememberGrow(1f, delays[i], durationMs = 900)
                val blockShape = if (skin == Skin.TERMINAL) RoundedCornerShape(2.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                val fill = when (skin) {
                    Skin.CLASSIC -> if (rank == 1) accent.tint(0.32f) else accent.tint(0.16f)
                    Skin.PAPER -> if (rank == 1) accent.start else Palette.TextPrimary.copy(alpha = 0.08f)
                    Skin.TERMINAL -> accent.start.copy(alpha = if (rank == 1) 0.22f else 0.08f)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PodiumHeights[i])
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = grow
                        }
                        .clip(blockShape)
                        .background(fill)
                        .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, blockShape) else Modifier)
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        if (skin == Skin.TERMINAL) "#$rank" else "$rank",
                        color = if (skin == Skin.PAPER && rank == 1) Palette.OnAccent else accent.start,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = LocalTokens.current.display,
                    )
                }
            }
        }
    }
}

// MARK: - Race

/** Everyone's average answer time as a bar (short = fast), the median and slowest answer below. */
@Composable
internal fun Race(replies: ReplyStats, key: Any?) {
    val rows = replies.rows.take(10)
    val maxAverage = rows.mapNotNull { it.average }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { i, r ->
            val color = personColor(r.person.colorIndex)
            Column(Modifier.staggeredEntrance(i, key = key, baseDelay = 200), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Palette.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Dot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        r.person.name,
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(duration(r.average), color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("médiane ${duration(r.median)}", color = Palette.TextTertiary, fontSize = 11.sp)
                    }
                }
                val fraction = r.average?.let { max(0.02, it / maxAverage).toFloat() } ?: 0f
                StatBar(fraction, color, Modifier.padding(start = 22.dp), delayMs = 250 + i * 70)
                Text(
                    "${count(r.count)} réponses · ${percent(r.under5min)} en < 5 min · plus lente ${duration(r.slowest)}",
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 22.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (replies.rows.size > 10) {
            Text("+ ${replies.rows.size - 10} autres personnes", color = Palette.TextTertiary, fontSize = 12.sp)
        }
    }
}

// MARK: - Volume

/** Donut of the messages per person, drawn around the total, then everyone's bar. */
@Composable
internal fun Volume(stats: ChatStats, key: Any?) {
    val total = stats.messages.size.toFloat()
    val list = stats.people.take(12)
    val rest = stats.people.drop(12).sumOf { it.messages }
    val skin = LocalSkin.current
    val restColor = Palette.Stroke
    val track = Palette.TextPrimary.copy(alpha = 0.06f)
    val sweep = rememberGrow(1f, 200, durationMs = 1400)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val stroke = (if (skin == Skin.PAPER) 26.dp else 22.dp).toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                val segments = list.map { personColor(it.colorIndex) to it.messages } +
                    (if (rest > 0) listOf(restColor to rest) else emptyList())
                var start = -90f
                val limit = 360f * sweep
                for ((color, n) in segments) {
                    val full = 360f * n / total
                    val visible = (limit - (start + 90f)).coerceIn(0f, full)
                    if (visible > 0f) {
                        val gap = if (segments.size > 1 && skin != Skin.TERMINAL) 1.5f else 0f
                        drawArc(
                            color,
                            start,
                            (visible - gap).coerceAtLeast(0.1f),
                            false,
                            topLeft,
                            arc,
                            style = Stroke(stroke, cap = StrokeCap.Butt),
                        )
                    }
                    start += full
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CountUpText(
                    total.toDouble(),
                    { count(it) },
                    TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
                    Palette.TextPrimary,
                )
                Text("messages", color = Palette.TextSecondary, fontSize = 12.sp)
            }
        }
        val maxMessages = list.firstOrNull()?.messages?.coerceAtLeast(1) ?: 1
        list.forEachIndexed { i, p ->
            val color = personColor(p.colorIndex)
            Column(Modifier.staggeredEntrance(i, key = key, baseDelay = 260), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        p.name,
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CountUpText(
                        p.messages.toDouble(),
                        { "${count(it)} · ${percent(p.messages / total.toDouble())}" },
                        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        Palette.TextPrimary,
                        durationMs = 1200,
                    )
                }
                StatBar(p.messages.toFloat() / maxMessages, color, delayMs = 300 + i * 60, height = 8.dp)
            }
        }
        if (rest > 0) {
            Text(
                "+ ${stats.people.size - 12} autres personnes : ${count(rest)} messages",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

// MARK: - Columns (delays, hours)

/** Vertical bars growing from the bottom, with the counts rolling above them. */
@Composable
internal fun Histogram(replies: ReplyStats) {
    val counts = replies.histogram
    val maxCount = max(1, counts.maxOrNull() ?: 1)
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(190.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { i, n ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                CountUpText(n.toDouble(), { count(it) }, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), Palette.TextPrimary, durationMs = 1100)
                Spacer(Modifier.height(4.dp))
                val h = rememberGrow(n.toFloat() / maxCount, delayMs = i * 80)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ColumnBar(h, if (skin == Skin.CLASSIC) null else accent.start, skin)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    Buckets[i].label,
                    color = Palette.TextTertiary,
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** One bar of a column chart, [fraction] of the height; Classic: the accent gradient. */
@Composable
internal fun ColumnBar(fraction: Float, color: Color?, skin: Skin, dim: Boolean = false) {
    val accent = LocalAccent.current
    val shape = when (skin) {
        Skin.CLASSIC -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
        Skin.PAPER -> RoundedCornerShape(2.dp)
        Skin.TERMINAL -> RoundedCornerShape(0.dp)
    }
    val m = Modifier
        .fillMaxWidth()
        .fillMaxHeight(fraction.coerceIn(0f, 1f))
        .clip(shape)
    val alpha = if (dim) 0.45f else 1f
    Box(
        when {
            color == null -> m.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(accent.end.copy(alpha = alpha), accent.start.copy(alpha = alpha))))
            skin == Skin.TERMINAL -> m.background(color.copy(alpha = if (dim) 0.18f else 0.35f)).border(1.dp, color.copy(alpha = alpha), shape)
            else -> m.background(color.copy(alpha = alpha))
        },
    )
}

/** Messages per hour of the day, the peak hour in full colour. */
@Composable
internal fun Hours(stats: ChatStats) {
    val maxCount = max(1, stats.hours.maxOrNull() ?: 1)
    val peak = stats.peakHour
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.hours.forEachIndexed { h, n ->
                val grow = rememberGrow(n.toFloat() / maxCount, delayMs = h * 25, durationMs = 1000)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    ColumnBar(grow, if (skin == Skin.CLASSIC && h == peak) null else accent, skin, dim = h != peak)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (h in 0 until 24 step 6) {
                Text("${h}h", color = Palette.TextTertiary, fontSize = 10.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Refrains and emojis

/** The most repeated messages as chat bubbles, alternating sides, the first one big. */
@Composable
internal fun Refrains(stats: ChatStats, key: Any?) {
    if (stats.refrains.isEmpty()) {
        Text("Aucun message n'apparaît plus d'une fois.", color = Palette.TextSecondary, fontSize = 14.sp)
        return
    }
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.refrains.forEachIndexed { i, r ->
            val right = i % 2 == 1
            val radius = if (skin == Skin.TERMINAL) 4.dp else 16.dp
            val shape = RoundedCornerShape(radius)
            Row(
                Modifier
                    .fillMaxWidth()
                    .slideIn(100 + i * 80, fromRight = right, key = key),
                horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val times = "× ${count(r.count)}"
                val timesColor = if (i == 0) accent.start else Palette.TextTertiary
                if (right) {
                    Text(times, color = timesColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    r.label,
                    color = Palette.TextPrimary,
                    fontSize = if (i == 0) 22.sp else 15.sp,
                    lineHeight = if (i == 0) 27.sp else 19.sp,
                    fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Normal,
                    fontFamily = if (i == 0) LocalTokens.current.display else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(shape)
                        .background(if (right) accent.tint(if (skin == Skin.PAPER) 0.14f else 0.2f) else Palette.Surface2)
                        .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, Palette.Stroke, shape) else Modifier)
                        .padding(horizontal = if (i == 0) 18.dp else 14.dp, vertical = if (i == 0) 12.dp else 9.dp),
                )
                if (!right) {
                    Spacer(Modifier.width(10.dp))
                    Text(times, color = timesColor, fontSize = if (i == 0) 16.sp else 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Favourite emojis, popping in one after the other; the favourite floats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Emojis(stats: ChatStats, key: Any?) {
    if (stats.emojis.isEmpty()) {
        Text("Aucun emoji dans cette discussion.", color = Palette.TextSecondary, fontSize = 14.sp)
        return
    }
    val skin = LocalSkin.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.emojis.forEachIndexed { i, (emoji, n) ->
            Column(
                Modifier
                    .popIn(100 + i * 50, key)
                    .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 14.dp))
                    .background(Palette.Surface2)
                    .widthIn(min = 62.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(emoji, fontSize = 28.sp, modifier = if (i == 0) Modifier.bob(0, amplitude = 3.dp, periodMs = 1800) else Modifier)
                Text(count(n), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
