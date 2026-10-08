package com.powermaxv2.ytdownloader.core.compress

import android.content.Context
import android.net.Uri
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegErrors
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** A file given to the Compresseur, with what the probe read from it. */
data class CompressItem(val file: LocalFile, val media: Media, val info: MediaInfo = MediaInfo())

/** What came out for one file: [saved] null when Gulp kept the original (it could not do lighter). */
data class CompressResult(val name: String, val before: Long, val after: Long, val saved: SavedFile?)

sealed interface CompressPhase {
    data object Idle : CompressPhase

    data object Reading : CompressPhase

    data object Ready : CompressPhase

    data class Unsupported(val message: String) : CompressPhase

    /** [progress] 0..1 or negative when unknown; [written] = bytes of the result so far, when known. */
    data class Working(val progress: Float, val step: String, val written: Long? = null) : CompressPhase

    data class Done(val results: List<CompressResult>) : CompressPhase {
        val before: Long get() = results.sumOf { it.before }
        val after: Long get() = results.sumOf { it.after }
        val savedFiles: List<SavedFile> get() = results.mapNotNull { it.saved }
    }

    data class Failed(val message: String, val debug: String? = null) : CompressPhase
}

data class CompressorUiState(
    val phase: CompressPhase = CompressPhase.Idle,
    val items: List<CompressItem> = emptyList(),
    val mode: Mode = Mode.EACH,
    val settings: CompressSettings = CompressSettings(),
) {
    val multiple: Boolean get() = items.size > 1

    /** Total size when every provider told it. */
    val totalSize: Long? get() = items.map { it.file.size }.takeIf { s -> s.all { it != null } }?.sumOf { it ?: 0L }

    /** Files that can only go into a ZIP. */
    val zipOnly: List<CompressItem> get() = items.filter { it.media == Media.OTHER }

    /** A size goal applies to one photo / video / sound / PDF. */
    val goalAllowed: Boolean get() = items.size == 1 && items.first().media != Media.OTHER && mode == Mode.EACH

    val hasVideo: Boolean get() = items.any { it.media == Media.VIDEO }

    /** Expected total size of the result, or null when Gulp cannot guess. */
    val estimate: Long?
        get() {
            if (items.isEmpty()) return null
            val effective = if (goalAllowed) settings else settings.copy(goal = null)
            return if (mode == Mode.ZIP) {
                CompressPlan.estimateZip(items.map { CompressPlan.ZipEstimateItem(it.media, it.file.name, it.file.size, it.info) }, effective)
            } else {
                items.map { CompressPlan.estimate(it.media, it.file.size, it.info, it.file.name, effective) ?: return null }.sum()
            }
        }

    /** Why the button is off, or null when Gulp can go. */
    val blocker: String?
        get() {
            val single = items.singleOrNull()
            val goal = settings.goal
            if (goalAllowed && single != null && goal != null && !CompressPlan.goalReachable(single.media, single.info.durationSec, goal)) {
                val max = CompressPlan.maxSeconds(single.media, goal)
                return "Trop long pour ${goal.label} : ${max / 60} min ${max % 60} s au plus dans ${goal.bytes / 1_000_000} Mo."
            }
            if (mode == Mode.EACH && zipOnly.isNotEmpty()) {
                return "« ${zipOnly.first().file.name} » ne se compresse pas seul : mettez les fichiers dans un ZIP."
            }
            return null
        }
}

/**
 * The Compresseur's state and work, held by the App like the Convertisseur's: a compression keeps running when the
 * user leaves the tool. One job at a time.
 */
class CompressorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(CompressorUiState())
    val state: StateFlow<CompressorUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is CompressPhase.Working

    /** Takes the chosen files (replacing the current ones, or added to them with [add]). False while a job runs. */
    fun pick(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        readJob?.cancel()
        val keep = if (add) _state.value.items else emptyList()
        _state.update { it.copy(phase = CompressPhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(keep, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CompressorUiState(phase = CompressPhase.Unsupported(e.message ?: "Impossible de lire ce fichier."))
            }
            _state.update { current ->
                if (current.phase is CompressPhase.Working) current else next.copy(settings = current.settings)
            }
        }
        return true
    }

    private fun describe(keep: List<CompressItem>, uris: List<Uri>): CompressorUiState {
        val known = keep.map { it.file.uri }.toSet()
        val fresh = uris.filter { it !in known }
        if (keep.size + fresh.size > CompressPlan.MAX_FILES) {
            return CompressorUiState(
                phase = CompressPhase.Unsupported("${CompressPlan.MAX_FILES} fichiers au plus à la fois."),
                items = keep,
            )
        }
        val items = keep + fresh.map { uri ->
            val info = FileIntake.describe(context, uri)
            val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
            val media = Media.of(FileKind.detect(info.name, info.mime, head), info.name)
            CompressItem(info, media, MediaProbe.read(context, uri, media))
        }
        val total = items.sumOf { it.file.size ?: 0L }
        if (total > CompressPlan.MAX_TOTAL_BYTES) {
            return CompressorUiState(phase = CompressPhase.Unsupported("2 Go au plus en tout : choisissez moins de fichiers."), items = items)
        }
        // Several files, or a file that only a ZIP can hold: the ZIP is the natural choice.
        val mode = if (items.size > 1 || items.any { it.media == Media.OTHER }) Mode.ZIP else Mode.EACH
        return CompressorUiState(CompressPhase.Ready, items, mode)
    }

    fun setSettings(settings: CompressSettings) {
        _state.update { if (it.phase is CompressPhase.Working) it else it.copy(settings = settings) }
    }

    fun setMode(mode: Mode) {
        _state.update { if (it.phase == CompressPhase.Ready) it.copy(mode = mode) else it }
    }

    fun remove(item: CompressItem) {
        _state.update {
            if (it.phase != CompressPhase.Ready) return@update it
            val left = it.items - item
            if (left.isEmpty()) CompressorUiState(settings = it.settings) else it.copy(items = left, mode = if (left.size == 1 && left.first().media != Media.OTHER) Mode.EACH else it.mode)
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { CompressorUiState(settings = it.settings) }
    }

    /** From a result or an error, back to the same files with their settings. */
    fun backToFiles() {
        _state.update { if (it.items.isNotEmpty() && !busy) it.copy(phase = CompressPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is CompressPhase.Working) it.copy(phase = CompressPhase.Ready) else it }
    }

    fun start() {
        val current = _state.value
        if (current.items.isEmpty() || busy || current.phase is CompressPhase.Reading || current.blocker != null) return
        val token = generation.incrementAndGet()
        val settings = if (current.goalAllowed) current.settings else current.settings.copy(goal = null)
        _state.update { it.copy(phase = CompressPhase.Working(-1f, "Préparation…")) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la compression.")
                return@launch
            }
            try {
                val worker = Compressor(context, engine, dir) { value, step, written -> progress(token, value, step, written) }
                val results = if (current.mode == Mode.ZIP) {
                    val zip = worker.zip(current.items, settings)
                    coroutineContext.ensureActive()
                    progress(token, -1f, "Enregistrement…", zip.length())
                    listOf(CompressResult(zip.name, current.items.sumOf { it.file.size ?: 0L }, zip.length(), MediaExporter.export(context, zip)))
                } else {
                    current.items.mapIndexed { index, item ->
                        coroutineContext.ensureActive()
                        val prefix = if (current.items.size > 1) "${index + 1}/${current.items.size} · " else ""
                        val out = worker.shrink(item, settings, prefix, index, current.items.size)
                        val before = out.before
                        val file = out.file
                        if (file == null) {
                            CompressResult(item.file.name, before, before, null)
                        } else {
                            progress(token, -1f, "${prefix}Enregistrement…", file.length())
                            val saved = MediaExporter.export(context, file)
                            CompressResult(saved.name, before, saved.size, saved).also { file.delete() }
                        }
                    }
                }
                update(token) { it.copy(phase = CompressPhase.Done(results)) }
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
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour ce fichier.", e.toString())
            } catch (e: Throwable) {
                // Nothing a file does may take the app (and the running downloads) down with it.
                fail(token, FfmpegErrors.GENERIC, e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (CompressorUiState) -> CompressorUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String, written: Long?) {
        update(token) { if (it.phase is CompressPhase.Working) it.copy(phase = CompressPhase.Working(value, step, written)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = CompressPhase.Failed(message, debug)) }
    }
}
