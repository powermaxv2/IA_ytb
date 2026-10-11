package com.powermaxv2.ytdownloader.core.pdfjoin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A file given to the Assembleur: its copy in the session's work dir and how many pages it brings. */
data class JoinSource(val file: LocalFile, val pdf: Boolean, val work: File, val pages: Int)

/** A line of the "done" card: a file given (where its pages went) or a file made (which pages it holds). */
data class JoinLine(val name: String, val pdf: Boolean, val detail: String)

sealed interface JoinPhase {
    data object Idle : JoinPhase

    /** The pages are shown and can be moved, turned, removed, cut. */
    data object Ready : JoinPhase

    data class Working(val done: Int, val total: Int) : JoinPhase {
        val progress: Float get() = if (total > 0) done.toFloat() / total else -1f
    }

    /**
     * [saved] the files written, [pages] pages in all, [lines] what the card lists, [cover] the pages drawn on top of
     * the card (ids of [JoinUiState.pages]), [drawn] pages that could only be copied as pictures.
     */
    data class Done(
        val tool: JoinTool,
        val saved: List<SavedFile>,
        val pages: Int,
        val lines: List<JoinLine>,
        val cover: List<Long>,
        val drawn: Int = 0,
    ) : JoinPhase {
        val size: Long get() = saved.sumOf { it.size }
    }

    data class Failed(val message: String, val debug: String? = null) : JoinPhase
}

data class JoinUiState(
    val phase: JoinPhase = JoinPhase.Idle,
    /** Files are being copied and read (the pages already there stay usable). */
    val reading: Boolean = false,
    val sources: List<JoinSource> = emptyList(),
    val pages: List<JoinPage> = emptyList(),
    val tool: JoinTool = JoinTool.JOIN,
    val split: SplitMode = SplitMode.EACH,
    /** [JoinTool.JOIN]: the page whose actions (move, turn, remove) are shown. */
    val selected: Long? = null,
    /** [JoinTool.SPLIT] with [SplitMode.CUTS]: pages that start a new PDF. */
    val cuts: Set<Long> = emptySet(),
    /** [JoinTool.REMOVE]: pages left out. */
    val marked: Set<Long> = emptySet(),
    /** A message about the last files added (refused ones, limits), shown above the pages. */
    val notice: String? = null,
) {
    /** What the button would make right now. */
    val parts: List<JoinPart>
        get() = JoinPlan.parts(tool, pages, sources.map { it.file.name }, split, cuts, marked)

    /** Why the button is off, or null when Gulp can go. */
    val blocker: String?
        get() = when {
            pages.isEmpty() -> "Ajoutez un PDF ou une photo."
            tool == JoinTool.REMOVE && marked.isEmpty() -> "Touchez les pages à retirer."
            tool == JoinTool.REMOVE && pages.all { it.id in marked } -> "Gardez au moins une page."
            tool == JoinTool.SPLIT && pages.size < 2 -> "Il faut au moins deux pages pour séparer."
            tool == JoinTool.SPLIT && parts.size < 2 -> "Touchez la page où commence le PDF suivant."
            else -> null
        }
}

/**
 * The Assembleur PDF's state and work, held by the App: the pages survive leaving the tool. Each file given is copied
 * once into a work dir kept for the session (the pages are drawn from it, and copied from it into the results);
 * [reset] deletes it. The results are written by [PdfAssembler].
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
    private var thumbJob: Job? = null
    private val nextId = AtomicLong(1)
    private val generation = AtomicInteger(0)

    /** Bumped by [reset]: thumbnails drawn for the files of an earlier session are thrown away. */
    private val session = AtomicInteger(0)

    /** PdfRenderer opens one page at a time: thumbnails and the build never draw together. */
    private val renderLock = Mutex()

    init {
        // PdfBox reads its glyph lists and standard fonts from the app's assets.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    val busy: Boolean get() = _state.value.phase is JoinPhase.Working

    /** Adds the chosen PDF and photos after the pages already there. False while the PDF is being written. */
    fun add(uris: List<Uri>): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        val previous = readJob
        val mine = session.get()
        _state.update { it.copy(reading = true, notice = null, phase = if (it.phase is JoinPhase.Ready || it.phase == JoinPhase.Idle) it.phase else JoinPhase.Ready) }
        readJob = scope.launch(Dispatchers.IO) {
            previous?.join()
            val notes = ArrayList<String>()
            val added = ArrayList<Pair<JoinSource, List<JoinPage>>>()
            try {
                val folder = dir ?: WorkDir.create(context).also { dir = it }
                var sourceCount = _state.value.sources.size
                var pageCount = _state.value.pages.size
                for (uri in uris.distinct()) {
                    coroutineContext.ensureActive()
                    if (sourceCount >= JoinPlan.MAX_FILES) {
                        notes += "${JoinPlan.MAX_FILES} fichiers au plus."
                        break
                    }
                    if (pageCount >= JoinPlan.MAX_PAGES) {
                        notes += "${JoinPlan.MAX_PAGES} pages au plus."
                        break
                    }
                    val result = try {
                        read(uri, folder, sourceCount)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        notes += e.message ?: "Un fichier n'a pas pu être lu."
                        null
                    } ?: continue
                    val room = JoinPlan.MAX_PAGES - pageCount
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
            if (session.get() != mine) {
                // The user started over meanwhile: these copies belong to nothing.
                added.forEach { it.first.work.delete() }
                return@launch
            }
            _state.update { s ->
                val pages = s.pages + added.flatMap { it.second }
                s.copy(
                    phase = when {
                        s.phase is JoinPhase.Working -> s.phase
                        pages.isEmpty() -> JoinPhase.Idle
                        else -> JoinPhase.Ready
                    },
                    reading = false,
                    sources = s.sources + added.map { it.first },
                    pages = pages,
                    notice = notes.distinct().joinToString("\n").ifEmpty { null },
                )
            }
            val fresh = added.flatMap { it.second }
            // Drawn on the side: adding more files does not wait for the thumbnails of the last ones.
            val before = thumbJob
            thumbJob = scope.launch(Dispatchers.IO) {
                before?.join()
                renderThumbs(fresh, mine)
            }
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
            throw IOException("« ${info.name} » est trop gros (${if (pdf) "200 Mo" else "64 Mo"} au plus).")
        }
        try {
            val pages = if (pdf) pdfPages(work, index, info.name) else listOf(photoPage(work, index, info.name))
            return JoinSource(info, pdf, work, pages.size) to pages
        } catch (e: Exception) {
            work.delete()
            throw e
        }
    }

    /** The pages of a PDF as Android draws them; PdfBox counts them when Android cannot open the file. */
    private fun pdfPages(file: File, source: Int, name: String): List<JoinPage> {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: SecurityException) {
            fd.close()
            throw IOException("« $name » est protégé par un mot de passe.")
        } catch (e: Exception) {
            fd.close()
            return pdfBoxPages(file, source, name)
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

    private fun pdfBoxPages(file: File, source: Int, name: String): List<JoinPage> {
        val doc = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            throw IOException("« $name » est protégé par un mot de passe.")
        } catch (e: Exception) {
            throw IOException("« $name » est illisible ou abîmé.")
        }
        doc.use {
            if (it.numberOfPages == 0) throw IOException("« $name » n'a aucune page.")
            return (0 until it.numberOfPages).map { i ->
                val page = it.getPage(i)
                val box = page.cropBox
                val turned = JoinPlan.pdfRotation(page.rotation, 0) % 180 != 0
                val w = if (turned) box.height else box.width
                val h = if (turned) box.width else box.height
                JoinPage(nextId.getAndIncrement(), source, i, w, h)
            }
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

    private suspend fun renderThumbs(pages: List<JoinPage>, mine: Int) {
        for (page in pages) {
            coroutineContext.ensureActive()
            if (session.get() != mine) return
            // A page removed before its turn needs no picture.
            if (_state.value.pages.none { it.id == page.id }) continue
            val bitmap = runCatching { draw(page, THUMB_WIDTH) }.getOrNull() ?: continue
            // Half the memory (a thumbnail has no transparency): 300 pages stay around 20 MB.
            val small = bitmap.copy(Bitmap.Config.RGB_565, false)?.also { bitmap.recycle() } ?: bitmap
            if (session.get() != mine) return small.recycle()
            _thumbs.update { it + (page.id to small) }
        }
    }

    /** Page [page] (unturned) drawn [width] pixels wide for a thumbnail, or at [JoinPlan.renderSize] when [width] is 0. */
    private suspend fun draw(page: JoinPage, width: Int): Bitmap {
        val source = _state.value.sources.getOrNull(page.source) ?: throw IOException("Fichier disparu.")
        return if (source.pdf) {
            renderLock.withLock { renderPdf(source.work, page, width) }
        } else {
            val (rw, rh) = JoinPlan.renderSize(page.widthPt, page.heightPt)
            Images.decode(source.work, if (width > 0) width * 2 else max(rw, rh))
        }
    }

    private fun renderPdf(file: File, page: JoinPage, width: Int): Bitmap {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = try {
                PdfRenderer(fd)
            } catch (e: Exception) {
                throw ConversionError("Android ne sait pas dessiner les pages de ce PDF.")
            }
            try {
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
            } finally {
                renderer.close()
            }
        } finally {
            fd.close()
        }
    }

    // MARK: - Editing

    fun setTool(tool: JoinTool) = edit { it.copy(tool = tool, selected = null) }

    fun setSplit(mode: SplitMode) = edit { it.copy(split = mode) }

    /** A tap on page [id]: selects it (Assembler), cuts before it (Séparer), marks it (Retirer). */
    fun tap(id: Long) = edit { s ->
        when (s.tool) {
            JoinTool.JOIN -> s.copy(selected = if (s.selected == id) null else id)
            JoinTool.SPLIT -> {
                if (s.pages.firstOrNull()?.id == id) return@edit s
                s.copy(split = SplitMode.CUTS, cuts = if (s.split == SplitMode.CUTS) JoinPlan.toggle(s.cuts, id) else setOf(id))
            }
            JoinTool.REMOVE -> s.copy(marked = JoinPlan.toggle(s.marked, id))
        }
    }

    fun move(id: Long, delta: Int) = edit { it.copy(pages = JoinPlan.move(it.pages, id, delta)) }

    fun moveTo(id: Long, index: Int) = edit { it.copy(pages = JoinPlan.moveTo(it.pages, id, index)) }

    fun rotate(id: Long) = edit { it.copy(pages = JoinPlan.rotate(it.pages, id)) }

    fun remove(id: Long) = edit {
        val pages = JoinPlan.remove(it.pages, id)
        it.copy(
            pages = pages,
            selected = null,
            cuts = it.cuts - id,
            marked = it.marked - id,
            phase = if (pages.isEmpty() && !it.reading) JoinPhase.Idle else it.phase,
        )
    }

    private inline fun edit(crossinline change: (JoinUiState) -> JoinUiState) {
        _state.update { if (it.phase == JoinPhase.Ready) change(it) else it }
    }

    /** From a result or an error, back to the same pages. */
    fun backToPages() {
        _state.update { if (it.pages.isNotEmpty() && !busy) it.copy(phase = JoinPhase.Ready) else it }
    }

    /** Forgets every file and deletes the session's copies. */
    fun reset() {
        if (busy) return
        session.incrementAndGet()
        readJob?.cancel()
        thumbJob?.cancel()
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

    // MARK: - Writing

    fun start() {
        val current = _state.value
        if (busy || (current.phase != JoinPhase.Ready && current.phase !is JoinPhase.Failed) || current.reading || current.blocker != null) return
        val parts = current.parts
        if (parts.isEmpty()) return
        val token = generation.incrementAndGet()
        val total = parts.sumOf { it.pages.size }
        _state.update { it.copy(phase = JoinPhase.Working(0, total), selected = null) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val work = dir ?: return@launch fail(token, "Les fichiers ne sont plus là : ajoutez-les à nouveau.")
            val outDir = File(work, "out")
            try {
                outDir.mkdirs()
                val saved = ArrayList<SavedFile>()
                var done = 0
                var drawn = 0
                PdfAssembler(current.sources) { page -> draw(page, 0) }.use { assembler ->
                    for (part in parts) {
                        coroutineContext.ensureActive()
                        val out = FileIntake.uniqueFile(outDir, part.name)
                        assembler.write(part.pages, out) { n -> progress(token, done + n, total) }
                        done += part.pages.size
                        coroutineContext.ensureActive()
                        saved += MediaExporter.export(context, out).also { out.delete() }
                    }
                    drawn = assembler.rasterized
                }
                val result = JoinPhase.Done(
                    tool = current.tool,
                    saved = saved,
                    pages = total,
                    lines = lines(current, parts, saved),
                    cover = parts.first().pages.take(3).map { it.id },
                    drawn = drawn,
                )
                update(token) { it.copy(phase = result) }
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
                // Nothing a file does may take the app (and the running downloads) down with it.
                fail(token, "Le PDF n'a pas pu être créé.", e.toString())
            } finally {
                withContext(NonCancellable) { outDir.deleteRecursively() }
            }
        }
    }

    /** The lines of the "done" card. */
    private fun lines(state: JoinUiState, parts: List<JoinPart>, saved: List<SavedFile>): List<JoinLine> {
        if (state.tool == JoinTool.SPLIT) {
            var start = 1
            return parts.mapIndexed { i, part ->
                val places = (start until start + part.pages.size).toList()
                start += part.pages.size
                JoinLine(saved.getOrNull(i)?.name ?: part.name, pdf = true, detail = JoinPlan.describePlaces(places))
            }
        }
        val part = parts.single()
        return JoinPlan.placements(part.pages).map { (source, places) ->
            val file = state.sources[source]
            val detail = JoinPlan.describePlaces(places) + if (file.pdf) "" else ", mise au format A4"
            JoinLine(file.file.name, file.pdf, detail)
        }
    }

    private inline fun update(token: Int, crossinline change: (JoinUiState) -> JoinUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, done: Int, total: Int) {
        update(token) { s -> if (s.phase is JoinPhase.Working) s.copy(phase = JoinPhase.Working(done, total)) else s }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = JoinPhase.Failed(message, debug)) }
    }

    private companion object {
        const val PDF_MAX_BYTES: Long = 200L * 1024 * 1024
        const val IMAGE_MAX_BYTES: Long = 64L * 1024 * 1024
        const val THUMB_WIDTH = 160
    }
}
