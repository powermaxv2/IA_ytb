package com.powermaxv2.ytdownloader.core.split

import com.powermaxv2.ytdownloader.core.trim.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlanTest {
    @Test fun countRoundsUp() {
        assertEquals(0, SplitPlan.count(0, SplitLength.MINUTE))
        assertEquals(1, SplitPlan.count(60_000, SplitLength.MINUTE))
        assertEquals(2, SplitPlan.count(60_001, SplitLength.MINUTE))
        assertEquals(3, SplitPlan.count(130_000, SplitLength.MINUTE))
        assertEquals(5, SplitPlan.count(130_000, SplitLength.HALF_MINUTE))
    }

    @Test fun equalPartsAreTheSameLengthAndEndToEnd() {
        val parts = SplitPlan.parts(130_000, SplitLength.MINUTE, equal = true)
        assertEquals(listOf(TrimRange(0, 43_333), TrimRange(43_333, 86_666), TrimRange(86_666, 130_000)), parts)
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        parts.forEach { assertTrue(it.durationMs <= 60_000) }
    }

    @Test fun fixedPartsLeaveTheRestAtTheEnd() {
        assertEquals(
            listOf(TrimRange(0, 60_000), TrimRange(60_000, 120_000), TrimRange(120_000, 130_000)),
            SplitPlan.parts(130_000, SplitLength.MINUTE, equal = false),
        )
        // A rest under a second is not worth a status.
        assertEquals(
            listOf(TrimRange(0, 30_000), TrimRange(30_000, 60_000)),
            SplitPlan.parts(60_400, SplitLength.HALF_MINUTE, equal = false),
        )
    }

    @Test fun everyPartFitsInAStatus() {
        listOf(61_000L, 119_999L, 600_000L, 3_599_999L).forEach { duration ->
            SplitLength.entries.forEach { length ->
                listOf(true, false).forEach { equal ->
                    val parts = SplitPlan.parts(duration, length, equal)
                    assertEquals(0L, parts.first().startMs)
                    assertTrue(duration - parts.last().endMs < SplitPlan.MIN_LAST_MS)
                    parts.forEach { assertTrue("$duration $length $equal", it.durationMs in 1..length.ms) }
                }
            }
        }
    }

    @Test fun shortOrHugeVideosAreRefused() {
        assertNotNull(SplitPlan.refusal(45_000, SplitLength.MINUTE))
        assertNotNull(SplitPlan.refusal(60_000, SplitLength.MINUTE))
        assertNull(SplitPlan.refusal(61_000, SplitLength.MINUTE))
        assertNull(SplitPlan.refusal(45_000, SplitLength.HALF_MINUTE))
        assertNull(SplitPlan.refusal(60L * 60_000, SplitLength.MINUTE))
        assertNotNull(SplitPlan.refusal(60L * 60_000 + 1, SplitLength.MINUTE))
    }

    @Test fun aCopyRunningPastTheLimitIsCutAgain() {
        assertFalse(SplitPlan.tooLong(60_000, SplitLength.MINUTE))
        assertFalse(SplitPlan.tooLong(60_250, SplitLength.MINUTE))
        assertTrue(SplitPlan.tooLong(61_500, SplitLength.MINUTE))
    }

    @Test fun namesSortInOrder() {
        assertEquals("vacances (statut 1 sur 3).mp4", SplitPlan.outputName("vacances.mp4", 0, 3, "mp4"))
        assertEquals("vacances (statut 04 sur 12).mp4", SplitPlan.outputName("vacances.MOV", 3, 12, "MP4"))
        assertEquals("fichier (statut 2 sur 2).mkv", SplitPlan.outputName("", 1, 2, "mkv"))
        val long = SplitPlan.outputName("x".repeat(400) + ".mp4", 0, 2, "mp4")
        assertTrue(long.endsWith(" (statut 1 sur 2).mp4"))
    }

    @Test fun shortTime() {
        assertEquals("0:43", SplitPlan.short(43_333))
        assertEquals("1:00", SplitPlan.short(60_000))
    }
}
