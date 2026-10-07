package com.powermaxv2.ytdownloader.core.preview

import kotlin.math.abs
import kotlin.math.sqrt

// Waveform of a sound file: the decoder (WaveformDecoder) measures the loudest sample of every ~20 ms slice;
// these functions turn the slices into a fixed number of bars. Pure, unit tested.

/**
 * Peaks of the decoded slices: slice i starts at [timeAt] (µs) and its loudest sample is [peakAt] (0..1). Holds at
 * most [maxSize] slices ([Waveform.MAX_SLICES]): further ones are dropped and [full] tells the decoder to stop.
 */
class PeakSeries(initialCapacity: Int = 1024, val maxSize: Int = Waveform.MAX_SLICES) {
    var size = 0
        private set
    private var times = LongArray(initialCapacity.coerceIn(1, maxSize.coerceAtLeast(1)))
    private var values = FloatArray(times.size)

    val full: Boolean get() = size >= maxSize

    fun add(timeUs: Long, peak: Float) {
        if (full) return
        if (size == times.size) {
            val grown = minOf(size.toLong() * 2, maxSize.toLong()).toInt()
            times = times.copyOf(grown)
            values = values.copyOf(grown)
        }
        times[size] = timeUs
        values[size] = peak.coerceIn(0f, 1f)
        size++
    }

    fun timeAt(i: Int): Long = times[i]
    fun peakAt(i: Int): Float = values[i]
    val lastTimeUs: Long get() = if (size == 0) 0L else times[size - 1]
}

object Waveform {
    /** Bars drawn. */
    const val BUCKETS = 400

    /** Sound decoded at most for the waveform: 10 minutes … */
    const val MAX_DURATION_US = 10L * 60 * 1_000_000

    /** … or 64 Mio of decoded PCM, whichever comes first. */
    const val MAX_PCM_BYTES = 64L * 1024 * 1024

    /** Length of a measured slice. */
    const val SLICE_US = 20_000L

    /** Most slices kept: twice what [MAX_DURATION_US] needs (a file whose timestamps lie cannot ask for more). */
    const val MAX_SLICES = (MAX_DURATION_US / SLICE_US * 2).toInt()

    /** Sample rates used to size a slice: a declared rate outside this range is a damaged or hostile file. */
    const val MIN_SLICE_RATE = 8_000
    const val MAX_SLICE_RATE = 384_000

    /** Frames in one ~20 ms slice at [sampleRate], the rate clamped to [MIN_SLICE_RATE]..[MAX_SLICE_RATE]. */
    fun sliceFrames(sampleRate: Int): Int = (sampleRate.coerceIn(MIN_SLICE_RATE, MAX_SLICE_RATE) * SLICE_US / 1_000_000L).toInt()

    /**
     * [n] bars over [durationUs] of sound: each bar is the loudest slice that covers it, a slice longer than a bar
     * fills every bar it covers (short sounds), bars after the decoded part stay 0. Scaled so the loudest bar is 1,
     * with a square root so quiet passages stay visible.
     */
    fun buckets(series: PeakSeries, durationUs: Long, n: Int = BUCKETS): FloatArray {
        val out = FloatArray(n)
        if (series.size == 0 || n <= 0) return out
        val total = durationUs.takeIf { it > 0 } ?: (series.lastTimeUs + SLICE_US)
        for (i in 0 until series.size) {
            val t = series.timeAt(i)
            val next = if (i + 1 < series.size) series.timeAt(i + 1) else t + SLICE_US
            val first = index(t, total, n)
            val last = (index(maxOf(next, t + 1) - 1, total, n)).coerceAtLeast(first)
            val p = series.peakAt(i)
            for (b in first..last) if (p > out[b]) out[b] = p
        }
        val max = out.maxOrNull() ?: 0f
        if (max <= 0f) return out
        for (b in out.indices) out[b] = sqrt(out[b] / max)
        return out
    }

    /** Bar of time [t]. */
    private fun index(t: Long, total: Long, n: Int): Int =
        ((t.coerceAtLeast(0).toDouble() / total) * n).toInt().coerceIn(0, n - 1)

    /** Loudest of 16-bit samples [from, to) as 0..1. */
    fun peak16(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
        var max = 0
        for (i in from until to) {
            val v = abs(samples[i].toInt())
            if (v > max) max = v
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }

    /** Loudest of float samples [from, to) as 0..1. */
    fun peakFloat(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        var max = 0f
        for (i in from until to) {
            val v = abs(samples[i])
            if (v > max) max = v
        }
        return max.coerceIn(0f, 1f)
    }

    /** Fraction 0..1 of the bars already played at [positionMs] of [durationMs]. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
