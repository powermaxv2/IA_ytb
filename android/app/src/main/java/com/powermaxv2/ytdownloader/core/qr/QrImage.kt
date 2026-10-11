package com.powermaxv2.ytdownloader.core.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Android side of the QR code tool: the PNG of a created code, and reading the code in a chosen picture.

object QrImage {
    /** Gulp red → orange, the colours of the logo in the middle of a created code. */
    private const val LOGO_START = 0xFFFF3B30.toInt()
    private const val LOGO_END = 0xFFFF8A1F.toInt()

    /**
     * [matrix] drawn black on white with a 4-module quiet zone (scanners need it), rounded finder eyes and the Gulp
     * logo in the middle when the code is strong enough for it. About [px] pixels wide.
     */
    fun render(matrix: QrMatrix, px: Int = 1200): Bitmap {
        val quiet = 4
        val total = matrix.size + quiet * 2
        val cell = (px / total).coerceAtLeast(4).toFloat()
        val side = (cell * total).toInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() }
        val hole = matrix.logoSpan()
        val o = quiet * cell
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y] || matrix.isFinder(x, y)) continue
            if (hole != null && x in hole && y in hole) continue
            canvas.drawRect(o + x * cell, o + y * cell, o + (x + 1) * cell, o + (y + 1) * cell, ink)
        }
        // Finder eyes: a rounded ring of 7 modules and a rounded 3-module pupil.
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        for ((fx, fy) in listOf(0 to 0, matrix.size - 7 to 0, 0 to matrix.size - 7)) {
            val l = o + fx * cell
            val t = o + fy * cell
            canvas.drawRoundRect(RectF(l, t, l + 7 * cell, t + 7 * cell), cell * 2f, cell * 2f, ink)
            canvas.drawRoundRect(RectF(l + cell, t + cell, l + 6 * cell, t + 6 * cell), cell * 1.4f, cell * 1.4f, white)
            canvas.drawRoundRect(RectF(l + 2 * cell, t + 2 * cell, l + 5 * cell, t + 5 * cell), cell, cell, ink)
        }
        hole?.let { span -> drawLogo(canvas, o + span.first * cell, o + (span.last + 1) * cell) }
        return bitmap
    }

    /** The Gulp mark (rounded square, chevron, smile) between [from] and [to] on both axes. */
    private fun drawLogo(canvas: Canvas, from: Float, to: Float) {
        val pad = (to - from) * 0.08f
        val l = from + pad
        val r = to - pad
        val s = r - l
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(l, l, r, r, LOGO_START, LOGO_END, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(l, l, r, r), s * 0.26f, s * 0.26f, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = s * 0.11f
        }
        val c = l + s / 2
        canvas.drawPath(Path().apply {
            moveTo(c - s * 0.2f, c - s * 0.18f)
            lineTo(c, c + s * 0.04f)
            lineTo(c + s * 0.2f, c - s * 0.18f)
        }, stroke)
        stroke.strokeWidth = s * 0.09f
        canvas.drawPath(Path().apply {
            moveTo(c - s * 0.22f, c + s * 0.15f)
            quadTo(c, c + s * 0.34f, c + s * 0.22f, c + s * 0.15f)
        }, stroke)
    }

    /** Writes [bitmap] as PNG to [dir]/[name] (replacing it) and returns the file. */
    suspend fun writePng(bitmap: Bitmap, dir: File, name: String): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    /**
     * Reads the code in the picture at [uri]: decoded at about 1600 px on its long side (big enough for a small
     * code in a photo, small enough for memory), then retried at 800 px (a huge code is easier to see smaller).
     */
    suspend fun decode(context: Context, uri: Uri): ScanResult? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return@withContext null
        for (target in listOf(1600, 800)) {
            var sample = 1
            while (longSide / (sample * 2) >= target) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: continue
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val result = QrCodec.decodePicture(pixels, bitmap.width, bitmap.height)
            bitmap.recycle()
            if (result != null) return@withContext result
            if (longSide < 1200) break
        }
        null
    }
}
