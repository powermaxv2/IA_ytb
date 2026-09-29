package com.powermaxv2.ytdownloader.core

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import kotlin.coroutines.resume

data class InstagramMedia(
    val url: String,
    val isVideo: Boolean,
    val thumbnail: String,
    val width: Int?,
    val height: Int?,
)

data class InstagramPost(
    val shortcode: String,
    val sourceUrl: String,
    val owner: String?,
    val caption: String?,
    val items: List<InstagramMedia>,
) {
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.count { it.isVideo }
    val isCarousel: Boolean get() = items.size > 1
    val title: String
        get() = caption?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80)
            ?: owner?.let { "Publication de @$it" }
            ?: "Publication Instagram"
}

sealed interface InstagramAccount {
    data object LoggedOut : InstagramAccount

    /** [username] stays null until Instagram has confirmed who is connected. */
    data class LoggedIn(val username: String?) : InstagramAccount

    /** The cookies are still there but Instagram refuses them: the user must log in again. */
    data object Expired : InstagramAccount
}

/**
 * Instagram account connection. The login happens in a WebView on instagram.com: the app never sees the
 * password, only the session cookies, which stay in the app's private storage.
 */
class InstagramSession(private val context: Context) {
    private val _account = MutableStateFlow<InstagramAccount>(InstagramAccount.LoggedOut)
    val account: StateFlow<InstagramAccount> = _account.asStateFlow()

    @Volatile
    private var cachedUserAgent: String? = null

    /** Same browser identity as the login page, so API calls look like the browser that logged in. */
    private suspend fun userAgent(): String = cachedUserAgent ?: withContext(Dispatchers.Main) {
        val agent = runCatching { browserUserAgent(WebSettings.getDefaultUserAgent(context)) }.getOrNull() ?: FALLBACK_UA
        cachedUserAgent = agent
        agent
    }

    suspend fun cookieHeader(): String? = withContext(Dispatchers.Main) {
        runCatching { CookieManager.getInstance().getCookie(HOME) }.getOrNull()
    }

    private suspend fun sessionCookies(): String? = cookieHeader()?.takeIf { it.contains("sessionid=") }

    /** Updates the state from the stored cookies (no network). Returns true when a session cookie exists. */
    suspend fun refresh(): Boolean {
        val hasSession = sessionCookies() != null
        _account.update { current ->
            when {
                !hasSession -> InstagramAccount.LoggedOut
                current is InstagramAccount.LoggedOut -> InstagramAccount.LoggedIn(null)
                else -> current
            }
        }
        return hasSession
    }

    /** Called when the login page may have finished: true if a new session cookie is there. */
    suspend fun completeLogin(): Boolean {
        if (sessionCookies() == null) return false
        withContext(Dispatchers.Main) { CookieManager.getInstance().flush() }
        _account.value = InstagramAccount.LoggedIn(null)
        return true
    }

    /**
     * Asks Instagram which account is connected: fills in the username, or marks the session as expired
     * when Instagram refuses it. Network problems leave the state unchanged.
     */
    suspend fun verify() {
        val cookies = sessionCookies()
        if (cookies == null) {
            _account.value = InstagramAccount.LoggedOut
            return
        }
        val userId = cookieValue(cookies, "ds_user_id") ?: return
        val headers = apiHeaders(cookies)
        val response = withContext(Dispatchers.IO) {
            runCatching { Http.get("$WEB_API/users/$userId/info/", headers, followRedirects = false) }.getOrNull()
        } ?: return
        when {
            response.isSuccess -> {
                val username = runCatching { JSONObject(response.body).optJSONObject("user")?.str("username") }.getOrNull()
                _account.value = InstagramAccount.LoggedIn(username)
            }
            isSessionRefused(response) -> _account.value = InstagramAccount.Expired
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            suspendCancellableCoroutine<Unit> { continuation ->
                manager.removeAllCookies { continuation.resume(Unit) }
            }
            manager.flush()
        }
        _account.value = InstagramAccount.LoggedOut
    }

    /** Writes the session as a Netscape cookies file for yt-dlp (one private copy per job). */
    suspend fun writeCookiesFile(dir: File): File? {
        val header = sessionCookies() ?: return null
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = header.split(";").mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            ".instagram.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
        }
        dir.mkdirs()
        val file = File(dir, "cookies.txt")
        file.writeText("# Netscape HTTP Cookie File\n" + lines.joinToString("\n") + "\n")
        return file
    }

    /** "instagram.com/share/…" links only redirect to the real post: follow them (with the account, for private posts). */
    suspend fun resolveLink(url: String): String {
        if (!shareRegex.containsMatchIn(url)) return url
        val headers = buildMap {
            put("User-Agent", userAgent())
            cookieHeader()?.let { put("Cookie", it) }
        }
        val target = withContext(Dispatchers.IO) { runCatching { Http.finalUrl(url, headers) }.getOrNull() }
        return target?.takeIf { shortcode(it) != null || it.contains("/stories/") } ?: url
    }

    /**
     * Reads a post (photo, video or carousel) through Instagram's web API, like yt-dlp does, with the connected
     * account: private posts of followed accounts work too. Returns null when the link is not a post or the API refuses.
     */
    suspend fun fetchPost(url: String): InstagramPost? {
        val code = shortcode(url) ?: return null
        val cookies = sessionCookies() ?: return null
        val headers = apiHeaders(cookies)
        val pk = runCatching { mediaPk(code) }.getOrNull() ?: return null
        for (api in listOf(WEB_API, MOBILE_API)) {
            val response = withContext(Dispatchers.IO) {
                runCatching { Http.get("$api/media/$pk/info/", headers, followRedirects = false) }.getOrNull()
            } ?: continue
            if (isSessionRefused(response)) {
                _account.value = InstagramAccount.Expired
                return null
            }
            if (!response.isSuccess) continue
            parsePost(response.body, code, url)?.let { return it }
        }
        return null
    }

    private fun parsePost(body: String, code: String, url: String): InstagramPost? = runCatching {
        val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0) ?: return@runCatching null
        val carousel = item.optJSONArray("carousel_media")
        val items = if (carousel != null) {
            (0 until carousel.length()).mapNotNull { index -> carousel.optJSONObject(index)?.let { parseMedia(it) } }
        } else {
            listOfNotNull(parseMedia(item))
        }
        if (items.isEmpty()) return@runCatching null
        InstagramPost(
            shortcode = code,
            sourceUrl = url,
            owner = item.optJSONObject("user")?.str("username"),
            caption = item.optJSONObject("caption")?.str("text"),
            items = items,
        )
    }.getOrNull()

    private fun parseMedia(o: JSONObject): InstagramMedia? {
        val image = o.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)
        val thumbnail = image?.str("url") ?: return null
        val video = o.optJSONArray("video_versions")?.optJSONObject(0)
        val videoUrl = video?.str("url")
        return if (videoUrl != null) {
            InstagramMedia(videoUrl, true, thumbnail, video?.num("width")?.toInt(), video?.num("height")?.toInt())
        } else {
            InstagramMedia(thumbnail, false, thumbnail, image?.num("width")?.toInt(), image?.num("height")?.toInt())
        }
    }

    private suspend fun apiHeaders(cookies: String): Map<String, String> {
        val agent = userAgent()
        return buildMap {
            put("X-IG-App-ID", WEB_APP_ID)
            put("X-ASBD-ID", "198387")
            put("X-IG-WWW-Claim", "0")
            put("X-Requested-With", "XMLHttpRequest")
            put("Origin", HOME)
            put("Referer", "$HOME/")
            put("Accept", "*/*")
            put("Cookie", cookies)
            put("User-Agent", agent)
            cookieValue(cookies, "csrftoken")?.let { put("X-CSRFToken", it) }
        }
    }

    /** Instagram no longer accepts the session (logged out elsewhere, security check…). */
    private fun isSessionRefused(response: Http.Response): Boolean {
        if (response.isSuccess) return false
        val body = response.body
        val location = response.location.orEmpty()
        return response.code == 401 ||
            (response.code in 300..399 && (location.contains("/accounts/login") || location.contains("/challenge"))) ||
            body.contains("login_required") ||
            body.contains("\"require_login\":true") ||
            body.contains("checkpoint_required")
    }

    companion object {
        const val HOME = "https://www.instagram.com"
        const val LOGIN_URL = "https://www.instagram.com/accounts/login/"
        private const val WEB_API = "https://www.instagram.com/api/v1"
        private const val MOBILE_API = "https://i.instagram.com/api/v1"
        private const val WEB_APP_ID = "936619743392459"
        private const val FALLBACK_UA =
            "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val shortcodeRegex =
            Regex("""instagram\.com/(?!share/)(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        private val shareRegex = Regex("""instagram\.com/share/""", RegexOption.IGNORE_CASE)

        fun shortcode(url: String): String? = shortcodeRegex.find(url)?.groupValues?.get(1)

        /**
         * The WebView's user agent without its "wv" / "Version/4.0" markers: Instagram then treats the login page
         * like Chrome instead of an embedded browser.
         */
        fun browserUserAgent(webViewAgent: String): String =
            webViewAgent.replace("; wv)", ")").replace(Regex("""\s*Version/\d+(\.\d+)*"""), "")

        private fun cookieValue(cookies: String, name: String): String? =
            Regex("""(?:^|;\s*)$name=([^;]+)""").find(cookies)?.groupValues?.get(1)?.trim()

        /** Numeric media id from a post shortcode (base-64 decoding, as Instagram does). */
        fun mediaPk(shortcode: String): String {
            var n = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (c in shortcode.take(11)) {
                val index = ALPHABET.indexOf(c)
                require(index >= 0) { "Code de publication invalide" }
                n = n.multiply(base).add(BigInteger.valueOf(index.toLong()))
            }
            return n.toString()
        }
    }
}
