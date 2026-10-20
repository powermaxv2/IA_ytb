package com.powermaxv2.ytdownloader.core.colors

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

/** The chosen photo, decoded upright (longest side ≤ [ColorPickerController.MAX_SIDE]). */
class PickPhoto(val name: String, val bitmap: Bitmap) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

sealed interface PickPhase {
    data object Idle : PickPhase

    data object Reading : PickPhase

    data class Unsupported(val message: String) : PickPhase

    data object Ready : PickPhase
}

data class PickUiState(
    val phase: PickPhase = PickPhase.Idle,
    val photo: PickPhoto? = null,
    /** The photo's main colours, biggest first. */
    val palette: List<Swatch> = emptyList(),
    /** Where the pipette is, as fractions of the photo (0‥1). */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** The colour under the pipette, 0xRRGGBB. */
    val color: Int = 0xFFFFFF,
    /** Colours the user kept ("Garder"), newest first; saved across launches. */
    val kept: List<Int> = emptyList(),
    val exporting: Boolean = false,
    /** The palette card last saved to the Galerie. */
    val exported: SavedFile? = null,
)

/**
 * State and work of the Pipette: the photo is copied to a work dir, decoded and dropped; its palette is
 * computed off the main thread from a small copy; a touch reads the average of the 3 × 3 pixels under the
 * finger. Held by [ColorPickerHolder] so the photo and the kept colours stay when the user goes home.
 */
class ColorPickerController(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("colors", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PickUiState(kept = KeptColors.decode(prefs.getString(KEY_KEPT, null))))
    val state: StateFlow<PickUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var exportJob: Job? = null

    fun pick(uri: Uri) {
        readJob?.cancel()
        _state.update { it.copy(phase = PickPhase.Reading, photo = null, palette = emptyList(), exported = null) }
        readJob = scope.launch(Dispatchers.IO) {
            val next: PickUiState = try {
                val photo = read(uri)
                val palette = palette(photo.bitmap)
                // The pipette starts on the main colour, where it shows best.
                val first = palette.firstOrNull()
                val x = first?.x ?: 0.5f
                val y = first?.y ?: 0.5f
                _state.value.copy(phase = PickPhase.Ready, photo = photo, palette = palette, x = x, y = y, color = sample(photo.bitmap, x, y))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                unsupported("Android a refusé l'accès : choisis la photo à nouveau.")
            } catch (e: FileTooLarge) {
                unsupported(e.message ?: "Photo trop volumineuse.")
            } catch (e: UnsupportedSource) {
                unsupported(e.message ?: "Cette photo ne peut pas être ouverte.")
            } catch (e: ConversionError) {
                unsupported(e.message ?: "Photo illisible.")
            } catch (e: OutOfMemoryError) {
                unsupported("Pas assez de mémoire pour cette photo.")
            } catch (e: Exception) {
                unsupported(e.message ?: "Impossible de lire cette photo.")
            }
            _state.update { next.copy(kept = it.kept) }
        }
    }

    private fun unsupported(message: String) = PickUiState(PickPhase.Unsupported(message), kept = _state.value.kept)

    private fun read(uri: Uri): PickPhoto {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE) {
            throw ConversionError("« ${info.name} » n'est pas une photo. Choisis un JPG, PNG, WebP ou HEIC.")
        }
        val dir = WorkDir.create(context)
        try {
            val copy = FileIntake.copyToWork(context, uri, MAX_BYTES, dir)
            return PickPhoto(info.name, Images.decode(copy, MAX_SIDE))
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    /** The pipette moved to ([x], [y]) (fractions of the photo). */
    fun touch(x: Float, y: Float) {
        val photo = _state.value.photo ?: return
        val fx = x.coerceIn(0f, 1f)
        val fy = y.coerceIn(0f, 1f)
        val color = sample(photo.bitmap, fx, fy)
        _state.update { if (it.photo === photo) it.copy(x = fx, y = fy, color = color) else it }
    }

    /** A colour of the palette touched: the pipette jumps to where it shows best. */
    fun select(swatch: Swatch) {
        _state.update { if (it.photo != null) it.copy(x = swatch.x, y = swatch.y, color = swatch.rgb) else it }
    }

    /** A kept colour touched: shown again, the pipette stays where it is. */
    fun recall(rgb: Int) {
        _state.update { it.copy(color = rgb) }
    }

    fun keep() = setKept(KeptColors.add(_state.value.kept, _state.value.color))

    fun forget(rgb: Int) = setKept(KeptColors.remove(_state.value.kept, rgb))

    private fun setKept(kept: List<Int>) {
        _state.update { it.copy(kept = kept) }
        prefs.edit().putString(KEY_KEPT, KeptColors.encode(kept)).apply()
    }

    /** Draws the palette card (photo + colours + codes) and saves it as a PNG in Pictures/Gulp. */
    fun export(onDone: (SavedFile?, String?) -> Unit) {
        val current = _state.value
        val photo = current.photo ?: return
        if (current.exporting || current.palette.isEmpty()) return
        _state.update { it.copy(exporting = true) }
        exportJob = scope.launch(Dispatchers.IO) {
            var dir: File? = null
            var saved: SavedFile? = null
            var error: String? = null
            try {
                dir = WorkDir.create(context)
                val card = PaletteCard.draw(photo.bitmap, current.palette)
                val file = FileIntake.uniqueFile(dir, PaletteCard.outputName(photo.name))
                file.outputStream().use { out ->
                    if (!card.compress(Bitmap.CompressFormat.PNG, 100, out)) throw ConversionError("Impossible d'écrire l'image.")
                }
                card.recycle()
                saved = MediaExporter.export(context, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                error = "Android a refusé l'enregistrement : autorise le stockage et réessaie."
            } catch (e: OutOfMemoryError) {
                error = "Pas assez de mémoire pour dessiner la palette."
            } catch (e: Throwable) {
                error = e.message ?: "L'enregistrement a échoué."
            } finally {
                dir?.let { withContext(NonCancellable) { WorkDir.delete(context, it) } }
            }
            _state.update { it.copy(exporting = false, exported = saved ?: it.exported) }
            withContext(Dispatchers.Main) { onDone(saved, error) }
        }
    }

    fun reset() {
        readJob?.cancel()
        if (_state.value.exporting) return
        _state.update { PickUiState(kept = it.kept) }
    }

    companion object {
        const val MAX_SIDE = 2048
        private const val MAX_BYTES: Long = 64L * 1024 * 1024
        private const val KEY_KEPT = "kept"

        /** Side of the small copy the palette is computed from. */
        private const val PALETTE_SIDE = 160

        fun palette(bitmap: Bitmap): List<Swatch> {
            val scale = PALETTE_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                bitmap
            }
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            val palette = PaletteExtractor.extract(pixels, small.width, small.height)
            if (small !== bitmap) small.recycle()
            return palette
        }

        /** Average of the 3 × 3 pixels at ([x], [y]) (fractions) of [bitmap]. */
        fun sample(bitmap: Bitmap, x: Float, y: Float): Int {
            val px = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val py = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val left = (px - 1).coerceAtLeast(0)
            val top = (py - 1).coerceAtLeast(0)
            val w = (px + 1).coerceAtMost(bitmap.width - 1) - left + 1
            val h = (py + 1).coerceAtMost(bitmap.height - 1) - top + 1
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, left, top, w, h)
            return ColorMath.sampleAverage(pixels, w, h, px - left, py - top, radius = 1)
        }
    }
}

/** The one controller of the tool, made on first use with the application context. */
object ColorPickerHolder {
    @Volatile private var controller: ColorPickerController? = null

    fun get(context: Context): ColorPickerController = controller ?: synchronized(this) {
        controller ?: ColorPickerController(
            context.applicationContext,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { controller = it }
    }
}
