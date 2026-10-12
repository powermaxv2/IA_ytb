package com.powermaxv2.ytdownloader.core.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

// The discussion printed as a PDF (A4), the way the reader shows it: a header with its name, one pill per day,
// bubbles on the left and on the right ("moi" in WhatsApp green), names in groups, photo thumbnails of an export
// "with media", and the page number at the bottom. Drawn with Android's own PdfDocument, on the phone.

object ChatPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val FOOTER = 24f
    private const val BUBBLE_MAX = 360f
    private const val PAD_X = 9f
    private const val PAD_Y = 6f
    private const val GAP = 3f
    private const val RUN_GAP = 7f
    private const val PHOTO_W = 180f
    private const val PHOTO_MAX_H = 240f

    /** A bubble longer than this many lines is cut with "…" (a whole page per message reads badly). */
    private const val MAX_LINES = 60

    private val MINE = Color.rgb(0xD9, 0xFD, 0xD3)
    private val THEIRS = Color.rgb(0xF1, 0xF2, 0xF4)
    private val INK = Color.rgb(0x11, 0x1B, 0x21)
    private val GREY = Color.rgb(0x66, 0x77, 0x81)
    private val HIGHLIGHT = Color.rgb(0x25, 0xD3, 0x66)

    /** Colours of the names in a group, picked by name so that a person keeps theirs. */
    private val NAMES = intArrayOf(
        Color.rgb(0xE5, 0x39, 0x35), Color.rgb(0x1E, 0x88, 0xE5), Color.rgb(0x8E, 0x24, 0xAA), Color.rgb(0x00, 0x89, 0x7B),
        Color.rgb(0xF4, 0x51, 0x1E), Color.rgb(0x39, 0x49, 0xAB), Color.rgb(0xC2, 0x18, 0x5B), Color.rgb(0x43, 0xA0, 0x47),
    )

    fun nameColor(author: String): Int = NAMES[(author.hashCode() and 0x7fffffff) % NAMES.size]

    /**
     * Writes [chat] seen by [me] into Download/Gulp and returns it. [progress] goes from 0 to 1.
     * Cancellable between messages; the half-written file is then deleted.
     */
    suspend fun export(context: Context, chat: ReaderChat, me: String?, progress: (Float) -> Unit = {}): SavedFile = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "gulp-work/reader").apply { mkdirs() }
        val safe = chat.name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").take(60).trim().ifEmpty { "Discussion" }
        val file = File(dir, "Discussion $safe.pdf")
        try {
            write(chat, me, file, progress)
            MediaExporter.export(context, file)
        } finally {
            file.delete()
        }
    }

    private suspend fun write(chat: ReaderChat, me: String?, out: File, progress: (Float) -> Unit) {
        val rows = ChatReader.rows(chat.messages, me)
        val doc = PdfDocument()
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = INK }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = GREY }
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = GREY; typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = INK; typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        val bottom = PAGE_H - MARGIN - FOOTER

        fun finishPage() {
            val p = page ?: return
            val c = p.canvas
            val footer = "${chat.name} · page $pageNumber · Gulp"
            c.drawText(footer, (PAGE_W - small.measureText(footer)) / 2f, PAGE_H - MARGIN / 2f, small)
            doc.finishPage(p)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNumber++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        newPage()
        // Header: name, line of figures, and a green rule.
        canvas!!.drawText(chat.name.take(70), MARGIN, y + 18f, title)
        y += 30f
        canvas!!.drawText(ChatReader.summary(chat.messages), MARGIN, y + 10f, label)
        y += 18f
        fill.color = HIGHLIGHT
        canvas!!.drawRect(MARGIN, y, MARGIN + 40f, y + 3f, fill)
        y += 16f

        var lastMine: Boolean? = null
        rows.forEachIndexed { r, row ->
            if (r % 200 == 0) {
                coroutineContext.ensureActive()
                progress(r / rows.size.toFloat())
            }
            when (row) {
                is ReaderRow.Day -> {
                    val w = label.measureText(row.label) + 20f
                    if (y + 30f > bottom) newPage()
                    y += 8f
                    val left = (PAGE_W - w) / 2f
                    fill.color = Color.rgb(0xE7, 0xF3, 0xFF)
                    canvas!!.drawRoundRect(RectF(left, y, left + w, y + 17f), 8.5f, 8.5f, fill)
                    canvas!!.drawText(row.label, left + 10f, y + 12f, label)
                    y += 25f
                    lastMine = null
                }
                is ReaderRow.Message -> {
                    val m = row.message
                    val media = ChatReader.mediaLabel(m.kind)
                    val body = ChatReader.body(m).ifEmpty { if (media == null) " " else "" }
                    val photo = m.file?.let { chat.photos[it] }?.let { decode(it) }
                    val inner = BUBBLE_MAX - 2 * PAD_X
                    val textW = if (body.isEmpty()) 0f else minOf(inner, maxLineWidth(body, text))
                    val layout = if (body.isEmpty()) null else layout(body, text, textW.coerceAtLeast(1f).toInt())
                    val authorH = if (row.showAuthor) 12f else 0f
                    val mediaH = if (media != null) 13f else 0f
                    // Tall photos are kept to [PHOTO_MAX_H], narrower.
                    val photoDrawH = photo?.let { minOf(PHOTO_W * it.height / it.width.toFloat(), PHOTO_MAX_H) } ?: 0f
                    val photoDrawW = photo?.let { photoDrawH * it.width / it.height.toFloat() } ?: 0f
                    val photoH = if (photo != null) photoDrawH + 4f else 0f
                    val timeLine = row.time + if (m.edited) " · modifié" else ""
                    val contentW = maxOf(
                        textW,
                        small.measureText(timeLine) + 4f,
                        if (row.showAuthor) name.measureText(m.author) else 0f,
                        if (media != null) label.measureText(media) else 0f,
                        photoDrawW,
                    ).coerceAtMost(inner)
                    val h = PAD_Y * 2 + authorH + mediaH + photoH + (layout?.height?.toFloat() ?: 0f) + 10f
                    val gap = if (lastMine == row.mine) GAP else RUN_GAP
                    if (y + gap + h > bottom) newPage() else y += gap
                    val w = contentW + 2 * PAD_X
                    val left = if (row.mine) PAGE_W - MARGIN - w else MARGIN
                    val c = canvas!!
                    fill.color = if (row.mine) MINE else THEIRS
                    c.drawRoundRect(RectF(left, y, left + w, y + h), 8f, 8f, fill)
                    var cy = y + PAD_Y
                    if (row.showAuthor) {
                        name.color = nameColor(m.author)
                        c.drawText(m.author, left + PAD_X, cy + 9f, name)
                        cy += authorH
                    }
                    if (media != null) {
                        c.drawText(media, left + PAD_X, cy + 10f, label)
                        cy += mediaH
                    }
                    if (photo != null) {
                        c.drawBitmap(photo, null, RectF(left + PAD_X, cy, left + PAD_X + photoDrawW, cy + photoDrawH), null)
                        photo.recycle()
                        cy += photoH
                    }
                    if (layout != null) {
                        c.save()
                        c.translate(left + PAD_X, cy)
                        layout.draw(c)
                        c.restore()
                        cy += layout.height
                    }
                    c.drawText(timeLine, left + w - PAD_X - small.measureText(timeLine), y + h - PAD_Y + 1f, small)
                    y += h
                    lastMine = row.mine
                }
            }
        }
        finishPage()
        progress(1f)
        try {
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Width of the longest line of [text] (a short message gets a short bubble). */
    private fun maxLineWidth(text: String, paint: TextPaint): Float =
        text.lineSequence().maxOfOrNull { paint.measureText(it) }?.plus(1f) ?: 0f

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(MAX_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        .getOrNull()
        ?.takeIf { it.width > 0 && it.height > 0 }
}
