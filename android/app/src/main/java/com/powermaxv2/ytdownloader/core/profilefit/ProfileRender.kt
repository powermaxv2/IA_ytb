package com.powermaxv2.ytdownloader.core.profilefit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Draws the square profile photo: the backdrop, then the whole photo where [ProfileFit.placement] puts it. */
object ProfileRender {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    /** The photo's own backdrop pieces, computed once per photo (they do not depend on the options). */
    class Backdrops(val blurred: Bitmap, val color: Int)

    /** The tiny blurred cover of [photo] and its main colour. */
    fun backdrops(photo: Bitmap): Backdrops {
        val side = ProfileFit.BLUR_SIDE
        val small = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val cover = ProfileFit.cover(photo.width, photo.height, side)
        Canvas(small).drawBitmap(photo, null, RectF(cover.left, cover.top, cover.right, cover.bottom), paint)
        val pixels = IntArray(side * side)
        small.getPixels(pixels, 0, side, 0, 0, side, side)
        val color = ProfileFit.backdropColor(pixels)
        small.setPixels(ProfileFit.blur(pixels, side, side, ProfileFit.BLUR_RADIUS), 0, side, 0, 0, side, side)
        return Backdrops(small, color)
    }

    /** The [side] × [side] profile photo for [options]. */
    fun render(photo: Bitmap, backdrops: Backdrops, side: Int, options: FitOptions): Bitmap {
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        when (options.backdrop) {
            Backdrop.BLUR -> {
                // Stretched from 72 px with bilinear filtering: very soft. A light veil keeps the photo in front.
                canvas.drawBitmap(backdrops.blurred, null, RectF(0f, 0f, side.toFloat(), side.toFloat()), paint)
                canvas.drawColor(Color.argb(46, 0, 0, 0))
            }
            Backdrop.COLOR -> canvas.drawColor(backdrops.color)
            Backdrop.WHITE -> canvas.drawColor(Color.WHITE)
            Backdrop.BLACK -> canvas.drawColor(Color.BLACK)
        }
        val box = ProfileFit.placement(photo.width, photo.height, side, options.shape, options.margin)
        canvas.drawBitmap(photo, null, RectF(box.left, box.top, box.right, box.bottom), paint)
        return out
    }
}
