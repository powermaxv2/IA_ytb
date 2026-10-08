package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.exp

/**
 * The example discussion shown by "Voir un exemple": a made-up flat-share group over three weeks, where Inès
 * answers in about a minute and Hugo in half an hour. Always the same (seeded), written as an Android export.
 */
object ChatSample {
    const val NAME = "Groupe « Coloc Belleville »"

    fun text(): String {
        var seed = 7L
        fun rnd(): Double {
            seed = (seed * 16_807) % 2_147_483_647
            return seed / 2_147_483_647.0
        }
        // Time in seconds, with fractions as in the original app, so the minutes written are the same.
        // Typical answer time of each person, in seconds.
        val people = listOf("Inès" to 50.0, "Camille" to 260.0, "Théo" to 520.0, "Hugo" to 1_900.0)
        val words = listOf(
            "Mdr", "Ok", "Grave", "Ça marche", "Je suis en route", "Qui a pris mes clés ?", "😂😂", "Ce soir ?", "Ok", "Mdr",
            "Trop bien", "Vas-y", "<Médias omis>", "J'arrive dans 10 min", "Grave 🔥", "Qui fait les courses ?", "Ok", "Oui",
            "Mdr", "Bonne nuit 😴", "Merci !", "Quelqu'un a vu le chat ?", "🙏", "Ça marche",
        )
        fun at(t: Double) = LocalDateTime.ofEpochSecond(kotlin.math.floor(t).toLong(), 0, ZoneOffset.UTC)
        fun epoch(d: LocalDateTime) = d.toEpochSecond(ZoneOffset.UTC).toDouble()
        fun pad(n: Int) = n.toString().padStart(2, '0')
        val out = ArrayList<String>()
        out.add("15/09/2026 18:02 - Les messages et les appels sont chiffrés de bout en bout.")
        val start = LocalDateTime.of(2026, 9, 15, 18, 0)
        var t = epoch(start)
        val sessionHours = intArrayOf(8, 12, 19, 21, 22)
        for (day in 0 until 22) {
            val sessions = 1 + (rnd() * 3).toInt()
            repeat(sessions) {
                val begin = start.toLocalDate().plusDays(day.toLong())
                    .atTime(sessionHours[(rnd() * 5).toInt()], (rnd() * 60).toInt())
                t = maxOf(t + 60, epoch(begin))
                var who = (rnd() * 4).toInt()
                val length = 4 + (rnd() * 16).toInt()
                repeat(length) {
                    val d = at(t)
                    out.add("${pad(d.dayOfMonth)}/${pad(d.monthValue)}/${d.year} ${pad(d.hour)}:${pad(d.minute)} - ${people[who].first}: ${words[(rnd() * words.size).toInt()]}")
                    var next = who
                    if (rnd() < 0.75) {
                        do next = (rnd() * 4).toInt() while (next == who)
                        t += people[next].second * exp((rnd() - 0.5) * 2.2)
                    } else {
                        t += 20 + rnd() * 90
                    }
                    who = next
                }
            }
        }
        return out.joinToString("\n")
    }

    val stats: ChatStats by lazy { ChatStats.of(WhatsAppChat.parse(text())!!) }
}
