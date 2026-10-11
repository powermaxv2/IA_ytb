package com.powermaxv2.ytdownloader.core.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws stickers on the phone: square crop, magic-wand cut-out, shape, white outline, meme text, and the WebP / PNG
 * encodings WhatsApp wants. Every picture is [StickerRules.SIZE] px square.
 */
object StickerArt {
    private const val SIZE = StickerRules.SIZE

    /** The middle square of [source], [side] px, in a software ARGB bitmap (the one the pixels are read from). */
    fun square(source: Bitmap, side: Int = SIZE): Bitmap {
        val crop = min(source.width, source.height)
        val left = (source.width - crop) / 2
        val top = (source.height - crop) / 2
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        Canvas(out).drawBitmap(readable, Rect(left, top, left + crop, top + crop), Rect(0, 0, side, side), paint)
        if (readable !== source) readable.recycle()
        return out
    }

    /**
     * The sticker of [square] (from [square]) in [style], as [variant]: a new [StickerRules.SIZE] px bitmap with a
     * transparent background. [seeds] are extra magic-wand touches (0..1 of the picture).
     */
    fun render(square: Bitmap, style: StickerStyle, variant: StickerVariant = StickerVariant.NORMAL, seeds: List<Pair<Float, Float>> = emptyList()): Bitmap {
        val content = cutOut(square, style, seeds)
        val outline = style.outline.px
        val margin = StickerRules.margin(style)
        val inner = RectF(margin.toFloat(), margin.toFloat(), (SIZE - margin).toFloat(), (SIZE - margin).toFloat())

        // The picture inside its shape, on its own layer.
        val shaped = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(shaped)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val mirror = style.mirror != (variant == StickerVariant.MIRROR)
        if (mirror) {
            canvas.save()
            canvas.scale(-1f, 1f, SIZE / 2f, SIZE / 2f)
        }
        canvas.drawBitmap(content, null, inner, filter)
        if (mirror) canvas.restore()
        if (content !== square) content.recycle()
        shapePath(style.shape, inner)?.let { path ->
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                if (style.shape == StickerShape.STAR || style.shape == StickerShape.HEART) pathEffect = CornerPathEffect(inner.width() * 0.06f)
            }
            canvas.drawPath(path, mask)
        }

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val result = Canvas(out)
        if (variant == StickerVariant.TILT) {
            result.rotate(-8f, SIZE / 2f, SIZE / 2f)
            result.scale(0.9f, 0.9f, SIZE / 2f, SIZE / 2f)
        }
        if (outline > 0) {
            // White border: the silhouette stamped in white all around, then the picture on top.
            val white = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            val steps = 24
            for (ring in listOf(outline.toFloat(), outline * 0.5f)) {
                for (k in 0 until steps) {
                    val a = 2 * PI * k / steps
                    result.drawBitmap(shaped, (cos(a) * ring).toFloat(), (sin(a) * ring).toFloat(), white)
                }
            }
        }
        result.drawBitmap(shaped, 0f, 0f, filter)
        shaped.recycle()
        // The text stays level and readable, even on a tilted or mirrored sticker.
        if (style.hasText && variant != StickerVariant.NO_TEXT) drawText(Canvas(out), style.text.trim(), style.textOnTop)
        return out
    }

    /** [square] with its plain background removed when the style asks for it (a copy), else [square] itself. */
    private fun cutOut(square: Bitmap, style: StickerStyle, seeds: List<Pair<Float, Float>>): Bitmap {
        if (!style.cutout && seeds.isEmpty()) return square
        val w = square.width
        val h = square.height
        val pixels = IntArray(w * h)
        square.getPixels(pixels, 0, w, 0, 0, w, h)
        val points = seeds.map { (x, y) -> (x * (w - 1)).roundToInt() to (y * (h - 1)).roundToInt() }
        val removed = Cutout.mask(pixels, w, h, style.tolerance, fromEdges = style.cutout, seeds = points)
        // A wand that would take (almost) everything found no subject: the picture stays as it is.
        if (Cutout.share(removed) > 0.97f) return square
        Cutout.apply(pixels, w, h, removed)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** The outline of [shape] in [r]; null = the whole square. */
    fun shapePath(shape: StickerShape, r: RectF): Path? {
        val w = r.width()
        val h = r.height()
        return when (shape) {
            StickerShape.SQUARE -> null
            StickerShape.ROUNDED -> Path().apply { addRoundRect(r, w * 0.2f, h * 0.2f, Path.Direction.CW) }
            StickerShape.ROUND -> Path().apply { addOval(r, Path.Direction.CW) }
            StickerShape.HEART -> Path().apply {
                val x = r.left
                val y = r.top
                moveTo(x + w * 0.5f, y + h * 0.95f)
                cubicTo(x + w * 0.1f, y + h * 0.68f, x - w * 0.02f, y + h * 0.38f, x + w * 0.08f, y + h * 0.2f)
                cubicTo(x + w * 0.2f, y + h * 0.0f, x + w * 0.44f, y + h * 0.02f, x + w * 0.5f, y + h * 0.22f)
                cubicTo(x + w * 0.56f, y + h * 0.02f, x + w * 0.8f, y + h * 0.0f, x + w * 0.92f, y + h * 0.2f)
                cubicTo(x + w * 1.02f, y + h * 0.38f, x + w * 0.9f, y + h * 0.68f, x + w * 0.5f, y + h * 0.95f)
                close()
            }
            StickerShape.STAR -> Path().apply {
                val cx = r.centerX()
                val cy = r.centerY() + h * 0.04f
                val outer = w * 0.52f
                val innerR = outer * 0.5f
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) outer else innerR
                    val a = -PI / 2 + i * PI / 5
                    val px = cx + (cos(a) * radius).toFloat()
                    val py = cy + (sin(a) * radius).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
        }
    }

    /** Meme text: bold white letters with a black outline, on one or two lines, as big as the width allows. */
    private fun drawText(canvas: Canvas, text: String, top: Boolean) {
        val lines = splitLines(text)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val maxWidth = SIZE * 0.9f
        var size = 92f
        paint.textSize = size
        while (size > 30f && lines.any { paint.measureText(it) > maxWidth }) {
            size -= 4f
            paint.textSize = size
        }
        val lineHeight = size * 1.08f
        val block = lineHeight * lines.size
        val firstBaseline = if (top) 20f + size * 0.92f else SIZE - 22f - block + size * 0.92f
        lines.forEachIndexed { i, line ->
            val y = firstBaseline + i * lineHeight
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(4f, size * 0.17f)
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.BLACK
            canvas.drawText(line, SIZE / 2f, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawText(line, SIZE / 2f, y, paint)
        }
    }

    /** One line, or two balanced lines for a longer text (split at the space nearest the middle). */
    internal fun splitLines(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= 14 || ' ' !in clean) return listOf(clean)
        val middle = clean.length / 2
        val spaces = clean.indices.filter { clean[it] == ' ' }
        val cut = spaces.minByOrNull { kotlin.math.abs(it - middle) } ?: return listOf(clean)
        return listOf(clean.substring(0, cut), clean.substring(cut + 1))
    }

    /** A still sticker as WebP, at the best quality that fits WhatsApp's limit. */
    fun encodeStill(bitmap: Bitmap): ByteArray {
        var last = ByteArray(0)
        for (quality in StickerRules.STATIC_QUALITIES) {
            last = webp(bitmap, quality)
            if (StickerRules.fits(last.size, animated = false)) return last
        }
        return last
    }

    /** One still WebP frame (assembled later by [AnimatedWebp]). */
    fun webp(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        check(bitmap.compress(format, quality, out)) { "Encodage WebP impossible" }
        return out.toByteArray()
    }

    /** The tray icon WhatsApp shows for the pack: the first sticker, 96 px, PNG. */
    fun tray(sticker: Bitmap): ByteArray {
        val small = Bitmap.createScaledBitmap(sticker, StickerRules.TRAY_SIZE, StickerRules.TRAY_SIZE, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (small !== sticker) small.recycle()
        return out.toByteArray()
    }

    /** [bitmap] at [side] × [side] (itself when it already is). */
    fun scaled(bitmap: Bitmap, side: Int): Bitmap {
        if (bitmap.width == side && bitmap.height == side) return bitmap
        val m = Matrix().apply { setScale(side.toFloat() / bitmap.width, side.toFloat() / bitmap.height) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
