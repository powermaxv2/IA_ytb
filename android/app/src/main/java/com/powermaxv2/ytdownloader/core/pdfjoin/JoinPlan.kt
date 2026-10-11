package com.powermaxv2.ytdownloader.core.pdfjoin

import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The Assembleur PDF's page list: which page of which file comes where, turned how, and which files come out.
// Pure (no Android types): unit tested in JoinPlanTest.

/** One page of the result: page [index] of source [source] (a PDF page, or 0 for a photo), turned by [rotation]. */
data class JoinPage(
    /** Stable for the whole session (list keys, thumbnails, cuts, marks). */
    val id: Long,
    val source: Int,
    val index: Int,
    /** Size of the page as it shows in the original, in points (1/72 inch), before [rotation]. */
    val widthPt: Float,
    val heightPt: Float,
    /** Turned by the user, clockwise: 0 / 90 / 180 / 270. */
    val rotation: Int = 0,
) {
    /** Size of the page in the result (sides swapped when turned a quarter). */
    val outWidthPt: Float get() = if (rotation % 180 == 0) widthPt else heightPt
    val outHeightPt: Float get() = if (rotation % 180 == 0) heightPt else widthPt
}

/** The three jobs of the tool, chosen with the chips at the top. */
enum class JoinTool(val label: String) {
    /** Every page, in the order shown, in one PDF. */
    JOIN("Assembler"),

    /** The pages cut into several PDF. */
    SPLIT("Séparer"),

    /** One PDF without the pages marked. */
    REMOVE("Retirer des pages"),
}

/** How [JoinTool.SPLIT] cuts. */
enum class SplitMode(val label: String) {
    EACH("Une page par PDF"),
    CUTS("Couper où je touche"),
}

/** A file that comes out: its name, its pages (in order) and where they come from. */
data class JoinPart(val name: String, val pages: List<JoinPage>)

object JoinPlan {
    const val MAX_FILES = 20
    const val MAX_PAGES = 300

    /** A4 in points. Photos become A4 pages (portrait or landscape like the photo), fitted inside [PHOTO_MARGIN_PT]. */
    const val A4_SHORT_PT = 595f
    const val A4_LONG_PT = 842f
    const val PHOTO_MARGIN_PT = 24f

    /** Longest side, in pixels, of a photo placed on a page: about 300 dpi across A4. */
    const val PHOTO_MAX_SIDE = 2480
    const val PHOTO_JPEG_QUALITY = 0.88f

    /**
     * A page that Gulp cannot copy as it is (a PDF the copier cannot read, though Android can draw it) is drawn
     * into a picture at this resolution, its longest side capped at [PAGE_MAX_SIDE] pixels.
     */
    const val DPI = 150
    const val PAGE_MAX_SIDE = 2400

    /** Size in points of the page made from a photo of [w] × [h] pixels: A4, landscape when the photo is wider. */
    fun photoPage(w: Int, h: Int): Pair<Float, Float> =
        if (w > h) A4_LONG_PT to A4_SHORT_PT else A4_SHORT_PT to A4_LONG_PT

    /**
     * Where a photo of [w] × [h] pixels sits on its page of [pageW] × [pageH] points: left, bottom, width, height
     * (PDF coordinates start at the bottom left). Fitted inside the margin, centred, never stretched.
     */
    fun photoBox(w: Int, h: Int, pageW: Float, pageH: Float): FloatArray {
        val boxW = pageW - 2 * PHOTO_MARGIN_PT
        val boxH = pageH - 2 * PHOTO_MARGIN_PT
        val scale = min(boxW / max(1, w), boxH / max(1, h))
        val drawW = max(1, w) * scale
        val drawH = max(1, h) * scale
        return floatArrayOf((pageW - drawW) / 2, (pageH - drawH) / 2, drawW, drawH)
    }

    /** Pixels of the picture of a page of [widthPt] × [heightPt]: [DPI], longest side at most [PAGE_MAX_SIDE]. */
    fun renderSize(widthPt: Float, heightPt: Float): Pair<Int, Int> {
        val w = max(1f, widthPt)
        val h = max(1f, heightPt)
        val scale = min(DPI / 72f, PAGE_MAX_SIDE / max(w, h))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    /** The /Rotate of a copied page: what the original had plus what the user added, as 0 / 90 / 180 / 270. */
    fun pdfRotation(original: Int, added: Int): Int = (((original + added) % 360) + 360) % 360 / 90 * 90

    /** [pages] with the page [id] moved by [delta] places (−1 earlier, +1 later), clamped to the list. */
    fun move(pages: List<JoinPage>, id: Long, delta: Int): List<JoinPage> {
        val from = pages.indexOfFirst { it.id == id }
        if (from < 0) return pages
        return moveTo(pages, id, from + delta)
    }

    /** [pages] with the page [id] put at place [index] (dropped there after a drag), clamped to the list. */
    fun moveTo(pages: List<JoinPage>, id: Long, index: Int): List<JoinPage> {
        val from = pages.indexOfFirst { it.id == id }
        if (from < 0) return pages
        val to = index.coerceIn(0, pages.lastIndex)
        if (to == from) return pages
        val list = pages.toMutableList()
        list.add(to, list.removeAt(from))
        return list
    }

    /** [pages] with the page [id] turned a quarter clockwise. */
    fun rotate(pages: List<JoinPage>, id: Long): List<JoinPage> =
        pages.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it }

    fun remove(pages: List<JoinPage>, id: Long): List<JoinPage> = pages.filterNot { it.id == id }

    /** [set] with [id] added, or taken out when it was there. */
    fun toggle(set: Set<Long>, id: Long): Set<Long> = if (id in set) set - id else set + id

    /**
     * The pieces of [pages] for [JoinTool.SPLIT]: one page each, or a new piece starting at every page of [cuts]
     * (a cut on the first page changes nothing).
     */
    fun pieces(pages: List<JoinPage>, mode: SplitMode, cuts: Set<Long>): List<List<JoinPage>> = when (mode) {
        SplitMode.EACH -> pages.map { listOf(it) }
        SplitMode.CUTS -> {
            val out = ArrayList<MutableList<JoinPage>>()
            pages.forEachIndexed { i, page ->
                if (i == 0 || page.id in cuts) out.add(ArrayList())
                out.last().add(page)
            }
            out
        }
    }

    /**
     * The files [tool] makes from [pages] ([sourceNames] = the names of the files given, by source number).
     * Empty when there is nothing to make (no page, or every page marked for removal).
     */
    fun parts(
        tool: JoinTool,
        pages: List<JoinPage>,
        sourceNames: List<String>,
        split: SplitMode = SplitMode.EACH,
        cuts: Set<Long> = emptySet(),
        marked: Set<Long> = emptySet(),
    ): List<JoinPart> {
        if (pages.isEmpty()) return emptyList()
        fun names(list: List<JoinPage>) = list.map { sourceNames.getOrElse(it.source) { "document.pdf" } }
        return when (tool) {
            JoinTool.JOIN -> listOf(JoinPart(outputName(names(pages)), pages))
            JoinTool.REMOVE -> {
                val kept = pages.filterNot { it.id in marked }
                if (kept.isEmpty() || kept.size == pages.size) emptyList() else listOf(JoinPart(removedName(names(pages)), kept))
            }
            JoinTool.SPLIT -> {
                val pieces = pieces(pages, split, cuts)
                var start = 1
                pieces.map { piece ->
                    val end = start + piece.size - 1
                    JoinPart(pieceName(names(pages), start, end), piece).also { start = end + 1 }
                }
            }
        }
    }

    /** The names of [sources] without extension, once each, in order: the base of every name Gulp writes. */
    private fun base(sources: List<String>): String {
        val bases = sources.map { SafeNames.baseOf(SafeNames.sanitize(it)).ifBlank { "document" } }.distinct()
        return when (bases.size) {
            0 -> "Assemblage"
            1 -> bases[0]
            2 -> "${bases[0]} + ${bases[1]}"
            else -> "${bases[0]} + ${bases.size - 1} autres"
        }
    }

    /** Ends [base] with [suffix] and ".pdf", shortened so the whole name fits [SafeNames.MAX_LENGTH]. */
    private fun named(base: String, suffix: String): String {
        val room = (SafeNames.MAX_LENGTH - suffix.length - ".pdf".length).coerceAtLeast(1)
        return SafeNames.sanitize(base.take(room).trimEnd('.', ' ') + suffix + ".pdf", fallback = "Assemblage.pdf")
    }

    /** "devis + facture.pdf", "devis + 2 autres.pdf"; one file only (turned or reordered): "devis (modifié).pdf". */
    fun outputName(sourceNames: List<String>): String {
        val single = sourceNames.distinct().size <= 1
        return named(base(sourceNames), if (single) " (modifié)" else "")
    }

    /** "devis (pages retirées).pdf". */
    fun removedName(sourceNames: List<String>): String = named(base(sourceNames), " (pages retirées)")

    /** "devis (page 3).pdf", "devis (pages 4-6).pdf": [from] and [to] count from 1 in the pages shown. */
    fun pieceName(sourceNames: List<String>, from: Int, to: Int): String =
        named(base(sourceNames), if (from == to) " (page $from)" else " (pages $from-$to)")

    /**
     * Where the pages of each file landed in a result: for every source of [pages], in the order it first appears,
     * its number and the places (from 1) of its pages.
     */
    fun placements(pages: List<JoinPage>): List<Pair<Int, List<Int>>> {
        val bySource = LinkedHashMap<Int, MutableList<Int>>()
        pages.forEachIndexed { i, page -> bySource.getOrPut(page.source) { ArrayList() }.add(i + 1) }
        return bySource.map { (source, places) -> source to places }
    }

    /** "page 6", "pages 4 et 5", "pages 1 à 3", "pages 1, 3 et 5", "pages 1 à 3 et 7" for sorted [places]. */
    fun describePlaces(places: List<Int>): String {
        val sorted = places.distinct().sorted()
        if (sorted.isEmpty()) return ""
        if (sorted.size == 1) return "page ${sorted[0]}"
        val runs = ArrayList<IntRange>()
        var start = sorted[0]
        var prev = start
        for (n in sorted.drop(1)) {
            if (n != prev + 1) {
                runs.add(start..prev)
                start = n
            }
            prev = n
        }
        runs.add(start..prev)
        val words = runs.flatMap { r ->
            when {
                r.first == r.last -> listOf("${r.first}")
                r.last == r.first + 1 -> listOf("${r.first}", "${r.last}")
                else -> listOf("${r.first} à ${r.last}")
            }
        }
        val text = if (words.size == 1) words[0] else words.dropLast(1).joinToString(", ") + " et " + words.last()
        return "pages $text"
    }

    /** Short description of the page list: "6 pages · 3 fichiers". */
    fun summary(pages: Int, files: Int): String =
        (if (pages > 1) "$pages pages" else "$pages page") + " · " + (if (files > 1) "$files fichiers" else "$files fichier")

    /** "6 pages", "1 page". */
    fun pages(count: Int): String = if (count > 1) "$count pages" else "$count page"
}
