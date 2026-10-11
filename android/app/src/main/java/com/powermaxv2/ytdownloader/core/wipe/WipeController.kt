package com.powermaxv2.ytdownloader.core.wipe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.SafeNames
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class WipePhoto(val file: LocalFile, val facts: PhotoFacts)

/** [after] = what the saved copy still tells (read back from it). */
data class WipeResult(val name: String, val saved: SavedFile, val reencoded: Boolean, val after: PhotoFacts = PhotoFacts())

sealed interface WipePhase {
    data object Idle : WipePhase

    data object Reading : WipePhase

    data class Unsupported(val message: String) : WipePhase

    data object Ready : WipePhase

    data class Working(val done: Int, val total: Int) : WipePhase

    data class Done(val results: List<WipeResult>) : WipePhase

    data class Failed(val message: String, val debug: String? = null) : WipePhase
}

data class WipeUiState(
    val phase: WipePhase = WipePhase.Idle,
    val photos: List<WipePhoto> = emptyList(),
    val choices: WipeChoices = WipeChoices(),
) {
    val withLocation: List<WipePhoto> get() = photos.filter { it.facts.hasLocation }
    val withDevice: List<WipePhoto> get() = photos.filter { it.facts.hasDevice }
    val withAuthor: List<WipePhoto> get() = photos.filter { it.facts.hasAuthor }
    val withDate: List<WipePhoto> get() = photos.filter { it.facts.hasDate }
}

/**
 * The Gomme's state and work, held by the App: photos are read in place (their EXIF), then each is copied into a
 * work dir, cleaned by [MetaStrip] (pixels untouched for JPEG / PNG / WebP; HEIC and others are re-encoded as JPEG,
 * which drops everything), checked again, and saved as a new photo in Pictures/Gulp. The originals never change.
 */
class WipeController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(WipeUiState())
    val state: StateFlow<WipeUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is WipePhase.Working

    fun pick(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        readJob?.cancel()
        val keep = if (add) _state.value.photos else emptyList()
        _state.update { it.copy(phase = WipePhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(keep, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                WipeUiState(phase = WipePhase.Unsupported("Android a refusé l'accès : choisis les photos à nouveau."))
            } catch (e: Exception) {
                WipeUiState(phase = WipePhase.Unsupported(e.message ?: "Impossible de lire ces photos."))
            }
            _state.update { current -> if (current.phase is WipePhase.Working) current else next.copy(choices = current.choices) }
        }
        return true
    }

    private suspend fun describe(keep: List<WipePhoto>, uris: List<Uri>): WipeUiState {
        val known = keep.map { it.file.uri }.toSet()
        val fresh = uris.filter { it !in known }
        if (keep.size + fresh.size > MAX_PHOTOS) {
            return WipeUiState(phase = WipePhase.Unsupported("$MAX_PHOTOS photos au plus à la fois."), photos = keep)
        }
        val refused = ArrayList<String>()
        val photos = keep + fresh.mapNotNull { uri ->
            coroutineContext.ensureActive()
            val info = FileIntake.describe(context, uri)
            val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
            if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE) {
                refused += info.name
                null
            } else {
                WipePhoto(info, readFacts(uri))
            }
        }
        if (photos.isEmpty()) {
            val what = refused.firstOrNull()?.let { "« $it » n'est pas une photo." } ?: "Aucune photo."
            return WipeUiState(phase = WipePhase.Unsupported("$what La Gomme prend des photos JPG, PNG, WebP ou HEIC."))
        }
        return WipeUiState(WipePhase.Ready, photos)
    }

    private fun readFacts(uri: Uri): PhotoFacts = runCatching {
        openOriginal(uri).use { facts(ExifInterface(it)) }
    }.getOrNull() ?: PhotoFacts()

    /**
     * The photo as stored. Since Android 10 the system blanks the GPS of a photo read by an app without the
     * "position des contenus multimédias" permission; with it, [MediaStore.setRequireOriginal] gives the real bytes.
     * Either way the copy is cleaned: the blanked GPS tags are dropped like any other.
     */
    private fun openOriginal(uri: Uri): InputStream {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val media = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
            for (candidate in listOfNotNull(media, uri)) {
                runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(candidate)) }.getOrNull()?.let { return it }
            }
        }
        return resolver.openInputStream(uri) ?: throw IOException("Impossible de lire cette photo.")
    }

    private fun facts(exif: ExifInterface): PhotoFacts {
        fun text(tag: String) = exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() && it.any(Char::isLetterOrDigit) }
        val latLong = FloatArray(2)
        val hasGps = exif.getLatLong(latLong) && !(latLong[0] == 0f && latLong[1] == 0f)
        val gpsTags = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null || exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null
        val extra = listOf(
            ExifInterface.TAG_SOFTWARE,
            "BodySerialNumber",
            "LensModel",
            "LensMake",
            "LensSerialNumber",
            "CameraOwnerName",
        ).any { text(it) != null }
        return PhotoFacts(
            latitude = if (hasGps) latLong[0].toDouble() else null,
            longitude = if (hasGps) latLong[1].toDouble() else null,
            make = text(ExifInterface.TAG_MAKE),
            model = text(ExifInterface.TAG_MODEL),
            deviceExtra = extra,
            serial = listOf("BodySerialNumber", "LensSerialNumber").any { text(it) != null },
            software = text(ExifInterface.TAG_SOFTWARE),
            locationHidden = !hasGps && gpsTags,
            artist = text(ExifInterface.TAG_ARTIST) ?: text("CameraOwnerName"),
            copyright = text(ExifInterface.TAG_COPYRIGHT),
            date = text(ExifInterface.TAG_DATETIME_ORIGINAL) ?: text(ExifInterface.TAG_DATETIME),
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
        )
    }

    fun setChoices(choices: WipeChoices) {
        _state.update { if (it.phase is WipePhase.Working) it else it.copy(choices = choices) }
    }

    fun remove(photo: WipePhoto) {
        _state.update {
            if (it.phase != WipePhase.Ready) return@update it
            val left = it.photos - photo
            if (left.isEmpty()) WipeUiState(choices = it.choices) else it.copy(photos = left)
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { WipeUiState(choices = it.choices) }
    }

    fun backToPhotos() {
        _state.update { if (it.photos.isNotEmpty() && !busy) it.copy(phase = WipePhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is WipePhase.Working) it.copy(phase = WipePhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        if (current.photos.isEmpty() || busy || current.phase is WipePhase.Reading) return
        val token = generation.incrementAndGet()
        val photos = current.photos
        val choices = current.choices
        _state.update { it.copy(phase = WipePhase.Working(0, photos.size)) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer le nettoyage.")
                return@launch
            }
            try {
                val results = photos.mapIndexed { index, photo ->
                    coroutineContext.ensureActive()
                    val result = clean(photo, choices, dir)
                    update(token) { if (it.phase is WipePhase.Working) it.copy(phase = WipePhase.Working(index + 1, photos.size)) else it }
                    result
                }
                update(token) { it.copy(phase = WipePhase.Done(results)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotStrippable) {
                fail(token, e.message ?: "Photo abîmée.")
            } catch (e: ConversionError) {
                fail(token, e.message ?: "Photo illisible.", e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Photo trop volumineuse.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Cette photo ne peut pas être ouverte.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisis les photos à nouveau, ou autorise le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour cette photo.", e.toString())
            } catch (e: Throwable) {
                fail(token, "Le nettoyage a échoué.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private suspend fun clean(photo: WipePhoto, choices: WipeChoices, dir: File): WipeResult {
        val job = coroutineContext
        val copy = FileIntake.uniqueFile(dir, photo.file.name)
        FileIntake.requireContent(photo.file.uri)
        if ((photo.file.size ?: 0L) > MAX_BYTES) throw FileTooLarge(MAX_BYTES)
        openOriginal(photo.file.uri).use { input ->
            copy.outputStream().use { output -> FileIntake.copyCapped(input, output, MAX_BYTES) { job.ensureActive() } }
        }
        val bytes = copy.readBytes()
        val facts = photo.facts
        val format = MetaStrip.formatOf(bytes)
        val reencoded = format == null
        val cleaned = if (format != null) {
            MetaStrip.strip(bytes, ExifWriter.build(kept(facts, choices)))
        } else {
            // HEIC and the like: the picture is decoded (upright) and written again as JPEG, without any metadata.
            val bitmap = Images.decode(copy)
            try {
                val out = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, REENCODE_QUALITY, out)) throw ConversionError("Impossible d'écrire la photo.")
                MetaStrip.jpeg(out.toByteArray(), ExifWriter.build(kept(facts, choices).copy(orientation = 1)))
            } finally {
                bitmap.recycle()
            }
        }
        copy.delete()
        val name = if (reencoded) SafeNames.withExtension(photo.file.name, "jpg") else photo.file.name
        val outDir = File(dir, "out").apply { mkdirs() }
        val out = FileIntake.uniqueFile(outDir, name)
        out.writeBytes(cleaned)
        val after = verify(out, choices)
        val saved = MediaExporter.export(context, out)
        out.delete()
        return WipeResult(saved.name, saved, reencoded, after)
    }

    /** Reads the cleaned photo back: anything the user asked to erase that is still there stops everything. */
    private fun verify(file: File, choices: WipeChoices): PhotoFacts {
        val facts = runCatching { facts(ExifInterface(file.absolutePath)) }.getOrNull() ?: return PhotoFacts()
        val left = (choices.location && facts.hasLocation) || (choices.device && facts.hasDevice) ||
            (choices.author && facts.hasAuthor) || (choices.date && facts.hasDate)
        if (left) throw ConversionError("Gulp n'a pas réussi à tout effacer de « ${file.name} » : rien n'a été enregistré.")
        return facts
    }

    private fun kept(facts: PhotoFacts, choices: WipeChoices) = KeptExif(
        orientation = facts.orientation,
        make = facts.make.takeUnless { choices.device },
        model = facts.model.takeUnless { choices.device },
        artist = facts.artist.takeUnless { choices.author },
        copyright = facts.copyright.takeUnless { choices.author },
        dateTimeOriginal = facts.date.takeUnless { choices.date },
        latitude = facts.latitude.takeUnless { choices.location },
        longitude = facts.longitude.takeUnless { choices.location },
    )

    private inline fun update(token: Int, crossinline change: (WipeUiState) -> WipeUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = WipePhase.Failed(message, debug)) }
    }

    companion object {
        const val MAX_PHOTOS = 50
        private const val MAX_BYTES: Long = 64L * 1024 * 1024
        private const val REENCODE_QUALITY = 95
    }
}
