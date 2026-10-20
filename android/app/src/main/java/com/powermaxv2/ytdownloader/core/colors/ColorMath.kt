package com.powermaxv2.ytdownloader.core.colors

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Pure colour maths of the Pipette (no Android): a colour is an Int 0xRRGGBB (alpha ignored).

/** One way to write a colour, as shown and copied by the tool ("HEX" → "#E4572E"). */
data class ColorFormat(val label: String, val value: String)

object ColorMath {
    fun red(rgb: Int): Int = (rgb shr 16) and 0xFF

    fun green(rgb: Int): Int = (rgb shr 8) and 0xFF

    fun blue(rgb: Int): Int = rgb and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** "#E4572E". */
    fun hex(rgb: Int): String = String.format(Locale.ROOT, "#%06X", rgb and 0xFFFFFF)

    /** "#e4572e", "E4572E", "#e52" or "0xFFE4572E" → the colour; null when it is not one. */
    fun parseHex(text: String): Int? {
        var s = text.trim().removePrefix("#")
        if (s.startsWith("0x", ignoreCase = true)) s = s.drop(2)
        if (s.length == 8) s = s.drop(2)
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length != 6 || s.any { Character.digit(it, 16) < 0 }) return null
        return s.toInt(16)
    }

    /** Hue 0‥359, saturation and lightness 0‥100 (CSS hsl). */
    fun hsl(rgb: Int): IntArray {
        val r = red(rgb) / 255f
        val g = green(rgb) / 255f
        val b = blue(rgb) / 255f
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val d = hi - lo
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        return intArrayOf(hue(r, g, b, hi, d), (s * 100).roundToInt(), (l * 100).roundToInt())
    }

    /** Hue 0‥359, saturation and value 0‥100. */
    fun hsv(rgb: Int): IntArray {
        val r = red(rgb) / 255f
        val g = green(rgb) / 255f
        val b = blue(rgb) / 255f
        val hi = max(r, max(g, b))
        val d = hi - min(r, min(g, b))
        val s = if (hi == 0f) 0f else d / hi
        return intArrayOf(hue(r, g, b, hi, d), (s * 100).roundToInt(), (hi * 100).roundToInt())
    }

    private fun hue(r: Float, g: Float, b: Float, hi: Float, d: Float): Int {
        if (d == 0f) return 0
        val h = when (hi) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return ((h + 360f).roundToInt()) % 360
    }

    /** Cyan, magenta, yellow, black in percent (the printer's CMJN). */
    fun cmyk(rgb: Int): IntArray {
        val r = red(rgb) / 255f
        val g = green(rgb) / 255f
        val b = blue(rgb) / 255f
        val k = 1f - max(r, max(g, b))
        if (k >= 1f) return intArrayOf(0, 0, 0, 100)
        fun part(c: Float) = ((1f - c - k) / (1f - k) * 100).roundToInt()
        return intArrayOf(part(r), part(g), part(b), (k * 100).roundToInt())
    }

    /** WCAG relative luminance, 0 (black) ‥ 1 (white). */
    fun luminance(rgb: Int): Double {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(red(rgb)) + 0.7152 * lin(green(rgb)) + 0.0722 * lin(blue(rgb))
    }

    /** WCAG contrast ratio between two colours, 1 ‥ 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** True when white text reads better than black on [rgb]. */
    fun wantsWhiteText(rgb: Int): Boolean = contrast(rgb, 0xFFFFFF) >= contrast(rgb, 0x000000)

    /** The ways the tool writes a colour, in the order of the screen. */
    fun formats(rgb: Int): List<ColorFormat> {
        val (h, s, l) = hsl(rgb).let { Triple(it[0], it[1], it[2]) }
        val c = cmyk(rgb)
        return listOf(
            ColorFormat("HEX", hex(rgb)),
            ColorFormat("RVB", "rgb(${red(rgb)}, ${green(rgb)}, ${blue(rgb)})"),
            ColorFormat("TSL", "hsl($h, $s%, $l%)"),
            ColorFormat("CMJN", "${c[0]} %, ${c[1]} %, ${c[2]} %, ${c[3]} %"),
        )
    }

    /** Squared "redmean" distance: cheap, and closer to the eye than plain RGB. */
    fun distance(a: Int, b: Int): Int {
        val rm = (red(a) + red(b)) / 2
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        return (((512 + rm) * dr * dr) shr 8) + 4 * dg * dg + (((767 - rm) * db * db) shr 8)
    }

    /** Everyday French name of the nearest colour of [NAMES] ("Rouge tomate", "Bleu ciel"…). */
    fun name(rgb: Int): String = NAMES.minBy { distance(it.second, rgb) }.first

    /**
     * The average colour of the pixels within [radius] of ([x], [y]) in [pixels] (ARGB, [width] × [height]),
     * so a touch is not thrown off by one noisy pixel. Transparent pixels are left out; all transparent → white.
     */
    fun sampleAverage(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, radius: Int = 1): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (yy in (y - radius).coerceAtLeast(0)..(y + radius).coerceAtMost(height - 1)) {
            for (xx in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(width - 1)) {
                val p = pixels[yy * width + xx]
                if ((p ushr 24) < 128) continue
                r += red(p)
                g += green(p)
                b += blue(p)
                n++
            }
        }
        if (n == 0) return 0xFFFFFF
        return rgb(((r + n / 2) / n).toInt(), ((g + n / 2) / n).toInt(), ((b + n / 2) / n).toInt())
    }

    private val NAMES: List<Pair<String, Int>> = listOf(
        "Noir" to 0x000000,
        "Gris anthracite" to 0x303030,
        "Gris" to 0x808080,
        "Gris clair" to 0xC8C8C8,
        "Blanc" to 0xFFFFFF,
        "Blanc cassé" to 0xF4F0E6,
        "Beige" to 0xE8D5B0,
        "Sable" to 0xD8B98A,
        "Marron" to 0x7B4A24,
        "Chocolat" to 0x4A2C1A,
        "Caramel" to 0xB5702D,
        "Rouge" to 0xE02020,
        "Rouge tomate" to 0xE4572E,
        "Bordeaux" to 0x7A1F2B,
        "Rose" to 0xF27BA5,
        "Rose pâle" to 0xF8C8D4,
        "Fuchsia" to 0xD5268F,
        "Corail" to 0xFF7F66,
        "Saumon" to 0xF4A08C,
        "Orange" to 0xFF8A1F,
        "Abricot" to 0xF7B267,
        "Jaune" to 0xFFD60A,
        "Jaune moutarde" to 0xC9A227,
        "Crème" to 0xFFF4C8,
        "Vert citron" to 0xB5E61D,
        "Vert" to 0x2EA043,
        "Vert sapin" to 0x1E5631,
        "Vert olive" to 0x7A7A2E,
        "Vert d'eau" to 0xA8E0C8,
        "Menthe" to 0x5BD6A6,
        "Turquoise" to 0x1BC6C2,
        "Bleu canard" to 0x0F6E74,
        "Bleu ciel" to 0x7EC8F2,
        "Bleu" to 0x2F6FDE,
        "Bleu marine" to 0x1B2A4A,
        "Bleu nuit" to 0x0E1A33,
        "Indigo" to 0x4B3FA6,
        "Violet" to 0x8E44C4,
        "Lavande" to 0xB9A7E8,
        "Prune" to 0x5E2750,
    )
}

/** The colours the user kept, newest first, as saved in the preferences ("E4572E,2F6FDE"). */
object KeptColors {
    const val MAX = 12

    fun decode(text: String?): List<Int> =
        text.orEmpty().split(',').mapNotNull { ColorMath.parseHex(it) }.distinct().take(MAX)

    fun encode(colors: List<Int>): String = colors.joinToString(",") { ColorMath.hex(it).drop(1) }

    /** [rgb] put first (moved there when already kept), the oldest dropped past [MAX]. */
    fun add(colors: List<Int>, rgb: Int): List<Int> = (listOf(rgb and 0xFFFFFF) + colors.filter { it != (rgb and 0xFFFFFF) }).take(MAX)

    fun remove(colors: List<Int>, rgb: Int): List<Int> = colors.filter { it != (rgb and 0xFFFFFF) }
}
