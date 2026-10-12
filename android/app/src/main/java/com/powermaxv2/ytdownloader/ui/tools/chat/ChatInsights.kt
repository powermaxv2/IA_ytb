package com.powermaxv2.ytdownloader.ui.tools.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatStats
import com.powermaxv2.ytdownloader.core.chat.ReplyStats
import com.powermaxv2.ytdownloader.core.chat.duration
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

// The cards added in 1.14 that need no media: the two precision switches, who answers whom, the records, the
// calendar (one square per day, appearing in a diagonal wave), the evolution (bars growing, then the answer
// time line drawing itself) and everyone's favourite words and shared sites.

private val RecordDay = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

private fun clockDay(clock: Long): String = LocalDate.ofEpochDay(Math.floorDiv(clock, 86_400L)).format(RecordDay)

// MARK: - Precision switches

/** A switch row: title, one line of explanation, and a knob sliding across when [checked] changes. */
@Composable
internal fun ToggleRow(title: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    val track by animateColorAsState(if (checked) accent else Palette.Surface2, tween(220), label = "track")
    val knobX by animateDpAsState(if (checked) 20.dp else 2.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "knob")
    val radius = if (skin == Skin.TERMINAL) 3.dp else 12.dp
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .clickable(role = Role.Switch) { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(radius))
                .background(track)
                .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent, RoundedCornerShape(radius)) else Modifier),
        ) {
            Box(
                Modifier
                    .offset(x = knobX, y = 2.dp)
                    .size(20.dp)
                    .clip(if (skin == Skin.TERMINAL) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (checked) Palette.OnAccent else Palette.TextTertiary),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Palette.TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

// MARK: - Who answers whom

/** The fastest pairs ("Inès → Camille": how fast Camille answers Inès), then who talks to whom the most. */
@Composable
internal fun Duos(replies: ReplyStats, key: Any?) {
    val fastest = replies.fastestPairs.take(5)
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fastest.isEmpty()) {
            Text("Pas assez de réponses entre deux personnes (au moins 3).", color = Palette.TextSecondary, fontSize = 14.sp)
        }
        fastest.forEachIndexed { i, pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .slideIn(250 + i * 110, fromRight = i % 2 == 1, key = key),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(pair.to.name, personColor(pair.to.colorIndex), 30)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${pair.to.name} répond à ${pair.from.name}",
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${count(pair.count)} réponses · médiane ${duration(pair.median)}", color = Palette.TextTertiary, fontSize = 11.5.sp)
                }
                CountUpText(
                    pair.average,
                    { duration(it) },
                    TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
                    if (i == 0) accent else Palette.TextPrimary,
                )
            }
        }
        val talk = replies.talkativePairs.take(3)
        if (talk.isNotEmpty() && replies.rows.size > 2) {
            Text(
                if (terminal) "> CEUX QUI SE PARLENT LE PLUS" else "Ceux qui se parlent le plus",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            val most = max(1, talk.first().second)
            talk.forEachIndexed { i, (two, n) ->
                Column(Modifier.staggeredEntrance(i, key = key, baseDelay = 500), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(personColor(two.first.colorIndex))
                        Box(Modifier.offset(x = (-3).dp)) { Dot(personColor(two.second.colorIndex)) }
                        Spacer(Modifier.width(6.dp))
                        Text("${two.first.name} & ${two.second.name}", color = Palette.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${count(n)} échanges", color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    StatBar(n.toFloat() / most, personColor(two.first.colorIndex), delayMs = 600 + i * 80, height = 8.dp)
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(if (terminal) RoundedCornerShape(4.dp) else CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial(name), color = Color.White, fontSize = (sizeDp * 0.42f).sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
    }
}

// MARK: - Records

/** The records of the discussion: fastest answer, longest wait, longest monologue, longest message, unanswered questions. */
@Composable
internal fun Records(stats: ChatStats, replies: ReplyStats, key: Any?) {
    val asker = replies.rows.filter { it.unanswered > 0 }.maxByOrNull { it.unanswered }
    val rows = listOfNotNull(
        replies.fastest?.let { Triple("⚡", duration(it.seconds), "Réponse la plus rapide · ${it.person} à ${it.to}, ${clockDay(it.clock)}") },
        replies.slowest?.let { Triple("🐢", duration(it.seconds), "Plus longue attente avant une réponse · ${it.person} à ${it.to}") },
        stats.longestMonologue?.let { Triple("🗣️", "${count(it.value)} messages", "Plus long monologue · ${it.person}, ${clockDay(it.clock)}") },
        stats.longestMessage?.let { Triple("📜", "${count(it.value)} mots", "Plus long message · ${it.person} : « ${it.excerpt.orEmpty().take(48)}… »") },
        if (replies.unanswered > 0) {
            Triple("👻", "${count(replies.unanswered)} questions", "Restées sans réponse" + (asker?.let { " · ${it.person.name} en a posé ${count(it.unanswered)}" } ?: ""))
        } else {
            null
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { i, (emoji, value, caption) ->
            Row(Modifier.slideIn(200 + i * 120, fromRight = false, key = key), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 26.sp, modifier = Modifier.popIn(300 + i * 120, key))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(value, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
                    Text(caption, color = Palette.TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// MARK: - Calendar

/** Weeks shown in the calendar: about six months. */
private const val CALENDAR_WEEKS = 26

/** One square per day (columns = weeks, Monday on top), darker when busier; squares appear in a diagonal wave. */
@Composable
internal fun Calendar(stats: ChatStats, key: Any?) {
    val accent = LocalAccent.current.start
    val skin = LocalSkin.current
    val empty = Palette.TextPrimary.copy(alpha = 0.06f)
    val lastDay = Math.floorDiv(stats.last, 86_400L)
    val lastMonday = lastDay - (LocalDate.ofEpochDay(lastDay).dayOfWeek.value - 1)
    val firstDay = Math.floorDiv(stats.first, 86_400L)
    val firstMonday = max(lastMonday - (CALENDAR_WEEKS - 1) * 7L, firstDay - (LocalDate.ofEpochDay(firstDay).dayOfWeek.value - 1))
    val weeks = ((lastMonday - firstMonday) / 7 + 1).toInt()
    val counts = remember(stats) { stats.daily.associate { it.epochDay to it.messages } }
    val most = max(1, counts.values.maxOrNull() ?: 1)
    val wave = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(250)
        wave.animateTo(1f, tween(1600))
    }
    // Six months or more: weeks across, days down (like GitHub). A short discussion: days across, weeks down,
    // so a few weeks still fill the card.
    val across = weeks >= 12
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!across) {
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(it, color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .then(if (across) Modifier.height(120.dp) else Modifier.aspectRatio(7f / weeks)),
        ) {
            val gap = (if (across) 3 else 5).dp.toPx()
            val columns = if (across) weeks else 7
            val rows = if (across) 7 else weeks
            val cell = minOf((size.width - gap * (columns - 1)) / columns, (size.height - gap * (rows - 1)) / rows)
            val left = (size.width - (cell * columns + gap * (columns - 1))) / 2
            val r = if (skin == Skin.CLASSIC) cell * 0.28f else cell * 0.12f
            for (w in 0 until weeks) {
                for (d in 0 until 7) {
                    val day = firstMonday + w * 7L + d
                    if (day < firstDay || day > lastDay) continue
                    val n = counts[day] ?: 0
                    // Diagonal wave: square (w, d) appears when the wave reaches it.
                    val reach = ((wave.value * (weeks + 7)) - (w + d)).coerceIn(0f, 1f)
                    if (reach <= 0f) continue
                    val level = if (n == 0) 0f else 0.22f + 0.78f * (n.toFloat() / most)
                    val c = if (n == 0) empty else accent.copy(alpha = level)
                    val s = cell * (0.4f + 0.6f * reach)
                    val col = if (across) w else d
                    val row = if (across) d else w
                    val x = left + col * (cell + gap) + (cell - s) / 2
                    val y = row * (cell + gap) + (cell - s) / 2
                    drawRoundRect(c.copy(alpha = c.alpha * reach), Offset(x, y), Size(s, s), CornerRadius(r, r))
                }
            }
        }
        val best = stats.busiestDay
        Text(
            listOfNotNull(
                "${count(stats.activeDays)} jours actifs sur ${count(stats.days)}",
                "${count(stats.longestDayStreak)} jours d'affilée au plus",
                best?.let { "record le ${LocalDate.ofEpochDay(it.epochDay).format(RecordDay)} (${count(it.messages)})" },
            ).joinToString(" · "),
            color = Palette.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

// MARK: - Evolution

/** Messages per month (or week) as growing bars, and the average answer time drawn over them as a line. */
@Composable
internal fun Evolution(stats: ChatStats, replies: ReplyStats, key: Any?) {
    val periods = stats.periods.takeLast(12)
    val answers = replies.perPeriod.takeLast(12)
    val accent = LocalAccent.current.start
    val line = personColor(2)
    val skin = LocalSkin.current
    val mostMessages = max(1, periods.maxOfOrNull { it.messages } ?: 1)
    val slowest = answers.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val draw = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(900)
        draw.animateTo(1f, tween(1200))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                periods.forEachIndexed { i, p ->
                    val grow = rememberGrow(p.messages.toFloat() / mostMessages, delayMs = 200 + i * 60, durationMs = 900)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        ColumnBar(grow, if (skin == Skin.CLASSIC) null else accent, skin, dim = true)
                    }
                }
            }
            // The answer time line, over the bars: higher = slower.
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val n = periods.size
                if (n < 2) return@Canvas
                val step = (size.width - 6.dp.toPx() * (n - 1)) / n
                val points = answers.mapIndexedNotNull { i, a ->
                    a?.let { Offset(i * (step + 6.dp.toPx()) + step / 2, size.height * (1f - 0.12f - 0.76f * (it / slowest).toFloat())) }
                }
                if (points.size < 2) return@Canvas
                val path = Path()
                val shown = draw.value * (points.size - 1)
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val t = (shown - (i - 1)).coerceIn(0f, 1f)
                    if (t <= 0f) break
                    val a = points[i - 1]
                    val b = points[i]
                    path.lineTo(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, p ->
                    if (i <= shown) drawCircle(line, radius = 4.dp.toPx(), center = p)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            periods.forEach { p ->
                Text(p.label, color = Palette.TextTertiary, fontSize = 9.5.sp, maxLines = 1, modifier = Modifier.weight(1f), softWrap = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.6f)))
            Text(" messages   ", color = Palette.TextTertiary, fontSize = 11.sp)
            Box(Modifier.size(width = 14.dp, height = 3.dp).background(line))
            Text(" temps de réponse moyen", color = Palette.TextTertiary, fontSize = 11.sp)
        }
        val known = periods.indices.filter { answers.getOrNull(it) != null }
        if (known.size >= 2) {
            val a = answers[known.first()]
            val b = answers[known.last()]
            val faster = b != null && a != null && b < a
            Text(
                "On répond ${if (faster) "plus vite" else "moins vite"} qu'avant : ${duration(a)} ${if (stats.weekly) "la semaine du" else "en"} ${periods[known.first()].label} → ${duration(b)} ${if (stats.weekly) "la semaine du" else "en"} ${periods[known.last()].label}.",
                color = Palette.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.slideIn(1400, fromRight = false, key = key),
            )
        }
    }
}

// MARK: - Words and links

/** Everyone's favourite words as chips popping in, then the sites links come from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Words(stats: ChatStats, key: Any?) {
    val people = stats.people.filter { it.topWords.isNotEmpty() }.take(6)
    val shape = RoundedCornerShape(if (terminal) 3.dp else 14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (people.isEmpty()) Text("Pas assez de messages écrits.", color = Palette.TextSecondary, fontSize = 14.sp)
        people.forEachIndexed { i, p ->
            val color = personColor(p.colorIndex)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.topWords.take(4).forEachIndexed { j, (w, n) ->
                        Text(
                            "$w ×${count(n)}",
                            color = if (j == 0) color else Palette.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = if (j == 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .popIn(200 + i * 120 + j * 60, key)
                                .clip(shape)
                                .background(if (j == 0) color.copy(alpha = 0.16f) else Palette.Surface2)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        if (stats.sites.isNotEmpty()) {
            Text(
                if (terminal) "> LIENS PARTAGÉS" else "Liens partagés",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.sites.forEachIndexed { i, (site, n) ->
                    Text(
                        "${SiteIcons[site] ?: "🔗"} $site ×${count(n)}",
                        color = Palette.TextPrimary,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .popIn(700 + i * 80, key)
                            .clip(shape)
                            .background(Palette.Surface2)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private val SiteIcons = mapOf(
    "YouTube" to "▶️",
    "TikTok" to "🎵",
    "Instagram" to "📸",
    "X" to "✖️",
    "Facebook" to "👍",
    "Snapchat" to "👻",
    "Spotify" to "🎧",
    "Maps" to "📍",
    "Amazon" to "📦",
)
