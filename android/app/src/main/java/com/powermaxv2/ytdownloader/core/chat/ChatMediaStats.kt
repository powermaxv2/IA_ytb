package com.powermaxv2.ytdownloader.core.chat

// What the files of an export "with media" tell, joined to the messages that name them: weight sent per person
// and per kind, vocal and video lengths, documents, the most used stickers (by fingerprint) and the last photos.

/** One person's media: weight and number of files per [Kind] (by ordinal), vocal lengths, favourite sticker. */
data class PersonMedia(
    val name: String,
    val bytes: LongArray = LongArray(Kind.entries.size),
    val files: IntArray = IntArray(Kind.entries.size),
    val voiceSeconds: Double = 0.0,
    val voiceCount: Int = 0,
    val longestVoice: Double = 0.0,
    val videoSeconds: Double = 0.0,
    val favoriteSticker: String? = null,
    val favoriteStickerCount: Int = 0,
) {
    val totalBytes: Long get() = bytes.sum()
    fun bytes(kind: Kind): Long = bytes[kind.ordinal]
    val averageVoice: Double? get() = if (voiceCount == 0) null else voiceSeconds / voiceCount

    override fun equals(other: Any?): Boolean = other is PersonMedia && other.name == name && other.totalBytes == totalBytes
    override fun hashCode(): Int = name.hashCode()
}

/**
 * A sticker ([hash] = fingerprint of its file) sent [count] times, most by [topPerson] ([topCount] times).
 * [image] = its .webp, [emoji] = what to draw instead when there is no file (the example discussion).
 */
data class StickerUse(
    val hash: String,
    val count: Int,
    val topPerson: String,
    val topCount: Int,
    val people: Int,
    val image: ByteArray? = null,
    val emoji: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is StickerUse && other.hash == hash && other.count == count
    override fun hashCode(): Int = hash.hashCode()
}

/** A record held by someone: [value] (seconds or bytes) of the file [name]. */
data class MediaRecord(val person: String, val value: Double, val name: String? = null)

data class MediaStats(
    /** Same order as [ChatStats.people]. */
    val people: List<PersonMedia>,
    /** Every sticker sent, most used first. */
    val stickers: List<StickerUse>,
    val voiceSeconds: Double,
    val voiceCount: Int,
    val longestVoice: MediaRecord?,
    val videoSeconds: Double,
    val videoCount: Int,
    val longestVideo: MediaRecord?,
    /** Documents by type ("PDF", "Word"…), most first. */
    val documents: List<Pair<String, Int>>,
    val biggestDocument: MediaRecord?,
    /** The last photos of the discussion that are in the .zip, newest first (image files, small). */
    val photos: List<ByteArray>,
    /** Files named by the discussion and found in the .zip, and those missing (never downloaded, "view once"). */
    val found: Int,
    val missing: Int,
) {
    val totalBytes: Long get() = people.sumOf { it.totalBytes }
    val stickerSends: Int get() = stickers.sumOf { it.count }
    fun of(name: String): PersonMedia? = people.firstOrNull { it.name == name }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        /** Joins [scan] to the [messages] naming its files; null when no message names a file of the .zip. */
        fun of(messages: List<ChatMessage>, order: List<String>, scan: MediaScan, emojis: Map<String, String> = emptyMap()): MediaStats? {
            if (scan.isEmpty) return null
            class Acc(val name: String) {
                val bytes = LongArray(Kind.entries.size)
                val files = IntArray(Kind.entries.size)
                var voice = 0.0
                var voices = 0
                var longestVoice = 0.0
                var video = 0.0
                val stickers = HashMap<String, Int>()
            }
            val acc = LinkedHashMap<String, Acc>()
            order.forEach { acc[it] = Acc(it) }
            val stickerCount = LinkedHashMap<String, HashMap<String, Int>>()
            val docs = HashMap<String, Int>()
            var voiceTotal = 0.0
            var voiceCount = 0
            var longestVoice: MediaRecord? = null
            var videoTotal = 0.0
            var videoCount = 0
            var longestVideo: MediaRecord? = null
            var biggestDoc: MediaRecord? = null
            var found = 0
            var missing = 0
            val photoNames = ArrayList<String>()
            for (m in messages) {
                val name = m.file ?: continue
                val f = scan.files[name]
                if (f == null) {
                    missing++
                    continue
                }
                found++
                val a = acc.getOrPut(m.author) { Acc(m.author) }
                val kind = if (m.kind == Kind.MEDIA) WhatsAppChat.fileKind(name) else m.kind
                a.bytes[kind.ordinal] += f.bytes
                a.files[kind.ordinal]++
                val s = f.seconds
                when (kind) {
                    Kind.VOICE -> if (s != null) {
                        a.voice += s
                        a.voices++
                        a.longestVoice = maxOf(a.longestVoice, s)
                        voiceTotal += s
                        voiceCount++
                        if (s > (longestVoice?.value ?: 0.0)) longestVoice = MediaRecord(m.author, s, name)
                    }
                    Kind.VIDEO -> if (s != null) {
                        a.video += s
                        videoTotal += s
                        videoCount++
                        if (s > (longestVideo?.value ?: 0.0)) longestVideo = MediaRecord(m.author, s, name)
                    }
                    Kind.STICKER -> f.hash?.let { h ->
                        a.stickers[h] = (a.stickers[h] ?: 0) + 1
                        val per = stickerCount.getOrPut(h) { HashMap() }
                        per[m.author] = (per[m.author] ?: 0) + 1
                    }
                    Kind.DOCUMENT -> {
                        val type = documentType(name)
                        docs[type] = (docs[type] ?: 0) + 1
                        if (f.bytes > (biggestDoc?.value ?: -1.0)) biggestDoc = MediaRecord(m.author, f.bytes.toDouble(), name)
                    }
                    Kind.PHOTO -> if (name in scan.photos) photoNames.add(name)
                    else -> Unit
                }
            }
            if (found == 0) return null
            val stickers = stickerCount.entries.map { (hash, per) ->
                val top = per.entries.maxByOrNull { it.value }!!
                StickerUse(hash, per.values.sum(), top.key, top.value, per.size, scan.stickerImages[hash], emojis[hash])
            }.sortedByDescending { it.count }
            val people = acc.values.map { a ->
                val fav = a.stickers.entries.maxByOrNull { it.value }
                PersonMedia(a.name, a.bytes, a.files, a.voice, a.voices, a.longestVoice, a.video, fav?.key, fav?.value ?: 0)
            }
            return MediaStats(
                people, stickers, voiceTotal, voiceCount, longestVoice, videoTotal, videoCount, longestVideo,
                docs.entries.sortedByDescending { it.value }.map { it.key to it.value },
                biggestDoc,
                photoNames.asReversed().distinct().take(MediaScanner.PHOTOS_KEPT).mapNotNull { scan.photos[it] },
                found, missing,
            )
        }

        /** "PDF", "Word", "Excel"… from a file name. */
        fun documentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "PDF"
            "doc", "docx", "odt", "rtf" -> "Word"
            "xls", "xlsx", "ods", "csv" -> "Excel"
            "ppt", "pptx", "odp" -> "PowerPoint"
            "txt", "md" -> "Texte"
            "zip", "rar", "7z" -> "Archive"
            "apk" -> "Appli"
            "" -> "Autre"
            else -> name.substringAfterLast('.').uppercase().take(5)
        }
    }
}

/** "1,2 Go", "34 Mo", "820 Ko". */
fun weight(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        kb < 1 -> "$bytes o"
        kb < 1024 -> "${kb.toLong()} Ko"
        kb < 1024 * 1024 -> {
            val mb = kb / 1024
            if (mb < 10) String.format(java.util.Locale.FRANCE, "%.1f Mo", mb) else "${mb.toLong()} Mo"
        }
        else -> String.format(java.util.Locale.FRANCE, "%.1f Go", kb / 1024 / 1024)
    }
}
