package com.powermaxv2.ytdownloader.core.slideshow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.Size
import com.powermaxv2.ytdownloader.core.convert.Images
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Diaporama's pictures, drawn on the phone: thumbnails for the screen, and each photo laid out at the video's
 * size (cropped, or whole on its own blurred backdrop) as a JPEG FFmpeg reads. Decoding goes through [Images]
 * (bounded size, EXIF rotation), so HEIC and hostile files are handled like everywhere else in Gulp.
 */
object SlideFrames {
    const val THUMB_SIDE = 320

    /** A small picture of [uri] for the list and the live preview, or null when the phone cannot read it. */
    fun thumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(THUMB_SIDE, THUMB_SIDE), null)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > THUMB_SIDE) {
                    val scale = THUMB_SIDE.toFloat() / longest
                    decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    /**
     * Draws the photo [source] (a work copy) into a [width] × [height] JPEG [out]. FILL crops it to cover the frame;
     * FIT shows all of it over a blurred, darkened, enlarged copy of itself (no black bars).
     */
    fun render(source: File, out: File, width: Int, height: Int, fit: SlideFit) {
        val photo = Images.decode(source, maxSide = minOf(Images.MAX_SIDE, 2 * max(width, height)))
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(frame)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            if (fit == SlideFit.FIT) drawBackdrop(canvas, photo, width, height)
            val p = SlidePlan.placement(photo.width, photo.height, width, height, fit)
            canvas.drawBitmap(photo, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), paint)
            FileOutputStream(out).use { stream ->
                if (!frame.compress(Bitmap.CompressFormat.JPEG, 92, stream)) throw IllegalStateException("JPEG")
            }
        } finally {
            frame.recycle()
            photo.recycle()
        }
    }

    /** The photo covering the frame, shrunk to a few dozen pixels and stretched back (a cheap, smooth blur), dimmed. */
    private fun drawBackdrop(canvas: Canvas, photo: Bitmap, width: Int, height: Int) {
        val smallW = max(4, width / 24)
        val smallH = max(4, height / 24)
        val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        try {
            val p = SlidePlan.placement(photo.width, photo.height, smallW, smallH, SlideFit.FILL)
            Canvas(small).drawBitmap(photo, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawBitmap(small, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(Color.argb(110, 0, 0, 0))
        } finally {
            small.recycle()
        }
    }
}
