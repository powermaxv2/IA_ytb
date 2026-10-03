package com.powermaxv2.ytdownloader.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A version of the Android app published as a GitHub release named "android-v1.3.1". */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** SHA-256 that GitHub announces for the file, when it gives one. */
    val sha256: String?,
)

/** Release notes are written in Markdown on GitHub: plain lines for the app and the notification. */
object UpdateNotes {
    private val bullet = Regex("""^[-*+]\s+""")

    fun lines(markdown: String): List<String> = markdown.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("```") }
        .map { it.replace(bullet, "• ").replace("**", "").replace("`", "") }

    fun preview(markdown: String, maxLines: Int): String = lines(markdown).take(maxLines).joinToString("\n")
}

sealed interface AppUpdateState {
    data object None : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : AppUpdateState

    /** Android first has to allow this app to install apps ("Installer des applis inconnues"), once. */
    data class NeedsPermission(val release: AppRelease) : AppUpdateState
    data class Installing(val release: AppRelease) : AppUpdateState
    data class Failed(val release: AppRelease, val message: String) : AppUpdateState
}

/**
 * Updates the app from the GitHub releases of the project: finds a newer version, downloads the APK for this phone,
 * checks its SHA-256 and that it is signed like the installed app, then installs it with Android's package installer.
 */
class AppUpdater(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private var job: Job? = null

    /** Verified APK waiting for the install permission. */
    private var verifiedApk: File? = null

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Asks GitHub for the newest Android release. Returns null when this app is already up to date. */
    suspend fun check(force: Boolean): AppRelease? {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastAppUpdateCheck < FOREGROUND_INTERVAL) return (_state.value as? AppUpdateState.Available)?.release
        _checking.value = true
        try {
            val release = withContext(Dispatchers.IO) { fetchNewest() }
            prefs.lastAppUpdateCheck = now
            val current = _state.value
            when {
                release == null -> if (current is AppUpdateState.Available) _state.value = AppUpdateState.None
                // A download or an installation in progress is never interrupted by a check.
                current is AppUpdateState.None || current is AppUpdateState.Available || current is AppUpdateState.Failed ->
                    _state.value = AppUpdateState.Available(release)
            }
            return release
        } finally {
            _checking.value = false
        }
    }

    /** Periodic check (JobScheduler): a system notification announces each new version once. */
    suspend fun backgroundCheck() {
        if (!prefs.settings.value.autoCheckAppUpdates) return
        val release = runCatching { check(force = true) }.getOrNull() ?: return
        if (prefs.notifiedAppVersion == release.version) return
        prefs.notifiedAppVersion = release.version
        Notifications.updateAvailable(context, release)
    }

    /** "Mettre à jour": downloads, verifies and installs the version found by [check] (checks first if needed). */
    fun startUpdate() {
        if (job?.isActive == true) return
        job = scope.launch {
            val release = when (val current = _state.value) {
                is AppUpdateState.Available -> current.release
                is AppUpdateState.Failed -> current.release
                is AppUpdateState.NeedsPermission -> {
                    resumeInstall()
                    return@launch
                }
                is AppUpdateState.None -> runCatching { check(force = true) }.getOrNull() ?: return@launch
                else -> return@launch
            }
            try {
                val apk = downloadVerified(release)
                verifiedApk = apk
                install(release, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = AppUpdateState.Failed(release, e.message?.takeIf { it.isNotBlank() } ?: "Erreur inconnue.")
            }
        }
    }

    /** Back in the app after the "Installer des applis inconnues" screen: installs if it was allowed. */
    fun resumeInstall() {
        val waiting = _state.value as? AppUpdateState.NeedsPermission ?: return
        val apk = verifiedApk?.takeIf { it.isFile }
        if (apk == null) {
            _state.value = AppUpdateState.Available(waiting.release)
            return
        }
        if (!canInstall()) return
        // Set at once so that a second onResume does not start a second installation.
        _state.value = AppUpdateState.Installing(waiting.release)
        scope.launch {
            try {
                install(waiting.release, apk)
            } catch (e: Throwable) {
                _state.value = AppUpdateState.Failed(waiting.release, e.message ?: "Échec de l'installation.")
            }
        }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Android asks the user to confirm: shown at once when the app is open, otherwise through a notification. */
    fun onConfirmationNeeded(confirm: Intent) {
        val release = (_state.value as? AppUpdateState.Installing)?.release
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val shown = App.instance.inForeground && runCatching { context.startActivity(confirm) }.isSuccess
        if (!shown) Notifications.installReady(context, confirm, release?.version)
    }

    fun onInstallFailed(status: Int, detail: String?) {
        val release = when (val current = _state.value) {
            is AppUpdateState.Installing -> current.release
            is AppUpdateState.Available -> current.release
            else -> return
        }
        val message = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android a bloqué l'installation."
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflit avec l'app installée : la mise à jour n'est pas signée de la même façon."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Cette version n'est pas compatible avec ce téléphone."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant pour installer la mise à jour."
            PackageInstaller.STATUS_FAILURE_INVALID -> "Le fichier de mise à jour est invalide."
            else -> "Échec de l'installation" + (detail?.takeIf { it.isNotBlank() }?.let { " : $it" } ?: ".")
        }
        _state.value = AppUpdateState.Failed(release, message)
    }

    /** The new version is installed (called in the new process): clean up. */
    fun onAppReplaced() {
        File(context.cacheDir, UPDATE_DIR).deleteRecursively()
    }

    // MARK: - GitHub

    private fun fetchNewest(): AppRelease? {
        val body = Http.getText(RELEASES_API, mapOf("Accept" to "application/vnd.github+json"))
        val candidates = mutableListOf<AppRelease>()
        JSONArray(body).forEachObject { _, release ->
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@forEachObject
            val version = tagRegex.matchEntire(release.str("tag_name").orEmpty())?.groupValues?.get(1) ?: return@forEachObject
            val assets = buildMap<String, JSONObject> {
                release.optJSONArray("assets")?.forEachObject { _, asset -> asset.str("name")?.let { put(it, asset) } }
            }
            // The APK for this phone's processor, or the universal one.
            val asset = (Build.SUPPORTED_ABIS.toList() + "universal")
                .firstNotNullOfOrNull { abi -> assets["YT-Downloader-$version-$abi.apk"] } ?: return@forEachObject
            val url = asset.str("browser_download_url")?.takeIf { it.startsWith(DOWNLOAD_PREFIX) } ?: return@forEachObject
            candidates += AppRelease(
                version = version,
                notes = release.str("body").orEmpty().trim(),
                apkUrl = url,
                apkSize = asset.num("size")?.toLong() ?: 0L,
                sha256 = asset.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
            )
        }
        val newest = candidates.maxWithOrNull { a, b -> Engine.compareVersions(a.version, b.version) } ?: return null
        return newest.takeIf { Engine.compareVersions(it.version, currentVersion) > 0 }
    }

    // MARK: - Download and checks

    private suspend fun downloadVerified(release: AppRelease): File {
        _state.value = AppUpdateState.Downloading(release, 0f)
        val dir = File(context.cacheDir, UPDATE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val apk = File(dir, "YT-Downloader-${release.version}.apk")
        val coroutine = currentCoroutineContext()
        var lastUpdate = 0L
        val sha256 = withContext(Dispatchers.IO) {
            Http.download(release.apkUrl, apk, shouldStop = { !coroutine.isActive }) { done, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < 200) return@download
                lastUpdate = now
                val size = if (total > 0) total else release.apkSize
                _state.value = AppUpdateState.Downloading(release, if (size > 0) (done.toFloat() / size).coerceIn(0f, 1f) else 0f)
            }
        }
        // GitHub gives the SHA-256 of every release file: without it, or if it differs, the file is refused.
        if (release.sha256 == null || !release.sha256.equals(sha256, ignoreCase = true)) {
            apk.delete()
            throw IOException(
                if (release.sha256 == null) "Empreinte SHA-256 absente de la release : mise à jour refusée."
                else "Le fichier reçu est abîmé (empreinte SHA-256 différente). Réessayez.",
            )
        }
        withContext(Dispatchers.IO) { checkApk(apk) }
        return apk
    }

    /** Same app, newer version, same signing key as the installed app: anything else is refused. */
    private fun checkApk(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        var candidate = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw IOException("Le fichier téléchargé n'est pas une app Android valide.")
        // Some Android 9 builds give no signing info for an APK file: ask again the old way.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && candidate.signingInfo == null) {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)?.let { candidate = it }
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != context.packageName) {
            throw SecurityException("Ce fichier n'est pas Gulp : mise à jour refusée.")
        }
        if (PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw IOException("Cette version n'est pas plus récente que celle installée.")
        }
        val expected = signers(installed)
        if (expected.isEmpty() || signers(candidate) != expected) {
            throw SecurityException("Cette mise à jour n'est pas signée comme l'app installée : elle a été refusée.")
        }
    }

    private fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return certificates.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    // MARK: - Installation

    private suspend fun install(release: AppRelease, apk: File) {
        if (!canInstall()) {
            _state.value = AppUpdateState.NeedsPermission(release)
            return
        }
        _state.value = AppUpdateState.Installing(release)
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                // Android 12+: no confirmation once this app has installed itself before.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("YT-Downloader.apk", 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output, 256 * 1024) }
                        session.fsync(output)
                    }
                    val status = Intent(context, InstallStatusReceiver::class.java)
                    val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pending = PendingIntent.getBroadcast(context, sessionId, status, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                runCatching { installer.abandonSession(sessionId) }
                throw e
            }
        }
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/powermaxv2/IA_ytb/releases?per_page=30"
        private const val DOWNLOAD_PREFIX = "https://github.com/powermaxv2/IA_ytb/releases/download/"
        private const val UPDATE_DIR = "update"

        /** At most one check every 5 min when the app comes to the screen (the background job runs every 6 h). */
        private const val FOREGROUND_INTERVAL = 5 * 60 * 1000L
        private val tagRegex = Regex("""android-v(\d+\.\d+\.\d+)""")
    }
}
