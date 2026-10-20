package com.powermaxv2.ytdownloader.core.blur

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.FaceDetector
import kotlin.math.max
import kotlin.math.roundToInt

/** Android side of [BlurPlan]: zones applied to a Bitmap, and faces found with the phone's own detector (offline). */
object BlurRender {
    /** Longest side of the picture the face detector looks at (it is slow and needs RGB 565). */
    private const val FACE_SIDE = 900
    private const val MAX_FACES = 12
    private const val MIN_CONFIDENCE = 0.3f

    /** A copy of [photo] (longest side brought to [maxSide] when bigger) with every zone hidden. */
    fun render(photo: Bitmap, zones: List<BlurZone>, options: BlurOptions, maxSide: Int = Int.MAX_VALUE): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / max(photo.width, photo.height))
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(photo, (photo.width * scale).roundToInt().coerceAtLeast(1), (photo.height * scale).roundToInt().coerceAtLeast(1), true)
        } else {
            photo.copy(Bitmap.Config.ARGB_8888, true)
        }
        val bitmap = if (out.isMutable && out.config == Bitmap.Config.ARGB_8888) out else out.copy(Bitmap.Config.ARGB_8888, true).also { out.recycle() }
        val w = bitmap.width
        val h = bitmap.height
        // Only the zones' pixels go through arrays: a 12 MP photo is never copied whole.
        for (zone in zones) {
            val b = BlurPlan.box(zone, w, h)
            val region = IntArray(b.width * b.height)
            bitmap.getPixels(region, 0, b.width, b.left, b.top, b.width, b.height)
            BlurPlan.hideRegion(region, b.width, b.height, options.effect, BlurPlan.cell(options.strength, w, h, b), zone.shape)
            bitmap.setPixels(region, 0, b.width, b.left, b.top, b.width, b.height)
        }
        return bitmap
    }

    /** The faces of [photo] as oval zones (ids from [firstId]); empty when none is found. */
    fun findFaces(photo: Bitmap, firstId: Int): List<BlurZone> {
        val scale = minOf(1f, FACE_SIDE.toFloat() / max(photo.width, photo.height))
        // FaceDetector wants an even width and RGB 565.
        val w = ((photo.width * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
        val h = (photo.height * scale).roundToInt().coerceAtLeast(2)
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(small).drawBitmap(photo, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val count = try {
            FaceDetector(w, h, MAX_FACES).findFaces(small, faces)
        } finally {
            small.recycle()
        }
        val mid = android.graphics.PointF()
        return faces.take(count).filterNotNull()
            .filter { it.confidence() >= MIN_CONFIDENCE }
            .mapIndexed { i, face ->
                face.getMidPoint(mid)
                BlurPlan.faceZone(firstId + i, mid.x, mid.y, face.eyesDistance(), w, h)
            }
    }
}
