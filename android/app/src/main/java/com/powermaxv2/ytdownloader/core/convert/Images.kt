package com.powermaxv2.ytdownloader.core.convert

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A conversion that failed for a reason the user can understand (French message, shown as is). */
class ConversionError(message: String, val debug: String? = null) : Exception(message)

/**
 * Photos and PDF pages, decoded on the phone. Every decode is bounded ([MAX_SIDE] px for photos, [PDF_PAGE_SIDE]
 * for rendered PDF pages) so a huge or hostile file cannot exhaust the memory.
 */
object Images {
    /** Longest side of a decoded photo. */
    const val MAX_SIDE = 4096

    /**
     * Longest side of a photo placed on a PDF page (about 150 dpi across A4). PdfDocument keeps every page's pixels
     * until writeTo(): drawn as RGB_565, a page holds at most 1240 × 1240 × 2 ≈ 3 MB, so with
     * [Formats.MAX_PDF_PHOTOS] (24) photos the worst case is ≈ 74 MB (≈ 148 MB if Skia kept them as ARGB).
     */
    const val PDF_IMAGE_SIDE = 1240

    /** Longest side of a rendered PDF page. */
    const val PDF_PAGE_SIDE = 2048

    /** Pages rendered at most from one PDF. */
    const val MAX_PDF_PAGES = 200

    /** A4 in PDF points (1/72 inch). */
    private const val A4_SHORT = 595
    private const val A4_LONG = 842
    private const val PAGE_MARGIN = 24f

    /** Decodes [file] with its longest side brought down to [maxSide] (EXIF rotation applied). */
    fun decode(file: File, maxSide: Int = MAX_SIDE): Bitmap {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(file, maxSide) else decodeLegacy(file, maxSide)
        } catch (e: OutOfMemoryError) {
            throw ConversionError("Photo trop grande pour la mémoire du téléphone.")
        } catch (e: ConversionError) {
            throw e
        } catch (e: Exception) {
            null
        }
        return bitmap ?: throw ConversionError(
            "Gulp ne peut pas lire cette photo : format non pris en charge par ce téléphone, ou fichier abîmé.",
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(file: File, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(file)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software pixels: Bitmap.compress and the PDF canvas need them.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            }
        }
    }

    private fun decodeLegacy(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return rotateFromExif(file, bitmap)
    }

    private fun rotateFromExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Writes [bitmap] as JPG, PNG or WebP into [out]; [quality] 0..100 (ignored by PNG). */
    fun encode(bitmap: Bitmap, target: Target, quality: Int, out: File) {
        val format = when (target) {
            Target.JPG -> Bitmap.CompressFormat.JPEG
            Target.PNG -> Bitmap.CompressFormat.PNG
            Target.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> throw IllegalArgumentException("$target n'est pas une image Bitmap")
        }
        // JPG has no transparency: transparent areas become white instead of black.
        val source = if (target == Target.JPG && bitmap.hasAlpha()) onWhite(bitmap) else bitmap
        try {
            val ok = out.outputStream().buffered().use { source.compress(format, quality.coerceIn(0, 100), it) }
            if (!ok) throw ConversionError("Impossible d'écrire l'image ${target.label}.")
        } finally {
            if (source !== bitmap) source.recycle()
        }
    }

    private fun onWhite(bitmap: Bitmap): Bitmap {
        val copy = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return copy
    }

    /**
     * One A4 page per photo (portrait or landscape like the photo), the photo fitted inside a small margin.
     * [onPage] gets the number of pages done.
     */
    suspend fun imagesToPdf(images: List<File>, out: File, onPage: (Int) -> Unit) {
        require(images.size <= Formats.MAX_PDF_PHOTOS)
        val document = PdfDocument()
        try {
            images.forEachIndexed { index, file ->
                coroutineContext.ensureActive()
                val decoded = decode(file, PDF_IMAGE_SIDE)
                // Half the memory per page (photos on paper do not need an alpha channel); white under transparency.
                val bitmap = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.RGB_565).also { copy ->
                    Canvas(copy).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(decoded, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                    decoded.recycle()
                }
                try {
                    val landscape = bitmap.width > bitmap.height
                    val pageW = if (landscape) A4_LONG else A4_SHORT
                    val pageH = if (landscape) A4_SHORT else A4_LONG
                    val page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
                    val boxW = pageW - 2 * PAGE_MARGIN
                    val boxH = pageH - 2 * PAGE_MARGIN
                    val scale = min(boxW / bitmap.width, boxH / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val left = (pageW - w) / 2
                    val top = (pageH - h) / 2
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
                onPage(index + 1)
            }
            out.outputStream().buffered().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    /**
     * Renders each page of [pdf] (at most [MAX_PDF_PAGES], longest side [PDF_PAGE_SIDE] px, white background) to
     * [outDir] as "<baseName> - page 001.jpg"… [onPage] gets (pages done, pages to do).
     */
    suspend fun pdfToImages(
        pdf: File,
        target: Target,
        quality: Int,
        outDir: File,
        baseName: String,
        onPage: (Int, Int) -> Unit,
    ): List<File> {
        val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.close()
            throw ConversionError("Ce PDF est protégé par un mot de passe : Gulp ne peut pas l'ouvrir.")
        } catch (e: Exception) {
            descriptor.close()
            throw ConversionError("Ce PDF est illisible ou abîmé.")
        }
        val outputs = ArrayList<File>()
        try {
            val count = min(renderer.pageCount, MAX_PDF_PAGES)
            if (count == 0) throw ConversionError("Ce PDF n'a aucune page.")
            val digits = max(3, count.toString().length)
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                renderer.openPage(index).use { page ->
                    // About 200 dpi, capped so the longest side stays under PDF_PAGE_SIDE.
                    val longest = max(page.width, page.height).coerceAtLeast(1)
                    val scale = min(200f / 72f, PDF_PAGE_SIDE.toFloat() / longest)
                    val w = max(1, (page.width * scale).roundToInt())
                    val h = max(1, (page.height * scale).roundToInt())
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val number = (index + 1).toString().padStart(digits, '0')
                        val file = File(outDir, Formats.pageName(baseName, number, target.ext))
                        encode(bitmap, target, quality, file)
                        outputs.add(file)
                    } finally {
                        bitmap.recycle()
                    }
                }
                onPage(index + 1, count)
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
        return outputs
    }

    /** Pages of [pdf] (to tell the user how many images will come), or null when unreadable. */
    fun pdfPageCount(pdf: File): Int? = runCatching {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    }.getOrNull()
}
