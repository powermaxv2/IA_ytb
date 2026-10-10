package com.powermaxv2.ytdownloader.core.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** The Android side of the Scanner's picture work: cut the page out, turn it, clean it, encode it. */
object ScanImages {
    /** The page's corners found in [photo] (see [DocumentDetector]), or null. */
    fun detect(photo: Bitmap): Quad? {
        val longest = max(photo.width, photo.height).coerceAtLeast(1)
        val k = DocumentDetector.WORK_SIDE.toFloat() / longest
        val w = max(8, (photo.width * k).roundToInt())
        val h = max(8, (photo.height * k).roundToInt())
        val small = Bitmap.createScaledBitmap(photo, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== photo) small.recycle()
        val gray = IntArray(pixels.size) { ScanFilters.luma(pixels[it]) }
        return DocumentDetector.detect(gray, w, h)
    }

    /**
     * The page cut out of [photo] along [quad] and straightened (perspective), turned by [rotation] degrees and
     * cleaned with [filter]; its longest side is at most [maxSide].
     */
    fun render(photo: Bitmap, quad: Quad, rotation: Int, filter: ScanFilter, maxSide: Int = ScanGeometry.MAX_OUTPUT_SIDE): Bitmap {
        val q = if (quad.isValid) quad else Quad.FULL
        val (w, h) = ScanGeometry.outputSize(q, photo.width, photo.height, maxSide)
        val src = q.points.flatMap { listOf(it.x * photo.width, it.y * photo.height) }.toFloatArray()
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) throw ConversionError("Ce cadre ne peut pas être redressé : déplacez les coins.")
        val turn = ScanGeometry.normalize(rotation)
        val (ow, oh) = ScanGeometry.rotated(w, h, turn)
        if (turn != 0) {
            matrix.postRotate(turn.toFloat())
            when (turn) {
                90 -> matrix.postTranslate(h.toFloat(), 0f)
                180 -> matrix.postTranslate(w.toFloat(), h.toFloat())
                270 -> matrix.postTranslate(0f, w.toFloat())
            }
        }
        val out = try {
            Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw ConversionError("Pas assez de mémoire pour cette page.")
        }
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        if (filter != ScanFilter.ORIGINAL) {
            val pixels = IntArray(ow * oh)
            out.getPixels(pixels, 0, ow, 0, 0, ow, oh)
            ScanFilters.apply(filter, pixels, ow, oh)
            out.setPixels(pixels, 0, ow, 0, 0, ow, oh)
        }
        return out
    }

    /** [bitmap] brought down to [w] × [h] (the same bitmap when it already is that size). */
    fun scaled(bitmap: Bitmap, w: Int, h: Int): Bitmap =
        if (bitmap.width == w && bitmap.height == h) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)

    /** [bitmap] scaled down so its longest side is at most [side]. */
    fun fit(bitmap: Bitmap, side: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= side) return bitmap
        val k = side.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * k).roundToInt()), max(1, (bitmap.height * k).roundToInt()), true)
    }

    fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)) throw ConversionError("Impossible d'écrire la page.")
        return out.toByteArray()
    }
}
