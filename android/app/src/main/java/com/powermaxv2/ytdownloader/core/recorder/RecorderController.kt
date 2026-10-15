package com.powermaxv2.ytdownloader.core.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/** Where the recorder stands. */
enum class RecPhase { IDLE, RECORDING, PAUSED }

/** What the Enregistreur vocal shows: the recording under way, and the memos already kept. */
data class RecorderState(
    val phase: RecPhase = RecPhase.IDLE,
    val elapsedMs: Long = 0,
    /** The last levels heard (0..1), oldest first: the live bars. */
    val live: List<Float> = emptyList(),
    val memos: List<MemoInfo> = emptyList(),
    val loaded: Boolean = false,
    /** The memo just saved: its card arrives with a flash. */
    val justSaved: String? = null,
)

/**
 * The Enregistreur vocal (ToolRegistry.VOICE_RECORDER): records the microphone in AAC (.m4a, mono, 44.1 kHz) into
 * Gulp's private folder `files/memos`, with a `.wave` side file holding the length and outline of each memo.
 * Nothing leaves the phone unless the user shares a memo or copies it to Music/Gulp.
 *
 * Android cuts the microphone of an app that is not on screen, so the tool stops and keeps the recording when it
 * leaves the screen ([stop]).
 */
class RecorderController(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val dir get() = File(app.filesDir, "memos")
    private val pending get() = File(dir, ".recording.$EXT")
    private val shareDir get() = File(app.cacheDir, "gulp-work/recorder")

    private var recorder: MediaRecorder? = null
    private var ticker: Job? = null

    /** Every level heard since the start, for the outline saved with the memo. */
    private val heard = ArrayList<Float>()

    /** Time recorded before the last resume, and when that resume happened (elapsedRealtime). */
    private var banked = 0L
    private var since = 0L

    fun refresh() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { load() }
            _state.update { it.copy(memos = list, loaded = true) }
        }
    }

    private fun load(): List<MemoInfo> {
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") && f.name.endsWith(".$EXT", ignoreCase = true) }.orEmpty()
        return Memos.sorted(
            files.map { f ->
                val meta = Memos.decodeMeta(runCatching { metaOf(f).readBytes() }.getOrNull())
                MemoInfo(f.name, meta?.first ?: 0L, f.length(), f.lastModified(), meta?.second.orEmpty())
            },
        )
    }

    private fun metaOf(file: File) = File(file.parentFile, ".${file.name}.wave")

    /** Starts a new memo; false (and nothing recording) when the microphone cannot be opened. */
    fun start(): Boolean {
        if (_state.value.phase != RecPhase.IDLE) return true
        dir.mkdirs()
        pending.delete()
        val rec = newRecorder()
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(44_100)
            rec.setAudioEncodingBitRate(96_000)
            rec.setOutputFile(pending.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (!ok) {
            runCatching { rec.release() }
            pending.delete()
            return false
        }
        recorder = rec
        heard.clear()
        banked = 0L
        since = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = RecPhase.RECORDING, elapsedMs = 0, live = emptyList(), justSaved = null) }
        tick()
        return true
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && _state.value.phase == RecPhase.RECORDING) {
                val level = Memos.level(runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0))
                heard.add(level)
                val elapsed = banked + (SystemClock.elapsedRealtime() - since)
                _state.update { s ->
                    s.copy(elapsedMs = elapsed, live = (s.live + level).takeLast(LIVE_BARS))
                }
                delay(TICK_MS)
            }
        }
    }

    fun pause() {
        if (_state.value.phase != RecPhase.RECORDING) return
        if (runCatching { recorder?.pause() }.isFailure) return
        banked += SystemClock.elapsedRealtime() - since
        ticker?.cancel()
        _state.update { it.copy(phase = RecPhase.PAUSED, elapsedMs = banked) }
    }

    fun resume() {
        if (_state.value.phase != RecPhase.PAUSED) return
        if (runCatching { recorder?.resume() }.isFailure) return
        since = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = RecPhase.RECORDING) }
        tick()
    }

    /**
     * Ends the recording and keeps it as "Mémo 14 oct. 19h20.m4a". Returns the saved memo's file name, or null
     * when there was nothing to keep (too short, or the recorder failed).
     */
    fun stop(): String? {
        val phase = _state.value.phase
        if (phase == RecPhase.IDLE) return null
        val elapsed = if (phase == RecPhase.RECORDING) banked + (SystemClock.elapsedRealtime() - since) else banked
        ticker?.cancel()
        val rec = recorder
        recorder = null
        val stopped = runCatching { rec?.stop() }.isSuccess
        runCatching { rec?.release() }
        if (!stopped || elapsed < MIN_MS || !pending.exists()) {
            pending.delete()
            _state.update { it.copy(phase = RecPhase.IDLE, elapsedMs = 0, live = emptyList()) }
            return null
        }
        val taken = dir.list().orEmpty().toList()
        val name = Memos.unique(Memos.fileName(Memos.defaultTitle(LocalDateTime.now())), taken)
        val target = File(dir, name)
        if (!pending.renameTo(target)) {
            pending.delete()
            _state.update { it.copy(phase = RecPhase.IDLE, elapsedMs = 0, live = emptyList()) }
            return null
        }
        runCatching { metaOf(target).writeBytes(Memos.encodeMeta(elapsed, heard)) }
        val memo = MemoInfo(name, elapsed, target.length(), target.lastModified(), Memos.downsample(heard, Memos.MAX_WAVE))
        heard.clear()
        _state.update { s ->
            s.copy(
                phase = RecPhase.IDLE,
                elapsedMs = 0,
                live = emptyList(),
                memos = Memos.sorted(listOf(memo) + s.memos.filterNot { it.fileName == name }),
                justSaved = name,
            )
        }
        return name
    }

    /** Throws the recording away. */
    fun discard() {
        if (_state.value.phase == RecPhase.IDLE) return
        ticker?.cancel()
        val rec = recorder
        recorder = null
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        pending.delete()
        heard.clear()
        _state.update { it.copy(phase = RecPhase.IDLE, elapsedMs = 0, live = emptyList()) }
    }

    /** The memo's file, for the player. */
    fun file(fileName: String): File = File(dir, fileName)

    /** Gives [fileName] the name [title]; returns the new file name, or null when the name is empty or taken. */
    fun rename(fileName: String, title: String): String? {
        val clean = Memos.cleanTitle(title) ?: return null
        val wanted = Memos.fileName(clean)
        if (wanted == fileName) return fileName
        val others = dir.list().orEmpty().filterNot { it.equals(fileName, ignoreCase = true) }
        if (others.any { it.equals(wanted, ignoreCase = true) }) return null
        val from = File(dir, fileName)
        val to = File(dir, wanted)
        // Only the case changes: some file systems need a detour through another name.
        val moved = if (fileName.equals(wanted, ignoreCase = true)) {
            val detour = File(dir, ".renaming.$EXT")
            from.renameTo(detour) && detour.renameTo(to)
        } else {
            from.renameTo(to)
        }
        if (!moved) return null
        metaOf(from).renameTo(metaOf(to))
        _state.update { s ->
            s.copy(
                memos = s.memos.map { if (it.fileName == fileName) it.copy(fileName = wanted) else it },
                justSaved = if (s.justSaved == fileName) wanted else s.justSaved,
            )
        }
        return wanted
    }

    fun delete(fileName: String) {
        File(dir, fileName).delete()
        metaOf(File(dir, fileName)).delete()
        _state.update { s -> s.copy(memos = s.memos.filterNot { it.fileName == fileName }) }
    }

    /** A copy of the memo under its name in the work folder (wiped at every launch), for another app to read. */
    suspend fun shareCopy(fileName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            shareDir.deleteRecursively()
            shareDir.mkdirs()
            File(dir, fileName).copyTo(File(shareDir, fileName), overwrite = true)
        }.getOrNull()
    }

    /** Copies the memo to Music/Gulp, where music players and file managers see it. */
    suspend fun export(fileName: String): SavedFile? = runCatching { MediaExporter.export(app, File(dir, fileName)) }.getOrNull()

    companion object {
        const val EXT = Memos.EXTENSION

        /** Levels read per second: 20. */
        const val TICK_MS = 50L

        /** Bars of the live outline. */
        const val LIVE_BARS = 64

        /** Shorter than this is a slip of the finger, not a memo. */
        const val MIN_MS = 600L

        val shared: RecorderController by lazy { RecorderController(App.instance) }
    }
}
