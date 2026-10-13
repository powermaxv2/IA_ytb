package com.powermaxv2.ytdownloader.core.profilefit

import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Photo de profil entière: WhatsApp keeps a centred square of a profile photo and shows it in a circle, so a
// portrait or a group photo loses heads and shoulders. Gulp puts the whole photo, smaller, on a square backdrop
// (the photo itself blurred, its main colour, white or black) so that all of it shows inside the round.
// This file is the pure part (geometry, colour, blur on plain pixel arrays): tested on the JVM.

/** Where the whole photo has to fit. */
enum class FitShape(val label: String) {
    /** Inside the circle WhatsApp shows: nothing of the photo is ever hidden. */
    CIRCLE("Dans le rond"),

    /** Inside the square file: bigger, the round only nibbles the photo's corners. */
    SQUARE("Dans le carré"),
}

/** What fills the space around the photo. */
enum class Backdrop(val label: String) {
    BLUR("Flou"),
    COLOR("Sa couleur"),
    WHITE("Blanc"),
    BLACK("Noir"),
}

/** Breathing room between the photo and the edge of the round (or of the square), in % of the side. */
enum class FitMargin(val label: String, val percent: Int) {
    TIGHT("Serré", 0),
    NORMAL("Normal", 4),
    WIDE("Large", 10),
}

data class FitOptions(
    val shape: FitShape = FitShape.CIRCLE,
    val backdrop: Backdrop = Backdrop.BLUR,
    val margin: FitMargin = FitMargin.NORMAL,
)

/** A rectangle in pixels of the square output. */
data class FitBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

object ProfileFit {
    /** Side of the saved photo: WhatsApp keeps 640 px, 1080 leaves room for its own compression. */
    const val OUTPUT_SIDE = 1080

    /** Side of the live preview. */
    const val PREVIEW_SIDE = 640

    /** Side of the tiny copy that gets blurred, then stretched back (a cheap, very soft blur). */
    const val BLUR_SIDE = 72
    const val BLUR_RADIUS = 5

    /**
     * Where a [width] × [height] photo is drawn in a [side] × [side] square: centred, as big as it can be while
     * staying whole inside the round ([FitShape.CIRCLE]: its diagonal is the circle's diameter) or inside the
     * square ([FitShape.SQUARE]), minus [margin] on each side.
     */
    fun placement(width: Int, height: Int, side: Int, shape: FitShape, margin: FitMargin = FitMargin.NORMAL): FitBox {
        require(width > 0 && height > 0 && side > 0)
        val room = side * (1f - 2f * margin.percent / 100f)
        val span = when (shape) {
            FitShape.CIRCLE -> sqrt(width.toDouble() * width + height.toDouble() * height).toFloat()
            FitShape.SQUARE -> max(width, height).toFloat()
        }
        val scale = room / span
        val w = width * scale
        val h = height * scale
        return FitBox((side - w) / 2f, (side - h) / 2f, w, h)
    }

    /** Where the photo is drawn to cover the whole square (cut on its long side): the blurred backdrop. */
    fun cover(width: Int, height: Int, side: Int): FitBox {
        require(width > 0 && height > 0 && side > 0)
        val scale = side.toFloat() / min(width, height)
        val w = width * scale
        val h = height * scale
        return FitBox((side - w) / 2f, (side - h) / 2f, w, h)
    }

    /**
     * Share of a [width] × [height] photo that WhatsApp hides on its own: it keeps the centred square
     * (min side²) and shows the circle inside it (π/4 of that square).
     */
    fun hiddenByWhatsApp(width: Int, height: Int): Float {
        require(width > 0 && height > 0)
        val m = min(width, height).toDouble()
        val shown = PI / 4.0 * m * m / (width.toDouble() * height)
        return (1.0 - shown).toFloat().coerceIn(0f, 1f)
    }

    /** Share of the photo that stays visible in the round once Gulp fitted it. */
    fun shownByGulp(shape: FitShape, width: Int, height: Int): Float = when (shape) {
        FitShape.CIRCLE -> 1f
        FitShape.SQUARE -> {
            // The photo fills the square on its long side; the round cuts the four corners off.
            val long = max(width, height).toDouble()
            val short = min(width, height).toDouble()
            val r = 0.5
            val a = short / long / 2.0 // half of the short side, the long side being 1
            // Area of the strip |y| ≤ a of a circle of radius 0.5, divided by the strip's area (1 × 2a).
            val inside = 2.0 * (a * sqrt(r * r - a * a) + r * r * kotlin.math.asin(a / r))
            (inside / (2.0 * a)).toFloat().coerceIn(0f, 1f)
        }
    }

    /** "43 %": a share as a whole percentage. */
    fun percent(share: Float): String = "${(share * 100f).roundToInt().coerceIn(0, 100)} %"

    /**
     * The photo's main colour for [Backdrop.COLOR]: the mean of its (opaque) pixels, a little darkened so the
     * photo stands out on it. Transparent pixels are skipped; an empty or fully transparent photo gives dark grey.
     */
    fun backdropColor(pixels: IntArray): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        if (n == 0L) return 0xFF202020.toInt()
        fun shade(sum: Long) = (sum / n * 82 / 100).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (shade(r) shl 16) or (shade(g) shl 8) or shade(b)
    }

    /**
     * Box blur of a [width] × [height] ARGB picture, [passes] times (three passes look like a gaussian).
     * Edges are clamped, so the picture does not darken at its borders. Returns a new array.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3): IntArray {
        require(pixels.size == width * height)
        if (radius <= 0 || width == 0 || height == 0) return pixels.copyOf()
        var src = pixels.copyOf()
        val dst = IntArray(src.size)
        repeat(passes) {
            pass(src, dst, width, height, radius, horizontal = true)
            pass(dst, src, width, height, radius, horizontal = false)
        }
        return src
    }

    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return if (horizontal) src[line * width + c] else src[c * width + line]
            }
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) dst[line * width + i] = out else dst[i * width + line] = out
                val leaving = at(i - radius)
                val entering = at(i + radius + 1)
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    /** Name of the saved photo: "vacances.heic" → "vacances-profil.jpg". */
    fun outputName(source: String): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(source, "photo")).ifBlank { "photo" }
        return SafeNames.sanitize("$base-profil.jpg", "photo-profil.jpg")
    }
}
