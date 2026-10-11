package com.powermaxv2.ytdownloader.core.cleaner

import com.powermaxv2.ytdownloader.core.Fmt

// The Nettoyeur WhatsApp's pure part: what kind of media a file of the WhatsApp folder is, which files are copies
// of each other, what is pre-checked, and the sentences the screen shows. No Android here (unit tested).

/** The kinds of media WhatsApp keeps, as the screen groups them. Ordered as shown. */
enum class MediaType(val label: String) {
    VIDEOS("Vidéos"),
    IMAGES("Photos"),
    DOCUMENTS("Documents"),
    VOICE("Vocaux"),
    AUDIO("Sons"),
    GIFS("GIF"),
    STICKERS("Stickers"),
    STATUSES("Statuts vus"),
    OTHER("Autres"),
}

/** One file of the WhatsApp folder. [id] is its document id (stable inside the chosen folder); [folder] is its path from the folder. */
data class MediaFile(
    val id: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val folder: String,
    val type: MediaType = CleanerFacts.typeOf(folder, name),
) {
    /** A copy WhatsApp kept of something the user sent (folder "Sent"): the received or original one may live elsewhere. */
    val sent: Boolean get() = folder.split('/').any { it.equals("Sent", ignoreCase = true) }
}

/** Files with the same content: [keep] stays, [copies] are pre-checked. */
data class DuplicateGroup(val keep: MediaFile, val copies: List<MediaFile>) {
    val files: List<MediaFile> get() = listOf(keep) + copies

    /** What deleting the copies frees. */
    val wasted: Long get() = copies.sumOf { it.size }
}

/** Space taken by one kind of media. */
data class TypeShare(val type: MediaType, val count: Int, val bytes: Long)

/** What the scan found. */
data class CleanerReport(
    val files: List<MediaFile> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    val shares: List<TypeShare> get() = CleanerFacts.shares(files)
    val wastedBytes: Long get() = duplicates.sumOf { it.wasted }
    val big: List<MediaFile> get() = CleanerFacts.bigFiles(files)
}

object CleanerFacts {
    /** Files smaller than this are never listed as big. */
    const val BIG_MIN = 5_000_000L

    /** How many big files the screen lists. */
    const val BIG_LIMIT = 40

    /** Below this size two files are not compared (WhatsApp's tiny thumbnails and empty files). */
    const val DUPLICATE_MIN = 1_000L

    private val imageExt = setOf("jpg", "jpeg", "png", "heic", "heif", "bmp")
    private val videoExt = setOf("mp4", "3gp", "mkv", "mov", "avi", "webm")
    private val audioExt = setOf("mp3", "m4a", "aac", "wav", "amr", "flac", "ogg")

    /** The kind of a file, from the WhatsApp folder it lives in (or its extension when the folder says nothing). */
    fun typeOf(folder: String, name: String): MediaType {
        val parts = folder.lowercase().split('/')
        fun has(word: String) = parts.any { it.contains(word) }
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            parts.any { it == ".statuses" } -> MediaType.STATUSES
            has("voice note") -> MediaType.VOICE
            has("video note") -> MediaType.VIDEOS
            has("animated gif") -> MediaType.GIFS
            has("sticker") -> MediaType.STICKERS
            has("images") -> MediaType.IMAGES
            has("video") -> MediaType.VIDEOS
            has("audio") -> MediaType.AUDIO
            has("document") -> MediaType.DOCUMENTS
            ext == "opus" -> MediaType.VOICE
            ext == "webp" -> MediaType.STICKERS
            ext in imageExt -> MediaType.IMAGES
            ext in videoExt -> MediaType.VIDEOS
            ext in audioExt -> MediaType.AUDIO
            ext == "pdf" || ext == "docx" || ext == "doc" || ext == "xlsx" || ext == "pptx" || ext == "zip" || ext == "apk" -> MediaType.DOCUMENTS
            else -> MediaType.OTHER
        }
    }

    /** Files WhatsApp or Android keep for themselves, never shown nor deleted (".nomedia", thumbnails, half-written files). */
    fun ignored(name: String): Boolean {
        val n = name.lowercase()
        return n == ".nomedia" || n.startsWith(".trashed") || n.endsWith(".tmp") || n.startsWith(".pending")
    }

    /**
     * Folders never walked: WhatsApp's chat backups and databases (deleting them loses the discussions), and its
     * private caches.
     */
    fun skippedFolder(name: String): Boolean {
        val n = name.lowercase()
        return n == "databases" || n == "backups" || n == ".shared" || n == ".thumbs" || n == ".trash"
    }

    /** Space per kind, biggest first; kinds with no file are left out. */
    fun shares(files: List<MediaFile>): List<TypeShare> =
        files.groupBy { it.type }
            .map { (type, list) -> TypeShare(type, list.size, list.sumOf { it.size }) }
            .sortedWith(compareByDescending<TypeShare> { it.bytes }.thenBy { it.type.ordinal })

    /** The biggest files (at least [BIG_MIN]), biggest first. */
    fun bigFiles(files: List<MediaFile>, limit: Int = BIG_LIMIT): List<MediaFile> =
        files.asSequence().filter { it.size >= BIG_MIN }.sortedByDescending { it.size }.take(limit).toList()

    /** Groups of files that could be copies of each other: same size, at least two. Only these need a fingerprint. */
    fun sameSize(files: List<MediaFile>): List<List<MediaFile>> =
        files.filter { it.size >= DUPLICATE_MIN }.groupBy { it.size }.values.filter { it.size > 1 }

    /**
     * Copies, from each file's content [fingerprint] (files without one are not compared). The file kept in each
     * group is the oldest one that is not in a "Sent" folder (the one received, or first saved); the others are
     * copies. Groups come biggest waste first.
     */
    fun duplicates(files: List<MediaFile>, fingerprint: Map<String, String>): List<DuplicateGroup> =
        files.filter { fingerprint[it.id] != null }
            .groupBy { fingerprint.getValue(it.id) to it.size }
            .values
            .filter { it.size > 1 }
            .map { group ->
                val ordered = group.sortedWith(compareBy<MediaFile> { it.sent }.thenBy { it.modified }.thenBy { it.folder.length }.thenBy { it.name })
                DuplicateGroup(ordered.first(), ordered.drop(1))
            }
            .sortedWith(compareByDescending<DuplicateGroup> { it.wasted }.thenBy { it.keep.name })

    /** What is checked when the results appear: every copy of every duplicate group, nothing else. */
    fun defaultSelection(duplicates: List<DuplicateGroup>): Set<String> =
        duplicates.flatMap { group -> group.copies.map { it.id } }.toSet()

    /** The files of [type] older than [days] days at [now]. */
    fun olderThan(files: List<MediaFile>, type: MediaType, days: Int, now: Long): List<MediaFile> {
        val limit = now - days * 86_400_000L
        return files.filter { it.type == type && it.modified in 1 until limit }
    }

    fun bytes(value: Long): String = Fmt.bytes(value.toDouble())

    fun files(count: Int): String = if (count > 1) "$count fichiers" else "$count fichier"

    /** Main button: "Libérer 1,2 Go · 34 fichiers". */
    fun freeLabel(bytes: Long, count: Int): String =
        if (count == 0) "Coche ce que tu veux supprimer" else "Libérer ${bytes(bytes)} · ${files(count)}"

    /** Body of the confirmation. */
    fun confirmText(count: Int, bytes: Long, kept: Int): String {
        val what = if (count > 1) "Ces $count fichiers seront supprimés" else "Ce fichier sera supprimé"
        val keep = if (kept > 0) " Pour chaque doublon, un exemplaire reste." else ""
        return "$what du téléphone pour de bon (${bytes(bytes)}). Ils disparaissent aussi des discussions WhatsApp, qui ne pourra plus les rouvrir.$keep"
    }

    /** Lines of the summary once deleted. */
    fun doneLines(deleted: List<MediaFile>, failed: Int, before: Long): List<String> {
        val lines = ArrayList<String>()
        val freed = deleted.sumOf { it.size }
        shares(deleted).forEach { lines += "${it.type.label} : ${files(it.count)}, ${bytes(it.bytes)}" }
        if (before > 0) {
            val ratio = freed * 100.0 / before
            val percent = if (freed > 0 && ratio < 0.5) "moins de 1" else Math.round(ratio).toString()
            lines += "Le dossier WhatsApp passe de ${bytes(before)} à ${bytes((before - freed).coerceAtLeast(0))} ($percent % de moins)"
        }
        if (failed > 0) lines += if (failed > 1) "$failed fichiers n'ont pas pu être supprimés" else "1 fichier n'a pas pu être supprimé"
        return lines
    }
}
