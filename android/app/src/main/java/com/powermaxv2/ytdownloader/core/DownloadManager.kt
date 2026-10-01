package com.powermaxv2.ytdownloader.core

import android.content.Context
import android.os.SystemClock
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class JobState { QUEUED, DOWNLOADING, PROCESSING, DONE, FAILED, CANCELED }

data class DownloadJob(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val summary: String,
    val platform: Platform,
    val state: JobState = JobState.QUEUED,
    /** 0…1, or a negative value while the progress is unknown (post-processing). */
    val progress: Float = 0f,
    val status: String = "En attente…",
    val outputs: List<SavedFile> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = state == JobState.QUEUED || state == JobState.DOWNLOADING || state == JobState.PROCESSING
    val isFinished: Boolean get() = !isActive
}

data class DirectItem(val url: String, val fileName: String)

sealed interface JobSpec {
    data class YtDlp(val request: DownloadRequest) : JobSpec
    data class Direct(val items: List<DirectItem>) : JobSpec
}

sealed interface JobEvent {
    data class Finished(val job: DownloadJob) : JobEvent
    data class Failed(val job: DownloadJob) : JobEvent
}

class DownloadManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
    private val prefs: Prefs,
    private val instagram: InstagramSession,
) {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _events = MutableSharedFlow<JobEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<JobEvent> = _events.asSharedFlow()

    private val specs = ConcurrentHashMap<String, JobSpec>()
    private val running = ConcurrentHashMap<String, Job>()
    private val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val slots = Semaphore(MAX_CONCURRENT)

    fun enqueue(title: String, thumbnail: String?, summary: String, platform: Platform, spec: JobSpec): String {
        val id = UUID.randomUUID().toString()
        specs[id] = spec
        _jobs.update { listOf(DownloadJob(id, title, thumbnail, summary, platform)) + it }
        launch(id)
        return id
    }

    fun cancel(id: String) {
        canceled += id
        engine.cancel(id)
        running[id]?.cancel()
        update(id) { if (it.isActive) it.copy(state = JobState.CANCELED, status = "Annulé", progress = 0f) else it }
    }

    fun retry(id: String) {
        if (!specs.containsKey(id)) return
        canceled -= id
        update(id) { it.copy(state = JobState.QUEUED, status = "En attente…", progress = 0f, outputs = emptyList()) }
        launch(id)
    }

    fun remove(id: String) {
        if (_jobs.value.firstOrNull { it.id == id }?.isActive == true) cancel(id)
        _jobs.update { list -> list.filterNot { it.id == id } }
        specs -= id
    }

    fun clearFinished() {
        val finished = _jobs.value.filter { it.isFinished }.map { it.id }.toSet()
        _jobs.update { list -> list.filterNot { it.id in finished } }
        finished.forEach { specs -= it }
    }

    private fun launch(id: String) {
        DownloadService.start(context)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            slots.withPermit {
                if (id !in canceled) run(id)
            }
        }
        running[id] = job
        job.invokeOnCompletion { running.remove(id, job) }
        job.start()
    }

    private fun update(id: String, transform: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun job(id: String): DownloadJob? = _jobs.value.firstOrNull { it.id == id }

    private suspend fun run(id: String) {
        val spec = specs[id] ?: return
        val workDir = File(context.cacheDir, "jobs/$id")
        workDir.deleteRecursively()
        workDir.mkdirs()
        update(id) { it.copy(state = JobState.DOWNLOADING, status = "Démarrage…", progress = 0f) }
        try {
            val files = when (spec) {
                is JobSpec.YtDlp -> runYtDlp(id, spec.request, workDir)
                is JobSpec.Direct -> runDirect(id, spec.items, workDir)
            }
            if (files.isEmpty()) throw DownloadError("Aucun fichier n'a été produit.")
            if (id in canceled) return
            update(id) { it.copy(state = JobState.PROCESSING, status = "Enregistrement dans la galerie…", progress = -1f) }
            val saved = files.map { MediaExporter.export(context, it) }
            val size = saved.sumOf { it.size }.toDouble()
            val detail = if (saved.size > 1) "${saved.size} fichiers · ${Fmt.bytes(size)}" else Fmt.bytes(size)
            update(id) {
                it.copy(state = JobState.DONE, status = "Terminé · $detail", progress = 1f, outputs = saved)
            }
            job(id)?.let { finished ->
                _events.tryEmit(JobEvent.Finished(finished))
                if (prefs.settings.value.notify) Notifications.finished(context, finished)
            }
        } catch (e: CancellationException) {
            update(id) { it.copy(state = JobState.CANCELED, status = "Annulé", progress = 0f) }
            throw e
        } catch (e: Throwable) {
            if (id in canceled || e is YoutubeDL.CanceledException || e is InterruptedException) {
                update(id) { it.copy(state = JobState.CANCELED, status = "Annulé", progress = 0f) }
            } else {
                val message = e.message?.takeIf { it.isNotBlank() } ?: "Erreur inconnue."
                update(id) { it.copy(state = JobState.FAILED, status = message, progress = 0f) }
                job(id)?.let { _events.tryEmit(JobEvent.Failed(it)) }
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun runYtDlp(id: String, request: DownloadRequest, workDir: File): List<File> {
        val cookies = if (Links.platform(request.url) == Platform.INSTAGRAM) instagram.writeCookiesFile(workDir) else null
        val outDir = File(workDir, "out").apply { mkdirs() }
        val tracker = ProgressTracker(request.mode)
        val tail = ArrayDeque<String>()
        val produced = mutableListOf<File>()
        var lastUpdate = 0L

        try {
            engine.execute(request.arguments(outDir, cookies), processId = id, mergeStderr = true) { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@execute
                when {
                    line.startsWith("[FILE]") -> synchronized(produced) { produced += File(line.removePrefix("[FILE]").trim()) }
                    line.startsWith("[PROG]") -> {
                        val progress = tracker.onProgress(line.removePrefix("[PROG]")) ?: return@execute
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate > 250 || progress.state == JobState.PROCESSING) {
                            lastUpdate = now
                            update(id) {
                                if (it.isActive) it.copy(state = progress.state, progress = progress.fraction, status = progress.text) else it
                            }
                        }
                    }
                    line.startsWith("[POST]") -> {
                        val parts = line.removePrefix("[POST]").split("|")
                        if (parts.size >= 2 && parts[1] != "finished") {
                            update(id) {
                                if (it.isActive) it.copy(state = JobState.PROCESSING, progress = -1f, status = postprocessorLabel(parts[0])) else it
                            }
                        }
                    }
                    else -> synchronized(tail) {
                        tail.addLast(line)
                        while (tail.size > 40) tail.removeFirst()
                    }
                }
            }
        } catch (e: YoutubeDLException) {
            val lines = synchronized(tail) { tail.toList() }
            throw DownloadError(Errors.friendly(lines + listOfNotNull(e.message)))
        }

        val files = synchronized(produced) { produced.toList() }.filter { it.isFile }
        if (files.isNotEmpty()) return files
        // Fallback: whatever media yt-dlp left in the output folder.
        return outDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            .filter { MediaExporter.mimeOf(it.name).let { m -> m.startsWith("video/") || m.startsWith("audio/") || m.startsWith("image/") } }
            .toList()
    }

    private suspend fun runDirect(id: String, items: List<DirectItem>, workDir: File): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        items.forEachIndexed { index, item ->
            val target = File(workDir, item.fileName)
            var lastUpdate = 0L
            Http.download(
                url = item.url,
                dest = target,
                headers = mapOf("Referer" to "https://www.instagram.com/"),
                shouldStop = { id in canceled },
            ) { downloaded, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < 200) return@download
                lastUpdate = now
                val itemFraction = if (total > 0) downloaded.toFloat() / total else 0f
                val overall = (index + itemFraction) / items.size
                update(id) {
                    if (it.isActive) {
                        it.copy(
                            state = JobState.DOWNLOADING,
                            progress = overall.coerceIn(0f, 1f),
                            status = "${(overall * 100).toInt()} % · élément ${index + 1}/${items.size}",
                        )
                    } else {
                        it
                    }
                }
            }
            files += target
        }
        files
    }

    private fun postprocessorLabel(name: String): String = when (name) {
        "Merger" -> "Fusion de l'audio et de la vidéo…"
        "FFmpegExtractAudio" -> "Conversion audio…"
        "EmbedThumbnail" -> "Ajout de la miniature…"
        "FFmpegMetadata" -> "Écriture des métadonnées…"
        "FFmpegEmbedSubtitle" -> "Ajout des sous-titres…"
        "FFmpegVideoRemuxer", "FFmpegVideoConvertor" -> "Conversion vidéo…"
        "MoveFiles", "MoveFilesAfterDownload" -> "Finalisation…"
        else -> "Traitement…"
    }

    companion object {
        private const val MAX_CONCURRENT = 2
    }
}

/** Turns yt-dlp's `[PROG]` lines into an overall progress (video + audio are downloaded one after the other). */
private class ProgressTracker(private val mode: MediaMode) {
    data class Snapshot(val state: JobState, val fraction: Float, val text: String)

    private var currentFormat: String? = null
    private var phaseIndex = 0
    private var progress = 0.0

    fun onProgress(payload: String): Snapshot? {
        val parts = payload.split("|")
        if (parts.size < 8) return null
        fun number(s: String) = s.trim().toDoubleOrNull()

        val downloaded = number(parts[0]) ?: 0.0
        val total = number(parts[1]) ?: number(parts[2])
        val speed = number(parts[3])
        val eta = number(parts[4])
        val formatId = parts[5]
        val hasVideo = parts[6].trim() !in setOf("none", "NA", "")
        val hasAudio = parts[7].trim() !in setOf("none", "NA", "")
        // Direct links, Snapchat and X progressive files declare no codec at all: one complete file, not video + audio.
        val codecsUnknown = parts[6].trim() in setOf("NA", "") && parts[7].trim() in setOf("NA", "")

        currentFormat?.let { if (it != formatId) phaseIndex++ }
        currentFormat = formatId

        val expected = if (mode == MediaMode.AUDIO || codecsUnknown || (hasVideo && hasAudio)) 1 else 2
        val fraction = total?.let { if (it > 0) minOf(1.0, downloaded / it) else 0.0 } ?: 0.0
        val phase = minOf(phaseIndex, expected - 1)
        progress = maxOf(progress, minOf(1.0, (phase + fraction) / expected))

        if (fraction >= 1 && phase == expected - 1) {
            return Snapshot(JobState.PROCESSING, -1f, "Finalisation…")
        }
        val pieces = mutableListOf("${(progress * 100).toInt()} %")
        if (expected == 2) pieces += if (phase == 0) "vidéo" else "audio"
        if (total != null && total > 0) pieces += "${Fmt.bytes(downloaded)} / ${Fmt.bytes(total)}"
        if (speed != null && speed > 0) pieces += Fmt.speed(speed)
        if (eta != null && eta > 0) pieces += "reste ${Fmt.eta(eta)}"
        return Snapshot(JobState.DOWNLOADING, progress.toFloat(), pieces.joinToString(" · "))
    }
}
