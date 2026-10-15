package com.powermaxv2.ytdownloader.core.readaloud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavJoinTest {
    @get:Rule val tmp = TemporaryFolder()

    private val mono = WavFormat(1, 1, 24_000, 16)

    private fun wav(format: WavFormat, samples: ByteArray, extraChunk: Boolean = false, sizeOverride: Int? = null): File {
        val file = tmp.newFile()
        val header = WavJoin.header(format, samples.size.toLong())
        file.outputStream().use { out ->
            if (extraChunk) {
                // RIFF/WAVE, then a LIST chunk of odd size (padded), then fmt and data.
                out.write(header, 0, 12)
                out.write("LIST".toByteArray())
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(3).array())
                out.write(byteArrayOf(1, 2, 3, 0))
                out.write(header, 12, 24)
            } else {
                out.write(header, 0, 36)
            }
            out.write("data".toByteArray())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sizeOverride ?: samples.size).array())
            out.write(samples)
        }
        return file
    }

    @Test fun readsHeaderPastOtherChunks() {
        val info = WavJoin.read(wav(mono, ByteArray(480), extraChunk = true))
        assertEquals(mono, info.format)
        assertEquals(12L + 12 + 24 + 8, info.dataOffset)
        assertEquals(480L, info.dataSize)
        assertEquals(10L, info.durationMs)
    }

    @Test fun streamedSizeMeansRestOfFile() {
        assertEquals(100L, WavJoin.read(wav(mono, ByteArray(100), sizeOverride = 0)).dataSize)
        assertEquals(100L, WavJoin.read(wav(mono, ByteArray(100), sizeOverride = -1)).dataSize)
        // Half a sample at the end is dropped.
        assertEquals(100L, WavJoin.read(wav(mono, ByteArray(101), sizeOverride = 0)).dataSize)
    }

    @Test fun joinsSamplesWithSilenceBetween() {
        val a = wav(mono, ByteArray(4) { 1 })
        val b = wav(mono, ByteArray(6) { 2 }, extraChunk = true)
        val out = tmp.newFile()
        val info = WavJoin.join(listOf(a, b), out, gapMs = 1)
        val gap = 24 * 2
        assertEquals(4L + gap + 6, info.dataSize)
        val bytes = out.readBytes()
        assertEquals(44 + 4 + gap + 6, bytes.size)
        assertArrayEquals(WavJoin.header(mono, info.dataSize), bytes.copyOf(44))
        assertArrayEquals(ByteArray(4) { 1 } + ByteArray(gap) + ByteArray(6) { 2 }, bytes.copyOfRange(44, bytes.size))
        assertEquals(info, WavJoin.read(out))
    }

    @Test fun eightBitSilenceIsMidScale() {
        val f8 = WavFormat(1, 1, 8_000, 8)
        val out = tmp.newFile()
        WavJoin.join(listOf(wav(f8, byteArrayOf(5)), wav(f8, byteArrayOf(6))), out, gapMs = 1)
        val bytes = out.readBytes()
        assertEquals(0x80.toByte(), bytes[45])
        assertEquals(44 + 1 + 8 + 1, bytes.size)
    }

    @Test fun refusesMixedFormatsAndNonWav() {
        val stereo = WavFormat(1, 2, 24_000, 16)
        try {
            WavJoin.join(listOf(wav(mono, ByteArray(4)), wav(stereo, ByteArray(4))), tmp.newFile())
            fail()
        } catch (e: IOException) {
            assertEquals("La voix a changé de format en cours de route", e.message)
        }
        val junk = tmp.newFile().apply { writeText("pas un wav du tout") }
        try {
            WavJoin.read(junk)
            fail()
        } catch (e: IOException) {
            assertEquals("Ce n'est pas un fichier WAV", e.message)
        }
    }

    @Test fun headerFields() {
        val h = ByteBuffer.wrap(WavJoin.header(mono, 1000)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1036, h.getInt(4))
        assertEquals(48_000, h.getInt(28))
        assertEquals(2.toShort(), h.getShort(32))
        assertEquals(1000, h.getInt(40))
    }
}
