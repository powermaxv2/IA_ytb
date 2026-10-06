package com.powermaxv2.ytdownloader.core.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    @Test fun bucketsTakeTheLoudestSliceAndScaleToOne() {
        val s = PeakSeries(2)
        // 4 slices of 250 ms over 1 s, 4 bars: one slice per bar.
        s.add(0, 0.25f)
        s.add(250_000, 1f)
        s.add(500_000, 0f)
        s.add(750_000, 0.5f)
        val b = Waveform.buckets(s, 1_000_000, 4)
        assertArrayEquals(floatArrayOf(0.5f, 1f, 0f, kotlin.math.sqrt(0.5f)), b, 1e-4f)
    }

    @Test fun longSlicesFillEveryBarTheyCover() {
        val s = PeakSeries()
        s.add(0, 0.5f)
        s.add(500_000, 1f)
        val b = Waveform.buckets(s, 1_000_000, 10)
        for (i in 0 until 5) assertEquals(kotlin.math.sqrt(0.5f), b[i], 1e-4f)
        // The last slice only covers its own 20 ms: bar 5.
        assertEquals(1f, b[5], 1e-4f)
        assertEquals(0f, b[9], 0f)
    }

    @Test fun decodedPartOnlyAndEmptyInput() {
        val s = PeakSeries()
        for (i in 0 until 50) s.add(i * 20_000L, 0.8f)
        // One second decoded of a ten second sound: bars after the first tenth stay 0.
        val b = Waveform.buckets(s, 10_000_000, 100)
        assertEquals(1f, b[0], 1e-4f)
        assertEquals(1f, b[9], 1e-4f)
        assertEquals(0f, b[10], 0f)
        assertEquals(0f, b[99], 0f)
        assertArrayEquals(FloatArray(5), Waveform.buckets(PeakSeries(), 1000, 5), 0f)
        // Unknown duration: the series' own length.
        val u = Waveform.buckets(s, 0, 10)
        assertEquals(1f, u[9], 1e-4f)
    }

    @Test fun peaks() {
        assertEquals(1f, Waveform.peak16(shortArrayOf(0, -32768, 100)), 0f)
        assertEquals(0.5f, Waveform.peak16(shortArrayOf(16384, -100)), 1e-4f)
        assertEquals(0f, Waveform.peak16(shortArrayOf(5, 32767), 0, 1), 1e-3f)
        assertEquals(0.75f, Waveform.peakFloat(floatArrayOf(0.1f, -0.75f)), 0f)
        assertEquals(1f, Waveform.peakFloat(floatArrayOf(3f)), 0f)
        assertEquals(0.5f, Waveform.progress(500, 1000), 0f)
        assertEquals(0f, Waveform.progress(500, 0), 0f)
    }
}
