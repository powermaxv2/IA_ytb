package com.powermaxv2.ytdownloader.core.profilefit

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The chosen photo, decoded upright (longest side ≤ [ProfileFitController.MAX_SIDE]), and its backdrops. */
class FitPhoto(val name: String, val bitmap: Bitmap, val backdrops: ProfileRender.Backdrops) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

sealed interface FitPhase {
    data object Idle : FitPhase

    data object Reading : FitPhase

    data class Unsupported(val message: String) : FitPhase

    data object Ready : FitPhase

    data object Saving : FitPhase

    data class Done(val saved: SavedFile) : FitPhase

    data class Failed(val message: String, val debug: String? = null) : FitPhase
}

data class FitUiState(
    val phase: FitPhase = FitPhase.Idle,
    val photo: FitPhoto? = null,
    val options: FitOptions = FitOptions(),
    /** The square result for [options], at [ProfileFit.PREVIEW_SIDE] (drawn in the round on screen). */
    val preview: Bitmap? = null,
)

/**
 * State and work of "Photo de profil entière". The photo is copied into a work dir, decoded, and dropped; the
 * preview is redrawn off the main thread at every option change; saving draws the 1080 px square as JPEG into
 * Pictures/Gulp. Held by [ProfileFitHolder] so the photo stays when the user goes home and comes back.
 */
class ProfileFitController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(FitUiState())
    val state: StateFlow<FitUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var previewJob: Job? = null
    private var saveJob: Job? = null

    val busy: Boolean get() = _state.value.phase == FitPhase.Saving

    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        previewJob?.cancel()
        _state.update { it.copy(phase = FitPhase.Reading, photo = null, preview = null) }
        readJob = scope.launch(Dispatchers.IO) {
            val next: FitUiState = try {
                val photo = read(uri)
                val options = _state.value.options
                FitUiState(FitPhase.Ready, photo, options, ProfileRender.render(photo.bitmap, photo.backdrops, ProfileFit.PREVIEW_SIDE, options))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                FitUiState(FitPhase.Unsupported("Android a refusé l'accès : choisis la photo à nouveau."))
            } catch (e: FileTooLarge) {
                FitUiState(FitPhase.Unsupported(e.message ?: "Photo trop volumineuse."))
            } catch (e: UnsupportedSource) {
                FitUiState(FitPhase.Unsupported(e.message ?: "Cette photo ne peut pas être ouverte."))
            } catch (e: ConversionError) {
                FitUiState(FitPhase.Unsupported(e.message ?: "Photo illisible."))
            } catch (e: OutOfMemoryError) {
                FitUiState(FitPhase.Unsupported("Pas assez de mémoire pour cette photo."))
            } catch (e: Exception) {
                FitUiState(FitPhase.Unsupported(e.message ?: "Impossible de lire cette photo."))
            }
            _state.update { current -> if (current.phase == FitPhase.Saving) current else next.copy(options = current.options) }
        }
        return true
    }

    private fun read(uri: Uri): FitPhoto {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE) {
            throw ConversionError("« ${info.name} » n'est pas une photo. Choisis un JPG, PNG, WebP ou HEIC.")
        }
        val dir = WorkDir.create(context)
        try {
            val copy = FileIntake.copyToWork(context, uri, MAX_BYTES, dir)
            val bitmap = Images.decode(copy, MAX_SIDE)
            return FitPhoto(info.name, bitmap, ProfileRender.backdrops(bitmap))
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    fun setOptions(options: FitOptions) {
        val current = _state.value
        if (current.phase == FitPhase.Saving || options == current.options) return
        _state.update { it.copy(options = options) }
        val photo = current.photo ?: return
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.Default) {
            val preview = ProfileRender.render(photo.bitmap, photo.backdrops, ProfileFit.PREVIEW_SIDE, options)
            _state.update { if (it.photo === photo && it.options == options) it.copy(preview = preview) else it }
        }
    }

    fun save() {
        val current = _state.value
        val photo = current.photo ?: return
        if (busy || current.phase is FitPhase.Reading) return
        _state.update { it.copy(phase = FitPhase.Saving) }
        saveJob = scope.launch(Dispatchers.IO) {
            var dir: File? = null
            try {
                dir = WorkDir.create(context)
                val square = ProfileRender.render(photo.bitmap, photo.backdrops, ProfileFit.OUTPUT_SIDE, current.options)
                val file = FileIntake.uniqueFile(dir, ProfileFit.outputName(photo.name))
                file.outputStream().use { out ->
                    if (!square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw ConversionError("Impossible d'écrire la photo.")
                }
                square.recycle()
                val saved = MediaExporter.export(context, file)
                _state.update { it.copy(phase = FitPhase.Done(saved)) }
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
        _state.update { it.copy(phase = FitPhase.Failed(message, debug)) }
    }

    /** From "done" or a failure back to the settings, same photo. */
    fun back() {
        _state.update { if (it.photo != null && !busy) it.copy(phase = FitPhase.Ready) else it }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        previewJob?.cancel()
        _state.update { FitUiState(options = it.options) }
    }

    companion object {
        /** Longest side kept from the photo: twice the saved side, plenty for a 1080 px square. */
        const val MAX_SIDE = 2048
        private const val MAX_BYTES: Long = 64L * 1024 * 1024
        private const val JPEG_QUALITY = 92
    }
}

/** The one controller of the tool, made on first use with the application context. */
object ProfileFitHolder {
    @Volatile private var controller: ProfileFitController? = null

    fun get(context: Context): ProfileFitController = controller ?: synchronized(this) {
        controller ?: ProfileFitController(
            context.applicationContext,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { controller = it }
    }
}
