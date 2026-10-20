package com.powermaxv2.ytdownloader.core.colors

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The palette as a picture to keep or send: the photo on top, then one band per colour with its code, its
 * name and its share. 1080 px wide, white paper.
 */
object PaletteCard {
    private const val WIDTH = 1080
    private const val MARGIN = 60f
    private const val BAND = 150f

    fun outputName(photoName: String): String = "${photoName.substringBeforeLast('.').ifBlank { "photo" }}-palette.png"

    fun draw(photo: Bitmap, palette: List<Swatch>): Bitmap {
        val inner = WIDTH - 2 * MARGIN
        val photoHeight = (inner * photo.height / photo.width).coerceAtMost(inner * 1.1f)
        val height = (MARGIN + photoHeight + 40f + palette.size * BAND + MARGIN).toInt()
        val card = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(card)
        c.drawColor(0xFFFBF8F1.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // The photo, cropped from its centre when it is very tall.
        val dst = RectF(MARGIN, MARGIN, MARGIN + inner, MARGIN + photoHeight)
        val srcHeight = (photo.width * photoHeight / inner).toInt().coerceAtMost(photo.height)
        val top = (photo.height - srcHeight) / 2
        c.save()
        val clip = android.graphics.Path().apply { addRoundRect(dst, 36f, 36f, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(photo, Rect(0, top, photo.width, top + srcHeight), dst, paint)
        c.restore()

        val code = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 46f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = Typeface.DEFAULT }
        var y = MARGIN + photoHeight + 40f
        for (swatch in palette) {
            val band = RectF(MARGIN, y, MARGIN + inner, y + BAND - 18f)
            paint.color = 0xFF000000.toInt() or swatch.rgb
            c.drawRoundRect(band, 28f, 28f, paint)
            val ink = if (ColorMath.wantsWhiteText(swatch.rgb)) 0xFFFFFFFF.toInt() else 0xFF14110F.toInt()
            code.color = ink
            label.color = ink
            label.alpha = 210
            val mid = band.centerY()
            c.drawText(ColorMath.hex(swatch.rgb), band.left + 36f, mid - 4f, code)
            c.drawText(ColorMath.name(swatch.rgb), band.left + 36f, mid + 40f, label)
            val share = "${PaletteExtractor.percent(swatch.share)} %"
            c.drawText(share, band.right - 36f - code.measureText(share), mid + 16f, code)
            y += BAND
        }
        return card
    }
}
