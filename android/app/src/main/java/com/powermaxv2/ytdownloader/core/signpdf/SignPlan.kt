package com.powermaxv2.ytdownloader.core.signpdf

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Pure geometry of the Signature PDF tool (no Android): where the signature sits on a page, how a finger moves and
// resizes it, how the drawn strokes are trimmed, and how a spot of the page as shown maps into the PDF's own space
// (pages can be turned by their /Rotate, and their crop box need not start at 0, 0). Tested on the JVM.

/** A point, in whatever unit the caller uses. */
data class SignPoint(val x: Float, val y: Float)

/** A rectangle: [left], [top] and its size, y going down. */
data class SignRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Where the signature sits on the page as shown: [x], [y] its top-left corner and [width] its width, all as fractions
 * of the page's width (x, width) and height (y). Its height follows from the signature's own shape.
 */
data class SignBox(val x: Float, val y: Float, val width: Float)

/** What is written under the signature: the day, initials, and the initials repeated on the other pages. */
data class SignStamp(
    val date: Boolean = false,
    val initials: String = "",
    /** The initials are also put in the bottom-right corner of every other page (a "paraphe"). */
    val initialsEverywhere: Boolean = false,
) {
    val cleanInitials: String get() = SignPlan.cleanText(initials).take(SignPlan.INITIALS_MAX)
}

/** An affine transform as a PDF writes it: (x, y) → (a·x + c·y + e, b·x + d·y + f). */
data class PdfMatrix(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    fun apply(x: Float, y: Float): SignPoint = SignPoint(a * x + c * y + e, b * x + d * y + f)
}

/** A page of the PDF in its own space: its crop box (origin, size in points, y up) and its /Rotate. */
data class PdfPageFrame(val x0: Float, val y0: Float, val width: Float, val height: Float, val rotation: Int) {
    /** Quarter turns of the page, 0..3 (a /Rotate that is not a multiple of 90 counts as 0, like readers do). */
    val quarter: Int get() = if (rotation % 90 != 0) 0 else Math.floorMod(rotation / 90, 4)

    /** Width of the page as shown (a page turned a quarter shows its height across). */
    val shownWidth: Float get() = if (quarter % 2 == 1) height else width
    val shownHeight: Float get() = if (quarter % 2 == 1) width else height
}

object SignPlan {
    /** Smallest and largest signature, as a fraction of the page's width. */
    const val MIN_WIDTH = 0.08f
    const val MAX_WIDTH = 0.9f

    /** Width of the signature when it first lands on a page. */
    const val START_WIDTH = 0.34f

    /** Height of the line under the signature (date, initials), as a fraction of the signature's width. */
    const val CAPTION_RATIO = 0.11f

    /** Gap between the signature and its caption, as a fraction of the signature's width. */
    const val CAPTION_GAP = 0.02f

    /** Width of the initials put on the other pages, as a fraction of the page's width; margin to the page's edges. */
    const val PARAPH_WIDTH = 0.12f
    const val PARAPH_MARGIN = 0.04f

    const val INITIALS_MAX = 6

    /** Margin kept around the strokes when the signature is cut out of the pad, as a fraction of the stroke width. */
    const val TRIM_PAD = 1.5f

    /** Points closer than this to the previous one (pad fraction) are dropped: a still finger draws no blob. */
    const val MIN_STEP = 0.0025f

    private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** The line written under the signature, or null when there is nothing to write. */
    fun caption(stamp: SignStamp, today: LocalDate): String? {
        val parts = buildList {
            stamp.cleanInitials.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (stamp.date) add("le ${today.format(DAY)}")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /**
     * Keeps what the standard PDF fonts can write (Latin-1 letters, digits, punctuation): anything else (emoji, other
     * alphabets) is dropped, blanks are folded.
     */
    fun cleanText(text: String): String =
        text.filter { it.code in 0x20..0x7E || it.code in 0xA0..0xFF }.replace(Regex("\\s+"), " ").trim()

    /** Height of the whole stamp (signature + caption) for a box [width] wide, as a fraction of the page's height. */
    fun footprintHeight(width: Float, signatureAspect: Float, pageAspect: Float, captioned: Boolean): Float {
        // width (page widths) → page-width units → page-height units through the page's aspect (w / h).
        val sig = width / signatureAspect.coerceAtLeast(0.05f)
        val caption = if (captioned) width * (CAPTION_RATIO + CAPTION_GAP) else 0f
        return (sig + caption) * pageAspect
    }

    /** The box a signature first gets on a page: bottom right, like a signature at the foot of a letter. */
    fun startBox(signatureAspect: Float, pageAspect: Float, captioned: Boolean): SignBox {
        val width = START_WIDTH
        val h = footprintHeight(width, signatureAspect, pageAspect, captioned)
        return clamp(SignBox(1f - width - 0.08f, 1f - h - 0.1f, width), signatureAspect, pageAspect, captioned)
    }

    /** [box] made to fit: its width within the limits (and the page), the whole stamp inside the page. */
    fun clamp(box: SignBox, signatureAspect: Float, pageAspect: Float, captioned: Boolean): SignBox {
        var width = box.width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        // A very tall signature on a wide page: shrink until it fits in height.
        val tall = footprintHeight(width, signatureAspect, pageAspect, captioned)
        if (tall > 1f) width = max(0.01f, width / tall)
        val h = footprintHeight(width, signatureAspect, pageAspect, captioned)
        return SignBox(
            x = box.x.coerceIn(0f, 1f - width),
            y = box.y.coerceIn(0f, max(0f, 1f - h)),
            width = width,
        )
    }

    /** [box] dragged by ([dx], [dy]) page fractions, kept on the page. */
    fun moved(box: SignBox, dx: Float, dy: Float, signatureAspect: Float, pageAspect: Float, captioned: Boolean): SignBox =
        clamp(box.copy(x = box.x + dx, y = box.y + dy), signatureAspect, pageAspect, captioned)

    /**
     * [box] resized from its bottom-right handle dragged by [dx] page widths: the top-left corner stays put, the
     * signature keeps its shape.
     */
    fun resized(box: SignBox, dx: Float, signatureAspect: Float, pageAspect: Float, captioned: Boolean): SignBox {
        val room = 1f - box.x
        val width = (box.width + dx).coerceIn(MIN_WIDTH, min(MAX_WIDTH, room))
        return clamp(box.copy(width = width), signatureAspect, pageAspect, captioned)
    }

    /** [box] scaled by [factor] around its centre (two-finger pinch). */
    fun scaled(box: SignBox, factor: Float, signatureAspect: Float, pageAspect: Float, captioned: Boolean): SignBox {
        val width = (box.width * factor).coerceIn(MIN_WIDTH, MAX_WIDTH)
        val oldH = footprintHeight(box.width, signatureAspect, pageAspect, captioned)
        val newH = footprintHeight(width, signatureAspect, pageAspect, captioned)
        val moved = SignBox(box.x - (width - box.width) / 2f, box.y - (newH - oldH) / 2f, width)
        return clamp(moved, signatureAspect, pageAspect, captioned)
    }

    /** Signature and caption rectangles on the page as shown, in points ([frame]'s shown size). */
    fun shownRects(box: SignBox, signatureAspect: Float, frame: PdfPageFrame): Pair<SignRect, SignRect> {
        val w = box.width * frame.shownWidth
        val h = w / signatureAspect.coerceAtLeast(0.05f)
        val left = box.x * frame.shownWidth
        val top = box.y * frame.shownHeight
        val signature = SignRect(left, top, w, h)
        val caption = SignRect(left, top + h + w * CAPTION_GAP, w, w * CAPTION_RATIO)
        return signature to caption
    }

    /** Where the initials go on the other pages: bottom right, in points of the page as shown. */
    fun paraphRect(frame: PdfPageFrame): SignRect {
        val w = PARAPH_WIDTH * frame.shownWidth
        val h = w * 0.4f
        val margin = PARAPH_MARGIN * min(frame.shownWidth, frame.shownHeight)
        return SignRect(frame.shownWidth - w - margin, frame.shownHeight - h - margin, w, h)
    }

    /**
     * A point of the page as shown (points, from its top-left, y down) in the PDF's own space (points, y up, crop box
     * origin). The page is shown turned [PdfPageFrame.rotation] degrees clockwise, as /Rotate asks.
     */
    fun toPdf(u: Float, v: Float, frame: PdfPageFrame): SignPoint = when (frame.quarter) {
        1 -> SignPoint(frame.x0 + v, frame.y0 + u)
        2 -> SignPoint(frame.x0 + frame.width - u, frame.y0 + v)
        3 -> SignPoint(frame.x0 + frame.width - v, frame.y0 + frame.height - u)
        else -> SignPoint(frame.x0 + u, frame.y0 + frame.height - v)
    }

    /**
     * The matrix that draws a picture (the unit square of PDF images, y up) upright into [rect] of the page as shown,
     * whatever the page's rotation.
     */
    fun imageMatrix(rect: SignRect, frame: PdfPageFrame): PdfMatrix {
        val bl = toPdf(rect.left, rect.bottom, frame)
        val br = toPdf(rect.right, rect.bottom, frame)
        val tl = toPdf(rect.left, rect.top, frame)
        return PdfMatrix(br.x - bl.x, br.y - bl.y, tl.x - bl.x, tl.y - bl.y, bl.x, bl.y)
    }

    /**
     * The text matrix that writes upright text of [fontSize] points from the baseline start ([u], [v]) of the page as
     * shown.
     */
    fun textMatrix(u: Float, v: Float, fontSize: Float, frame: PdfPageFrame): PdfMatrix {
        val origin = toPdf(u, v, frame)
        val right = toPdf(u + 1f, v, frame)
        val up = toPdf(u, v - 1f, frame)
        return PdfMatrix(
            (right.x - origin.x) * fontSize, (right.y - origin.y) * fontSize,
            (up.x - origin.x) * fontSize, (up.y - origin.y) * fontSize,
            origin.x, origin.y,
        )
    }

    /** Font size that fits [text] of [advance] (font units per 1 pt, i.e. width at 1 pt) into [rect]. */
    fun fitFont(rect: SignRect, advance: Float): Float {
        val byHeight = rect.height * 0.86f
        val byWidth = if (advance > 0f) rect.width / advance else byHeight
        return min(byHeight, byWidth).coerceIn(4f, 28f)
    }

    // MARK: - Strokes

    /** Strokes with the points too close to the previous one dropped (the first and last points always stay). */
    fun simplify(stroke: List<SignPoint>, minStep: Float = MIN_STEP): List<SignPoint> {
        if (stroke.size <= 2) return stroke
        val out = ArrayList<SignPoint>(stroke.size)
        out.add(stroke.first())
        for (i in 1 until stroke.lastIndex) {
            val p = stroke[i]
            val last = out.last()
            if (hypot(p.x - last.x, p.y - last.y) >= minStep) out.add(p)
        }
        out.add(stroke.last())
        return out
    }

    /** Bounds of [strokes], or null when nothing was drawn. */
    fun bounds(strokes: List<List<SignPoint>>): SignRect? {
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return SignRect(minX, minY, maxX - minX, maxY - minY)
    }

    /** Whether [strokes] are worth a signature: some ink, not a lone tap. */
    fun isSignature(strokes: List<List<SignPoint>>): Boolean {
        val b = bounds(strokes) ?: return false
        val length = strokes.sumOf { s -> s.zipWithNext { p, q -> hypot(q.x - p.x, q.y - p.y).toDouble() }.sum() }
        return max(b.width, b.height) >= 0.04f && length >= 0.08
    }

    /**
     * The picture the signature is saved as: [strokes] (pad fractions, the pad [padAspect] wide for 1 high) cut to
     * their bounds plus a margin, scaled so the longest side is [maxSide] px. Returns the picture's size and the
     * transform pad → picture pixels (scale, then offset), and the stroke width in picture pixels.
     */
    fun trim(strokes: List<List<SignPoint>>, padAspect: Float, strokeWidth: Float, maxSide: Int): TrimmedInk? {
        val raw = bounds(strokes) ?: return null
        // Pad fractions → square units (x × aspect), so the drawing keeps its shape.
        val b = SignRect(raw.left * padAspect, raw.top, raw.width * padAspect, raw.height)
        val pad = strokeWidth * TRIM_PAD
        val w = b.width + 2 * pad
        val h = b.height + 2 * pad
        val scale = maxSide / max(w, h)
        val pw = max(1, (w * scale).toInt())
        val ph = max(1, (h * scale).toInt())
        return TrimmedInk(
            width = pw,
            height = ph,
            scaleX = scale * padAspect,
            scaleY = scale,
            offsetX = (pad - b.left) * scale,
            offsetY = (pad - b.top) * scale,
            strokeWidth = strokeWidth * scale,
        )
    }

    /** Whether two floats are equal within [eps] (tests and clamping). */
    fun near(a: Float, b: Float, eps: Float = 1e-4f): Boolean = abs(a - b) <= eps
}

/** How to draw pad strokes into the saved picture: pixel = point × scale + offset. */
data class TrimmedInk(
    val width: Int,
    val height: Int,
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val strokeWidth: Float,
) {
    val aspect: Float get() = width.toFloat() / height
    fun map(p: SignPoint): SignPoint = SignPoint(p.x * scaleX + offsetX, p.y * scaleY + offsetY)
}
