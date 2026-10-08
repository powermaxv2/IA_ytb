package com.powermaxv2.ytdownloader.core.chat

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// What "Vu, répondu" computes from a discussion: messages per person, activity hours, repeated messages and
// emojis ([ChatStats.of]), then the response times for a "new conversation" threshold ([ChatStats.replies]).
// A response = a message sent just after someone else's, in the same conversation.

/** One person of the discussion; [colorIndex] picks their colour (rank by number of messages). */
data class Person(val name: String, val messages: Int, val words: Int, val media: Int, val colorIndex: Int)

/** A message written several times, as first written ([label]), and how many times. */
data class Refrain(val label: String, val count: Int)

data class ChatStats(
    val messages: List<ChatMessage>,
    /** Most talkative first. */
    val people: List<Person>,
    /** Messages per hour of the day, 0..23. */
    val hours: IntArray,
    val refrains: List<Refrain>,
    val emojis: List<Pair<String, Int>>,
    val days: Int,
    val first: Long,
    val last: Long,
) {
    val mediaCount: Int get() = people.sumOf { it.media }
    val perDay: Int get() = (messages.size.toDouble() / days).roundToInt()
    val peakHour: Int get() = hours.indices.maxByOrNull { hours[it] } ?: 0

    fun replies(gap: Gap): ReplyStats = replies(this, gap.seconds)

    companion object {
        fun of(messages: List<ChatMessage>): ChatStats {
            require(messages.isNotEmpty())
            class Acc(val name: String, var msgs: Int = 0, var words: Int = 0, var media: Int = 0)
            val people = LinkedHashMap<String, Acc>()
            val hours = IntArray(24)
            val counts = LinkedHashMap<String, IntArray>()
            val labels = HashMap<String, String>()
            val emojis = LinkedHashMap<String, Int>()
            for (m in messages) {
                val p = people.getOrPut(m.author) { Acc(m.author) }
                p.msgs++
                hours[m.hour.coerceIn(0, 23)]++
                if (WhatsAppChat.isMedia(m.text)) {
                    p.media++
                    continue
                }
                p.words += m.text.split(WHITESPACE).count { it.isNotEmpty() }
                val key = m.text.lowercase().replace(WHITESPACE, " ").replace(TRAILING, "").trim()
                if (key.isNotEmpty()) {
                    val c = counts.getOrPut(key) { IntArray(1) }
                    if (c[0] == 0) labels[key] = m.text.replace(TRAILING, "").ifEmpty { m.text }
                    c[0]++
                }
                for (e in emojisOf(m.text)) emojis[e] = (emojis[e] ?: 0) + 1
            }
            val list = people.values
                .sortedByDescending { it.msgs } // stable: first to speak first on ties
                .mapIndexed { i, a -> Person(a.name, a.msgs, a.words, a.media, i) }
            val refrains = counts.entries
                .filter { it.value[0] > 1 }
                .sortedByDescending { it.value[0] }
                .take(8)
                .map { Refrain(labels.getValue(it.key), it.value[0]) }
            val topEmojis = emojis.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value }
            val first = messages.first().time
            val last = messages.last().time
            val days = max(1, ((last - first) / 86_400.0).roundToInt() + 1)
            return ChatStats(messages, list, hours, refrains, topEmojis, days, first, last)
        }

        private val WHITESPACE = Regex("\\s+")
        private val TRAILING = Regex("[\\s.!?…,;:]+$")

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
            val all = ArrayList<Long>()
            val ms = stats.messages
            for (i in 1 until ms.size) {
                if (ms[i].author == ms[i - 1].author) continue
                val dt = ms[i].time - ms[i - 1].time
                if (dt < 0 || dt > gapSeconds) continue
                byName[ms[i].author]?.add(dt)
                all.add(dt)
            }
            val rows = stats.people.map { p ->
                val a = byName.getValue(p.name)
                ReplyRow(
                    person = p,
                    count = a.size,
                    median = median(a),
                    average = mean(a),
                    fastest = a.minOrNull(),
                    under5min = if (a.isEmpty()) null else a.count { it < 300 }.toDouble() / a.size,
                )
            }.sortedWith(
                compareBy<ReplyRow> { if (it.median == null) 1 else 0 }
                    .thenBy { if (it.count < PODIUM_MIN) 1 else 0 }
                    .thenBy { it.median ?: Double.MAX_VALUE }
                    .thenBy { it.average ?: Double.MAX_VALUE },
            )
            return ReplyStats(rows, all, mean(all), median(all))
        }

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

/** Response times of one person; null = no answer measured. */
data class ReplyRow(
    val person: Person,
    val count: Int,
    val median: Double?,
    val average: Double?,
    val fastest: Long?,
    val under5min: Double?,
)

data class ReplyStats(val rows: List<ReplyRow>, val all: List<Long>, val average: Double?, val median: Double?) {
    /** The three fastest with at least [ChatStats.PODIUM_MIN] answers. */
    val podium: List<ReplyRow> get() = rows.filter { it.count >= ChatStats.PODIUM_MIN && it.median != null }.take(3)
    val under5min: Double? get() = if (all.isEmpty()) null else all.count { it < 300 }.toDouble() / all.size

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
