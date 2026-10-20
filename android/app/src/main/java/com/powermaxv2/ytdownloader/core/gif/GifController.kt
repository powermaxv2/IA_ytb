package com.powermaxv2.ytdownloader.core.gif

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegErrors
import com.powermaxv2.ytdownloader.core.convert.FfmpegInput
import com.powermaxv2.ytdownloader.core.convert.FfmpegRunner
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.convert.Target
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** The video given to the Créateur de GIF, with its size once rotated. */
data class GifVideo(val file: LocalFile, val durationMs: Long, val width: Int, val height: Int)

/** A photo of a GIF made from photos. [thumb] is null when the phone cannot draw it small. */
data class GifPhoto(val id: Long, val uri: Uri, val name: String, val thumb: Bitmap?)

sealed interface GifPhase {
    data object Idle : GifPhase

    /** The video, or photos, are being read. */
    data object Reading : GifPhase

    data class Unsupported(val message: String) : GifPhase

    data object Ready : GifPhase

    /** [progress] 0..1, negative when unknown. */
    data class Working(val progress: Float, val step: String) : GifPhase

    data class Done(val saved: SavedFile, val width: Int, val height: Int, val frames: Int, val durationMs: Long) : GifPhase

    data class Failed(val message: String, val debug: String? = null) : GifPhase
}

data class GifUiState(
    val phase: GifPhase = GifPhase.Idle,
    val source: GifSource = GifSource.VIDEO,
    val video: GifVideo? = null,
    val photos: List<GifPhoto> = emptyList(),
    val range: GifRange = GifRange(0, 0),
    val settings: GifSettings = GifSettings(),
    /** Eight frames across the whole video, under the handles. */
    val strip: List<Bitmap> = emptyList(),
    /** Frames spread over the passage, played by the live preview. */
    val preview: List<Bitmap> = emptyList(),
    /** A one-off remark (files left out). */
    val notice: String? = null,
) {
    /** Size and crop of the GIF, or null with nothing to make it from. */
    val geometry: GifGeometry?
        get() = when (source) {
            GifSource.VIDEO -> video?.let { GifPlan.geometry(it.width, it.height, settings.shape, settings.size.side) }
            GifSource.PHOTOS -> photos.firstOrNull()?.let { first ->
                val thumb = first.thumb
                GifPlan.photoGeometry(thumb?.width ?: 1, thumb?.height ?: 1, settings.shape, settings.size.side)
            }
        }

    /** Longest passage of the video for these choices. */
    val maxMs: Long
        get() = geometry?.let { GifPlan.maxSourceMs(it, settings.smooth, settings.speed, settings.effect) } ?: GifPlan.MAX_SOURCE_MS

    /** Frames of the GIF. */
    val frames: Int
        get() = when (source) {
            GifSource.VIDEO -> GifPlan.videoFrames(range, settings)
            GifSource.PHOTOS -> GifPlan.order(photos.size, settings.effect).size
        }

    /** Length of one play of the GIF. */
    val outputMs: Long
        get() = when (source) {
            GifSource.VIDEO -> GifPlan.outputMs(range.durationMs, settings.speed, settings.effect)
            GifSource.PHOTOS -> frames * settings.pace.frameMs
        }

    val estimateBytes: Long?
        get() = geometry?.let { GifPlan.estimateBytes(it, frames, settings.size.colors, source == GifSource.PHOTOS) }

    /** Why the GIF cannot be made yet, or null. */
    val refusal: String?
        get() = when (source) {
            GifSource.VIDEO -> if (video == null) "Choisis une vidéo." else null
            GifSource.PHOTOS -> if (photos.size < GifPlan.MIN_PHOTOS) "Ajoute au moins ${GifPlan.MIN_PHOTOS} photos." else null
        }
}

/**
 * The Créateur de GIF's state and work, made on first use ([shared]) and kept while the app lives, so a GIF keeps
 * being made when the user leaves the screen. A video is read in place (size, frames for the preview) and copied
 * into a work dir only when the GIF is made; photos are laid out at the GIF's size by [GifFrames]. FFmpeg writes the
 * GIF, which is saved to the Gallery (Pictures/Gulp), and the work dir is deleted.
 */
class GifController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(GifUiState())
    val state: StateFlow<GifUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var previewJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)
    private val ids = AtomicLong(0)

    val busy: Boolean get() = _state.value.phase is GifPhase.Working

    // MARK: - Source

    /** Switches between a video and photos (each keeps what was chosen for it). */
    fun setSource(source: GifSource) {
        if (busy) return
        _state.update { s ->
            val has = if (source == GifSource.VIDEO) s.video != null else s.photos.isNotEmpty()
            s.copy(source = source, phase = if (has) GifPhase.Ready else GifPhase.Idle, notice = null)
        }
    }

    /** Takes the chosen video (replacing the current one). False while a GIF is being made. */
    fun pickVideo(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        previewJob?.cancel()
        _state.update { it.copy(phase = GifPhase.Reading, source = GifSource.VIDEO, video = null, strip = emptyList(), preview = emptyList(), notice = null) }
        readJob = scope.launch(Dispatchers.IO) {
            val read = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                "Android a refusé l'accès : choisissez la vidéo à nouveau."
            } catch (e: Exception) {
                e.message ?: "Impossible de lire cette vidéo."
            }
            if (read is String) {
                _state.update { if (it.phase == GifPhase.Reading) it.copy(phase = GifPhase.Unsupported(read)) else it }
                return@launch
            }
            val video = read as GifVideo
            _state.update { s ->
                if (s.phase != GifPhase.Reading) return@update s
                val next = s.copy(phase = GifPhase.Ready, video = video)
                next.copy(range = GifPlan.initial(video.durationMs, next.maxMs))
            }
            val strip = strip(video)
            coroutineContext.ensureActive()
            _state.update { if (it.video?.file?.uri == uri) it.copy(strip = strip) else it }
            refreshPreview(immediate = true)
        }
        return true
    }

    /** The video read, or the reason it cannot be used. */
    private fun describe(uri: Uri): Any {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.VIDEO) return "Le Créateur de GIF prend une vidéo. « ${info.name} » n'en est pas une."
        FfmpegInput.refusal(head)?.let { return it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return "Gulp ne trouve pas la durée de cette vidéo : elle est peut-être abîmée."
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return "Ce fichier n'a pas d'images : c'est un son."
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            if (w <= 0 || h <= 0) return "Gulp ne trouve pas la taille de cette vidéo."
            return GifVideo(info, duration, w, h)
        } catch (e: RuntimeException) {
            return "Gulp ne sait pas lire cette vidéo sur ce téléphone."
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Eight frames across the whole video (closest key frames: fast). */
    private suspend fun strip(video: GifVideo): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(context, video.file.uri)
            for (i in 0 until STRIP_FRAMES) {
                coroutineContext.ensureActive()
                val us = video.durationMs * 1_000L * (2 * i + 1) / (2 * STRIP_FRAMES)
                GifFrames.frame(retriever, us, STRIP_HEIGHT, exact = false)?.let(frames::add)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // No frames: the handles still work.
        } finally {
            runCatching { retriever.release() }
        }
        return frames
    }

    /** Fetches the frames of the passage for the live preview (after a short pause, the handles may still move). */
    private fun refreshPreview(immediate: Boolean = false) {
        val video = _state.value.video ?: return
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.IO) {
            if (!immediate) delay(PREVIEW_DEBOUNCE_MS)
            val range = _state.value.range
            val retriever = MediaMetadataRetriever()
            val frames = ArrayList<Bitmap>()
            try {
                retriever.setDataSource(context, video.file.uri)
                for (ms in GifPlan.previewTimes(range, PREVIEW_FRAMES)) {
                    coroutineContext.ensureActive()
                    GifFrames.frame(retriever, ms * 1_000L, GifFrames.PREVIEW_SIDE, exact = true)?.let(frames::add)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // The preview stays as it was.
            } finally {
                runCatching { retriever.release() }
            }
            coroutineContext.ensureActive()
            if (frames.isNotEmpty()) _state.update { if (it.video?.file?.uri == video.file.uri) it.copy(preview = frames) else it }
        }
    }

    /** Adds the chosen photos after the current ones. False while a GIF is being made. */
    fun addPhotos(uris: List<Uri>): Boolean {
        if (busy) return false
        val room = GifPlan.MAX_PHOTOS - _state.value.photos.size
        val taken = uris.distinct().take(room.coerceAtLeast(0))
        val skipped = uris.distinct().size - taken.size
        if (taken.isEmpty()) {
            _state.update { it.copy(notice = "${GifPlan.MAX_PHOTOS} photos au plus.") }
            return true
        }
        _state.update { it.copy(source = GifSource.PHOTOS, phase = if (it.photos.isEmpty()) GifPhase.Reading else it.phase, notice = null) }
        scope.launch(Dispatchers.IO) {
            var refused = 0
            for (uri in taken) {
                val photo = try {
                    val info = FileIntake.describe(context, uri)
                    val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
                    if (FileKind.detect(info.name, info.mime, head) == FileKind.IMAGE) GifPhoto(ids.incrementAndGet(), uri, info.name, GifFrames.thumbnail(context, uri)) else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (photo == null) refused++
                _state.update { s ->
                    if (s.phase is GifPhase.Working) return@update s
                    val photos = if (photo != null) s.photos + photo else s.photos
                    s.copy(photos = photos, phase = if (photos.isNotEmpty() && s.source == GifSource.PHOTOS) GifPhase.Ready else s.phase)
                }
            }
            val notice = listOfNotNull(
                if (refused > 0) "$refused fichier${if (refused > 1) "s" else ""} laissé${if (refused > 1) "s" else ""} de côté : ce ne sont pas des photos." else null,
                if (skipped > 0) "${GifPlan.MAX_PHOTOS} photos au plus : $skipped de trop." else null,
            ).joinToString(" ").ifBlank { null }
            _state.update { s ->
                val phase = if (s.phase == GifPhase.Reading && s.photos.isEmpty()) GifPhase.Unsupported(notice ?: "Aucune photo lisible.") else s.phase
                s.copy(phase = phase, notice = notice)
            }
        }
        return true
    }

    fun removePhoto(id: Long) = edit { s ->
        val photos = s.photos.filterNot { it.id == id }
        s.copy(photos = photos, phase = if (photos.isEmpty()) GifPhase.Idle else s.phase)
    }

    /** Moves a photo one place earlier (-1) or later (+1). */
    fun movePhoto(id: Long, delta: Int) = edit { s ->
        val i = s.photos.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in s.photos.indices) return@edit s
        val list = s.photos.toMutableList()
        list.add(j, list.removeAt(i))
        s.copy(photos = list)
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // MARK: - Passage and choices

    fun setStart(ms: Long) = edit { s -> s.copy(range = GifPlan.moveStart(s.range, ms, s.video?.durationMs ?: 0L, s.maxMs)) }

    fun setEnd(ms: Long) = edit { s -> s.copy(range = GifPlan.moveEnd(s.range, ms, s.video?.durationMs ?: 0L, s.maxMs)) }

    /** A handle was let go: the preview frames of the new passage are fetched. */
    fun settle() = refreshPreview()

    /** Changes the choices; the passage is shortened if it no longer fits the new limit. */
    fun setSettings(settings: GifSettings) {
        val before = _state.value.range
        edit { s ->
            val next = s.copy(settings = settings.copy(caption = settings.caption.take(GifPlan.CAPTION_MAX * 2)))
            next.copy(range = GifPlan.clamp(next.range, next.video?.durationMs ?: 0L, next.maxMs))
        }
        if (_state.value.range != before) refreshPreview()
    }

    private inline fun edit(crossinline change: (GifUiState) -> GifUiState) {
        _state.update { if (it.phase == GifPhase.Ready) change(it) else it }
    }

    // MARK: - Flow

    /** Back to the start: nothing chosen. */
    fun reset() {
        if (busy) return
        readJob?.cancel()
        previewJob?.cancel()
        _state.update { GifUiState(source = it.source, settings = it.settings) }
    }

    /** From a result or an error, back to the same video or photos. */
    fun backToEdit() {
        _state.update { s ->
            val has = if (s.source == GifSource.VIDEO) s.video != null else s.photos.isNotEmpty()
            if (!busy && has) s.copy(phase = GifPhase.Ready) else s
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is GifPhase.Working) it.copy(phase = GifPhase.Ready) else it }
    }

    /** Makes the GIF. */
    fun start() {
        val current = _state.value
        if (busy || current.phase !is GifPhase.Ready && current.phase !is GifPhase.Failed) return
        if (current.refusal != null) return
        val geometry = current.geometry ?: return
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = GifPhase.Working(-1f, "Préparation…"), notice = null) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer le GIF.")
                return@launch
            }
            try {
                val out = File(dir, "out").apply { mkdirs() }
                val work = File(out, "output.gif")
                val root = WorkDir.root(context).absolutePath
                val settings = current.settings
                val caption = GifPlan.cleanCaption(settings.caption)
                val step = "Création du GIF…"
                when (current.source) {
                    GifSource.VIDEO -> {
                        val video = current.video ?: return@launch
                        val range = GifPlan.clamp(current.range, video.durationMs, current.maxMs)
                        working(token, -1f, "Copie de la vidéo…")
                        val job = coroutineContext
                        val copy = FileIntake.copyToWork(context, video.file.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
                        val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
                        FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
                        val input = File(dir, "input.${FfmpegInput.workExt(head, video.file.name)}")
                        if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer la vidéo.")
                        val captionFile = if (caption.isNotEmpty()) {
                            File(dir, "caption.png").also { GifFrames.renderCaption(it, geometry.width, geometry.height, caption) }
                        } else {
                            null
                        }
                        val args = GifPlan.videoArgs(root, input.absolutePath, captionFile?.absolutePath, work.absolutePath, range, geometry, settings)
                        working(token, 0f, step)
                        val length = GifPlan.outputMs(range.durationMs, settings.speed, settings.effect)
                        FfmpegRunner(context, engine).run(args, work, Target.GIF, length / 1000.0) { working(token, it, step) }
                        input.delete()
                    }
                    GifSource.PHOTOS -> {
                        val photos = current.photos
                        val job = coroutineContext
                        val drawn = photos.mapIndexed { i, photo ->
                            job.ensureActive()
                            working(token, PREPARE_SHARE * i / photos.size, "Photo ${i + 1} sur ${photos.size}…")
                            val copy = FileIntake.copyToWork(context, photo.uri, PHOTO_MAX_BYTES, dir) { job.ensureActive() }
                            val png = File(dir, String.format(Locale.ROOT, "photo%03d.png", i + 1))
                            try {
                                val bitmap = Images.decode(copy, maxSide = 2 * max(geometry.width, geometry.height))
                                try {
                                    GifFrames.renderPhoto(bitmap, png, geometry.width, geometry.height, caption)
                                } finally {
                                    bitmap.recycle()
                                }
                            } catch (e: ConversionError) {
                                throw ConversionError("Photo ${i + 1} (« ${photo.name} ») : ${e.message}")
                            } catch (e: OutOfMemoryError) {
                                throw ConversionError("Photo ${i + 1} (« ${photo.name} ») trop grande pour la mémoire du téléphone.")
                            } finally {
                                copy.delete()
                            }
                            png
                        }
                        // The frames in playing order (a boomerang shows most photos twice).
                        GifPlan.order(drawn.size, settings.effect).forEachIndexed { k, index ->
                            drawn[index].copyTo(File(dir, String.format(Locale.ROOT, "frame%03d.png", k + 1)), overwrite = true)
                        }
                        val pattern = File(dir, "frame%03d.png").absolutePath
                        val args = GifPlan.photoArgs(root, pattern, work.absolutePath, settings.pace.frameMs, settings.size.colors)
                        working(token, PREPARE_SHARE, step)
                        FfmpegRunner(context, engine).run(args, work, Target.GIF, current.outputMs / 1000.0) { fraction ->
                            working(token, PREPARE_SHARE + (1f - PREPARE_SHARE) * fraction.coerceIn(0f, 1f), step)
                        }
                    }
                }
                coroutineContext.ensureActive()
                working(token, -1f, "Enregistrement…")
                val now = Calendar.getInstance()
                val name = GifPlan.outputName(
                    current.video?.file?.name?.takeIf { current.source == GifSource.VIDEO },
                    now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR),
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                )
                val named = FileIntake.uniqueFile(out, name)
                named.delete()
                if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer le GIF.")
                val saved = MediaExporter.export(context, named)
                update(token) { it.copy(phase = GifPhase.Done(saved, geometry.width, geometry.height, current.frames, current.outputMs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Fichier trop volumineux.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Ce fichier ne peut pas être ouvert.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisissez le fichier à nouveau, ou autorisez le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: Throwable) {
                fail(token, FfmpegErrors.GENERIC, e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (GifUiState) -> GifUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun working(token: Int, progress: Float, step: String) {
        update(token) { if (it.phase is GifPhase.Working) it.copy(phase = GifPhase.Working(progress, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = GifPhase.Failed(message, debug)) }
    }

    companion object {
        private const val STRIP_FRAMES = 8
        private const val STRIP_HEIGHT = 96
        private const val PREVIEW_FRAMES = 10
        private const val PREVIEW_DEBOUNCE_MS = 250L
        private const val PHOTO_MAX_BYTES: Long = 64L * 1024 * 1024

        /** Share of the progress spent laying out photos. */
        private const val PREPARE_SHARE = 0.4f

        /** The one controller of the app, made on first use and kept as long as the app lives. */
        val shared: GifController by lazy { GifController(App.instance, App.instance.scope, App.instance.engine) }
    }
}
