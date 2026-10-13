package com.powermaxv2.ytdownloader.core.signpdf

import android.graphics.Bitmap
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

/**
 * Writes the signed copy with PdfBox: the original pages are kept as they are (text stays text), the signature is
 * added on top of one page as a picture with a transparent background, with its caption (initials, date) under it,
 * and the initials in the corner of the other pages when asked. A visual signature: no certificate is involved.
 */
object SignWriter {
    /** [onPage] gets the pages done out of the total. [out] is deleted again when anything fails. */
    suspend fun write(
        source: File,
        out: File,
        pageIndex: Int,
        box: SignBox,
        signature: Bitmap,
        stamp: SignStamp,
        today: LocalDate,
        onPage: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        try {
            val doc = try {
                PDDocument.load(source, MemoryUsageSetting.setupMixed(SCRATCH_MEMORY))
            } catch (e: InvalidPasswordException) {
                throw ConversionError("Ce PDF est protégé par un mot de passe : Gulp ne peut pas le signer.")
            }
            doc.use {
                // A PDF that opens without a password but carries restrictions: the signed copy is saved without them.
                if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                val total = doc.numberOfPages
                if (pageIndex !in 0 until total) throw IOException("Page introuvable.")
                val caption = SignPlan.caption(stamp, today)
                val image = LosslessFactory.createFromImage(doc, signature)
                val aspect = signature.width.toFloat() / signature.height.coerceAtLeast(1)
                val paraph = stamp.cleanInitials.takeIf { stamp.initialsEverywhere && it.isNotEmpty() }
                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    val page = doc.getPage(i)
                    val frame = frameOf(page)
                    if (i == pageIndex) {
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            val (sigRect, captionRect) = SignPlan.shownRects(box, aspect, frame)
                            cs.drawImage(image, matrix(SignPlan.imageMatrix(sigRect, frame)))
                            caption?.let { writeText(cs, it, captionRect, frame) }
                        }
                    } else if (paraph != null) {
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            writeText(cs, paraph, SignPlan.paraphRect(frame), frame, alignRight = true)
                        }
                    }
                    onPage(i + 1, total)
                }
                coroutineContext.ensureActive()
                doc.save(out)
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    /** The page's crop box and rotation (what a reader shows). */
    fun frameOf(page: PDPage): PdfPageFrame {
        val box = page.cropBox
        return PdfPageFrame(box.lowerLeftX, box.lowerLeftY, box.width, box.height, page.rotation)
    }

    private fun writeText(cs: PDPageContentStream, text: String, rect: SignRect, frame: PdfPageFrame, alignRight: Boolean = false) {
        val font = PDType1Font.HELVETICA
        val written = runCatching { font.getStringWidth(text) / 1000f }.getOrNull()?.let { text to it }
            // A character the font cannot write after all: keep plain ASCII.
            ?: text.filter { it.code in 0x20..0x7E }.let { ascii -> ascii to font.getStringWidth(ascii) / 1000f }
        val (shown, advance) = written
        if (shown.isBlank()) return
        val size = SignPlan.fitFont(rect, advance)
        val width = advance * size
        val left = if (alignRight) rect.right - width else rect.left
        // Baseline a little above the bottom of the line, for the descenders.
        val baseline = rect.top + rect.height * 0.8f
        cs.beginText()
        cs.setNonStrokingColor(INK_R, INK_G, INK_B)
        cs.setFont(font, 1f)
        cs.setTextMatrix(matrix(SignPlan.textMatrix(left, baseline, size, frame)))
        cs.showText(shown)
        cs.endText()
    }

    private fun matrix(m: PdfMatrix) = Matrix(m.a, m.b, m.c, m.d, m.e, m.f)

    /** Memory PdfBox may use before it spills to temporary files. */
    private const val SCRATCH_MEMORY: Long = 48L * 1024 * 1024

    private const val INK_R = 0.09f
    private const val INK_G = 0.10f
    private const val INK_B = 0.12f
}
