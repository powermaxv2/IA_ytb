package com.powermaxv2.ytdownloader.ui.tools.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatStats
import com.powermaxv2.ytdownloader.core.chat.Kind
import com.powermaxv2.ytdownloader.core.chat.ReplyRow
import com.powermaxv2.ytdownloader.core.chat.ReplyStats
import com.powermaxv2.ytdownloader.core.chat.WeekdayNames
import com.powermaxv2.ytdownloader.core.chat.duration
import com.powermaxv2.ytdownloader.core.chat.percent
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

// The cards added in 1.11: everyone's file (a dozen numbers each), media and stickers, who starts and ends the
// conversations, and the days of the week. Same motion as the others: cards rise in, numbers count up, bars grow.

private val ShortDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

private fun epochDay(day: Long): String = LocalDate.ofEpochDay(day).format(ShortDay)

private fun plural(n: Int, one: String, many: String): String = "${count(n)} ${if (n > 1) many else one}"

private fun one(x: Double?): String = if (x == null) "—" else String.format(Locale.FRANCE, "%.1f", x)

// MARK: - Everyone's file

/** One card per person (fastest first): their answer times on top, then a grid of numbers. */
@Composable
internal fun Profiles(stats: ChatStats, replies: ReplyStats, key: Any?) {
    val rows = replies.rows.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { i, r -> Profile(stats, r, i, key) }
        if (replies.rows.size > 8) {
            Text("+ ${replies.rows.size - 8} autres personnes", color = Palette.TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Profile(stats: ChatStats, r: ReplyRow, index: Int, key: Any?) {
    val p = r.person
    val color = personColor(p.colorIndex)
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = key, baseDelay = 200)
            .clip(shape)
            .background(if (skin == Skin.PAPER) Palette.TextPrimary.copy(alpha = 0.04f) else Palette.Surface2)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, color.copy(alpha = 0.6f), shape) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .popIn(250 + index * 90, key)
                    .size(40.dp)
                    .clip(if (skin == Skin.TERMINAL) RoundedCornerShape(5.dp) else CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial(p.name), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "répond en ${duration(r.average)} en moyenne",
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            p.favoriteEmoji?.let { Text(it, fontSize = 24.sp, modifier = Modifier.bob(index, amplitude = 2.dp, periodMs = 2200)) }
        }
        val facts = listOf(
            Fact("médiane", r.median, { duration(it) }),
            Fact("plus lente", r.slowest?.toDouble(), { duration(it) }),
            Fact("en < 1 min", r.under1min, { percent(it) }),
            Fact("messages", p.messages.toDouble(), { count(it) }),
            Fact("mots / message", p.wordsPerMessage, { one(it) }),
            Fact("réponses", r.count.toDouble(), { count(it) }),
            Fact("conversations lancées", r.starts.toDouble(), { count(it) }),
            Fact("derniers mots", r.lastWords.toDouble(), { count(it) }),
            Fact("rafale max", p.longestBurst.toDouble(), { "${count(it)} d'affilée" }),
            if (stats.stickersKnown) Fact("stickers", p.stickers.toDouble(), { count(it) }) else Fact("médias", p.media.toDouble(), { count(it) }),
            Fact("emojis", p.emojis.toDouble(), { count(it) }),
            Fact("entre 0 h et 5 h", p.night.toDouble(), { count(it) }),
        )
        facts.chunked(3).forEachIndexed { line, three ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                three.forEachIndexed { j, f -> FactCell(f, Modifier.weight(1f), highlight = line == 0 && j == 0, color = color) }
            }
        }
        val extra = listOfNotNull(
            if (stats.stickersKnown && p.media > p.stickers) plural(p.media, "média", "médias") else null,
            if (p.count(Kind.VOICE) > 0) plural(p.count(Kind.VOICE), "vocal", "vocaux") else null,
            if (p.deleted > 0) plural(p.deleted, "supprimé", "supprimés") else null,
            if (p.edited > 0) plural(p.edited, "modifié", "modifiés") else null,
            if (p.links > 0) plural(p.links, "lien", "liens") else null,
            "actif ${count(p.activeDays)} jours sur ${count(stats.days)}",
        )
        Text(extra.joinToString(" · "), color = Palette.TextTertiary, fontSize = 11.5.sp, lineHeight = 15.sp)
    }
}

private class Fact(val label: String, val value: Double?, val format: (Double?) -> String)

@Composable
private fun FactCell(f: Fact, modifier: Modifier, highlight: Boolean, color: Color) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        CountUpText(
            f.value,
            f.format,
            TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
            if (highlight) color else Palette.TextPrimary,
            durationMs = 1200,
        )
        Text(f.label, color = Palette.TextTertiary, fontSize = 10.5.sp, lineHeight = 13.sp, maxLines = 2)
    }
}

// MARK: - Media and stickers

private val KindIcons = mapOf(
    Kind.STICKER to "✨",
    Kind.PHOTO to "📷",
    Kind.VIDEO to "🎬",
    Kind.VOICE to "🎙️",
    Kind.AUDIO to "🎵",
    Kind.GIF to "🎞️",
    Kind.DOCUMENT to "📄",
    Kind.CONTACT to "👤",
    Kind.LOCATION to "📍",
    Kind.POLL to "📊",
    Kind.CALL to "📞",
    Kind.MEDIA to "🖼️",
    Kind.DELETED to "🗑️",
)

/** One tile per kind sent (stickers first), popping in; then everyone's stickers as bars. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaCard(stats: ChatStats, key: Any?) {
    val kinds = KindIcons.keys.filter { stats.count(it) > 0 }
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (kinds.isEmpty()) {
            Text("Aucun média, sticker ou appel dans cette discussion.", color = Palette.TextSecondary, fontSize = 14.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kinds.forEachIndexed { i, k ->
                    val first = i == 0 && k == Kind.STICKER
                    Column(
                        Modifier
                            .popIn(120 + i * 60, key)
                            .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 14.dp))
                            .background(if (first) accent.tint(if (skin == Skin.PAPER) 0.14f else 0.2f) else Palette.Surface2)
                            .then(if (first && skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, RoundedCornerShape(4.dp)) else Modifier)
                            .widthIn(min = 92.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(KindIcons.getValue(k), fontSize = 22.sp, modifier = if (first) Modifier.bob(0, amplitude = 3.dp, periodMs = 1600) else Modifier)
                        CountUpText(
                            stats.count(k).toDouble(),
                            { count(it) },
                            TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
                            if (first) accent.start else Palette.TextPrimary,
                            durationMs = 1100,
                        )
                        Text(k.label, color = Palette.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        if (stats.stickersKnown) {
            val list = stats.people.sortedByDescending { it.stickers }.take(8)
            val most = max(1, list.firstOrNull()?.stickers ?: 1)
            if (list.any { it.stickers > 0 }) {
                Text(if (terminal) "> STICKERS PAR PERSONNE" else "Stickers par personne", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                list.forEachIndexed { i, p ->
                    PersonBar(p.name, p.colorIndex, p.stickers, most, "${count(p.stickers)} · ${percent(p.stickers.toDouble() / max(1, stats.stickers))}", i, key)
                }
            }
        } else {
            Text(
                "Cet export Android « sans médias » écrit « <Médias omis> » pour les photos, vocaux et stickers sans les distinguer. " +
                    "Pour compter les stickers, exporte la discussion « avec médias » (ou depuis un iPhone).",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/** A name, its number on the right and a bar growing to [value] / [max]. */
@Composable
private fun PersonBar(name: String, colorIndex: Int, value: Int, max: Int, label: String, index: Int, key: Any?) {
    val color = personColor(colorIndex)
    Column(Modifier.staggeredEntrance(index, key = key, baseDelay = 260), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(8.dp))
            Text(name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(label, color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        StatBar(value.toFloat() / max, color, delayMs = 300 + index * 60, height = 8.dp)
    }
}

// MARK: - Conversations

/** How many conversations, who opens them, who has the last word, and the records of the discussion. */
@Composable
internal fun Conversations(stats: ChatStats, replies: ReplyStats, key: Any?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniKpi("conversations", replies.conversations.toDouble(), { count(it) }, Modifier.weight(1f), 0, key)
            MiniKpi("messages chacune", replies.messagesPerConversation, { count(it) }, Modifier.weight(1f), 1, key)
            MiniKpi("plus long silence", stats.longestSilence.toDouble(), { duration(it) }, Modifier.weight(1f), 2, key)
        }
        val people = replies.rows.sortedByDescending { it.starts }.take(6)
        val mostStarts = max(1, people.firstOrNull()?.starts ?: 1)
        Text(if (terminal) "> QUI LANCE LA CONVERSATION" else "Qui lance la conversation", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        people.forEachIndexed { i, r ->
            PersonBar(r.person.name, r.person.colorIndex, r.starts, mostStarts, "${count(r.starts)} · ${percent(r.starts.toDouble() / replies.conversations)}", i, key)
        }
        val closers = replies.rows.sortedByDescending { it.lastWords }.take(6)
        val mostLast = max(1, closers.firstOrNull()?.lastWords ?: 1)
        Text(if (terminal) "> QUI A LE DERNIER MOT" else "Qui a le dernier mot", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        closers.forEachIndexed { i, r ->
            PersonBar(r.person.name, r.person.colorIndex, r.lastWords, mostLast, "${count(r.lastWords)} · ${percent(r.lastWords.toDouble() / replies.conversations)}", i + 3, key)
        }
        val records = listOfNotNull(
            stats.busiestDay?.let { "📅 Jour record : ${epochDay(it.epochDay)} ${LocalDate.ofEpochDay(it.epochDay).year}, ${count(it.messages)} messages" },
            "🔥 Plus longue série : ${count(stats.longestDayStreak)} jours d'affilée avec au moins un message",
            "💬 ${count(stats.perActiveDay)} messages par jour actif, ${count(stats.words)} mots en tout",
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            records.forEachIndexed { i, line ->
                Text(line, color = Palette.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.slideIn(400 + i * 120, fromRight = false, key = key))
            }
        }
    }
}

@Composable
private fun MiniKpi(label: String, value: Double?, format: (Double?) -> String, modifier: Modifier, index: Int, key: Any?) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 12.dp)
    Column(
        modifier
            .fillMaxHeight()
            .popIn(150 + index * 90, key)
            .clip(shape)
            .background(Palette.Surface2)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, Palette.Stroke, shape) else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CountUpText(
            value,
            format,
            TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
            LocalAccent.current.start,
        )
        Text(label, color = Palette.TextTertiary, fontSize = 10.5.sp, lineHeight = 13.sp, maxLines = 2)
    }
}

// MARK: - Days of the week

/** Messages per day of the week, the busiest in full colour; under the hours. */
@Composable
internal fun Weekdays(stats: ChatStats) {
    val most = max(1, stats.weekdays.maxOrNull() ?: 1)
    val peak = stats.peakWeekday
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (terminal) "> JOURS DE LA SEMAINE" else "Jours de la semaine",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.weekdays.forEachIndexed { d, n ->
                val grow = rememberGrow(n.toFloat() / most, delayMs = 300 + d * 60, durationMs = 1000)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    ColumnBar(grow, if (skin == Skin.CLASSIC && d == peak) null else accent, skin, dim = d != peak)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekdayNames.forEachIndexed { d, name ->
                Text(
                    name.take(3),
                    color = if (d == peak) accent else Palette.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (d == peak) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
