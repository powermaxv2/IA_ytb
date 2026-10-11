package com.powermaxv2.ytdownloader.core.wipe

import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What the Gomme writes back into a cleaned JPEG: only the orientation (else the photo would show sideways) and
 * the kinds of information the user chose to keep. Everything else of the original EXIF (serial numbers, lens,
 * thumbnails, maker notes, software…) is never copied.
 */
data class KeptExif(
    /** EXIF orientation 1..8 (1 = normal). */
    val orientation: Int = 1,
    val make: String? = null,
    val model: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    /** "2026:10:10 18:42:07" (EXIF date format). */
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Builds a minimal EXIF block (the payload of a JPEG APP1 segment: "Exif\0\0" + a big-endian TIFF structure) from
 * [KeptExif]. Pure JVM, unit tested.
 */
object ExifWriter {
    private const val ASCII = 2
    private const val SHORT = 3
    private const val LONG = 4
    private const val RATIONAL = 5
    private const val BYTE = 1

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    /** The APP1 payload ("Exif\0\0…"), or null when there is nothing to write (normal orientation, nothing kept). */
    fun build(kept: KeptExif): ByteArray? {
        val ifd0 = ArrayList<Entry>()
        val exif = ArrayList<Entry>()
        val gps = ArrayList<Entry>()
        kept.make?.let(::clean)?.let { ifd0 += ascii(0x010F, it) }
        kept.model?.let(::clean)?.let { ifd0 += ascii(0x0110, it) }
        if (kept.orientation in 2..8) ifd0 += Entry(0x0112, SHORT, 1, short(kept.orientation) + byteArrayOf(0, 0))
        kept.artist?.let(::clean)?.let { ifd0 += ascii(0x013B, it) }
        kept.copyright?.let(::clean)?.let { ifd0 += ascii(0x8298, it) }
        kept.dateTimeOriginal?.let(::cleanDate)?.let {
            ifd0 += ascii(0x0132, it)
            exif += ascii(0x9003, it)
        }
        val lat = kept.latitude
        val lon = kept.longitude
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && abs(lat) <= 90 && abs(lon) <= 180) {
            gps += Entry(0x0000, BYTE, 4, byteArrayOf(2, 2, 0, 0))
            gps += ascii(0x0001, if (lat < 0) "S" else "N")
            gps += Entry(0x0002, RATIONAL, 3, degrees(abs(lat)))
            gps += ascii(0x0003, if (lon < 0) "W" else "E")
            gps += Entry(0x0004, RATIONAL, 3, degrees(abs(lon)))
        }
        if (ifd0.isEmpty() && exif.isEmpty() && gps.isEmpty()) return null

        // Pointers to the sub-IFDs are filled once their offsets are known (placeholder now).
        if (exif.isNotEmpty()) ifd0 += Entry(0x8769, LONG, 1, ByteArray(4))
        if (gps.isNotEmpty()) ifd0 += Entry(0x8825, LONG, 1, ByteArray(4))
        ifd0.sortBy { it.tag }

        // Layout (offsets from the start of the TIFF header): header (8), IFD0 + its data, Exif IFD, GPS IFD.
        val ifd0Offset = 8
        val exifOffset = ifd0Offset + size(ifd0)
        val gpsOffset = exifOffset + (if (exif.isEmpty()) 0 else size(exif))
        val withPointers = ifd0.map {
            when (it.tag) {
                0x8769 -> Entry(it.tag, LONG, 1, int(exifOffset))
                0x8825 -> Entry(it.tag, LONG, 1, int(gpsOffset))
                else -> it
            }
        }
        val tiff = ByteArrayOutputStream()
        tiff.write(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42))
        tiff.write(int(ifd0Offset))
        writeIfd(tiff, withPointers, ifd0Offset)
        if (exif.isNotEmpty()) writeIfd(tiff, exif, exifOffset)
        if (gps.isNotEmpty()) writeIfd(tiff, gps, gpsOffset)
        return "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + tiff.toByteArray()
    }

    /** Bytes taken by an IFD whose entries are [entries]: count, entries, next-IFD link, then values over 4 bytes. */
    private fun size(entries: List<Entry>): Int = 2 + entries.size * 12 + 4 + entries.sumOf { if (it.data.size > 4) pad(it.data.size) else 0 }

    private fun pad(n: Int) = n + (n and 1)

    private fun writeIfd(out: ByteArrayOutputStream, entries: List<Entry>, offset: Int) {
        out.write(short(entries.size))
        var data = offset + 2 + entries.size * 12 + 4
        val tail = ByteArrayOutputStream()
        for (e in entries) {
            out.write(short(e.tag))
            out.write(short(e.type))
            out.write(int(e.count))
            if (e.data.size <= 4) {
                out.write(e.data + ByteArray(4 - e.data.size))
            } else {
                out.write(int(data))
                tail.write(e.data)
                if (e.data.size and 1 == 1) tail.write(0)
                data += pad(e.data.size)
            }
        }
        out.write(int(0))
        out.write(tail.toByteArray())
    }

    private fun ascii(tag: Int, text: String): Entry {
        val bytes = text.toByteArray(Charsets.US_ASCII) + 0.toByte()
        return Entry(tag, ASCII, bytes.size, bytes)
    }

    /** Printable ASCII only (EXIF ASCII), 64 characters at most; null when nothing is left. */
    private fun clean(text: String): String? =
        text.filter { it.code in 0x20..0x7E }.trim().take(64).ifEmpty { null }

    private fun cleanDate(text: String): String? =
        text.trim().takeIf { it.matches(Regex("""\d{4}:\d{2}:\d{2} \d{2}:\d{2}:\d{2}""")) }

    /** Degrees, minutes, seconds (seconds in 1/10000) as three RATIONALs. */
    private fun degrees(value: Double): ByteArray {
        val d = value.toLong()
        val minutesFull = (value - d) * 60
        val m = minutesFull.toLong()
        val s = ((minutesFull - m) * 60 * 10_000).roundToLong()
        return int(d.toInt()) + int(1) + int(m.toInt()) + int(1) + int(s.toInt()) + int(10_000)
    }

    private fun short(v: Int) = byteArrayOf((v shr 8 and 0xFF).toByte(), (v and 0xFF).toByte())

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v shr 16 and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(), (v and 0xFF).toByte())

    /** "48.8650, 2.3780" for the screen. */
    fun coordinates(lat: Double, lon: Double): String = String.format(Locale.ROOT, "%.4f, %.4f", lat, lon)
}
