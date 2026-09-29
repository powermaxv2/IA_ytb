package com.powermaxv2.ytdownloader.core

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val title: String
        get() = caption?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80)
            ?: owner?.let { "Publication de @$it" }
            ?: "Publication Instagram"
}

/**
 * Instagram account connection. The login happens in a WebView on instagram.com: the app never sees the
 * password, only the session cookies, which stay in the app's private storage.
 */
class InstagramSession {
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    suspend fun cookieHeader(): String? = withContext(Dispatchers.Main) {
        runCatching { CookieManager.getInstance().getCookie(HOME) }.getOrNull()
    }

    suspend fun refresh(): Boolean {
        val loggedIn = cookieHeader()?.contains("sessionid=") == true
        _loggedIn.value = loggedIn
        return loggedIn
    }

    suspend fun logout() {
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            suspendCancellableCoroutine<Unit> { continuation ->
                manager.removeAllCookies { continuation.resume(Unit) }
            }
            manager.flush()
        }
        _loggedIn.value = false
    }

    /** Writes the session as a Netscape cookies file for yt-dlp (one private copy per job). */
    suspend fun writeCookiesFile(dir: File): File? {
        val header = cookieHeader()?.takeIf { it.contains("sessionid=") } ?: return null
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

    /**
     * Reads a post (photo, video or carousel) through Instagram's web API, like yt-dlp does.
     * Requires a connected account. Returns null when the link is not a post or the API refuses.
     */
    suspend fun fetchPost(url: String): InstagramPost? {
        val code = shortcode(url) ?: return null
        val cookies = cookieHeader()?.takeIf { it.contains("sessionid=") } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val csrf = Regex("csrftoken=([^;]+)").find(cookies)?.groupValues?.get(1)?.trim()
                val headers = buildMap {
                    put("X-IG-App-ID", "936619743392459")
                    put("X-ASBD-ID", "198387")
                    put("X-IG-WWW-Claim", "0")
                    put("Origin", "https://www.instagram.com")
                    put("Referer", "https://www.instagram.com/")
                    put("Accept", "*/*")
                    put("Cookie", cookies)
                    put("User-Agent", BROWSER_UA)
                    if (csrf != null) put("X-CSRFToken", csrf)
                }
                val json = JSONObject(Http.getText("https://i.instagram.com/api/v1/media/${mediaPk(code)}/info/", headers))
                val item = json.optJSONArray("items")?.optJSONObject(0) ?: return@runCatching null
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
        }
    }

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

    companion object {
        const val HOME = "https://www.instagram.com"
        const val LOGIN_URL = "https://www.instagram.com/accounts/login/"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val shortcodeRegex =
            Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

        fun shortcode(url: String): String? = shortcodeRegex.find(url)?.groupValues?.get(1)

        fun isPostLink(url: String): Boolean = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?p/""").containsMatchIn(url)

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
