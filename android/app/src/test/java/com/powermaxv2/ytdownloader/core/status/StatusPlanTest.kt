package com.powermaxv2.ytdownloader.core.status

import com.powermaxv2.ytdownloader.core.trim.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPlanTest {
    @Test fun countRoundsUp() {
        assertEquals(0, StatusPlan.count(0, StatusLength.MINUTE))
        assertEquals(1, StatusPlan.count(60_000, StatusLength.MINUTE))
        assertEquals(2, StatusPlan.count(60_001, StatusLength.MINUTE))
        assertEquals(3, StatusPlan.count(130_000, StatusLength.MINUTE))
        assertEquals(5, StatusPlan.count(130_000, StatusLength.HALF_MINUTE))
    }

    @Test fun equalPartsAreTheSameLengthAndEndToEnd() {
        val parts = StatusPlan.parts(130_000, StatusLength.MINUTE, equal = true)
        assertEquals(listOf(TrimRange(0, 43_333), TrimRange(43_333, 86_666), TrimRange(86_666, 130_000)), parts)
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        parts.forEach { assertTrue(it.durationMs <= 60_000) }
    }

    @Test fun fixedPartsLeaveTheRestAtTheEnd() {
        assertEquals(
            listOf(TrimRange(0, 60_000), TrimRange(60_000, 120_000), TrimRange(120_000, 130_000)),
            StatusPlan.parts(130_000, StatusLength.MINUTE, equal = false),
        )
        // A rest under a second is not worth a status.
        assertEquals(
            listOf(TrimRange(0, 30_000), TrimRange(30_000, 60_000)),
            StatusPlan.parts(60_400, StatusLength.HALF_MINUTE, equal = false),
        )
    }

    @Test fun everyPartFitsInAStatus() {
        listOf(61_000L, 119_999L, 600_000L, 3_599_999L).forEach { duration ->
            StatusLength.entries.forEach { length ->
                listOf(true, false).forEach { equal ->
                    val parts = StatusPlan.parts(duration, length, equal)
                    assertEquals(0L, parts.first().startMs)
                    assertTrue(duration - parts.last().endMs < StatusPlan.MIN_LAST_MS)
                    parts.forEach { assertTrue("$duration $length $equal", it.durationMs in 1..length.ms) }
                }
            }
        }
    }

    @Test fun shortOrHugeVideosAreRefused() {
        assertNotNull(StatusPlan.refusal(45_000, StatusLength.MINUTE))
        assertNotNull(StatusPlan.refusal(60_000, StatusLength.MINUTE))
        assertNull(StatusPlan.refusal(61_000, StatusLength.MINUTE))
        assertNull(StatusPlan.refusal(45_000, StatusLength.HALF_MINUTE))
        assertNull(StatusPlan.refusal(60L * 60_000, StatusLength.MINUTE))
        assertNotNull(StatusPlan.refusal(60L * 60_000 + 1, StatusLength.MINUTE))
    }

    @Test fun aCopyRunningPastTheLimitIsCutAgain() {
        assertFalse(StatusPlan.tooLong(60_000, StatusLength.MINUTE))
        assertFalse(StatusPlan.tooLong(60_250, StatusLength.MINUTE))
        assertTrue(StatusPlan.tooLong(61_500, StatusLength.MINUTE))
    }

    @Test fun namesSortInOrder() {
        assertEquals("vacances (statut 1 sur 3).mp4", StatusPlan.outputName("vacances.mp4", 0, 3, "mp4"))
        assertEquals("vacances (statut 04 sur 12).mp4", StatusPlan.outputName("vacances.MOV", 3, 12, "MP4"))
        assertEquals("fichier (statut 2 sur 2).mkv", StatusPlan.outputName("", 1, 2, "mkv"))
        val long = StatusPlan.outputName("x".repeat(400) + ".mp4", 0, 2, "mp4")
        assertTrue(long.endsWith(" (statut 1 sur 2).mp4"))
    }

    @Test fun shortTime() {
        assertEquals("0:43", StatusPlan.short(43_333))
        assertEquals("1:00", StatusPlan.short(60_000))
    }
}
