package com.powermaxv2.ytdownloader.core.watermark

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// The Filigrane's rules, without Android: the stamped line, where each copy of it goes on a page, the names of the
// saved files. The photo painter, the PDF stamper and the live preview all place their text with [Watermark.stamps],
// so what the screen shows is what gets saved.

/** Text written once in the middle, or repeated in staggered rows over the whole page. */
enum class WatermarkMode(val label: String) {
    TILED("Répété"),
    SINGLE("Une fois"),
}

/** Colour of the text (ARGB, alpha given separately by [WatermarkStyle.opacity]). */
enum class WatermarkInk(val label: String, val argb: Long) {
    GREY("Gris", 0xFF5F6368),
    RED("Rouge", 0xFFD93025),
    BLUE("Bleu", 0xFF1A56DB),
    BLACK("Noir", 0xFF111111),
    WHITE("Blanc", 0xFFFFFFFF),
}

data class WatermarkStyle(
    val text: String = Watermark.DEFAULT_TEXT,
    /** Adds " – 13/10/2026" (today) after the text. */
    val withDate: Boolean = true,
    /** 0.1 (barely there) … 0.9 (almost solid). */
    val opacity: Float = 0.32f,
    /** Degrees, clockwise on screen: -30 is a line rising to the right, 0 is flat. */
    val angle: Float = -30f,
    /** Height of the letters as a fraction of the page's shorter side. */
    val size: Float = 0.055f,
    val mode: WatermarkMode = WatermarkMode.TILED,
    val ink: WatermarkInk = WatermarkInk.GREY,
) {
    /** The same style with every number brought back into its range. */
    fun clamped(): WatermarkStyle = copy(
        opacity = opacity.coerceIn(Watermark.MIN_OPACITY, Watermark.MAX_OPACITY),
        angle = angle.coerceIn(-90f, 90f),
        size = size.coerceIn(Watermark.MIN_SIZE, Watermark.MAX_SIZE),
        text = text.take(Watermark.MAX_TEXT),
    )
}

/** Centre of one copy of the text, in page units (origin top-left, y down). */
data class Stamp(val x: Float, val y: Float)

object Watermark {
    const val DEFAULT_TEXT = "Copie pour location"
    const val MAX_TEXT = 80
    const val MIN_OPACITY = 0.1f
    const val MAX_OPACITY = 0.9f
    const val MIN_SIZE = 0.025f
    const val MAX_SIZE = 0.16f

    /** More copies than this on one page are never drawn (a tiny text on a huge page). */
    const val MAX_STAMPS = 400

    /** Ready-made texts offered as chips. */
    val presets = listOf(
        "Copie pour location",
        "Copie pour dossier",
        "Document confidentiel",
        "Ne pas diffuser",
        "Copie pour la banque",
    )

    /** "13/10/2026". [month] goes from 1 to 12. */
    fun dateText(day: Int, month: Int, year: Int): String =
        "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"

    /** Today's date on the phone, as [dateText] writes it. */
    fun today(): String = Calendar.getInstance().let {
        dateText(it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.MONTH) + 1, it.get(Calendar.YEAR))
    }

    /** The line that is stamped: the text on one line (default text when empty), then [date] if asked. */
    fun label(style: WatermarkStyle, date: String): String {
        val text = style.text
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TEXT)
            .ifEmpty { DEFAULT_TEXT }
        return if (style.withDate) "$text – $date" else text
    }

    /** Height of the letters, in the same unit as [pageWidth] / [pageHeight]. */
    fun fontSize(pageWidth: Float, pageHeight: Float, style: WatermarkStyle): Float =
        min(pageWidth, pageHeight) * style.size.coerceIn(MIN_SIZE, MAX_SIZE)

    /**
     * Where the copies of a text [textWidth] × [textHeight] go on a page [pageWidth] × [pageHeight] (each copy is then
     * turned by [WatermarkStyle.angle] around its own centre). [WatermarkMode.SINGLE]: one copy in the middle.
     * [WatermarkMode.TILED]: rows that follow the slant, every other row shifted by half a step like bricks, kept
     * only where a copy touches the page; centre-out order so an animation can grow from the middle.
     */
    fun stamps(pageWidth: Float, pageHeight: Float, textWidth: Float, textHeight: Float, style: WatermarkStyle): List<Stamp> {
        if (pageWidth <= 0f || pageHeight <= 0f) return emptyList()
        val cx = pageWidth / 2
        val cy = pageHeight / 2
        if (style.mode == WatermarkMode.SINGLE) return listOf(Stamp(cx, cy))
        val h = textHeight.coerceAtLeast(1e-3f * min(pageWidth, pageHeight))
        val w = textWidth.coerceAtLeast(h)
        val rowGap = h * ROW_GAP
        val colGap = w + h * COL_GAP
        val radius = hypot(pageWidth, pageHeight) / 2
        // Half the diagonal of a copy: a copy whose centre is further than this outside the page cannot touch it.
        val reach = hypot(w, h) / 2
        val rows = ceil((radius + reach) / rowGap).toInt() + 1
        val cols = ceil((radius + reach) / colGap).toInt() + 1
        val theta = style.angle * PI.toFloat() / 180f
        val c = cos(theta)
        val s = sin(theta)
        val out = ArrayList<Stamp>()
        for (j in -rows..rows) {
            val shift = if (abs(j) % 2 == 1) colGap / 2 else 0f
            for (i in -cols..cols) {
                // Position along the text (u) and across it (v), turned into page coordinates.
                val u = i * colGap + shift
                val v = j * rowGap
                val x = cx + u * c - v * s
                val y = cy + u * s + v * c
                if (x < -reach || x > pageWidth + reach || y < -reach || y > pageHeight + reach) continue
                out.add(Stamp(x, y))
            }
        }
        out.sortBy { hypot(it.x - cx, it.y - cy) }
        return if (out.size > MAX_STAMPS) out.subList(0, MAX_STAMPS).toList() else out
    }

    /** Space between rows, in text heights. */
    const val ROW_GAP = 3.4f

    /** Space between two copies on a row, in text heights. */
    const val COL_GAP = 2.2f

    /** Name of the stamped copy: "Carte identité.jpg" → "Carte identité - filigrane.jpg". */
    fun outputName(name: String, ext: String): String =
        SafeNames.withExtension(SafeNames.baseOf(name).trimEnd() + " - filigrane", ext)

    /** Extension of a stamped photo: PNG stays PNG (transparency, screenshots), anything else becomes JPG. */
    fun photoExtension(name: String, mime: String?): String {
        val png = mime.equals("image/png", ignoreCase = true) || SafeNames.extensionOf(name).equals("png", ignoreCase = true)
        return if (png) "png" else "jpg"
    }

    /**
     * [text] limited to what PDF's built-in Helvetica can write (WinAnsi: Latin-1 and a few signs like – € « »),
     * anything else replaced by "?". Only used when no phone font could be embedded in the PDF.
     */
    fun winAnsi(text: String): String = buildString {
        text.forEach { ch ->
            append(if (ch.code in 0x20..0x7E || ch.code in 0xA0..0xFF || ch in WIN_ANSI_EXTRA) ch else '?')
        }
    }

    private const val WIN_ANSI_EXTRA = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ"

    /**
     * Matrix (a b c d e f, PDF order) that maps the page as it is shown (origin bottom-left, after its /Rotate of
     * [rotation] degrees clockwise) to the page's own space, for a visible box at ([left], [bottom]) of
     * [width] × [height] in the page's own space. Text drawn in "shown" coordinates then reads upright.
     */
    fun shownToPage(rotation: Int, left: Float, bottom: Float, width: Float, height: Float): FloatArray =
        when (((rotation % 360) + 360) % 360) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, left + width, bottom)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, left + width, bottom + height)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, left, bottom + height)
            else -> floatArrayOf(1f, 0f, 0f, 1f, left, bottom)
        }

    /** Size of the page as shown: width and height swap for a page turned a quarter. */
    fun shownSize(rotation: Int, width: Float, height: Float): Pair<Float, Float> =
        if ((((rotation % 360) + 360) % 360) % 180 == 90) height to width else width to height
}
