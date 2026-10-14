package com.powermaxv2.ytdownloader.core.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Créateur de GIF's pictures, drawn on the phone: frames of the video for the preview, each photo laid out at the
 * GIF's size, and the caption (white letters outlined in black, at the bottom) as a transparent PNG FFmpeg lays over
 * the video. Photos are decoded through [com.powermaxv2.ytdownloader.core.convert.Images] (bounded, EXIF rotation).
 */
object GifFrames {
    /** Height of the frames shown in the preview. */
    const val PREVIEW_SIDE = 320

    /** A frame of the video near [us] microseconds, [height] px high; [exact] seeks the exact frame (slower). */
    fun frame(retriever: MediaMetadataRetriever, us: Long, height: Int, exact: Boolean): Bitmap? {
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(us, option, height * 3, height)
        } else {
            retriever.getFrameAtTime(us, option)
        } ?: return null
        if (raw.height <= height * 1.2f) return raw
        val w = max(1, (raw.width * height.toFloat() / raw.height).roundToInt())
        return Bitmap.createScaledBitmap(raw, w, height, true).also { if (it !== raw) raw.recycle() }
    }

    /** Draws the photo [photo] cropped to cover a [width] × [height] PNG [out], with [caption] at the bottom. */
    fun renderPhoto(photo: Bitmap, out: File, width: Int, height: Int, caption: String) {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(frame)
            canvas.drawColor(Color.BLACK)
            val c = GifPlan.cover(photo.width, photo.height, width.toFloat() / height)
            canvas.drawBitmap(
                photo,
                Rect(c.x, c.y, c.x + c.width, c.y + c.height),
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
            drawCaption(canvas, width, height, caption)
            writePng(frame, out)
        } finally {
            frame.recycle()
        }
    }

    /** The caption alone on a transparent [width] × [height] PNG [out]. */
    fun renderCaption(out: File, width: Int, height: Int, caption: String) {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawCaption(Canvas(frame), width, height, caption)
            writePng(frame, out)
        } finally {
            frame.recycle()
        }
    }

    /** White bold letters outlined in black, centred near the bottom, shrunk until they fit 92 % of the width. */
    fun drawCaption(canvas: Canvas, width: Int, height: Int, caption: String) {
        val text = GifPlan.cleanCaption(caption)
        if (text.isEmpty()) return
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            textSize = GifPlan.captionSize(width, height)
        }
        val room = width * 0.92f
        val measured = fill.measureText(text)
        if (measured > room) fill.textSize = max(8f, fill.textSize * room / measured)
        val stroke = Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, fill.textSize * 0.14f)
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val baseline = height - fill.textSize * 0.55f
        canvas.drawText(text, width / 2f, baseline, stroke)
        canvas.drawText(text, width / 2f, baseline, fill)
    }

    private fun writePng(bitmap: Bitmap, out: File) {
        FileOutputStream(out).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IllegalStateException("PNG")
        }
    }

    /** Thumbnail of a photo, reused from the Diaporama. */
    fun thumbnail(context: Context, uri: android.net.Uri): Bitmap? =
        com.powermaxv2.ytdownloader.core.slideshow.SlideFrames.thumbnail(context, uri)
}
