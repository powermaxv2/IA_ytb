package com.powermaxv2.ytdownloader.core

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

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

/**
 * Public Instagram posts, read like a logged-out visitor. Since 1.4.0 the app never logs in to Instagram:
 * Instagram locked an account whose session the app was using.
 */
object Instagram {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val shortcodeRegex =
        Regex("""instagram\.com/(?!share/)(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val shareRegex = Regex("""instagram\.com/(?:share|s)/""", RegexOption.IGNORE_CASE)
    private val storyRegex = Regex("""instagram\.com/stories/""", RegexOption.IGNORE_CASE)
    private val shortLinkRegex = Regex("""instagram\.com/s/([A-Za-z0-9_=-]+)""", RegexOption.IGNORE_CASE)

    fun shortcode(url: String): String? = shortcodeRegex.find(url)?.groupValues?.get(1)

    /** Story or highlight, also as a "instagram.com/s/…" share link (a highlight id in base 64). */
    fun isStory(url: String): Boolean {
        if (storyRegex.containsMatchIn(url)) return true
        val token = shortLinkRegex.find(url)?.groupValues?.get(1) ?: return false
        return runCatching { String(Base64.decode(token, Base64.URL_SAFE), Charsets.UTF_8).startsWith("highlight:") }
            .getOrDefault(false)
    }

    /**
     * Deletes what the Instagram login of versions before 1.4.0 left on the phone: the WebView's cookies (the session),
     * storage and cache. Nothing in the app opens a WebView any more, so the files are not in use.
     */
    fun purgeAccountData(context: Context) {
        File(context.applicationInfo.dataDir).listFiles { file -> file.name.startsWith("app_webview") }
            ?.forEach { it.deleteRecursively() }
        listOf("WebView", "org.chromium.android_webview").forEach { File(context.cacheDir, it).deleteRecursively() }
    }

    /** "instagram.com/share/…" and "/s/…" links only redirect to the real post: follow them. */
    suspend fun resolveLink(url: String): String {
        if (!shareRegex.containsMatchIn(url) || isStory(url)) return url
        val target = withContext(Dispatchers.IO) {
            runCatching { Http.finalUrl(url, mapOf("User-Agent" to BROWSER_UA)) }.getOrNull()
        }
        return target?.takeIf { shortcode(it) != null || storyRegex.containsMatchIn(it) } ?: url
    }

    /**
     * The post as a list of photos and videos when it holds at least one photo, from yt-dlp's answer (yt-dlp only
     * downloads videos, but it describes the photos too). Null for videos only: they keep the usual quality choice.
     */
    fun photoPost(json: JSONObject, url: String): InstagramPost? {
        val nodes = json.optJSONArray("entries")?.let { entries ->
            buildList { entries.forEachObject { _, entry -> add(entry) } }
        } ?: listOf(json)
        val items = nodes.mapNotNull(::media)
        if (items.all { it.isVideo }) return null
        return InstagramPost(
            // Part of the file names: the id given by yt-dlp is reduced to safe characters.
            shortcode = shortcode(url) ?: json.str("id")?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(64)?.trim('.') ?: "post",
            sourceUrl = url,
            owner = json.str("channel") ?: nodes.firstNotNullOfOrNull { it.str("channel") },
            caption = json.str("description") ?: nodes.firstNotNullOfOrNull { it.str("description") },
            items = items,
        )
    }

    private fun media(entry: JSONObject): InstagramMedia? {
        val thumbnail = entry.str("thumbnail")?.takeIf { it.startsWith("https://") }
        val formats = buildList { entry.optJSONArray("formats")?.forEachObject { _, format -> add(format) } }
        if (formats.isEmpty()) return thumbnail?.let { InstagramMedia(it, false, it, null, null) }
        // One file with its sound: Instagram's own versions, not the DASH streams (video and audio apart).
        val video = formats
            .filter { it.str("format_id")?.startsWith("dash") != true && it.str("vcodec") != "none" }
            .filter { it.str("url")?.startsWith("https://") == true }
            .maxByOrNull { it.num("height") ?: 0.0 }
            ?: return null
        return InstagramMedia(
            url = video.str("url") ?: return null,
            isVideo = true,
            thumbnail = thumbnail.orEmpty(),
            width = video.num("width")?.toInt(),
            height = video.num("height")?.toInt(),
        )
    }
}
