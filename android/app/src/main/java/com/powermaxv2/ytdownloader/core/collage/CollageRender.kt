package com.powermaxv2.ytdownloader.core.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.powermaxv2.ytdownloader.core.profilefit.ProfileFit

/** Draws the collage picture with [Collage.cells]: background, then each photo cropped to fill its cell. */
object CollageRender {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    /** Main colour of [photo] (for [CollageBackground.COLOR]), from a 48 px copy. */
    fun mainColor(photo: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(photo, SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== photo) small.recycle()
        return ProfileFit.backdropColor(pixels)
    }

    /** The collage with its long side at [longSide], [photos] laid out in this order, [colors] their main colours. */
    fun render(photos: List<Bitmap>, colors: List<Int>, options: CollageOptions, longSide: Int): Bitmap {
        val (width, height) = Collage.size(options.format, longSide)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Collage.backgroundColor(options.background, Collage.mix(colors)))
        val radius = Collage.radius(options, width, height)
        val scatter = options.layout == CollageLayout.SCATTER
        Collage.cells(photos.size, options, width, height).forEach { cell ->
            val photo = photos[cell.index]
            canvas.save()
            canvas.rotate(cell.rotation, cell.centerX, cell.centerY)
            val target = if (scatter) {
                drawPrint(canvas, cell, radius, width, height)
                Collage.printPhoto(cell)
            } else {
                cell
            }
            val crop = Collage.crop(photo.width, photo.height, target.width, target.height)
            val dst = RectF(target.left, target.top, target.right, target.bottom)
            if (radius > 0f && !scatter) {
                canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
            }
            canvas.drawBitmap(photo, Rect(crop.left, crop.top, crop.right, crop.bottom), dst, paint)
            canvas.restore()
        }
        return out
    }

    /** The white print under a pêle-mêle photo, with a soft shadow. */
    private fun drawPrint(canvas: Canvas, cell: CollageCell, radius: Float, width: Int, height: Int) {
        val rect = RectF(cell.left, cell.top, cell.right, cell.bottom)
        val shadow = minOf(width, height) * 0.006f
        val r = radius * 0.5f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(RectF(rect.left + shadow, rect.top + shadow * 1.6f, rect.right + shadow, rect.bottom + shadow * 1.6f), r, r, p)
        p.color = Color.WHITE
        canvas.drawRoundRect(rect, r, r, p)
    }

    private const val SAMPLE = 48
}
