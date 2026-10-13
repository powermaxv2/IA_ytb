package com.powermaxv2.ytdownloader.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Draws the watermark: onto a photo with Android's Canvas, onto every page of a PDF with PdfBox (the pages stay as
 * they are, text and drawings untouched; the watermark is added on top as real text, see-through).
 */
object WatermarkStamper {
    /** Draws [label] over [bitmap] (mutable) in [style]. */
    fun paint(bitmap: Bitmap, label: String, style: WatermarkStyle) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val size = Watermark.fontSize(w, h, style)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = size
            color = inkColor(style)
            if (style.ink == WatermarkInk.WHITE) setShadowLayer(size * 0.08f, 0f, size * 0.02f, Color.argb(110, 0, 0, 0))
        }
        val textWidth = paint.measureText(label)
        val metrics = paint.fontMetrics
        // Baseline that puts the middle of the capitals on the stamp's centre.
        val baseline = -(metrics.ascent + metrics.descent) / 2
        val canvas = Canvas(bitmap)
        Watermark.stamps(w, h, textWidth, size, style).forEach { stamp ->
            canvas.save()
            canvas.translate(stamp.x, stamp.y)
            canvas.rotate(style.angle)
            canvas.drawText(label, -textWidth / 2, baseline, paint)
            canvas.restore()
        }
    }

    private fun inkColor(style: WatermarkStyle): Int {
        val rgb = (style.ink.argb and 0xFFFFFF).toInt()
        val alpha = (style.opacity.coerceIn(Watermark.MIN_OPACITY, Watermark.MAX_OPACITY) * 255).toInt()
        return (alpha shl 24) or rgb
    }

    /**
     * Writes [source] with [label] stamped on each page into [out]. [onPage] gets (pages done, pages in all).
     * Throws [com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException] for a PDF locked by a password.
     */
    suspend fun stampPdf(source: File, out: File, label: String, style: WatermarkStyle, onPage: (Int, Int) -> Unit) {
        try {
            PDDocument.load(source, MemoryUsageSetting.setupMixed(SCRATCH_MEMORY)).use { doc ->
                // A PDF that only forbids changes (no opening password) can still be stamped: the copy is a new file.
                if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                val (font, text) = font(doc, label)
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = style.opacity.coerceIn(Watermark.MIN_OPACITY, Watermark.MAX_OPACITY)
                }
                val rgb = style.ink.argb
                val red = ((rgb shr 16) and 0xFF) / 255f
                val green = ((rgb shr 8) and 0xFF) / 255f
                val blue = (rgb and 0xFF) / 255f
                val total = doc.numberOfPages
                for ((index, page) in doc.pages.withIndex()) {
                    coroutineContext.ensureActive()
                    val box = page.cropBox
                    val rotation = page.rotation
                    val (shownW, shownH) = Watermark.shownSize(rotation, box.width, box.height)
                    val size = Watermark.fontSize(shownW, shownH, style)
                    val textWidth = font.getStringWidth(text) / 1000f * size
                    // Middle of the capitals on the stamp's centre (cap height ≈ 0.7 em).
                    val lift = size * 0.35f
                    val theta = Math.toRadians(-style.angle.toDouble())
                    val toPage = Watermark.shownToPage(rotation, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.saveGraphicsState()
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(red, green, blue)
                        cs.transform(Matrix(toPage[0], toPage[1], toPage[2], toPage[3], toPage[4], toPage[5]))
                        Watermark.stamps(shownW, shownH, textWidth, size, style).forEach { stamp ->
                            // Stamps are placed with y down (like the screen); PDF counts y up from the bottom.
                            val matrix = Matrix.getRotateInstance(theta, stamp.x, shownH - stamp.y)
                            matrix.translate(-textWidth / 2, -lift)
                            cs.beginText()
                            cs.setFont(font, size)
                            cs.setTextMatrix(matrix)
                            cs.showText(text)
                            cs.endText()
                        }
                        cs.restoreGraphicsState()
                    }
                    onPage(index + 1, total)
                }
                coroutineContext.ensureActive()
                doc.save(out)
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    /**
     * A phone font embedded in the PDF (accents, any alphabet), else PDF's own Helvetica with the text brought down
     * to what it can write.
     */
    private fun font(doc: PDDocument, label: String): Pair<PDFont, String> {
        for (path in SYSTEM_FONTS) {
            val file = File(path)
            if (!file.canRead()) continue
            val font = runCatching { PDType0Font.load(doc, file) }.getOrNull() ?: continue
            // Throws when a character has no glyph in this font.
            if (runCatching { font.getStringWidth(label) }.isSuccess && runCatching { font.encode(label) }.isSuccess) return font to label
        }
        return PDType1Font.HELVETICA_BOLD to Watermark.winAnsi(label)
    }

    private val SYSTEM_FONTS = listOf(
        "/system/fonts/Roboto-Bold.ttf",
        "/system/fonts/DroidSans-Bold.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )

    /** PdfBox keeps up to this much in memory, the rest in temporary files. */
    private const val SCRATCH_MEMORY = 48L * 1024 * 1024
}
