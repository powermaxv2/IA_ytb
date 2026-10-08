package com.powermaxv2.ytdownloader.core.compress

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// What the Compresseur does to each kind of file, how small it expects the result to be, and the exact FFmpeg
// command of a video / sound. Pure (no Android types): unit tested in CompressPlanTest.

/** How hard Gulp squeezes. */
enum class Level(val label: String, val detail: String) {
    LIGHT("Léger", "Presque pareil"),
    BALANCED("Équilibré", "Bon compromis"),
    STRONG("Maxi", "Le plus petit"),
}

/** A size the result must fit in (decimal units, like the sizes the screen shows). */
enum class SizeGoal(val label: String, val bytes: Long) {
    DISCORD("Discord", 10_000_000L),
    WHATSAPP("WhatsApp", 16_000_000L),
    EMAIL("Email", 25_000_000L),
}

/** What Gulp can make lighter. [OTHER] files can only go into a ZIP. */
enum class Media(val label: String) {
    IMAGE("Photo"),
    VIDEO("Vidéo"),
    AUDIO("Son"),
    PDF("PDF"),
    OTHER("Fichier"),
    ;

    companion object {
        /** GIFs stay [OTHER]: decoding one keeps only its first frame. */
        fun of(kind: FileKind, name: String): Media = when (kind) {
            FileKind.IMAGE -> if (SafeNames.extensionOf(name).lowercase() in IMAGE_EXT) IMAGE else OTHER
            FileKind.VIDEO -> VIDEO
            FileKind.AUDIO -> AUDIO
            FileKind.PDF -> PDF
            else -> OTHER
        }

        /** Photos the phone decodes and Gulp re-encodes. */
        val IMAGE_EXT = setOf("jpg", "jpeg", "jpe", "png", "webp", "heic", "heif", "bmp", "tif", "tiff", "avif")
    }
}

/** One file alone, or several: each made lighter, or all in one ZIP. */
enum class Mode { EACH, ZIP }

/** What the probe read from a file (all optional: a provider may hide it, a file may be odd). */
data class MediaInfo(
    val durationSec: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val pages: Int? = null,
)

data class CompressSettings(
    val level: Level = Level.BALANCED,
    /** A size to fit in (one file only); null = [level] decides. */
    val goal: SizeGoal? = null,
    /** Videos above 1080p come down to 1080p (720p at [Level.STRONG]). */
    val downscale: Boolean = true,
    /** In a ZIP, photos / videos / sounds / PDF are made lighter first (a ZIP alone gains almost nothing on them). */
    val shrinkBeforeZip: Boolean = true,
)

data class ImagePlan(val maxSide: Int, val quality: Int)

data class PdfPlan(val dpi: Int, val quality: Int)

/** [crf] (quality mode) or [videoKbps] (size mode); [maxHeight] = short side cap, null = unchanged. */
data class VideoPlan(val crf: Int?, val videoKbps: Int?, val maxHeight: Int?, val audioKbps: Int)

data class AudioPlan(val kbps: Int, val ext: String)

object CompressPlan {
    /** Most files at once, and their total size. */
    const val MAX_FILES = 50
    const val MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Pages of a PDF rebuilt at most. */
    const val MAX_PDF_PAGES = 300

    /** Size mode aims a little under the goal: encoders overshoot a bit, and the ZIP / MP4 headers add some. */
    const val GOAL_MARGIN = 0.92

    /** Below this, Gulp keeps the original: the "compressed" file would not really be lighter. */
    const val MIN_GAIN = 0.98

    /** Lowest video bitrate worth making (kbit/s); a goal needing less is out of reach. */
    const val MIN_VIDEO_KBPS = 120
    const val MIN_AUDIO_KBPS = 24

    fun image(level: Level): ImagePlan = when (level) {
        Level.LIGHT -> ImagePlan(4096, 85)
        Level.BALANCED -> ImagePlan(2560, 74)
        Level.STRONG -> ImagePlan(1600, 58)
    }

    fun pdf(level: Level): PdfPlan = when (level) {
        Level.LIGHT -> PdfPlan(150, 80)
        Level.BALANCED -> PdfPlan(110, 66)
        Level.STRONG -> PdfPlan(80, 50)
    }

    /** PDF settings tried in turn to reach a size goal, from the best looking to the lightest. */
    val pdfLadder = listOf(PdfPlan(150, 75), PdfPlan(110, 62), PdfPlan(85, 50), PdfPlan(72, 38), PdfPlan(60, 30))

    fun video(info: MediaInfo, settings: CompressSettings): VideoPlan {
        val short = shortSide(info)
        val goal = settings.goal
        val duration = info.durationSec
        if (goal != null && duration != null && duration > 0) {
            val totalKbps = kbps(goal.bytes, duration)
            val audio = (totalKbps * 0.12).toInt().coerceIn(32, 96)
            val video = max(MIN_VIDEO_KBPS, totalKbps - audio)
            // Fewer pixels for a small bitrate: a sharp 480p beats a blocky 1080p.
            val height = when {
                video >= 2500 -> 1080
                video >= 1000 -> 720
                video >= 450 -> 480
                else -> 360
            }
            return VideoPlan(crf = null, videoKbps = video, maxHeight = capHeight(short, height), audioKbps = audio)
        }
        val (crf, audio, cap) = when (settings.level) {
            Level.LIGHT -> Triple(24, 128, 1080)
            Level.BALANCED -> Triple(28, 96, 1080)
            Level.STRONG -> Triple(32, 64, 720)
        }
        return VideoPlan(crf = crf, videoKbps = null, maxHeight = if (settings.downscale) capHeight(short, cap) else null, audioKbps = audio)
    }

    fun audio(info: MediaInfo, name: String, settings: CompressSettings): AudioPlan {
        val ext = if (SafeNames.extensionOf(name).lowercase() == "mp3") "mp3" else "m4a"
        val goal = settings.goal
        val duration = info.durationSec
        val kbps = if (goal != null && duration != null && duration > 0) {
            kbps(goal.bytes, duration).coerceIn(MIN_AUDIO_KBPS, 192)
        } else {
            when (settings.level) {
                Level.LIGHT -> 160
                Level.BALANCED -> 112
                Level.STRONG -> 64
            }
        }
        return AudioPlan(kbps, ext)
    }

    /** Whether [goal] can be reached for a media of [durationSec] (null duration: Gulp tries anyway). */
    fun goalReachable(media: Media, durationSec: Double?, goal: SizeGoal): Boolean {
        if (durationSec == null || durationSec <= 0) return true
        val floor = when (media) {
            Media.VIDEO -> MIN_VIDEO_KBPS + 32
            Media.AUDIO -> MIN_AUDIO_KBPS
            else -> return true
        }
        return kbps(goal.bytes, durationSec) >= floor
    }

    /** Longest media that fits in [goal] (seconds), for the "trop long" message. */
    fun maxSeconds(media: Media, goal: SizeGoal): Int {
        val floor = if (media == Media.VIDEO) MIN_VIDEO_KBPS + 32 else MIN_AUDIO_KBPS
        return (goal.bytes * GOAL_MARGIN * 8 / 1000 / floor).toInt()
    }

    /**
     * Expected size of the result (bytes), or null when Gulp cannot guess. Never above [size]: Gulp keeps the
     * original when it cannot do lighter.
     */
    fun estimate(media: Media, size: Long?, info: MediaInfo, name: String, settings: CompressSettings): Long? {
        val goal = settings.goal
        val guess: Long? = when (media) {
            Media.VIDEO -> {
                val d = info.durationSec
                val plan = video(info, settings)
                when {
                    d == null -> size?.let { (it * levelRatio(settings.level)).roundToLong() }
                    plan.videoKbps != null -> ((plan.videoKbps + plan.audioKbps) * 1000.0 / 8 * d).roundToLong()
                    else -> ((crfKbps(plan.crf ?: 28, plan.maxHeight ?: shortSide(info) ?: 1080) + plan.audioKbps) * 1000.0 / 8 * d).roundToLong()
                }
            }
            Media.AUDIO -> info.durationSec?.let { (audio(info, name, settings).kbps * 1000.0 / 8 * it).roundToLong() }
                ?: size?.let { (it * levelRatio(settings.level)).roundToLong() }
            Media.IMAGE -> {
                val w = info.width
                val h = info.height
                if (w != null && h != null && w > 0 && h > 0) {
                    val plan = image(settings.level)
                    val scale = min(1.0, plan.maxSide.toDouble() / max(w, h))
                    val pixels = w * scale * h * scale
                    (pixels * bytesPerPixel(plan.quality)).roundToLong()
                } else {
                    size?.let { (it * levelRatio(settings.level)).roundToLong() }
                }
            }
            Media.PDF -> info.pages?.let { pages ->
                val plan = pdf(settings.level)
                // An A4 page at plan.dpi.
                val pixels = (8.27 * plan.dpi) * (11.69 * plan.dpi)
                (pages * (pixels * bytesPerPixel(plan.quality) * 0.8 + 600)).roundToLong()
            }
            Media.OTHER -> size
        }
        val aimed = if (goal != null && media != Media.OTHER) min(guess ?: goal.bytes, (goal.bytes * GOAL_MARGIN).roundToLong()) else guess
        return when {
            aimed == null -> null
            size == null -> aimed
            else -> min(aimed, size)
        }
    }

    /** Expected size of a ZIP of [items]: each file lightened first or not, then deflated. */
    fun estimateZip(items: List<ZipEstimateItem>, settings: CompressSettings): Long? {
        var total = 22L
        for (item in items) {
            val size = item.size ?: return null
            val inner = if (settings.shrinkBeforeZip && item.media != Media.OTHER) {
                estimate(item.media, size, item.info, item.name, settings.copy(goal = null)) ?: size
            } else {
                size
            }
            val deflated = if (alreadyCompressed(item.name) || item.media != Media.OTHER) inner else (inner * 0.35).roundToLong()
            total += deflated + 76 + 2L * item.name.length
        }
        return total
    }

    data class ZipEstimateItem(val media: Media, val name: String, val size: Long?, val info: MediaInfo)

    /** Formats that are already compressed: stored as is in a ZIP (deflating them only costs time). */
    fun alreadyCompressed(name: String): Boolean = SafeNames.extensionOf(name).lowercase() in packedExt

    private val packedExt = setOf(
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "avif",
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp",
        "mp3", "m4a", "aac", "ogg", "opus", "flac",
        "zip", "7z", "rar", "gz", "xz", "bz2", "apk", "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "pdf",
    )

    /** Name of the lighter file: "Vacances.mov" → "Vacances (compressé).mp4". */
    fun outputName(inputName: String, ext: String): String {
        val base = SafeNames.baseOf(inputName).ifBlank { "fichier" }
        return SafeNames.withExtension("$base (compressé).x", ext)
    }

    /** Extension of the result: photos become JPG (WebP when they have transparency), videos MP4. */
    fun outputExt(media: Media, name: String, hasAlpha: Boolean = false): String = when (media) {
        Media.IMAGE -> if (hasAlpha) "webp" else "jpg"
        Media.VIDEO -> "mp4"
        Media.AUDIO -> if (SafeNames.extensionOf(name).lowercase() == "mp3") "mp3" else "m4a"
        Media.PDF -> "pdf"
        Media.OTHER -> SafeNames.extensionOf(name).ifEmpty { "bin" }
    }

    /** FFmpeg arguments for a video; paths absolute and inside [root], like [FfmpegArgs.build]. */
    fun videoArgs(root: String, input: String, output: String, plan: VideoPlan): List<String> {
        requirePaths(root, input, output)
        val rate = plan.videoKbps?.let { listOf("-b:v", "${it}k", "-maxrate", "${it * 13 / 10}k", "-bufsize", "${it * 2}k") }
            ?: listOf("-crf", "${plan.crf ?: 28}")
        return FfmpegArgs.prefix(input) +
            listOf("-map", "0:v:0", "-map", "0:a:0?", "-vf", scale(plan.maxHeight)) +
            listOf("-c:v", "libx264", "-preset", "veryfast") + rate + listOf("-pix_fmt", "yuv420p") +
            listOf("-c:a", "aac", "-b:a", "${plan.audioKbps}k", "-ac", "2", "-movflags", "+faststart", "-map_metadata", "-1") +
            output
    }

    /** FFmpeg arguments for a sound (MP3 stays MP3, anything else becomes AAC in M4A). */
    fun audioArgs(root: String, input: String, output: String, plan: AudioPlan): List<String> {
        requirePaths(root, input, output)
        val codec = if (plan.ext == "mp3") listOf("-c:a", "libmp3lame") else listOf("-c:a", "aac", "-movflags", "+faststart")
        return FfmpegArgs.prefix(input) + listOf("-map", "0:a:0", "-vn") + codec + listOf("-b:a", "${plan.kbps}k") + output
    }

    /** Short side brought down to [height] (portrait videos too), never up; both sides even. */
    fun scale(height: Int?): String {
        if (height == null) return "scale=trunc(iw/2)*2:trunc(ih/2)*2"
        return "scale='if(gt(iw,ih),-2,trunc(min($height,iw)/2)*2)':'if(gt(iw,ih),trunc(min($height,ih)/2)*2,-2)'"
    }

    private fun requirePaths(root: String, input: String, output: String) {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
    }

    private fun kbps(bytes: Long, seconds: Double): Int = (bytes * GOAL_MARGIN * 8 / 1000 / seconds).toInt()

    private fun shortSide(info: MediaInfo): Int? {
        val w = info.width ?: return null
        val h = info.height ?: return null
        return min(w, h).takeIf { it > 0 }
    }

    /** [cap] when the video is bigger than that, else null (no scaling needed). */
    private fun capHeight(short: Int?, cap: Int): Int? = if (short == null || short > cap) cap else null

    /** Typical H.264 veryfast bitrate (kbit/s) at a CRF for a short side, for the estimate only. */
    private fun crfKbps(crf: Int, short: Int): Double {
        val at1080 = when {
            crf <= 24 -> 5200.0
            crf <= 28 -> 2800.0
            else -> 1500.0
        }
        val pixels = (short.toDouble() / 1080).let { it * it }
        return at1080 * pixels
    }

    private fun bytesPerPixel(quality: Int): Double = when {
        quality >= 85 -> 0.30
        quality >= 74 -> 0.19
        quality >= 60 -> 0.13
        else -> 0.09
    }

    private fun levelRatio(level: Level): Double = when (level) {
        Level.LIGHT -> 0.7
        Level.BALANCED -> 0.4
        Level.STRONG -> 0.2
    }
}
