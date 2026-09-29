package com.powermaxv2.ytdownloader.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

sealed interface EngineState {
    data object Starting : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

sealed interface UpdateResult {
    data class Updated(val version: String) : UpdateResult
    data class UpToDate(val version: String) : UpdateResult
}

class DownloadError(message: String) : Exception(message)

/**
 * Wraps yt-dlp (Python), FFmpeg and QuickJS bundled in the APK.
 * yt-dlp updates come from its official GitHub release and are checked against the published SHA-256.
 */
class Engine(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val _integrityAlert = MutableStateFlow<String?>(null)
    val integrityAlert: StateFlow<String?> = _integrityAlert.asStateFlow()

    /** Runs and updates never overlap: replacing yt-dlp while Python reads it would break the run. */
    private val gate = Mutex()
    private var activeRuns = 0

    private val ytdlpFile: File
        get() = File(File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)

    fun start() {
        scope.launch(Dispatchers.IO) {
            checkIntegrity()
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(t.message ?: t.javaClass.simpleName)
                return@launch
            }
            refreshVersion()
            val stale = System.currentTimeMillis() - prefs.lastYtdlpCheck > 12 * 60 * 60 * 1000L
            if (prefs.settings.value.autoUpdateYtDlp && stale) {
                runCatching { update() }
            }
        }
    }

    fun dismissIntegrityAlert() {
        _integrityAlert.value = null
    }

    /** A yt-dlp installed by an update must still match the fingerprint recorded when it was verified. */
    private fun checkIntegrity() {
        val expected = prefs.ytdlpHash ?: return
        val file = ytdlpFile
        if (!file.exists()) {
            prefs.ytdlpHash = null
            return
        }
        val actual = runCatching { Http.sha256(file) }.getOrNull()
        if (!expected.equals(actual, ignoreCase = true)) {
            file.parentFile?.deleteRecursively()
            prefs.ytdlpHash = null
            _integrityAlert.value =
                "yt-dlp avait été modifié : il a été supprimé et remplacé par la version d'origine de l'app."
        }
    }

    suspend fun awaitReady() {
        val state = _state.first { it !is EngineState.Starting }
        if (state is EngineState.Failed) {
            throw DownloadError("Le moteur de téléchargement n'a pas pu démarrer : ${state.message}")
        }
    }

    /**
     * Runs yt-dlp with [args]. [onLine] receives each output line (stdout, plus stderr when [mergeStderr]).
     * Throws [YoutubeDLException] on failure, [YoutubeDL.CanceledException] when cancelled.
     */
    suspend fun execute(
        args: List<String>,
        processId: String? = null,
        mergeStderr: Boolean = false,
        onLine: ((String) -> Unit)? = null,
    ): YoutubeDLResponse {
        awaitReady()
        while (true) {
            val admitted = gate.withLock {
                if (!_updating.value) {
                    activeRuns++
                    true
                } else {
                    false
                }
            }
            if (admitted) break
            _updating.first { !it }
        }
        try {
            return withContext(Dispatchers.IO) {
                val request = YoutubeDLRequest(emptyList<String>()).addCommands(args)
                val callback: ((Float, Long, String) -> Unit)? =
                    onLine?.let { handler -> { _: Float, _: Long, line: String -> handler(line) } }
                YoutubeDL.getInstance().execute(request, processId, mergeStderr, callback)
            }
        } finally {
            withContext(NonCancellable) { gate.withLock { activeRuns-- } }
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    suspend fun refreshVersion() {
        _version.value = runCatching {
            execute(listOf("--version")).out.lines().lastOrNull { it.isNotBlank() }?.trim()
        }.getOrNull()
    }

    /** Installs the latest yt-dlp release after checking its SHA-256 against the official SHA2-256SUMS. */
    suspend fun update(): UpdateResult {
        awaitReady()
        gate.withLock {
            if (activeRuns > 0) throw DownloadError("Attendez la fin des téléchargements en cours.")
            _updating.value = true
        }
        val result = try {
            withContext(Dispatchers.IO) { installLatest() }
        } finally {
            _updating.value = false
        }
        refreshVersion()
        return result
    }

    private fun installLatest(): UpdateResult {
        prefs.lastYtdlpCheck = System.currentTimeMillis()
        val release = JSONObject(Http.getText(RELEASE_API, mapOf("Accept" to "application/vnd.github+json")))
        val tag = release.getString("tag_name").trim()
        val current = _version.value
        if (current != null && compareVersions(tag, current) <= 0) return UpdateResult.UpToDate(current)

        var binaryUrl: String? = null
        var sumsUrl: String? = null
        release.optJSONArray("assets")?.forEachObject { _, asset ->
            when (asset.str("name")) {
                "yt-dlp" -> {
                    binaryUrl = asset.str("browser_download_url")
                }
                "SHA2-256SUMS" -> {
                    sumsUrl = asset.str("browser_download_url")
                }
            }
        }
        val binary = binaryUrl?.takeIf { it.startsWith(DOWNLOAD_PREFIX) }
            ?: throw IOException("Fichier yt-dlp introuvable dans la dernière version.")
        val sums = sumsUrl?.takeIf { it.startsWith(DOWNLOAD_PREFIX) }
            ?: throw IOException("Empreintes SHA-256 introuvables : mise à jour refusée.")

        val expected = Http.getText(sums).lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1].removePrefix("*") == "yt-dlp" }
            ?.get(0)?.lowercase()
            ?: throw IOException("Empreinte de yt-dlp absente : mise à jour refusée.")

        val target = ytdlpFile
        val dir = target.parentFile ?: throw IOException("Dossier yt-dlp invalide")
        dir.mkdirs()
        val part = File(dir, "yt-dlp.part")
        val actual = try {
            Http.download(binary, part)
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        if (actual != expected) {
            part.delete()
            throw SecurityException("Empreinte SHA-256 incorrecte : le fichier téléchargé a été refusé.")
        }
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) {
                part.delete()
                throw IOException("Impossible d'installer la nouvelle version de yt-dlp.")
            }
        }
        prefs.ytdlpHash = actual
        return UpdateResult.Updated(tag)
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val DOWNLOAD_PREFIX = "https://github.com/yt-dlp/yt-dlp/releases/download/"

        /** Compares yt-dlp versions such as "2025.11.12" and "2025.11.12.1". */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
