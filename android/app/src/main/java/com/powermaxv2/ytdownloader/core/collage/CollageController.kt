package com.powermaxv2.ytdownloader.core.collage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** One chosen photo, decoded upright (longest side ≤ [CollageController.MAX_SIDE]), and its main colour. */
class CollagePhoto(val id: Long, val name: String, val bitmap: Bitmap, val color: Int) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

sealed interface CollagePhase {
    data object Idle : CollagePhase

    /** Photos are being read; [count] is how many were picked. */
    data class Reading(val count: Int) : CollagePhase

    data class Unsupported(val message: String) : CollagePhase

    data object Ready : CollagePhase

    data object Saving : CollagePhase

    data class Done(val saved: SavedFile) : CollagePhase

    data class Failed(val message: String, val debug: String? = null) : CollagePhase
}

data class CollageUiState(
    val phase: CollagePhase = CollagePhase.Idle,
    /** The photos, in the collage's order. */
    val photos: List<CollagePhoto> = emptyList(),
    val options: CollageOptions = CollageOptions(),
    /** Position of the photo picked for a swap (the next touched photo trades places with it). */
    val selected: Int? = null,
    /** A line about the last pick ("2 photos illisibles ignorées", "9 photos au maximum"). */
    val notice: String? = null,
) {
    val canAdd: Boolean get() = photos.size < Collage.MAX_PHOTOS
    val enough: Boolean get() = photos.size >= Collage.MIN_PHOTOS
}

/**
 * State and work of "Collage photos". Picked photos are copied one by one into a work dir, decoded and dropped;
 * the screen draws the live preview itself from [CollageUiState] (no bitmap per option change); saving draws the
 * full picture with [CollageRender] as JPEG into Pictures/Gulp. Held by [CollageHolder] so the photos stay when
 * the user goes home and comes back.
 */
class CollageController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(CollageUiState())
    val state: StateFlow<CollageUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var nextId = 1L

    val busy: Boolean get() = _state.value.phase == CollagePhase.Saving || _state.value.phase is CollagePhase.Reading

    /** Reads [uris]: they replace the photos, or join them when [add]. False when busy. */
    fun pick(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy || uris.isEmpty()) return false
        val keep = if (add) _state.value.photos else emptyList()
        val room = Collage.MAX_PHOTOS - keep.size
        val wanted = uris.take(room.coerceAtLeast(0))
        if (wanted.isEmpty()) {
            _state.update { it.copy(notice = "${Collage.MAX_PHOTOS} photos au maximum.") }
            return true
        }
        _state.update { it.copy(phase = CollagePhase.Reading(wanted.size), selected = null, notice = null) }
        readJob = scope.launch(Dispatchers.IO) {
            val read = ArrayList<CollagePhoto>()
            var failed = 0
            var lastError: String? = null
            for (uri in wanted) {
                try {
                    read += read(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    failed++
                    lastError = "Pas assez de mémoire pour toutes ces photos."
                } catch (e: Exception) {
                    failed++
                    lastError = when (e) {
                        is SecurityException -> "Android a refusé l'accès : choisis les photos à nouveau."
                        else -> e.message ?: "Photo illisible."
                    }
                }
            }
            val photos = keep + read
            val notices = buildList {
                if (failed > 0) add(if (failed == 1) "1 photo n'a pas pu être lue." else "$failed photos n'ont pas pu être lues.")
                if (uris.size > wanted.size) add("${Collage.MAX_PHOTOS} photos au maximum : les autres sont ignorées.")
            }
            _state.update { current ->
                when {
                    photos.isEmpty() -> CollageUiState(CollagePhase.Unsupported(lastError ?: "Aucune photo lisible."), options = current.options)
                    else -> current.copy(phase = CollagePhase.Ready, photos = photos, notice = notices.joinToString(" ").ifBlank { null })
                }
            }
        }
        return true
    }

    private fun read(uri: Uri): CollagePhoto {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE) {
            throw ConversionError("« ${info.name} » n'est pas une photo.")
        }
        val dir = WorkDir.create(context)
        try {
            val copy = FileIntake.copyToWork(context, uri, MAX_BYTES, dir)
            val bitmap = Images.decode(copy, MAX_SIDE)
            return CollagePhoto(nextId++, info.name, bitmap, CollageRender.mainColor(bitmap))
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    fun setOptions(options: CollageOptions) {
        _state.update { if (it.phase == CollagePhase.Saving) it else it.copy(options = options) }
    }

    /**
     * A touch on the photo at [position]: picks it for a swap, swaps it with the one already picked, or drops the
     * pick when it is the same photo.
     */
    fun touch(position: Int) {
        _state.update { s ->
            if (s.phase != CollagePhase.Ready || position !in s.photos.indices) return@update s
            when (val first = s.selected) {
                null -> s.copy(selected = position)
                position -> s.copy(selected = null)
                else -> s.copy(photos = Collage.swap(s.photos, first, position), selected = null)
            }
        }
    }

    /** Moves the photo at [position] one place earlier ([step] = -1) or later (+1). */
    fun shift(position: Int, step: Int) {
        _state.update { s ->
            if (s.phase != CollagePhase.Ready) return@update s
            val to = position + step
            if (to !in s.photos.indices) return@update s
            s.copy(photos = Collage.move(s.photos, position, to), selected = s.selected?.let { if (it == position) to else it })
        }
    }

    fun remove(position: Int) {
        _state.update { s ->
            if (s.phase != CollagePhase.Ready || position !in s.photos.indices) return@update s
            val photos = s.photos.toMutableList().also { it.removeAt(position) }
            if (photos.isEmpty()) CollageUiState(options = s.options) else s.copy(photos = photos, selected = null, notice = null)
        }
    }

    /** New order of the photos (and new tilts for the pêle-mêle). */
    fun shuffle() {
        _state.update { s ->
            if (s.phase != CollagePhase.Ready) return@update s
            val seed = s.options.seed + 1
            s.copy(photos = Collage.shuffle(s.photos, seed), options = s.options.copy(seed = seed), selected = null)
        }
    }

    fun save() {
        val current = _state.value
        if (busy || !current.enough) return
        _state.update { it.copy(phase = CollagePhase.Saving, selected = null) }
        scope.launch(Dispatchers.IO) {
            var dir: File? = null
            try {
                dir = WorkDir.create(context)
                val picture = CollageRender.render(
                    current.photos.map { it.bitmap },
                    current.photos.map { it.color },
                    current.options,
                    Collage.OUTPUT_LONG_SIDE,
                )
                val file = FileIntake.uniqueFile(dir, Collage.outputName(current.photos.size))
                file.outputStream().use { out ->
                    if (!picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw ConversionError("Impossible d'écrire le collage.")
                }
                picture.recycle()
                val saved = MediaExporter.export(context, file)
                _state.update { it.copy(phase = CollagePhase.Done(saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                fail("Android a refusé l'enregistrement : autorise le stockage et réessaie.", e.toString())
            } catch (e: IOException) {
                fail("Écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail("Pas assez de mémoire pour ce collage.", e.toString())
            } catch (e: Throwable) {
                fail(e.message ?: "L'enregistrement a échoué.", e.toString())
            } finally {
                dir?.let { withContext(NonCancellable) { WorkDir.delete(context, it) } }
            }
        }
    }

    private fun fail(message: String, debug: String?) {
        _state.update { it.copy(phase = CollagePhase.Failed(message, debug)) }
    }

    /** From "done" or a failure back to the settings, same photos. */
    fun back() {
        _state.update { if (it.photos.isNotEmpty() && it.phase != CollagePhase.Saving) it.copy(phase = CollagePhase.Ready) else it }
    }

    fun reset() {
        if (_state.value.phase == CollagePhase.Saving) return
        readJob?.cancel()
        _state.update { CollageUiState(options = it.options) }
    }

    companion object {
        /** Longest side kept from each photo: enough for the big cell of a 2048 px collage, light enough for 9. */
        const val MAX_SIDE = 1440
        private const val MAX_BYTES: Long = 64L * 1024 * 1024
        private const val JPEG_QUALITY = 92
    }
}

/** The one controller of the tool, made on first use with the application context. */
object CollageHolder {
    @Volatile private var controller: CollageController? = null

    fun get(context: Context): CollageController = controller ?: synchronized(this) {
        controller ?: CollageController(
            context.applicationContext,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { controller = it }
    }
}
