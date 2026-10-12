package com.powermaxv2.ytdownloader.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChatMediaTest {
    /** An Ogg Opus file: OpusHead page (pre-skip 312), then audio pages, the last at [samples] (48 kHz). */
    private fun opus(samples: Long, pages: Int = 3): ByteArray {
        val out = ByteArrayOutputStream()
        fun page(granule: Long, body: ByteArray) {
            val h = ByteArray(27)
            "OggS".toByteArray().copyInto(h)
            for (i in 0 until 8) h[6 + i] = (granule shr (8 * i)).toByte()
            h[26] = 1
            out.write(h)
            out.write(body.size)
            out.write(body)
        }
        val head = ByteArray(19)
        "OpusHead".toByteArray().copyInto(head)
        head[10] = (312 and 0xFF).toByte()
        head[11] = (312 shr 8).toByte()
        page(0, head)
        for (p in 1..pages) page(312 + samples * p / pages, ByteArray(200) { it.toByte() })
        return out.toByteArray()
    }

    /** An MP4 whose video data (mdat) comes before moov › mvhd (timescale 600). */
    private fun mp4(seconds: Int, version: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        fun box(type: String, body: ByteArray): ByteArray {
            val b = ByteArrayOutputStream()
            val size = body.size + 8
            b.write(byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()))
            b.write(type.toByteArray())
            b.write(body)
            return b.toByteArray()
        }
        fun be(v: Long, n: Int) = ByteArray(n) { (v shr (8 * (n - 1 - it))).toByte() }
        out.write(box("ftyp", "isom0000".toByteArray()))
        out.write(box("mdat", ByteArray(5_000)))
        val mvhd = if (version == 1) {
            byteArrayOf(1, 0, 0, 0) + be(0, 8) + be(0, 8) + be(600, 4) + be(seconds * 600L, 8) + ByteArray(80)
        } else {
            byteArrayOf(0, 0, 0, 0) + be(0, 4) + be(0, 4) + be(600, 4) + be(seconds * 600L, 4) + ByteArray(80)
        }
        out.write(box("moov", box("mvhd", mvhd) + box("trak", ByteArray(40))))
        return out.toByteArray()
    }

    @Test fun vocalLength() {
        assertEquals(12.5, MediaDurations.ogg(ByteArrayInputStream(opus(600_000)))!!, 0.001)
        assertNull(MediaDurations.ogg(ByteArrayInputStream("pas un ogg".toByteArray())))
    }

    @Test fun videoLength() {
        assertEquals(42.0, MediaDurations.mp4(ByteArrayInputStream(mp4(42)))!!, 0.001)
        assertEquals(3_700.0, MediaDurations.mp4(ByteArrayInputStream(mp4(3_700, version = 1)))!!, 0.001)
        assertNull(MediaDurations.mp4(ByteArrayInputStream(ByteArray(3))))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun exportWithMedia() {
        val sticker = "RIFF-sticker-A".toByteArray()
        val other = "RIFF-sticker-B".toByteArray()
        val chat = """
            15/09/2026 21:00 - Hugo: STK-20260915-WA0001.webp (fichier joint)
            15/09/2026 21:01 - Inès: STK-20260915-WA0002.webp (fichier joint)
            15/09/2026 21:02 - Inès: PTT-20260915-WA0003.opus (fichier joint)
            15/09/2026 21:03 - Hugo: STK-20260915-WA0004.webp (fichier joint)
            15/09/2026 21:04 - Hugo: VID-20260915-WA0005.mp4 (fichier joint)
            15/09/2026 21:05 - Inès: IMG-20260915-WA0006.jpg (fichier joint)
            15/09/2026 21:06 - Inès: DOC-20260915-WA0007.pdf (fichier joint)
            15/09/2026 21:07 - Hugo: IMG-20260915-WA0008.jpg (fichier joint)
        """.trimIndent()
        val bytes = zip(
            "STK-20260915-WA0001.webp" to sticker,
            "Discussion WhatsApp avec Coloc.txt" to chat.toByteArray(),
            "STK-20260915-WA0002.webp" to sticker.copyOf(), // the same sticker, another name
            "PTT-20260915-WA0003.opus" to opus(48_000 * 30),
            "STK-20260915-WA0004.webp" to other,
            "VID-20260915-WA0005.mp4" to mp4(61),
            "IMG-20260915-WA0006.jpg" to ByteArray(1_000),
            "DOC-20260915-WA0007.pdf" to ByteArray(70_000),
            // IMG-…0008 never downloaded: missing from the .zip.
        )
        val export = ChatExport.read(ByteArrayInputStream(bytes))
        assertTrue(export.text.startsWith("15/09/2026 21:00"))
        val phase = ChatExport.analyse(export.text, "Coloc.zip", export.media) as ChatPhase.Ready
        val media = phase.stats.media!!
        assertEquals(7, media.found)
        assertEquals(1, media.missing)
        assertEquals(listOf(2, 1), media.stickers.map { it.count })
        assertEquals(2, media.stickers.first().people)
        assertEquals(30.0, media.voiceSeconds, 0.01)
        assertEquals("Inès", media.longestVoice!!.person)
        assertEquals(61.0, media.longestVideo!!.value, 0.01)
        assertEquals(listOf("PDF" to 1), media.documents)
        assertEquals(70_000.0, media.biggestDocument!!.value, 0.1)
        assertEquals(1, media.photos.size)
        val hugo = media.of("Hugo")!!
        assertEquals(2, hugo.files[Kind.STICKER.ordinal])
        assertEquals((sticker.size + other.size).toLong(), hugo.bytes(Kind.STICKER))
        assertNotNull(hugo.favoriteSticker)
        assertTrue(phase.stats.stickersKnown)
    }

    @Test fun exportWithoutMediaHasNoMediaStats() {
        val text = "15/09/2026 21:00 - Hugo: <Médias omis>\n15/09/2026 21:01 - Inès: ok"
        val export = ChatExport.read(ByteArrayInputStream(zip("WhatsApp Chat with Hugo.txt" to text.toByteArray())))
        val phase = ChatExport.analyse(export.text, "x.zip", export.media) as ChatPhase.Ready
        assertNull(phase.stats.media)
        assertTrue(!phase.stats.stickersKnown)
    }

    @Test fun iphoneAttachment() {
        val m = WhatsAppChat.parse("[15/09/2026 21:02:10] Hugo: ‎<attached: 00000012-STICKER-2026-09-15-21-02-10.webp>\n[15/09/2026 21:02:28] Inès: Yo")!!
        assertEquals("00000012-STICKER-2026-09-15-21-02-10.webp", m[0].file)
        assertEquals(Kind.STICKER, m[0].kind)
        assertNull(m[1].file)
    }

    @Test fun samplesHaveMedia() {
        val s = ChatSample.stats
        val media = s.media!!
        assertTrue(media.stickers.size >= 3)
        assertTrue(media.stickers.first().emoji != null)
        assertTrue(media.voiceSeconds > 0)
        assertTrue(media.photos.isNotEmpty())
        assertTrue(s.periods.size >= 3)
        assertTrue(s.weekly)
    }

    @Test fun summerTimeHourIsNotCounted() {
        // 29 March 2026, Paris: 2:00 becomes 3:00. Written 1:50 then 3:10 = 20 minutes really.
        val text = "29/03/2026 01:50 - A: dodo ?\n29/03/2026 03:10 - B: oui"
        val m = WhatsAppChat.parse(text, ZoneId.of("Europe/Paris"))!!
        assertEquals(20 * 60L, m[1].time - m[0].time)
        assertEquals(3, m[1].hour)
        val plain = WhatsAppChat.parse(text)!!
        assertEquals(80 * 60L, plain[1].time - plain[0].time)
    }

    @Test fun spanishExport() {
        val text = """
            15/09/26 21:00 - Los mensajes y las llamadas están cifrados de extremo a extremo.
            15/09/26 21:00 - Ana: Hola
            15/09/26 21:01 - Luis: <Multimedia omitido>
            15/09/26 21:02 - Ana: Se eliminó este mensaje.
            15/09/26 21:03 - Luis: STK-20260915-WA0001.webp (archivo adjunto)
            15/09/26 21:04 - Ana: vale <Se editó este mensaje.>
        """.trimIndent()
        val m = WhatsAppChat.parse(text)!!
        assertEquals(listOf(Kind.TEXT, Kind.MEDIA, Kind.DELETED, Kind.STICKER, Kind.TEXT), m.map { it.kind })
        assertTrue(m[4].edited)
        assertEquals("vale", m[4].text)
    }

    @Test fun callLength() {
        assertEquals(12 * 60L, WhatsAppChat.callSeconds("Appel vocal • 12 min"))
        assertEquals(3_600L + 180, WhatsAppChat.callSeconds("Voice call, 1 hr 3 min"))
        assertNull(WhatsAppChat.callSeconds("Appel vocal manqué"))
    }

    @Test fun weight() {
        assertEquals("820 Ko", weight(820 * 1024))
        assertEquals("3,5 Mo", weight((3.5 * 1024 * 1024).toLong()))
        assertEquals("34 Mo", weight(34L * 1024 * 1024))
        assertEquals("1,2 Go", weight((1.2 * 1024 * 1024 * 1024).toLong()))
    }
}
