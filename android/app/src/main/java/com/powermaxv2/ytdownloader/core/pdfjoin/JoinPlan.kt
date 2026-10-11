package com.powermaxv2.ytdownloader.core.pdfjoin

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The Assembleur PDF's page list: which page of which file comes where, turned how. Pure (no Android types):
// unit tested in JoinPlanTest.

/** One page of the result: page [index] of source [source] (a PDF page, or 0 for a photo), turned by [rotation]. */
data class JoinPage(
    /** Stable for the whole session (list keys, thumbnails). */
    val id: Long,
    val source: Int,
    val index: Int,
    /** Size of the original page in points (1/72 inch), before [rotation]. */
    val widthPt: Float,
    val heightPt: Float,
    /** Clockwise, 0 / 90 / 180 / 270. */
    val rotation: Int = 0,
) {
    /** Size of the page in the result (sides swapped when turned a quarter). */
    val outWidthPt: Float get() = if (rotation % 180 == 0) widthPt else heightPt
    val outHeightPt: Float get() = if (rotation % 180 == 0) heightPt else widthPt
}

/** What comes out: one PDF with every page, or one PDF per page. */
enum class JoinMode(val label: String) {
    ONE("Un seul PDF"),
    EACH("Un PDF par page"),
}

object JoinPlan {
    const val MAX_FILES = 20
    const val MAX_PAGES = 300

    /** A4 width in points: photos become pages this wide. */
    const val A4_WIDTH_PT = 595f

    /** Pages are rendered at this resolution (dots per inch), their longest side capped at [PAGE_MAX_SIDE] pixels. */
    const val DPI = 150
    const val PAGE_MAX_SIDE = 2400
    const val JPEG_QUALITY = 85

    /** Size in points of the page made from a photo of [w] × [h] pixels: A4 wide, same proportions. */
    fun photoPage(w: Int, h: Int): Pair<Float, Float> {
        val width = max(1, w)
        val height = max(1, h)
        return A4_WIDTH_PT to A4_WIDTH_PT * height / width
    }

    /** Pixels of the picture of a page of [widthPt] × [heightPt]: [DPI], longest side at most [PAGE_MAX_SIDE]. */
    fun renderSize(widthPt: Float, heightPt: Float): Pair<Int, Int> {
        val w = max(1f, widthPt)
        val h = max(1f, heightPt)
        val scale = min(DPI / 72f, PAGE_MAX_SIDE / max(w, h))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    /** [pages] with the page [id] moved by [delta] places (−1 earlier, +1 later), clamped to the list. */
    fun move(pages: List<JoinPage>, id: Long, delta: Int): List<JoinPage> {
        val from = pages.indexOfFirst { it.id == id }
        if (from < 0) return pages
        val to = (from + delta).coerceIn(0, pages.lastIndex)
        if (to == from) return pages
        val list = pages.toMutableList()
        list.add(to, list.removeAt(from))
        return list
    }

    /** [pages] with the page [id] turned a quarter clockwise. */
    fun rotate(pages: List<JoinPage>, id: Long): List<JoinPage> =
        pages.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it }

    fun remove(pages: List<JoinPage>, id: Long): List<JoinPage> = pages.filterNot { it.id == id }

    /** Name of the result: "devis.pdf" for one file, "devis + facture.pdf", "devis + 2 autres.pdf". */
    fun outputName(sourceNames: List<String>): String {
        val bases = sourceNames.map { SafeNames.baseOf(SafeNames.sanitize(it)).ifBlank { "document" } }.distinct()
        val base = when (bases.size) {
            0 -> "Assemblage"
            1 -> bases[0]
            2 -> "${bases[0]} + ${bases[1]}"
            else -> "${bases[0]} + ${bases.size - 1} autres"
        }
        return SafeNames.sanitize("$base.pdf", fallback = "Assemblage.pdf")
    }

    /** "devis (page 3).pdf" for [JoinMode.EACH]. */
    fun pageName(sourceName: String, position: Int): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName)).ifBlank { "document" }
        val suffix = String.format(Locale.ROOT, " (page %d).pdf", position)
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }

    /** Short description of the page list: "6 pages · 3 fichiers". */
    fun summary(pages: Int, files: Int): String =
        (if (pages > 1) "$pages pages" else "$pages page") + " · " + (if (files > 1) "$files fichiers" else "$files fichier")
}
