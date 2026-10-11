package com.powermaxv2.ytdownloader.core.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AnimatedWebpTest {
    // 16 × 16 still WebP made once with Pillow (libwebp): a red disc on transparent (VP8X + ALPH + VP8), a blue
    // disc on blue (simple lossy VP8) and a green disc on transparent (lossless VP8L).
    private val alpha = decode(
        "UklGRs4AAABXRUJQVlA4WAoAAAAQAAAADwAADwAAQUxQSCcAAAABDzD/ERFCUSSp0VQhAAkrBetI4nnlGRER/Q+AtPJW68XfapWnAQAAVlA4IIAAAACQBACdASoQABAAAUAmJbACdLoBRAPkA3gDYAN8A6wD0APAA+AD9mf0A9oAJUAA/vLrf80DtcllaSSGtXgHfX/77PukmTTHbqR+v/2HXNeG+qPub1wxWrf/Q61tw32JHS6LYxCx/6HXNdNexL7ySxIb2P1k4xxP4FHsJpZIsaAAAA==",
    )
    private val opaque = decode("UklGRjoAAABXRUJQVlA4IC4AAACQAQCdASoQABAAAUAmJaACdLoAA5gA/vtV4/+lwf/S4P/pcH/pcH8bss4bpAAA")
    private val lossless = decode("UklGRjgAAABXRUJQVlA4TCwAAAAvD8ADEA8wyBHzHwxFkaRGU4UAJKwUrCOJ55VnRET0PwDSylutF3+rVZ4GAA==")

    private fun decode(b64: String) = Base64.getDecoder().decode(b64)

    private fun ascii(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    /** The top-level chunks of a RIFF WebP: (fourCC, size). */
    private fun chunks(b: ByteArray): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var at = 12
        while (at + 8 <= b.size) {
            val size = le32(b, at + 4)
            out += ascii(b, at) to size
            at += 8 + size + (size and 1)
        }
        return out
    }

    @Test fun readsTheThreeKindsOfStillWebp() {
        val a = AnimatedWebp.parse(alpha)
        assertEquals(16, a.width)
        assertEquals(16, a.height)
        assertTrue(a.hasAlpha)
        assertEquals(listOf("ALPH", "VP8 "), a.chunks.map { it.fourCc })

        val o = AnimatedWebp.parse(opaque)
        assertEquals(16 to 16, o.width to o.height)
        assertFalse(o.hasAlpha)
        assertEquals(listOf("VP8 "), o.chunks.map { it.fourCc })

        val l = AnimatedWebp.parse(lossless)
        assertEquals(16 to 16, l.width to l.height)
        assertTrue(l.hasAlpha)
        assertEquals(listOf("VP8L"), l.chunks.map { it.fourCc })
    }

    @Test fun assemblesAValidAnimatedRiff() {
        val out = AnimatedWebp.assemble(listOf(alpha, opaque, lossless), listOf(83, 83, 100), 16, 16)
        assertEquals("RIFF", ascii(out, 0))
        assertEquals(out.size - 8, le32(out, 4))
        assertEquals("WEBP", ascii(out, 8))
        assertEquals(listOf("VP8X", "ANIM", "ANMF", "ANMF", "ANMF"), chunks(out).map { it.first })
        // VP8X: animation + alpha flags, canvas 16 × 16 (stored minus one).
        assertEquals(0x12, out[20].toInt() and 0xFF)
        assertEquals(15, out[24].toInt() and 0xFF)
        assertEquals(15, out[27].toInt() and 0xFF)
        // Loops forever, transparent background.
        assertEquals(0, le32(out, 38))
        assertEquals(3 to 266, AnimatedWebp.describe(out))
    }

    @Test fun framesKeepTheirImageChunksAndDoNotBlend() {
        val out = AnimatedWebp.assemble(listOf(alpha), listOf(100), 16, 16)
        // RIFF(12) + VP8X(18) + ANIM(14) = 44: the ANMF chunk.
        assertEquals("ANMF", ascii(out, 44))
        val payload = 52
        assertEquals(15, out[payload + 6].toInt() and 0xFF)
        assertEquals(100, out[payload + 12].toInt() and 0xFF)
        assertEquals(0x03, out[payload + 15].toInt())
        assertEquals("ALPH", ascii(out, payload + 16))
        val alphSize = le32(out, payload + 20)
        assertEquals("VP8 ", ascii(out, payload + 24 + alphSize + (alphSize and 1)))
    }

    @Test fun allOpaqueFramesHaveNoAlphaFlag() {
        val out = AnimatedWebp.assemble(listOf(opaque, opaque), listOf(50, 50), 16, 16)
        assertEquals(0x02, out[20].toInt() and 0xFF)
    }

    @Test fun refusesWhatIsNotAStillWebp() {
        val animated = AnimatedWebp.assemble(listOf(opaque), listOf(50), 16, 16)
        assertTrue(runCatching { AnimatedWebp.parse(animated) }.isFailure)
        assertTrue(runCatching { AnimatedWebp.parse("RIFF0000WAVEfmt ".toByteArray()) }.isFailure)
        assertTrue(runCatching { AnimatedWebp.parse(alpha.copyOf(60)) }.isFailure)
        assertTrue(runCatching { AnimatedWebp.assemble(emptyList(), emptyList(), 16, 16) }.isFailure)
        assertTrue(runCatching { AnimatedWebp.assemble(listOf(opaque), listOf(1, 2), 16, 16) }.isFailure)
        assertTrue(runCatching { AnimatedWebp.assemble(listOf(opaque), listOf(1), 8, 8) }.isFailure)
        assertNull(AnimatedWebp.describe(opaque))
    }
}
