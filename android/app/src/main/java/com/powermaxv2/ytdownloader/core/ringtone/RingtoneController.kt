package com.powermaxv2.ytdownloader.core.ringtone

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Engine
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** The sound given to the tool (a video gives its sound). */
data class RingSource(val file: LocalFile, val durationMs: Long, val hasVideo: Boolean)

sealed interface RingPhase {
    data object Idle : RingPhase

    data object Reading : RingPhase

    data class Unsupported(val message: String) : RingPhase

    data object Ready : RingPhase

    /** [progress] 0..1 or negative when unknown. */
    data class Working(val progress: Float, val step: String) : RingPhase

    /** Saved; [applied] once it is the phone's default sound for [use]. */
    data class Done(val saved: SavedFile, val use: RingtoneUse, val durationMs: Long, val applied: Boolean = false) : RingPhase

    data class Failed(val message: String, val debug: String? = null) : RingPhase
}

data class RingUiState(
    val phase: RingPhase = RingPhase.Idle,
    val source: RingSource? = null,
    val range: RingRange = RingRange(0, 0),
    val use: RingtoneUse = RingtoneUse.RINGTONE,
    val fade: RingtoneFade = RingtoneFade.SOFT,
    val louder: Boolean = false,
    /** Loudness bars (0..1), [RingtonePlan.BARS] of them, null while decoding or when the phone cannot. */
    val wave: FloatArray? = null,
    /** Where the catchiest passage of the current length starts (the "refrain" mark), when there is a waveform. */
    val bestMs: Long? = null,
    /** Position of the preview being played (ms in the file), null when silent. */
    val playheadMs: Long? = null,
    /** The user asked to set the sound as default and was sent to allow it: done on return when allowed. */
    val applyPending: Boolean = false,
) {
    val playing: Boolean get() = playheadMs != null

    override fun equals(other: Any?): Boolean = other is RingUiState && phase == other.phase && source == other.source &&
        range == other.range && use == other.use && fade == other.fade && louder == other.louder && wave === other.wave &&
        bestMs == other.bestMs && playheadMs == other.playheadMs && applyPending == other.applyPending

    override fun hashCode(): Int = listOf(phase, source, range, use, fade, louder, bestMs, playheadMs, applyPending).hashCode()
}

/**
 * The Sonneries tool's state and work, kept while the app lives (a cut keeps going off screen). The sound is read
 * in place (duration, waveform, preview with MediaPlayer); FFmpeg cuts it to an MP3 with fades in a work dir, then
 * [RingtoneSaver] files it as a ringtone, notification or alarm and can make it the default.
 */
class RingtoneController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: () -> Engine,
) {
    private val _state = MutableStateFlow(RingUiState())
    val state: StateFlow<RingUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private var playJob: Job? = null
    private var player: MediaPlayer? = null
    private val generation = AtomicInteger(0)

    /** The handles were not moved yet: the passage follows the waveform's best guess when it arrives. */
    private var untouched = true

    val busy: Boolean get() = _state.value.phase is RingPhase.Working

    fun pick(uri: Uri): Boolean {
        if (busy) return false
        stopPreview()
        readJob?.cancel()
        untouched = true
        _state.update { RingUiState(phase = RingPhase.Reading, use = it.use, fade = it.fade, louder = it.louder) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                RingUiState(phase = RingPhase.Unsupported("Android a refusé l'accès : choisissez le fichier à nouveau."))
            } catch (e: Exception) {
                RingUiState(phase = RingPhase.Unsupported(e.message ?: "Impossible de lire ce fichier."))
            }
            _state.update { current -> if (current.phase is RingPhase.Working) current else next }
            val source = next.source ?: return@launch
            val wave = runCatching { WaveformDecoder.decode(context, uri, RingtonePlan.BARS)?.bars }.getOrNull() ?: return@launch
            coroutineContext.ensureActive()
            _state.update { s ->
                if (s.source?.file?.uri != uri) return@update s
                val best = RingtonePlan.bestStart(wave, source.durationMs, s.range.durationMs)
                val range = if (untouched && best != null) RingtonePlan.slide(s.range, best, source.durationMs, s.use) else s.range
                s.copy(wave = wave, bestMs = best, range = range)
            }
        }
        return true
    }

    private fun describe(uri: Uri): RingUiState {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.VIDEO && kind != FileKind.AUDIO) {
            return RingUiState(phase = RingPhase.Unsupported("Choisissez un morceau ou un son (une vidéo marche aussi). « ${info.name} » n'en est pas un."))
        }
        FfmpegInput.refusal(head)?.let { return RingUiState(phase = RingPhase.Unsupported(it)) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return RingUiState(phase = RingPhase.Unsupported("Gulp ne trouve pas la durée de ce son : il est peut-être abîmé."))
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null &&
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            ) {
                return RingUiState(phase = RingPhase.Unsupported("Cette vidéo n'a pas de son."))
            }
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val previous = _state.value
            return RingUiState(
                phase = RingPhase.Ready,
                source = RingSource(info, duration, hasVideo),
                range = RingtonePlan.initial(duration, previous.use),
                use = previous.use,
                fade = previous.fade,
                louder = previous.louder,
            )
        } catch (e: RuntimeException) {
            return RingUiState(phase = RingPhase.Unsupported("Gulp ne sait pas lire ce son sur ce téléphone."))
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun setStart(ms: Long) = edit { s -> s.copy(range = RingtonePlan.moveStart(s.range, ms, s.durationMs, s.use)) }

    fun setEnd(ms: Long) = edit { s -> s.copy(range = RingtonePlan.moveEnd(s.range, ms, s.durationMs, s.use)) }

    /** The whole passage dragged so it starts at [ms]. */
    fun slideTo(ms: Long) = edit { s -> s.copy(range = RingtonePlan.slide(s.range, ms, s.durationMs, s.use)) }

    /** Jumps to the refrain (the loudest passage of the current length). */
    fun toBest() = edit { s ->
        val wave = s.wave ?: return@edit s
        val best = RingtonePlan.bestStart(wave, s.durationMs, s.range.durationMs) ?: return@edit s
        s.copy(range = RingtonePlan.slide(s.range, best, s.durationMs, s.use), bestMs = best)
    }

    fun setUse(use: RingtoneUse) = edit { s ->
        val range = RingtonePlan.forUse(s.range, s.durationMs, use)
        val best = s.wave?.let { RingtonePlan.bestStart(it, s.durationMs, range.durationMs) }
        s.copy(use = use, range = range, bestMs = best)
    }

    fun setFade(fade: RingtoneFade) = edit { it.copy(fade = fade) }

    fun setLouder(louder: Boolean) = edit { it.copy(louder = louder) }

    private val RingUiState.durationMs: Long get() = source?.durationMs ?: 0L

    private inline fun edit(crossinline change: (RingUiState) -> RingUiState) {
        _state.update {
            if (it.phase == RingPhase.Ready && it.source != null) {
                untouched = false
                change(it)
            } else {
                it
            }
        }
    }

    // MARK: - Preview

    /** Plays the passage (or the saved sound, on the result), or stops it when it plays. */
    fun togglePreview() {
        if (_state.value.playing) {
            stopPreview()
            return
        }
        val s = _state.value
        val done = s.phase as? RingPhase.Done
        val uri = done?.saved?.uri ?: s.source?.file?.uri ?: return
        val from = if (done != null) 0L else s.range.startMs
        val to = if (done != null) done.durationMs else s.range.endMs
        playJob = scope.launch(Dispatchers.Main) {
            val mp = MediaPlayer()
            player = mp
            try {
                withContext(Dispatchers.IO) {
                    mp.setDataSource(context, uri)
                    mp.prepare()
                }
                mp.seekTo(from.toInt())
                mp.start()
                while (isActive) {
                    val position = if (done != null) mp.currentPosition.toLong() else mp.currentPosition.toLong().coerceAtLeast(from)
                    _state.update { it.copy(playheadMs = position) }
                    if (position >= to || !mp.isPlaying) break
                    delay(33)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A sound the phone cannot play: no preview, the cut still works.
            } finally {
                runCatching { mp.stop() }
                runCatching { mp.release() }
                if (player === mp) player = null
                _state.update { it.copy(playheadMs = null) }
            }
        }
    }

    fun stopPreview() {
        playJob?.cancel()
        playJob = null
        _state.update { if (it.playheadMs != null) it.copy(playheadMs = null) else it }
    }

    // MARK: - Default sound

    /**
     * Makes the saved sound the phone's default. False when Gulp must first be allowed to change the settings: the
     * screen then opens that page, and [resumed] finishes the job on the way back.
     */
    fun applyDefault(): Boolean {
        val done = _state.value.phase as? RingPhase.Done ?: return true
        if (!RingtoneSaver.canSetDefault(context)) {
            _state.update { it.copy(applyPending = true) }
            return false
        }
        val ok = RingtoneSaver.setDefault(context, done.saved.uri, done.use)
        _state.update { s -> (s.phase as? RingPhase.Done)?.let { s.copy(phase = it.copy(applied = ok), applyPending = false) } ?: s }
        return true
    }

    /** Back from the settings page: sets the sound if the user allowed it. */
    fun resumed() {
        if (!_state.value.applyPending) return
        if (RingtoneSaver.canSetDefault(context)) applyDefault() else _state.update { it.copy(applyPending = false) }
    }

    // MARK: - Cut

    fun reset() {
        if (busy) return
        stopPreview()
        readJob?.cancel()
        _state.update { RingUiState(use = it.use, fade = it.fade, louder = it.louder) }
    }

    fun backToEdit() {
        stopPreview()
        _state.update { if (it.source != null && !busy) it.copy(phase = RingPhase.Ready, applyPending = false) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is RingPhase.Working) it.copy(phase = RingPhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.phase !is RingPhase.Ready && current.phase !is RingPhase.Failed) return
        stopPreview()
        val range = RingtonePlan.clamp(current.range, source.durationMs, current.use)
        if (range.durationMs <= 0) return
        val token = generation.incrementAndGet()
        val use = current.use
        val fade = current.fade
        val louder = current.louder
        _state.update { it.copy(phase = RingPhase.Working(-1f, "Copie du morceau…")) }
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
                val input = File(dir, "input.${FfmpegInput.workExt(head, source.file.name)}")
                if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer le fichier.")
                val work = File(dir, "output.mp3")
                val title = RingtonePlan.title(source.file.name)
                val args = RingtonePlan.args(WorkDir.root(context).absolutePath, input.absolutePath, work.absolutePath, range, fade, louder, title)
                val step = step(use, louder)
                progress(token, 0f, step)
                FfmpegRunner(context, engine()).run(args, work, Target.MP3, range.durationMs / 1000.0) { progress(token, it, step) }
                input.delete()
                coroutineContext.ensureActive()
                progress(token, -1f, "Rangement dans les sons du téléphone…")
                val named = File(File(dir, "out").apply { mkdirs() }, RingtonePlan.outputName(source.file.name, use))
                if (!work.renameTo(named)) throw ConversionError("Impossible d'enregistrer le son.")
                val saved = RingtoneSaver.save(context, named, use, title)
                update(token) { it.copy(phase = RingPhase.Done(saved, use, range.durationMs)) }
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

    private fun step(use: RingtoneUse, louder: Boolean): String = when {
        louder -> "Coupe et mise à niveau du son…"
        use == RingtoneUse.NOTIFICATION -> "Création de la notification…"
        use == RingtoneUse.ALARM -> "Création du réveil…"
        else -> "Création de la sonnerie…"
    }

    private inline fun update(token: Int, crossinline change: (RingUiState) -> RingUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is RingPhase.Working) it.copy(phase = RingPhase.Working(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = RingPhase.Failed(message, debug)) }
    }

    companion object {
        /** The one controller of the app, made on first use (App.kt untouched, like the Redimensionneur's). */
        val shared: RingtoneController by lazy { RingtoneController(App.instance, App.instance.scope) { App.instance.engine } }
    }
}
