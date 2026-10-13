package com.powermaxv2.ytdownloader.core.signpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import java.io.IOException

/** The user's signature as saved: its picture (transparent background) and its shape. */
data class SavedSignature(val file: File, val width: Int, val height: Int, val stamp: Long) {
    val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

/**
 * Keeps the drawn signature in the app's private files (`files/signature/signature.png`): drawn once, it is there for
 * every PDF until the user draws another one or deletes it. Nothing leaves the phone.
 */
class SignatureStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, DIR)
    private val file = File(dir, NAME)

    /** The saved signature, or null when none was drawn yet (or its file is unreadable). */
    fun current(): SavedSignature? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return SavedSignature(file, bounds.outWidth, bounds.outHeight, file.lastModified())
    }

    /** The saved picture, decoded (null when none). Blocking. */
    fun load(): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    /**
     * Draws [strokes] (pad fractions, the pad [padAspect] wide for 1 high) in [ink], trimmed to the ink, into a
     * transparent PNG that replaces the saved one. Blocking.
     */
    fun save(strokes: List<List<SignPoint>>, padAspect: Float, ink: Int): SavedSignature {
        val clean = strokes.map { SignPlan.simplify(it) }.filter { it.isNotEmpty() }
        val trim = SignPlan.trim(clean, padAspect, STROKE, MAX_SIDE) ?: throw IOException("Aucun trait à enregistrer.")
        val bitmap = Bitmap.createBitmap(trim.width, trim.height, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bitmap), clean, trim, ink)
            dir.mkdirs()
            val tmp = File(dir, "$NAME.tmp")
            tmp.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Signature non enregistrée.")
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("Signature non enregistrée.")
            }
        } finally {
            bitmap.recycle()
        }
        return current() ?: throw IOException("Signature non enregistrée.")
    }

    fun delete() {
        file.delete()
    }

    companion object {
        private const val DIR = "signature"
        private const val NAME = "signature.png"

        /** Stroke width, as a fraction of the pad's height. */
        const val STROKE = 0.022f

        /** Longest side of the saved picture: sharp on a printed page, light in the PDF. */
        const val MAX_SIDE = 1200

        /** The two inks offered: a black pen and a blue one. */
        const val INK_BLACK = 0xFF16181D.toInt()
        const val INK_BLUE = 0xFF1C3FAA.toInt()

        /** Draws [strokes] smoothed (curves through the midpoints) with round ends, as the pad shows them. */
        fun draw(canvas: Canvas, strokes: List<List<SignPoint>>, trim: TrimmedInk, ink: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = trim.strokeWidth
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
            strokes.forEach { stroke ->
                val points = stroke.map(trim::map)
                if (points.size == 1) {
                    canvas.drawCircle(points[0].x, points[0].y, trim.strokeWidth / 2f, dot)
                    return@forEach
                }
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.lastIndex) {
                        val p = points[i]
                        val q = points[i + 1]
                        quadTo(p.x, p.y, (p.x + q.x) / 2f, (p.y + q.y) / 2f)
                    }
                    lineTo(points.last().x, points.last().y)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
