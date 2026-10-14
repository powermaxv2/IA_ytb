package com.powermaxv2.ytdownloader.core.pdfimages

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// PDF en images: the pure part (page choice, sizes, names), tested on the JVM without Android.

/** What comes out of the PDF. */
enum class ImagesMode(val label: String) {
    /** Each chosen page drawn as one picture, text and all. */
    PAGES("Pages entières"),

    /** The photos and pictures placed inside the chosen pages, at their own resolution. */
    EMBEDDED("Images contenues"),
}

/** File type of the pictures made from pages (and of the embedded pictures that are not JPG already). */
enum class ImagesFormat(val ext: String, val label: String, val mime: String) {
    JPG("jpg", "JPG", "image/jpeg"),
    PNG("png", "PNG", "image/png"),
}

/** How sharp the pages are drawn, in dots per inch of the printed page. */
enum class Sharpness(val label: String, val dpi: Int, val detail: String) {
    SCREEN("Écran", 110, "léger, pour lire sur un téléphone"),
    STANDARD("Standard", 160, "net, pour envoyer"),
    HIGH("Haute", 300, "pour imprimer ou zoomer"),
}

/** One page of the opened PDF: its index (from 0) and its size in points (1/72 inch), as shown (turn applied). */
data class PdfPageInfo(val index: Int, val widthPt: Float, val heightPt: Float)

object PdfImagesPlan {
    /** Longest side of a picture drawn from a page. */
    const val MAX_SIDE = 4096

    /** Pixels of one picture drawn from a page (ARGB: 4 bytes each, so about 96 MB at most in memory). */
    const val MAX_PIXELS = 24_000_000

    /** An embedded picture smaller than this on both sides (a logo dot, a line, a bullet) is left out. */
    const val MIN_EMBEDDED_SIDE = 48

    /** JPEG quality of the pictures Gulp encodes. */
    const val JPEG_QUALITY = 92

    /**
     * Size in pixels of a page of [widthPt] × [heightPt] points drawn at [dpi], kept under [MAX_SIDE] on its longest
     * side and under [MAX_PIXELS] in all, with the page's proportions.
     */
    fun renderSize(widthPt: Float, heightPt: Float, dpi: Int): Pair<Int, Int> {
        val w = widthPt.coerceAtLeast(1f)
        val h = heightPt.coerceAtLeast(1f)
        var scale = dpi / 72f
        scale = min(scale, MAX_SIDE / max(w, h))
        val pixels = w * h * scale * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        return max(1, (w * scale).roundToInt()).coerceAtMost(MAX_SIDE) to max(1, (h * scale).roundToInt()).coerceAtMost(MAX_SIDE)
    }

    /**
     * Pages (numbered from 1) written by the user: "1-3, 7", "2;4", "10-" (10 to the end), "-3" (1 to 3), "5-2"
     * (read as 2 to 5). Spaces are free, "à" and "–" work as dashes. Null when a part is not a page of a PDF with
     * [count] pages, or nothing is written. The result is sorted.
     */
    fun parsePages(text: String, count: Int): List<Int>? {
        if (count <= 0) return null
        val cleaned = text.lowercase()
            .replace('–', '-')
            .replace('—', '-')
            .replace(" à ", "-")
            .replace(" a ", "-")
        val parts = cleaned.split(',', ';').map { it.replace(" ", "") }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val pages = sortedSetOf<Int>()
        for (part in parts) {
            val dash = part.indexOf('-')
            if (dash < 0) {
                val n = part.toIntOrNull() ?: return null
                if (n !in 1..count) return null
                pages.add(n)
            } else {
                val left = part.substring(0, dash)
                val right = part.substring(dash + 1)
                if (right.contains('-')) return null
                val a = if (left.isEmpty()) 1 else left.toIntOrNull() ?: return null
                val b = if (right.isEmpty()) count else right.toIntOrNull() ?: return null
                val from = min(a, b)
                val to = max(a, b)
                if (from < 1 || to > count) return null
                for (n in from..to) pages.add(n)
            }
        }
        return pages.toList()
    }

    /** [pages] (from 1) written short: "1-3, 7, 9-12"; "" when empty. */
    fun formatPages(pages: Collection<Int>): String {
        val sorted = pages.toSortedSet().toList()
        if (sorted.isEmpty()) return ""
        val out = ArrayList<String>()
        var start = sorted[0]
        var last = start
        fun flush() {
            out += when {
                start == last -> "$start"
                last == start + 1 -> "$start, $last"
                else -> "$start-$last"
            }
        }
        for (n in sorted.drop(1)) {
            if (n == last + 1) {
                last = n
            } else {
                flush()
                start = n
                last = n
            }
        }
        flush()
        return out.joinToString(", ")
    }

    /** [selected] with page [page] added, or taken out when it was in. */
    fun toggle(selected: Set<Int>, page: Int): Set<Int> = if (page in selected) selected - page else selected + page

    /** All pages of a PDF with [count] pages (from 1). */
    fun all(count: Int): Set<Int> = (1..count.coerceAtLeast(0)).toSet()

    /**
     * Name of the picture of page [page] in a PDF of [count] pages: "Facture - page 03.jpg". Numbers get as many
     * digits as the last page (at least 2), so the gallery and the ZIP keep them in order.
     */
    fun pageName(base: String, page: Int, count: Int, ext: String): String =
        "${clean(base)} - page ${number(page, count)}.$ext"

    /** Name of an embedded picture: "Facture - p02 image 1.jpg" (page and rank on that page). */
    fun embeddedName(base: String, page: Int, count: Int, rank: Int, ext: String): String =
        "${clean(base)} - p${number(page, count)} image $rank.$ext"

    /** Name of the ZIP holding all the pictures: "Facture - images.zip". */
    fun zipName(base: String): String = "${clean(base)} - images.zip"

    private fun number(n: Int, count: Int): String = n.toString().padStart(max(2, count.toString().length), '0')

    /** The PDF's name without ".pdf", without the characters file systems refuse, not too long. */
    fun clean(base: String): String {
        val noExt = if (base.lowercase().endsWith(".pdf")) base.dropLast(4) else base
        val safe = noExt.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trimEnd('.', ' ')
        return safe.take(80).trimEnd('.', ' ').ifEmpty { "document" }
    }

    /**
     * Where the pictures go: a ZIP is always offered, and is chosen by default when there are many pictures (more
     * than [MANY]), so the gallery is not flooded.
     */
    fun zipByDefault(pictures: Int): Boolean = pictures > MANY

    const val MANY = 30

    /** "1 image", "12 images". */
    fun imagesText(n: Int): String = if (n > 1) "$n images" else "$n image"

    /** "1 page", "12 pages". */
    fun pagesText(n: Int): String = if (n > 1) "$n pages" else "$n page"

    /**
     * Whether an embedded picture of [width] × [height] pixels is worth keeping: not a hairline, a dot or a tiny
     * logo.
     */
    fun worthKeeping(width: Int, height: Int): Boolean = width >= MIN_EMBEDDED_SIDE || height >= MIN_EMBEDDED_SIDE

    /** Down-sampling factor (1, 2, 4…) so a [width] × [height] embedded picture decodes under [MAX_PIXELS]. */
    fun subsampling(width: Int, height: Int): Int {
        var factor = 1
        while (width.toLong() / factor * (height.toLong() / factor) > MAX_PIXELS) factor *= 2
        return factor
    }
}
