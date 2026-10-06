package com.powermaxv2.ytdownloader.core.convert

import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.SafeNames

// What the Convertisseur turns into what, and the exact FFmpeg command of each conversion. Pure (no Android
// types): unit tested in FormatsTest / FfmpegArgsTest.

/** What the user gave the converter, as far as conversions go. */
enum class Category(val label: String) {
    VIDEO("Vidéo"),
    AUDIO("Son"),
    IMAGE("Photo"),
    PDF("PDF"),
    MARKDOWN("Markdown"),
    CSV("CSV"),
    JSON("JSON"),
    ;

    companion object {
        /** The category of a detected [FileKind], or null when Gulp does not convert that kind of file. */
        fun of(kind: FileKind): Category? = when (kind) {
            FileKind.VIDEO -> VIDEO
            FileKind.AUDIO -> AUDIO
            FileKind.IMAGE -> IMAGE
            FileKind.PDF -> PDF
            FileKind.MARKDOWN -> MARKDOWN
            FileKind.CSV -> CSV
            FileKind.JSON -> JSON
            else -> null
        }
    }
}

/** Groups of target tiles on screen. */
enum class Family(val label: String) {
    VIDEO("Vidéo"),
    AUDIO("Audio"),
    IMAGE("Image"),
    DOCUMENT("Document"),
}

/** An output format. [ext] is the saved file's extension. */
enum class Target(val ext: String, val label: String, val family: Family, val detail: String) {
    MP4("mp4", "MP4", Family.VIDEO, "H.264 · AAC"),
    MKV("mkv", "MKV", Family.VIDEO, "H.265 · AAC"),
    WEBM("webm", "WebM", Family.VIDEO, "VP9 · Opus"),
    MOV("mov", "MOV", Family.VIDEO, "H.264 · AAC"),
    AVI("avi", "AVI", Family.VIDEO, "MPEG-4 · MP3"),
    GIF("gif", "GIF", Family.VIDEO, "Animé, muet, 30 s"),
    MP3("mp3", "MP3", Family.AUDIO, "Lu partout"),
    M4A("m4a", "M4A", Family.AUDIO, "AAC"),
    OPUS("opus", "Opus", Family.AUDIO, "Très léger"),
    OGG("ogg", "OGG", Family.AUDIO, "Vorbis"),
    FLAC("flac", "FLAC", Family.AUDIO, "Sans perte"),
    WAV("wav", "WAV", Family.AUDIO, "Non compressé"),
    JPG("jpg", "JPG", Family.IMAGE, "Photo"),
    PNG("png", "PNG", Family.IMAGE, "Sans perte"),
    WEBP("webp", "WebP", Family.IMAGE, "Léger"),
    BMP("bmp", "BMP", Family.IMAGE, "Non compressé"),
    TIFF("tiff", "TIFF", Family.IMAGE, "Impression"),
    PDF("pdf", "PDF", Family.DOCUMENT, "Page A4"),
    HTML("html", "HTML", Family.DOCUMENT, "Page web"),
    JSON("json", "JSON", Family.DOCUMENT, "Liste d'objets"),
    CSV("csv", "CSV", Family.DOCUMENT, "Tableur"),
}

/** How a conversion is done. */
enum class Method {
    /** FFmpeg reads the media file and writes the target ([FfmpegArgs]). */
    FFMPEG,

    /** Image decoded on the phone, written with Bitmap.compress (JPG, PNG, WebP). */
    BITMAP,

    /** Image decoded on the phone, saved as PNG, then FFmpeg writes BMP / TIFF. */
    BITMAP_FFMPEG,

    /** Photos laid out on A4 pages (PdfDocument). */
    IMAGES_TO_PDF,

    /** One image per PDF page (PdfRenderer). */
    PDF_TO_IMAGES,
    MARKDOWN_TO_HTML,
    CSV_TO_JSON,
    JSON_TO_CSV,
}

/** Video size: the short side is brought down to [height] pixels (never up). */
enum class Resolution(val label: String, val height: Int?) {
    ORIGINAL("Originale", null),
    P2160("2160p", 2160),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
}

/** Compression level; each encoder has its own scale. */
enum class Quality(
    val label: String,
    /** libx264 -crf. */
    val x264Crf: Int,
    /** libx265 -crf. */
    val x265Crf: Int,
    /** libvpx-vp9 -crf (with -b:v 0). */
    val vp9Crf: Int,
    /** mpeg4 (MPEG-4 part 2) -q:v. */
    val mpeg4Q: Int,
    /** GIF frames per second and longest side. */
    val gifFps: Int,
    val gifSide: Int,
    /** JPG / WebP quality (0..100). */
    val imageQuality: Int,
) {
    HIGH("Haute", x264Crf = 18, x265Crf = 22, vp9Crf = 28, mpeg4Q = 3, gifFps = 15, gifSide = 640, imageQuality = 92),
    MEDIUM("Moyenne", x264Crf = 23, x265Crf = 26, vp9Crf = 33, mpeg4Q = 5, gifFps = 12, gifSide = 480, imageQuality = 82),
    LIGHT("Légère", x264Crf = 28, x265Crf = 30, vp9Crf = 38, mpeg4Q = 8, gifFps = 10, gifSide = 360, imageQuality = 68),
}

/** Bitrate of the lossy audio codecs (MP3, AAC, Opus, Vorbis), alone or in a video. */
enum class AudioBitrate(val kbps: Int) {
    K320(320),
    K192(192),
    K128(128),
    ;

    val label: String get() = "$kbps kbps"
}

/** The user's choices; each target only reads the ones it uses ([Formats.usesResolution]…). */
data class ConvertOptions(
    val resolution: Resolution = Resolution.ORIGINAL,
    val quality: Quality = Quality.MEDIUM,
    val audioBitrate: AudioBitrate = AudioBitrate.K192,
    /** MKV only: copy the streams as they are (fast, no quality loss, nothing re-encoded). */
    val copyStreams: Boolean = false,
)

object Formats {
    private val videoTargets = listOf(Target.MP4, Target.MKV, Target.WEBM, Target.MOV, Target.AVI, Target.GIF)
    private val audioTargets = listOf(Target.MP3, Target.M4A, Target.OPUS, Target.OGG, Target.FLAC, Target.WAV)
    private val imageTargets = listOf(Target.JPG, Target.PNG, Target.WEBP, Target.BMP, Target.TIFF, Target.PDF)

    /** Video targets that re-encode the picture (resolution and quality apply). */
    private val encodedVideo = setOf(Target.MP4, Target.MKV, Target.WEBM, Target.MOV, Target.AVI)
    private val lossyAudio = setOf(Target.MP3, Target.M4A, Target.OPUS, Target.OGG)

    /** Most photos at once (photos → PDF, or several photos to one format). */
    const val MAX_IMAGES = 100

    /** Longest GIF made from a video, in seconds (a GIF weighs a lot). */
    const val GIF_MAX_SECONDS = 30

    /** Output formats offered for a [category]; [multiple] = several photos picked at once (PDF first). */
    fun targets(category: Category, multiple: Boolean = false): List<Target> = when (category) {
        Category.VIDEO -> videoTargets + audioTargets
        Category.AUDIO -> audioTargets
        Category.IMAGE -> if (multiple) listOf(Target.PDF) + imageTargets.dropLast(1) else imageTargets
        Category.PDF -> listOf(Target.JPG, Target.PNG)
        Category.MARKDOWN -> listOf(Target.HTML)
        Category.CSV -> listOf(Target.JSON)
        Category.JSON -> listOf(Target.CSV)
    }

    /**
     * Target selected when a file arrives: PDF for several photos, else the first one that changes the format
     * (a .mp4 starts on MKV, a .jpeg on PNG).
     */
    fun defaultTarget(category: Category, inputName: String, multiple: Boolean = false): Target {
        val list = targets(category, multiple)
        if (multiple) return list.first()
        val ext = normalizeExt(SafeNames.extensionOf(inputName))
        return list.firstOrNull { it.ext != ext } ?: list.first()
    }

    private fun normalizeExt(ext: String) = when (ext) {
        "jpeg", "jpe" -> "jpg"
        "tif" -> "tiff"
        "htm" -> "html"
        else -> ext
    }

    /** How [target] is produced from a [category] file. */
    fun method(category: Category, target: Target): Method = when (category) {
        Category.VIDEO, Category.AUDIO -> Method.FFMPEG
        Category.IMAGE -> when (target) {
            Target.JPG, Target.PNG, Target.WEBP -> Method.BITMAP
            Target.BMP, Target.TIFF -> Method.BITMAP_FFMPEG
            Target.PDF -> Method.IMAGES_TO_PDF
            else -> throw IllegalArgumentException("$target depuis une image")
        }
        Category.PDF -> Method.PDF_TO_IMAGES
        Category.MARKDOWN -> Method.MARKDOWN_TO_HTML
        Category.CSV -> Method.CSV_TO_JSON
        Category.JSON -> Method.JSON_TO_CSV
    }

    /** Short line under a tile; a few depend on the source ("Une image par page" from a PDF). */
    fun detail(category: Category, target: Target, multiple: Boolean = false): String = when {
        category == Category.PDF -> "Une image par page"
        category == Category.IMAGE && target == Target.PDF -> if (multiple) "$MAX_PDF_PHOTOS photos au plus" else "Page A4"
        else -> target.detail
    }

    fun allowsCopy(target: Target): Boolean = target == Target.MKV

    fun usesResolution(target: Target, options: ConvertOptions): Boolean =
        target in encodedVideo && !(options.copyStreams && allowsCopy(target))

    fun usesQuality(category: Category, target: Target, options: ConvertOptions): Boolean = when {
        target in encodedVideo -> !(options.copyStreams && allowsCopy(target))
        target == Target.GIF -> true
        target == Target.JPG || target == Target.WEBP -> category == Category.IMAGE || category == Category.PDF
        else -> false
    }

    fun usesAudioBitrate(target: Target, options: ConvertOptions): Boolean = when {
        target in encodedVideo -> !(options.copyStreams && allowsCopy(target))
        else -> target in lossyAudio
    }

    /** One-line summary of the options that apply ("1080p · Moyenne · 192 kbps"), or null when none does. */
    fun summary(category: Category, target: Target, options: ConvertOptions): String? {
        val parts = buildList {
            if (allowsCopy(target) && options.copyStreams) add("Copie rapide")
            if (usesResolution(target, options)) add(options.resolution.label)
            if (usesQuality(category, target, options)) add("Qualité ${options.quality.label.lowercase()}")
            if (usesAudioBitrate(target, options)) add(options.audioBitrate.label)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val wordExt = setOf("doc", "docx", "docm", "odt", "rtf", "pages")
    private val excelExt = setOf("xls", "xlsx", "xlsm", "ods", "numbers")
    private val powerPointExt = setOf("ppt", "pptx", "odp", "key")
    private val rawExt = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "srw", "pef", "x3f", "3fr", "iiq")

    /**
     * Why a file cannot be converted, in French, or null when it can (its [Category] then exists). Office documents
     * and RAW photos are refused by extension (they would need a huge engine), whatever their bytes look like.
     */
    fun refusal(kind: FileKind, name: String): String? {
        val ext = SafeNames.extensionOf(name)
        return when {
            ext in wordExt -> "Les documents Word ne se convertissent pas dans Gulp : il faudrait embarquer une suite bureautique entière."
            ext in excelExt -> "Les classeurs Excel ne se convertissent pas dans Gulp : il faudrait embarquer une suite bureautique entière."
            ext in powerPointExt -> "Les présentations PowerPoint ne se convertissent pas dans Gulp : il faudrait embarquer une suite bureautique entière."
            ext in rawExt -> "Les photos RAW ne se convertissent pas dans Gulp : leur développement demande un logiciel spécialisé."
            ext == "epub" -> "Les livres numériques (EPUB) ne se convertissent pas dans Gulp."
            Category.of(kind) != null -> null
            kind == FileKind.ZIP -> "Les archives ne se convertissent pas : Aperçu peut afficher ce qu'elles contiennent."
            kind == FileKind.TEXT || kind == FileKind.CODE -> "Ce texte n'a pas de conversion dans Gulp : Aperçu peut l'afficher."
            else -> "Gulp ne sait pas convertir ce type de fichier."
        }
    }

    /** Most photos in one PDF: PdfDocument keeps every page in memory until the file is written. */
    const val MAX_PDF_PHOTOS = 24

    /**
     * Name of one page rendered from a PDF: "<base> - page 007.jpg", the base shortened so the whole name stays
     * within [SafeNames.MAX_LENGTH] and the page number is never cut.
     */
    fun pageName(base: String, number: String, ext: String): String {
        val suffix = " - page $number.$ext"
        val room = (SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)
        return base.take(room).trimEnd('.', ' ').ifEmpty { "page" } + suffix
    }

    /** Name of the converted file: the source name with the target's extension ("Vacances.mkv" → "Vacances.mp4"). */
    fun outputName(inputName: String, target: Target): String = SafeNames.withExtension(inputName, target.ext)
}

/**
 * FFmpeg command lines (arguments after the executable). Paths are absolute work-dir paths, so none can be read
 * as an option; FFmpeg only reads local files (`-protocol_whitelist file,pipe`) and reports its progress on
 * stdout (`-progress pipe:1`).
 */
object FfmpegArgs {
    /** Options before the input: no stdin, overwrite, quiet stats, machine-readable progress on stdout. */
    fun prefix(input: String): List<String> = listOf(
        "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
        "-protocol_whitelist", "file,pipe", "-i", input,
    )

    /**
     * Full arguments to turn [input] into [output] as [target]. Both paths must be absolute, normalised and inside
     * [root] (the tools' work dir, `cacheDir/gulp-work`): FFmpeg never touches anything else.
     */
    fun build(root: String, input: String, output: String, target: Target, options: ConvertOptions): List<String> {
        require(isInside(root, input) && isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        return prefix(input) + codec(target, options) + output
    }

    /** [path] is absolute, has no "." / ".." / empty segment, and lies strictly under the absolute [root]. */
    fun isInside(root: String, path: String): Boolean {
        val base = root.trimEnd('/')
        if (!base.startsWith("/") || base.length < 2 || !path.startsWith("$base/")) return false
        val segments = path.substring(1).split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }

    /**
     * Extension for the fixed work names FFmpeg sees ("input.mkv", "output.mp4"): the user's name never reaches
     * FFmpeg, so a "%d" in it cannot turn into an image-sequence pattern. Anything odd becomes "bin".
     */
    fun safeExt(ext: String): String = ext.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"

    /** Longest output for [target], in seconds (FFmpeg `-t`), or null. */
    fun maxSeconds(target: Target): Int? = if (target == Target.GIF) Formats.GIF_MAX_SECONDS else null

    private fun codec(target: Target, o: ConvertOptions): List<String> {
        val kbps = "${o.audioBitrate.kbps}k"
        val firstVideo = listOf("-map", "0:v:0", "-map", "0:a:0?")
        val audioOnly = listOf("-map", "0:a:0")
        return when (target) {
            Target.MP4, Target.MOV -> firstVideo + listOf(
                "-vf", scale(o.resolution),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "${o.quality.x264Crf}", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", kbps,
                "-movflags", "+faststart",
            )
            Target.MKV -> if (o.copyStreams) {
                listOf("-map", "0:v", "-map", "0:a?", "-c", "copy")
            } else {
                firstVideo + listOf(
                    "-vf", scale(o.resolution),
                    "-c:v", "libx265", "-preset", "veryfast", "-crf", "${o.quality.x265Crf}", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", kbps,
                )
            }
            Target.WEBM -> firstVideo + listOf(
                "-vf", scale(o.resolution),
                "-c:v", "libvpx-vp9", "-crf", "${o.quality.vp9Crf}", "-b:v", "0",
                "-deadline", "good", "-cpu-used", "5", "-row-mt", "1", "-pix_fmt", "yuv420p",
                "-c:a", "libopus", "-b:a", kbps, "-ac", "2",
            )
            Target.AVI -> firstVideo + listOf(
                "-vf", scale(o.resolution),
                "-c:v", "mpeg4", "-vtag", "xvid", "-q:v", "${o.quality.mpeg4Q}", "-pix_fmt", "yuv420p",
                "-c:a", "libmp3lame", "-b:a", kbps, "-ac", "2",
            )
            Target.GIF -> listOf(
                "-map", "0:v:0", "-an", "-t", "${Formats.GIF_MAX_SECONDS}",
                "-vf", gifFilter(o.quality),
                "-loop", "0",
            )
            Target.MP3 -> audioOnly + listOf("-c:a", "libmp3lame", "-b:a", kbps, "-ac", "2")
            Target.M4A -> audioOnly + listOf("-c:a", "aac", "-b:a", kbps, "-movflags", "+faststart")
            Target.OPUS -> audioOnly + listOf("-c:a", "libopus", "-b:a", kbps, "-ac", "2")
            Target.OGG -> audioOnly + listOf("-c:a", "libvorbis", "-b:a", kbps)
            Target.FLAC -> audioOnly + listOf("-c:a", "flac")
            Target.WAV -> audioOnly + listOf("-c:a", "pcm_s16le")
            // Still images (from a PNG we wrote ourselves).
            Target.BMP -> listOf("-frames:v", "1", "-update", "1", "-c:v", "bmp")
            Target.TIFF -> listOf("-frames:v", "1", "-update", "1", "-c:v", "tiff", "-compression_algo", "lzw")
            Target.JPG, Target.PNG, Target.WEBP, Target.PDF, Target.HTML, Target.JSON, Target.CSV ->
                throw IllegalArgumentException("$target ne passe pas par FFmpeg")
        }
    }

    /**
     * Scale filter: the short side goes down to the resolution's height (portrait videos too), never up; both
     * sides stay even (H.264 / H.265 in 4:2:0 need it).
     */
    fun scale(resolution: Resolution): String {
        val n = resolution.height ?: return "scale=trunc(iw/2)*2:trunc(ih/2)*2"
        return "scale='if(gt(iw,ih),-2,trunc(min($n,iw)/2)*2)':'if(gt(iw,ih),trunc(min($n,ih)/2)*2,-2)'"
    }

    /**
     * GIF: lower frame rate, longest side capped, and a palette computed for each frame (stats_mode=single,
     * new=1) so FFmpeg never has to keep the whole clip in memory before writing it.
     */
    fun gifFilter(quality: Quality): String {
        val side = quality.gifSide
        return "fps=${quality.gifFps}," +
            "scale='if(gt(iw,ih),min($side,iw),-2)':'if(gt(iw,ih),-2,min($side,ih))':flags=lanczos," +
            "split[a][b];[a]palettegen=stats_mode=single[p];[b][p]paletteuse=new=1:dither=bayer:bayer_scale=4"
    }
}

/** Pure arithmetic of the bounded photo decodes (unit tested). */
object ImageMath {
    /**
     * Power-of-two BitmapFactory sample size so that the decoded image's longest side is at most [maxSide]
     * (a 8000 px photo with maxSide 4096 → 2, i.e. 4000 px).
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > maxSide) sample *= 2
        return sample
    }
}
