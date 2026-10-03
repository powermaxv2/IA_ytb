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
import com.powermaxv2.ytdownloader.core.Instagram
import com.powermaxv2.ytdownloader.core.InstagramPost
import com.powermaxv2.ytdownloader.core.JobEvent
import com.powermaxv2.ytdownloader.core.JobSpec
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaInfo
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.Notifications
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.PlaylistEntry
import com.powermaxv2.ytdownloader.core.PlaylistInfo
import com.powermaxv2.ytdownloader.core.PlaylistQuality
import com.powermaxv2.ytdownloader.core.QualityOption
import com.powermaxv2.ytdownloader.core.UpdateCheckJob
import com.powermaxv2.ytdownloader.core.UpdateResult
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.core.commonArguments
import com.powermaxv2.ytdownloader.core.str
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
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
import java.util.Locale

/** Settings is not in the bottom bar: it opens from the gear in each screen header. */
enum class Tab { YOUTUBE, INSTAGRAM, OTHER, DOWNLOADS, SETTINGS }

/** Where the hub is: the home screen (list of tools), a tool, or the settings opened from the home screen. */
enum class Place { HOME, TOOL, SETTINGS }

sealed interface Phase {
    data object Idle : Phase
    data class Loading(val platform: Platform) : Phase
    data class Failed(val message: String) : Phase
    data class Ready(val media: MediaInfo) : Phase
}

/** Link field and analysis result of one link tab (YouTube, Instagram, or Autres for every other site). */
class LinkPane(val platform: Platform, val tab: Tab, val name: String) {
    var urlText by mutableStateOf("")
    var phase by mutableStateOf<Phase>(Phase.Idle)
    internal var job: Job? = null
}

data class Toast(val message: String, val success: Boolean = true, val id: Long = System.nanoTime())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val engine = app.engine
    val downloads = app.downloads
    val prefs = app.prefs
    val updater = app.updater

    /** Result of "Rechercher une mise à jour" in the settings. */
    var appUpdateMessage by mutableStateOf<String?>(null)
        private set

    /** Version whose banner was closed with ✕ (shown again on the next launch). */
    var dismissedUpdate by mutableStateOf<String?>(null)
        private set

    val youtube = LinkPane(Platform.YOUTUBE, Tab.YOUTUBE, "YouTube")
    val insta = LinkPane(Platform.INSTAGRAM, Tab.INSTAGRAM, "Instagram")
    val other = LinkPane(Platform.OTHER, Tab.OTHER, "Autres")

    /** Every launch starts on the home screen. */
    var place by mutableStateOf(Place.HOME)
        private set

    /** Tool shown when [place] is [Place.TOOL]. */
    var toolId by mutableStateOf(ToolRegistry.DOWNLOADER)
        private set

    var tab by mutableStateOf(Tab.YOUTUBE)
        private set

    /** Platform whose colours the app wears: the current link tab, or the last one on Files / Settings. */
    var accentPlatform by mutableStateOf(Platform.YOUTUBE)
        private set

    private var tabBeforeSettings = Tab.YOUTUBE
    var updateMessage by mutableStateOf<String?>(null)
        private set

    /** Incremented each time something is queued: drives the "drop into downloads" animation. */
    var queuedPulse by mutableStateOf(0)
        private set

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    init {
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
        when (value) {
            Tab.YOUTUBE -> accentPlatform = Platform.YOUTUBE
            Tab.INSTAGRAM -> accentPlatform = Platform.INSTAGRAM
            Tab.OTHER -> accentPlatform = Platform.OTHER
            else -> Unit
        }
    }

    /** Opens a tool of the hub; ignored for an unknown tool or one that is not out yet. */
    fun openTool(id: String) {
        if (ToolRegistry.find(id) == null) return
        toolId = id
        place = Place.TOOL
    }

    fun goHome() {
        place = Place.HOME
    }

    /** From the home screen: settings over the home screen; inside the downloader: its Réglages tab. */
    fun openSettings() {
        if (place != Place.TOOL) {
            place = Place.SETTINGS
            return
        }
        if (tab != Tab.SETTINGS) tabBeforeSettings = tab
        tab = Tab.SETTINGS
    }

    fun closeSettings() {
        if (place == Place.SETTINGS) {
            place = Place.HOME
            return
        }
        selectTab(tabBeforeSettings)
    }

    /** Opens the downloader on [value] (notification "Fichiers", shared link). */
    fun showInDownloader(value: Tab) {
        openTool(ToolRegistry.DOWNLOADER)
        selectTab(value)
    }

    /** Back to the last link tab (from the empty Files screen). */
    fun openLinkTab() {
        selectTab(
            when (accentPlatform) {
                Platform.INSTAGRAM -> Tab.INSTAGRAM
                Platform.OTHER -> Tab.OTHER
                else -> Tab.YOUTUBE
            },
        )
    }

    fun toast(message: String, success: Boolean = true) {
        _toasts.tryEmit(Toast(message, success))
    }

    // MARK: - Links

    /** YouTube and Instagram links go to their own tab; X, TikTok, Facebook, Snapchat, BeReal and any other site to Autres. */
    private fun paneFor(url: String): LinkPane = when (Links.platform(url)) {
        Platform.YOUTUBE -> youtube
        Platform.INSTAGRAM -> insta
        else -> other
    }

    private fun show(pane: LinkPane) {
        showInDownloader(pane.tab)
    }

    fun onSharedText(text: String) {
        val link = Links.extract(text) ?: text.trim()
        val pane = Links.normalize(link)?.let(::paneFor) ?: youtube
        pane.urlText = link
        show(pane)
        analyze(pane)
    }

    fun paste(pane: LinkPane, text: String?) {
        if (text.isNullOrBlank()) {
            toast("Le presse-papiers est vide", success = false)
            return
        }
        pane.urlText = Links.extract(text) ?: text.trim()
        analyze(pane)
    }

    fun reset(pane: LinkPane) {
        pane.job?.cancel()
        pane.urlText = ""
        pane.phase = Phase.Idle
    }

    fun analyze(pane: LinkPane) {
        val url = Links.normalize(pane.urlText)
        if (url == null) {
            pane.phase = Phase.Failed(
                when (pane.platform) {
                    Platform.INSTAGRAM -> "Collez un lien Instagram valide (https://www.instagram.com/…)."
                    Platform.OTHER -> "Collez un lien https:// valide (X, TikTok, Facebook, Snapchat ou un site de vidéos)."
                    else -> "Collez un lien https:// valide (YouTube…)."
                },
            )
            return
        }
        val platform = Links.platform(url)
        // A link pasted in the wrong tab moves to the right tab.
        val target = paneFor(url)
        if (target !== pane) {
            reset(pane)
            target.urlText = url
            show(target)
            val what = if (platform == Platform.OTHER) "Lien" else "Lien ${platform.label}"
            toast("$what : ouvert dans l'onglet ${target.name}")
            analyze(target)
            return
        }
        pane.urlText = url
        pane.job?.cancel()
        if (platform == Platform.BEREAL) {
            pane.phase = Phase.Failed(BEREAL_NO_LINKS)
            return
        }
        pane.phase = Phase.Loading(platform)
        pane.job = viewModelScope.launch {
            pane.phase = try {
                Phase.Ready(if (platform == Platform.INSTAGRAM) fetchInstagram(pane, url) else mediaInfo(ytdlpJson(url), url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Phase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Erreur inconnue.")
            }
        }
    }

    /** Public posts only, read without any account: the app no longer logs in to Instagram. */
    private suspend fun fetchInstagram(pane: LinkPane, link: String): MediaInfo {
        val url = Instagram.resolveLink(link)
        if (url != link) pane.urlText = url
        if (Instagram.isStory(url)) throw DownloadError(STORY_NO_ACCOUNT)
        // Photos have no video format: yt-dlp must describe them instead of stopping with an error.
        val json = ytdlpJson(url, listOf("--ignore-no-formats-error"))
        Instagram.photoPost(json, url)?.let { return MediaInfo.Photos(it) }
        return mediaInfo(json, url)
    }

    private suspend fun ytdlpJson(url: String, extraArgs: List<String> = emptyList()): JSONObject = withContext(Dispatchers.IO) {
        val args = commonArguments + listOf("-J", "--no-playlist", "--flat-playlist", "--no-warnings") + extraArgs + listOf("--", url)
        val response = try {
            engine.execute(args)
        } catch (e: YoutubeDLException) {
            throw DownloadError(Errors.friendly((e.message ?: "").lines()))
        }
        runCatching { JSONObject(response.out.trim()) }.getOrElse {
            // Keep yt-dlp's own explanation when it gave one instead of blaming an out-of-date yt-dlp.
            val reason = response.err.lines().map { it.trim().take(300) }.filter { it.isNotEmpty() }
            throw DownloadError(if (reason.isEmpty()) UNREADABLE else Errors.friendly(reason.takeLast(5)))
        }
    }

    private fun mediaInfo(json: JSONObject, url: String): MediaInfo =
        if (json.str("_type") == "playlist") {
            val playlist = PlaylistInfo.from(json, url)
            if (playlist.entries.isEmpty()) throw DownloadError(NO_MEDIA)
            MediaInfo.Playlist(playlist)
        } else {
            val info = VideoInfo.from(json)
            if (info.formats.isEmpty()) throw DownloadError(NO_MEDIA)
            MediaInfo.Video(info, url)
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
        val n = entries.size
        queued(
            when {
                mode == MediaMode.AUDIO -> if (n > 1) "$n pistes audio ajoutées" else "1 piste audio ajoutée"
                n > 1 -> "$n vidéos ajoutées"
                else -> "1 vidéo ajoutée"
            },
        )
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

    // MARK: - App updates

    fun startAppUpdate() {
        dismissedUpdate = null
        Notifications.cancelUpdate(app)
        updater.startUpdate()
    }

    fun dismissAppUpdate(version: String) {
        dismissedUpdate = version
    }

    fun setAutoCheckAppUpdates(enabled: Boolean) {
        prefs.update { it.copy(autoCheckAppUpdates = enabled) }
        UpdateCheckJob.schedule(app, enabled)
    }

    fun checkAppUpdate() {
        if (updater.checking.value) return
        appUpdateMessage = null
        viewModelScope.launch {
            appUpdateMessage = try {
                val release = updater.check(force = true)
                if (release == null) {
                    "Gulp est à jour (version ${updater.currentVersion})."
                } else {
                    dismissedUpdate = null
                    "Version ${release.version} disponible : touchez « Mettre à jour » en haut de l'écran."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                when (e.message) {
                    "HTTP 403", "HTTP 429" -> "GitHub limite les vérifications pour le moment : réessayez dans une heure."
                    else -> "Vérification impossible : ${e.message?.takeIf { it.isNotBlank() } ?: "pas de réseau"}"
                }
            }
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
        private const val STORY_NO_ACCOUNT =
            "Les stories Instagram ne se voient qu'avec un compte connecté, et l'app ne se connecte plus à Instagram " +
                "(pour protéger votre compte) : elles ne peuvent pas être téléchargées."
        private const val BEREAL_NO_LINKS =
            "BeReal ne donne aucun lien vers une publication : les BeReal ne se voient que dans l'application, " +
                "et les liens bere.al ne mènent qu'à un profil. Il n'y a rien à télécharger depuis ce lien."
        private const val UNREADABLE =
            "yt-dlp n'a rien pu lire à ce lien. Vérifiez-le et réessayez ; si cela persiste, mettez yt-dlp à jour dans les réglages."
    }
}
