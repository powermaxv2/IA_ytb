package com.powermaxv2.ytdownloader.core.readaloud

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.convert.FfmpegRunner
import com.powermaxv2.ytdownloader.core.convert.Target
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** Speed of the voice. */
enum class SpeechRate(val value: Float, val label: String) {
    SLOW(0.75f, "×0,75"),
    NORMAL(1f, "×1"),
    FAST(1.25f, "×1,25"),
    FASTER(1.5f, "×1,5"),
    FASTEST(2f, "×2"),
}

/** Height of the voice. */
enum class SpeechPitch(val value: Float, val label: String) {
    LOW(0.8f, "Grave"),
    NORMAL(1f, "Normale"),
    HIGH(1.25f, "Aiguë"),
}

/** File the text is saved as. */
enum class AudioOut(val ext: String, val label: String, val detail: String) {
    MP3("mp3", "MP3", "Lu partout"),
    M4A("m4a", "M4A", "Plus léger"),
    WAV("wav", "WAV", "Sans compression"),
}

/** The phone's speech engine: starting, ready, or absent / broken. */
sealed interface SpeechEngine {
    data object Starting : SpeechEngine

    data object Ready : SpeechEngine

    data class Missing(val message: String) : SpeechEngine
}

/** Saving the text as an audio file. */
sealed interface SpeechExport {
    data object None : SpeechExport

    /** [done] of [total] blocks spoken into files, then [encoding] (FFmpeg) at [fraction]. */
    data class Working(val done: Int, val total: Int, val encoding: Boolean = false, val fraction: Float = 0f) : SpeechExport {
        val progress: Float get() = if (encoding) 0.85f + 0.15f * fraction else 0.85f * done / total.coerceAtLeast(1)
    }

    data class Done(val saved: SavedFile, val seconds: Int) : SpeechExport

    data class Failed(val message: String, val debug: String? = null) : SpeechExport
}

data class ReadAloudUiState(
    val engine: SpeechEngine = SpeechEngine.Starting,
    val text: String = "",
    /** Pieces read one after the other (from [SpeechText.chunks] of [text]). */
    val chunks: List<SpeechChunk> = SpeechText.chunks(text),
    /** Name of the file the text comes from; null for a typed or pasted text. */
    val sourceName: String? = null,
    val sourceKind: SpeechSource? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    val playing: Boolean = false,
    /** Piece being read (or where reading starts again). */
    val current: Int = 0,
    /** Word being said, as offsets in the current piece; null between words or when the voice does not tell. */
    val word: IntRange? = null,
    val rate: SpeechRate = SpeechRate.NORMAL,
    val pitch: SpeechPitch = SpeechPitch.NORMAL,
    val languages: List<LanguageChoice> = emptyList(),
    val language: String? = null,
    val voices: List<VoiceChoice> = emptyList(),
    val voice: String? = null,
    val format: AudioOut = AudioOut.MP3,
    val export: SpeechExport = SpeechExport.None,
) {
    val hasText: Boolean get() = chunks.isNotEmpty()
    val busy: Boolean get() = export is SpeechExport.Working

    /** Share of the text already read, 0..1. */
    val progress: Float get() {
        if (chunks.isEmpty()) return 0f
        val total = chunks.sumOf { it.length }.coerceAtLeast(1)
        val inside = word?.let { it.first.coerceAtMost(chunks.getOrNull(current)?.length ?: 0) } ?: 0
        return ((SpeechText.readBefore(chunks, current) + inside).toFloat() / total).coerceIn(0f, 1f)
    }

    val totalSeconds: Int get() = SpeechText.estimateSeconds(chunks.sumOf { it.length }, rate.value)
    val leftSeconds: Int get() = SpeechText.estimateSeconds(chunks.drop(current).sumOf { it.length }, rate.value)

    fun chunkText(index: Int): String = chunks.getOrNull(index)?.let { text.substring(it.start, it.end) }.orEmpty()
}

/**
 * The Lecture à voix haute's state and work: a text (typed, pasted, or read from a file) spoken by the phone's own
 * voice (android.speech.tts), piece by piece so the screen follows along, and saved as MP3 / M4A / WAV in
 * Musique › Gulp. Nothing leaves the phone, except with a voice the engine says needs the Internet.
 * One controller for the app ([shared]).
 */
class ReadAloudController(private val context: Context, private val scope: CoroutineScope, private val engine: Engine) {
    private val _state = MutableStateFlow(ReadAloudUiState())
    val state: StateFlow<ReadAloudUiState> = _state.asStateFlow()

    @Volatile private var tts: TextToSpeech? = null
    private var allVoices: List<Voice> = emptyList()

    /** Bumped at every play / pause: callbacks of an older run are ignored. */
    private val playRun = AtomicInteger(0)

    /** Highest piece already handed to the voice in the current run. */
    @Volatile private var queued = -1

    private val generation = AtomicInteger(0)
    private var loadJob: Job? = null
    private var exportJob: Job? = null

    /** Files being written by the voice, by utterance id. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** Starts the phone's voice (once); called when the screen opens. */
    fun start() {
        if (tts != null) return
        _state.update { it.copy(engine = SpeechEngine.Starting) }
        val created = runCatching {
            TextToSpeech(context.applicationContext) { status -> scope.launch(Dispatchers.Main) { onInit(status) } }
        }.getOrElse {
            _state.update { it.copy(engine = SpeechEngine.Missing(NO_ENGINE)) }
            return
        }
        tts = created
        created.setOnUtteranceProgressListener(listener)
    }

    private fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            runCatching { t.shutdown() }
            tts = null
            _state.update { it.copy(engine = SpeechEngine.Missing(NO_ENGINE)) }
            return
        }
        allVoices = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
        val infos = allVoices.map { VoiceInfo(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired, it.quality) }
        val device = Locale.getDefault()
        val languages = VoiceList.languages(infos, device.language)
        val language = _state.value.language?.takeIf { l -> languages.any { it.code == l } }
            ?: VoiceList.defaultLanguage(languages, device.language)
        val voices = language?.let { VoiceList.voices(infos, it, device.country) }.orEmpty()
        val engineDefault = runCatching { t.defaultVoice?.name }.getOrNull()
        val voice = _state.value.voice?.takeIf { v -> voices.any { it.name == v } }
            ?: voices.firstOrNull { it.name == engineDefault }?.name
            ?: voices.firstOrNull()?.name
        _state.update { it.copy(engine = SpeechEngine.Ready, languages = languages, language = language, voices = voices, voice = voice) }
        applySettings()
    }

    /** Voice, speed and pitch of the state handed to the engine. */
    private fun applySettings() {
        val t = tts ?: return
        val s = _state.value
        runCatching {
            val v = allVoices.firstOrNull { it.name == s.voice }
            if (v != null) {
                t.voice = v
            } else {
                // An engine that lists no voices: the language alone.
                t.language = s.language?.let { Locale(it) } ?: Locale.FRENCH
            }
            t.setSpeechRate(s.rate.value)
            t.setPitch(s.pitch.value)
        }
    }

    // MARK: - Text

    /** A typed or pasted text (replaces a file's). */
    fun setText(text: String) {
        if (_state.value.busy) return
        val clean = text.take(SpeechText.MAX_TYPED)
        if (_state.value.playing) pause()
        _state.update {
            it.copy(
                text = clean,
                chunks = SpeechText.chunks(clean),
                sourceName = null,
                sourceKind = null,
                loadError = null,
                current = 0,
                word = null,
                export = SpeechExport.None,
            )
        }
    }

    /** Reads the words of [uri] (text, Markdown, subtitles, web page, Word, OpenDocument, EPUB, PDF). */
    fun open(uri: Uri) {
        if (_state.value.busy) return
        pause()
        loadJob?.cancel()
        val token = generation.incrementAndGet()
        _state.update { it.copy(loading = true, loadError = null, export = SpeechExport.None) }
        loadJob = scope.launch(Dispatchers.IO) {
            var dir: File? = null
            try {
                val info = FileIntake.describe(context, uri)
                val kind = SpeechText.kindOf(info.name, info.mime)
                    ?: throw ConversionError("« ${info.name} » ne contient pas de texte à lire. Choisis un texte, un PDF, un Word, un EPUB, une page web ou des sous-titres.")
                val work = WorkDir.create(context).also { dir = it }
                val job = coroutineContext
                val file = FileIntake.copyToWork(context, uri, MAX_FILE_BYTES, work) { job.ensureActive() }
                val raw = when (kind) {
                    SpeechSource.TEXT -> SpeechText.decode(file.readBytes())
                    SpeechSource.MARKDOWN -> SpeechText.fromMarkdown(SpeechText.decode(file.readBytes()))
                    SpeechSource.SUBTITLES -> SpeechText.fromSubtitles(SpeechText.decode(file.readBytes()))
                    SpeechSource.HTML -> SpeechText.fromHtml(SpeechText.decode(file.readBytes()))
                    SpeechSource.DOCX -> DocumentText.docx(file)
                    SpeechSource.ODT -> DocumentText.odt(file)
                    SpeechSource.EPUB -> DocumentText.epub(file)
                    SpeechSource.PDF -> pdfText(file)
                }
                coroutineContext.ensureActive()
                val text = SpeechText.normalize(raw)
                val chunks = SpeechText.chunks(text)
                if (chunks.isEmpty()) {
                    throw ConversionError(
                        if (kind == SpeechSource.PDF) "Ce PDF n'a pas de texte : c'est sans doute un scan (des photos de pages)." else "Aucun texte à lire dans ce fichier.",
                    )
                }
                update(token) {
                    it.copy(
                        text = text,
                        chunks = chunks,
                        sourceName = info.name,
                        sourceKind = kind,
                        loading = false,
                        current = 0,
                        word = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = when (e) {
                    is InvalidPasswordException -> "Ce PDF est protégé par un mot de passe : Gulp ne peut pas le lire."
                    is SecurityException -> "Android a refusé l'accès : choisis le fichier à nouveau."
                    is FileTooLarge, is UnsupportedSource, is ConversionError, is DocumentTextError -> e.message ?: "Fichier illisible."
                    is java.util.zip.ZipException -> "Ce fichier est abîmé : il ne s'ouvre pas."
                    is OutOfMemoryError -> "Ce fichier est trop gros pour être lu."
                    else -> "Impossible de lire ce fichier."
                }
                update(token) { it.copy(loading = false, loadError = message) }
            } finally {
                dir?.let { withContext(NonCancellable) { WorkDir.delete(context, it) } }
            }
        }
    }

    private fun pdfText(file: File): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = minOf(doc.numberOfPages, MAX_PDF_PAGES)
            }
            return stripper.getText(doc)
        }
    }

    /** Forgets the text (back to the empty field). */
    fun clear() {
        if (_state.value.busy) return
        pause()
        generation.incrementAndGet()
        loadJob?.cancel()
        _state.update {
            it.copy(text = "", chunks = emptyList(), sourceName = null, sourceKind = null, loading = false, loadError = null, current = 0, word = null, export = SpeechExport.None)
        }
    }

    // MARK: - Reading

    /** Reads from piece [from] (the current one by default) to the end. */
    fun play(from: Int = _state.value.current) {
        val t = tts ?: return
        val s = _state.value
        if (s.engine != SpeechEngine.Ready || s.busy || s.chunks.isEmpty()) return
        val start = if (from >= s.chunks.size) 0 else from.coerceAtLeast(0)
        val run = playRun.incrementAndGet()
        applySettings()
        runCatching { t.stop() }
        _state.update { it.copy(playing = true, current = start, word = null, export = if (it.export is SpeechExport.Done) it.export else SpeechExport.None) }
        queued = start - 1
        enqueue(run, start, TextToSpeech.QUEUE_FLUSH)
        for (i in start + 1..start + AHEAD) enqueue(run, i, TextToSpeech.QUEUE_ADD)
    }

    private fun enqueue(run: Int, index: Int, mode: Int) {
        val t = tts ?: return
        val s = _state.value
        if (index !in s.chunks.indices || index <= queued) return
        queued = index
        val result = t.speak(s.chunkText(index), mode, null, "p-$run-$index")
        if (result != TextToSpeech.SUCCESS && run == playRun.get()) {
            _state.update { it.copy(playing = false, word = null) }
        }
    }

    fun pause() {
        playRun.incrementAndGet()
        if (_state.value.playing) runCatching { tts?.stop() }
        _state.update { it.copy(playing = false, word = null) }
    }

    fun toggle() {
        if (_state.value.playing) pause() else play()
    }

    /** Goes to piece [index]; reading goes on from there when it was on. */
    fun seek(index: Int) {
        val s = _state.value
        if (s.chunks.isEmpty()) return
        val target = index.coerceIn(0, s.chunks.lastIndex)
        if (s.playing) play(target) else _state.update { it.copy(current = target, word = null) }
    }

    fun previous() = seek(_state.value.current - 1)

    fun next() = seek(_state.value.current + 1)

    fun setRate(rate: SpeechRate) = changeVoice { it.copy(rate = rate) }

    fun setPitch(pitch: SpeechPitch) = changeVoice { it.copy(pitch = pitch) }

    fun setVoice(name: String) = changeVoice { s -> if (s.voices.any { it.name == name }) s.copy(voice = name) else s }

    fun setLanguage(code: String) = changeVoice { s ->
        val infos = allVoices.map { VoiceInfo(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired, it.quality) }
        val voices = VoiceList.voices(infos, code, Locale.getDefault().country)
        s.copy(language = code, voices = voices, voice = voices.firstOrNull()?.name)
    }

    fun setFormat(format: AudioOut) {
        _state.update { if (it.busy) it else it.copy(format = format, export = if (it.export is SpeechExport.Done) SpeechExport.None else it.export) }
    }

    /** A setting of the voice changed: a reading on goes on with it from the current piece. */
    private fun changeVoice(change: (ReadAloudUiState) -> ReadAloudUiState) {
        if (_state.value.busy) return
        _state.update(change)
        applySettings()
        if (_state.value.playing) play(_state.value.current)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (kind, run, index) = parse(utteranceId) ?: return
            if (kind != 'p' || run != playRun.get()) return
            _state.update { if (it.playing) it.copy(current = index, word = null) else it }
            enqueue(run, index + AHEAD, TextToSpeech.QUEUE_ADD)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (kind, run, index) = parse(utteranceId) ?: return
            if (kind != 'p' || run != playRun.get()) return
            _state.update { if (it.playing && it.current == index) it.copy(word = start until end) else it }
        }

        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            pending.remove(id)?.complete(true)
            val (kind, run, index) = parse(id) ?: return
            if (kind != 'p' || run != playRun.get()) return
            if (index >= _state.value.chunks.lastIndex) {
                // The end: next time starts over.
                playRun.incrementAndGet()
                _state.update { it.copy(playing = false, current = 0, word = null) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = failed(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = failed(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let { pending.remove(it)?.complete(false) }
        }

        private fun failed(utteranceId: String?) {
            val id = utteranceId ?: return
            pending.remove(id)?.complete(false)
            val (kind, run, _) = parse(id) ?: return
            if (kind == 'p' && run == playRun.get()) {
                playRun.incrementAndGet()
                _state.update { it.copy(playing = false, word = null) }
            }
        }
    }

    /** "p-3-12" → ('p', 3, 12). */
    private fun parse(id: String?): Triple<Char, Int, Int>? {
        val parts = id?.split('-') ?: return null
        if (parts.size != 3 || parts[0].length != 1) return null
        val run = parts[1].toIntOrNull() ?: return null
        val index = parts[2].toIntOrNull() ?: return null
        return Triple(parts[0][0], run, index)
    }

    // MARK: - Saving as audio

    /** Speaks the whole text into an audio file saved in Musique › Gulp. */
    fun export() {
        val s = _state.value
        val t = tts ?: return
        if (s.engine != SpeechEngine.Ready || s.busy || s.chunks.isEmpty() || s.loading) return
        pause()
        applySettings()
        val token = generation.incrementAndGet()
        val blocks = SpeechText.blocks(s.chunks, minOf(SpeechText.BLOCK_MAX, TextToSpeech.getMaxSpeechInputLength() - 100))
        val text = s.text
        val format = s.format
        val name = SpeechText.outputName(s.sourceName, format.ext)
        _state.update { it.copy(export = SpeechExport.Working(0, blocks.size)) }
        exportJob = scope.launch(Dispatchers.IO) {
            val dir = WorkDir.create(context)
            try {
                val parts = ArrayList<File>(blocks.size)
                blocks.forEachIndexed { i, block ->
                    coroutineContext.ensureActive()
                    val part = File(dir, "part-%05d.wav".format(Locale.ROOT, i))
                    val id = "x-$token-$i"
                    val done = CompletableDeferred<Boolean>()
                    pending[id] = done
                    val queuedOk = t.synthesizeToFile(text.substring(block.start, block.end), Bundle(), part, id) == TextToSpeech.SUCCESS
                    val ok = queuedOk && withTimeoutOrNull(BLOCK_TIMEOUT_MS) { done.await() } == true
                    pending.remove(id)
                    if (!ok || part.length() <= 44) {
                        throw ConversionError("La voix du téléphone n'a pas pu écrire le son (bloc ${i + 1} sur ${blocks.size}). Essaie une autre voix.")
                    }
                    parts.add(part)
                    update(token) { st -> st.copy(export = SpeechExport.Working(i + 1, blocks.size)) }
                }
                val wav = File(dir, "voix.wav")
                val job = coroutineContext
                val info = WavJoin.join(parts, wav, gapMs = GAP_MS) { job.ensureActive() }
                parts.forEach { it.delete() }
                val seconds = (info.durationMs / 1000).toInt()
                val audio = if (format == AudioOut.WAV) {
                    wav
                } else {
                    update(token) { st -> st.copy(export = SpeechExport.Working(blocks.size, blocks.size, encoding = true)) }
                    val out = File(dir, "voix.${format.ext}")
                    val args = SpeechArgs.encode(WorkDir.root(context).absolutePath, wav.absolutePath, out.absolutePath, format)
                    val target = if (format == AudioOut.MP3) Target.MP3 else Target.M4A
                    FfmpegRunner(context, engine).run(args, out, target, info.durationMs / 1000.0) { f ->
                        update(token) { st -> if (st.export is SpeechExport.Working) st.copy(export = SpeechExport.Working(blocks.size, blocks.size, true, f)) else st }
                    }
                    wav.delete()
                    out
                }
                val named = File(dir, name)
                if (!audio.renameTo(named)) throw IOException("rename")
                val saved = MediaExporter.export(context, named)
                update(token) { it.copy(export = SpeechExport.Done(saved, seconds)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: "L'audio n'a pas été enregistré.", e.debug)
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'enregistrement : autorise le stockage et réessaie.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: Throwable) {
                fail(token, "L'audio n'a pas été enregistré.", e.toString())
            } finally {
                pending.keys.filter { it.startsWith("x-$token-") }.forEach { pending.remove(it)?.complete(false) }
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    fun cancelExport() {
        generation.incrementAndGet()
        exportJob?.cancel()
        runCatching { tts?.stop() }
        _state.update { if (it.export is SpeechExport.Working) it.copy(export = SpeechExport.None) else it }
    }

    fun dismissExport() {
        _state.update { if (it.busy) it else it.copy(export = SpeechExport.None) }
    }

    private inline fun update(token: Int, crossinline change: (ReadAloudUiState) -> ReadAloudUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(export = SpeechExport.Failed(message, debug)) }
    }

    companion object {
        /** The app's one controller (made on first use, lives as long as the app). */
        val shared: ReadAloudController by lazy { ReadAloudController(App.instance, App.instance.scope, App.instance.engine) }

        /** Pieces handed to the voice ahead of the one being read (no gap between two sentences). */
        private const val AHEAD = 2
        private const val MAX_FILE_BYTES: Long = 80L * 1024 * 1024
        private const val MAX_PDF_PAGES = 400
        private const val BLOCK_TIMEOUT_MS = 120_000L

        /** Pause between two blocks of the saved audio. */
        private const val GAP_MS = 250
        private const val NO_ENGINE = "Ce téléphone n'a pas de voix de synthèse active. Installe ou active « Synthèse vocale Google » (Paramètres › Accessibilité › Synthèse vocale), puis rouvre l'outil."
    }
}

/** FFmpeg arguments that turn the joined WAV into the chosen format, mono, voice quality. */
object SpeechArgs {
    fun encode(root: String, input: String, output: String, format: AudioOut): List<String> {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        val codec = when (format) {
            AudioOut.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "96k")
            AudioOut.M4A -> listOf("-c:a", "aac", "-b:a", "80k", "-movflags", "+faststart")
            AudioOut.WAV -> listOf("-c:a", "pcm_s16le")
        }
        return FfmpegArgs.prefix(input) + listOf("-map", "0:a:0", "-vn", "-ac", "1") + codec + output
    }
}
