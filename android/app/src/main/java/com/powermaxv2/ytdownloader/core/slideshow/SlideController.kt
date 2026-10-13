package com.powermaxv2.ytdownloader.core.slideshow

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegErrors
import com.powermaxv2.ytdownloader.core.convert.FfmpegInput
import com.powermaxv2.ytdownloader.core.convert.FfmpegRunner
import com.powermaxv2.ytdownloader.core.convert.Target
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
import kotlinx.coroutines.cancelAndJoin
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** A photo of the show as the screen shows it. [thumb] is null while it is read (or when the phone cannot). */
data class SlidePhoto(val id: Long, val uri: Uri, val name: String, val durationMs: Long, val thumb: Bitmap? = null)

/** The music under the show; [durationMs] when Android can tell. */
data class SlideMusic(val uri: Uri, val name: String, val durationMs: Long?)

sealed interface SlidePhase {
    data object Idle : SlidePhase

    data object Editing : SlidePhase

    /** [progress] 0..1 for the whole job (photos laid out, then the video encoded), negative when unknown. */
    data class Working(val progress: Float, val step: String) : SlidePhase

    data class Done(val saved: SavedFile, val durationMs: Long) : SlidePhase

    data class Failed(val message: String, val debug: String? = null) : SlidePhase
}

data class SlideUiState(
    val phase: SlidePhase = SlidePhase.Idle,
    val photos: List<SlidePhoto> = emptyList(),
    val music: SlideMusic? = null,
    val format: SlideFormat = SlideFormat.VERTICAL,
    val fit: SlideFit = SlideFit.FILL,
    val transition: SlideTransition = SlideTransition.KEN_BURNS,
    /** Photos being read (thumbnails). */
    val adding: Int = 0,
    /** A one-off remark (files left out), shown above the list. */
    val notice: String? = null,
) {
    val slides: List<Slide> get() = photos.map { Slide(it.id, it.name, it.durationMs) }
    val totalMs: Long get() = SlidePlan.totalMs(slides, transition)
    val refusal: String? get() = SlidePlan.refusal(photos.size)

    /** The duration every photo has, or null when they differ. */
    val commonDurationMs: Long? get() = photos.map { it.durationMs }.distinct().singleOrNull()
}

/**
 * The Diaporama's state and work, held by the App like the other tools' (a video keeps being made when the user leaves
 * the screen). Each photo is copied into a work dir, laid out at the video's size by [SlideFrames], then FFmpeg joins
 * the pictures (and the music) into an MP4 saved to the Gallery (Movies/Gulp); the work dir is deleted.
 */
class SlideController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(SlideUiState())
    val state: StateFlow<SlideUiState> = _state.asStateFlow()

    private var workJob: Job? = null
    private val generation = AtomicInteger(0)
    private val ids = AtomicLong(0)

    val busy: Boolean get() = _state.value.phase is SlidePhase.Working

    /** Adds the chosen photos after the current ones. False while a video is being made. */
    fun add(uris: List<Uri>): Boolean {
        if (busy) return false
        val room = SlidePlan.MAX_PHOTOS - _state.value.photos.size
        val taken = uris.distinct().take(room.coerceAtLeast(0))
        val skipped = uris.distinct().size - taken.size
        if (taken.isEmpty()) {
            _state.update { it.copy(notice = "${SlidePlan.MAX_PHOTOS} photos au plus.") }
            return true
        }
        val duration = _state.value.commonDurationMs ?: SlidePlan.DEFAULT_DURATION_MS
        _state.update { it.copy(adding = it.adding + taken.size, notice = null) }
        scope.launch(Dispatchers.IO) {
            var refused = 0
            for (uri in taken) {
                val photo = try {
                    val info = FileIntake.describe(context, uri)
                    val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
                    if (FileKind.detect(info.name, info.mime, head) == FileKind.IMAGE) {
                        SlidePhoto(ids.incrementAndGet(), uri, info.name, duration, SlideFrames.thumbnail(context, uri))
                    } else {
                        null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (photo == null) refused++
                _state.update { s ->
                    val photos = if (photo != null && !busyPhase(s)) s.photos + photo else s.photos
                    val phase = if (photos.isNotEmpty() && (s.phase == SlidePhase.Idle)) SlidePhase.Editing else s.phase
                    s.copy(photos = photos, phase = phase, adding = (s.adding - 1).coerceAtLeast(0))
                }
            }
            val notice = listOfNotNull(
                if (refused > 0) "$refused fichier${if (refused > 1) "s" else ""} laissé${if (refused > 1) "s" else ""} de côté : ce ne sont pas des photos." else null,
                if (skipped > 0) "${SlidePlan.MAX_PHOTOS} photos au plus : $skipped de trop." else null,
            ).joinToString(" ").ifBlank { null }
            if (notice != null) _state.update { it.copy(notice = notice) }
        }
        return true
    }

    private fun busyPhase(s: SlideUiState) = s.phase is SlidePhase.Working

    fun remove(id: Long) = edit { s ->
        val photos = s.photos.filterNot { it.id == id }
        s.copy(photos = photos, phase = if (photos.isEmpty() && s.adding == 0) SlidePhase.Idle else s.phase, notice = null)
    }

    /** Moves photo [id] by [delta] places (−1 earlier, +1 later). */
    fun move(id: Long, delta: Int) = edit { s ->
        val from = s.photos.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in s.photos.indices) return@edit s
        val list = s.photos.toMutableList()
        list.add(to, list.removeAt(from))
        s.copy(photos = list)
    }

    /** Next duration for photo [id] (1,5 s → 2 s → … → 7 s → 1,5 s). */
    fun cycleDuration(id: Long) = edit { s ->
        s.copy(photos = s.photos.map { if (it.id == id) it.copy(durationMs = SlidePlan.nextDuration(it.durationMs)) else it })
    }

    /** The same duration for every photo. */
    fun setAllDurations(ms: Long) = edit { s -> s.copy(photos = s.photos.map { it.copy(durationMs = ms) }) }

    fun setFormat(format: SlideFormat) = edit { it.copy(format = format) }

    fun setFit(fit: SlideFit) = edit { it.copy(fit = fit) }

    fun setTransition(transition: SlideTransition) = edit { it.copy(transition = transition) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private inline fun edit(crossinline change: (SlideUiState) -> SlideUiState) {
        _state.update { if (it.phase == SlidePhase.Editing) change(it) else it }
    }

    /** Takes the chosen music (sound file, or the sound of a video). */
    fun pickMusic(uri: Uri) {
        if (busy) return
        scope.launch(Dispatchers.IO) {
            val music = try {
                val info = FileIntake.describe(context, uri)
                val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
                val kind = FileKind.detect(info.name, info.mime, head)
                if ((kind != FileKind.AUDIO && kind != FileKind.VIDEO) || FfmpegInput.refusal(head) != null) {
                    _state.update { it.copy(notice = "« ${info.name} » n'est pas un son : choisissez une musique (MP3, M4A, OGG…).") }
                    return@launch
                }
                SlideMusic(uri, info.name, durationOf(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Gulp ne peut pas lire cette musique.") }
                return@launch
            }
            _state.update { if (it.phase == SlidePhase.Editing) it.copy(music = music, notice = null) else it }
        }
    }

    fun removeMusic() = edit { it.copy(music = null) }

    private fun durationOf(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Back to an empty show (the format, fit and transition stay). */
    fun reset() {
        if (busy) return
        _state.update { SlideUiState(format = it.format, fit = it.fit, transition = it.transition) }
    }

    /** From a result or an error, back to the same photos and settings. */
    fun backToEdit() {
        _state.update { if (it.photos.isNotEmpty() && !busyPhase(it)) it.copy(phase = SlidePhase.Editing) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is SlidePhase.Working) it.copy(phase = SlidePhase.Editing) else it }
    }

    fun start() {
        val current = _state.value
        if (busy || current.phase !is SlidePhase.Editing && current.phase !is SlidePhase.Failed) return
        if (current.refusal != null || current.adding > 0) return
        val photos = current.photos
        val slides = current.slides
        val music = current.music
        val format = current.format
        val fit = current.fit
        val transition = current.transition
        val totalMs = current.totalMs
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = SlidePhase.Working(0f, "Préparation des photos…"), notice = null) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer le diaporama.", null)
                return@launch
            }
            try {
                val job = coroutineContext
                val (frameW, frameH) = SlidePlan.frameSize(format, transition)
                val frames = photos.mapIndexed { i, photo ->
                    job.ensureActive()
                    working(token, PREPARE_SHARE * i / photos.size, "Photo ${i + 1} sur ${photos.size}…")
                    val copy = FileIntake.copyToWork(context, photo.uri, PHOTO_MAX_BYTES, dir) { job.ensureActive() }
                    val frame = File(dir, String.format(java.util.Locale.ROOT, "slide%03d.jpg", i + 1))
                    try {
                        SlideFrames.render(copy, frame, frameW, frameH, fit)
                    } catch (e: ConversionError) {
                        throw ConversionError("Photo ${i + 1} (« ${photo.name} ») : ${e.message}")
                    } catch (e: OutOfMemoryError) {
                        throw ConversionError("Photo ${i + 1} (« ${photo.name} ») trop grande pour la mémoire du téléphone.")
                    } finally {
                        copy.delete()
                    }
                    frame
                }
                val musicFile = music?.let { m ->
                    working(token, PREPARE_SHARE, "Préparation de la musique…")
                    val copy = FileIntake.copyToWork(context, m.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
                    val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
                    FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
                    val named = File(dir, "music.${FfmpegInput.workExt(head, m.name)}")
                    if (!copy.renameTo(named)) throw ConversionError("Impossible de préparer la musique.")
                    named
                }
                job.ensureActive()
                val now = Calendar.getInstance()
                val name = SlidePlan.outputName(
                    now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR),
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                )
                val out = File(dir, "out").apply { mkdirs() }
                val work = File(out, "slideshow.mp4")
                val root = WorkDir.root(context).absolutePath
                val args = SlidePlan.args(root, frames.map { it.absolutePath }, slides, musicFile?.absolutePath, work.absolutePath, format, transition)
                val step = "Montage de la vidéo…"
                working(token, PREPARE_SHARE, step)
                FfmpegRunner(context, engine).run(args, work, Target.MP4, totalMs / 1000.0) { fraction ->
                    working(token, PREPARE_SHARE + (1f - PREPARE_SHARE) * fraction.coerceIn(0f, 1f), step)
                }
                job.ensureActive()
                frames.forEach(File::delete)
                val named = File(out, name)
                if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer la vidéo.")
                val saved = MediaExporter.export(context, named)
                named.delete()
                update(token) { it.copy(phase = SlidePhase.Done(saved, totalMs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Fichier trop volumineux.", null)
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Une photo ne peut pas être ouverte.", null)
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès à une photo : ajoutez-la à nouveau.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: Throwable) {
                fail(token, FfmpegErrors.GENERIC, e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (SlideUiState) -> SlideUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun working(token: Int, progress: Float, step: String) {
        update(token) { if (it.phase is SlidePhase.Working) it.copy(phase = SlidePhase.Working(progress, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String?) {
        update(token) { it.copy(phase = SlidePhase.Failed(message, debug)) }
    }

    private companion object {
        /** Share of the progress bar for laying out the photos; the rest is FFmpeg's. */
        const val PREPARE_SHARE = 0.25f

        /** Largest photo taken (a 200 MP JPEG is under 60 Mo). */
        const val PHOTO_MAX_BYTES = 80L * 1024 * 1024
    }
}
