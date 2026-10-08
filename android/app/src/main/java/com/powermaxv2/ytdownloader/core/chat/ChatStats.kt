package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// What "Vu, répondu" computes from a discussion: messages, words, media and stickers per person, activity hours
// and days, repeated messages and emojis ([ChatStats.of]), then, for a "new conversation" threshold
// ([ChatStats.replies]), the response times and who starts or ends the conversations.
// A response = the first message someone sends after someone else's, in the same conversation.

/** One person of the discussion; [colorIndex] picks their colour (rank by number of messages). */
data class Person(
    val name: String,
    val messages: Int,
    val words: Int,
    val media: Int,
    val colorIndex: Int,
    /** Messages of each [Kind], by ordinal. */
    val kinds: IntArray = IntArray(Kind.entries.size),
    val edited: Int = 0,
    val emojis: Int = 0,
    val links: Int = 0,
    /** Messages sent between midnight and 5 a.m. */
    val night: Int = 0,
    /** Most messages sent in a row before someone else writes. */
    val longestBurst: Int = 0,
    /** Days on which they wrote at least once. */
    val activeDays: Int = 0,
    val favoriteEmoji: String? = null,
) {
    fun count(kind: Kind): Int = kinds[kind.ordinal]
    val stickers: Int get() = count(Kind.STICKER)
    val deleted: Int get() = count(Kind.DELETED)
    val texts: Int get() = count(Kind.TEXT)

    /** Words per written message (media left out); null when they wrote nothing. */
    val wordsPerMessage: Double? get() = if (texts == 0) null else words.toDouble() / texts

    override fun equals(other: Any?): Boolean = other is Person && other.name == name && other.messages == messages && other.colorIndex == colorIndex
    override fun hashCode(): Int = name.hashCode() * 31 + messages
}

/** A message written several times, as first written ([label]), and how many times. */
data class Refrain(val label: String, val count: Int)

/** A day of the discussion ([epochDay] as in [LocalDate.toEpochDay]) and its number of messages. */
data class DayCount(val epochDay: Long, val messages: Int)

data class ChatStats(
    val messages: List<ChatMessage>,
    /** Most talkative first. */
    val people: List<Person>,
    /** Messages per hour of the day, 0..23. */
    val hours: IntArray,
    val refrains: List<Refrain>,
    val emojis: List<Pair<String, Int>>,
    /** Calendar days from the first message to the last, both included. */
    val days: Int,
    val first: Long,
    val last: Long,
    /** Messages per day of the week, Monday first. */
    val weekdays: IntArray = IntArray(7),
    /** Days with at least one message. */
    val activeDays: Int = days,
    val busiestDay: DayCount? = null,
    /** Most days in a row with at least one message. */
    val longestDayStreak: Int = 0,
    /** Longest time without any message, and when it started. */
    val longestSilence: Long = 0,
    val longestSilenceFrom: Long = first,
    /** The export gives minutes only (Android): two messages of the same minute are 0 s apart on paper. */
    val toTheMinute: Boolean = false,
) {
    fun count(kind: Kind): Int = people.sumOf { it.count(kind) }
    val mediaCount: Int get() = people.sumOf { it.media }
    val stickers: Int get() = count(Kind.STICKER)
    val words: Int get() = people.sumOf { it.words }
    val edited: Int get() = people.sumOf { it.edited }
    val perDay: Int get() = (messages.size.toDouble() / days).roundToInt()
    val perActiveDay: Int get() = (messages.size.toDouble() / max(1, activeDays)).roundToInt()
    val peakHour: Int get() = hours.indices.maxByOrNull { hours[it] } ?: 0
    val peakWeekday: Int get() = weekdays.indices.maxByOrNull { weekdays[it] } ?: 0

    /**
     * Stickers can be told apart: the export has media file names (with media) or iPhone notices. An Android
     * export without media writes "<Médias omis>" for photos, stickers and vocals alike.
     */
    val stickersKnown: Boolean get() = people.any { p -> Kind.entries.any { it.isMedia && it != Kind.MEDIA && p.count(it) > 0 } }

    fun replies(gap: Gap): ReplyStats = replies(this, gap.seconds)

    companion object {
        fun of(messages: List<ChatMessage>): ChatStats {
            require(messages.isNotEmpty())
            class Acc(val name: String) {
                var msgs = 0
                var words = 0
                var media = 0
                val kinds = IntArray(Kind.entries.size)
                var edited = 0
                var emojis = 0
                var links = 0
                var night = 0
                var burst = 0
                var longestBurst = 0
                val days = HashSet<Long>()
                val emojiCounts = LinkedHashMap<String, Int>()
            }
            val people = LinkedHashMap<String, Acc>()
            val hours = IntArray(24)
            val weekdays = IntArray(7)
            val perDay = LinkedHashMap<Long, Int>()
            val counts = LinkedHashMap<String, IntArray>()
            val labels = HashMap<String, String>()
            val emojis = LinkedHashMap<String, Int>()
            var previous: String? = null
            for (m in messages) {
                val p = people.getOrPut(m.author) { Acc(m.author) }
                p.msgs++
                p.kinds[m.kind.ordinal]++
                if (m.edited) p.edited++
                p.burst = if (previous == m.author) p.burst + 1 else 1
                p.longestBurst = max(p.longestBurst, p.burst)
                previous = m.author
                val hour = m.hour.coerceIn(0, 23)
                hours[hour]++
                if (hour < 5) p.night++
                val date = LocalDate.ofEpochDay(Math.floorDiv(m.time, 86_400L))
                weekdays[date.dayOfWeek.value - 1]++
                perDay[date.toEpochDay()] = (perDay[date.toEpochDay()] ?: 0) + 1
                p.days.add(date.toEpochDay())
                if (m.kind != Kind.TEXT) {
                    if (m.kind.isMedia) p.media++
                    continue
                }
                p.words += m.text.split(WHITESPACE).count { w -> w.any { it.isLetterOrDigit() } }
                p.links += LINK.findAll(m.text).count()
                val key = m.text.lowercase().replace(WHITESPACE, " ").replace(TRAILING, "").trim()
                if (key.isNotEmpty()) {
                    val c = counts.getOrPut(key) { IntArray(1) }
                    if (c[0] == 0) labels[key] = m.text.replace(TRAILING, "").ifEmpty { m.text }
                    c[0]++
                }
                for (e in emojisOf(m.text)) {
                    emojis[e] = (emojis[e] ?: 0) + 1
                    p.emojiCounts[e] = (p.emojiCounts[e] ?: 0) + 1
                    p.emojis++
                }
            }
            val list = people.values
                .sortedByDescending { it.msgs } // stable: first to speak first on ties
                .mapIndexed { i, a ->
                    Person(
                        a.name, a.msgs, a.words, a.media, i, a.kinds, a.edited, a.emojis, a.links, a.night,
                        a.longestBurst, a.days.size, a.emojiCounts.maxByOrNull { it.value }?.key,
                    )
                }
            val refrains = counts.entries
                .filter { it.value[0] > 1 }
                .sortedByDescending { it.value[0] }
                .take(8)
                .map { Refrain(labels.getValue(it.key), it.value[0]) }
            val topEmojis = emojis.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value }
            val first = messages.first().time
            val last = messages.last().time
            val firstDay = Math.floorDiv(first, 86_400L)
            val lastDay = Math.floorDiv(last, 86_400L)
            val days = max(1, (lastDay - firstDay + 1).toInt())

            var streak = 0
            var bestStreak = 0
            var prevDay = Long.MIN_VALUE
            for (d in perDay.keys.sorted()) {
                streak = if (d == prevDay + 1) streak + 1 else 1
                bestStreak = max(bestStreak, streak)
                prevDay = d
            }
            var silence = 0L
            var silenceFrom = first
            for (i in 1 until messages.size) {
                val dt = messages[i].time - messages[i - 1].time
                if (dt > silence) {
                    silence = dt
                    silenceFrom = messages[i - 1].time
                }
            }
            val busiest = perDay.entries.maxByOrNull { it.value }?.let { DayCount(it.key, it.value) }
            return ChatStats(
                messages, list, hours, refrains, topEmojis, days, first, last,
                weekdays = weekdays,
                activeDays = perDay.size,
                busiestDay = busiest,
                longestDayStreak = bestStreak,
                longestSilence = silence,
                longestSilenceFrom = silenceFrom,
                toTheMinute = messages.none { it.withSeconds },
            )
        }

        private val WHITESPACE = Regex("\\s+")
        private val TRAILING = Regex("[\\s.!?…,;:]+$")
        private val LINK = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE)

        /**
         * Two messages written in the same minute of an export to the minute: the answer came 0 to 59 s later,
         * 20 s on average (both moments anywhere in the minute, the answer after). Counted as such rather than
         * as 0 s, so that "instant" answers do not all read "0 s" and averages are not pulled down.
         */
        const val SAME_MINUTE = 20L

        /** Pictographic emojis of [text], one per symbol (skin tones, joiners and flags letters left out). */
        fun emojisOf(text: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (isPictographic(cp)) out.add(String(Character.toChars(cp)))
                i += Character.charCount(cp)
            }
            return out
        }

        private fun isPictographic(cp: Int): Boolean = when (cp) {
            in 0x1F3FB..0x1F3FF -> false // skin tones
            in 0x1F1E6..0x1F1FF -> false // flag letters
            in 0x1F000..0x1FAFF -> true
            in 0x2600..0x27BF -> true
            in 0x2300..0x23FF -> cp == 0x231A || cp == 0x231B || cp == 0x2328 || cp == 0x23CF || cp in 0x23E9..0x23FA
            0x2B50, 0x2B55, 0x2B1B, 0x2B1C, 0x2B05, 0x2B06, 0x2B07, 0x2934, 0x2935, 0x3030, 0x303D, 0x3297, 0x3299,
            0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139 -> true
            else -> false
        }

        fun replies(stats: ChatStats, gapSeconds: Long): ReplyStats {
            val byName = stats.people.associate { it.name to ArrayList<Long>() }
            val starts = stats.people.associate { it.name to IntArray(1) }
            val lastWords = stats.people.associate { it.name to IntArray(1) }
            val all = ArrayList<Long>()
            val ms = stats.messages
            var conversations = 1
            starts[ms[0].author]?.let { it[0]++ }
            for (i in 1 until ms.size) {
                val dt = ms[i].time - ms[i - 1].time
                if (dt > gapSeconds) {
                    // Silence long enough: the previous message ended a conversation, this one starts the next.
                    conversations++
                    lastWords[ms[i - 1].author]?.let { it[0]++ }
                    starts[ms[i].author]?.let { it[0]++ }
                    continue
                }
                if (ms[i].author == ms[i - 1].author || dt < 0) continue
                val answer = if (dt == 0L && stats.toTheMinute) SAME_MINUTE else dt
                byName[ms[i].author]?.add(answer)
                all.add(answer)
            }
            lastWords[ms.last().author]?.let { it[0]++ }
            val rows = stats.people.map { p ->
                val a = byName.getValue(p.name)
                ReplyRow(
                    person = p,
                    count = a.size,
                    median = median(a),
                    average = mean(a),
                    slowest = a.maxOrNull(),
                    under1min = share(a, 60),
                    under5min = share(a, 300),
                    starts = starts.getValue(p.name)[0],
                    lastWords = lastWords.getValue(p.name)[0],
                )
            }.sortedWith(
                compareBy<ReplyRow> { if (it.average == null) 1 else 0 }
                    .thenBy { if (it.count < PODIUM_MIN) 1 else 0 }
                    .thenBy { it.average ?: Double.MAX_VALUE }
                    .thenBy { it.median ?: Double.MAX_VALUE },
            )
            return ReplyStats(rows, all, mean(all), median(all), conversations, stats.messages.size)
        }

        private fun share(a: List<Long>, under: Long): Double? = if (a.isEmpty()) null else a.count { it < under }.toDouble() / a.size

        fun median(a: List<Long>): Double? {
            if (a.isEmpty()) return null
            val s = a.sorted()
            val k = s.size / 2
            return if (s.size % 2 == 1) s[k].toDouble() else (s[k - 1] + s[k]) / 2.0
        }

        fun mean(a: List<Long>): Double? = if (a.isEmpty()) null else a.sum().toDouble() / a.size

        /** Answers needed to stand on the podium. */
        const val PODIUM_MIN = 3
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Response times of one person, null = no answer measured; and the conversations they open and close. */
data class ReplyRow(
    val person: Person,
    val count: Int,
    val median: Double?,
    val average: Double?,
    val slowest: Long?,
    val under1min: Double?,
    val under5min: Double?,
    val starts: Int = 0,
    val lastWords: Int = 0,
)

data class ReplyStats(
    val rows: List<ReplyRow>,
    val all: List<Long>,
    val average: Double?,
    val median: Double?,
    val conversations: Int = 1,
    private val messageCount: Int = 0,
) {
    /** The three fastest on average with at least [ChatStats.PODIUM_MIN] answers. */
    val podium: List<ReplyRow> get() = rows.filter { it.count >= ChatStats.PODIUM_MIN && it.average != null }.take(3)
    val under5min: Double? get() = if (all.isEmpty()) null else all.count { it < 300 }.toDouble() / all.size

    /** Who opens the most conversations, then who most often has the last word. */
    val starter: ReplyRow? get() = rows.filter { it.starts > 0 }.maxByOrNull { it.starts }
    val closer: ReplyRow? get() = rows.filter { it.lastWords > 0 }.maxByOrNull { it.lastWords }
    val messagesPerConversation: Double get() = messageCount.toDouble() / max(1, conversations)

    /** Answers per delay bucket of [Buckets]. */
    val histogram: IntArray get() = IntArray(Buckets.size) { i -> all.count { it >= Buckets[i].from && it < Buckets[i].until } }
}

class Bucket(val from: Long, val until: Long, val label: String)

val Buckets = listOf(
    Bucket(0, 60, "< 1 min"),
    Bucket(60, 300, "1–5 min"),
    Bucket(300, 900, "5–15 min"),
    Bucket(900, 3_600, "15–60 min"),
    Bucket(3_600, 10_800, "1–3 h"),
    Bucket(10_800, Long.MAX_VALUE, "3 h +"),
)

/** After how long without a message a new conversation starts (an answer later than that is not one). */
enum class Gap(val seconds: Long, val label: String) {
    H1(3_600, "1 h"),
    H2(7_200, "2 h"),
    H3(10_800, "3 h"),
    H6(21_600, "6 h"),
    H12(43_200, "12 h"),
    H24(86_400, "24 h"),
    NEVER(Long.MAX_VALUE, "jamais"),
    ;

    companion object {
        val DEFAULT = H6
    }
}

/** "18 s", "4 min 05", "2 h 30", "1,5 j"; "—" when there is nothing. */
fun duration(seconds: Double?): String {
    if (seconds == null || seconds.isNaN()) return "—"
    val s = seconds.roundToLong()
    return when {
        s < 60 -> "$s s"
        s < 3_600 -> {
            val m = s / 60
            val r = s % 60
            if (r != 0L) "$m min ${r.toString().padStart(2, '0')}" else "$m min"
        }
        s < 86_400 -> {
            val minutes = (s / 60.0).roundToLong()
            val h = minutes / 60
            val m = minutes % 60
            if (m != 0L) "$h h ${m.toString().padStart(2, '0')}" else "$h h"
        }
        else -> String.format(java.util.Locale.FRANCE, "%.1f j", s / 86_400.0)
    }
}

fun duration(seconds: Long?): String = duration(seconds?.toDouble())

/** "42 %", "—". */
fun percent(x: Double?): String = if (x == null || x.isNaN()) "—" else "${(x * 100).roundToInt()} %"

/** "lundi"… for [ChatStats.weekdays] indexes. */
val WeekdayNames = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
