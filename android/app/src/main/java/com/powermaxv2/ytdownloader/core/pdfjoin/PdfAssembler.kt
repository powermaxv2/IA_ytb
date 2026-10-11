package com.powermaxv2.ytdownloader.core.pdfjoin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.coroutines.coroutineContext

/**
 * Writes the Assembleur's results with PdfBox: the pages of a PDF are copied as they are (text stays text and can
 * still be selected and searched, drawings stay sharp, links between copied pages keep working), photos become A4
 * pages. A PDF that PdfBox cannot read but Android can draw has its pages drawn into pictures by [raster] instead.
 *
 * One assembler serves one job: the sources it opens stay open until [close], because a copied page shares its
 * fonts and pictures with the file it comes from until the result is saved.
 */
class PdfAssembler(
    private val sources: List<JoinSource>,
    private val raster: suspend (JoinPage) -> Bitmap,
) : Closeable {
    private val opened = HashMap<Int, PDDocument?>()

    /** Pages Gulp could only draw into a picture (their PDF could not be read for copying), over every [write]. */
    var rasterized = 0
        private set

    /** Writes [pages] into [out] (deleted again when anything fails). [onPage] gets the number of pages written. */
    suspend fun write(pages: List<JoinPage>, out: File, onPage: (Int) -> Unit) {
        require(pages.isNotEmpty())
        try {
            PDDocument(MemoryUsageSetting.setupMixed(SCRATCH_MEMORY)).use { doc ->
                // Original page (in its own file) → its copy, for the links between pages.
                val copies = IdentityHashMap<COSDictionary, COSDictionary>()
                val copied = ArrayList<PDPage>()
                pages.forEachIndexed { i, page ->
                    coroutineContext.ensureActive()
                    val source = sources.getOrNull(page.source) ?: throw IOException("Fichier disparu.")
                    if (source.pdf) {
                        val original = document(page.source)?.let { d -> runCatching { d.getPage(page.index) }.getOrNull() }
                        val copy = original?.let { runCatching { doc.importPage(it) }.getOrNull() }
                        if (original != null && copy != null) {
                            copy.rotation = JoinPlan.pdfRotation(original.rotation, page.rotation)
                            // Article threads point at the other pages of the original: not carried over.
                            copy.cosObject.removeItem(COSName.B)
                            copies[original.cosObject] = copy.cosObject
                            copied.add(copy)
                        } else {
                            drawnPage(doc, page)
                        }
                    } else {
                        photoPage(doc, source.work, page)
                    }
                    onPage(i + 1)
                }
                copied.forEach { fixAnnotations(it, copies) }
                coroutineContext.ensureActive()
                doc.save(out)
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    /** The PdfBox document of source [index], opened once; null when PdfBox cannot read it (Android may still draw it). */
    private fun document(index: Int): PDDocument? {
        // A file PdfBox could not read is remembered as null, not tried again for each of its pages.
        if (opened.containsKey(index)) return opened[index]
        val source = sources[index]
        val doc = try {
            PDDocument.load(source.work, MemoryUsageSetting.setupMixed(SCRATCH_MEMORY))
        } catch (e: InvalidPasswordException) {
            throw ConversionError("« ${source.file.name} » est protégé par un mot de passe.")
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        opened[index] = doc
        return doc
    }

    /** A page drawn by Android into a picture, the size it shows, turned like the user asked. */
    private suspend fun drawnPage(doc: PDDocument, page: JoinPage) {
        val bitmap = opaque(raster(page.copy(rotation = 0)))
        try {
            val pdfPage = PDPage(PDRectangle(page.widthPt, page.heightPt))
            doc.addPage(pdfPage)
            val image = JPEGFactory.createFromImage(doc, bitmap, PAGE_JPEG_QUALITY)
            PDPageContentStream(doc, pdfPage).use { it.drawImage(image, 0f, 0f, page.widthPt, page.heightPt) }
            pdfPage.rotation = JoinPlan.pdfRotation(0, page.rotation)
            rasterized++
        } finally {
            bitmap.recycle()
        }
    }

    /** A photo on an A4 page (landscape when the photo is wider), its EXIF rotation applied. */
    private fun photoPage(doc: PDDocument, file: File, page: JoinPage) {
        val bitmap = opaque(Images.decode(file, JoinPlan.PHOTO_MAX_SIDE))
        try {
            val (w, h) = JoinPlan.photoPage(bitmap.width, bitmap.height)
            val pdfPage = PDPage(PDRectangle(w, h))
            doc.addPage(pdfPage)
            val image = JPEGFactory.createFromImage(doc, bitmap, JoinPlan.PHOTO_JPEG_QUALITY)
            val (x, y, dw, dh) = JoinPlan.photoBox(bitmap.width, bitmap.height, w, h).toList()
            PDPageContentStream(doc, pdfPage).use { it.drawImage(image, x, y, dw, dh) }
            pdfPage.rotation = JoinPlan.pdfRotation(0, page.rotation)
        } finally {
            bitmap.recycle()
        }
    }

    /** [bitmap] on white, without alpha (a JPEG has none; transparent areas would turn black). */
    private fun opaque(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bitmap.recycle()
        flat.setHasAlpha(false)
        return flat
    }

    /**
     * The annotations of a copied [page] (links, notes, form fields) still point at the original pages. Each one is
     * copied and pointed at the new page, links to a page that was copied too follow it, and anything that would drag
     * the rest of the original file into the result is dropped: links to pages left out, note pop-ups and replies,
     * the form a field belonged to (the field keeps its look, it is just no longer part of a form).
     */
    private fun fixAnnotations(page: PDPage, copies: IdentityHashMap<COSDictionary, COSDictionary>) {
        val dict = page.cosObject
        val annots = dict.getDictionaryObject(COSName.ANNOTS) as? COSArray ?: return
        val kept = COSArray()
        for (i in 0 until annots.size()) {
            val annot = annots.getObject(i) as? COSDictionary ?: continue
            val subtype = annot.getCOSName(COSName.SUBTYPE)?.name
            if (subtype == "Popup") continue
            val copy = COSDictionary(annot)
            copy.setItem(COSName.P, dict)
            copy.removeItem(POPUP)
            copy.removeItem(IRT)
            copy.removeItem(COSName.PARENT)
            if (subtype == "Link") {
                val dest = copy.getDictionaryObject(COSName.DEST)
                val action = copy.getDictionaryObject(COSName.A) as? COSDictionary
                when {
                    dest != null -> copy.setItem(COSName.DEST, remap(dest, copies) ?: continue)
                    action != null && action.getCOSName(COSName.S)?.name == "GoTo" -> {
                        val target = remap(action.getDictionaryObject(COSName.D), copies) ?: continue
                        copy.setItem(COSName.A, COSDictionary(action).apply {
                            setItem(COSName.D, target)
                            removeItem(NEXT)
                        })
                    }
                    action != null -> copy.setItem(COSName.A, COSDictionary(action).apply { removeItem(NEXT) })
                }
            }
            kept.add(copy)
        }
        if (kept.size() == 0) dict.removeItem(COSName.ANNOTS) else dict.setItem(COSName.ANNOTS, kept)
    }

    /** A destination "[page /XYZ …]" pointed at the copy of its page, or null (page not copied, named destination…). */
    private fun remap(dest: COSBase?, copies: IdentityHashMap<COSDictionary, COSDictionary>): COSArray? {
        val array = dest as? COSArray ?: return null
        if (array.size() == 0) return null
        val target = copies[array.getObject(0) as? COSDictionary ?: return null] ?: return null
        return COSArray().apply {
            add(target)
            for (j in 1 until array.size()) add(array.get(j))
        }
    }

    override fun close() {
        opened.values.forEach { runCatching { it?.close() } }
        opened.clear()
    }

    private companion object {
        /** Memory PdfBox may use per document before it spills into a scratch file in the cache. */
        const val SCRATCH_MEMORY = 16L * 1024 * 1024
        const val PAGE_JPEG_QUALITY = 0.85f
        val POPUP: COSName = COSName.getPDFName("Popup")
        val IRT: COSName = COSName.getPDFName("IRT")
        val NEXT: COSName = COSName.getPDFName("Next")
    }
}
