package com.powermaxv2.ytdownloader.core.voice

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
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
import kotlin.math.abs
import kotlin.math.max

/** The voice note picked: the file, its length when the phone can tell it before decoding. */
data class VoiceSource(val file: LocalFile, val durationMs: Long? = null, val video: Boolean = false)

/** Whether the French model is on the phone. */
enum class VoiceModelStatus { MISSING, READY }

sealed interface VoicePhase {
    data object Idle : VoicePhase

    data object Reading : VoicePhase

    data class Unsupported(val message: String) : VoicePhase

    data object Ready : VoicePhase

    /** Before listening: model download ([progress] 0..1, or -1 while unknown), unpacking, decoding the sound. */
    data class Preparing(val step: VoiceStep, val progress: Float = -1f, val detail: String? = null) : VoicePhase

    /** Listening: what is already written, the words being heard ([partial]) and how far in the sound. */
    data class Listening(
        val sentences: List<VoiceSentence>,
        val partial: String,
        val heardMs: Long,
        val totalMs: Long,
    ) : VoicePhase {
        val progress: Float get() = if (totalMs > 0) (heardMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    }

    data class Done(val sentences: List<VoiceSentence>, val durationMs: Long, val tookMs: Long, val saved: SavedFile? = null) : VoicePhase {
        val text: String get() = VoiceText.plainText(sentences)
        val empty: Boolean get() = sentences.all { it.words.isEmpty() }
    }

    data class Failed(val message: String, val debug: String? = null) : VoicePhase
}

enum class VoiceStep(val label: String) {
    DOWNLOAD("Téléchargement du modèle français"),
    UNPACK("Installation du modèle"),
    DECODE("Lecture du vocal"),
    LOAD("Réveil du modèle"),
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val source: VoiceSource? = null,
    val model: VoiceModelStatus = VoiceModelStatus.MISSING,
    /** Loudness of the sound in [BARS] bars 0..1, once it is decoded (drawn behind the words). */
    val wave: List<Float> = emptyList(),
) {
    val busy: Boolean get() = phase is VoicePhase.Preparing || phase is VoicePhase.Listening

    companion object {
        const val BARS = 48
    }
}

/**
 * The "Vocaux en texte" tool's state and work, held by the App (a transcription keeps going when the user leaves the
 * screen). A voice note is copied into a work dir, FFmpeg turns it into 16 kHz mono PCM, and Vosk writes it down on
 * the phone with its French model, word by word. The model (42 Mo) is downloaded once, checked against its
 * SHA-256, and kept in the app's private files; no sound or text ever leaves the phone.
 */
class VoiceController(private val context: Context, private val scope: CoroutineScope, private val engine: Engine) {
    private val models = VoiceModelStore(context)
    private val _state = MutableStateFlow(VoiceUiState(model = models.status()))
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.busy

    fun pick(uri: Uri): Boolean {
        if (busy) return false
        readJob?.cancel()
        generation.incrementAndGet()
        _state.update { VoiceUiState(phase = VoicePhase.Reading, model = models.status()) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                VoiceUiState(phase = VoicePhase.Unsupported("Android a refusé l'accès : choisis le vocal à nouveau."))
            } catch (e: Exception) {
                VoiceUiState(phase = VoicePhase.Unsupported(e.message ?: "Impossible de lire ce fichier."))
            }
            _state.update { current -> if (current.busy) current else next.copy(model = models.status()) }
        }
        return true
    }

    private fun describe(uri: Uri): VoiceUiState {
        val info = FileIntake.describe(context, uri)
        val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
        val kind = FileKind.detect(info.name, info.mime, head)
        if (kind != FileKind.AUDIO && kind != FileKind.VIDEO) {
            return VoiceUiState(
                phase = VoicePhase.Unsupported("« ${info.name} » n'est pas un son. Vocaux en texte prend les vocaux WhatsApp (.opus), les mémos (.m4a), les MP3, WAV ou OGG, et le son des vidéos."),
            )
        }
        val duration = runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } finally {
                    release()
                }
            }
        }.getOrNull()?.takeIf { it > 0 }
        return VoiceUiState(VoicePhase.Ready, VoiceSource(info, duration, video = kind == FileKind.VIDEO))
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        generation.incrementAndGet()
        _state.value = VoiceUiState(model = models.status())
    }

    fun cancel() {
        val job = workJob ?: return
        generation.incrementAndGet()
        job.cancel()
        _state.update { it.copy(phase = if (it.source != null) VoicePhase.Ready else VoicePhase.Idle) }
    }

    /** Frees the 68 Mo of the model (it is downloaded again the next time). */
    fun deleteModel() {
        if (busy) return
        scope.launch(Dispatchers.IO) {
            models.delete()
            _state.update { it.copy(model = models.status()) }
        }
    }

    fun start() {
        val current = _state.value
        val source = current.source ?: return
        if (busy || current.phase !is VoicePhase.Ready && current.phase !is VoicePhase.Failed && current.phase !is VoicePhase.Done) return
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = VoicePhase.Preparing(VoiceStep.DECODE), wave = emptyList()) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la transcription.")
                return@launch
            }
            try {
                val started = SystemClock.elapsedRealtime()
                val modelDir = models.ensure { step, progress, detail ->
                    update(token) { it.copy(phase = VoicePhase.Preparing(step, progress, detail), model = models.status()) }
                }
                update(token) { it.copy(model = VoiceModelStatus.READY) }
                coroutineContext.ensureActive()

                update(token) { it.copy(phase = VoicePhase.Preparing(VoiceStep.DECODE, 0f)) }
                val pcm = decode(token, source, dir)
                val totalMs = pcm.length() / BYTES_PER_MS
                if (totalMs < 200) throw ConversionError("Ce son est vide ou trop court pour être transcrit.")
                val wave = loudness(pcm, VoiceUiState.BARS)
                update(token) { it.copy(phase = VoicePhase.Preparing(VoiceStep.LOAD), wave = wave) }

                val sentences = VoskEngine.transcribe(modelDir, pcm) { done, partial, heardMs ->
                    update(token) { it.copy(phase = VoicePhase.Listening(done, partial, heardMs, totalMs)) }
                }
                val took = SystemClock.elapsedRealtime() - started
                update(token) { it.copy(phase = VoicePhase.Done(sentences, totalMs, took)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: VoiceModelError) {
                update(token) { it.copy(model = models.status()) }
                fail(token, e.message ?: "Le modèle n'a pas pu être installé.", e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Fichier trop volumineux.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Ce fichier ne peut pas être ouvert.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisis le vocal à nouveau.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: Throwable) {
                // Native errors of the recogniser (UnsatisfiedLinkError on an odd phone, a model that will not load).
                fail(token, "La reconnaissance vocale n'a pas pu démarrer sur ce téléphone.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    /** The voice note as raw 16 kHz mono 16-bit PCM ("voice.pcm" in [dir]), through the FFmpeg Gulp already ships. */
    private suspend fun decode(token: Int, source: VoiceSource, dir: File): File {
        val job = coroutineContext
        val copy = FileIntake.copyToWork(context, source.file.uri, FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
        val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(max(0, input.read(b))) } }
        FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
        val input = File(dir, "input.${FfmpegInput.workExt(head, source.file.name)}")
        if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer le vocal.")
        val output = File(dir, "voice.pcm")
        val args = VoiceArgs.decode(WorkDir.root(context).absolutePath, input.absolutePath, output.absolutePath)
        FfmpegRunner(context, engine).run(args, output, Target.WAV, source.durationMs?.let { it / 1000.0 }) { p ->
            update(token) { if (it.phase is VoicePhase.Preparing) it.copy(phase = VoicePhase.Preparing(VoiceStep.DECODE, p)) else it }
        }
        input.delete()
        return output
    }

    /** Peaks of [pcm] in [bars] equal slices, 0..1 (one quick read of the file). */
    private fun loudness(pcm: File, bars: Int): List<Float> {
        val samples = pcm.length() / 2
        if (samples <= 0) return emptyList()
        val per = max(1L, samples / bars)
        val peaks = FloatArray(bars)
        val buffer = ByteArray(64 * 1024)
        var index = 0L
        pcm.inputStream().buffered().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                var i = 0
                while (i + 1 < n) {
                    val v = abs(((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()) / 32768f
                    val bar = (index / per).toInt().coerceAtMost(bars - 1)
                    if (v > peaks[bar]) peaks[bar] = v
                    index++
                    i += 2
                }
            }
        }
        val top = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return peaks.map { (it / top).coerceIn(0.04f, 1f) }
    }

    /** Saves the text as a .txt in Download/Gulp. */
    fun saveText() {
        val current = _state.value
        val phase = current.phase as? VoicePhase.Done ?: return
        val source = current.source ?: return
        if (phase.saved != null) return
        val token = generation.get()
        scope.launch(Dispatchers.IO) {
            val dir = runCatching { WorkDir.create(context) }.getOrNull() ?: return@launch
            try {
                val file = File(dir, VoiceText.outputName(source.file.name))
                file.writeText(VoiceText.fileText(source.file.name, phase.durationMs, phase.sentences))
                val saved = MediaExporter.export(context, file)
                update(token) { s -> (s.phase as? VoicePhase.Done)?.let { s.copy(phase = it.copy(saved = saved)) } ?: s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(token, "Le texte n'a pas pu être enregistré.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (VoiceUiState) -> VoiceUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = VoicePhase.Failed(message, debug)) }
    }

    private companion object {
        /** 16 000 samples of 2 bytes per second. */
        const val BYTES_PER_MS = 32L
    }
}

/** FFmpeg command that turns a voice note into what Vosk listens to. Pure, unit tested. */
object VoiceArgs {
    fun decode(root: String, input: String, output: String): List<String> {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        return FfmpegArgs.prefix(input) + listOf(
            "-map", "0:a:0", "-vn", "-sn", "-dn",
            "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-f", "s16le",
            output,
        )
    }
}
