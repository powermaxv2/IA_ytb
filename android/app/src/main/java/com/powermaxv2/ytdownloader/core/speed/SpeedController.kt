package com.powermaxv2.ytdownloader.core.speed

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.powermaxv2.ytdownloader.App
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
import com.powermaxv2.ytdownloader.core.files.LocalFile
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The video given to the Vitesse vidéo, with what was read from it. */
data class SpeedSource(
    val file: LocalFile,
    val durationMs: Long,
    val hasAudio: Boolean,
    val width: Int? = null,
    val height: Int? = null,
    /** Frames per second of the video track, when the file says. */
    val fps: Double? = null,
    /** Sample rate of the sound track, when the file says. */
    val sampleRate: Int? = null,
)

sealed interface SpeedPhase {
    data object Idle : SpeedPhase

    data object Reading : SpeedPhase

    data class Unsupported(val message: String) : SpeedPhase

    data object Ready : SpeedPhase

    /** [progress] 0..1 or negative when unknown. */
    data class Working(val progress: Float, val step: String) : SpeedPhase

    data class Done(val saved: SavedFile, val speed: Double, val sound: SpeedSound, val durationMs: Long) : SpeedPhase

    data class Failed(val message: String, val debug: String? = null) : SpeedPhase
}

data class SpeedUiState(
    val phase: SpeedPhase = SpeedPhase.Idle,
    val source: SpeedSource? = null,
    val speed: Double = SpeedPlan.DEFAULT_SPEED,
    val sound: SpeedSound = SpeedSound.NATURAL,
    /** A few frames across the video, run through the film strip at the chosen speed. */
    val frames: List<Bitmap> = emptyList(),
) {
    /** Length of the result. */
    val outputMs: Long get() = SpeedPlan.outputMs(source?.durationMs ?: 0L, speed)
}

/**
 * The Vitesse vidéo's state and work: one controller for the whole app ([shared]), so a long slow motion keeps
 * running when the user leaves the tool. The video is read in place (length, tracks, frames) and only copied into
 * a work dir when the change starts; FFmpeg writes the MP4, which is saved to Movies › Gulp, and the copy is deleted.
 */
class SpeedController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(SpeedUiState())
    val state: StateFlow<SpeedUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is SpeedPhase.Working

    /** Takes the chosen video (replacing the current one). False while a change runs. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        _state.update { SpeedUiState(phase = SpeedPhase.Reading, speed = it.speed, sound = it.sound) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SpeedUiState(phase = SpeedPhase.Unsupported("Android a refusé l'accès : choisissez la vidéo à nouveau."))
            } catch (e: Exception) {
                SpeedUiState(phase = SpeedPhase.Unsupported(e.message ?: "Impossible de lire cette vidéo."))
            }
            _state.update { current -> if (current.phase is SpeedPhase.Working) current else next.copy(speed = current.speed, sound = current.sound) }
            if (next.source == null) return@launch
            val frames = runCatching { frames(uri, next.source.durationMs) }.getOrNull() ?: return@launch
            coroutineContext.ensureActive()
            _state.update { if (it.source?.file?.uri == uri) it.copy(frames = frames) else it }
        }
        return true
    }

    private fun describe(uri: Uri): SpeedUiState {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.VIDEO) {
            return SpeedUiState(phase = SpeedPhase.Unsupported("La Vitesse vidéo prend une vidéo. « ${info.name} » n'en est pas une."))
        }
        FfmpegInput.refusal(head)?.let { return SpeedUiState(phase = SpeedPhase.Unsupported(it)) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return SpeedUiState(phase = SpeedPhase.Unsupported("Gulp ne trouve pas la durée de cette vidéo : elle est peut-être abîmée."))
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return SpeedUiState(phase = SpeedPhase.Unsupported("Ce fichier n'a pas d'image : pour un son, essayez le Découpeur."))
            }
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            val (fps, rate) = tracks(uri)
            return SpeedUiState(SpeedPhase.Ready, SpeedSource(info, duration, hasAudio, w, h, fps, rate))
        } catch (e: RuntimeException) {
            return SpeedUiState(phase = SpeedPhase.Unsupported("Gulp ne sait pas lire cette vidéo sur ce téléphone."))
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Frame rate of the video track and sample rate of the sound track, when the container says (else null). */
    private fun tracks(uri: Uri): Pair<Double?, Int?> {
        val extractor = MediaExtractor()
        var fps: Double? = null
        var rate: Int? = null
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && fps == null && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
                        .recoverCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }
                        .getOrNull()
                }
                if (mime.startsWith("audio/") && rate == null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    rate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull()
                }
            }
        } catch (e: Exception) {
            // Unknown: the plan falls back to 30 fps and 48 kHz.
        } finally {
            runCatching { extractor.release() }
        }
        return fps to rate
    }

    /** Six frames across the video, small (the closest key frames: fast). */
    private suspend fun frames(uri: Uri, durationMs: Long): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until FRAMES) {
                coroutineContext.ensureActive()
                val us = durationMs * 1_000L * (2 * i + 1) / (2 * FRAMES)
                val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_HEIGHT * 2, FRAME_HEIGHT)
                } else {
                    retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } ?: continue
                if (raw.height <= FRAME_HEIGHT * 1.2f) {
                    frames += raw
                } else {
                    val w = max(1, (raw.width * FRAME_HEIGHT.toFloat() / raw.height).roundToInt())
                    frames += Bitmap.createScaledBitmap(raw, w, FRAME_HEIGHT, true).also { if (it !== raw) raw.recycle() }
                }
            }
        } catch (e: CancellationException) {
            frames.forEach(Bitmap::recycle)
            throw e
        } catch (e: RuntimeException) {
            // No frames: the strip draws plain cells.
        } finally {
            runCatching { retriever.release() }
        }
        return frames
    }

    fun setSpeed(speed: Double) = edit { it.copy(speed = SpeedPlan.nearest(speed)) }

    fun setSound(sound: SpeedSound) = edit { it.copy(sound = sound) }

    private inline fun edit(crossinline change: (SpeedUiState) -> SpeedUiState) {
        _state.update { if (it.phase !is SpeedPhase.Working) change(it) else it }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { current -> SpeedUiState(speed = current.speed, sound = current.sound) }
    }

    /** From a result or an error, back to the same video. */
    fun backToEdit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = SpeedPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is SpeedPhase.Working) it.copy(phase = SpeedPhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.phase !is SpeedPhase.Ready && current.phase !is SpeedPhase.Done && current.phase !is SpeedPhase.Failed) return
        val token = generation.incrementAndGet()
        val speed = current.speed
        val sound = current.sound
        val outMs = SpeedPlan.outputMs(source.durationMs, speed)
        _state.update { it.copy(phase = SpeedPhase.Working(-1f, "Copie de la vidéo…")) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la vidéo.")
                return@launch
            }
            try {
                val job = coroutineContext
                val copy = FileIntake.copyToWork(context, source.file.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
                val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
                FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
                val input = File(dir, "input.${FfmpegInput.workExt(head, source.file.name)}")
                if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer la vidéo.")
                val work = File(dir, "output.mp4")
                val args = SpeedPlan.args(
                    WorkDir.root(context).absolutePath, input.absolutePath, work.absolutePath,
                    speed, sound, source.hasAudio, source.fps, source.sampleRate,
                )
                val step = if (SpeedPlan.isSlow(speed)) "Ralenti ${SpeedPlan.label(speed)}…" else "Accéléré ${SpeedPlan.label(speed)}…"
                progress(token, 0f, step)
                FfmpegRunner(context, engine).run(args, work, Target.MP4, outMs / 1000.0) { progress(token, it, step) }
                input.delete()
                coroutineContext.ensureActive()
                progress(token, -1f, "Enregistrement…")
                val named = FileIntake.uniqueFile(File(dir, "out").apply { mkdirs() }, SpeedPlan.outputName(source.file.name, speed))
                named.delete()
                if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer la vidéo.")
                val saved = MediaExporter.export(context, named)
                update(token) { it.copy(phase = SpeedPhase.Done(saved, speed, if (source.hasAudio) sound else SpeedSound.MUTE, outMs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Vidéo trop volumineuse.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Cette vidéo ne peut pas être ouverte.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisissez la vidéo à nouveau, ou autorisez le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: Throwable) {
                fail(token, FfmpegErrors.GENERIC, e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (SpeedUiState) -> SpeedUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is SpeedPhase.Working) it.copy(phase = SpeedPhase.Working(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = SpeedPhase.Failed(message, debug)) }
    }

    companion object {
        private const val FRAMES = 6
        private const val FRAME_HEIGHT = 120

        /** The one controller of the app, made on first use and kept as long as the app lives, like the tools' controllers in App. */
        val shared: SpeedController by lazy { SpeedController(App.instance, App.instance.scope, App.instance.engine) }
    }
}
