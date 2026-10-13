package com.powermaxv2.ytdownloader.core.mylink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import com.powermaxv2.ytdownloader.core.qr.QrImage

// Android side of Mon lien WhatsApp: the picture that is saved or shared. The code itself is drawn by the QR code
// tool's QrImage (same rounded eyes and Gulp logo); this only frames it as a card to print or post.

object MyLinkImage {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF111B21.toInt()
    private const val GREY = 0xFF667781.toInt()
    /** WhatsApp's green, for the band and the frame around the code. */
    private const val GREEN = 0xFF25D366.toInt()
    private const val GREEN_DARK = 0xFF128C7E.toInt()
    private const val BUBBLE = 0xFFD9FDD3.toInt()

    /** The picture for [ready] in [format], or null when its link is too long for a code. */
    fun render(ready: MyLink.Ready, message: String, format: MyImageFormat): Bitmap? {
        val matrix = QrCodec.encode(ready.link) ?: return null
        return when (format) {
            MyImageFormat.QR -> QrImage.render(matrix, 1200)
            MyImageFormat.CARD -> card(QrImage.render(matrix, 760), ready, MyLinks.cleanMessage(message))
        }
    }

    /** A 1080 × 1350 card (the size Instagram and WhatsApp statuses show whole): title, number, code, message. */
    private fun card(code: Bitmap, ready: MyLink.Ready, message: String): Bitmap {
        val w = 1080
        val h = 1350
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Green band with the title.
        paint.color = GREEN_DARK
        canvas.drawRect(0f, 0f, w.toFloat(), 230f, paint)
        paint.color = GREEN
        canvas.drawRoundRect(RectF(-60f, 170f, w + 60f, 290f), 120f, 120f, paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 68f
        }
        canvas.drawText("Écris-moi sur WhatsApp", w / 2f, 150f, text)
        text.textSize = 54f
        canvas.drawText(ready.pretty, w / 2f, 252f, text)

        // The code in a rounded green frame.
        val side = 700f
        val left = (w - side) / 2f
        val top = 350f
        paint.color = GREEN
        canvas.drawRoundRect(RectF(left - 16f, top - 16f, left + side + 16f, top + side + 16f), 56f, 56f, paint)
        paint.color = WHITE
        canvas.drawRoundRect(RectF(left - 4f, top - 4f, left + side + 4f, top + side + 4f), 46f, 46f, paint)
        canvas.drawBitmap(code, null, RectF(left + 10f, top + 10f, left + side - 10f, top + side - 10f), Paint(Paint.FILTER_BITMAP_FLAG))
        code.recycle()

        // The message people will find already written, as a sent bubble, or the hint to scan.
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 38f
            color = GREY
        }
        var y = top + side + 92f
        if (message.isNotEmpty()) {
            val line = ellipsize(message.replace('\n', ' '), small, w - 260f)
            val width = small.measureText(line) + 64f
            paint.color = BUBBLE
            canvas.drawRoundRect(RectF((w - width) / 2f, y - 52f, (w + width) / 2f, y + 22f), 30f, 30f, paint)
            small.color = INK
            canvas.drawText(line, w / 2f, y, small)
            small.color = GREY
            y += 90f
        }
        canvas.drawText("Scanne avec l'appareil photo pour m'écrire", w / 2f, y, small)
        small.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        small.color = GREEN_DARK
        small.textSize = 40f
        canvas.drawText(MyLinks.shortLink(ready.digits), w / 2f, minOf(y + 70f, h - 40f), small)
        return bitmap
    }

    /** [text] cut with "…" so it fits in [max] pixels. */
    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > max) end--
        if (text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end).trimEnd() + "…"
    }
}
