package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FileCopyTest {
    @Test fun copiesEverythingUnderTheCap() {
        val data = ByteArray(700_000) { it.toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(700_000L, FileIntake.copyCapped(ByteArrayInputStream(data), out, 1_000_000))
        assertTrue(out.toByteArray().contentEquals(data))
    }

    @Test(expected = FileTooLarge::class)
    fun stopsOverTheCap() {
        FileIntake.copyCapped(ByteArrayInputStream(ByteArray(600_000)), ByteArrayOutputStream(), 500_000)
    }

    @Test fun aThrowingCheckpointStopsTheCopy() {
        val out = ByteArrayOutputStream()
        var calls = 0
        try {
            FileIntake.copyCapped(ByteArrayInputStream(ByteArray(10_000_000)), out, Long.MAX_VALUE) {
                if (++calls == 3) throw IllegalStateException("cancelled")
            }
            fail("the copy should have stopped")
        } catch (e: IllegalStateException) {
            // Two 256 Kio blocks copied, then stopped before the third.
            assertEquals(2 * 256 * 1024, out.size())
        }
    }
}
