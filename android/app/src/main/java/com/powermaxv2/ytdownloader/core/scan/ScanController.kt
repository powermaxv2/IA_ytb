package com.powermaxv2.ytdownloader.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.compress.JpegPdfWriter
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * One page of a scan: the photo it comes from (a private copy), where the sheet is in it, how it is turned and
 * cleaned. Nothing is baked in before the PDF is made, so every choice can still be changed.
 */
data class ScanPage(
    val id: Long,
    val photo: File,
    /** Shape of the photo (after its EXIF rotation): width / height. */
    val aspect: Float,
    val quad: Quad = Quad.FULL,
    /** What the detector found, for "Cadre auto"; null = nothing found. */
    val detected: Quad? = null,
    val rotation: Int = 0,
    val filter: ScanFilter = ScanFilter.DEFAULT,
)

sealed interface ScanPhase {
    /** No page yet. */
    data object Idle : ScanPhase

    /** The list of pages, their order and the PDF settings. */
    data object Pages : ScanPhase

    /** The corner / turn / filter editor of one page. */
    data class Editing(val pageId: Long) : ScanPhase

    /** [progress] 0..1, negative when unknown. */
    data class Working(val progress: Float, val step: String) : ScanPhase

    data class Done(val saved: SavedFile, val pages: Int) : ScanPhase

    data class Failed(val message: String, val debug: String? = null) : ScanPhase
}

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val pages: List<ScanPage> = emptyList(),
    /** Small pictures of the finished pages (cropped, turned, cleaned), by page id. */
    val thumbs: Map<Long, Bitmap> = emptyMap(),
    /** The photo shown in the editor (the whole photo, not cut). */
    val editorPhoto: Bitmap? = null,
    /** The page in the editor with each filter, for the filter tiles. */
    val filterPreviews: Map<ScanFilter, Bitmap> = emptyMap(),
    /** Photos being read (a shimmer is shown meanwhile). */
    val importing: Int = 0,
    val pageSize: PageSize = PageSize.A4,
    val quality: ScanQuality = ScanQuality.NORMAL,
    /** What the user typed as the file name; empty = [defaultName]. */
    val name: String = "",
    val defaultName: String = ScanPlan.defaultName(),
) {
    fun page(id: Long): ScanPage? = pages.firstOrNull { it.id == id }

    val editing: ScanPage? get() = (phase as? ScanPhase.Editing)?.let { page(it.pageId) }

    val estimate: Long get() = ScanPlan.estimate(pages.size, pageSize, quality, pages.count { it.filter == ScanFilter.BW })

    val fileName: String get() = ScanPlan.fileName(name, defaultName)
}

/**
 * The Scanner's state and work, held by the App like the other tools' (leaving the screen keeps the pages).
 * Photos live in a private work folder that is deleted on [reset] and at the next launch.
 */
class ScanController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Bitmap work, one at a time (memory). */
    private val lock = Mutex()
    private val ids = AtomicLong(0)
    private var session: File? = null
    private var exportJob: Job? = null

    val busy: Boolean get() = _state.value.phase is ScanPhase.Working

    private fun sessionDir(): File = session?.takeIf { it.isDirectory } ?: WorkDir.create(context).also { session = it }

    /** A new empty file for the camera app to write a photo into (shared to it through Gulp's FileProvider). */
    fun newShotFile(): File {
        val dir = File(sessionDir(), "photos").apply { mkdirs() }
        return File(dir, "photo-${System.currentTimeMillis()}.jpg")
    }

    /** The camera came back: [ok] with a photo in [file], or nothing (then the empty file is removed). */
    fun onShot(file: File, ok: Boolean) {
        if (!ok || !file.isFile || file.length() == 0L) {
            file.delete()
            return
        }
        add(listOf(file), edit = true)
    }

    /** Images picked in the file picker: copied into the scan, then shown in the list. */
    fun pick(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        val room = ScanPlan.MAX_PAGES - _state.value.pages.size
        if (room <= 0) return
        _state.update { it.copy(importing = it.importing + 1) }
        scope.launch(Dispatchers.IO) {
            val files = ArrayList<File>()
            var error: String? = null
            try {
                val dir = File(sessionDir(), "photos").apply { mkdirs() }
                for (uri in uris.take(room)) {
                    try {
                        files += FileIntake.copyToWork(context, uri, PHOTO_MAX_BYTES, dir)
                    } catch (e: FileTooLarge) {
                        error = "Une image trop lourde a été laissée de côté (40 Mo au plus)."
                    } catch (e: UnsupportedSource) {
                        error = e.message
                    } catch (e: IOException) {
                        error = "Une image n'a pas pu être lue."
                    } catch (e: SecurityException) {
                        error = "Android a refusé l'accès à une image."
                    }
                }
            } catch (e: IOException) {
                error = e.message ?: "Impossible de préparer le scan."
            } finally {
                _state.update { it.copy(importing = it.importing - 1) }
            }
            if (files.isNotEmpty()) add(files, edit = uris.size == 1 && _state.value.pages.isEmpty())
            if (error != null && files.isEmpty() && _state.value.pages.isEmpty()) {
                _state.update { it.copy(phase = ScanPhase.Failed(error ?: "")) }
            }
        }
    }

    private fun add(files: List<File>, edit: Boolean) {
        _state.update { it.copy(importing = it.importing + 1) }
        scope.launch(Dispatchers.Default) {
            try {
                for (file in files) {
                    val page = lock.withLock { read(file) }
                    if (page == null) {
                        file.delete()
                        continue
                    }
                    val (newPage, thumb) = page
                    _state.update { s ->
                        if (s.pages.size >= ScanPlan.MAX_PAGES) return@update s
                        val pages = s.pages + newPage
                        val phase = when {
                            s.phase is ScanPhase.Working -> s.phase
                            edit && files.size == 1 -> s.phase
                            s.phase is ScanPhase.Editing -> s.phase
                            else -> ScanPhase.Pages
                        }
                        s.copy(pages = pages, thumbs = s.thumbs + (newPage.id to thumb), phase = phase)
                    }
                    if (edit && files.size == 1) edit(newPage.id)
                }
                if (_state.value.pages.isEmpty() && _state.value.phase == ScanPhase.Idle && files.isNotEmpty()) {
                    _state.update { it.copy(phase = ScanPhase.Failed("Gulp ne peut pas lire cette image : format non pris en charge, ou fichier abîmé.")) }
                }
            } finally {
                _state.update { it.copy(importing = it.importing - 1) }
            }
        }
    }

    /** Decodes [file] once: its shape, where the sheet is, and the first small picture. Null when unreadable. */
    private fun read(file: File): Pair<ScanPage, Bitmap>? {
        val photo = try {
            Images.decode(file, WORK_SIDE)
        } catch (e: ConversionError) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
        return try {
            val detected = runCatching { ScanImages.detect(photo) }.getOrNull()
            val page = ScanPage(
                id = ids.incrementAndGet(),
                photo = file,
                aspect = photo.width.toFloat() / photo.height.coerceAtLeast(1),
                quad = detected ?: Quad.FULL,
                detected = detected,
            )
            page to ScanImages.render(photo, page.quad, page.rotation, page.filter, THUMB_SIDE)
        } finally {
            photo.recycle()
        }
    }

    /** Opens the editor on page [id]. */
    fun edit(id: Long) {
        val page = _state.value.page(id) ?: return
        if (busy) return
        _state.update { it.copy(phase = ScanPhase.Editing(id), editorPhoto = null, filterPreviews = emptyMap()) }
        scope.launch(Dispatchers.Default) {
            val photo = lock.withLock { runCatching { Images.decode(page.photo, EDITOR_SIDE) }.getOrNull() }
            _state.update { s ->
                if ((s.phase as? ScanPhase.Editing)?.pageId == id) s.copy(editorPhoto = photo) else s.also { photo?.recycle() }
            }
            refresh(id)
        }
    }

    fun setQuad(id: Long, quad: Quad) = change(id) { if (quad.isValid) it.copy(quad = quad) else it }

    fun autoQuad(id: Long) = change(id) { it.copy(quad = it.detected ?: Quad.FULL) }

    fun fullQuad(id: Long) = change(id) { it.copy(quad = Quad.FULL) }

    fun rotate(id: Long) = change(id) { it.copy(rotation = ScanGeometry.normalize(it.rotation + 90)) }

    fun setFilter(id: Long, filter: ScanFilter) = change(id, previews = false) { it.copy(filter = filter) }

    /** Applies [change] to page [id], then redraws its small picture (and the filter tiles when [previews]). */
    private fun change(id: Long, previews: Boolean = true, change: (ScanPage) -> ScanPage) {
        if (busy) return
        var changed = false
        _state.update { s ->
            val pages = s.pages.map { p -> if (p.id == id) change(p).also { changed = it != p } else p }
            s.copy(pages = pages)
        }
        if (changed) scope.launch(Dispatchers.Default) { refresh(id, previews) }
    }

    /** Redraws page [id]'s small picture and, in the editor, its filter tiles. */
    private suspend fun refresh(id: Long, previews: Boolean = true) {
        lock.withLock {
            val page = _state.value.page(id) ?: return
            val photo = runCatching { Images.decode(page.photo, WORK_SIDE) }.getOrNull() ?: return
            try {
                val base = ScanImages.render(photo, page.quad, page.rotation, ScanFilter.ORIGINAL, THUMB_SIDE)
                val all = ScanFilter.entries.associateWith { filter ->
                    if (filter == ScanFilter.ORIGINAL) base else filtered(base, filter)
                }
                val thumb = all.getValue(page.filter)
                _state.update { s ->
                    if (s.page(id) == null) return@update s
                    val editing = (s.phase as? ScanPhase.Editing)?.pageId == id
                    s.copy(
                        thumbs = s.thumbs + (id to thumb),
                        filterPreviews = if (editing && previews) all else s.filterPreviews,
                    )
                }
            } finally {
                photo.recycle()
            }
        }
    }

    private fun filtered(base: Bitmap, filter: ScanFilter): Bitmap {
        val copy = base.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(copy.width * copy.height)
        copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
        ScanFilters.apply(filter, pixels, copy.width, copy.height)
        copy.setPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
        return copy
    }

    /** Back from the editor to the list of pages. */
    fun closeEditor() {
        _state.update { s ->
            if (s.phase !is ScanPhase.Editing) return@update s
            s.copy(phase = if (s.pages.isEmpty()) ScanPhase.Idle else ScanPhase.Pages, editorPhoto = null, filterPreviews = emptyMap())
        }
    }

    fun remove(id: Long) {
        if (busy) return
        val page = _state.value.page(id) ?: return
        _state.update { s ->
            val pages = s.pages.filterNot { it.id == id }
            val phase = when {
                pages.isEmpty() -> ScanPhase.Idle
                s.phase is ScanPhase.Editing -> ScanPhase.Pages
                else -> s.phase
            }
            s.copy(pages = pages, thumbs = s.thumbs - id, phase = phase, editorPhoto = if (phase is ScanPhase.Editing) s.editorPhoto else null)
        }
        page.photo.delete()
    }

    /** Moves page [id] by [delta] places (−1 = earlier). */
    fun move(id: Long, delta: Int) {
        if (busy) return
        _state.update { s -> s.copy(pages = ScanOrder.move(s.pages, s.pages.indexOfFirst { it.id == id }, delta)) }
    }

    fun setPageSize(size: PageSize) = _state.update { if (it.phase is ScanPhase.Working) it else it.copy(pageSize = size) }

    fun setQuality(quality: ScanQuality) = _state.update { if (it.phase is ScanPhase.Working) it else it.copy(quality = quality) }

    fun setName(name: String) = _state.update { it.copy(name = name.take(100)) }

    /** From the result or an error, back to the pages (to change something and make the PDF again). */
    fun backToPages() {
        _state.update { if (busy) it else it.copy(phase = if (it.pages.isEmpty()) ScanPhase.Idle else ScanPhase.Pages) }
    }

    /** A new scan: the pages and their photos are thrown away. */
    fun reset() {
        if (busy) return
        session?.let { WorkDir.delete(context, it) }
        session = null
        _state.value = ScanUiState(pageSize = _state.value.pageSize, quality = _state.value.quality)
    }

    fun cancel() {
        exportJob?.cancel()
        _state.update { if (it.phase is ScanPhase.Working) it.copy(phase = ScanPhase.Pages) else it }
    }

    /** Makes the PDF and saves it in Download/Gulp. */
    fun export() {
        val current = _state.value
        if (current.pages.isEmpty() || busy || current.importing > 0) return
        _state.update { it.copy(phase = ScanPhase.Working(0f, "Préparation…"), editorPhoto = null, filterPreviews = emptyMap()) }
        exportJob = scope.launch(Dispatchers.Default) {
            val pages = current.pages
            var out: File? = null
            try {
                val dir = File(sessionDir(), "out").apply { mkdirs() }
                val file = FileIntake.uniqueFile(dir, current.fileName)
                out = file
                lock.withLock {
                    JpegPdfWriter(file.outputStream().buffered()).use { writer ->
                        pages.forEachIndexed { index, page ->
                            coroutineContext.ensureActive()
                            step(index.toFloat() / pages.size, "Page ${index + 1} sur ${pages.size}…")
                            writePage(writer, page, current.pageSize, current.quality)
                        }
                        writer.finish()
                    }
                }
                coroutineContext.ensureActive()
                step(1f, "Enregistrement…")
                val saved = MediaExporter.export(context, file)
                file.delete()
                _state.update { it.copy(phase = ScanPhase.Done(saved, pages.size)) }
            } catch (e: CancellationException) {
                out?.delete()
                throw e
            } catch (e: ConversionError) {
                out?.delete()
                fail(e.message ?: GENERIC, e.debug)
            } catch (e: OutOfMemoryError) {
                out?.delete()
                fail("Pas assez de mémoire : choisissez la qualité « Légère ».", e.toString())
            } catch (e: IOException) {
                out?.delete()
                fail("Écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: Throwable) {
                out?.delete()
                fail(GENERIC, e.toString())
            }
        }
    }

    private fun writePage(writer: JpegPdfWriter, page: ScanPage, size: PageSize, quality: ScanQuality) {
        val photo = Images.decode(page.photo, Images.MAX_SIDE)
        val rendered = try {
            ScanImages.render(photo, page.quad, page.rotation, ScanFilter.ORIGINAL, ScanGeometry.MAX_OUTPUT_SIDE)
        } finally {
            photo.recycle()
        }
        val placement = ScanPlan.place(rendered.width, rendered.height, size)
        val (tw, th) = ScanPlan.targetPixels(rendered.width, rendered.height, placement, quality)
        // Brought down to the final size first: the filters then work on fewer pixels (and black & white looks
        // the same as on screen).
        val sized = ScanImages.scaled(rendered, tw, th)
        if (sized !== rendered) rendered.recycle()
        try {
            if (page.filter != ScanFilter.ORIGINAL) {
                val pixels = IntArray(tw * th)
                sized.getPixels(pixels, 0, tw, 0, 0, tw, th)
                ScanFilters.apply(page.filter, pixels, tw, th)
                sized.setPixels(pixels, 0, tw, 0, 0, tw, th)
            }
            val jpeg = ScanImages.jpeg(sized, if (page.filter == ScanFilter.BW) quality.bwJpeg else quality.jpeg)
            writer.addPage(jpeg, tw, th, placement.pageWidth, placement.pageHeight, placement.x, placement.y, placement.width, placement.height)
        } finally {
            sized.recycle()
        }
    }

    private fun step(progress: Float, step: String) {
        _state.update { if (it.phase is ScanPhase.Working) it.copy(phase = ScanPhase.Working(progress, step)) else it }
    }

    private fun fail(message: String, debug: String? = null) {
        _state.update { it.copy(phase = ScanPhase.Failed(message, debug)) }
    }

    private companion object {
        const val PHOTO_MAX_BYTES = 40L * 1024 * 1024

        /** Photo size for detection and small pictures. */
        const val WORK_SIDE = 1200

        /** Photo size in the editor. */
        const val EDITOR_SIDE = 1600

        /** Longest side of a page's small picture. */
        const val THUMB_SIDE = 480

        const val GENERIC = "Le PDF n'a pas pu être créé."
    }
}

/** Page order changes (pure, unit tested). */
object ScanOrder {
    /** [list] with the item at [index] moved by [delta] places (clamped to the ends); unchanged for a bad index. */
    fun <T> move(list: List<T>, index: Int, delta: Int): List<T> {
        if (index !in list.indices || delta == 0) return list
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return list
        val out = list.toMutableList()
        val item = out.removeAt(index)
        out.add(target, item)
        return out
    }
}
