package com.powermaxv2.ytdownloader.core.wipe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class MetaStripTest {
    // MARK: - Fixtures

    private fun plainJpeg(): ByteArray {
        val image = BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 32) for (y in 0 until 24) image.setRGB(x, y, (x * 8 shl 16) or (y * 10 shl 8) or 0x40)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun segment(marker: Int, payload: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), marker.toByte(), (payload.size + 2 shr 8).toByte(), (payload.size + 2 and 0xFF).toByte()) + payload

    /** A phone-like JPEG: EXIF with GPS and serial, XMP, a comment, then an appended "motion photo" after EOI. */
    private fun phoneJpeg(): ByteArray {
        val plain = plainJpeg()
        val exif = "Exif\u0000\u0000MM\u0000*GPS Paris 48.8650 2.3780 SERIAL-123".toByteArray(Charsets.ISO_8859_1)
        val xmp = "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>Maxime</x:xmpmeta>".toByteArray(Charsets.ISO_8859_1)
        val mpf = "MPF\u0000secret preview".toByteArray(Charsets.ISO_8859_1)
        val icc = "ICC_PROFILE\u0000\u0001\u0001fakeprofile".toByteArray(Charsets.ISO_8859_1)
        val head = plain.copyOfRange(0, 2)
        val rest = plain.copyOfRange(2, plain.size)
        val trailer = "ftypmp42 VIDEO WITH GPS".toByteArray(Charsets.ISO_8859_1)
        return head + segment(0xE1, exif) + segment(0xE1, xmp) + segment(0xE2, mpf) + segment(0xE2, icc) +
            segment(0xFE, "Shot by Maxime".toByteArray()) + rest + trailer
    }

    private fun ByteArray.contains(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    private fun decodes(bytes: ByteArray): Boolean = ImageIO.read(ByteArrayInputStream(bytes))?.width == 32

    // MARK: - JPEG

    @Test fun jpegLosesEverySecretButKeepsThePicture() {
        val dirty = phoneJpeg()
        assertTrue(decodes(dirty))
        val clean = MetaStrip.strip(dirty)
        assertTrue(decodes(clean))
        listOf("GPS", "SERIAL", "Maxime", "xmpmeta", "MPF", "VIDEO", "secret").forEach { assertFalse(it, clean.contains(it)) }
        // The colour profile stays.
        assertTrue(clean.contains("ICC_PROFILE"))
        // The picture data itself is untouched: the plain JPEG without its own APP0 is a suffix-free match.
        val plain = plainJpeg()
        val scan = plain.indexOfMarker(0xDA)
        val cleanScan = clean.indexOfMarker(0xDA)
        assertArrayEquals(plain.copyOfRange(scan, plain.size), clean.copyOfRange(cleanScan, clean.size))
    }

    @Test fun cleanJpegIsUnchangedApartFromItsMetadata() {
        val plain = plainJpeg()
        val clean = MetaStrip.strip(plain)
        assertArrayEquals(plain, clean)
    }

    @Test fun keptExifIsWrittenFirst() {
        val exif = ExifWriter.build(KeptExif(orientation = 6, dateTimeOriginal = "2026:10:10 18:42:07"))
        assertNotNull(exif)
        val clean = MetaStrip.strip(phoneJpeg(), exif)
        assertTrue(decodes(clean))
        assertEquals(0xE1, clean[3].toInt() and 0xFF)
        assertTrue(clean.contains("2026:10:10 18:42:07"))
        assertFalse(clean.contains("GPS"))
        val tiff = Tiff(clean.copyOfRange(4 + 2 + 6, 4 + 2 + exif!!.size))
        assertEquals(6, tiff.ifd0[0x0112])
    }

    @Test fun brokenJpegIsRefused() {
        val plain = plainJpeg()
        assertThrows { MetaStrip.strip(plain.copyOfRange(0, plain.size / 2)) }
        assertThrows { MetaStrip.strip(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x7F, 0x7F)) }
        assertThrows { MetaStrip.strip("hello world, not a photo".toByteArray()) }
    }

    // MARK: - EXIF writer

    @Test fun nothingKeptMeansNoExif() {
        assertNull(ExifWriter.build(KeptExif()))
        assertNull(ExifWriter.build(KeptExif(orientation = 1, make = "  ")))
    }

    @Test fun exifWriterStructure() {
        val exif = ExifWriter.build(
            KeptExif(
                orientation = 8,
                make = "Google",
                model = "Pixel 8 Pro",
                artist = "Maxime",
                dateTimeOriginal = "2026:10:10 18:42:07",
                latitude = 48.865,
                longitude = -2.378,
            ),
        )!!
        assertEquals("Exif\u0000\u0000", String(exif, 0, 6, Charsets.ISO_8859_1))
        val tiff = Tiff(exif.copyOfRange(6, exif.size))
        assertEquals("Google", tiff.ifd0[0x010F])
        assertEquals("Pixel 8 Pro", tiff.ifd0[0x0110])
        assertEquals(8, tiff.ifd0[0x0112])
        assertEquals("Maxime", tiff.ifd0[0x013B])
        assertEquals("2026:10:10 18:42:07", tiff.exif[0x9003])
        assertEquals("N", tiff.gps[0x0001])
        assertEquals("W", tiff.gps[0x0003])
        @Suppress("UNCHECKED_CAST")
        val lat = tiff.gps[0x0002] as List<Double>
        assertEquals(48.865, lat[0] + lat[1] / 60 + lat[2] / 3600, 1e-6)
        // IFD0 tags in ascending order (readers rely on it).
        assertEquals(tiff.ifd0Order.sorted(), tiff.ifd0Order)
    }

    @Test fun oddTextIsCleaned() {
        val exif = ExifWriter.build(KeptExif(make = "Ça\u0000va", dateTimeOriginal = "hier"))!!
        val tiff = Tiff(exif.copyOfRange(6, exif.size))
        assertEquals("ava", tiff.ifd0[0x010F])
        assertNull(tiff.exif[0x9003])
        assertNull(tiff.ifd0[0x0132])
    }

    // MARK: - PNG and WebP

    @Test fun pngTextChunksAreDropped() {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val png = out.toByteArray()
        val iend = png.size - 12
        val text = pngChunk("tEXt", "Author\u0000Maxime".toByteArray(Charsets.ISO_8859_1))
        val exif = pngChunk("eXIf", "MM\u0000*GPS".toByteArray(Charsets.ISO_8859_1))
        val dirty = png.copyOfRange(0, iend) + text + exif + png.copyOfRange(iend, png.size)
        val clean = MetaStrip.strip(dirty)
        assertFalse(clean.contains("Maxime"))
        assertFalse(clean.contains("GPS"))
        assertArrayEquals(png, clean)
        assertNotNull(ImageIO.read(ByteArrayInputStream(clean)))
    }

    @Test fun webpExifAndXmpAreDroppedAndFlagsCleared() {
        val vp8x = byteArrayOf(0x0C, 0, 0, 0, 7, 0, 0, 7, 0, 0)
        val vp8l = byteArrayOf(0x2F, 1, 2, 3, 4)
        val chunks = riffChunk("VP8X", vp8x) + riffChunk("VP8L", vp8l) + riffChunk("EXIF", "GPS 48.8".toByteArray()) +
            riffChunk("XMP ", "<x>Maxime</x>".toByteArray())
        val dirty = "RIFF".toByteArray() + le(chunks.size + 4) + "WEBP".toByteArray() + chunks
        val clean = MetaStrip.strip(dirty)
        assertFalse(clean.contains("GPS"))
        assertFalse(clean.contains("Maxime"))
        val expected = riffChunk("VP8X", byteArrayOf(0, 0, 0, 0, 7, 0, 0, 7, 0, 0)) + riffChunk("VP8L", vp8l)
        assertArrayEquals("RIFF".toByteArray() + le(expected.size + 4) + "WEBP".toByteArray() + expected, clean)
    }

    // MARK: - Helpers

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: NotStrippable) {
            return
        }
        throw AssertionError("NotStrippable attendu")
    }

    private fun ByteArray.indexOfMarker(marker: Int): Int {
        for (i in 0 until size - 1) if (this[i] == 0xFF.toByte() && (this[i + 1].toInt() and 0xFF) == marker) return i
        return -1
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val crc = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt()
        return be(data.size) + type.toByteArray() + data + be(crc)
    }

    private fun riffChunk(type: String, data: ByteArray): ByteArray =
        type.toByteArray() + le(data.size) + data + (if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf())

    private fun be(v: Int) = byteArrayOf((v ushr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v ushr 24).toByte())

    /** Tiny big-endian TIFF reader: IFD0, Exif IFD and GPS IFD as tag → String / Int / List<Double>. */
    private class Tiff(val b: ByteArray) {
        val ifd0 = HashMap<Int, Any>()
        val exif = HashMap<Int, Any>()
        val gps = HashMap<Int, Any>()
        val ifd0Order = ArrayList<Int>()

        init {
            if (b.size >= 8) {
                read(u32(4), ifd0, ifd0Order)
                (ifd0[0x8769] as? Int)?.let { read(it, exif, ArrayList()) }
                (ifd0[0x8825] as? Int)?.let { read(it, gps, ArrayList()) }
            }
        }

        private fun u16(at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        private fun u32(at: Int) = (u16(at) shl 16) or u16(at + 2)

        private fun read(offset: Int, into: HashMap<Int, Any>, order: MutableList<Int>) {
            val n = u16(offset)
            for (k in 0 until n) {
                val e = offset + 2 + k * 12
                val tag = u16(e)
                val type = u16(e + 2)
                val count = u32(e + 4)
                order += tag
                into[tag] = when (type) {
                    2 -> {
                        val at = if (count <= 4) e + 8 else u32(e + 8)
                        String(b, at, count - 1, Charsets.US_ASCII)
                    }
                    3 -> u16(e + 8)
                    4 -> u32(e + 8)
                    5 -> (0 until count).map { i -> val at = u32(e + 8) + i * 8; u32(at).toDouble() / u32(at + 4) }
                    else -> count
                }
            }
        }
    }
}
