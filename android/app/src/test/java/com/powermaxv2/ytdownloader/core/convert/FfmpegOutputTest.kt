package com.powermaxv2.ytdownloader.core.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegOutputTest {
    @Test fun durationFromTheLog() {
        assertEquals(192.48, FfmpegProgress.parseDuration("  Duration: 00:03:12.48, start: 0.000000, bitrate: 1411 kb/s")!!, 1e-9)
        assertEquals(3723.5, FfmpegProgress.parseDuration("  Duration: 01:02:03.50, start: 0.0")!!, 1e-9)
        assertNull(FfmpegProgress.parseDuration("  Duration: N/A, bitrate: N/A"))
        assertNull(FfmpegProgress.parseDuration("  Duration: 00:00:00.00, start: 0"))
        assertNull(FfmpegProgress.parseDuration("Stream #0:0: Video: h264"))
    }

    @Test fun positionFromProgressLines() {
        assertEquals(12.345678, FfmpegProgress.parseOutTime("out_time_us=12345678")!!, 1e-9)
        // out_time_ms is in microseconds too (FFmpeg quirk).
        assertEquals(1.5, FfmpegProgress.parseOutTime("out_time_ms=1500000")!!, 1e-9)
        assertEquals(0.0, FfmpegProgress.parseOutTime("out_time_us=-23000")!!, 1e-9)
        assertNull(FfmpegProgress.parseOutTime("out_time_us=N/A"))
        assertNull(FfmpegProgress.parseOutTime("out_time=00:00:12.345678"))
        assertNull(FfmpegProgress.parseOutTime("frame=120"))
        assertTrue(FfmpegProgress.isEnd("progress=end"))
        assertFalse(FfmpegProgress.isEnd("progress=continue"))
    }

    @Test fun fractions() {
        assertEquals(0.5f, FfmpegProgress.fraction(30.0, 60.0)!!, 1e-6f)
        assertEquals(1f, FfmpegProgress.fraction(90.0, 60.0)!!, 1e-6f)
        assertNull(FfmpegProgress.fraction(30.0, null))
        assertNull(FfmpegProgress.fraction(30.0, 0.0))
    }

    @Test fun errorMessages() {
        assertEquals(
            "Ce fichier n'a pas de son à extraire.",
            FfmpegErrors.message(listOf("Stream map '0:a:0' matches no streams.", "To ignore this, add a trailing '?' to the map."), Target.MP3),
        )
        assertEquals(
            "Ce fichier est illisible ou abîmé.",
            FfmpegErrors.message(listOf("/w/a.mp4: Invalid data found when processing input"), Target.MKV),
        )
        assertEquals(
            "Plus assez d'espace libre sur le téléphone.",
            FfmpegErrors.message(listOf("av_interleaved_write_frame(): No space left on device"), Target.MP4),
        )
        assertEquals(
            "Ce format ne peut pas contenir ces pistes : essayez MP4 ou MKV.",
            FfmpegErrors.message(listOf("[avi @ 0x7] Could not find tag for codec opus in stream #1, codec not currently supported in container"), Target.AVI),
        )
        assertEquals(FfmpegErrors.GENERIC, FfmpegErrors.message(listOf("Conversion failed!"), Target.MP4))
        assertEquals(FfmpegErrors.GENERIC, FfmpegErrors.message(emptyList(), null))
    }
}
