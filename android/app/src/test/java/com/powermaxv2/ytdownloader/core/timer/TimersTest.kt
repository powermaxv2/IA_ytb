package com.powermaxv2.ytdownloader.core.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimersTest {
    private val t0 = 1_700_000_000_000L

    @Test fun addStartsAndNamesTimers() {
        var list = Timers.add(emptyList(), "  Pâtes ", 9 * 60_000L, t0)
        list = Timers.add(list, "", 60_000L, t0)
        assertEquals(listOf("Pâtes", "Minuteur 2"), list.map { it.name })
        assertEquals(listOf(1L, 2L), list.map { it.id })
        assertEquals(t0 + 9 * 60_000L, list[0].endsAt)
        assertEquals(9 * 60_000L, list[0].left(t0))
        assertEquals(4 * 60_000L, list[0].left(t0 + 5 * 60_000L))
    }

    @Test fun durationIsClampedAndCountLimited() {
        val one = Timers.add(emptyList(), "x", 10L, t0)
        assertEquals(1000L, one[0].durationMs)
        val big = Timers.add(emptyList(), "x", Long.MAX_VALUE, t0)
        assertEquals(Timers.MAX_DURATION_MS, big[0].durationMs)
        var list = emptyList<TimerItem>()
        repeat(20) { list = Timers.add(list, "t$it", 60_000, t0) }
        assertEquals(Timers.MAX_COUNT, list.size)
    }

    @Test fun pauseKeepsWhatIsLeftAndStartResumes() {
        var list = Timers.add(emptyList(), "Four", 10 * 60_000L, t0)
        list = Timers.pause(list, 1, t0 + 3 * 60_000L)
        assertFalse(list[0].running)
        assertEquals(7 * 60_000L, list[0].left(t0 + 99 * 60_000L))
        assertTrue(list[0].touched(t0))
        list = Timers.start(list, 1, t0 + 20 * 60_000L)
        assertEquals(t0 + 27 * 60_000L, list[0].endsAt)
    }

    @Test fun ringingThenStopResetsToFullDuration() {
        var list = Timers.add(emptyList(), "Thé", 3 * 60_000L, t0)
        val after = t0 + 3 * 60_000L
        assertTrue(list[0].ringing(after))
        assertEquals(0L, list[0].left(after + 5_000))
        assertEquals(1f, list[0].progress(after))
        // A ringing timer cannot be "paused" at zero.
        assertEquals(list, Timers.pause(list, 1, after))
        list = Timers.reset(list, 1)
        assertFalse(list[0].running)
        assertEquals(3 * 60_000L, list[0].left(after))
        assertFalse(list[0].touched(after))
    }

    @Test fun plusMinuteSnoozesOrExtends() {
        var list = Timers.add(emptyList(), "Œufs", 60_000L, t0)
        list = Timers.plusMinute(list, 1, t0 + 10_000)
        assertEquals(t0 + 120_000, list[0].endsAt)
        val ring = t0 + 200_000
        list = Timers.plusMinute(list, 1, ring)
        assertEquals(ring + 60_000, list[0].endsAt)
        list = Timers.pause(list, 1, ring)
        list = Timers.plusMinute(list, 1, ring)
        assertEquals(120_000L, list[0].leftMs)
    }

    @Test fun nextIsTheSoonestRunning() {
        var list = Timers.add(emptyList(), "a", 10 * 60_000L, t0)
        list = Timers.add(list, "b", 2 * 60_000L, t0)
        list = Timers.add(list, "c", 60_000L, t0, start = false)
        assertEquals("b", Timers.next(list, t0)?.name)
        assertEquals("a", Timers.next(list, t0 + 3 * 60_000L)?.name)
        assertNull(Timers.next(Timers.remove(list, 1), t0 + 3 * 60_000L))
    }

    @Test fun stopwatchCountsAcrossPauses() {
        var w = Timers.startWatch(Stopwatch(), t0)
        assertEquals(5_000L, w.elapsed(t0 + 5_000))
        w = Timers.pauseWatch(w, t0 + 5_000)
        assertEquals(5_000L, w.elapsed(t0 + 60_000))
        w = Timers.startWatch(w, t0 + 60_000)
        assertEquals(7_500L, w.elapsed(t0 + 62_500))
        assertFalse(w.idle)
        assertTrue(Timers.resetWatch().idle)
    }

    @Test fun lapsMarkFastestAndSlowest() {
        var w = Timers.startWatch(Stopwatch(), t0)
        w = Timers.lap(w, t0 + 30_000) // 30 s
        w = Timers.lap(w, t0 + 55_000) // 25 s
        w = Timers.lap(w, t0 + 95_000) // 40 s
        val laps = Timers.laps(w)
        assertEquals(listOf(3, 2, 1), laps.map { it.number })
        assertEquals(listOf(40_000L, 25_000L, 30_000L), laps.map { it.splitMs })
        assertTrue(laps.first { it.number == 2 }.best)
        assertTrue(laps.first { it.number == 3 }.worst)
        assertFalse(laps.first { it.number == 1 }.best || laps.first { it.number == 1 }.worst)
        // One lap: neither best nor worst. A paused watch takes no lap.
        val one = Timers.laps(Timers.lap(Timers.startWatch(Stopwatch(), t0), t0 + 1000))
        assertFalse(one[0].best || one[0].worst)
        assertEquals(w.laps, Timers.lap(Timers.pauseWatch(w, t0 + 99_000), t0 + 100_000).laps)
        assertEquals("Tour 1 : 00:30.00 (total 00:30.00)\nTour 2 : 00:25.00 (total 00:55.00)\nTour 3 : 00:40.00 (total 01:35.00)", Timers.lapsText(w))
    }

    @Test fun formats() {
        assertEquals("5:00", Timers.clock(300_000))
        assertEquals("4:05", Timers.clock(244_001))
        assertEquals("0:00", Timers.clock(0))
        assertEquals("0:01", Timers.clock(1))
        assertEquals("1:02:03", Timers.clock(3_723_000))
        assertEquals("00:07.42", Timers.watch(7_429))
        assertEquals("1:02:03.40", Timers.watch(3_723_400))
        assertEquals("1 min", Timers.words(60_000))
        assertEquals("2 min 30", Timers.words(150_000))
        assertEquals("1 h 30", Timers.words(5_400_000))
        assertEquals("1 h", Timers.words(3_600_000))
        assertEquals("45 s", Timers.words(45_000))
        assertEquals(Triple(1, 2, 3), Timers.parts(3_723_000))
        assertEquals(3_723_000L, Timers.ofParts(1, 2, 3))
        assertEquals(Timers.ofParts(23, 59, 59), Timers.ofParts(40, 99, 99))
    }

    @Test fun storageRoundTripsAndSkipsJunk() {
        var list = Timers.add(emptyList(), "Lessive | couleurs", 45 * 60_000L, t0)
        list = Timers.pause(Timers.add(list, "Sieste", 20 * 60_000L, t0), 2, t0 + 60_000)
        val back = Timers.decode(Timers.encode(list))
        assertEquals("Lessive / couleurs", back[0].name)
        assertEquals(list[0].endsAt, back[0].endsAt)
        assertEquals(list[1], back[1])
        assertEquals(1, Timers.decode("junk\n1|5000|-1|9999999|x\n2|0|-1|0|bad").size)
        assertEquals(5000L, Timers.decode("1|5000|-1|9999999|x")[0].leftMs)
        assertTrue(Timers.decode(null).isEmpty())

        val w = Timers.lap(Timers.startWatch(Stopwatch(), t0), t0 + 1234)
        assertEquals(w, Timers.decodeWatch(Timers.encodeWatch(w)))
        val paused = Timers.pauseWatch(w, t0 + 5000)
        assertEquals(paused, Timers.decodeWatch(Timers.encodeWatch(paused)))
        assertTrue(Timers.decodeWatch("nope").idle)
    }
}
