package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDateTime
import java.time.ZoneOffset

// Reading of a WhatsApp discussion export ("Exporter la discussion → Sans médias"): one line per message,
// "15/09/2026 18:02 - Inès: Moi !!" (Android) or "[15/09/2026 18:02:41] Inès: Moi !!" (iPhone), in the order
// of dates of the phone's language (J/M/A, M/J/A or A/M/J), with or without AM/PM. A line that does not start
// with a date continues the message above it. Pure Kotlin: tested on the JVM.

/** One message of the discussion. [time] is in seconds on a clock without time zone (only differences matter). */
data class ChatMessage(val time: Long, val hour: Int, val author: String, val text: String)

object WhatsAppChat {
    private val LINE = Regex(
        """^\[?(\d{1,4})[/.\-](\d{1,2})[/.\-](\d{1,4}),?\s*(?:à\s*)?(\d{1,2})[:h.](\d{2})(?:[:.](\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?]?\s*(?:[-–]\s*)?(.*)$""",
    )
    private val AUTHOR = Regex("""^([^:]{1,60}?):\s?([\s\S]*)$""")

    /** Invisible direction marks WhatsApp puts around names and dates. */
    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")
    private val NBSP = Regex("[  ]")

    /** Media left out of the export, deleted or edited messages: counted, but not as words or "refrains". */
    private val MEDIA = Regex(
        "^(<?\\s*(médias? omis|media omitted|fichier omis)\\s*>?" +
            "|.*\\b(image|vidéo|video|audio|sticker|gif|document|photo|autocollant)\\s+(absente?|omis(e)?|omitted)\\b.*" +
            "|ce message a été supprimé|vous avez supprimé ce message|this message was deleted|you deleted this message" +
            "|message supprimé|null|<ce message a été modifié>" +
            "|.*\\.(vcf|pdf|opus|jpg|jpeg|png|webp|mp4)\\s*\\(fichier joint\\).*)$",
        RegexOption.IGNORE_CASE,
    )

    fun isMedia(text: String): Boolean = MEDIA.matches(text)

    private class Raw(
        val a: Int, val b: Int, val c: Int, val h: Int, val mi: Int, val s: Int, val ampm: String?,
        val author: String, val text: StringBuilder,
    )

    /** The messages of [text], oldest first, or null when it does not look like a WhatsApp export. */
    fun parse(text: String): List<ChatMessage>? {
        val lines = text.replace("\r", "").replace(INVISIBLE, "").replace(NBSP, " ").split('\n')
        val raw = ArrayList<Raw>()
        var current: Raw? = null
        for (line in lines) {
            val m = LINE.matchEntire(line)
            if (m != null) {
                val g = m.groupValues
                val am = AUTHOR.matchEntire(g[8])
                if (am == null) {
                    // System message ("Les messages sont chiffrés…", "X a ajouté Y"): no author, skipped.
                    current = null
                    continue
                }
                val author = am.groupValues[1].removePrefix("~").trim()
                val r = Raw(
                    g[1].toIntOrNull() ?: continue, g[2].toInt(), g[3].toIntOrNull() ?: continue,
                    g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0, g[7].ifEmpty { null },
                    author, StringBuilder(am.groupValues[2]),
                )
                raw.add(r)
                current = r
            } else if (current != null && line.isNotEmpty()) {
                current.text.append('\n').append(line)
            }
        }
        if (raw.isEmpty()) return null

        val order = dateOrder(raw)
        val messages = raw.mapNotNull { r ->
            val (day, month, year0) = when (order) {
                DateOrder.YMD -> Triple(r.c, r.b, r.a)
                DateOrder.MDY -> Triple(r.b, r.a, r.c)
                DateOrder.DMY -> Triple(r.a, r.b, r.c)
            }
            val year = if (year0 < 100) year0 + 2000 else year0
            var hour = r.h
            r.ampm?.let { ap ->
                val pm = ap.contains('p', ignoreCase = true)
                if (pm && hour < 12) hour += 12
                if (!pm && hour == 12) hour = 0
            }
            if (r.author.isEmpty()) return@mapNotNull null
            val time = runCatching {
                LocalDateTime.of(year, month, day, hour, r.mi, r.s).toEpochSecond(ZoneOffset.UTC)
            }.getOrNull() ?: return@mapNotNull null
            ChatMessage(time, hour, r.author, r.text.toString().trim())
        }
        return messages.ifEmpty { null }
    }

    private enum class DateOrder { DMY, MDY, YMD }

    /** J/M/A, M/J/A or A/M/J, from the values seen; when nothing settles it, the order in which dates follow best. */
    private fun dateOrder(raw: List<Raw>): DateOrder {
        if (raw.any { it.a > 31 }) return DateOrder.YMD
        if (raw.any { it.a > 12 }) return DateOrder.DMY
        if (raw.any { it.b > 12 }) return DateOrder.MDY
        fun score(key: (Raw) -> Int): Int {
            var k = 0
            var prev = 0
            for (r in raw) {
                val v = r.c * 400 + key(r)
                if (v >= prev) k++
                prev = v
            }
            return k
        }
        val dmy = score { it.b * 32 + it.a }
        val mdy = score { it.a * 32 + it.b }
        return if (mdy > dmy || (mdy == dmy && raw.any { it.ampm != null })) DateOrder.MDY else DateOrder.DMY
    }

    /** "WhatsApp Chat with Coloc.txt" → "Coloc". */
    fun niceName(fileName: String?): String {
        val name = fileName.orEmpty()
        val nice = name
            .replace(Regex("\\.(txt|zip)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^WhatsApp Chat( with| -)?\\s*|^Discussion WhatsApp avec\\s*", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .trim()
        return nice.ifEmpty { name.ifEmpty { "Discussion" } }
    }
}
