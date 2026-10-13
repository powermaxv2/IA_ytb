package com.powermaxv2.ytdownloader.core.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/** The file being stamped. [width] × [height]: proportions of its first page as shown. */
data class WatermarkSource(
    val file: LocalFile,
    val pdf: Boolean,
    val pages: Int,
    val width: Int,
    val height: Int,
)

sealed interface WatermarkPhase {
    data object Idle : WatermarkPhase

    data object Reading : WatermarkPhase

    data class Unsupported(val message: String) : WatermarkPhase

    data object Ready : WatermarkPhase

    data class Working(val done: Int, val total: Int) : WatermarkPhase

    data class Done(val saved: SavedFile) : WatermarkPhase

    data class Failed(val message: String, val debug: String? = null) : WatermarkPhase
}

data class WatermarkUiState(
    val phase: WatermarkPhase = WatermarkPhase.Idle,
    val source: WatermarkSource? = null,
    /** The photo, or the first page of the PDF, small enough for the screen (null in screenshot tests). */
    val preview: Bitmap? = null,
    val style: WatermarkStyle = WatermarkStyle(),
    /** "13/10/2026": the date added after the text. */
    val date: String = "",
) {
    val label: String get() = Watermark.label(style, date)
}

/**
 * The Filigrane's state and work: the chosen photo or PDF is copied into a private work folder, shown, then stamped
 * into a new file saved in Galerie › Gulp (photo) or Téléchargements › Gulp (PDF). The original never changes.
 * One controller for the app ([shared]), so the work goes on when the user leaves the screen.
 */
class WatermarkController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(WatermarkUiState(date = Watermark.today()))
    val state: StateFlow<WatermarkUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    /** Work folder and copy of the current file; kept so the user can stamp again with other settings. */
    @Volatile private var dir: File? = null

    @Volatile private var copy: File? = null

    init {
        // PdfBox reads its glyph lists and standard fonts from the app's assets.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    val busy: Boolean get() = _state.value.phase is WatermarkPhase.Working

    /** Opens [uri] (a photo or a PDF). False while a file is being stamped. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = WatermarkPhase.Reading, source = null, preview = null, date = Watermark.today()) }
        readJob = scope.launch(Dispatchers.IO) {
            dropWork()
            val next: (WatermarkUiState) -> WatermarkUiState = try {
                val (source, preview) = read(uri)
                val ready: (WatermarkUiState) -> WatermarkUiState = { s -> s.copy(phase = WatermarkPhase.Ready, source = source, preview = preview) }
                ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dropWork()
                val message = when (e) {
                    is SecurityException -> "Android a refusé l'accès : choisis le fichier à nouveau."
                    is FileTooLarge, is UnsupportedSource, is ConversionError -> e.message ?: "Fichier illisible."
                    else -> "Impossible de lire ce fichier."
                }
                val refused: (WatermarkUiState) -> WatermarkUiState = { s -> s.copy(phase = WatermarkPhase.Unsupported(message)) }
                refused
            }
            if (generation.get() == token) _state.update(next)
        }
        return true
    }

    private suspend fun read(uri: Uri): Pair<WatermarkSource, Bitmap> {
        val info = FileIntake.describe(context, uri)
        val head = FileIntake.readHead(context, uri)
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.IMAGE && kind != FileKind.PDF) {
            throw ConversionError("« ${info.name} » n'est ni une photo ni un PDF. Le Filigrane prend les photos (JPG, PNG, WebP, HEIC) et les PDF.")
        }
        val pdf = kind == FileKind.PDF
        val work = WorkDir.create(context)
        dir = work
        val file = FileIntake.copyToWork(context, uri, maxBytes = if (pdf) MAX_PDF_BYTES else MAX_PHOTO_BYTES, dir = work)
        copy = file
        return if (pdf) readPdf(info, file) else readPhoto(info, file)
    }

    private fun readPhoto(info: LocalFile, file: File): Pair<WatermarkSource, Bitmap> {
        val preview = Images.decode(file, PREVIEW_SIDE)
        return WatermarkSource(info, pdf = false, pages = 1, width = preview.width, height = preview.height) to preview
    }

    private fun readPdf(info: LocalFile, file: File): Pair<WatermarkSource, Bitmap> {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: SecurityException) {
            fd.close()
            throw ConversionError(LOCKED)
        } catch (e: Exception) {
            fd.close()
            throw ConversionError("Ce PDF est illisible ou abîmé.")
        }
        try {
            val count = renderer.pageCount
            if (count == 0) throw ConversionError("Ce PDF n'a aucune page.")
            if (count > MAX_PAGES) throw ConversionError("$MAX_PAGES pages au plus par PDF.")
            renderer.openPage(0).use { page ->
                val scale = PREVIEW_SIDE.toFloat() / max(page.width, page.height).coerceAtLeast(1)
                val w = max(1, (page.width * scale).roundToInt())
                val h = max(1, (page.height * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return WatermarkSource(info, pdf = true, pages = count, width = page.width, height = page.height) to bitmap
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    fun setStyle(style: WatermarkStyle) {
        _state.update { if (it.phase is WatermarkPhase.Working) it else it.copy(style = style.clamped()) }
    }

    /** Back to the settings after a result or a failure (same file). */
    fun edit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = WatermarkPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is WatermarkPhase.Working) it.copy(phase = WatermarkPhase.Ready) else it }
    }

    /** Forgets the file (the style stays for the next one). */
    fun reset() {
        if (busy) return
        generation.incrementAndGet()
        readJob?.cancel()
        _state.update { WatermarkUiState(style = it.style, date = Watermark.today()) }
        scope.launch(Dispatchers.IO) { dropWork() }
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        val file = copy ?: return
        val work = dir ?: return
        if (busy || current.phase is WatermarkPhase.Reading) return
        val token = generation.incrementAndGet()
        val style = current.style
        val label = current.label
        _state.update { it.copy(phase = WatermarkPhase.Working(0, source.pages)) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val outDir = File(work, "out").apply { mkdirs() }
            var out: File? = null
            try {
                val saved = if (source.pdf) {
                    val target = FileIntake.uniqueFile(outDir, Watermark.outputName(source.file.name, "pdf"))
                    out = target
                    WatermarkStamper.stampPdf(file, target, label, style) { done, total ->
                        update(token) { s -> if (s.phase is WatermarkPhase.Working) s.copy(phase = WatermarkPhase.Working(done, total)) else s }
                    }
                    MediaExporter.export(context, target)
                } else {
                    val ext = Watermark.photoExtension(source.file.name, source.file.mime)
                    val target = FileIntake.uniqueFile(outDir, Watermark.outputName(source.file.name, ext))
                    out = target
                    stampPhoto(file, target, ext, label, style)
                    update(token) { s -> s.copy(phase = WatermarkPhase.Working(1, 1)) }
                    MediaExporter.export(context, target)
                }
                update(token) { it.copy(phase = WatermarkPhase.Done(saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidPasswordException) {
                fail(token, LOCKED)
            } catch (e: ConversionError) {
                fail(token, e.message ?: "Fichier illisible.", e.debug)
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'enregistrement : autorise le stockage et réessaie.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour ce fichier.", e.toString())
            } catch (e: Throwable) {
                fail(token, "Le filigrane n'a pas pu être ajouté.", e.toString())
            } finally {
                withContext(NonCancellable) { out?.delete() }
            }
        }
    }

    private fun stampPhoto(file: File, target: File, ext: String, label: String, style: WatermarkStyle) {
        val decoded = Images.decode(file)
        val bitmap = if (decoded.isMutable && decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
                ?: throw ConversionError("Pas assez de mémoire pour cette photo.")
        }
        try {
            WatermarkStamper.paint(bitmap, label, style)
            val ok = target.outputStream().use { stream ->
                if (ext == "png") {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                }
            }
            if (!ok) throw ConversionError("Impossible d'écrire la photo.")
        } finally {
            bitmap.recycle()
        }
    }

    private fun dropWork() {
        val folder = dir
        dir = null
        copy = null
        folder?.let { WorkDir.delete(context, it) }
    }

    private inline fun update(token: Int, crossinline change: (WatermarkUiState) -> WatermarkUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = WatermarkPhase.Failed(message, debug)) }
    }

    companion object {
        /** The app's one controller (made on first use, lives as long as the app). */
        val shared: WatermarkController by lazy { WatermarkController(App.instance, App.instance.scope) }

        const val MAX_PAGES = 500
        private const val MAX_PHOTO_BYTES: Long = 64L * 1024 * 1024
        private const val MAX_PDF_BYTES: Long = 200L * 1024 * 1024
        private const val PREVIEW_SIDE = 1400
        private const val JPEG_QUALITY = 93
        private const val LOCKED = "Ce PDF est protégé par un mot de passe : Gulp ne peut pas l'ouvrir."
    }
}
