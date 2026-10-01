package com.powermaxv2.ytdownloader.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// MARK: - Lenient JSON access (yt-dlp JSON is not always strictly typed)

internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }
}

internal fun JSONObject.num(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

internal inline fun JSONArray.forEachObject(action: (index: Int, JSONObject) -> Unit) {
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        action(i, item)
    }
}

// MARK: - yt-dlp info

/** Counters Facebook puts before its titles ("9.8K views · 341 reactions | Title"); valid for Kotlin and yt-dlp (Python). */
internal const val FACEBOOK_COUNTERS =
    """(?i)^(?:[\d.,]+\s?[KMB]?\s(?:views?|reactions?|comments?|shares?|plays?)\s*·?\s*)+\|\s*(?=\S)"""
private val facebookCounters = Regex(FACEBOOK_COUNTERS)

data class VideoFormat(
    val formatId: String,
    val ext: String?,
    val vcodec: String?,
    val acodec: String?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val tbr: Double?,
    val filesize: Double?,
    val dynamicRange: String?,
) {
    /** Direct links often carry no codec or height: the file extension then tells whether it is a video. */
    val hasVideo: Boolean
        get() = when {
            ext == "mhtml" -> false
            vcodec != null -> vcodec != "none"
            else -> height != null || ext in videoExtensions
        }

    val hasAudio: Boolean
        get() = acodec?.let { it != "none" } ?: false

    /** False only when the format is known to be silent: progressive files that list no codec usually carry sound. */
    val mayHaveAudio: Boolean
        get() = acodec != "none"

    val isHdr: Boolean
        get() = dynamicRange?.let { !it.equals("SDR", ignoreCase = true) } ?: false

    companion object {
        private val videoExtensions = setOf(
            "mp4", "m4v", "mov", "webm", "mkv", "flv", "avi", "ts", "3gp", "mpg", "mpeg", "ogv", "wmv", "unknown_video",
        )
        internal val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg")

        fun from(o: JSONObject) = VideoFormat(
            formatId = o.str("format_id") ?: UUID.randomUUID().toString(),
            ext = o.str("ext"),
            vcodec = o.str("vcodec"),
            acodec = o.str("acodec"),
            width = o.num("width")?.toInt(),
            height = o.num("height")?.toInt(),
            fps = o.num("fps"),
            tbr = o.num("tbr"),
            filesize = o.num("filesize") ?: o.num("filesize_approx"),
            dynamicRange = o.str("dynamic_range"),
        )
    }
}

data class VideoInfo(
    val id: String,
    val title: String,
    val uploader: String?,
    val duration: Double?,
    val thumbnail: String?,
    val viewCount: Double?,
    val webpageUrl: String?,
    val formats: List<VideoFormat>,
) {
    val bestAudio: VideoFormat?
        get() = formats.filter { it.hasAudio && !it.hasVideo }.maxByOrNull { it.tbr ?: 0.0 }

    fun estimatedSize(format: VideoFormat): Double? {
        format.filesize?.takeIf { it > 0 }?.let { return it }
        val tbr = format.tbr ?: return null
        val duration = duration ?: return null
        return tbr * 1000 / 8 * duration
    }

    val audioEstimatedSize: Double? get() = bestAudio?.let { estimatedSize(it) }

    /** One option per video height that comes with sound, best first; a single "Max" option when no height is known. */
    val qualityOptions: List<QualityOption> by lazy {
        val audioSize = audioEstimatedSize ?: 0.0
        val sized = formats.filter { it.hasVideo && it.height != null }
        // Facebook lists silent video-only heights next to its sound files, which have no height.
        val withSound = if (bestAudio != null) sized else sized.filter { it.mayHaveAudio }
        val offered = when {
            withSound.isNotEmpty() -> withSound
            formats.any { it.hasVideo && it.height == null && it.mayHaveAudio } -> emptyList()
            else -> sized
        }
        val byHeight = offered
            .groupBy { it.height ?: 0 }
            .map { (height, group) ->
                val best = group.maxByOrNull { it.tbr ?: 0.0 }
                val width = group.mapNotNull { it.width }.maxOrNull() ?: height
                val fps = (group.mapNotNull { it.fps }.maxOrNull() ?: 0.0).roundToInt()
                val size = best?.let { b -> estimatedSize(b)?.let { it + if (b.hasAudio) 0.0 else audioSize } }
                QualityOption(
                    height = height,
                    displayP = minOf(width, height),
                    maxFps = fps,
                    hdr = group.any { it.isHdr },
                    estimatedSize = size,
                )
            }
            .sortedByDescending { it.height }
        if (byHeight.isNotEmpty()) return@lazy byHeight
        val unsized = formats.filter { it.hasVideo && it.mayHaveAudio }.ifEmpty { formats.filter { it.hasVideo } }
        if (unsized.isEmpty()) return@lazy emptyList()
        val best = unsized.maxByOrNull { it.tbr ?: 0.0 }
        val size = best?.let { b -> estimatedSize(b)?.let { it + if (b.hasAudio) 0.0 else audioSize } }
        listOf(QualityOption(height = null, displayP = 0, maxFps = 0, hdr = false, estimatedSize = size))
    }

    companion object {
        fun from(o: JSONObject): VideoInfo {
            val formats = mutableListOf<VideoFormat>()
            o.optJSONArray("formats")?.forEachObject { _, f -> formats += VideoFormat.from(f) }
            // Single-file sources (Snapchat, direct links) have no format list: the file is described at the top level.
            if (formats.isEmpty() && o.str("url") != null && o.str("ext") !in VideoFormat.imageExtensions) {
                formats += VideoFormat.from(o)
            }
            val title = o.str("title") ?: o.str("fulltitle") ?: "Sans titre"
            val fromFacebook = o.str("extractor_key")?.startsWith("Facebook") == true
            return VideoInfo(
                id = o.str("id") ?: "",
                title = if (fromFacebook) title.replaceFirst(facebookCounters, "") else title,
                uploader = o.str("channel") ?: o.str("uploader"),
                duration = o.num("duration"),
                thumbnail = o.str("thumbnail") ?: lastThumbnail(o),
                viewCount = o.num("view_count"),
                webpageUrl = o.str("webpage_url"),
                formats = formats,
            )
        }

        internal fun lastThumbnail(o: JSONObject): String? {
            val thumbs = o.optJSONArray("thumbnails") ?: return null
            for (i in thumbs.length() - 1 downTo 0) {
                thumbs.optJSONObject(i)?.str("url")?.let { return it }
            }
            return null
        }
    }
}

// MARK: - Playlists (and Instagram carousels)

data class PlaylistEntry(
    val id: String,
    val title: String,
    /** Direct link of the video, or null when it can only be reached through the playlist. */
    val url: String?,
    /** 1-based position in the playlist, used with `--playlist-items` when [url] is null. */
    val position: Int,
    val duration: Double?,
    val thumbnail: String?,
) {
    /** Private / deleted videos still appear in playlists but cannot be downloaded. */
    val isUnavailable: Boolean
        get() = title.lowercase(Locale.ROOT) in setOf("[private video]", "[deleted video]", "[unavailable video]")
}

data class PlaylistInfo(
    val id: String,
    val title: String,
    val uploader: String?,
    val sourceUrl: String,
    val entries: List<PlaylistEntry>,
) {
    companion object {
        fun from(o: JSONObject, sourceUrl: String): PlaylistInfo {
            val seen = HashSet<String>()
            val entries = mutableListOf<PlaylistEntry>()
            o.optJSONArray("entries")?.forEachObject { index, e ->
                // Skip nested playlists (e.g. channel tabs): only single videos are listed.
                val ieKey = e.str("ie_key")
                if (e.str("_type") == "playlist" || ieKey == "YoutubeTab") return@forEachObject
                val id = e.str("id") ?: e.str("url") ?: "item-$index"
                if (!seen.add(id)) return@forEachObject
                val isYouTube = ieKey == "Youtube"
                val type = e.str("_type")
                val watchUrl = "https://www.youtube.com/watch?v=$id"
                val direct = when {
                    // Flat reference to another page (YouTube playlists…).
                    type == "url" || type == "url_transparent" ->
                        e.str("url")?.takeIf { it.startsWith("http") } ?: if (isYouTube) watchUrl else null
                    isYouTube -> watchUrl
                    // Item embedded in the page itself (Instagram carousel…): reached by its position.
                    else -> null
                }
                val thumbnail = if (isYouTube && e.str("id") != null) {
                    "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                } else {
                    e.str("thumbnail") ?: VideoInfo.lastThumbnail(e)
                }
                entries += PlaylistEntry(
                    id = id,
                    title = e.str("title") ?: direct ?: "Élément ${index + 1}",
                    url = direct,
                    position = index + 1,
                    duration = e.num("duration"),
                    thumbnail = thumbnail,
                )
            }
            return PlaylistInfo(
                id = o.str("id") ?: "",
                title = o.str("title") ?: "Playlist",
                uploader = o.str("channel") ?: o.str("uploader"),
                sourceUrl = o.str("webpage_url") ?: sourceUrl,
                entries = entries,
            )
        }
    }
}

sealed interface MediaInfo {
    data class Video(val info: VideoInfo, val sourceUrl: String) : MediaInfo
    data class Playlist(val info: PlaylistInfo) : MediaInfo
    data class Photos(val post: InstagramPost) : MediaInfo
}

/** Fixed quality ladder used for playlists (formats are not known per video in advance). */
data class PlaylistQuality(val height: Int?) {
    val title: String get() = height?.let { "${it}p" } ?: "Max"

    val badge: String
        get() = when {
            height == null -> "MAX"
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "Full HD"
            height >= 720 -> "HD"
            else -> "SD"
        }

    val detail: String get() = if (height == null) "Meilleure qualité" else "Ou la meilleure en dessous"
    val summaryLabel: String get() = height?.let { "≤ ${it}p" } ?: "Max"

    companion object {
        val ladder = listOf(null, 2160, 1440, 1080, 720, 480, 360).map { PlaylistQuality(it) }
    }
}

data class QualityOption(
    /** Value used in the yt-dlp `height<=` filter; null when the source does not tell its height. */
    val height: Int?,
    /** Short side of the frame — what people call "1080p" (handles vertical videos like Reels). */
    val displayP: Int,
    val maxFps: Int,
    val hdr: Boolean,
    val estimatedSize: Double?,
) {
    val label: String get() = if (height == null) "Max" else "${displayP}p"

    val badge: String
        get() = when {
            height == null -> "Original"
            displayP >= 4320 -> "8K"
            displayP >= 2160 -> "4K"
            displayP >= 1440 -> "2K"
            displayP >= 1080 -> "Full HD"
            displayP >= 720 -> "HD"
            else -> "SD"
        }

    val detail: String
        get() = buildList {
            if (maxFps > 0) add("$maxFps fps")
            if (hdr) add("HDR")
            estimatedSize?.let { add("~" + Fmt.bytes(it)) }
        }.ifEmpty { listOf("—") }.joinToString(" · ")
}

// MARK: - Download options

enum class MediaMode(val title: String) {
    VIDEO("Vidéo + son"),
    AUDIO("Audio seul"),
}

enum class VideoContainer(val title: String, val subtitle: String, val fileExtension: String) {
    MP4("MP4", "Qualité maximale (VP9 / AV1 au-delà de 1080p).", "mp4"),
    COMPAT("MP4 · H.264", "Lisible partout, même sur les vieux téléphones (souvent ≤ 1080p).", "mp4"),
    MKV("MKV", "Qualité maximale — conteneur le plus flexible.", "mkv"),
}

enum class AudioFormat(val title: String, val badge: String, val subtitle: String, val ext: String) {
    M4A("M4A", "AAC", "Compatible partout", "m4a"),
    MP3("MP3", "V0", "Compatible partout, haute qualité", "mp3"),
    OPUS("Opus", "Original", "Flux d'origine, sans réencodage", "opus"),
    FLAC("FLAC", "Lossless", "Sans perte, fichier plus lourd", "flac"),
}

/** Options shared by every yt-dlp call: no user config and no plugins can change its behaviour. */
val commonArguments = listOf("--ignore-config", "--no-plugin-dirs")

data class DownloadRequest(
    val url: String,
    val mode: MediaMode,
    val height: Int?,
    val qualityLabel: String,
    val container: VideoContainer,
    val audioFormat: AudioFormat,
    val embedThumbnail: Boolean,
    val embedMetadata: Boolean,
    val embedChapters: Boolean,
    val subtitles: Boolean,
    val subtitleLangs: String,
    /** Position in a playlist/carousel when the item has no link of its own. */
    val playlistItem: Int? = null,
) {
    val summary: String
        get() = when (mode) {
            MediaMode.VIDEO -> "$qualityLabel · ${container.title}"
            MediaMode.AUDIO -> "Audio · ${audioFormat.title}"
        }

    fun arguments(outputDir: File, cookies: File?): List<String> {
        val args = commonArguments.toMutableList()
        args += listOf("--newline", "--progress", "--no-colors", "--no-mtime", "--no-simulate")
        args += if (playlistItem != null) {
            listOf("--yes-playlist", "--playlist-items", playlistItem.toString())
        } else {
            listOf("--no-playlist")
        }
        args += listOf(
            "--concurrent-fragments", "4",
            "--progress-template",
            "download:[PROG]%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s|%(info.format_id)s|%(info.vcodec)s|%(info.acodec)s",
            "--progress-template",
            "postprocess:[POST]%(progress.postprocessor)s|%(progress.status)s",
            // Chosen formats ("137+140" = video then audio): tells the progress bar how many files to expect.
            "--print", "video:[FMT]%(format_id)s",
            "--print", "after_move:[FILE]%(filepath)s",
        )

        when (mode) {
            MediaMode.AUDIO -> {
                val selector = if (audioFormat == AudioFormat.M4A) "ba[ext=m4a]/ba/b" else "ba/b"
                args += listOf("-f", selector, "-x", "--audio-format", audioFormat.ext, "--audio-quality", "0")
                args += listOf("-o", "%(title).150B.%(ext)s")
            }
            MediaMode.VIDEO -> {
                val h = height?.let { "[height<=$it]" } ?: ""
                val selector = when (container) {
                    VideoContainer.MP4 -> "bv*$h+ba[ext=m4a]/bv*$h+ba/b$h/bv*+ba/b"
                    VideoContainer.MKV -> "bv*$h+ba/b$h/bv*+ba/b"
                    VideoContainer.COMPAT ->
                        "bv*[vcodec^=avc]$h+ba[ext=m4a]/b[ext=mp4]$h/bv*$h+ba[ext=m4a]/bv*$h+ba/b$h/bv*+ba/b"
                }
                args += listOf("-f", selector, "--merge-output-format", container.fileExtension)
                // " [1080p]" only when the height is known: direct links and Snapchat do not tell it.
                args += listOf("-o", "%(title).150B%(height& [{}p]|)s.%(ext)s")
                if (embedChapters) args += "--embed-chapters"
                if (subtitles) {
                    val langs = subtitleLangs.trim().ifEmpty { "fr,en" }
                    args += listOf("--write-subs", "--sub-langs", langs, "--embed-subs")
                }
            }
        }

        if (Links.platform(url) == Platform.FACEBOOK) args += listOf("--replace-in-metadata", "title", FACEBOOK_COUNTERS, "")
        if (embedMetadata) args += "--embed-metadata"
        if (embedThumbnail) args += "--embed-thumbnail"
        if (cookies != null) args += listOf("--cookies", cookies.absolutePath)
        args += listOf("-P", outputDir.absolutePath, "--", url)
        return args
    }
}

// MARK: - Formatting helpers

object Fmt {
    fun bytes(value: Double): String {
        val units = listOf("o", "Ko", "Mo", "Go", "To")
        var v = value
        var i = 0
        while (v >= 1000 && i < units.lastIndex) {
            v /= 1000
            i++
        }
        return if (i == 0) "${v.toInt()} ${units[i]}" else String.format(Locale.FRANCE, "%.1f %s", v, units[i])
    }

    fun duration(seconds: Double): String {
        val t = seconds.roundToInt()
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun views(n: Double): String = when {
        n >= 1_000_000_000 -> String.format(Locale.FRANCE, "%.1f Md de vues", n / 1_000_000_000)
        n >= 1_000_000 -> String.format(Locale.FRANCE, "%.1f M de vues", n / 1_000_000)
        n >= 1_000 -> String.format(Locale.FRANCE, "%.0f k vues", n / 1_000)
        else -> "${n.toInt()} vues"
    }

    fun speed(bytesPerSecond: Double): String = bytes(bytesPerSecond) + "/s"

    fun eta(seconds: Double): String = duration(seconds)
}
