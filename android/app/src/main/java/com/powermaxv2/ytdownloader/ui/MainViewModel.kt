package com.powermaxv2.ytdownloader.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.DirectItem
import com.powermaxv2.ytdownloader.core.DownloadError
import com.powermaxv2.ytdownloader.core.DownloadRequest
import com.powermaxv2.ytdownloader.core.Errors
import com.powermaxv2.ytdownloader.core.InstagramPost
import com.powermaxv2.ytdownloader.core.InstagramSession
import com.powermaxv2.ytdownloader.core.JobEvent
import com.powermaxv2.ytdownloader.core.JobSpec
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaInfo
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.PlaylistEntry
import com.powermaxv2.ytdownloader.core.PlaylistInfo
import com.powermaxv2.ytdownloader.core.PlaylistQuality
import com.powermaxv2.ytdownloader.core.QualityOption
import com.powermaxv2.ytdownloader.core.UpdateResult
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.core.commonArguments
import com.powermaxv2.ytdownloader.core.str
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

enum class Tab { HOME, DOWNLOADS, SETTINGS }

sealed interface Phase {
    data object Idle : Phase
    data class Loading(val platform: Platform) : Phase
    data class Failed(val message: String) : Phase
    data class Ready(val media: MediaInfo) : Phase
}

data class Toast(val message: String, val success: Boolean = true, val id: Long = System.nanoTime())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val engine = app.engine
    val downloads = app.downloads
    val instagram = app.instagram
    val prefs = app.prefs

    var urlText by mutableStateOf("")
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    var tab by mutableStateOf(Tab.HOME)
        private set
    var showInstagramLogin by mutableStateOf(false)
    var updateMessage by mutableStateOf<String?>(null)
        private set

    /** Incremented each time something is queued: drives the "drop into downloads" animation. */
    var queuedPulse by mutableStateOf(0)
        private set

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    private var analyzeJob: Job? = null

    init {
        viewModelScope.launch { instagram.refresh() }
        viewModelScope.launch {
            downloads.events.collect { event ->
                when (event) {
                    is JobEvent.Finished -> toast("Terminé : ${event.job.title}")
                    is JobEvent.Failed -> toast("Échec : ${event.job.title}", success = false)
                }
            }
        }
    }

    fun selectTab(value: Tab) {
        tab = value
    }

    fun toast(message: String, success: Boolean = true) {
        _toasts.tryEmit(Toast(message, success))
    }

    // MARK: - Links

    fun onSharedText(text: String) {
        urlText = Links.extract(text) ?: text.trim()
        tab = Tab.HOME
        analyze()
    }

    fun paste(text: String?) {
        if (text.isNullOrBlank()) {
            toast("Le presse-papiers est vide", success = false)
            return
        }
        urlText = Links.extract(text) ?: text.trim()
        analyze()
    }

    fun reset() {
        analyzeJob?.cancel()
        urlText = ""
        phase = Phase.Idle
    }

    fun analyze() {
        val url = Links.normalize(urlText)
        if (url == null) {
            phase = Phase.Failed("Collez un lien https:// valide (YouTube, Instagram…).")
            return
        }
        urlText = url
        analyzeJob?.cancel()
        val platform = Links.platform(url)
        phase = Phase.Loading(platform)
        analyzeJob = viewModelScope.launch {
            phase = try {
                Phase.Ready(fetch(url, platform))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Phase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Erreur inconnue.")
            }
        }
    }

    private suspend fun fetch(url: String, platform: Platform): MediaInfo {
        val isInstagram = platform == Platform.INSTAGRAM
        // Photos and carousels: Instagram's own API gives every image, yt-dlp only the videos.
        if (isInstagram && InstagramSession.isPostLink(url) && instagram.refresh()) {
            instagram.fetchPost(url)?.let { post -> if (post.photoCount > 0) return MediaInfo.Photos(post) }
        }
        return try {
            ytdlpInfo(url, isInstagram)
        } catch (e: DownloadError) {
            if (isInstagram && instagram.refresh()) {
                instagram.fetchPost(url)?.let { return MediaInfo.Photos(it) }
            }
            throw e
        }
    }

    private suspend fun ytdlpInfo(url: String, isInstagram: Boolean): MediaInfo = withContext(Dispatchers.IO) {
        val workDir = File(app.cacheDir, "analyze/${UUID.randomUUID()}")
        try {
            val cookies = if (isInstagram) instagram.writeCookiesFile(workDir) else null
            val args = commonArguments +
                listOf("-J", "--no-playlist", "--flat-playlist", "--no-warnings") +
                (cookies?.let { listOf("--cookies", it.absolutePath) } ?: emptyList()) +
                listOf("--", url)
            val response = try {
                engine.execute(args)
            } catch (e: YoutubeDLException) {
                throw DownloadError(Errors.friendly((e.message ?: "").lines()))
            }
            val json = runCatching { JSONObject(response.out.trim()) }.getOrElse {
                throw DownloadError("Réponse inattendue de yt-dlp. Mettez à jour yt-dlp dans les réglages.")
            }
            if (json.str("_type") == "playlist") {
                val playlist = PlaylistInfo.from(json, url)
                if (playlist.entries.isEmpty()) throw DownloadError(NO_MEDIA)
                MediaInfo.Playlist(playlist)
            } else {
                val info = VideoInfo.from(json)
                if (info.formats.isEmpty()) throw DownloadError(NO_MEDIA)
                MediaInfo.Video(info, url)
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    // MARK: - Downloads

    private fun request(
        url: String,
        mode: MediaMode,
        height: Int?,
        qualityLabel: String,
        container: VideoContainer,
        audioFormat: AudioFormat,
        playlistItem: Int? = null,
    ): DownloadRequest {
        val s = prefs.settings.value
        return DownloadRequest(
            url = url,
            mode = mode,
            height = height,
            qualityLabel = qualityLabel,
            container = container,
            audioFormat = audioFormat,
            embedThumbnail = s.embedThumbnail,
            embedMetadata = s.embedMetadata,
            embedChapters = s.embedChapters,
            subtitles = s.subtitles,
            subtitleLangs = s.subtitleLangs,
            playlistItem = playlistItem,
        )
    }

    private fun rememberChoices(container: VideoContainer, audioFormat: AudioFormat) {
        prefs.update { it.copy(container = container, audioFormat = audioFormat) }
    }

    fun downloadVideo(
        info: VideoInfo,
        sourceUrl: String,
        mode: MediaMode,
        quality: QualityOption?,
        container: VideoContainer,
        audioFormat: AudioFormat,
    ) {
        rememberChoices(container, audioFormat)
        val req = request(sourceUrl, mode, quality?.height, quality?.label ?: "Max", container, audioFormat)
        downloads.enqueue(info.title, info.thumbnail, req.summary, Links.platform(sourceUrl), JobSpec.YtDlp(req))
        queued("Ajouté aux téléchargements")
    }

    fun downloadPlaylist(
        playlist: PlaylistInfo,
        entries: List<PlaylistEntry>,
        mode: MediaMode,
        quality: PlaylistQuality,
        container: VideoContainer,
        audioFormat: AudioFormat,
    ) {
        if (entries.isEmpty()) return
        rememberChoices(container, audioFormat)
        val platform = Links.platform(playlist.sourceUrl)
        entries.forEach { entry ->
            val req = request(
                url = entry.url ?: playlist.sourceUrl,
                mode = mode,
                height = quality.height,
                qualityLabel = quality.summaryLabel,
                container = container,
                audioFormat = audioFormat,
                playlistItem = if (entry.url == null) entry.position else null,
            )
            downloads.enqueue(entry.title, entry.thumbnail, req.summary, platform, JobSpec.YtDlp(req))
        }
        queued(if (entries.size == 1) "1 vidéo ajoutée" else "${entries.size} vidéos ajoutées")
    }

    fun downloadPhotos(post: InstagramPost, indexes: List<Int>) {
        val items = indexes.sorted().mapNotNull { post.items.getOrNull(it)?.let { media -> it to media } }
        if (items.isEmpty()) return
        val owner = (post.owner ?: "instagram").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val direct = items.map { (index, media) ->
            val ext = Uri.parse(media.url).lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "mp4") }
                ?: if (media.isVideo) "mp4" else "jpg"
            DirectItem(media.url, "${owner}_${post.shortcode}_${index + 1}.$ext")
        }
        val photos = items.count { !it.second.isVideo }
        val videos = items.size - photos
        val summary = listOfNotNull(
            photos.takeIf { it > 0 }?.let { if (it > 1) "$it photos" else "1 photo" },
            videos.takeIf { it > 0 }?.let { if (it > 1) "$it vidéos" else "1 vidéo" },
        ).joinToString(" · ")
        downloads.enqueue(post.title, post.items.firstOrNull()?.thumbnail, summary, Platform.INSTAGRAM, JobSpec.Direct(direct))
        queued("Ajouté aux téléchargements")
    }

    private fun queued(message: String) {
        queuedPulse++
        toast(message)
    }

    // MARK: - Instagram

    fun onInstagramLoggedIn() {
        viewModelScope.launch {
            if (instagram.refresh()) {
                showInstagramLogin = false
                toast("Connecté à Instagram")
            }
        }
    }

    fun logoutInstagram() {
        viewModelScope.launch {
            instagram.logout()
            toast("Déconnecté d'Instagram")
        }
    }

    // MARK: - yt-dlp updates

    fun updateYtDlp() {
        if (engine.updating.value) return
        updateMessage = null
        viewModelScope.launch {
            updateMessage = try {
                when (val result = engine.update()) {
                    is UpdateResult.Updated -> "yt-dlp mis à jour : ${result.version} (empreinte vérifiée)"
                    is UpdateResult.UpToDate -> "yt-dlp est à jour (${result.version})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "Échec de la mise à jour : ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    companion object {
        private const val NO_MEDIA = "Aucune vidéo téléchargeable trouvée à ce lien (les directs en cours ne sont pas pris en charge)."
    }
}
