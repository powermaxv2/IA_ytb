package com.powermaxv2.ytdownloader.core.sticker

import kotlin.math.max
import kotlin.math.roundToInt

/** Outline of the sticker, in pixels of the 512 px picture. */
enum class StickerShape(val label: String) {
    SQUARE("Carré"),
    ROUNDED("Arrondi"),
    ROUND("Rond"),
    HEART("Cœur"),
    STAR("Étoile"),
}

/** White border around the sticker. */
enum class StickerOutline(val label: String, val px: Int) {
    NONE("Aucun", 0),
    THIN("Fin", 7),
    THICK("Épais", 14),
}

/** How a sticker is drawn: the same for every sticker of a pack. */
data class StickerStyle(
    val shape: StickerShape = StickerShape.ROUNDED,
    val outline: StickerOutline = StickerOutline.THICK,
    /** Meme text drawn over the sticker (white, black outline); empty = none. */
    val text: String = "",
    val textOnTop: Boolean = false,
    /** Magic wand from the edges: removes a plain background around the subject. */
    val cutout: Boolean = false,
    val tolerance: Int = Cutout.DEFAULT_TOLERANCE,
    val mirror: Boolean = false,
) {
    val hasText: Boolean get() = text.isNotBlank()
}

/** Extra copy made when there are fewer than 3 stickers (WhatsApp's minimum per pack). */
enum class StickerVariant {
    NORMAL,
    MIRROR,

    /** Turned a little, like a sticker stuck in a hurry. */
    TILT,
    NO_TEXT,

    /** Animated: played backwards. */
    REVERSE,
}

/** What a video sticker keeps: [startMs] then [lengthMs], optionally played forth and back. */
data class StickerClip(val startMs: Long = 0, val lengthMs: Long = StickerRules.DEFAULT_CLIP_MS, val boomerang: Boolean = false)

/**
 * WhatsApp's rules for third-party sticker packs, and the choices Gulp makes to fit them. Pure Kotlin (tested).
 * Source: WhatsApp's "stickers" sample app (StickerPackValidator): 512 × 512 WebP, still ≤ 100 KB, animated
 * ≤ 500 KB and ≤ 10 s with frames of at least 8 ms, tray icon 96 × 96 PNG ≤ 50 KB, 3 to 30 stickers, 1 to 3 emojis.
 */
object StickerRules {
    const val SIZE = 512
    const val TRAY_SIZE = 96
    const val STATIC_MAX_BYTES = 100 * 1024
    const val ANIMATED_MAX_BYTES = 500 * 1024
    const val TRAY_MAX_BYTES = 50 * 1024
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val MAX_ANIMATED_MS = 10_000L
    const val MIN_FRAME_MS = 8
    const val NAME_MAX = 128

    /** Frames per second read from a video; fewer are kept when the file is too heavy. */
    const val SOURCE_FPS = 12
    const val DEFAULT_CLIP_MS = 3_000L
    val CLIP_CHOICES = listOf(1_000L, 2_000L, 3_000L)

    const val PUBLISHER = "Gulp"
    const val DEFAULT_NAME = "Mes stickers Gulp"
    val EMOJIS = listOf("😂", "❤️", "😍", "🔥", "👍", "😎", "😭", "🎉")
    val DEFAULT_EMOJI = EMOJIS.first()

    /** Qualities tried, best first, until a still sticker fits in [STATIC_MAX_BYTES]. */
    val STATIC_QUALITIES = listOf(92, 85, 75, 65, 55, 45, 35, 25, 15)

    /** (frames per second, quality) tried in turn until an animated sticker fits in [ANIMATED_MAX_BYTES]. */
    val ANIMATED_LADDER: List<Pair<Int, Int>> = listOf(12, 10, 8, 6, 5, 4).flatMap { fps -> listOf(70, 55, 40, 28).map { fps to it } }

    /**
     * Which picture and which [StickerVariant] each sticker of the pack is: one per picture, completed up to
     * [MIN_STICKERS] with variants (mirror, then without the text, backwards for a video, or tilted).
     */
    fun plan(count: Int, hasText: Boolean, animated: Boolean): List<Pair<Int, StickerVariant>> {
        require(count in 1..MAX_STICKERS) { "1 à $MAX_STICKERS images" }
        val base = (0 until count).map { it to StickerVariant.NORMAL }
        if (count >= MIN_STICKERS) return base
        val third = when {
            hasText -> StickerVariant.NO_TEXT
            animated -> StickerVariant.REVERSE
            else -> StickerVariant.TILT
        }
        return if (count == 2) base + (0 to StickerVariant.MIRROR) else base + listOf(0 to StickerVariant.MIRROR, 0 to third)
    }

    /** Indices of [count] frames read at [fromFps] to keep for [toFps] (evenly spread, never empty). */
    fun subsample(count: Int, fromFps: Int, toFps: Int): List<Int> {
        if (count <= 0) return emptyList()
        if (toFps >= fromFps) return (0 until count).toList()
        val kept = max(1, (count.toLong() * toFps / fromFps).toInt())
        return (0 until kept).map { k -> (k.toLong() * fromFps / toFps).toInt().coerceAtMost(count - 1) }.distinct()
    }

    /** Frame order: forward, or forward then backward without repeating the ends ([boomerang]), or [reverse]d. */
    fun order(indices: List<Int>, boomerang: Boolean, reverse: Boolean): List<Int> {
        val base = if (reverse) indices.reversed() else indices
        if (!boomerang || base.size < 3) return base
        return base + base.subList(1, base.size - 1).reversed()
    }

    /** Duration of each frame at [fps], at least [MIN_FRAME_MS]. */
    fun frameMs(fps: Int): Int = max(MIN_FRAME_MS, (1000.0 / fps.coerceAtLeast(1)).roundToInt())

    /** The clip clamped to a source of [durationMs]: at most 3 s, inside the file. */
    fun clamp(clip: StickerClip, durationMs: Long): StickerClip {
        val length = clip.lengthMs.coerceIn(200L, minOf(DEFAULT_CLIP_MS, max(200L, durationMs)))
        val start = clip.startMs.coerceIn(0L, max(0L, durationMs - length))
        return clip.copy(startMs = start, lengthMs = length)
    }

    /** Empty border kept around the picture for the outline (px of the 512 px sticker). */
    fun margin(style: StickerStyle): Int = when {
        style.outline.px > 0 -> style.outline.px + 3
        style.shape == StickerShape.SQUARE && !style.cutout -> 0
        else -> 2
    }

    /**
     * Where a touch at ([x], [y]) on a sticker (0..1) falls on its source picture (0..1), or null outside the
     * picture or on a tilted variant (the magic wand only works on the straight sticker).
     */
    fun toSource(style: StickerStyle, variant: StickerVariant, x: Float, y: Float): Pair<Float, Float>? {
        if (variant == StickerVariant.TILT) return null
        val m = margin(style).toFloat() / SIZE
        val span = 1f - 2 * m
        var u = (x - m) / span
        val v = (y - m) / span
        if (u !in 0f..1f || v !in 0f..1f) return null
        if (style.mirror != (variant == StickerVariant.MIRROR)) u = 1f - u
        return u to v
    }

    /** Whether a sticker of [bytes] is accepted by WhatsApp. */
    fun fits(bytes: Int, animated: Boolean): Boolean = bytes <= if (animated) ANIMATED_MAX_BYTES else STATIC_MAX_BYTES

    /** The pack name shown in WhatsApp: one line, at most [NAME_MAX] characters, never empty. */
    fun packName(input: String): String {
        val clean = input.replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim()
        return clean.take(NAME_MAX).trim().ifEmpty { DEFAULT_NAME }
    }

    /** A pack identifier WhatsApp accepts ([A-Za-z0-9_], unique per pack). */
    fun packId(millis: Long, salt: Int): String = "gulp_${millis.toString(36)}_${(salt and 0x7FFFFFFF).toString(36)}"

    /** Whether [id] is an identifier this app made (also guards the provider's paths). */
    fun isPackId(id: String?): Boolean = id != null && id.length in 6..64 && id.startsWith("gulp_") && id.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }

    /** Whether [name] is a sticker or tray file name this app writes ("01.webp", "tray.png"). */
    fun isAssetName(name: String?): Boolean = name != null && (name == TRAY_FILE || Regex("^\\d{2}\\.webp$").matches(name))

    const val TRAY_FILE = "tray.png"

    fun stickerFile(index: Int): String = (index + 1).toString().padStart(2, '0') + ".webp"

    /** "3 stickers", "1 sticker animé"… */
    fun countText(count: Int, animated: Boolean): String {
        val noun = if (count > 1) "stickers" else "sticker"
        val kind = if (animated) (if (count > 1) " animés" else " animé") else ""
        return "$count $noun$kind"
    }

    /** "45 Ko" for the size line under each sticker. */
    fun kiloText(bytes: Int): String = "${(bytes + 512) / 1024} Ko"
}
