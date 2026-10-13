package com.powermaxv2.ytdownloader.core.signpdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.powermaxv2.ytdownloader.core.preview.PdfPages
import com.powermaxv2.ytdownloader.core.preview.PdfUnreadable
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** The PDF being signed: its name, its copy in the work dir, its page count. */
data class SignDoc(val name: String, val work: File, val pages: Int)

sealed interface SignPhase {
    /** No PDF yet (the signature can be drawn or changed). */
    data object Idle : SignPhase

    /** The chosen PDF is being copied and opened. */
    data object Reading : SignPhase

    /** The PDF is shown: the signature can be placed on a page. */
    data object Ready : SignPhase

    data class Working(val done: Int, val total: Int) : SignPhase {
        val progress: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    /** [page] (0-based) of [pages] was signed; the copy is [saved]. */
    data class Done(val saved: SavedFile, val page: Int, val pages: Int, val paraphs: Int) : SignPhase

    data class Failed(val message: String, val debug: String? = null) : SignPhase
}

data class SignUiState(
    val phase: SignPhase = SignPhase.Idle,
    /** The saved signature, null until one is drawn. */
    val signature: SavedSignature? = null,
    /** The drawing pad is open. */
    val drawing: Boolean = false,
    val ink: Int = SignatureStore.INK_BLACK,
    val doc: SignDoc? = null,
    /** The page shown and signed (0-based). */
    val page: Int = 0,
    /** Width / height of [page] as shown. */
    val pageAspect: Float = 0.707f,
    /** Where the signature sits on [page]; null until a PDF is open. */
    val box: SignBox? = null,
    val stamp: SignStamp = SignStamp(),
) {
    val captioned: Boolean get() = SignPlan.caption(stamp, LocalDate.now()) != null
    val signatureAspect: Float get() = signature?.aspect ?: 3f
}

/**
 * The Signature PDF tool's state and work, held by the App: the PDF stays open when the user leaves the tool, and a
 * signing goes on. The signature itself is saved for good in [SignatureStore].
 */
class SignController(private val context: Context, private val scope: CoroutineScope) {
    private val store = SignatureStore(context)
    private val _state = MutableStateFlow(SignUiState(signature = store.current()))
    val state: StateFlow<SignUiState> = _state.asStateFlow()

    private var pdf: PdfPages? = null
    private var dir: File? = null
    private var job: Job? = null

    /** Bumped when the user starts over: work from before is dropped. */
    private val generation = AtomicInteger(0)

    /** PdfPages draws one page at a time. */
    private val renderLock = Mutex()

    init {
        // PdfBox reads its glyph lists and standard fonts from the app's assets.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    val busy: Boolean get() = _state.value.phase is SignPhase.Working

    // MARK: - Signature

    fun openPad() = _state.update { it.copy(drawing = true) }

    fun closePad() = _state.update { it.copy(drawing = false) }

    fun setInk(ink: Int) = _state.update { it.copy(ink = ink) }

    /** Saves the drawn [strokes] as the signature; false when they are too little to be one. */
    fun saveSignature(strokes: List<List<SignPoint>>, padAspect: Float): Boolean {
        if (!SignPlan.isSignature(strokes)) return false
        val ink = _state.value.ink
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { store.save(strokes, padAspect, ink) }.getOrNull()
            _state.update { s ->
                val signature = saved ?: s.signature
                s.copy(
                    signature = signature,
                    drawing = saved == null,
                    // The new signature has its own shape: the box keeps its corner and width, its height follows.
                    box = s.box?.let { SignPlan.clamp(it, signature?.aspect ?: 3f, s.pageAspect, s.captioned) },
                )
            }
        }
        return true
    }

    fun deleteSignature() {
        scope.launch(Dispatchers.IO) {
            store.delete()
            _state.update { it.copy(signature = null) }
        }
    }

    /** The signature's picture, decoded (null when none). Blocking: call off the main thread. */
    fun signatureBitmap(): Bitmap? = store.load()

    // MARK: - PDF

    /** Opens the chosen PDF (any open one is closed). False while a signed copy is being written. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        val token = generation.incrementAndGet()
        job?.cancel()
        closeDoc()
        _state.update { it.copy(phase = SignPhase.Reading, doc = null, box = null, page = 0) }
        job = scope.launch(Dispatchers.IO) {
            var folder: File? = null
            try {
                val info = FileIntake.describe(context, uri)
                val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
                if (FileKind.detect(info.name, info.mime, head) != FileKind.PDF) throw IOException("« ${info.name} » n'est pas un PDF.")
                folder = WorkDir.create(context)
                val work = try {
                    FileIntake.copyToWork(context, uri, PDF_MAX_BYTES, folder)
                } catch (e: FileTooLarge) {
                    throw IOException("Ce PDF est trop gros (200 Mo au plus).")
                }
                val pages = PdfPages.open(context, uri)
                if (generation.get() != token) {
                    pages.close()
                    WorkDir.delete(context, folder)
                    return@launch
                }
                if (pages.pageCount == 0) {
                    pages.close()
                    throw IOException("Ce PDF n'a aucune page.")
                }
                pdf = pages
                dir = folder
                // A letter is signed on its last page: start there.
                val start = pages.pageCount - 1
                val aspect = renderLock.withLock { pages.aspect(start) }
                _state.update { s ->
                    s.copy(
                        phase = SignPhase.Ready,
                        doc = SignDoc(info.name, work, pages.pageCount),
                        page = start,
                        pageAspect = aspect,
                        box = SignPlan.startBox(s.signatureAspect, aspect, s.captioned),
                    )
                }
            } catch (e: CancellationException) {
                folder?.let { WorkDir.delete(context, it) }
                throw e
            } catch (e: Exception) {
                folder?.let { WorkDir.delete(context, it) }
                if (generation.get() == token) {
                    val message = when (e) {
                        is PdfUnreadable, is IOException -> e.message
                        else -> null
                    } ?: "Ce PDF n'a pas pu être ouvert."
                    _state.update { it.copy(phase = SignPhase.Failed(message, e.toString()), doc = null) }
                }
            }
        }
        return true
    }

    /** Page [index] drawn [widthPx] wide, or null (no PDF, or it was closed). Suspends off the main thread. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderLock.withLock { pdf?.render(index, widthPx) }
    }

    fun setPage(index: Int) {
        val doc = _state.value.doc ?: return
        if (busy || index !in 0 until doc.pages) return
        scope.launch(Dispatchers.IO) {
            val aspect = renderLock.withLock { pdf?.aspect(index) } ?: return@launch
            _state.update { s ->
                s.copy(page = index, pageAspect = aspect, box = s.box?.let { SignPlan.clamp(it, s.signatureAspect, aspect, s.captioned) })
            }
        }
    }

    fun move(dx: Float, dy: Float) = _state.update { s ->
        s.copy(box = s.box?.let { SignPlan.moved(it, dx, dy, s.signatureAspect, s.pageAspect, s.captioned) })
    }

    fun resize(dx: Float) = _state.update { s ->
        s.copy(box = s.box?.let { SignPlan.resized(it, dx, s.signatureAspect, s.pageAspect, s.captioned) })
    }

    fun scale(factor: Float) = _state.update { s ->
        s.copy(box = s.box?.let { SignPlan.scaled(it, factor, s.signatureAspect, s.pageAspect, s.captioned) })
    }

    fun setStamp(stamp: SignStamp) = _state.update { s ->
        val next = s.copy(stamp = stamp)
        next.copy(box = s.box?.let { SignPlan.clamp(it, next.signatureAspect, next.pageAspect, next.captioned) })
    }

    /** Writes the signed copy into Download/Gulp. */
    fun start() {
        val s = _state.value
        val doc = s.doc ?: return
        val box = s.box ?: return
        if (busy || s.signature == null) return
        val token = generation.get()
        _state.update { it.copy(phase = SignPhase.Working(0, doc.pages)) }
        job = scope.launch(Dispatchers.IO) {
            val outDir = WorkDir.create(context)
            try {
                val signature = store.load() ?: throw ConversionError("La signature enregistrée est introuvable : dessinez-la à nouveau.")
                val out = File(outDir, SafeNames.withExtension(SafeNames.baseOf(doc.name) + " signé", "pdf"))
                try {
                    SignWriter.write(doc.work, out, s.page, box, signature, s.stamp, LocalDate.now()) { done, total ->
                        if (generation.get() == token) _state.update { it.copy(phase = SignPhase.Working(done, total)) }
                    }
                } finally {
                    signature.recycle()
                }
                val saved = MediaExporter.export(context, out)
                val paraphs = if (s.stamp.initialsEverywhere && s.stamp.cleanInitials.isNotEmpty()) doc.pages - 1 else 0
                if (generation.get() == token) _state.update { it.copy(phase = SignPhase.Done(saved, s.page, doc.pages, paraphs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: "Le PDF n'a pas pu être signé.", e.debug)
            } catch (e: OutOfMemoryError) {
                fail(token, "Ce PDF est trop lourd pour la mémoire du téléphone.", e.toString())
            } catch (e: Exception) {
                fail(token, "Le PDF n'a pas pu être signé.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, outDir) }
            }
        }
    }

    fun cancel() {
        if (!busy) return
        job?.cancel()
        _state.update { it.copy(phase = SignPhase.Ready) }
    }

    /** Back to the page after a failure or a signed copy, to sign again elsewhere. */
    fun backToPage() = _state.update { s -> if (s.doc != null) s.copy(phase = SignPhase.Ready) else s.copy(phase = SignPhase.Idle) }

    /** Closes the PDF and starts over (the signature stays). */
    fun reset() {
        generation.incrementAndGet()
        job?.cancel()
        closeDoc()
        _state.update { SignUiState(signature = it.signature, ink = it.ink, stamp = it.stamp) }
    }

    private fun closeDoc() {
        pdf?.let { pages -> scope.launch(Dispatchers.IO) { pages.close() } }
        pdf = null
        dir?.let { WorkDir.delete(context, it) }
        dir = null
    }

    private fun fail(token: Int, message: String, debug: String?) {
        if (generation.get() == token) _state.update { it.copy(phase = SignPhase.Failed(message, debug)) }
    }

    private companion object {
        const val PDF_MAX_BYTES: Long = 200L * 1024 * 1024
    }
}
