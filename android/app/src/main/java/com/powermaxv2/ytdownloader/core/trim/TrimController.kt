package com.powermaxv2.ytdownloader.core.trim

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.powermaxv2.ytdownloader.core.preview.WaveformDecoder
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The file given to the Découpeur, with what was read from it. */
data class TrimSource(
    val file: LocalFile,
    val durationMs: Long,
    val hasVideo: Boolean,
    val width: Int? = null,
    val height: Int? = null,
)

/** Pictures drawn under the handles: frames of a video, or the loudness of a sound (0..1 per bar). */
class TrimStrip(val frames: List<Bitmap> = emptyList(), val wave: FloatArray? = null)

sealed interface TrimPhase {
    data object Idle : TrimPhase

    data object Reading : TrimPhase

    data class Unsupported(val message: String) : TrimPhase

    data object Ready : TrimPhase

    /** [progress] 0..1 or negative when unknown. */
    data class Working(val progress: Float, val step: String) : TrimPhase

    data class Done(val saved: SavedFile, val before: Long?, val output: TrimOutput, val durationMs: Long, val copied: Boolean) : TrimPhase

    data class Failed(val message: String, val debug: String? = null) : TrimPhase
}

data class TrimUiState(
    val phase: TrimPhase = TrimPhase.Idle,
    val source: TrimSource? = null,
    val range: TrimRange = TrimRange(0, 0),
    val output: TrimOutput = TrimOutput.VIDEO,
    /** Re-encode the video so the cut falls on the exact frame (slower). */
    val exact: Boolean = false,
    val strip: TrimStrip = TrimStrip(),
    /** Frame at the start of the passage (videos), refreshed after each move of the start handle. */
    val startFrame: Bitmap? = null,
) {
    val sourceExt: String get() = source?.file?.name?.let(SafeNames::extensionOf).orEmpty()

    /** Whether this choice is a stream copy (instant). */
    val copies: Boolean get() = source?.let { TrimPlan.copies(output, sourceExt, it.hasVideo, exact) } ?: false
}

/**
 * The Découpeur's state and work, held by the App like the Compresseur's: a cut keeps running when the user leaves
 * the tool. The file is read in place (duration, frames, waveform) and only copied into a work dir when the cut
 * starts; FFmpeg writes the passage, which is saved to the Gallery / Music, and the copy is deleted.
 */
class TrimController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(TrimUiState())
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var frameJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is TrimPhase.Working

    /** Takes the chosen file (replacing the current one). False while a cut runs. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        frameJob?.cancel()
        _state.update { TrimUiState(phase = TrimPhase.Reading, output = it.output, exact = it.exact) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                TrimUiState(phase = TrimPhase.Unsupported("Android a refusé l'accès : choisissez le fichier à nouveau."))
            } catch (e: Exception) {
                TrimUiState(phase = TrimPhase.Unsupported(e.message ?: "Impossible de lire ce fichier."))
            }
            _state.update { current -> if (current.phase is TrimPhase.Working) current else next }
            val source = next.source ?: return@launch
            val strip = runCatching { strip(uri, source) }.getOrNull() ?: return@launch
            coroutineContext.ensureActive()
            _state.update { if (it.source?.file?.uri == uri) it.copy(strip = strip) else it }
            refreshStartFrame()
        }
        return true
    }

    private fun describe(uri: Uri): TrimUiState {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.VIDEO && kind != FileKind.AUDIO) {
            return TrimUiState(phase = TrimPhase.Unsupported("Le Découpeur prend une vidéo ou un son. « ${info.name} » n'en est pas un."))
        }
        FfmpegInput.refusal(head)?.let { return TrimUiState(phase = TrimPhase.Unsupported(it)) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return TrimUiState(phase = TrimPhase.Unsupported("Gulp ne trouve pas la durée de ce fichier : il est peut-être abîmé."))
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            val source = TrimSource(info, duration, hasVideo, w, h)
            val previous = _state.value
            val output = previous.output.takeIf { it in TrimPlan.outputs(hasVideo) } ?: TrimPlan.outputs(hasVideo).first()
            return TrimUiState(TrimPhase.Ready, source, TrimPlan.initial(duration, output), output, previous.exact)
        } catch (e: RuntimeException) {
            return TrimUiState(phase = TrimPhase.Unsupported("Gulp ne sait pas lire ce fichier sur ce téléphone."))
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Eight frames across a video, or the waveform of a sound. */
    private suspend fun strip(uri: Uri, source: TrimSource): TrimStrip {
        if (!source.hasVideo) return TrimStrip(wave = WaveformDecoder.decode(context, uri, WAVE_BARS)?.bars)
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until FRAMES) {
                coroutineContext.ensureActive()
                val us = source.durationMs * 1_000L * (2 * i + 1) / (2 * FRAMES)
                frame(retriever, us, FRAME_HEIGHT)?.let(frames::add)
            }
        } catch (e: CancellationException) {
            frames.forEach(Bitmap::recycle)
            throw e
        } catch (e: RuntimeException) {
            // No frames: the handles still work.
        } finally {
            runCatching { retriever.release() }
        }
        return TrimStrip(frames = frames)
    }

    /** A frame near [us] microseconds, [height] pixels high (the closest key frame: fast). */
    private fun frame(retriever: MediaMetadataRetriever, us: Long, height: Int): Bitmap? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, height * 3, height)
        } else {
            retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } ?: return null
        if (raw.height <= height * 1.2f) return raw
        val w = max(1, (raw.width * height.toFloat() / raw.height).roundToInt())
        return Bitmap.createScaledBitmap(raw, w, height, true).also { if (it !== raw) raw.recycle() }
    }

    /** Moves the start handle (ms). */
    fun setStart(ms: Long) = edit { s -> s.copy(range = TrimPlan.moveStart(s.range, ms, s.source?.durationMs ?: 0L, s.output)) }

    /** Moves the end handle (ms). */
    fun setEnd(ms: Long) = edit { s -> s.copy(range = TrimPlan.moveEnd(s.range, ms, s.source?.durationMs ?: 0L, s.output)) }

    /** The start handle was let go: the picture of the new start is fetched. */
    fun settle() = refreshStartFrame()

    fun setOutput(output: TrimOutput) = edit { s ->
        val duration = s.source?.durationMs ?: 0L
        s.copy(output = output, range = TrimPlan.clamp(s.range, duration, output))
    }

    fun setExact(exact: Boolean) = edit { it.copy(exact = exact) }

    private inline fun edit(crossinline change: (TrimUiState) -> TrimUiState) {
        _state.update { if (it.phase == TrimPhase.Ready && it.source != null) change(it) else it }
    }

    private fun refreshStartFrame() {
        val current = _state.value
        val source = current.source ?: return
        if (!source.hasVideo) return
        frameJob?.cancel()
        frameJob = scope.launch(Dispatchers.IO) {
            delay(120)
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, source.file.uri)
                frame(retriever, _state.value.range.startMs * 1_000L, START_FRAME_HEIGHT)
            } catch (e: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            coroutineContext.ensureActive()
            _state.update { if (it.source?.file?.uri == source.file.uri) it.copy(startFrame = bitmap) else it }
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        frameJob?.cancel()
        _state.update { current -> TrimUiState(output = current.output, exact = current.exact) }
    }

    /** From a result or an error, back to the same file and passage. */
    fun backToEdit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = TrimPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is TrimPhase.Working) it.copy(phase = TrimPhase.Ready) else it }
    }

    /** From a result: the same passage again as [output] (« Garder le son »). */
    fun again(output: TrimOutput) {
        backToEdit()
        setOutput(output)
        start()
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.phase !is TrimPhase.Ready && current.phase !is TrimPhase.Done && current.phase !is TrimPhase.Failed) return
        val range = TrimPlan.clamp(current.range, source.durationMs, current.output)
        if (range.durationMs <= 0) return
        val token = generation.incrementAndGet()
        val output = current.output
        val exact = current.exact
        _state.update { it.copy(phase = TrimPhase.Working(-1f, "Copie du fichier…")) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la coupe.")
                return@launch
            }
            try {
                val job = coroutineContext
                val copy = FileIntake.copyToWork(context, source.file.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
                val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
                FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
                val workExt = FfmpegInput.workExt(head, source.file.name)
                val input = File(dir, "input.$workExt")
                if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer le fichier.")
                // The extension the bytes call for decides the copy (a ".mp4" that is really a MKV stays MKV).
                val ext = TrimPlan.outputExt(output, workExt, source.hasVideo, exact)
                val work = File(dir, "output.$ext")
                val args = TrimPlan.args(WorkDir.root(context).absolutePath, input.absolutePath, work.absolutePath, range, output, workExt, source.hasVideo, exact)
                val copied = TrimPlan.copies(output, workExt, source.hasVideo, exact)
                progress(token, 0f, step(output, copied))
                FfmpegRunner(context, engine).run(args, work, target(output, ext), range.durationMs / 1000.0) { progress(token, it, step(output, copied)) }
                input.delete()
                coroutineContext.ensureActive()
                progress(token, -1f, "Enregistrement…")
                val named = FileIntake.uniqueFile(File(dir, "out").apply { mkdirs() }, TrimPlan.outputName(source.file.name, range, output, ext))
                named.delete()
                if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer le passage.")
                val saved = MediaExporter.export(context, named)
                update(token) { it.copy(phase = TrimPhase.Done(saved, source.file.size, output, range.durationMs, copied)) }
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

    private fun step(output: TrimOutput, copied: Boolean): String = when (output) {
        TrimOutput.VIDEO -> if (copied) "Découpe sans réencodage…" else "Découpe à l'image près…"
        TrimOutput.AUDIO -> "Extraction du son…"
        TrimOutput.RINGTONE -> "Création de la sonnerie…"
        TrimOutput.GIF -> "Création du GIF…"
    }

    private fun target(output: TrimOutput, ext: String): Target = when (output) {
        TrimOutput.VIDEO -> Target.entries.firstOrNull { it.ext == ext } ?: Target.MP4
        TrimOutput.AUDIO -> Target.entries.firstOrNull { it.ext == ext } ?: Target.M4A
        TrimOutput.RINGTONE -> Target.MP3
        TrimOutput.GIF -> Target.GIF
    }

    private inline fun update(token: Int, crossinline change: (TrimUiState) -> TrimUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is TrimPhase.Working) it.copy(phase = TrimPhase.Working(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = TrimPhase.Failed(message, debug)) }
    }

    private companion object {
        const val FRAMES = 8
        const val FRAME_HEIGHT = 96
        const val START_FRAME_HEIGHT = 360
        const val WAVE_BARS = 48
    }
}
