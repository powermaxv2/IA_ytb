package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.exp

/**
 * The example discussion shown by "Voir un exemple": a made-up flat-share group over three weeks, where Inès
 * answers in about a minute and Hugo in half an hour. Always the same (seeded), written as an Android export
 * "with media", so stickers, photos and vocals can be told apart.
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
        // "<Médias omis>" of the word list becomes, in turn, one of these attached files.
        val files = listOf(
            "STK-%s-WA%04d.webp", "IMG-%s-WA%04d.jpg", "STK-%s-WA%04d.webp", "PTT-%s-WA%04d.opus", "VID-%s-WA%04d.mp4",
            "IMG-%s-WA%04d.jpg", "PTT-%s-WA%04d.opus", "DOC-%s-WA%04d.pdf",
        )
        // Every other emoji-only message is sent as a sticker instead (no draw: the timings stay the same).
        var emojiOnly = 0
        // Some "on my way" become vocals and some "Trop bien" come with a link, the same way.
        var spoken = 0
        var shared = 0
        val links = listOf("https://youtu.be/dQw4w9WgXcQ", "https://www.tiktok.com/@coloc/video/1", "https://www.youtube.com/watch?v=Belleville", "https://www.instagram.com/p/Coloc/")
        var attached = 0
        var written = 0
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
                    var text = words[(rnd() * words.size).toInt()]
                    if ((text == "Je suis en route" || text == "J'arrive dans 10 min" || text == "Bonne nuit 😴") && ++spoken % 2 == 0) {
                        val date = "${d.year}${pad(d.monthValue)}${pad(d.dayOfMonth)}"
                        text = "PTT-%s-WA%04d.opus".format(date, attached) + " (fichier joint)"
                        attached++
                    } else if (text == "Trop bien" && ++shared % 2 == 0) {
                        text += " " + links[(shared / 2) % links.size]
                    } else if ((text == "😂😂" || text == "🙏") && ++emojiOnly % 2 == 0) {
                        val date = "${d.year}${pad(d.monthValue)}${pad(d.dayOfMonth)}"
                        text = "STK-%s-WA%04d.webp".format(date, attached) + " (fichier joint)"
                        attached++
                    } else if (text == "<Médias omis>") {
                        val date = "${d.year}${pad(d.monthValue)}${pad(d.dayOfMonth)}"
                        text = files[attached % files.size].format(date, attached) + " (fichier joint)"
                        attached++
                    } else if (++written % 25 == 0) {
                        text += " <Ce message a été modifié>"
                    }
                    out.add("${pad(d.dayOfMonth)}/${pad(d.monthValue)}/${d.year} ${pad(d.hour)}:${pad(d.minute)} - ${people[who].first}: $text")
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

    val stats: ChatStats by lazy {
        val messages = WhatsAppChat.parse(text())!!
        ChatStats.of(messages, media(messages), StickerEmojis)
    }

    /** The example's stickers: 8 made-up ones, drawn as emojis, some much more used than others. */
    private val StickerEmojis = listOf("😂", "🔥", "🥹", "💀", "🫠", "😎", "🙏", "👀").mapIndexed { i, e -> "sample$i" to e }.toMap()

    /**
     * Made-up files for the example's attachments (it has no .zip): weights, vocal and video lengths, sticker
     * fingerprints and a few drawn photos, always the same.
     */
    private fun media(messages: List<ChatMessage>): MediaScan {
        var seed = 11L
        fun rnd(): Double {
            seed = (seed * 16_807) % 2_147_483_647
            return seed / 2_147_483_647.0
        }
        val files = LinkedHashMap<String, MediaFile>()
        val photos = LinkedHashMap<String, ByteArray>()
        for (m in messages) {
            val name = m.file ?: continue
            val file = when (WhatsAppChat.fileKind(name)) {
                Kind.STICKER -> {
                    // Skewed towards the first ones: 0 is the favourite.
                    val i = (rnd() * rnd() * StickerEmojis.size).toInt()
                    MediaFile(name, 18_000L + (rnd() * 30_000).toLong(), hash = "sample$i")
                }
                Kind.VOICE -> {
                    val s = 5 + rnd() * rnd() * 110
                    MediaFile(name, (s * 2_000).toLong(), seconds = s)
                }
                Kind.VIDEO -> {
                    val s = 5 + rnd() * 50
                    MediaFile(name, (s * 900_000).toLong(), seconds = s)
                }
                Kind.PHOTO -> {
                    photos[name] = SamplePictures.photo(photos.size)
                    MediaFile(name, 120_000L + (rnd() * 2_400_000).toLong())
                }
                else -> MediaFile(name, 40_000L + (rnd() * 900_000).toLong())
            }
            files[name] = file
        }
        val kept = photos.keys.toList().takeLast(MediaScanner.PHOTOS_KEPT).associateWith { photos.getValue(it) }
        return MediaScan(files, emptyMap(), kept)
    }
}

/** Little landscapes drawn as BMP files (no Android needed), for the example's gallery. */
internal object SamplePictures {
    private val skies = listOf(
        intArrayOf(0xFF8A5B, 0xFFD27A), intArrayOf(0x4B5BD6, 0xA9B8FF), intArrayOf(0x21A06A, 0xC8F2DA),
        intArrayOf(0xE2522E, 0x5A1E6E), intArrayOf(0x1D2A5C, 0xE0A526), intArrayOf(0x9B59B6, 0xF7C6D9),
    )

    fun photo(index: Int, size: Int = 96): ByteArray {
        val (top, bottom) = skies[index % skies.size].let { it[0] to it[1] }
        val sunX = size * (0.25 + 0.5 * ((index * 37) % 10) / 10.0)
        val pixels = IntArray(size * size) { p ->
            val x = p % size
            val y = p / size
            val t = y.toDouble() / size
            val hill = size * (0.68 + 0.08 * kotlin.math.sin((x + index * 13) / 9.0))
            val sun = kotlin.math.hypot(x - sunX, y - size * 0.32) < size * 0.11
            when {
                y > hill -> mix(0x1B1410, bottom, 0.25)
                sun -> 0xFFF4D6
                else -> mix(top, bottom, t)
            }
        }
        return bmp(size, size, pixels)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 24-bit BMP, rows bottom up, each padded to 4 bytes. */
    private fun bmp(w: Int, h: Int, rgb: IntArray): ByteArray {
        val row = (w * 3 + 3) / 4 * 4
        val size = 54 + row * h
        val out = ByteArray(size)
        fun put(at: Int, v: Int, n: Int) {
            for (i in 0 until n) out[at + i] = (v shr (8 * i)).toByte()
        }
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        put(2, size, 4)
        put(10, 54, 4)
        put(14, 40, 4)
        put(18, w, 4)
        put(22, h, 4)
        put(26, 1, 2)
        put(28, 24, 2)
        put(34, row * h, 4)
        for (y in 0 until h) {
            val base = 54 + (h - 1 - y) * row
            for (x in 0 until w) {
                val c = rgb[y * w + x]
                out[base + x * 3] = (c and 0xFF).toByte()
                out[base + x * 3 + 1] = ((c shr 8) and 0xFF).toByte()
                out[base + x * 3 + 2] = ((c shr 16) and 0xFF).toByte()
            }
        }
        return out
    }
}
