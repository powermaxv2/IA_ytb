package com.powermaxv2.ytdownloader.core.pdfimages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.convert.Target
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The opened PDF and the size of each of its pages. */
data class PdfImagesSource(val file: LocalFile, val pages: List<PdfPageInfo>) {
    val count: Int get() = pages.size
}

sealed interface PdfImagesPhase {
    data object Idle : PdfImagesPhase

    data object Reading : PdfImagesPhase

    data class Unsupported(val message: String) : PdfImagesPhase

    data object Ready : PdfImagesPhase

    /** [done] of [total] pages handled; [page] (from 1) is the one being drawn or searched. */
    data class Working(val done: Int, val total: Int, val page: Int) : PdfImagesPhase

    /** [saved]: the pictures (gallery) or the one ZIP; [pictures] how many pictures were made. */
    data class Done(val saved: List<SavedFile>, val pictures: Int, val zip: Boolean) : PdfImagesPhase

    /** "Images contenues" found nothing in the chosen pages. */
    data object NothingInside : PdfImagesPhase

    data class Failed(val message: String, val debug: String? = null) : PdfImagesPhase
}

data class PdfImagesUiState(
    val phase: PdfImagesPhase = PdfImagesPhase.Idle,
    val source: PdfImagesSource? = null,
    /** Chosen pages, numbered from 1. */
    val selected: Set<Int> = emptySet(),
    /** What the "Pages" field shows: written by the user, or the selection written short. */
    val pagesText: String = "",
    /** The "Pages" field holds something that is not a page of this PDF. */
    val pagesError: Boolean = false,
    val mode: ImagesMode = ImagesMode.PAGES,
    val format: ImagesFormat = ImagesFormat.JPG,
    val sharpness: Sharpness = Sharpness.STANDARD,
    /** Everything in one ZIP (Téléchargements) instead of one picture each in the gallery. */
    val zip: Boolean = false,
    /** The user flipped the ZIP switch: Gulp stops choosing it from the number of pages. */
    val zipChosen: Boolean = false,
    /** Up to three of the pictures made, small, for the result card (empty in screenshot tests). */
    val covers: List<Bitmap> = emptyList(),
) {
    /** Size in pixels of the first chosen page at the chosen sharpness (shown under the chips). */
    val firstSize: Pair<Int, Int>?
        get() {
            val page = source?.pages?.getOrNull((selected.minOrNull() ?: 1) - 1) ?: return null
            return PdfImagesPlan.renderSize(page.widthPt, page.heightPt, sharpness.dpi)
        }
}

/**
 * PDF en images: the chosen PDF is copied into a private work folder; its pages are shown as thumbnails to pick from;
 * then each chosen page is drawn as a JPG / PNG (or the pictures inside them are taken out), saved in Galerie › Gulp
 * or in one ZIP in Téléchargements › Gulp. One controller for the app ([shared]): the work goes on off-screen.
 */
class PdfImagesController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PdfImagesUiState())
    val state: StateFlow<PdfImagesUiState> = _state.asStateFlow()

    /** Thumbnails of the first [MAX_THUMBS] pages, by page number (from 1), drawn one after the other. */
    private val _thumbs = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<Int, Bitmap>> = _thumbs.asStateFlow()

    private var readJob: Job? = null
    private var thumbJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    /** PdfRenderer draws one page at a time: thumbnails and the pictures never draw together. */
    private val renderLock = Mutex()

    @Volatile private var dir: File? = null

    @Volatile private var copy: File? = null

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    val busy: Boolean get() = _state.value.phase is PdfImagesPhase.Working

    /** Opens the PDF at [uri]. False while pictures are being made. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        thumbJob?.cancel()
        val token = generation.incrementAndGet()
        dropThumbs()
        _state.update { it.copy(phase = PdfImagesPhase.Reading, source = null, selected = emptySet(), pagesText = "", pagesError = false, covers = emptyList()) }
        readJob = scope.launch(Dispatchers.IO) {
            dropWork()
            try {
                val source = read(uri)
                val all = PdfImagesPlan.all(source.count)
                update(token) { s ->
                    s.copy(
                        phase = PdfImagesPhase.Ready,
                        source = source,
                        selected = all,
                        pagesText = PdfImagesPlan.formatPages(all),
                        zip = if (s.zipChosen) s.zip else PdfImagesPlan.zipByDefault(all.size),
                    )
                }
                val file = copy ?: return@launch
                thumbJob = scope.launch(Dispatchers.IO) { drawThumbs(file, source, token) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dropWork()
                val message = when (e) {
                    is SecurityException -> "Android a refusé l'accès : choisis le PDF à nouveau."
                    is FileTooLarge, is UnsupportedSource, is ConversionError -> e.message ?: "PDF illisible."
                    else -> "Impossible de lire ce fichier."
                }
                update(token) { it.copy(phase = PdfImagesPhase.Unsupported(message)) }
            }
        }
        return true
    }

    private fun read(uri: Uri): PdfImagesSource {
        val info = FileIntake.describe(context, uri)
        val head = FileIntake.readHead(context, uri)
        if (FileKind.detect(info.name, info.mime, head) != FileKind.PDF) {
            throw ConversionError("« ${info.name} » n'est pas un PDF. Choisis un fichier PDF.")
        }
        val work = WorkDir.create(context)
        dir = work
        val file = FileIntake.copyToWork(context, uri, maxBytes = MAX_PDF_BYTES, dir = work)
        copy = file
        return openRenderer(file) { renderer ->
            val count = renderer.pageCount
            if (count == 0) throw ConversionError("Ce PDF n'a aucune page.")
            if (count > MAX_PAGES) throw ConversionError("$MAX_PAGES pages au plus par PDF.")
            val pages = (0 until count).map { i ->
                renderer.openPage(i).use { PdfPageInfo(i, it.width.toFloat(), it.height.toFloat()) }
            }
            PdfImagesSource(info, pages)
        }
    }

    private suspend fun drawThumbs(file: File, source: PdfImagesSource, token: Int) {
        for (page in source.pages.take(MAX_THUMBS)) {
            coroutineContext.ensureActive()
            if (generation.get() != token) return
            val bitmap = runCatching {
                renderLock.withLock {
                    openRenderer(file) { renderer -> draw(renderer, page.index, THUMB_WIDTH, (THUMB_WIDTH * page.heightPt / page.widthPt.coerceAtLeast(1f)).roundToInt()) }
                }
            }.getOrNull() ?: continue
            // Half the memory (a page has no transparency): 120 thumbnails stay around 8 MB.
            val small = bitmap.copy(Bitmap.Config.RGB_565, false)?.also { bitmap.recycle() } ?: bitmap
            if (generation.get() != token) return small.recycle()
            _thumbs.update { it + (page.index + 1 to small) }
        }
    }

    // MARK: - Settings

    /** A tap on page [page] (from 1): chosen or left out. */
    fun toggle(page: Int) = edit { s ->
        val selected = PdfImagesPlan.toggle(s.selected, page)
        s.copy(selected = selected, pagesText = PdfImagesPlan.formatPages(selected), pagesError = false).withZipFor(selected.size)
    }

    fun selectAll() = edit { s ->
        val all = PdfImagesPlan.all(s.source?.count ?: 0)
        s.copy(selected = all, pagesText = PdfImagesPlan.formatPages(all), pagesError = false).withZipFor(all.size)
    }

    fun selectNone() = edit { it.copy(selected = emptySet(), pagesText = "", pagesError = false) }

    /** The user typed in the "Pages" field: the grid follows when it is valid. */
    fun setPagesText(text: String) = edit { s ->
        val parsed = PdfImagesPlan.parsePages(text, s.source?.count ?: 0)
        when {
            text.isBlank() -> s.copy(pagesText = text, selected = emptySet(), pagesError = false)
            parsed == null -> s.copy(pagesText = text, pagesError = true)
            else -> s.copy(pagesText = text, selected = parsed.toSet(), pagesError = false).withZipFor(parsed.size)
        }
    }

    fun setMode(mode: ImagesMode) = edit { it.copy(mode = mode) }

    fun setFormat(format: ImagesFormat) = edit { it.copy(format = format) }

    fun setSharpness(sharpness: Sharpness) = edit { it.copy(sharpness = sharpness) }

    fun setZip(zip: Boolean) = edit { it.copy(zip = zip, zipChosen = true) }

    private fun PdfImagesUiState.withZipFor(pictures: Int) = if (zipChosen) this else copy(zip = PdfImagesPlan.zipByDefault(pictures))

    private inline fun edit(crossinline change: (PdfImagesUiState) -> PdfImagesUiState) {
        _state.update { if (it.phase == PdfImagesPhase.Ready && it.source != null) change(it) else it }
    }

    /** Back to the pages after a result or a failure (same PDF). */
    fun edit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = PdfImagesPhase.Ready, covers = emptyList()) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is PdfImagesPhase.Working) it.copy(phase = PdfImagesPhase.Ready) else it }
    }

    /** Forgets the PDF (the settings stay for the next one). */
    fun reset() {
        if (busy) return
        generation.incrementAndGet()
        readJob?.cancel()
        thumbJob?.cancel()
        dropThumbs()
        _state.update { PdfImagesUiState(mode = it.mode, format = it.format, sharpness = it.sharpness) }
        scope.launch(Dispatchers.IO) { dropWork() }
    }

    // MARK: - Work

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        val file = copy ?: return
        val work = dir ?: return
        if (busy || current.phase != PdfImagesPhase.Ready || current.selected.isEmpty() || current.pagesError) return
        val pages = current.selected.sorted()
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = PdfImagesPhase.Working(0, pages.size, pages.first()), covers = emptyList()) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val outDir = File(work, "out").apply { deleteRecursively(); mkdirs() }
            try {
                val base = source.file.name
                val made = when (current.mode) {
                    ImagesMode.PAGES -> renderPages(file, source, pages, current, outDir, token)
                    ImagesMode.EMBEDDED -> EmbeddedImages.extract(file, pages, current.format, outDir, base) { done, total ->
                        progress(token, done, total, pages.getOrNull(done) ?: pages.last())
                    }
                }
                if (made.isEmpty()) {
                    update(token) { it.copy(phase = PdfImagesPhase.NothingInside) }
                    return@launch
                }
                val covers = made.take(3).mapNotNull { runCatching { Images.decode(it, COVER_SIDE) }.getOrNull() }
                val saved = if (current.zip) {
                    val zip = FileIntake.uniqueFile(outDir, PdfImagesPlan.zipName(base))
                    zipAll(made, zip)
                    listOf(MediaExporter.export(context, zip))
                } else {
                    made.map { coroutineContext.ensureActive(); MediaExporter.export(context, it) }
                }
                update(token) { it.copy(phase = PdfImagesPhase.Done(saved, made.size, current.zip), covers = covers) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidPasswordException) {
                fail(token, LOCKED)
            } catch (e: ConversionError) {
                fail(token, e.message ?: "PDF illisible.", e.debug)
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'enregistrement : autorise le stockage et réessaie.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire : choisis une netteté plus basse.", e.toString())
            } catch (e: Throwable) {
                fail(token, "Les images n'ont pas pu être créées.", e.toString())
            } finally {
                withContext(NonCancellable) { outDir.deleteRecursively() }
            }
        }
    }

    private suspend fun renderPages(file: File, source: PdfImagesSource, pages: List<Int>, settings: PdfImagesUiState, outDir: File, token: Int): List<File> {
        val target = if (settings.format == ImagesFormat.PNG) Target.PNG else Target.JPG
        val out = ArrayList<File>()
        renderLock.withLock {
            openRenderer(file) { renderer ->
                pages.forEachIndexed { done, number ->
                    // Inside the renderer's block: cancellation is checked by hand between pages.
                    if (generation.get() != token) throw CancellationException()
                    progress(token, done, pages.size, number)
                    val page = source.pages[number - 1]
                    val (w, h) = PdfImagesPlan.renderSize(page.widthPt, page.heightPt, settings.sharpness.dpi)
                    val bitmap = draw(renderer, page.index, w, h)
                    try {
                        val picture = File(outDir, PdfImagesPlan.pageName(source.file.name, number, source.count, target.ext))
                        Images.encode(bitmap, target, PdfImagesPlan.JPEG_QUALITY, picture)
                        out += picture
                    } finally {
                        bitmap.recycle()
                    }
                }
                progress(token, pages.size, pages.size, pages.last())
            }
        }
        return out
    }

    private suspend fun zipAll(files: List<File>, zip: File) {
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            // JPG and PNG are compressed already: deflating them again only costs time.
            out.setLevel(Deflater.BEST_SPEED)
            for (file in files) {
                coroutineContext.ensureActive()
                out.putNextEntry(ZipEntry(file.name).apply { time = file.lastModified() })
                file.inputStream().buffered().use { it.copyTo(out, 256 * 1024) }
                out.closeEntry()
            }
        }
    }

    private fun progress(token: Int, done: Int, total: Int, page: Int) {
        update(token) { s -> if (s.phase is PdfImagesPhase.Working) s.copy(phase = PdfImagesPhase.Working(done, total, page)) else s }
    }

    private inline fun <T> openRenderer(file: File, block: (PdfRenderer) -> T): T {
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
            return block(renderer)
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /** Page [index] drawn on white into a [width] × [height] bitmap. */
    private fun draw(renderer: PdfRenderer, index: Int, width: Int, height: Int): Bitmap =
        renderer.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap
        }

    private fun dropThumbs() {
        val old = _thumbs.value
        _thumbs.value = emptyMap()
        // Recycled later: the screen may still be drawing the last frame with them.
        if (old.isNotEmpty()) scope.launch { kotlinx.coroutines.delay(1_000); old.values.forEach { it.recycle() } }
    }

    private fun dropWork() {
        val folder = dir
        dir = null
        copy = null
        folder?.let { WorkDir.delete(context, it) }
    }

    private inline fun update(token: Int, crossinline change: (PdfImagesUiState) -> PdfImagesUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = PdfImagesPhase.Failed(message, debug)) }
    }

    companion object {
        /** The app's one controller (made on first use, lives as long as the app). */
        val shared: PdfImagesController by lazy { PdfImagesController(App.instance, App.instance.scope) }

        const val MAX_PAGES = 500

        /** Pages shown as thumbnails; the others are chosen with the "Pages" field. */
        const val MAX_THUMBS = 120
        private const val THUMB_WIDTH = 180
        private const val COVER_SIDE = 480
        private const val MAX_PDF_BYTES: Long = 200L * 1024 * 1024
        private const val LOCKED = "Ce PDF est protégé par un mot de passe : Gulp ne peut pas l'ouvrir."
    }
}
