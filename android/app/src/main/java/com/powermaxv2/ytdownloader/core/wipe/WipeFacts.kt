package com.powermaxv2.ytdownloader.core.wipe

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

// What the Gomme reads from the photos and what it says about them on screen. Pure JVM, unit tested.

/** What a photo tells about whoever took it, as read from its EXIF. */
data class PhotoFacts(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val make: String? = null,
    val model: String? = null,
    /** Serial numbers, lens or software: also about the phone. */
    val deviceExtra: Boolean = false,
    /** A serial number of the phone or its lens is written in the photo. */
    val serial: Boolean = false,
    /** App that last wrote the photo ("Google Photos", "HDR+ 1.0…"). */
    val software: String? = null,
    /** GPS tags are there but Android blanked them (no "position des contenus multimédias" permission). */
    val locationHidden: Boolean = false,
    val artist: String? = null,
    val copyright: String? = null,
    /** "2026:10:10 18:42:07". */
    val date: String? = null,
    val orientation: Int = 1,
) {
    val hasLocation: Boolean get() = (latitude != null && longitude != null) || locationHidden
    val hasDevice: Boolean get() = make != null || model != null || deviceExtra
    val hasAuthor: Boolean get() = artist != null || copyright != null
    val hasDate: Boolean get() = date != null

    /**
     * The phone as people call it: "Pixel 8 Pro", "iPhone 15" (Google and Apple models say it all), else the brand
     * then the model ("Samsung SM-S921B"), never the brand twice.
     */
    val device: String?
        get() {
            val m = model ?: return make
            val brand = make ?: return m
            val word = brand.trim().substringBefore(' ')
            val selfExplained = word.lowercase() in setOf("google", "apple")
            return if (selfExplained || m.startsWith(word, ignoreCase = true)) m else "${brand.replaceFirstChar { it.uppercase() }} $m"
        }
}

/** What the user wants gone; the date stays by default (it sorts the gallery and gives nothing away alone). */
data class WipeChoices(
    val location: Boolean = true,
    val device: Boolean = true,
    val author: Boolean = true,
    val date: Boolean = false,
)


/** One of the four kinds of information the Gomme shows, each with its switch. */
enum class WipeKind { LOCATION, DEVICE, AUTHOR, DATE }

fun WipeChoices.wipes(kind: WipeKind): Boolean = when (kind) {
    WipeKind.LOCATION -> location
    WipeKind.DEVICE -> device
    WipeKind.AUTHOR -> author
    WipeKind.DATE -> date
}

fun WipeChoices.with(kind: WipeKind, on: Boolean): WipeChoices = when (kind) {
    WipeKind.LOCATION -> copy(location = on)
    WipeKind.DEVICE -> copy(device = on)
    WipeKind.AUTHOR -> copy(author = on)
    WipeKind.DATE -> copy(date = on)
}

fun PhotoFacts.has(kind: WipeKind): Boolean = when (kind) {
    WipeKind.LOCATION -> hasLocation
    WipeKind.DEVICE -> hasDevice
    WipeKind.AUTHOR -> hasAuthor
    WipeKind.DATE -> hasDate
}

/** A line of the result card: [done] = a check (erased), else a clock (kept on purpose) or a dash (nothing there). */
data class DoneLine(val kind: WipeKind?, val text: String, val done: Boolean)

/** The words of the Gomme's screens. */
object WipeReport {
    /**
     * What the photos say about [kind], for the line under its title: the first photo that tells it, then how many
     * of them do when it is not all; null when none does.
     */
    fun detail(kind: WipeKind, photos: List<PhotoFacts>): String? {
        val telling = photos.filter { it.has(kind) }
        val first = telling.firstOrNull() ?: return null
        val text = when (kind) {
            WipeKind.LOCATION -> first.latitude?.let { lat -> first.longitude?.let { lon -> coordinates(lat, lon) } }
                ?: "Présent, masqué par Android"
            WipeKind.DEVICE -> listOfNotNull(
                first.device ?: "Infos du téléphone",
                if (first.serial) "n° de série" else first.software,
            ).joinToString(" · ")
            WipeKind.AUTHOR -> listOfNotNull(first.artist?.let { "« $it »" }, first.copyright?.let { if (it.startsWith("©")) it else "© $it" })
                .joinToString(" · ")
            WipeKind.DATE -> readableDate(first.date ?: "")
        }
        return if (photos.size > 1 && telling.size < photos.size) "$text · ${photos(telling.size)} sur ${photos.size}" else text
    }

    /** "48,8650° N · 2,3780° E". */
    fun coordinates(lat: Double, lon: Double): String {
        fun part(v: Double) = String.format(Locale.FRANCE, "%.4f°", abs(v))
        return "${part(lat)} ${if (lat < 0) "S" else "N"} · ${part(lon)} ${if (lon < 0) "O" else "E"}"
    }

    /** "sam. 10 oct. 2026 · 18:42" from "2026:10:10 18:42:07"; the raw text when it does not parse. */
    fun readableDate(exifDate: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).apply { isLenient = false }.parse(exifDate)
            ?: return exifDate
        SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.FRANCE).format(parsed)
    }.getOrDefault(exifDate)

    /** "1 photo", "3 photos". */
    fun photos(n: Int): String = if (n > 1) "$n photos" else "$n photo"

    /**
     * The result card's lines, from what the photos told [before], the [choices], and what the saved copies still
     * tell ([after], read back from them): only what really happened is said.
     */
    fun doneLines(before: List<PhotoFacts>, choices: WipeChoices, after: List<PhotoFacts>, reencoded: Int): List<DoneLine> {
        val lines = ArrayList<DoneLine>()
        WipeKind.entries.forEach { kind ->
            val had = before.count { it.has(kind) }
            val still = after.count { it.has(kind) }
            val line = when {
                had == 0 -> null
                choices.wipes(kind) -> DoneLine(kind, erased(kind, had, before), done = true)
                still > 0 -> DoneLine(kind, kept(kind), done = false)
                // Kept on purpose, but the file type had no room for it (a plain WebP).
                else -> DoneLine(kind, lost(kind), done = true)
            }
            line?.let(lines::add)
        }
        if (lines.none { it.done }) lines.add(0, DoneLine(null, "Détails cachés effacés (miniature, logiciel, XMP)", done = true))
        if (reencoded > 0) lines += DoneLine(null, if (reencoded > 1) "$reencoded photos HEIC enregistrées en JPG" else "Photo HEIC enregistrée en JPG", done = true)
        return lines
    }

    private fun erased(kind: WipeKind, had: Int, before: List<PhotoFacts>): String = when (kind) {
        WipeKind.LOCATION -> if (before.size > 1) "Lieu effacé sur ${photos(had)}" else "Lieu effacé"
        WipeKind.DEVICE -> if (before.any { it.serial }) "Téléphone et n° de série effacés" else "Téléphone effacé"
        WipeKind.AUTHOR -> "Nom de l'auteur effacé"
        WipeKind.DATE -> "Date et heure effacées"
    }

    private fun kept(kind: WipeKind): String = when (kind) {
        WipeKind.LOCATION -> "Lieu gardé, comme tu l'as choisi"
        WipeKind.DEVICE -> "Téléphone gardé, comme tu l'as choisi"
        WipeKind.AUTHOR -> "Auteur gardé, comme tu l'as choisi"
        WipeKind.DATE -> "Date gardée, comme tu l'as choisi"
    }

    private fun lost(kind: WipeKind): String = when (kind) {
        WipeKind.LOCATION -> "Lieu effacé aussi (ce format ne peut pas le garder)"
        WipeKind.DEVICE -> "Téléphone effacé aussi (ce format ne peut pas le garder)"
        WipeKind.AUTHOR -> "Auteur effacé aussi (ce format ne peut pas le garder)"
        WipeKind.DATE -> "Date effacée aussi (ce format ne peut pas la garder)"
    }
}
