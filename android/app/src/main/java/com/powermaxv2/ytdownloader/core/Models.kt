package com.powermaxv2.ytdownloader.core

import java.util.Locale
import kotlin.math.roundToInt

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
