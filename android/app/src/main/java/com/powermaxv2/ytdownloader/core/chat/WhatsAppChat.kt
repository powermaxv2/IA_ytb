package com.powermaxv2.ytdownloader.core.chat

import java.time.LocalDateTime
import java.time.ZoneOffset

// Reading of a WhatsApp discussion export: one line per message,
// "15/09/2026 18:02 - Inès: Moi !!" (Android) or "[15/09/2026 18:02:41] Inès: Moi !!" (iPhone), in the order
// of dates of the phone's language (J/M/A, M/J/A or A/M/J), with or without AM/PM. A line that does not start
// with a date continues the message above it. Media, stickers, calls, deleted and edited messages are recognised
// in French and English, exported with or without media. Pure Kotlin: tested on the JVM.

/** What a message is: written text, or what WhatsApp wrote in its place. */
enum class Kind(val label: String) {
    TEXT("textes"),
    PHOTO("photos"),
    VIDEO("vidéos"),
    VOICE("vocaux"),
    AUDIO("audios"),
    STICKER("stickers"),
    GIF("GIF"),
    DOCUMENT("documents"),
    CONTACT("contacts"),
    LOCATION("positions"),
    POLL("sondages"),
    CALL("appels"),

    /** "<Médias omis>": an Android export without media does not say which kind (photo, sticker, vocal…). */
    MEDIA("médias"),
    DELETED("supprimés"),
    ;

    /** Something sent instead of written (not counted as words, refrains or emojis). */
    val isMedia: Boolean get() = this != TEXT && this != DELETED && this != CALL && this != POLL
}

/**
 * One message of the discussion. [time] is in seconds on a clock without time zone (only differences matter);
 * [withSeconds] = the export wrote the seconds (iPhone), else the time is to the minute (Android).
 */
data class ChatMessage(
    val time: Long,
    val hour: Int,
    val author: String,
    val text: String,
    val kind: Kind = Kind.TEXT,
    val edited: Boolean = false,
    val withSeconds: Boolean = false,
)

object WhatsAppChat {
    private val LINE = Regex(
        """^\[?(\d{1,4})[/.\-](\d{1,2})[/.\-](\d{1,4}),?\s*(?:à\s*)?(\d{1,2})[:h.](\d{2})(?:[:.](\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?]?\s*(?:[-–]\s*)?(.*)$""",
    )
    private val AUTHOR = Regex("""^([^:]{1,60}?):\s?([\s\S]*)$""")

    /** Invisible direction marks WhatsApp puts around names, dates and its own notices. */
    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")
    private val NBSP = Regex("[  ]")

    /**
     * Left-to-right mark: the iPhone puts it in front of every text WhatsApp writes itself (media, calls, notices),
     * never in front of what a person typed. Swapped for this private character while reading, so it survives
     * the cleaning of the line.
     */
    private const val LRM = '‎'
    private const val MARK = ''

    /** Android notices with a colon in them ("Inès a changé le sujet en « Soirée : samedi »") are not authors. */
    private val NOTICE_AUTHOR = Regex(
        "\\s(a|vous a|avez|s'est|est|ont) (créé|ajouté|retiré|quitté|changé|rejoint|épinglé|supprimé|modifié|été|parti|activé|désactivé)|" +
            "\\s(added|created|changed|removed|left|joined|pinned|deleted|turned|was|is now)\\s|[«»\"“”]",
        RegexOption.IGNORE_CASE,
    )

    private val IOS_NOTICE = Regex(
        "chiffrés de bout en bout|end-to-end encrypted|code de sécurité|security code|messages éphémères|disappearing messages|" +
            "\\b(a|vous a|avez|s'est|est|ont) (créé|ajouté|retiré|quitté|changé|rejoint|épinglé|bloqué|débloqué|activé|désactivé)\\b|" +
            "\\b(created|added|removed|left|changed|joined|pinned|blocked|unblocked|turned on|turned off)\\b|administrat|\\badmin\\b",
        RegexOption.IGNORE_CASE,
    )

    private val EDITED = Regex("\\s*$MARK?<(ce message a été modifié|this message was edited|message modifié)>\\s*$", RegexOption.IGNORE_CASE)

    private val DELETED = Regex(
        "^(ce message a été supprimé|vous avez supprimé ce message|this message was deleted|you deleted this message" +
            "|message supprimé|ce message a été supprimé par un administrateur|this message was deleted by admin.*)\\.?$",
        RegexOption.IGNORE_CASE,
    )
    private val OMITTED = Regex("^<?\\s*(médias? omis|media omitted|fichier omis|null)\\s*>?$", RegexOption.IGNORE_CASE)

    /** iPhone without media: "image absente", "sticker omitted", "document omis" (after the file name). */
    private val IOS_OMITTED = Regex(
        "(?:^|\\s)(image|photo|vid[ée]o|audio|sticker|autocollant|gif|document|carte de contact|contact card)\\s+" +
            "(omise?s?|absente?|omitted|retirée?)$",
        RegexOption.IGNORE_CASE,
    )

    /** Export with media. Android: "IMG-20260915-WA0003.jpg (fichier joint)"; iPhone: "<attached: 00000012-PHOTO-….jpg>". */
    private val ANDROID_FILE = Regex("^(\\S.*?\\.[A-Za-z0-9]{2,5})\\s+\\((fichier joint|file attached|pièce jointe)\\)", RegexOption.IGNORE_CASE)
    private val IOS_FILE = Regex("^<(?:attached|pièce jointe|joint)\\s*:\\s*(.+?)>", RegexOption.IGNORE_CASE)

    private val CALL = Regex(
        "^(appel (vocal|vidéo)( de groupe)?( manqué)?|(missed )?(group )?(voice|video) call|appel manqué)\\b.*",
        RegexOption.IGNORE_CASE,
    )
    private val POLL = Regex("^(SONDAGE|POLL)\\s*:", RegexOption.IGNORE_CASE)
    private val LOCATION = Regex(
        "^(localisation|location|position)\\s*:\\s*https?://(maps\\.google|www\\.google\\.com/maps|maps\\.apple)|" +
            "^(position en direct partagée|live location shared|position en direct)",
        RegexOption.IGNORE_CASE,
    )

    /** What [text] is, as WhatsApp wrote it (case and final dot ignored). */
    fun kindOf(text: String): Kind {
        val ios = text.firstOrNull() == MARK
        val t = text.replace(MARK.toString(), "").trim()
        if (t.isEmpty()) return Kind.TEXT
        if (DELETED.matches(t)) return Kind.DELETED
        if (OMITTED.matches(t)) return Kind.MEDIA
        ANDROID_FILE.find(t)?.let { return fileKind(it.groupValues[1]) }
        IOS_FILE.find(t)?.let { return fileKind(it.groupValues[1]) }
        if (POLL.containsMatchIn(t)) return Kind.POLL
        if (LOCATION.containsMatchIn(t)) return Kind.LOCATION
        if (ios) {
            // Only WhatsApp writes these on an iPhone: a person typing "appel vidéo ce soir ?" stays a text.
            if (CALL.matches(t)) return Kind.CALL
            IOS_OMITTED.find(t)?.let { return omittedKind(it.groupValues[1]) }
        } else if (t.length <= 40) {
            IOS_OMITTED.matchEntire(t)?.let { return omittedKind(it.groupValues[1]) }
        }
        return Kind.TEXT
    }

    /** Kept for the callers that only need "written or not". */
    fun isMedia(text: String): Boolean = kindOf(text) != Kind.TEXT

    private fun omittedKind(word: String): Kind = when (word.lowercase()) {
        "image", "photo" -> Kind.PHOTO
        "vidéo", "video" -> Kind.VIDEO
        "audio" -> Kind.VOICE
        "sticker", "autocollant" -> Kind.STICKER
        "gif" -> Kind.GIF
        "carte de contact", "contact card" -> Kind.CONTACT
        else -> if (word.lowercase().startsWith("vid")) Kind.VIDEO else Kind.DOCUMENT
    }

    /** From an attached file's name: WhatsApp's prefixes first (STK-, PTT-, -STICKER-…), then the extension. */
    internal fun fileKind(name: String): Kind {
        val n = name.uppercase()
        return when {
            n.startsWith("STK-") || "-STICKER-" in n -> Kind.STICKER
            n.startsWith("PTT-") || "-AUDIO-" in n && n.endsWith(".OPUS") -> Kind.VOICE
            "-GIF-" in n -> Kind.GIF
            n.startsWith("IMG-") || "-PHOTO-" in n -> Kind.PHOTO
            n.startsWith("VID-") || "-VIDEO-" in n -> Kind.VIDEO
            n.startsWith("AUD-") || "-AUDIO-" in n -> Kind.AUDIO
            n.endsWith(".VCF") -> Kind.CONTACT
            n.endsWith(".WEBP") -> Kind.STICKER
            Regex("\\.(JPE?G|PNG|HEIC)$").containsMatchIn(n) -> Kind.PHOTO
            Regex("\\.(MP4|MOV|3GP|MKV)$").containsMatchIn(n) -> Kind.VIDEO
            n.endsWith(".OPUS") -> Kind.VOICE
            Regex("\\.(MP3|M4A|AAC|WAV|OGG)$").containsMatchIn(n) -> Kind.AUDIO
            n.endsWith(".GIF") -> Kind.GIF
            else -> Kind.DOCUMENT
        }
    }

    private class Raw(
        val a: Int, val b: Int, val c: Int, val h: Int, val mi: Int, val s: Int, val withSeconds: Boolean, val ampm: String?,
        val author: String, val text: StringBuilder,
        /** A marked text that is not a media, a call…: maybe an iPhone notice. */
        val marked: Boolean = false,
    )

    /** The messages of [text], oldest first, or null when it does not look like a WhatsApp export. */
    fun parse(text: String): List<ChatMessage>? {
        val lines = text.replace("\r", "").replace(NBSP, " ").split('\n')
        val raw = ArrayList<Raw>()
        var current: Raw? = null
        for (rawLine in lines) {
            // A mark right after "Name: " (iPhone notice or media) is kept as MARK; every other mark goes.
            val line = rawLine.replace(": $LRM", ": $MARK").replace(INVISIBLE, "")
            val m = LINE.matchEntire(line)
            if (m != null) {
                val g = m.groupValues
                val am = AUTHOR.matchEntire(g[8])
                val author = am?.groupValues?.get(1)?.removePrefix("~")?.trim().orEmpty()
                val body = am?.groupValues?.get(2).orEmpty()
                // System message ("Les messages sont chiffrés…", "X a ajouté Y"): no author, skipped with the
                // lines that continue it. On an iPhone it has an author (the group, or the contact) and a marked
                // text: settled once every line is read.
                if (am == null || author.isEmpty() || NOTICE_AUTHOR.containsMatchIn(author)) {
                    current = null
                    continue
                }
                val r = Raw(
                    g[1].toIntOrNull() ?: continue, g[2].toInt(), g[3].toIntOrNull() ?: continue,
                    g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0, g[6].isNotEmpty(), g[7].ifEmpty { null },
                    author, StringBuilder(body), marked = body.firstOrNull() == MARK && kindOf(body) == Kind.TEXT,
                )
                raw.add(r)
                current = r
            } else if (current != null && line.isNotEmpty()) {
                current.text.append('\n').append(line)
            }
        }
        // iPhone notices: marked texts of an "author" who never types anything (the group's name), or that read
        // like a notice ("… a ajouté …", "chiffrés de bout en bout").
        val typing = raw.filter { !it.marked }.mapTo(HashSet()) { it.author }
        raw.removeAll { it.marked && (it.author !in typing || IOS_NOTICE.containsMatchIn(it.text)) }
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
            val time = runCatching {
                LocalDateTime.of(year, month, day, hour, r.mi, r.s).toEpochSecond(ZoneOffset.UTC)
            }.getOrNull() ?: return@mapNotNull null
            var body = r.text.toString()
            val edited = EDITED.containsMatchIn(body)
            if (edited) body = body.replace(EDITED, "")
            val kind = kindOf(body.trim())
            ChatMessage(time, hour, r.author, body.replace(MARK.toString(), "").trim(), kind, edited, r.withSeconds)
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
