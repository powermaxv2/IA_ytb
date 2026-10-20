package com.powermaxv2.ytdownloader.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.convert.Target
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
import kotlin.coroutines.coroutineContext

/** The video given to the Capture d'image, with what was read from it. */
data class CaptureSource(
    val file: LocalFile,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
)

/** What « Image la plus nette » found: the frame it moved to and how much sharper it is than where it started. */
data class HuntResult(val fromMs: Long, val toMs: Long, val gainPercent: Int) {
    val moved: Boolean get() = fromMs != toMs
}

sealed interface CapturePhase {
    data object Idle : CapturePhase

    data object Reading : CapturePhase

    data class Unsupported(val message: String) : CapturePhase

    data object Ready : CapturePhase

    /** [progress] 0..1 or negative when unknown. */
    data class Working(val progress: Float, val step: String) : CapturePhase

    /** The photos saved, with small pictures of them for the screen. */
    data class Done(val saved: List<SavedFile>, val shots: List<Bitmap>, val format: CaptureFormat) : CapturePhase

    data class Failed(val message: String, val debug: String? = null) : CapturePhase
}

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.Idle,
    val source: CaptureSource? = null,
    /** Playhead, always on the middle of a frame ([CapturePlan.snap]). */
    val positionMs: Long = 0L,
    /** Frames spread across the video, under the playhead. */
    val strip: List<Bitmap> = emptyList(),
    /** The frame at the playhead (exact once the finger lets go, the closest key frame while it drags). */
    val preview: Bitmap? = null,
    /** Sharpness of [preview] on the 0..1 gauge, null until scored. */
    val sharpness: Float? = null,
    /** True while « Image la plus nette » scores the frames around the playhead. */
    val hunting: Boolean = false,
    val hunt: HuntResult? = null,
    val format: CaptureFormat = CaptureFormat.JPG,
    val mode: CaptureMode = CaptureMode.SINGLE,
) {
    val frameIndex: Long get() = source?.let { CapturePlan.frameIndex(positionMs, it.fps) } ?: 0L
}

/**
 * The Capture d'image's state and work. The video is read in place with Android's own decoder (nothing is copied):
 * frames for the strip and the preview, an exact frame for the photo, written in full resolution to Pictures/Gulp.
 * Held by [shared] so a capture keeps going off its screen.
 */
class CaptureController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var previewJob: Job? = null
    private var huntJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    /** One decoder for the current video; MediaMetadataRetriever is not thread safe, so every use takes [lock]. */
    private val lock = Mutex()
    private var retriever: MediaMetadataRetriever? = null
    private var retrieverUri: Uri? = null

    val busy: Boolean get() = _state.value.phase is CapturePhase.Working

    /** Takes the chosen video (replacing the current one). False while photos are being saved. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        previewJob?.cancel()
        huntJob?.cancel()
        _state.update { CaptureUiState(phase = CapturePhase.Reading, format = it.format, mode = it.mode) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                CaptureUiState(phase = CapturePhase.Unsupported("Android a refusé l'accès : choisissez la vidéo à nouveau."))
            } catch (e: Exception) {
                CaptureUiState(phase = CapturePhase.Unsupported(e.message ?: "Impossible de lire cette vidéo."))
            }
            _state.update { current -> if (current.phase is CapturePhase.Working) current else next }
            val source = next.source ?: return@launch
            refreshPreview(exact = true)
            val strip = runCatching { strip(source) }.getOrDefault(emptyList())
            coroutineContext.ensureActive()
            _state.update { if (it.source?.file?.uri == uri) it.copy(strip = strip) else it }
        }
        return true
    }

    private suspend fun describe(uri: Uri): CaptureUiState {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.VIDEO) {
            return CaptureUiState(phase = CapturePhase.Unsupported("La Capture d'image prend une vidéo. « ${info.name} » n'en est pas une."))
        }
        return withRetriever(uri) { r ->
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return@withRetriever CaptureUiState(phase = CapturePhase.Unsupported("Gulp ne trouve pas la durée de cette vidéo : elle est peut-être abîmée."))
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return@withRetriever CaptureUiState(phase = CapturePhase.Unsupported("Ce fichier n'a pas d'images : c'est un son."))
            }
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            val frames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            } else {
                null
            }
            val rate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            val fps = CapturePlan.fps(frames, duration, rate)
            val source = CaptureSource(info, duration, w.coerceAtLeast(1), h.coerceAtLeast(1), fps)
            val previous = _state.value
            CaptureUiState(CapturePhase.Ready, source, CapturePlan.snap(0, fps, duration), format = previous.format, mode = previous.mode)
        } ?: CaptureUiState(phase = CapturePhase.Unsupported("Gulp ne sait pas lire cette vidéo sur ce téléphone."))
    }

    /** Runs [block] with the decoder of [uri] (opened on first use), or returns null when Android cannot read it. */
    private suspend fun <T> withRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T): T? = lock.withLock {
        try {
            val r = retriever?.takeIf { retrieverUri == uri } ?: run {
                retriever?.let { runCatching { it.release() } }
                retriever = null
                MediaMetadataRetriever().also {
                    it.setDataSource(context, uri)
                    retriever = it
                    retrieverUri = uri
                }
            }
            block(r)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: RuntimeException) {
            if (e is SecurityException) throw e
            null
        }
    }

    private suspend fun releaseRetriever() = lock.withLock {
        retriever?.let { runCatching { it.release() } }
        retriever = null
        retrieverUri = null
    }

    /**
     * The frame at [us] µs, its longest side at most [maxSide] px (0 = full size). [exact] decodes up to that very
     * frame; otherwise the closest key frame (fast, for the strip and while the finger drags).
     */
    private fun frame(r: MediaMetadataRetriever, us: Long, maxSide: Int, exact: Boolean, source: CaptureSource): Bitmap? {
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val raw = if (maxSide > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val (w, h) = CapturePlan.fit(source.width, source.height, maxSide)
            r.getScaledFrameAtTime(us, option, w, h)
        } else {
            r.getFrameAtTime(us, option)
        } ?: return null
        if (maxSide <= 0 || maxOf(raw.width, raw.height) <= maxSide * 1.2f) return raw
        val (w, h) = CapturePlan.fit(raw.width, raw.height, maxSide)
        return Bitmap.createScaledBitmap(raw, w, h, true).also { if (it !== raw) raw.recycle() }
    }

    /** Ten frames across the video, for the strip. */
    private suspend fun strip(source: CaptureSource): List<Bitmap> {
        val frames = ArrayList<Bitmap>()
        for (i in 0 until STRIP_FRAMES) {
            coroutineContext.ensureActive()
            val us = source.durationMs * 1_000L * (2 * i + 1) / (2 * STRIP_FRAMES)
            withRetriever(source.file.uri) { frame(it, us, STRIP_SIDE, exact = false, source) }?.let(frames::add)
        }
        return frames
    }

    /** Sharpness of [bitmap], scored at [SCORE_SIDE] px so every frame is judged at the same size. */
    private fun score(bitmap: Bitmap): Double {
        val (w, h) = CapturePlan.fit(bitmap.width, bitmap.height, SCORE_SIDE)
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        return CapturePlan.sharpness(CapturePlan.luma(pixels), w, h)
    }

    /** Moves the playhead while the finger drags (key frames, fast). */
    fun scrub(ms: Long) {
        edit { s -> s.copy(positionMs = CapturePlan.snap(ms, s.source!!.fps, s.source.durationMs), hunt = null) }
        refreshPreview(exact = false)
    }

    /** The finger let go: the exact frame is decoded and scored. */
    fun settle() = refreshPreview(exact = true)

    /** [frames] images forward (or back). */
    fun step(frames: Long) {
        edit { s -> s.copy(positionMs = CapturePlan.step(s.positionMs, frames, s.source!!.fps, s.source.durationMs), hunt = null) }
        refreshPreview(exact = true)
    }

    /** [deltaMs] forward (or back). */
    fun jump(deltaMs: Long) {
        edit { s -> s.copy(positionMs = CapturePlan.jump(s.positionMs, deltaMs, s.source!!.fps, s.source.durationMs), hunt = null) }
        refreshPreview(exact = true)
    }

    fun setFormat(format: CaptureFormat) = edit { it.copy(format = format) }

    fun setMode(mode: CaptureMode) = edit { it.copy(mode = mode) }

    private inline fun edit(crossinline change: (CaptureUiState) -> CaptureUiState) {
        _state.update { if (it.phase == CapturePhase.Ready && it.source != null && !it.hunting) change(it) else it }
    }

    private fun refreshPreview(exact: Boolean) {
        val source = _state.value.source ?: return
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.IO) {
            delay(if (exact) 90 else 30)
            val ms = _state.value.positionMs
            val bitmap = withRetriever(source.file.uri) { frame(it, ms * 1_000L, PREVIEW_SIDE, exact, source) } ?: return@launch
            coroutineContext.ensureActive()
            val level = if (exact) CapturePlan.level(score(bitmap)) else null
            _state.update {
                if (it.source?.file?.uri == source.file.uri && it.positionMs == ms) {
                    it.copy(preview = bitmap, sharpness = level ?: it.sharpness)
                } else {
                    it
                }
            }
        }
    }

    /** « Image la plus nette » : scores the frames around the playhead and moves to the sharpest one. */
    fun hunt() {
        val current = _state.value
        val source = current.source ?: return
        if (current.phase != CapturePhase.Ready || current.hunting) return
        _state.update { it.copy(hunting = true, hunt = null) }
        huntJob?.cancel()
        huntJob = scope.launch(Dispatchers.IO) {
            val from = current.positionMs
            val candidates = CapturePlan.around(from, CapturePlan.HUNT_COUNT, 1, source.fps, source.durationMs)
            val scores = ArrayList<Double>()
            val pictures = HashMap<Long, Bitmap>()
            try {
                for (ms in candidates) {
                    coroutineContext.ensureActive()
                    val bitmap = withRetriever(source.file.uri) { frame(it, ms * 1_000L, PREVIEW_SIDE, exact = true, source) }
                    scores += bitmap?.let(::score) ?: 0.0
                    bitmap?.let { pictures[ms] = it }
                }
                val to = CapturePlan.pick(candidates, scores, from)
                val before = scores.getOrElse(candidates.indexOf(from)) { 0.0 }
                val after = scores[candidates.indexOf(to)]
                val result = HuntResult(from, to, CapturePlan.gainPercent(before, after))
                _state.update {
                    if (it.source?.file?.uri != source.file.uri) return@update it
                    it.copy(
                        positionMs = to,
                        preview = pictures[to] ?: it.preview,
                        sharpness = CapturePlan.level(after),
                        hunting = false,
                        hunt = result,
                    )
                }
                pictures.forEach { (ms, b) -> if (ms != to) b.recycle() }
            } catch (e: CancellationException) {
                _state.update { it.copy(hunting = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(hunting = false) }
            }
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        previewJob?.cancel()
        huntJob?.cancel()
        _state.update { current -> CaptureUiState(format = current.format, mode = current.mode) }
        scope.launch(Dispatchers.IO) { releaseRetriever() }
    }

    /** From a result or an error, back to the same video and moment. */
    fun backToEdit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = CapturePhase.Ready, hunting = false) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is CapturePhase.Working) it.copy(phase = CapturePhase.Ready) else it }
    }

    /** Saves the frame at the playhead (or a burst around it) in full resolution. */
    fun capture() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.hunting) return
        if (current.phase !is CapturePhase.Ready && current.phase !is CapturePhase.Failed) return
        val token = generation.incrementAndGet()
        val format = current.format
        val moments = when (current.mode) {
            CaptureMode.SINGLE -> listOf(current.positionMs)
            CaptureMode.BURST -> CapturePlan.around(current.positionMs, CapturePlan.BURST_COUNT, CapturePlan.BURST_SPACING, source.fps, source.durationMs)
        }
        huntJob?.cancel()
        previewJob?.cancel()
        _state.update { it.copy(phase = CapturePhase.Working(0f, step(0, moments.size))) }
        workJob = scope.launch(Dispatchers.IO) {
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la capture.")
                return@launch
            }
            val saved = ArrayList<SavedFile>()
            val shots = ArrayList<Bitmap>()
            try {
                moments.forEachIndexed { i, ms ->
                    coroutineContext.ensureActive()
                    progress(token, i.toFloat() / moments.size, step(i, moments.size))
                    val full = withRetriever(source.file.uri) { frame(it, ms * 1_000L, 0, exact = true, source) }
                        ?: throw ConversionError("Android n'a pas pu lire l'image à ${CapturePlan.timecode(ms)}.")
                    try {
                        val out = FileIntake.uniqueFile(dir, CapturePlan.outputName(source.file.name, ms, format))
                        Images.encode(full, if (format == CaptureFormat.PNG) Target.PNG else Target.JPG, CapturePlan.JPG_QUALITY, out)
                        val (w, h) = CapturePlan.fit(full.width, full.height, SHOT_SIDE)
                        shots += Bitmap.createScaledBitmap(full, w, h, true)
                        saved += MediaExporter.export(context, out)
                        out.delete()
                    } finally {
                        full.recycle()
                    }
                }
                update(token) { it.copy(phase = CapturePhase.Done(saved, shots, format)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: "La capture n'a pas abouti.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisissez la vidéo à nouveau, ou autorisez le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Cette vidéo est trop grande pour ce téléphone : essayez une image à la fois.", e.toString())
            } catch (e: Throwable) {
                fail(token, "La capture n'a pas abouti.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private fun step(i: Int, total: Int): String = if (total == 1) "Capture en pleine qualité…" else "Image ${i + 1} sur $total…"

    private inline fun update(token: Int, crossinline change: (CaptureUiState) -> CaptureUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is CapturePhase.Working) it.copy(phase = CapturePhase.Working(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = CapturePhase.Failed(message, debug)) }
    }

    companion object {
        private const val STRIP_FRAMES = 10
        private const val STRIP_SIDE = 160
        private const val PREVIEW_SIDE = 960
        private const val SCORE_SIDE = 320
        private const val SHOT_SIDE = 360

        /** The one controller of the app, made on first use and kept as long as the app lives. */
        val shared: CaptureController by lazy { CaptureController(App.instance, App.instance.scope) }
    }
}
