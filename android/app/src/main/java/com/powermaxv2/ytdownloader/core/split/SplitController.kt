package com.powermaxv2.ytdownloader.core.split

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
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.powermaxv2.ytdownloader.core.trim.TrimOutput
import com.powermaxv2.ytdownloader.core.trim.TrimPlan
import com.powermaxv2.ytdownloader.core.trim.TrimRange
import com.powermaxv2.ytdownloader.core.trim.TrimSource
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface SplitPhase {
    data object Idle : SplitPhase

    data object Reading : SplitPhase

    data class Unsupported(val message: String) : SplitPhase

    data object Ready : SplitPhase

    /** Part [index] (from 0) of [count] is being cut; [progress] 0..1 for the whole series, negative when unknown. */
    data class Working(val index: Int, val count: Int, val progress: Float, val step: String) : SplitPhase

    /** [saved] in order; [redone] parts had to be cut again exactly (their copy ran past the status length). */
    data class Done(val saved: List<SavedFile>, val copied: Boolean, val redone: Int = 0) : SplitPhase

    /** [saved]: the parts already in the Gallery when it failed. */
    data class Failed(val message: String, val debug: String? = null, val saved: List<SavedFile> = emptyList()) : SplitPhase
}

data class SplitUiState(
    val phase: SplitPhase = SplitPhase.Idle,
    val source: TrimSource? = null,
    val length: SplitLength = SplitLength.MINUTE,
    /** Same length for every part (a 2:10 video gives 3 × 0:43 rather than 1:00, 1:00, 0:10). */
    val equal: Boolean = true,
    /** Re-encode so each part starts on the exact frame (slower). */
    val exact: Boolean = false,
    /** Frames spread over the video: [frames] i is at the middle of the i-th of [frames].size slices. */
    val frames: List<Bitmap> = emptyList(),
) {
    val parts: List<TrimRange> get() = source?.let { SplitPlan.parts(it.durationMs, length, equal) }.orEmpty()

    val refusal: String? get() = source?.let { SplitPlan.refusal(it.durationMs, length) }

    val copies: Boolean get() = source?.file?.name?.let { TrimPlan.copies(TrimOutput.VIDEO, SafeNames.extensionOf(it), true, exact) } ?: false

    /** The frame closest to the middle of [part], or null while the frames are read. */
    fun thumb(part: TrimRange): Bitmap? {
        val duration = source?.durationMs ?: return null
        if (frames.isEmpty() || duration <= 0) return null
        val middle = (part.startMs + part.endMs) / 2.0
        return frames.indices.minByOrNull { i -> abs(middle - duration * (2 * i + 1) / (2.0 * frames.size)) }?.let(frames::get)
    }
}

/**
 * The Découpe statut's state and work, held by the App like the Découpeur's (a series keeps being cut when the user
 * leaves the tool). The video is read in place, copied once into a work dir, then each part is cut by FFmpeg with the
 * Découpeur's command, saved to the Gallery (Movies/Gulp) as soon as it is ready, and the copy is deleted.
 */
class SplitController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is SplitPhase.Working

    /** Takes the chosen video (replacing the current one). False while a series is being cut. */
    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        _state.update { SplitUiState(phase = SplitPhase.Reading, length = it.length, equal = it.equal, exact = it.exact) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SplitUiState(phase = SplitPhase.Unsupported("Android a refusé l'accès : choisissez la vidéo à nouveau."))
            } catch (e: Exception) {
                SplitUiState(phase = SplitPhase.Unsupported(e.message ?: "Impossible de lire cette vidéo."))
            }
            _state.update { current -> if (current.phase is SplitPhase.Working) current else next }
            val source = next.source ?: return@launch
            val frames = runCatching { frames(uri, source) }.getOrNull() ?: return@launch
            coroutineContext.ensureActive()
            _state.update { if (it.source?.file?.uri == uri) it.copy(frames = frames) else it }
        }
        return true
    }

    private fun describe(uri: Uri): SplitUiState {
        val previous = _state.value
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        if (FileKind.detect(info.name, info.mime, head) != FileKind.VIDEO) {
            return SplitUiState(phase = SplitPhase.Unsupported("La Découpe statut prend une vidéo. « ${info.name} » n'en est pas une."))
        }
        FfmpegInput.refusal(head)?.let { return SplitUiState(phase = SplitPhase.Unsupported(it)) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return SplitUiState(phase = SplitPhase.Unsupported("Gulp ne trouve pas la durée de cette vidéo : elle est peut-être abîmée."))
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return SplitUiState(phase = SplitPhase.Unsupported("« ${info.name} » n'a pas d'image : la Découpe statut prend une vidéo."))
            }
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            return SplitUiState(SplitPhase.Ready, TrimSource(info, duration, true, w, h), previous.length, previous.equal, previous.exact)
        } catch (e: RuntimeException) {
            return SplitUiState(phase = SplitPhase.Unsupported("Gulp ne sait pas lire cette vidéo sur ce téléphone."))
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** [FRAMES] frames spread over the video (the closest key frames: fast). */
    private suspend fun frames(uri: Uri, source: TrimSource): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until FRAMES) {
                coroutineContext.ensureActive()
                val us = source.durationMs * 1_000L * (2 * i + 1) / (2 * FRAMES)
                frame(retriever, us)?.let(frames::add)
            }
        } catch (e: CancellationException) {
            frames.forEach(Bitmap::recycle)
            throw e
        } catch (e: RuntimeException) {
            // No pictures: the parts are still listed.
        } finally {
            runCatching { retriever.release() }
        }
        // A missing frame would shift the others in time: all or nothing.
        return if (frames.size == FRAMES) frames else emptyList<Bitmap>().also { frames.forEach(Bitmap::recycle) }
    }

    private fun frame(retriever: MediaMetadataRetriever, us: Long): Bitmap? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_SIZE, FRAME_SIZE)
        } else {
            retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } ?: return null
        val longest = max(raw.width, raw.height)
        if (longest <= FRAME_SIZE * 1.2f) return raw
        val scale = FRAME_SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(raw, max(1, (raw.width * scale).roundToInt()), max(1, (raw.height * scale).roundToInt()), true)
            .also { if (it !== raw) raw.recycle() }
    }

    fun setLength(length: SplitLength) = edit { it.copy(length = length) }

    fun setEqual(equal: Boolean) = edit { it.copy(equal = equal) }

    fun setExact(exact: Boolean) = edit { it.copy(exact = exact) }

    private inline fun edit(crossinline change: (SplitUiState) -> SplitUiState) {
        _state.update { if (it.phase == SplitPhase.Ready && it.source != null) change(it) else it }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { SplitUiState(length = it.length, equal = it.equal, exact = it.exact) }
    }

    /** From a result or an error, back to the same video and settings. */
    fun backToEdit() {
        _state.update { if (it.source != null && !busy) it.copy(phase = SplitPhase.Ready) else it }
    }

    /** Stops the series; the parts already saved stay in the Gallery. */
    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is SplitPhase.Working) it.copy(phase = SplitPhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.phase !is SplitPhase.Ready && current.phase !is SplitPhase.Failed) return
        if (current.refusal != null) return
        val parts = current.parts
        if (parts.isEmpty()) return
        val token = generation.incrementAndGet()
        val length = current.length
        val exact = current.exact
        _state.update { it.copy(phase = SplitPhase.Working(0, parts.size, -1f, "Copie de la vidéo…")) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val saved = ArrayList<SavedFile>()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la découpe.", null, saved)
                return@launch
            }
            try {
                val job = coroutineContext
                val copy = FileIntake.copyToWork(context, source.file.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
                val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
                FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
                val workExt = FfmpegInput.workExt(head, source.file.name)
                val input = File(dir, "input.$workExt")
                if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer la vidéo.")
                val root = WorkDir.root(context).absolutePath
                val out = File(dir, "out").apply { mkdirs() }
                val copied = TrimPlan.copies(TrimOutput.VIDEO, workExt, true, exact)
                var redone = 0
                parts.forEachIndexed { index, range ->
                    coroutineContext.ensureActive()
                    fun overall(fraction: Float) = (index + fraction.coerceIn(0f, 1f)) / parts.size
                    val label = "Statut ${index + 1} sur ${parts.size}…"
                    working(token, index, parts.size, overall(0f), label)
                    var ext = TrimPlan.outputExt(TrimOutput.VIDEO, workExt, true, exact)
                    var work = cut(root, input, dir, range, workExt, exact, ext) { working(token, index, parts.size, overall(it), label) }
                    if (copied && SplitPlan.tooLong(durationOf(work) ?: 0L, length)) {
                        // The copy started on a key frame too far before the part: cut again on the exact frame.
                        work.delete()
                        redone++
                        val again = "Statut ${index + 1} sur ${parts.size} à la seconde près…"
                        working(token, index, parts.size, overall(0f), again)
                        ext = TrimPlan.outputExt(TrimOutput.VIDEO, workExt, true, exact = true)
                        work = cut(root, input, dir, range, workExt, true, ext) { working(token, index, parts.size, overall(it), again) }
                    }
                    coroutineContext.ensureActive()
                    val named = File(out, SplitPlan.outputName(source.file.name, index, parts.size, ext))
                    named.delete()
                    if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer le statut ${index + 1}.")
                    saved += MediaExporter.export(context, named)
                    named.delete()
                }
                input.delete()
                update(token) { it.copy(phase = SplitPhase.Done(saved.toList(), copied, redone)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug, saved)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Vidéo trop volumineuse.", null, saved)
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Cette vidéo ne peut pas être ouverte.", null, saved)
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisissez la vidéo à nouveau, ou autorisez le stockage.", e.toString(), saved)
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString(), saved)
            } catch (e: Throwable) {
                fail(token, FfmpegErrors.GENERIC, e.toString(), saved)
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    /** One part of [input] into a new file of the work [dir], with the Découpeur's command. */
    private suspend fun cut(root: String, input: File, dir: File, range: TrimRange, workExt: String, exact: Boolean, ext: String, onProgress: (Float) -> Unit): File {
        val work = File(dir, "part.$ext")
        work.delete()
        val args = TrimPlan.args(root, input.absolutePath, work.absolutePath, range, TrimOutput.VIDEO, workExt, true, exact)
        val target = Target.entries.firstOrNull { it.ext == ext } ?: Target.MP4
        FfmpegRunner(context, engine).run(args, work, target, range.durationMs / 1000.0, onProgress)
        return work
    }

    /** Length of a written part (ms), or null when Android cannot read it. */
    private fun durationOf(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private inline fun update(token: Int, crossinline change: (SplitUiState) -> SplitUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun working(token: Int, index: Int, count: Int, progress: Float, step: String) {
        update(token) { if (it.phase is SplitPhase.Working) it.copy(phase = SplitPhase.Working(index, count, progress, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String?, saved: List<SavedFile>) {
        update(token) { it.copy(phase = SplitPhase.Failed(message, debug, saved.toList())) }
    }

    private companion object {
        const val FRAMES = 12
        const val FRAME_SIZE = 240
    }
}
