package com.powermaxv2.ytdownloader.core.status

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// The Statuts tool, without Android: where WhatsApp keeps the statuses, which files are statuses, how long they
// still live, how a kept copy is named, and the texts of the screen. Everything here is tested on the JVM.

/** The app whose statuses are read. Each keeps them in its own hidden ".Statuses" folder. */
enum class StatusApp(val title: String, private val packageFolder: String, private val appFolder: String) {
    WHATSAPP("WhatsApp", "com.whatsapp", "WhatsApp"),
    BUSINESS("WhatsApp Business", "com.whatsapp.w4b", "WhatsApp Business"),
    ;

    /** Since 2021 (scoped storage), on every Android version WhatsApp supports. */
    val mediaPath: String get() = "Android/media/$packageFolder/$appFolder/Media/$STATUSES"

    /** Older WhatsApp versions, at the root of the phone's storage. */
    val legacyPath: String get() = "$appFolder/Media/$STATUSES"

    companion object {
        /** The app a folder picked by the user belongs to, from its path ("primary:Android/media/com.whatsapp.w4b/…"). */
        fun of(path: String): StatusApp {
            val p = path.substringAfter(':').lowercase(Locale.ROOT)
            return if ("com.whatsapp.w4b" in p || "whatsapp business" in p) BUSINESS else WHATSAPP
        }
    }
}

const val STATUSES = ".Statuses"

/** Document id (Android's external storage provider) of the folder the system picker should open on. */
fun initialDocumentId(app: StatusApp): String = "primary:${app.mediaPath}"

/**
 * Where the statuses are, from the document id of the folder the user picked: the folder itself when it is a
 * ".Statuses" folder; null when it is above it (the caller then looks inside, see [statusesSearchDepth]).
 */
fun statusesFolderOf(documentId: String): String? =
    documentId.takeIf { it.trimEnd('/').substringAfterLast('/').substringAfterLast(':') == STATUSES }

/**
 * How many levels to look under a picked folder for ".Statuses": "Android/media/com.whatsapp" is 3 levels above
 * it (WhatsApp › Media › .Statuses). A folder that is not WhatsApp's is not searched at all.
 */
fun statusesSearchDepth(documentId: String): Int {
    val path = documentId.substringAfter(':').trim('/').lowercase(Locale.ROOT)
    if ("whatsapp" !in path) return 0
    val last = path.substringAfterLast('/')
    return when (last) {
        "media" -> 1
        "whatsapp", "whatsapp business" -> 2
        "com.whatsapp", "com.whatsapp.w4b" -> 3
        else -> 0
    }
}

enum class StatusKind { PHOTO, VIDEO }

/** A status file of the folder (statuses are photos or videos; ".nomedia" and anything else are skipped). */
fun statusKind(name: String, mime: String?): StatusKind? {
    if (name.startsWith(".")) return null
    val m = mime?.lowercase(Locale.ROOT).orEmpty()
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        m.startsWith("image/") || ext in PHOTO_EXTENSIONS -> StatusKind.PHOTO
        m.startsWith("video/") || ext in VIDEO_EXTENSIONS -> StatusKind.VIDEO
        else -> null
    }
}

private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private val VIDEO_EXTENSIONS = setOf("mp4", "3gp", "mkv", "webm", "mov")

const val STATUS_LIFETIME_MS: Long = 24L * 60 * 60 * 1000
private const val HOUR_MS: Long = 60L * 60 * 1000

/**
 * Hours a status still has before WhatsApp deletes it, counted from when the phone received it (the file date):
 * 24 just after, 1 in its last hour, 0 once the day is over (WhatsApp may keep it a little longer). Rounded up, so
 * "1 h" means less than an hour left.
 */
fun hoursLeft(modifiedAt: Long, now: Long): Int {
    if (modifiedAt <= 0L) return 24
    val left = STATUS_LIFETIME_MS - (now - modifiedAt).coerceAtLeast(0L)
    if (left <= 0L) return 0
    return ((left + HOUR_MS - 1) / HOUR_MS).toInt().coerceIn(1, 24)
}

/** Fraction of its day a status still has (for its ring): 1 just received, 0 when it is about to go. */
fun lifeLeft(modifiedAt: Long, now: Long): Float {
    if (modifiedAt <= 0L) return 1f
    val left = STATUS_LIFETIME_MS - (now - modifiedAt).coerceAtLeast(0L)
    return (left.toFloat() / STATUS_LIFETIME_MS).coerceIn(0f, 1f)
}

/** Short label of the time left, on the ring of a thumbnail. */
fun leftLabel(hours: Int): String = if (hours <= 0) "0h" else "${hours}h"

/**
 * Name of a kept copy: "Statut 2026-10-11 14h32 a1b2c3.jpg". WhatsApp names statuses with a long random code;
 * the date says when it was received, the start of the code keeps two statuses of the same minute apart.
 */
fun keptName(original: String, modifiedAt: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val ext = original.substringAfterLast('.', "").lowercase(Locale.ROOT).ifEmpty { "jpg" }
    val code = original.substringBeforeLast('.').filter { it.isLetterOrDigit() }.take(6).lowercase(Locale.ROOT).ifEmpty { "statut" }
    if (modifiedAt <= 0L) return "Statut $code.$ext"
    val format = SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.FRANCE).apply { timeZone = zone }
    return "Statut ${format.format(Date(modifiedAt))} $code.$ext"
}

/** Which statuses the grid shows. */
enum class StatusFilter(val title: String) {
    ALL("Tout"),
    PHOTOS("Photos"),
    VIDEOS("Vidéos"),
    ;

    fun accepts(kind: StatusKind): Boolean = when (this) {
        ALL -> true
        PHOTOS -> kind == StatusKind.PHOTO
        VIDEOS -> kind == StatusKind.VIDEO
    }
}

/** "9 statuts", "1 statut", "Aucun statut". */
fun statusCount(n: Int): String = when (n) {
    0 -> "Aucun statut"
    1 -> "1 statut"
    else -> "$n statuts"
}

/** "6 photos · 3 vidéos", "1 photo", "2 vidéos". */
fun kindsLine(photos: Int, videos: Int): String = listOfNotNull(
    photos.takeIf { it > 0 }?.let { if (it > 1) "$it photos" else "1 photo" },
    videos.takeIf { it > 0 }?.let { if (it > 1) "$it vidéos" else "1 vidéo" },
).joinToString(" · ").ifEmpty { "rien pour l'instant" }

/** Title of the "done" card. */
fun keptTitle(n: Int): String = if (n > 1) "$n statuts gardés" else "Statut gardé"

/** Where the kept files went: "5 photos dans Pictures/Gulp · 2 vidéos dans Movies/Gulp". */
fun keptWhere(photos: Int, videos: Int): String = listOfNotNull(
    photos.takeIf { it > 0 }?.let { (if (it > 1) "$it photos" else "1 photo") + " dans Pictures/Gulp" },
    videos.takeIf { it > 0 }?.let { (if (it > 1) "$it vidéos" else "1 vidéo") + " dans Movies/Gulp" },
).joinToString(" · ")

/** "0:28" for a video of 28 s, "1:05" for 65 s; null when unknown. */
fun durationLabel(ms: Long?): String? {
    if (ms == null || ms <= 0L) return null
    val s = ((ms + 500) / 1000).coerceAtLeast(1)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
