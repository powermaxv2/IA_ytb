package com.powermaxv2.ytdownloader.core.resize

import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// What the Redimensionneur does to each photo: the crop, the size of the copy, its format and its name, and the
// search for the best quality under a weight. Pure (no Android types): unit tested in ResizePlanTest.

/** How the new size is chosen. */
enum class ResizeMode(val label: String) {
    /** The longest side is brought down to a number of pixels. */
    SIDE("Taille"),

    /** Both sides are multiplied by a percentage. */
    PERCENT("Pourcentage"),

    /** The copy must weigh less than a size (a form, a site that refuses big photos). */
    WEIGHT("Poids"),
}

/** Longest side of the copy, in pixels. */
enum class SidePreset(val px: Int, val label: String, val detail: String) {
    UHD(3840, "3840", "4K"),
    FULL_HD(1920, "1920", "Full HD"),
    HD(1280, "1280", "HD"),
    POST(1080, "1080", "Réseaux"),
    WEB(800, "800", "Web"),
    SMALL(480, "480", "Mini"),
}

enum class PercentPreset(val percent: Int) {
    P75(75),
    P50(50),
    P33(33),
    P25(25),
    ;

    val label: String get() = "$percent %"
}

/** Heaviest copy allowed (decimal units, like the sizes the screen shows). */
enum class WeightGoal(val bytes: Long, val label: String) {
    KB100(100_000L, "100 Ko"),
    KB200(200_000L, "200 Ko"),
    KB500(500_000L, "500 Ko"),
    MB1(1_000_000L, "1 Mo"),
    MB2(2_000_000L, "2 Mo"),
}

/** A shape the photo is cut to, from its centre. [w] / [h] = 0 = no crop. */
enum class CropRatio(val w: Int, val h: Int, val label: String) {
    NONE(0, 0, "Libre"),
    SQUARE(1, 1, "1:1"),
    PORTRAIT(4, 5, "4:5"),
    PHOTO(3, 4, "3:4"),
    WIDE(16, 9, "16:9"),
    STORY(9, 16, "9:16"),
}

enum class OutFormat(val label: String, val ext: String, val mime: String) {
    JPEG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
}

/** "Même format" keeps JPG / PNG / WebP; anything else (HEIC, BMP…) becomes JPG. */
enum class FormatChoice(val label: String, val format: OutFormat?) {
    SAME("Identique", null),
    JPEG("JPG", OutFormat.JPEG),
    PNG("PNG", OutFormat.PNG),
    WEBP("WebP", OutFormat.WEBP),
}

enum class QualityPreset(val quality: Int, val label: String) {
    BEST(95, "Max"),
    HIGH(85, "Haute"),
    MEDIUM(72, "Moyenne"),
}

data class ResizeSettings(
    val mode: ResizeMode = ResizeMode.SIDE,
    val side: SidePreset = SidePreset.FULL_HD,
    val percent: PercentPreset = PercentPreset.P50,
    val weight: WeightGoal = WeightGoal.MB1,
    val crop: CropRatio = CropRatio.NONE,
    val format: FormatChoice = FormatChoice.SAME,
    val quality: QualityPreset = QualityPreset.HIGH,
    /** Date, phone and place written back into the copy; off = the copy says nothing. */
    val keepExif: Boolean = false,
)

/** A part of a picture, in pixels: [left], [top] and its size. */
data class Box(val left: Int, val top: Int, val width: Int, val height: Int)

/** Width × height of a picture, as shown ("1920 × 1080"). */
data class Dims(val width: Int, val height: Int) {
    val longest: Int get() = max(width, height)

    override fun toString(): String = "$width × $height"
}

object ResizePlan {
    /** Most photos at once. */
    const val MAX_PHOTOS = 50

    /** Biggest side Gulp decodes or writes (the phone's memory: 4096 × 4096 × 4 ≈ 64 MB). */
    const val MAX_SIDE = 4096

    /** Most pixels decoded at once (≈ 96 MB in ARGB): a tall panorama cropped square still gets its full height. */
    const val MAX_DECODE_PIXELS = 24_000_000L

    /** Smallest side of a copy: under it the result is a smudge. */
    const val MIN_SIDE = 16

    /** Weight mode aims a little under the goal: the EXIF written back and the rounding of the encoder add some. */
    const val WEIGHT_MARGIN = 0.97

    /** Lowest JPEG / WebP quality tried for a weight before the photo is made smaller instead. */
    const val MIN_QUALITY = 40
    const val MAX_QUALITY = 92

    /** Each round of the weight search keeps this share of the sides when no quality fits. */
    const val SHRINK_STEP = 0.8

    /** The centred part of a [width] × [height] picture that has the [crop] shape (the whole picture when none). */
    fun cropBox(width: Int, height: Int, crop: CropRatio): Box {
        if (crop == CropRatio.NONE || width <= 0 || height <= 0) return Box(0, 0, max(width, 0), max(height, 0))
        // Compare width/height with crop.w/crop.h without floating point: too wide → cut the sides, else top and bottom.
        return if (width.toLong() * crop.h > height.toLong() * crop.w) {
            val w = max(1, (height.toLong() * crop.w / crop.h).toInt())
            Box((width - w) / 2, 0, w, height)
        } else {
            val h = max(1, (width.toLong() * crop.h / crop.w).toInt())
            Box(0, (height - h) / 2, width, h)
        }
    }

    /**
     * Size of the copy of an upright [source] (EXIF rotation applied) after the crop and the resize. A photo is
     * never made bigger, and never over [MAX_SIDE]. In [ResizeMode.WEIGHT] it is the size the search starts from.
     */
    fun target(source: Dims, settings: ResizeSettings): Dims {
        val box = cropBox(source.width, source.height, settings.crop)
        val longest = max(box.width, box.height)
        if (longest <= 0) return Dims(0, 0)
        val wanted = when (settings.mode) {
            ResizeMode.SIDE -> settings.side.px
            ResizeMode.PERCENT -> (longest * settings.percent.percent / 100.0).roundToInt()
            ResizeMode.WEIGHT -> longest
        }
        val side = min(min(wanted, longest), MAX_SIDE).coerceAtLeast(min(MIN_SIDE, longest))
        return scaled(Dims(box.width, box.height), side)
    }

    /** [dims] with its longest side at [side], the shape kept (each side at least 1 px). */
    fun scaled(dims: Dims, side: Int): Dims {
        val longest = dims.longest
        if (longest <= 0 || side >= longest) return dims
        val scale = side.toDouble() / longest
        return Dims(max(1, (dims.width * scale).roundToInt()), max(1, (dims.height * scale).roundToInt()))
    }

    /**
     * Longest side to decode an upright [source] at, when the copy is [scale] times the size of its crop: just big
     * enough (a little over, the rest is done by a smooth scale), within [MAX_DECODE_PIXELS].
     */
    fun decodeSide(source: Dims, scale: Double): Int {
        val longest = source.longest
        if (longest <= 0) return MAX_SIDE
        val wanted = ceil(longest * scale.coerceIn(0.0, 1.0)).toInt() + 2
        val area = source.width.toDouble() * source.height
        val byPixels = if (area <= 0) longest else (longest * sqrt(MAX_DECODE_PIXELS / area)).toInt()
        return max(1, min(min(wanted, longest), byPixels))
    }

    /** Whether the copy keeps the photo's pixels as they are (same size, no crop): the screen says so. */
    fun unchanged(source: Dims, settings: ResizeSettings): Boolean =
        settings.mode != ResizeMode.WEIGHT && target(source, settings) == source

    /** The format of the copy of [name]; [hasAlpha] = it has see-through pixels (a JPG of it would be on white). */
    fun format(name: String, choice: FormatChoice, hasAlpha: Boolean = false): OutFormat {
        choice.format?.let { return it }
        return when (SafeNames.extensionOf(name)) {
            "png" -> OutFormat.PNG
            "webp" -> OutFormat.WEBP
            "jpg", "jpeg", "jpe" -> OutFormat.JPEG
            // HEIC, BMP, TIFF, AVIF…: JPG, or WebP when there is transparency to keep.
            else -> if (hasAlpha) OutFormat.WEBP else OutFormat.JPEG
        }
    }

    /**
     * In weight mode PNG cannot choose its weight (it is lossless): it only gets smaller with fewer pixels. JPG and
     * WebP play with quality first.
     */
    fun lossy(format: OutFormat): Boolean = format != OutFormat.PNG

    /** "Vacances.HEIC" at 1920 × 1440 → "Vacances_1920x1440.jpg". */
    fun outputName(inputName: String, dims: Dims, format: OutFormat): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(inputName, fallback = "photo"))
        return SafeNames.withExtension("${base}_${dims.width}x${dims.height}", format.ext)
    }

    /** What a [goal] lets the encoder write, once the margin is taken. */
    fun weightLimit(goal: WeightGoal, extraBytes: Int = 0): Long = (goal.bytes * WEIGHT_MARGIN).toLong() - extraBytes

    /** One try of the weight search: these [dims] at this [quality] (ignored for PNG). */
    data class Attempt(val dims: Dims, val quality: Int)

    /**
     * The best copy under [limit] bytes. [encode] writes the photo at a size and a quality and returns its weight.
     * For each size (the start, then [SHRINK_STEP] smaller each round), a binary search finds the highest quality
     * that fits; the first size where one fits wins. Returns the attempt and its weight, or the smallest try when
     * nothing fits (the screen then says the goal was missed).
     */
    fun fit(start: Dims, lossy: Boolean, limit: Long, maxRounds: Int = 30, encode: (Attempt) -> Long): Pair<Attempt, Long> {
        var dims = start
        var last: Pair<Attempt, Long>? = null
        repeat(maxRounds) {
            if (lossy) {
                var lo = MIN_QUALITY
                var hi = MAX_QUALITY
                var best: Pair<Attempt, Long>? = null
                while (lo <= hi) {
                    val q = (lo + hi) / 2
                    val attempt = Attempt(dims, q)
                    val size = encode(attempt)
                    last = attempt to size
                    if (size <= limit) {
                        best = attempt to size
                        lo = q + 1
                    } else {
                        hi = q - 1
                    }
                }
                best?.let { return it }
            } else {
                val attempt = Attempt(dims, 100)
                val size = encode(attempt)
                last = attempt to size
                if (size <= limit) return last!!
            }
            if (dims.longest <= MIN_SIDE) return last!!
            dims = scaled(dims, max(MIN_SIDE, (dims.longest * SHRINK_STEP).roundToInt()))
        }
        return last!!
    }

    /** "−62 %" of [after] against [before], 0 when the copy is not lighter. */
    fun gainPercent(before: Long, after: Long): Int =
        if (before <= 0 || after >= before) 0 else ((before - after) * 100.0 / before).roundToInt().coerceIn(0, 100)
}
