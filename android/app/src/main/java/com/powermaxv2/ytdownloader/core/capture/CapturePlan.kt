package com.powermaxv2.ytdownloader.core.capture

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The Capture d'image's arithmetic: frames of a video (index ↔ time), steps of one image or one second, the frames
// looked at around the playhead to find the sharpest one, the sharpness score itself (variance of the Laplacian of
// the picture's brightness) and the names of the photos. Pure (no Android types): unit tested in CapturePlanTest.

/** How the photo is written. */
enum class CaptureFormat(val label: String, val ext: String, val detail: String) {
    JPG("JPG", "jpg", "léger, qualité 95"),
    PNG("PNG", "png", "sans aucune perte"),
}

/** One photo, or a burst of photos around the playhead. */
enum class CaptureMode(val label: String) {
    SINGLE("Cette image"),
    BURST("Rafale ×${CapturePlan.BURST_COUNT}"),
}

object CapturePlan {
    /** Frame rate assumed when the file does not tell. */
    const val DEFAULT_FPS = 30f

    /** Frames scored by « Image la plus nette » (the playhead's and five on each side). */
    const val HUNT_COUNT = 11

    /** Photos of a burst, [BURST_SPACING] frames apart. */
    const val BURST_COUNT = 5
    const val BURST_SPACING = 3

    /** JPG quality of the photos. */
    const val JPG_QUALITY = 95

    /**
     * Frame rate of a video: frames counted by the file over its duration, else the capture rate (slow motion),
     * else [DEFAULT_FPS]. Kept between 1 and 240.
     */
    fun fps(frameCount: Int?, durationMs: Long, captureRate: Float?): Float {
        val counted = if (frameCount != null && frameCount > 1 && durationMs > 0) frameCount * 1_000f / durationMs else null
        val rate = counted ?: captureRate?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_FPS
        return rate.coerceIn(1f, 240f)
    }

    /** Index of the last frame of a video of [durationMs]. */
    fun lastIndex(durationMs: Long, fps: Float): Long = max(0L, ceil(durationMs.coerceAtLeast(0L) * fps / 1_000.0).toLong() - 1)

    /** Index of the frame shown at [ms]. */
    fun frameIndex(ms: Long, fps: Float): Long = floor(ms.coerceAtLeast(0L) * fps / 1_000.0 + 1e-6).toLong()

    /** Middle of frame [index] in ms, the time asked to the decoder so it lands on that frame and no other. */
    fun msOfFrame(index: Long, fps: Float): Long = ((index.coerceAtLeast(0L) + 0.5) * 1_000.0 / fps).roundToLong()

    /** [ms] moved to the middle of its frame, inside the video. */
    fun snap(ms: Long, fps: Float, durationMs: Long): Long {
        val index = frameIndex(ms, fps).coerceIn(0L, lastIndex(durationMs, fps))
        return msOfFrame(index, fps).coerceAtMost(max(0L, durationMs - 1))
    }

    /** [frames] images later (or earlier when negative) than [ms], stopping at the first and last frames. */
    fun step(ms: Long, frames: Long, fps: Float, durationMs: Long): Long {
        val index = (frameIndex(ms, fps) + frames).coerceIn(0L, lastIndex(durationMs, fps))
        return msOfFrame(index, fps).coerceAtMost(max(0L, durationMs - 1))
    }

    /** [ms] moved by [deltaMs] (one second for the « ±1 s » buttons), on a frame. */
    fun jump(ms: Long, deltaMs: Long, fps: Float, durationMs: Long): Long = snap((ms + deltaMs).coerceIn(0L, max(0L, durationMs - 1)), fps, durationMs)

    /**
     * [count] frames centred on the one at [ms], [spacing] frames apart, in time order. Near the start or the end of
     * the video the window slides inwards; a video shorter than the window gives each of its frames once.
     */
    fun around(ms: Long, count: Int, spacing: Int, fps: Float, durationMs: Long): List<Long> {
        val last = lastIndex(durationMs, fps)
        val gap = spacing.coerceAtLeast(1).toLong()
        val n = count.coerceAtLeast(1)
        val span = (n - 1) * gap
        val centre = frameIndex(ms, fps).coerceIn(0L, last)
        val first = (centre - span / 2).coerceAtMost(last - span).coerceAtLeast(0L)
        return (0 until n)
            .map { (first + it * gap).coerceAtMost(last) }
            .distinct()
            .map { msOfFrame(it, fps).coerceAtMost(max(0L, durationMs - 1)) }
    }

    /** Brightness (0..255) of ARGB pixels. */
    fun luma(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val c = argb[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        (r * 299 + g * 587 + b * 114) / 1_000
    }

    /**
     * Sharpness of a picture of [width] × [height] brightness values: the variance of its Laplacian (the sum of the
     * four neighbours minus four times the pixel). Edges that are crisp give big swings, a blurred or moving frame
     * gives small ones. Only comparable between pictures of the same size (the frames are scored at one size).
     */
    fun sharpness(luma: IntArray, width: Int, height: Int): Double {
        require(luma.size >= width * height) { "Image trop petite" }
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var squares = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = luma[i - 1] + luma[i + 1] + luma[i - width] + luma[i + width] - 4 * luma[i]
                sum += lap
                squares += lap.toDouble() * lap
                n++
            }
        }
        val mean = sum / n
        return (squares / n - mean * mean).coerceAtLeast(0.0)
    }

    /** Index of the sharpest score (the first one on a tie, so the playhead stays when nothing is better). */
    fun best(scores: List<Double>): Int {
        require(scores.isNotEmpty()) { "Aucune image" }
        var bestIndex = 0
        scores.forEachIndexed { i, s -> if (s > scores[bestIndex]) bestIndex = i }
        return bestIndex
    }

    /** The frame the hunt keeps: the sharpest, unless the playhead's own frame is within 3 % of it. */
    fun pick(candidates: List<Long>, scores: List<Double>, currentMs: Long): Long {
        require(candidates.size == scores.size && candidates.isNotEmpty()) { "Scores incomplets" }
        val best = best(scores)
        val here = candidates.indexOf(currentMs)
        if (here >= 0 && scores[here] >= scores[best] * 0.97) return currentMs
        return candidates[best]
    }

    /** How much sharper [after] is than [before], in percent (0 when not sharper). */
    fun gainPercent(before: Double, after: Double): Int {
        if (after <= before) return 0
        if (before <= 0.0) return 100
        return ((after / before - 1.0) * 100).roundToInt().coerceAtMost(999)
    }

    /** A score on a 0..1 gauge (a frame scored at 320 px: 0 flat, ~0.5 ordinary, near 1 very crisp). */
    fun level(score: Double): Float = (score / (score + SHARP_HALF)).toFloat().coerceIn(0f, 1f)

    /** Word under the gauge. */
    fun verdict(level: Float): String = when {
        level < 0.25f -> "Floue"
        level < 0.5f -> "Un peu floue"
        level < 0.75f -> "Nette"
        else -> "Très nette"
    }

    /** "0:12.345", "1:02:03.040" (milliseconds). */
    fun timecode(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        val total = v / 1_000
        val h = total / 3_600
        val m = (total / 60) % 60
        val s = total % 60
        val milli = v % 1_000
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, milli)
        } else {
            String.format(Locale.ROOT, "%d:%02d.%03d", m, s, milli)
        }
    }

    /** "0m12s345", "1h02m03s040": the moment in a file name. */
    fun stamp(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        val total = v / 1_000
        val h = total / 3_600
        val m = (total / 60) % 60
        val s = total % 60
        val milli = v % 1_000
        return if (h > 0) {
            String.format(Locale.ROOT, "%dh%02dm%02ds%03d", h, m, s, milli)
        } else {
            String.format(Locale.ROOT, "%dm%02ds%03d", m, s, milli)
        }
    }

    /** "concert (0m12s345).jpg". */
    fun outputName(sourceName: String, ms: Long, format: CaptureFormat): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName)).ifBlank { "video" }
        val suffix = " (${stamp(ms)}).${format.ext}"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }

    /** Size a frame is decoded at to be shown or scored: its longest side brought to [maxSide], never enlarged. */
    fun fit(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val longest = max(w, h)
        if (longest <= maxSide) return w to h
        val scale = maxSide.toDouble() / longest
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    /** Score at which the gauge is half full. */
    private const val SHARP_HALF = 120.0
}
