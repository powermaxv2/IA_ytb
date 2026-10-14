package com.powermaxv2.ytdownloader.core.blur

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The chosen photo, decoded upright (longest side ≤ [BlurController.MAX_SIDE]). */
class BlurPhoto(val name: String, val bitmap: Bitmap) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
    val aspect: Float get() = width.toFloat() / height
}

sealed interface BlurPhase {
    data object Idle : BlurPhase

    data object Reading : BlurPhase

    data class Unsupported(val message: String) : BlurPhase

    data object Ready : BlurPhase

    data object Saving : BlurPhase

    data class Done(val saved: SavedFile) : BlurPhase

    data class Failed(val message: String, val debug: String? = null) : BlurPhase
}

/** Where the face search is. */
sealed interface FaceSearch {
    data object Idle : FaceSearch

    data object Searching : FaceSearch

    /** [added] new zones (0: none found, or all already hidden). */
    data class Found(val added: Int) : FaceSearch
}

data class BlurUiState(
    val phase: BlurPhase = BlurPhase.Idle,
    val photo: BlurPhoto? = null,
    val zones: List<BlurZone> = emptyList(),
    /** Id of the zone the handles are on, or null. */
    val selected: Int? = null,
    val options: BlurOptions = BlurOptions(),
    /** The photo with its zones hidden, at most [BlurController.PREVIEW_SIDE] px. */
    val preview: Bitmap? = null,
    val faces: FaceSearch = FaceSearch.Idle,
    val canUndo: Boolean = false,
)

/**
 * State and work of "Flouter une photo". The photo is copied, decoded and dropped; every change of the zones or
 * options redraws the preview off the main thread (the latest change wins); saving hides the zones on the full
 * photo and writes a JPEG to Pictures/Gulp. A JPEG written by Android carries no EXIF: place, phone and date go too.
 */
class BlurController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(BlurUiState())
    val state: StateFlow<BlurUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var previewJob: Job? = null
    private var faceJob: Job? = null
    private var nextId = 1
    private val history = ArrayDeque<List<BlurZone>>()

    val busy: Boolean get() = _state.value.phase == BlurPhase.Saving

    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        previewJob?.cancel()
        faceJob?.cancel()
        history.clear()
        _state.update { BlurUiState(BlurPhase.Reading, options = it.options) }
        readJob = scope.launch(Dispatchers.IO) {
            val next: BlurUiState = try {
                val photo = read(uri)
                BlurUiState(BlurPhase.Ready, photo, preview = BlurRender.render(photo.bitmap, emptyList(), BlurOptions(), PREVIEW_SIDE))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                BlurUiState(BlurPhase.Unsupported("Android a refusé l'accès : choisis la photo à nouveau."))
            } catch (e: FileTooLarge) {
                BlurUiState(BlurPhase.Unsupported(e.message ?: "Photo trop volumineuse."))
            } catch (e: UnsupportedSource) {
                BlurUiState(BlurPhase.Unsupported(e.message ?: "Cette photo ne peut pas être ouverte."))
            } catch (e: ConversionError) {
                BlurUiState(BlurPhase.Unsupported(e.message ?: "Photo illisible."))
            } catch (e: OutOfMemoryError) {
                BlurUiState(BlurPhase.Unsupported("Pas assez de mémoire pour cette photo."))
            } catch (e: Exception) {
                BlurUiState(BlurPhase.Unsupported(e.message ?: "Impossible de lire cette photo."))
            }
            _state.update { current -> if (current.phase == BlurPhase.Saving) current else next.copy(options = current.options) }
        }
        return true
    }

    private fun read(uri: Uri): BlurPhoto {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE) {
            throw ConversionError("« ${info.name} » n'est pas une photo. Choisis un JPG, PNG, WebP ou HEIC.")
        }
        val dir = WorkDir.create(context)
        try {
            val copy = FileIntake.copyToWork(context, uri, MAX_BYTES, dir)
            return BlurPhoto(info.name, Images.decode(copy, MAX_SIDE))
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    // MARK: - Zones

    /** A zone drawn by a finger's drag, between two points in 0..1 of the photo. */
    fun draw(x0: Float, y0: Float, x1: Float, y1: Float) {
        val s = _state.value
        if (!editable(s) || s.zones.size >= BlurPlan.MAX_ZONES) return
        val zone = BlurPlan.fromDrag(nextId++, x0, y0, x1, y1, s.options.shape)
        commit(s.zones + zone, selected = zone.id)
    }

    fun select(id: Int?) {
        _state.update { s -> s.copy(selected = id?.takeIf { wanted -> s.zones.any { it.id == wanted } }) }
    }

    /** Starts a move or resize: the zones before it go to the undo list once. */
    fun beginEdit() {
        if (editable(_state.value)) remember(_state.value.zones)
    }

    fun move(id: Int, dx: Float, dy: Float) = edit(id) { BlurPlan.move(it, dx, dy) }

    fun resize(id: Int, dw: Float, dh: Float) = edit(id) { BlurPlan.resize(it, dw, dh) }

    private fun edit(id: Int, change: (BlurZone) -> BlurZone) {
        val s = _state.value
        if (!editable(s)) return
        show(s.zones.map { if (it.id == id) change(it) else it }, selected = id)
    }

    fun delete(id: Int) {
        val s = _state.value
        if (!editable(s)) return
        commit(s.zones.filterNot { it.id == id }, selected = null)
    }

    fun clear() {
        val s = _state.value
        if (!editable(s) || s.zones.isEmpty()) return
        commit(emptyList(), selected = null)
    }

    fun undo() {
        val s = _state.value
        if (!editable(s)) return
        val previous = synchronized(history) { history.removeLastOrNull() } ?: return
        show(previous, selected = null)
    }

    /** Finds the faces of the photo and adds an oval zone on each one not hidden yet. */
    fun findFaces() {
        val s = _state.value
        val photo = s.photo ?: return
        if (!editable(s) || s.faces == FaceSearch.Searching) return
        _state.update { it.copy(faces = FaceSearch.Searching) }
        faceJob = scope.launch(Dispatchers.Default) {
            val found = try {
                BlurRender.findFaces(photo.bitmap, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            val current = _state.value
            if (current.photo !== photo) return@launch
            val added = BlurPlan.newFaces(current.zones, found, nextId).take(BlurPlan.MAX_ZONES - current.zones.size)
            nextId += added.size
            _state.update { it.copy(faces = FaceSearch.Found(added.size)) }
            if (added.isNotEmpty()) commit(current.zones + added, selected = null)
        }
    }

    fun setOptions(options: BlurOptions) {
        val s = _state.value
        if (!editable(s) || options == s.options) return
        // The shape chips also reshape the zone being edited.
        val zones = if (options.shape != s.options.shape && s.selected != null) {
            remember(s.zones)
            s.zones.map { if (it.id == s.selected) it.copy(shape = options.shape) else it }
        } else {
            s.zones
        }
        _state.update { it.copy(options = options, zones = zones, canUndo = history.isNotEmpty()) }
        redraw()
    }

    private fun editable(s: BlurUiState) = s.photo != null && s.phase == BlurPhase.Ready

    private fun remember(zones: List<BlurZone>) {
        // The face search commits from a background thread.
        synchronized(history) {
            history.addLast(zones)
            while (history.size > MAX_UNDO) history.removeFirst()
        }
    }

    private fun commit(zones: List<BlurZone>, selected: Int?) {
        remember(_state.value.zones)
        show(zones, selected)
    }

    private fun show(zones: List<BlurZone>, selected: Int?) {
        _state.update { it.copy(zones = zones, selected = selected, canUndo = history.isNotEmpty()) }
        redraw()
    }

    /** Redraws the preview shortly after the last change (a drag sends many). */
    private fun redraw() {
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.Default) {
            delay(PREVIEW_DELAY_MS)
            val s = _state.value
            val photo = s.photo ?: return@launch
            val preview = BlurRender.render(photo.bitmap, s.zones, s.options, PREVIEW_SIDE)
            _state.update { if (it.photo === photo && it.zones == s.zones && it.options == s.options) it.copy(preview = preview) else it }
        }
    }

    // MARK: - Saving

    fun save() {
        val current = _state.value
        val photo = current.photo ?: return
        if (busy || current.phase != BlurPhase.Ready || current.zones.isEmpty()) return
        _state.update { it.copy(phase = BlurPhase.Saving, selected = null) }
        scope.launch(Dispatchers.IO) {
            var dir: File? = null
            try {
                dir = WorkDir.create(context)
                val hidden = BlurRender.render(photo.bitmap, current.zones, current.options)
                val file = FileIntake.uniqueFile(dir, BlurPlan.outputName(photo.name))
                file.outputStream().use { out ->
                    if (!hidden.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw ConversionError("Impossible d'écrire la photo.")
                }
                hidden.recycle()
                val saved = MediaExporter.export(context, file)
                _state.update { it.copy(phase = BlurPhase.Done(saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                fail("Android a refusé l'enregistrement : autorise le stockage et réessaie.", e.toString())
            } catch (e: IOException) {
                fail("Écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail("Pas assez de mémoire pour cette photo.", e.toString())
            } catch (e: Throwable) {
                fail(e.message ?: "L'enregistrement a échoué.", e.toString())
            } finally {
                dir?.let { withContext(NonCancellable) { WorkDir.delete(context, it) } }
            }
        }
    }

    private fun fail(message: String, debug: String?) {
        _state.update { it.copy(phase = BlurPhase.Failed(message, debug)) }
    }

    /** From "done" or a failure back to the zones, same photo. */
    fun back() {
        _state.update { if (it.photo != null && !busy) it.copy(phase = BlurPhase.Ready) else it }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        previewJob?.cancel()
        faceJob?.cancel()
        history.clear()
        _state.update { BlurUiState(options = it.options) }
    }

    companion object {
        /** Longest side kept from the photo (≈ 7 MP, about 28 MB as pixels, copied once to save). */
        const val MAX_SIDE = 3072
        const val PREVIEW_SIDE = 1080
        private const val PREVIEW_DELAY_MS = 70L
        private const val MAX_UNDO = 30
        private const val MAX_BYTES: Long = 64L * 1024 * 1024
        private const val JPEG_QUALITY = 92
    }
}

/** The one controller of the tool, made on first use with the application context. */
object BlurHolder {
    @Volatile private var controller: BlurController? = null

    fun get(context: Context): BlurController = controller ?: synchronized(this) {
        controller ?: BlurController(
            context.applicationContext,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { controller = it }
    }
}
