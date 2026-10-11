package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// What "Vu, répondu" computes from a discussion: messages, words, media and stickers per person, activity hours,
// days and months, repeated messages, emojis, favourite words and links, records, and with an export "with media"
// the weight, vocals and stickers of each one ([ChatStats.of]); then, for [ReplyOptions] (threshold of a new
// conversation, nights left out, real wait), the response times, who answers whom, and who starts or ends the
// conversations ([ChatStats.replies]).
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
    /** Their most used words (common words left out), most first. */
    val topWords: List<Pair<String, Int>> = emptyList(),
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

/**
 * A stretch of the discussion for the "Évolution" card: a month, or a week (Monday) when the discussion lasts less
 * than [ChatStats.MONTHS_MIN] months. [key] = year × 12 + month − 1, or the Monday's epoch day.
 */
data class Period(val key: Long, val messages: Int, val weekly: Boolean) {
    /** "sept.", "15/09". */
    val label: String get() = if (weekly) {
        LocalDate.ofEpochDay(key).let { "${it.dayOfMonth.toString().padStart(2, '0')}/${it.monthValue.toString().padStart(2, '0')}" }
    } else {
        MonthNames[(key % 12).toInt()]
    }
}

val MonthNames = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

/** A record held by [person]: [value] (words, messages in a row…), on [clock], with an [excerpt]. */
data class TextRecord(val person: String, val value: Int, val clock: Long, val excerpt: String? = null)

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
    /** Every day with at least one message, oldest first. */
    val daily: List<DayCount> = emptyList(),
    /** Every month (or week, for a short discussion) from the first message to the last, empty ones included. */
    val periods: List<Period> = emptyList(),
    val longestMessage: TextRecord? = null,
    /** Most messages in a row by one person. */
    val longestMonologue: TextRecord? = null,
    /** Links shared, by site ("YouTube", "TikTok"…), most first. */
    val sites: List<Pair<String, Int>> = emptyList(),
    /** Calls whose length the export gives, and their total. */
    val timedCalls: Int = 0,
    val callSeconds: Long = 0,
    /** What the files of an export "with media" tell; null without them. */
    val media: MediaStats? = null,
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

    val weekly: Boolean get() = periods.firstOrNull()?.weekly ?: false

    /** The [Period.key] of a clock time. */
    fun periodOf(clock: Long): Long = periodKey(LocalDate.ofEpochDay(Math.floorDiv(clock, 86_400L)), weekly)

    fun replies(gap: Gap): ReplyStats = replies(this, ReplyOptions(gap))
    fun replies(options: ReplyOptions): ReplyStats = replies(this, options)

    companion object {
        /**
         * The numbers of [messages]; with [media] (the files of the .zip), the weight, vocals and stickers too.
         * [stickerEmojis] = what to draw for stickers without a file (the example discussion).
         */
        fun of(messages: List<ChatMessage>, media: MediaScan? = null, stickerEmojis: Map<String, String> = emptyMap()): ChatStats {
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
                val vocabulary = HashMap<String, Int>()
            }
            val people = LinkedHashMap<String, Acc>()
            val hours = IntArray(24)
            val weekdays = IntArray(7)
            val perDay = LinkedHashMap<Long, Int>()
            val counts = LinkedHashMap<String, IntArray>()
            val labels = HashMap<String, String>()
            val emojis = LinkedHashMap<String, Int>()
            var previous: String? = null
            val sites = HashMap<String, Int>()
            var longestMessage: TextRecord? = null
            var monologue: TextRecord? = null
            var timedCalls = 0
            var callSeconds = 0L
            for (m in messages) {
                val p = people.getOrPut(m.author) { Acc(m.author) }
                p.msgs++
                p.kinds[m.kind.ordinal]++
                if (m.edited) p.edited++
                if (previous != m.author) p.burst = 0
                p.burst++
                p.longestBurst = max(p.longestBurst, p.burst)
                if (p.burst > (monologue?.value ?: 1)) monologue = TextRecord(m.author, p.burst, m.clock)
                previous = m.author
                val hour = m.hour.coerceIn(0, 23)
                hours[hour]++
                if (hour < 5) p.night++
                val date = LocalDate.ofEpochDay(Math.floorDiv(m.clock, 86_400L))
                weekdays[date.dayOfWeek.value - 1]++
                perDay[date.toEpochDay()] = (perDay[date.toEpochDay()] ?: 0) + 1
                p.days.add(date.toEpochDay())
                if (m.kind == Kind.CALL) {
                    WhatsAppChat.callSeconds(m.text)?.let {
                        timedCalls++
                        callSeconds += it
                    }
                }
                if (m.kind != Kind.TEXT) {
                    if (m.kind.isMedia) p.media++
                    continue
                }
                val tokens = m.text.split(WHITESPACE)
                val wordCount = tokens.count { w -> w.any { it.isLetterOrDigit() } }
                p.words += wordCount
                if (wordCount > (longestMessage?.value ?: 0)) {
                    longestMessage = TextRecord(m.author, wordCount, m.clock, m.text.replace(WHITESPACE, " ").take(90))
                }
                for (link in LINK.findAll(m.text)) {
                    p.links++
                    val site = siteOf(link.value)
                    sites[site] = (sites[site] ?: 0) + 1
                }
                for (t in tokens) {
                    if (t.startsWith("http", true) || t.startsWith("www.", true)) continue
                    val w = t.lowercase().trim { !it.isLetter() }
                    if (w.length < 3 || w.any { !it.isLetter() && it != '\'' && it != '-' } || w in STOP_WORDS) continue
                    p.vocabulary[w] = (p.vocabulary[w] ?: 0) + 1
                }
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
                        a.vocabulary.entries.filter { it.value > 1 }.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                    )
                }
            val refrains = counts.entries
                .filter { it.value[0] > 1 }
                .sortedByDescending { it.value[0] }
                .take(8)
                .map { Refrain(labels.getValue(it.key), it.value[0]) }
            val topEmojis = emojis.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value }
            val first = messages.first().clock
            val last = messages.last().clock
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
                    silenceFrom = messages[i - 1].clock
                }
            }
            val busiest = perDay.entries.maxByOrNull { it.value }?.let { DayCount(it.key, it.value) }
            val firstDate = LocalDate.ofEpochDay(firstDay)
            val lastDate = LocalDate.ofEpochDay(lastDay)
            val weekly = (lastDate.year * 12 + lastDate.monthValue) - (firstDate.year * 12 + firstDate.monthValue) + 1 < MONTHS_MIN
            val perPeriod = HashMap<Long, Int>()
            perDay.forEach { (d, n) -> periodKey(LocalDate.ofEpochDay(d), weekly).let { perPeriod[it] = (perPeriod[it] ?: 0) + n } }
            val periods = ArrayList<Period>()
            var k = periodKey(firstDate, weekly)
            val lastKey = periodKey(lastDate, weekly)
            while (k <= lastKey) {
                periods.add(Period(k, perPeriod[k] ?: 0, weekly))
                k += if (weekly) 7 else 1
            }
            return ChatStats(
                messages, list, hours, refrains, topEmojis, days, first, last,
                weekdays = weekdays,
                activeDays = perDay.size,
                busiestDay = busiest,
                longestDayStreak = bestStreak,
                longestSilence = silence,
                longestSilenceFrom = silenceFrom,
                toTheMinute = messages.none { it.withSeconds },
                daily = perDay.entries.sortedBy { it.key }.map { DayCount(it.key, it.value) },
                periods = periods,
                longestMessage = longestMessage,
                longestMonologue = monologue,
                sites = sites.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
                timedCalls = timedCalls,
                callSeconds = callSeconds,
                media = media?.let { MediaStats.of(messages, list.map { p -> p.name }, it, stickerEmojis) },
            )
        }

        /** Below this many calendar months, the "Évolution" card goes week by week. */
        const val MONTHS_MIN = 3

        internal fun periodKey(date: LocalDate, weekly: Boolean): Long =
            if (weekly) date.toEpochDay() - (date.dayOfWeek.value - 1) else date.year * 12L + date.monthValue - 1

        /** "YouTube", "TikTok"… for a link; else its domain. */
        fun siteOf(link: String): String {
            val host = link.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').substringBefore('?')
            return when {
                host == "youtu.be" || host.endsWith("youtube.com") -> "YouTube"
                host.endsWith("tiktok.com") -> "TikTok"
                host.endsWith("instagram.com") -> "Instagram"
                host == "x.com" || host.endsWith("twitter.com") -> "X"
                host.endsWith("facebook.com") || host == "fb.watch" -> "Facebook"
                host.endsWith("snapchat.com") -> "Snapchat"
                host.endsWith("spotify.com") -> "Spotify"
                host.startsWith("maps.") || host == "goo.gl" || "google.com/maps" in link -> "Maps"
                host.endsWith("reddit.com") -> "Reddit"
                host.endsWith("whatsapp.com") || host == "wa.me" -> "WhatsApp"
                host.endsWith("amazon.fr") || host.endsWith("amazon.com") || host == "amzn.to" -> "Amazon"
                else -> host.removePrefix("m.").ifEmpty { "lien" }
            }
        }

        /** Words left out of the favourite words: the most common of French, English and Spanish. */
        private val STOP_WORDS = (
            "les des une est pas que qui dans pour sur avec mais par plus tout fait comme bien ont son ses aux été elle " +
                "ils nous vous leur cette ces sont ait mes tes nos vos moi toi lui eux ça cela ceci ici là oui non très peu " +
                "aussi alors donc car quand où quoi comment avoir être faire dit tous toute toutes rien sans sous chez entre " +
                "avant après encore déjà juste jamais toujours trop vers était avait étais avais suis es sommes êtes sera " +
                "ont fais faut peut veux vais va vas vont estce c'est j'ai t'as t'es j'suis qu'il qu'elle d'un d'une l'on " +
                "the and you that was for are with this have not but what all your can just they she him her his them " +
                "will would there their about from out get got like its it's i'm don't que los las por para una con del " +
                "esto eso pero muy más como"
            ).split(' ').toHashSet()

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

        /** Hours of the night left out by [ReplyOptions.nights]: from midnight to 7 a.m. */
        const val NIGHT_END = 7

        /** Seconds of [from]..[to] (clock seconds) between midnight and [NIGHT_END] h. */
        internal fun nightOverlap(from: Long, to: Long): Long {
            if (to <= from) return 0
            var total = 0L
            var day = Math.floorDiv(from, 86_400L)
            val lastDay = Math.floorDiv(to, 86_400L)
            while (day <= lastDay) {
                val a = max(from, day * 86_400L)
                val b = minOf(to, day * 86_400L + NIGHT_END * 3_600L)
                if (b > a) total += b - a
                day++
            }
            return total
        }

        fun replies(stats: ChatStats, options: ReplyOptions): ReplyStats {
            val gapSeconds = options.gap.seconds
            val byName = stats.people.associate { it.name to ArrayList<Long>() }
            val starts = stats.people.associate { it.name to IntArray(1) }
            val lastWords = stats.people.associate { it.name to IntArray(1) }
            val unanswered = stats.people.associate { it.name to IntArray(1) }
            val pairs = LinkedHashMap<Pair<String, String>, ArrayList<Long>>()
            val perPeriod = HashMap<Long, ArrayList<Long>>()
            val all = ArrayList<Long>()
            var fastest: ReplyRecord? = null
            var slowest: ReplyRecord? = null
            val ms = stats.messages
            var conversations = 1
            starts[ms[0].author]?.let { it[0]++ }
            // First message of the current run of messages by the same person, in this conversation.
            var runStart = 0

            fun ended(m: ChatMessage) {
                lastWords[m.author]?.let { it[0]++ }
                if (m.kind == Kind.TEXT && '?' in m.text) unanswered[m.author]?.let { it[0]++ }
            }
            for (i in 1 until ms.size) {
                val dt = ms[i].time - ms[i - 1].time
                if (dt > gapSeconds) {
                    // Silence long enough: the previous message ended a conversation, this one starts the next.
                    conversations++
                    ended(ms[i - 1])
                    starts[ms[i].author]?.let { it[0]++ }
                    runStart = i
                    continue
                }
                if (ms[i].author == ms[i - 1].author || dt < 0) continue
                val from = if (options.realWait) ms[runStart] else ms[i - 1]
                runStart = i
                var wait = ms[i].time - from.time
                if (wait < 0) continue
                if (options.nights) {
                    // Chatting in the middle of the night still counts; only the night slept through is left out.
                    val sameNight = Math.floorDiv(from.clock, 86_400L) == Math.floorDiv(ms[i].clock, 86_400L) && ms[i].hour < NIGHT_END && from.hour < NIGHT_END
                    if (!sameNight) wait = (wait - nightOverlap(from.clock, ms[i].clock)).coerceAtLeast(0)
                }
                val answer = if (wait == 0L && stats.toTheMinute) SAME_MINUTE else wait
                byName[ms[i].author]?.add(answer)
                all.add(answer)
                pairs.getOrPut(from.author to ms[i].author) { ArrayList() }.add(answer)
                perPeriod.getOrPut(stats.periodOf(ms[i].clock)) { ArrayList() }.add(answer)
                if (answer < (fastest?.seconds ?: Long.MAX_VALUE)) fastest = ReplyRecord(ms[i].author, from.author, answer, ms[i].clock)
                if (answer > (slowest?.seconds ?: -1L)) slowest = ReplyRecord(ms[i].author, from.author, answer, ms[i].clock)
            }
            lastWords[ms.last().author]?.let { it[0]++ }
            val people = stats.people.associateBy { it.name }
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
                    trimmed = trimmedMean(a),
                    unanswered = unanswered.getValue(p.name)[0],
                    fastest = a.minOrNull(),
                )
            }.sortedWith(
                compareBy<ReplyRow> { if (it.average == null) 1 else 0 }
                    .thenBy { if (it.count < PODIUM_MIN) 1 else 0 }
                    .thenBy { it.average ?: Double.MAX_VALUE }
                    .thenBy { it.median ?: Double.MAX_VALUE },
            )
            val pairRows = pairs.mapNotNull { (k, a) ->
                val from = people[k.first] ?: return@mapNotNull null
                val to = people[k.second] ?: return@mapNotNull null
                PairRow(from, to, a.size, mean(a), median(a))
            }
            return ReplyStats(
                rows, all, mean(all), median(all), conversations, stats.messages.size,
                trimmed = trimmedMean(all),
                pairs = pairRows,
                perPeriod = stats.periods.map { mean(perPeriod[it.key].orEmpty()) },
                fastest = fastest,
                slowest = slowest,
                options = options,
            )
        }

        /** Mean without the extremes: the fastest and slowest tenth left out (from 10 answers). */
        fun trimmedMean(a: List<Long>): Double? {
            if (a.size < 10) return mean(a)
            val s = a.sorted()
            val cut = s.size / 10
            return mean(s.subList(cut, s.size - cut))
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
    /** Mean without the fastest and slowest tenth. */
    val trimmed: Double? = average,
    /** Questions that ended a conversation: nobody answered them. */
    val unanswered: Int = 0,
    val fastest: Long? = null,
)

/** How [from]'s messages are answered by [to]. */
data class PairRow(val from: Person, val to: Person, val count: Int, val average: Double?, val median: Double?)

/** One answer: [person] answering [to] after [seconds], on [clock]. */
data class ReplyRecord(val person: String, val to: String, val seconds: Long, val clock: Long)

/**
 * How answers are measured. [gap] = silence after which a new conversation starts; [nights] = the hours from
 * midnight to 7 a.m. slept through are not counted; [realWait] = from the first message of the other's run of
 * messages (what the person really waited), instead of the last one.
 */
data class ReplyOptions(val gap: Gap = Gap.DEFAULT, val nights: Boolean = false, val realWait: Boolean = false)

data class ReplyStats(
    val rows: List<ReplyRow>,
    val all: List<Long>,
    val average: Double?,
    val median: Double?,
    val conversations: Int = 1,
    private val messageCount: Int = 0,
    val trimmed: Double? = average,
    val pairs: List<PairRow> = emptyList(),
    /** Mean answer in each of [ChatStats.periods], null = no answer then. */
    val perPeriod: List<Double?> = emptyList(),
    val fastest: ReplyRecord? = null,
    val slowest: ReplyRecord? = null,
    val options: ReplyOptions = ReplyOptions(),
) {
    /** Pairs with at least [ChatStats.PODIUM_MIN] answers, fastest first. */
    val fastestPairs: List<PairRow> get() = pairs.filter { it.count >= ChatStats.PODIUM_MIN && it.average != null }.sortedBy { it.average }

    /** Pairs that answer each other most (both directions together), most first. */
    val talkativePairs: List<Pair<Pair<Person, Person>, Int>> get() = pairs
        .groupBy { listOf(it.from.name, it.to.name).sorted() }
        .map { (_, rows) -> (rows[0].from to rows[0].to) to rows.sumOf { it.count } }
        .sortedByDescending { it.second }

    val unanswered: Int get() = rows.sumOf { it.unanswered }

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
