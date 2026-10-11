package com.powermaxv2.ytdownloader.core.pdfjoin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.compress.JpegPdfWriter
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A file given to the Assembleur: its copy in the session's work dir and how many pages it brings. */
data class JoinSource(val file: LocalFile, val pdf: Boolean, val work: File, val pages: Int)

sealed interface JoinPhase {
    data object Idle : JoinPhase

    data object Reading : JoinPhase

    data object Ready : JoinPhase

    data class Working(val done: Int, val total: Int) : JoinPhase

    data class Done(val saved: List<SavedFile>, val pages: Int) : JoinPhase

    data class Failed(val message: String, val debug: String? = null) : JoinPhase
}

data class JoinUiState(
    val phase: JoinPhase = JoinPhase.Idle,
    val sources: List<JoinSource> = emptyList(),
    val pages: List<JoinPage> = emptyList(),
    /** Page whose actions (move, turn, remove) are shown. */
    val selected: Long? = null,
    val mode: JoinMode = JoinMode.ONE,
    /** A message about the last files added (refused ones, limits), shown above the pages. */
    val notice: String? = null,
)

/**
 * The Assembleur PDF's state and work, held by the App. Each file given is copied once into a work dir kept for the
 * session (the pages are drawn from it, and drawn again into the result); [reset] deletes it. The result is built
 * like the Compresseur rebuilds a PDF: every page becomes a JPEG picture in a new PDF ([JpegPdfWriter]).
 */
class JoinController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    /** Small pictures of the pages, by [JoinPage.id]. */
    private val _thumbs = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<Long, Bitmap>> = _thumbs.asStateFlow()

    private var dir: File? = null
    private var readJob: Job? = null
    private var workJob: Job? = null
    private val nextId = AtomicLong(1)
    private val generation = AtomicInteger(0)

    /** PdfRenderer opens one page at a time: thumbnails and the build never draw together. */
    private val renderLock = Mutex()

    val busy: Boolean get() = _state.value.phase is JoinPhase.Working

    /** Adds the chosen PDF and photos at the end of the pages. False while the PDF is being built. */
    fun add(uris: List<Uri>): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        val previous = readJob
        _state.update { it.copy(phase = JoinPhase.Reading, notice = null) }
        readJob = scope.launch(Dispatchers.IO) {
            previous?.join()
            val notes = ArrayList<String>()
            val added = ArrayList<Pair<JoinSource, List<JoinPage>>>()
            try {
                val folder = dir ?: WorkDir.create(context).also { dir = it }
                var sourceCount = _state.value.sources.size
                var pageCount = _state.value.pages.size
                for (uri in uris) {
                    coroutineContext.ensureActive()
                    if (sourceCount >= JoinPlan.MAX_FILES) {
                        notes += "${JoinPlan.MAX_FILES} fichiers au plus."
                        break
                    }
                    val read = runCatching { read(uri, folder, sourceCount) }
                    val result = read.getOrElse { e ->
                        if (e is CancellationException) throw e
                        notes += e.message ?: "Un fichier n'a pas pu être lu."
                        null
                    } ?: continue
                    val room = JoinPlan.MAX_PAGES - pageCount
                    if (room <= 0) {
                        result.first.work.delete()
                        notes += "${JoinPlan.MAX_PAGES} pages au plus."
                        break
                    }
                    val pages = result.second.take(room)
                    if (pages.size < result.second.size) notes += "${JoinPlan.MAX_PAGES} pages au plus : la fin de « ${result.first.file.name} » n'est pas prise."
                    added += result.first to pages
                    sourceCount++
                    pageCount += pages.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                notes += e.message ?: "Impossible de préparer les fichiers."
            }
            _state.update { s ->
                val pages = s.pages + added.flatMap { it.second }
                s.copy(
                    phase = if (pages.isEmpty()) JoinPhase.Idle else JoinPhase.Ready,
                    sources = s.sources + added.map { it.first },
                    pages = pages,
                    notice = notes.distinct().joinToString("\n").ifEmpty { null },
                )
            }
            renderThumbs(added.flatMap { it.second })
        }
        return true
    }

    /** Copies [uri] into [folder] and lists its pages (source number [index]). */
    private fun read(uri: Uri, folder: File, index: Int): Pair<JoinSource, List<JoinPage>> {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.PDF && kind != FileKind.IMAGE) throw IOException("« ${info.name} » n'est ni un PDF ni une photo.")
        val pdf = kind == FileKind.PDF
        val work = try {
            FileIntake.copyToWork(context, uri, if (pdf) PDF_MAX_BYTES else IMAGE_MAX_BYTES, folder)
        } catch (e: FileTooLarge) {
            throw IOException("« ${info.name} » est trop gros.")
        }
        try {
            val pages = if (pdf) pdfPages(work, index, info.name) else listOf(photoPage(work, index, info.name))
            return JoinSource(info, pdf, work, pages.size) to pages
        } catch (e: Exception) {
            work.delete()
            throw e
        }
    }

    private fun pdfPages(file: File, source: Int, name: String): List<JoinPage> {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: SecurityException) {
            fd.close()
            throw IOException("« $name » est protégé par un mot de passe.")
        } catch (e: Exception) {
            fd.close()
            throw IOException("« $name » est illisible ou abîmé.")
        }
        try {
            if (renderer.pageCount == 0) throw IOException("« $name » n'a aucune page.")
            return (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { page -> JoinPage(nextId.getAndIncrement(), source, i, page.width.toFloat(), page.height.toFloat()) }
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    private fun photoPage(file: File, source: Int, name: String): JoinPage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var w = bounds.outWidth
        var h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            // HEIC on older phones, or a damaged photo: the full decode says.
            val bitmap = runCatching { Images.decode(file, 512) }.getOrNull() ?: throw IOException("« $name » est une photo illisible.")
            w = bitmap.width
            h = bitmap.height
            bitmap.recycle()
        } else if (exifTurned(file)) {
            w = h.also { h = w }
        }
        val (wPt, hPt) = JoinPlan.photoPage(w, h)
        return JoinPage(nextId.getAndIncrement(), source, 0, wPt, hPt)
    }

    private fun exifTurned(file: File): Boolean = runCatching {
        val o = android.media.ExifInterface(file.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        o in 5..8
    }.getOrDefault(false)

    private suspend fun renderThumbs(pages: List<JoinPage>) {
        for (page in pages) {
            coroutineContext.ensureActive()
            val bitmap = runCatching { draw(page, THUMB_WIDTH, rotate = false) }.getOrNull() ?: continue
            _thumbs.update { it + (page.id to bitmap) }
        }
    }

    /** Page [page] drawn [width] pixels wide (for a thumbnail) or at [JoinPlan.renderSize] when [width] is 0. */
    private suspend fun draw(page: JoinPage, width: Int, rotate: Boolean): Bitmap {
        val source = _state.value.sources.getOrNull(page.source) ?: throw IOException("Fichier disparu.")
        val bitmap = if (source.pdf) {
            renderLock.withLock { renderPdf(source.work, page, width) }
        } else {
            val (rw, rh) = JoinPlan.renderSize(page.widthPt, page.heightPt)
            Images.decode(source.work, if (width > 0) width * 2 else max(rw, rh))
        }
        if (!rotate || page.rotation == 0) return bitmap
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(page.rotation.toFloat()) }, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private fun renderPdf(file: File, page: JoinPage, width: Int): Bitmap {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(page.index).use { p ->
                    val (w, h) = if (width > 0) {
                        width to max(1, (width * p.height.toFloat() / p.width.coerceAtLeast(1)).roundToInt())
                    } else {
                        JoinPlan.renderSize(p.width.toFloat(), p.height.toFloat())
                    }
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    return bitmap
                }
            }
        } finally {
            fd.close()
        }
    }

    fun select(id: Long?) = edit { it.copy(selected = if (it.selected == id) null else id) }

    fun move(id: Long, delta: Int) = edit { it.copy(pages = JoinPlan.move(it.pages, id, delta)) }

    fun rotate(id: Long) = edit { it.copy(pages = JoinPlan.rotate(it.pages, id)) }

    fun remove(id: Long) = edit {
        val pages = JoinPlan.remove(it.pages, id)
        it.copy(pages = pages, selected = null, phase = if (pages.isEmpty()) JoinPhase.Idle else it.phase)
    }

    fun setMode(mode: JoinMode) = edit { it.copy(mode = mode) }

    private inline fun edit(crossinline change: (JoinUiState) -> JoinUiState) {
        _state.update { if (it.phase == JoinPhase.Ready || it.phase == JoinPhase.Reading) change(it) else it }
    }

    fun backToPages() {
        _state.update { if (it.pages.isNotEmpty() && !busy) it.copy(phase = JoinPhase.Ready) else it }
    }

    /** Forgets every file and deletes the session's copies. */
    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.value = JoinUiState()
        _thumbs.value = emptyMap()
        dir?.let { old ->
            dir = null
            scope.launch(Dispatchers.IO) { WorkDir.delete(context, old) }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is JoinPhase.Working) it.copy(phase = JoinPhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        if (current.pages.isEmpty() || busy || current.phase != JoinPhase.Ready) return
        val token = generation.incrementAndGet()
        val pages = current.pages
        _state.update { it.copy(phase = JoinPhase.Working(0, pages.size), selected = null) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val outDir = File(dir ?: return@launch fail(token, "Les fichiers ne sont plus là : ajoutez-les à nouveau."), "out")
            try {
                outDir.mkdirs()
                val saved = if (current.mode == JoinMode.ONE) {
                    val names = pages.map { p -> current.sources[p.source].file.name }
                    val out = FileIntake.uniqueFile(outDir, JoinPlan.outputName(names))
                    write(out, pages, token, 0, pages.size)
                    listOf(MediaExporter.export(context, out).also { out.delete() })
                } else {
                    pages.mapIndexed { i, page ->
                        val out = FileIntake.uniqueFile(outDir, JoinPlan.pageName(current.sources[page.source].file.name, i + 1))
                        write(out, listOf(page), token, i, pages.size)
                        MediaExporter.export(context, out).also { out.delete() }
                    }
                }
                update(token) { it.copy(phase = JoinPhase.Done(saved, pages.size)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: "Le PDF n'a pas pu être créé.", e.debug)
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Ce fichier ne peut pas être ouvert.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : ajoutez les fichiers à nouveau, ou autorisez le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour ces pages.", e.toString())
            } catch (e: Throwable) {
                fail(token, "Le PDF n'a pas pu être créé.", e.toString())
            } finally {
                withContext(NonCancellable) { outDir.deleteRecursively() }
            }
        }
    }

    /** Writes [pages] into [out]; [offset] / [total] place them on the progress. */
    private suspend fun write(out: File, pages: List<JoinPage>, token: Int, offset: Int, total: Int) {
        try {
            JpegPdfWriter(out.outputStream().buffered()).use { writer ->
                pages.forEachIndexed { i, page ->
                    coroutineContext.ensureActive()
                    val bitmap = draw(page, 0, rotate = true)
                    try {
                        val jpeg = ByteArrayOutputStream()
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JoinPlan.JPEG_QUALITY, jpeg)) throw ConversionError("Impossible d'écrire une page.")
                        writer.addPage(jpeg.toByteArray(), bitmap.width, bitmap.height, page.outWidthPt, page.outHeightPt)
                    } finally {
                        bitmap.recycle()
                    }
                    update(token) { s -> if (s.phase is JoinPhase.Working) s.copy(phase = JoinPhase.Working(offset + i + 1, total)) else s }
                }
                writer.finish()
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    private inline fun update(token: Int, crossinline change: (JoinUiState) -> JoinUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = JoinPhase.Failed(message, debug)) }
    }

    private companion object {
        const val PDF_MAX_BYTES: Long = 200L * 1024 * 1024
        const val IMAGE_MAX_BYTES: Long = 64L * 1024 * 1024
        const val THUMB_WIDTH = 180
    }
}
