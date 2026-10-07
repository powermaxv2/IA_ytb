package com.powermaxv2.ytdownloader.core.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.WorkDir
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A PDF that cannot be shown, with a French message. */
class PdfUnreadable(message: String) : IOException(message)

/**
 * An open PDF for the viewer: one page rendered at a time (PdfRenderer allows no more), off the main thread, into
 * a bitmap whose longest side is at most [MAX_SIDE]. [close] may come from any thread while a page renders: it
 * waits for that page, then frees the renderer, the descriptor and the work copy.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val workDir: File?,
    private val context: Context,
) : Closeable {
    private val lock = Any()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /**
     * Page [index] rendered [widthPx] wide (white background), its longest side capped at [MAX_SIDE];
     * null once closed. Blocking: call on a background thread.
     */
    fun render(index: Int, widthPx: Int): Bitmap? {
        synchronized(lock) {
            if (closed || index !in 0 until pageCount) return null
            return renderer.openPage(index).use { page ->
                val pw = page.width.coerceAtLeast(1)
                val ph = page.height.coerceAtLeast(1)
                val scale = min(widthPx.coerceAtLeast(1).toFloat() / pw, MAX_SIDE.toFloat() / max(pw, ph))
                val w = max(1, (pw * scale).roundToInt()).coerceAtMost(MAX_SIDE)
                val h = max(1, (ph * scale).roundToInt()).coerceAtMost(MAX_SIDE)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /** Width / height of page [index] (to size its slot before it renders). */
    fun aspect(index: Int): Float {
        synchronized(lock) {
            if (closed || index !in 0 until pageCount) return 0.707f
            return renderer.openPage(index).use { it.width.coerceAtLeast(1).toFloat() / it.height.coerceAtLeast(1) }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
            workDir?.let { WorkDir.delete(context, it) }
        }
    }

    companion object {
        /** Longest side of a rendered page. */
        const val MAX_SIDE = 2048

        /** Most bytes copied when the provider's descriptor cannot seek. */
        const val MAX_COPY_BYTES: Long = 200L * 1024 * 1024

        /**
         * Opens [uri] directly when its descriptor can seek, else from a capped copy in a work dir.
         * Throws [PdfUnreadable] with the reason. Blocking: call on a background thread.
         */
        fun open(context: Context, uri: Uri, checkpoint: (() -> Unit)? = null): PdfPages {
            FileIntake.requireContent(uri)
            val app = context.applicationContext
            val direct = runCatching { app.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (direct != null) {
                if (isSeekable(direct)) {
                    try {
                        return PdfPages(direct, PdfRenderer(direct), null, app)
                    } catch (e: SecurityException) {
                        runCatching { direct.close() }
                        throw PdfUnreadable(PASSWORD)
                    } catch (e: Exception) {
                        // A seekable file PdfRenderer refuses is damaged: a copy would not help.
                        runCatching { direct.close() }
                        throw PdfUnreadable(UNREADABLE)
                    }
                }
                // A pipe (some providers stream): PdfRenderer needs to seek, so read it from a copy.
                runCatching { direct.close() }
            }
            val dir = WorkDir.create(app)
            try {
                val copy = try {
                    FileIntake.copyToWork(app, uri, MAX_COPY_BYTES, dir, checkpoint)
                } catch (e: FileTooLarge) {
                    throw PdfUnreadable("PDF trop volumineux pour l'aperçu : 200 Mo au maximum.")
                }
                val fd = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    return PdfPages(fd, PdfRenderer(fd), dir, app)
                } catch (e: SecurityException) {
                    runCatching { fd.close() }
                    throw PdfUnreadable(PASSWORD)
                } catch (e: Exception) {
                    runCatching { fd.close() }
                    throw PdfUnreadable(UNREADABLE)
                }
            } catch (e: Throwable) {
                WorkDir.delete(app, dir)
                throw e
            }
        }

        /** Whether [fd] can seek (a real file), unlike a pipe. */
        private fun isSeekable(fd: ParcelFileDescriptor): Boolean = try {
            Os.lseek(fd.fileDescriptor, 0L, OsConstants.SEEK_CUR)
            true
        } catch (e: ErrnoException) {
            false
        }

        private const val UNREADABLE = "Ce PDF est illisible ou abîmé."

        private const val PASSWORD = "Ce PDF est protégé par un mot de passe : Gulp ne peut pas l'afficher."
    }
}
