package com.powermaxv2.ytdownloader.core.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MemosTest {
    @Test fun defaultTitleSaysDayAndHour() {
        assertEquals("Mémo 14 oct. 19h20", Memos.defaultTitle(LocalDateTime.of(2026, 10, 14, 19, 20)))
        assertEquals("Mémo 3 mai 08h05", Memos.defaultTitle(LocalDateTime.of(2026, 5, 3, 8, 5)))
        assertEquals("Mémo 14 oct. 19h20.m4a", Memos.fileName(Memos.defaultTitle(LocalDateTime.of(2026, 10, 14, 19, 20))))
    }

    @Test fun typedNamesAreMadeSafe() {
        assertEquals("Idée cadeau", Memos.cleanTitle("  Idée   cadeau "))
        assertEquals("a_b", Memos.cleanTitle("a/b"))
        assertEquals("cache", Memos.cleanTitle("..cache"))
        assertNull(Memos.cleanTitle("   "))
        assertNull(Memos.cleanTitle("..."))
        assertEquals(Memos.MAX_TITLE, Memos.cleanTitle("x".repeat(200))!!.length)
    }

    @Test fun takenNamesGetANumber() {
        assertEquals("Note.m4a", Memos.unique("Note.m4a", listOf("Autre.m4a")))
        assertEquals("Note (2).m4a", Memos.unique("Note.m4a", listOf("note.M4A")))
        assertEquals("Note (3).m4a", Memos.unique("Note.m4a", listOf("Note.m4a", "Note (2).m4a")))
        assertEquals("Mémo 14 oct. 19h20 (2).m4a", Memos.unique("Mémo 14 oct. 19h20.m4a", listOf("Mémo 14 oct. 19h20.m4a")))
        assertEquals("Mémo 14 oct. 19h20", MemoInfo("Mémo 14 oct. 19h20.m4a", 0, 0, 0).title)
    }

    @Test fun durations() {
        assertEquals("0:00", Memos.duration(0))
        assertEquals("0:07", Memos.duration(7_400))
        assertEquals("12:03", Memos.duration(723_000))
        assertEquals("1:02:03", Memos.duration(3_723_000))
        assertEquals("0:00", Memos.duration(-5))
        assertEquals("4", Memos.tenths(7_450))
    }

    @Test fun sizes() {
        assertEquals("900 o", Memos.size(900))
        assertEquals("47 Ko", Memos.size(48_200))
        assertEquals("1,2 Mo", Memos.size(1_260_000))
    }

    @Test fun whenTextIsRelative() {
        val now = LocalDateTime.of(2026, 10, 14, 20, 0)
        fun at(d: LocalDateTime) = d.toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("Aujourd'hui, 19h20", Memos.whenText(at(LocalDateTime.of(2026, 10, 14, 19, 20)), now, ZoneOffset.UTC))
        assertEquals("Hier, 08h05", Memos.whenText(at(LocalDateTime.of(2026, 10, 13, 8, 5)), now, ZoneOffset.UTC))
        assertEquals("2 oct., 10h00", Memos.whenText(at(LocalDateTime.of(2026, 10, 2, 10, 0)), now, ZoneOffset.UTC))
        assertEquals("2 oct. 2025", Memos.whenText(at(LocalDateTime.of(2025, 10, 2, 10, 0)), now, ZoneOffset.UTC))
    }

    @Test fun levelsFollowDecibels() {
        assertEquals(0f, Memos.level(0))
        assertEquals(0f, Memos.level(-3))
        assertEquals(1f, Memos.level(32767))
        assertEquals(1f, Memos.level(90_000))
        val whisper = Memos.level(300)
        val talk = Memos.level(5_000)
        assertTrue(whisper > 0f && whisper < talk && talk < 1f)
    }

    @Test fun downsampleKeepsPeaks() {
        assertEquals(emptyList<Float>(), Memos.downsample(emptyList(), 10))
        assertEquals(listOf(0.1f, 0.2f), Memos.downsample(listOf(0.1f, 0.2f), 10))
        assertEquals(listOf(0.9f, 0.5f), Memos.downsample(listOf(0.1f, 0.9f, 0.2f, 0.5f), 2))
        assertEquals(240, Memos.downsample(List(10_000) { 0.5f }, 240).size)
    }

    @Test fun metaRoundTrips() {
        val wave = List(1_000) { (it % 10) / 9f }
        val (ms, back) = Memos.decodeMeta(Memos.encodeMeta(42_500, wave))!!
        assertEquals(42_500L, ms)
        assertEquals(Memos.MAX_WAVE, back.size)
        assertTrue(back.all { it in 0f..1f })
        assertNull(Memos.decodeMeta(null))
        assertNull(Memos.decodeMeta(ByteArray(3)))
        assertEquals(0L to emptyList<Float>(), Memos.decodeMeta(Memos.encodeMeta(0, emptyList())))
    }

    @Test fun newestFirstAndCounts() {
        val a = MemoInfo("a.m4a", 2_000, 10, createdAt = 1)
        val b = MemoInfo("b.m4a", 40_000, 10, createdAt = 5)
        assertEquals(listOf("b.m4a", "a.m4a"), Memos.sorted(listOf(a, b)).map { it.fileName })
        assertEquals("Aucun mémo", Memos.countText(emptyList()))
        assertEquals("1 mémo · 0:02", Memos.countText(listOf(a)))
        assertEquals("2 mémos · 0:42", Memos.countText(listOf(a, b)))
        assertEquals("b", b.title)
    }

    @Test fun seekAndSpeed() {
        assertEquals(5_000L, Memos.seekTo(0.5f, 10_000))
        assertEquals(10_000L, Memos.seekTo(3f, 10_000))
        assertEquals(0L, Memos.seekTo(-1f, 10_000))
        assertEquals(1.5f, Memos.nextSpeed(1f))
        assertEquals(1f, Memos.nextSpeed(0.75f))
        assertEquals(1.5f, Memos.nextSpeed(3f))
        assertEquals("1,5×", Memos.speedText(1.5f))
        assertEquals("2×", Memos.speedText(2f))
        assertEquals("0,75×", Memos.speedText(0.75f))
    }
}
