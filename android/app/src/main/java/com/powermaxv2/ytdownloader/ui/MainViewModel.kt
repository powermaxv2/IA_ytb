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
import com.powermaxv2.ytdownloader.core.InstagramAccount
import com.powermaxv2.ytdownloader.core.InstagramPost
import com.powermaxv2.ytdownloader.core.InstagramSession
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
import com.powermaxv2.ytdownloader.core.StoryResult
import com.powermaxv2.ytdownloader.core.UpdateCheckJob
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

/** Settings is not in the bottom bar: it opens from the gear in each screen header. */
enum class Tab { YOUTUBE, INSTAGRAM, OTHER, DOWNLOADS, SETTINGS }

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
    val instagram = app.instagram
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

    /** Each emission opens the Instagram login screen. */
    private val _loginRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginRequests: SharedFlow<Unit> = _loginRequests.asSharedFlow()

    init {
        viewModelScope.launch { if (instagram.refresh()) instagram.verify() }
        viewModelScope.launch { runCatching { updater.check(force = false) } }
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

    fun openSettings() {
        if (tab != Tab.SETTINGS) tabBeforeSettings = tab
        tab = Tab.SETTINGS
    }

    fun closeSettings() {
        selectTab(tabBeforeSettings)
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
        selectTab(pane.tab)
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
                Phase.Ready(if (platform == Platform.INSTAGRAM) fetchInstagram(pane, url) else ytdlpInfo(url, cookies = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Phase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Erreur inconnue.")
            }
        }
    }

    private suspend fun fetchInstagram(pane: LinkPane, link: String): MediaInfo {
        val url = instagram.resolveLink(link)
        if (url != link) pane.urlText = url
        val loggedIn = instagram.refresh()
        if (InstagramSession.isStory(url)) return fetchStory(url, loggedIn)
        // With the account, Instagram's own API lists every photo and video of a post, private ones included.
        val post = if (loggedIn) instagram.fetchPost(url) else null
        if (post != null && (post.isCarousel || post.photoCount > 0)) return MediaInfo.Photos(post)
        return try {
            ytdlpInfo(url, cookies = loggedIn)
        } catch (e: DownloadError) {
            // Single video that yt-dlp could not read: download it straight from the API answer.
            post?.let { return MediaInfo.Photos(it) }
            if (loggedIn && instagram.account.value is InstagramAccount.Expired) throw DownloadError(SESSION_EXPIRED)
            throw e
        }
    }

    /** Stories only exist behind a login and yt-dlp cannot read them reliably: ask Instagram's API directly. */
    private suspend fun fetchStory(url: String, loggedIn: Boolean): MediaInfo {
        if (!loggedIn) throw DownloadError(STORY_LOGIN)
        return when (val result = instagram.fetchStory(url)) {
            is StoryResult.Found -> MediaInfo.Photos(result.post)
            StoryResult.SessionExpired -> throw DownloadError(SESSION_EXPIRED)
            is StoryResult.Unavailable -> throw DownloadError("$STORY_UNAVAILABLE\nDétail : ${result.trace}")
        }
    }

    private suspend fun ytdlpInfo(url: String, cookies: Boolean): MediaInfo = withContext(Dispatchers.IO) {
        val workDir = File(app.cacheDir, "analyze/${UUID.randomUUID()}")
        try {
            val cookiesFile = if (cookies) instagram.writeCookiesFile(workDir) else null
            val args = commonArguments +
                listOf("-J", "--no-playlist", "--flat-playlist", "--no-warnings") +
                (cookiesFile?.let { listOf("--cookies", it.absolutePath) } ?: emptyList()) +
                listOf("--", url)
            val response = try {
                engine.execute(args)
            } catch (e: YoutubeDLException) {
                throw DownloadError(Errors.friendly((e.message ?: "").lines()))
            }
            val json = runCatching { JSONObject(response.out.trim()) }.getOrElse {
                // Keep yt-dlp's own explanation when it gave one instead of blaming an out-of-date yt-dlp.
                val reason = response.err.lines().map { it.trim().take(300) }.filter { it.isNotEmpty() }
                throw DownloadError(if (reason.isEmpty()) UNREADABLE else Errors.friendly(reason.takeLast(5)))
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

    // MARK: - Instagram

    fun openInstagramLogin() {
        viewModelScope.launch {
            // Reconnecting: drop the old session first, otherwise it would be taken for the new login.
            if (instagram.refresh()) instagram.logout()
            _loginRequests.emit(Unit)
        }
    }

    /** The login screen closed: a session cookie there means a new login (the old one was dropped before). */
    fun onInstagramLoginClosed() {
        viewModelScope.launch {
            if (!instagram.completeLogin()) return@launch
            toast("Connecté à Instagram")
            // The link that needed the account is analysed again.
            if (insta.phase is Phase.Failed && insta.urlText.isNotBlank()) analyze(insta)
            instagram.verify()
        }
    }

    fun logoutInstagram() {
        viewModelScope.launch {
            instagram.logout()
            toast("Déconnecté d'Instagram")
        }
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
                    "YT Downloader est à jour (version ${updater.currentVersion})."
                } else {
                    dismissedUpdate = null
                    "Version ${release.version} disponible : touchez « Mettre à jour » en haut de l'écran."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "Vérification impossible : ${e.message?.takeIf { it.isNotBlank() } ?: "pas de réseau"}"
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
        private const val SESSION_EXPIRED = "Session Instagram expirée : reconnectez votre compte en haut de l'onglet Instagram."
        private const val STORY_LOGIN = "Les stories Instagram ne sont visibles qu'avec un compte : connectez le vôtre en haut de l'onglet Instagram."
        private const val STORY_UNAVAILABLE = "Story introuvable : elle a expiré (24 h), a été supprimée ou n'est pas visible avec ce compte."
        private const val BEREAL_NO_LINKS =
            "BeReal ne donne aucun lien vers une publication : les BeReal ne se voient que dans l'application, " +
                "et les liens bere.al ne mènent qu'à un profil. Il n'y a rien à télécharger depuis ce lien."
        private const val UNREADABLE =
            "yt-dlp n'a rien pu lire à ce lien. Vérifiez-le et réessayez ; si cela persiste, mettez yt-dlp à jour dans les réglages."
    }
}
