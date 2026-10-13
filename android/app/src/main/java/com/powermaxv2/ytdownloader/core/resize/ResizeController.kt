package com.powermaxv2.ytdownloader.core.resize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.powermaxv2.ytdownloader.App
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
import com.powermaxv2.ytdownloader.core.wipe.ExifWriter
import com.powermaxv2.ytdownloader.core.wipe.KeptExif
import com.powermaxv2.ytdownloader.core.wipe.MetaStrip
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/** A chosen photo; [dims] = upright size (EXIF rotation applied), null when the provider would not tell. */
data class ResizePhoto(val file: LocalFile, val dims: Dims?)

/** A saved copy: its size before / after (bytes), its new [dims], and whether it missed the weight goal. */
data class ResizeResult(
    val name: String,
    val saved: SavedFile,
    val before: Long,
    val after: Long,
    val from: Dims,
    val dims: Dims,
    val overWeight: Boolean = false,
)

sealed interface ResizePhase {
    data object Idle : ResizePhase

    data object Reading : ResizePhase

    data class Unsupported(val message: String) : ResizePhase

    data object Ready : ResizePhase

    data class Working(val done: Int, val total: Int) : ResizePhase

    data class Done(val results: List<ResizeResult>) : ResizePhase

    data class Failed(val message: String, val debug: String? = null) : ResizePhase
}

data class ResizeUiState(
    val phase: ResizePhase = ResizePhase.Idle,
    val photos: List<ResizePhoto> = emptyList(),
    val settings: ResizeSettings = ResizeSettings(),
) {
    /** Total weight of the chosen photos, when every provider told it. */
    val totalBytes: Long? get() = photos.map { it.file.size }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0L }
}

/**
 * The Redimensionneur's state and work. Each photo is copied into a work dir, decoded at the size it needs
 * (upright), cut to the chosen shape, scaled, written as JPG / PNG / WebP (for a weight goal, the best quality
 * then the biggest size that fit), given back its date / phone / place if asked, and saved as a new photo in
 * Pictures/Gulp. The originals never change. Held by [shared] so a batch keeps going off its screen.
 */
class ResizeController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ResizeUiState())
    val state: StateFlow<ResizeUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is ResizePhase.Working

    fun pick(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        readJob?.cancel()
        val keep = if (add) _state.value.photos else emptyList()
        _state.update { it.copy(phase = ResizePhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(keep, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                ResizeUiState(phase = ResizePhase.Unsupported("Android a refusé l'accès : choisis les photos à nouveau."))
            } catch (e: Exception) {
                ResizeUiState(phase = ResizePhase.Unsupported(e.message ?: "Impossible de lire ces photos."))
            }
            _state.update { current -> if (current.phase is ResizePhase.Working) current else next.copy(settings = current.settings) }
        }
        return true
    }

    private suspend fun describe(keep: List<ResizePhoto>, uris: List<Uri>): ResizeUiState {
        val known = keep.map { it.file.uri }.toSet()
        val fresh = uris.filter { it !in known }
        if (keep.size + fresh.size > ResizePlan.MAX_PHOTOS) {
            return ResizeUiState(phase = ResizePhase.Unsupported("${ResizePlan.MAX_PHOTOS} photos au plus à la fois."), photos = keep)
        }
        val refused = ArrayList<String>()
        val photos = keep + fresh.mapNotNull { uri ->
            coroutineContext.ensureActive()
            val info = FileIntake.describe(context, uri)
            val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
            val gif = info.mime == "image/gif" || info.name.lowercase().endsWith(".gif")
            if (FileKind.detect(info.name, info.mime, head) != FileKind.IMAGE || gif) {
                refused += info.name
                null
            } else {
                ResizePhoto(info, readDims(uri))
            }
        }
        if (photos.isEmpty()) {
            val what = refused.firstOrNull()?.let { "« $it » n'est pas une photo." } ?: "Aucune photo."
            return ResizeUiState(phase = ResizePhase.Unsupported("$what Le Redimensionneur prend des photos JPG, PNG, WebP ou HEIC."))
        }
        return ResizeUiState(ResizePhase.Ready, photos)
    }

    /** Size without decoding the pixels, turned when the EXIF says the photo is on its side. */
    private fun readDims(uri: Uri): Dims? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        upright(bounds.outWidth, bounds.outHeight, orientation)
    }.getOrNull()

    private fun upright(width: Int, height: Int, orientation: Int): Dims = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
        -> Dims(height, width)
        else -> Dims(width, height)
    }

    fun setSettings(settings: ResizeSettings) {
        _state.update { if (it.phase is ResizePhase.Working) it else it.copy(settings = settings) }
    }

    fun remove(photo: ResizePhoto) {
        _state.update {
            if (it.phase != ResizePhase.Ready) return@update it
            val left = it.photos - photo
            if (left.isEmpty()) ResizeUiState(settings = it.settings) else it.copy(photos = left)
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { ResizeUiState(settings = it.settings) }
    }

    fun backToPhotos() {
        _state.update { if (it.photos.isNotEmpty() && !busy) it.copy(phase = ResizePhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is ResizePhase.Working) it.copy(phase = ResizePhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        if (current.photos.isEmpty() || busy || current.phase is ResizePhase.Reading) return
        val token = generation.incrementAndGet()
        val photos = current.photos
        val settings = current.settings
        _state.update { it.copy(phase = ResizePhase.Working(0, photos.size)) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer les photos.")
                return@launch
            }
            try {
                val results = photos.mapIndexed { index, photo ->
                    coroutineContext.ensureActive()
                    val result = resize(photo, settings, dir)
                    update(token) { if (it.phase is ResizePhase.Working) it.copy(phase = ResizePhase.Working(index + 1, photos.size)) else it }
                    result
                }
                update(token) { it.copy(phase = ResizePhase.Done(results)) }
            } catch (e: CancellationException) {
                throw e
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
                fail(token, "Le redimensionnement a échoué.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private suspend fun resize(photo: ResizePhoto, settings: ResizeSettings, dir: File): ResizeResult {
        val job = coroutineContext
        FileIntake.requireContent(photo.file.uri)
        if ((photo.file.size ?: 0L) > MAX_BYTES) throw FileTooLarge(MAX_BYTES)
        val copy = FileIntake.uniqueFile(dir, photo.file.name)
        val input = context.contentResolver.openInputStream(photo.file.uri) ?: throw IOException("Impossible de lire cette photo.")
        input.use { stream ->
            copy.outputStream().use { output -> FileIntake.copyCapped(stream, output, MAX_BYTES) { job.ensureActive() } }
        }
        val before = copy.length()
        val source = photo.dims ?: dimsOf(copy) ?: throw ConversionError("Gulp ne peut pas lire la taille de « ${photo.file.name} ».")
        val target = ResizePlan.target(source, settings)
        val box = ResizePlan.cropBox(source.width, source.height, settings.crop)
        // Decoded just big enough: the crop of the decoded picture must still be at least the target size.
        val scale = target.longest.toDouble() / max(1, max(box.width, box.height))
        val decodeSide = ResizePlan.decodeSide(source, scale)
        val kept = if (settings.keepExif) keptExif(copy) else null
        var bitmap = Images.decode(copy, decodeSide)
        try {
            bitmap = cropped(bitmap, settings.crop)
            job.ensureActive()
            val alpha = bitmap.hasAlpha() && hasTransparentPixel(bitmap)
            val format = ResizePlan.format(photo.file.name, settings.format, alpha)
            if (format == OutFormat.JPEG && bitmap.hasAlpha()) bitmap = onWhite(bitmap)
            val exif = kept?.let(ExifWriter::build)
            var dims = Dims(bitmap.width, bitmap.height)
            var overWeight = false
            val encoded: ByteArray = if (settings.mode == ResizeMode.WEIGHT) {
                val limit = ResizePlan.weightLimit(settings.weight, exif?.size ?: 0)
                // One scaled picture per size tried (the quality search encodes the same size several times).
                var cache: Pair<Dims, Bitmap>? = null
                fun at(size: Dims): Bitmap = cache?.takeIf { it.first == size }?.second ?: run {
                    cache?.second?.takeIf { it !== bitmap }?.recycle()
                    scaledTo(bitmap, size).also { cache = size to it }
                }
                try {
                    val (attempt, size) = ResizePlan.fit(ResizePlan.scaled(dims, target.longest), ResizePlan.lossy(format), limit) { attempt ->
                        job.ensureActive()
                        encode(at(attempt.dims), format, attempt.quality).size.toLong()
                    }
                    overWeight = size > limit
                    dims = attempt.dims
                    // The search ends on its best try, which may not be the last one encoded: write it again.
                    encode(at(attempt.dims), format, attempt.quality)
                } finally {
                    cache?.second?.takeIf { it !== bitmap }?.recycle()
                }
            } else {
                val scaled = scaledTo(bitmap, target)
                dims = Dims(scaled.width, scaled.height)
                try {
                    encode(scaled, format, settings.quality.quality)
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
            }
            job.ensureActive()
            // Bitmap.compress writes no EXIF; the strip also drops any other block, and puts back what was kept.
            val bytes = runCatching { MetaStrip.strip(encoded, exif) }.getOrDefault(encoded)
            val name = ResizePlan.outputName(photo.file.name, dims, format)
            val outDir = File(dir, "out").apply { mkdirs() }
            val out = FileIntake.uniqueFile(outDir, name)
            out.writeBytes(bytes)
            copy.delete()
            val saved = MediaExporter.export(context, out)
            out.delete()
            return ResizeResult(saved.name, saved, before, bytes.size.toLong(), source, dims, overWeight)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun dimsOf(file: File): Dims? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return upright(bounds.outWidth, bounds.outHeight, orientation)
    }

    /** The centred [crop] of [bitmap] (recycled when a new one is made). */
    private fun cropped(bitmap: Bitmap, crop: CropRatio): Bitmap {
        val box = ResizePlan.cropBox(bitmap.width, bitmap.height, crop)
        if (box.width == bitmap.width && box.height == bitmap.height) return bitmap
        val cut = Bitmap.createBitmap(bitmap, box.left, box.top, box.width, box.height)
        if (cut !== bitmap) bitmap.recycle()
        return cut
    }

    private fun scaledTo(bitmap: Bitmap, dims: Dims): Bitmap =
        if (dims.width == bitmap.width && dims.height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, dims.width, dims.height, true)

    private fun encode(bitmap: Bitmap, format: OutFormat, quality: Int): ByteArray {
        val compress = when (format) {
            OutFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutFormat.PNG -> Bitmap.CompressFormat.PNG
            OutFormat.WEBP -> webp()
        }
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(compress, quality.coerceIn(0, 100), out)) throw ConversionError("Impossible d'écrire la photo en ${format.label}.")
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** Whether a pixel is really see-through (many PNGs carry an alpha channel that is opaque everywhere). */
    private fun hasTransparentPixel(bitmap: Bitmap): Boolean {
        val step = max(1, min(bitmap.width, bitmap.height) / 64)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 250) return true
                x += step
            }
            y += step
        }
        return false
    }

    /** [bitmap] over white (recycled): JPG has no transparency. */
    private fun onWhite(bitmap: Bitmap): Bitmap {
        val copy = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bitmap.recycle()
        return copy
    }

    /** Date, phone, author and place of the original; the copy is upright, so its orientation is normal. */
    private fun keptExif(file: File): KeptExif? = runCatching {
        val exif = ExifInterface(file.absolutePath)
        fun text(tag: String) = exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() && it.any(Char::isLetterOrDigit) }
        val latLong = FloatArray(2)
        val gps = exif.getLatLong(latLong) && !(latLong[0] == 0f && latLong[1] == 0f)
        KeptExif(
            orientation = 1,
            make = text(ExifInterface.TAG_MAKE),
            model = text(ExifInterface.TAG_MODEL),
            artist = text(ExifInterface.TAG_ARTIST),
            copyright = text(ExifInterface.TAG_COPYRIGHT),
            dateTimeOriginal = text(ExifInterface.TAG_DATETIME_ORIGINAL) ?: text(ExifInterface.TAG_DATETIME),
            latitude = if (gps) latLong[0].toDouble() else null,
            longitude = if (gps) latLong[1].toDouble() else null,
        )
    }.getOrNull()

    private inline fun update(token: Int, crossinline change: (ResizeUiState) -> ResizeUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = ResizePhase.Failed(message, debug)) }
    }

    companion object {
        private const val MAX_BYTES: Long = 64L * 1024 * 1024

        /**
         * The one controller of the app, made on first use and kept as long as the app lives (a batch survives
         * leaving the screen), like the other tools' controllers in App.
         */
        val shared: ResizeController by lazy { ResizeController(App.instance, App.instance.scope) }
    }
}
