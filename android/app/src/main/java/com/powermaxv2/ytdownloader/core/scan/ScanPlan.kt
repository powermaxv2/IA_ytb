package com.powermaxv2.ytdownloader.core.scan

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Size of the PDF pages. [widthPt] / [heightPt] in points (1/72 inch), portrait; 0 = the photo's own shape. */
enum class PageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595.28f, 841.89f),
    LETTER("Lettre US", 612f, 792f),
    FIT("Comme la photo", 0f, 0f),
}

/** Resolution and JPEG quality of the pages: the PDF's weight. */
enum class ScanQuality(val label: String, val dpi: Int, val jpeg: Int, val bwJpeg: Int) {
    LIGHT("Légère", 110, 60, 50),
    NORMAL("Normale", 150, 75, 65),
    HIGH("Haute", 220, 88, 80),
}

/** Where a page's picture sits on its PDF page, in points (origin bottom-left, as in PDF). */
data class Placement(val pageWidth: Float, val pageHeight: Float, val x: Float, val y: Float, val width: Float, val height: Float)

/** The pure part of the PDF: page layout, pixel budget, size estimate, file name. Unit tested. */
object ScanPlan {
    /** Pages in one PDF at most. */
    const val MAX_PAGES = 60

    /** Width in points of a page made with [PageSize.FIT] (its height follows the photo). */
    const val FIT_WIDTH = 595.28f

    /** White border around the picture on an A4 / Letter page. */
    const val MARGIN = 0f

    /**
     * Places a [pixelWidth] × [pixelHeight] picture on a page of [size]: the page turns landscape for a landscape
     * picture, and the picture fills it as much as it can without being cut or stretched (centred).
     */
    fun place(pixelWidth: Int, pixelHeight: Int, size: PageSize): Placement {
        require(pixelWidth > 0 && pixelHeight > 0)
        val ratio = pixelWidth.toFloat() / pixelHeight
        if (size == PageSize.FIT) {
            val w = FIT_WIDTH
            val h = w / ratio
            return Placement(w, h, 0f, 0f, w, h)
        }
        val landscape = pixelWidth > pixelHeight
        val pw = if (landscape) size.heightPt else size.widthPt
        val ph = if (landscape) size.widthPt else size.heightPt
        val k = min((pw - 2 * MARGIN) / pixelWidth, (ph - 2 * MARGIN) / pixelHeight)
        val w = pixelWidth * k
        val h = pixelHeight * k
        return Placement(pw, ph, (pw - w) / 2f, (ph - h) / 2f, w, h)
    }

    /**
     * Pixel size the picture is brought down to for [quality]: what [quality]'s dpi needs at its printed size,
     * never larger than it already is.
     */
    fun targetPixels(pixelWidth: Int, pixelHeight: Int, placement: Placement, quality: ScanQuality): Pair<Int, Int> {
        val wanted = placement.width / 72f * quality.dpi
        val k = min(1f, wanted / pixelWidth)
        return maxOf(1, (pixelWidth * k).roundToInt()) to maxOf(1, (pixelHeight * k).roundToInt())
    }

    /** Rough weight of a PDF of [pages] pages (for the button's "≈ 1,1 Mo"). */
    fun estimate(pages: Int, size: PageSize, quality: ScanQuality, bwPages: Int = 0): Long {
        val area = if (size == PageSize.FIT) 595.28f * 841.89f else size.widthPt * size.heightPt
        val pixels = area / (72f * 72f) * quality.dpi * quality.dpi
        // About 0.9 bit per pixel for a JPEG text page at normal quality, half that for black & white.
        val bitsPerPixel = when (quality) {
            ScanQuality.LIGHT -> 0.7f
            ScanQuality.NORMAL -> 0.9f
            ScanQuality.HIGH -> 1.3f
        }
        val colour = (pages - bwPages).coerceAtLeast(0)
        val perPage = pixels * bitsPerPixel / 8f
        return (colour * perPage + bwPages * perPage * 0.5f + 1_200).toLong()
    }

    /** "Scan 10-10-2026 14h32" for [date]. */
    fun defaultName(date: Date = Date()): String = "Scan " + SimpleDateFormat("dd-MM-yyyy HH'h'mm", Locale.FRANCE).format(date)

    /** The PDF's file name from what the user typed ([fallback] when empty), safe and ending in ".pdf". */
    fun fileName(typed: String, fallback: String): String {
        val base = typed.trim().removeSuffix(".pdf").removeSuffix(".PDF").trim().ifEmpty { fallback }
        // "Rapport v1.2" keeps its ".2": withExtension only drops the ".pdf" added here.
        return SafeNames.withExtension(SafeNames.sanitize(base, fallback) + ".pdf", "pdf")
    }
}
