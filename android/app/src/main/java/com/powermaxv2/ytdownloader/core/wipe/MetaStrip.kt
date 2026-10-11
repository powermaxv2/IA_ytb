package com.powermaxv2.ytdownloader.core.wipe

import java.io.ByteArrayOutputStream

/** A photo the Gomme cannot clean without re-encoding it (not a JPEG / PNG / WebP, or a damaged one). */
class NotStrippable(message: String) : Exception(message)

/**
 * Removes the hidden information of a photo without touching its pixels: the picture data is copied byte for byte,
 * only the metadata blocks around it are dropped. Pure JVM, unit tested.
 *
 * - JPEG: every APPn segment except JFIF (APP0), the colour profile (APP2 "ICC_PROFILE") and Adobe (APP14, it
 *   says how to read the colours) is dropped, with the comments (COM); so are EXIF (GPS, phone, serial numbers,
 *   thumbnail), XMP, IPTC / Photoshop, and the extra pictures some phones append after the end of the image
 *   (MPF previews, depth maps, motion-photo videos: they carry their own EXIF). A minimal EXIF from [ExifWriter]
 *   can be put back (orientation and what the user kept).
 * - PNG: eXIf, tEXt, zTXt, iTXt and tIME chunks are dropped.
 * - WebP: EXIF and XMP chunks are dropped and the VP8X flags updated.
 */
object MetaStrip {
    enum class Format { JPEG, PNG, WEBP }

    fun formatOf(head: ByteArray): Format? = when {
        head.size >= 3 && u(head, 0) == 0xFF && u(head, 1) == 0xD8 && u(head, 2) == 0xFF -> Format.JPEG
        head.size >= 8 && head.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> Format.PNG
        head.size >= 12 && ascii(head, 0, 4) == "RIFF" && ascii(head, 8, 4) == "WEBP" -> Format.WEBP
        else -> null
    }

    /** [data] cleaned; [exif] (an APP1 payload "Exif\0\0…") is written back into a JPEG when given. */
    fun strip(data: ByteArray, exif: ByteArray? = null): ByteArray = when (formatOf(data)) {
        Format.JPEG -> jpeg(data, exif)
        Format.PNG -> png(data)
        Format.WEBP -> webp(data)
        null -> throw NotStrippable("Ce fichier n'est pas une photo JPEG, PNG ou WebP.")
    }

    // MARK: - JPEG

    private val keptApp0 = "JFIF"

    fun jpeg(data: ByteArray, exif: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        out.write(0xFF)
        out.write(0xD8)
        if (exif != null) {
            require(exif.size + 2 <= 0xFFFF) { "EXIF trop long" }
            out.write(0xFF)
            out.write(0xE1)
            out.write(exif.size + 2 shr 8)
            out.write(exif.size + 2 and 0xFF)
            out.write(exif)
        }
        var i = 2
        var sawImage = false
        while (true) {
            if (i >= data.size) throw NotStrippable("Photo JPEG incomplète ou abîmée.")
            if (u(data, i) != 0xFF) throw NotStrippable("Photo JPEG abîmée.")
            // Fill bytes (0xFF 0xFF…) before a marker.
            while (i + 1 < data.size && u(data, i + 1) == 0xFF) i++
            if (i + 1 >= data.size) throw NotStrippable("Photo JPEG incomplète.")
            val marker = u(data, i + 1)
            when {
                marker == 0xD9 -> {
                    // End of the image: whatever follows (appended pictures or videos) is left out.
                    if (!sawImage) throw NotStrippable("Photo JPEG sans image.")
                    out.write(0xFF)
                    out.write(0xD9)
                    return out.toByteArray()
                }
                marker == 0x01 || marker in 0xD0..0xD7 -> {
                    // Markers without a length.
                    out.write(data, i, 2)
                    i += 2
                }
                else -> {
                    if (i + 4 > data.size) throw NotStrippable("Photo JPEG incomplète.")
                    val length = (u(data, i + 2) shl 8) or u(data, i + 3)
                    if (length < 2 || i + 2 + length > data.size) throw NotStrippable("Photo JPEG abîmée.")
                    val end = i + 2 + length
                    if (keep(marker, data, i + 4, length - 2)) out.write(data, i, end - i)
                    i = end
                    if (marker == 0xDA) {
                        // Start of scan: the compressed picture runs until the next real marker.
                        sawImage = true
                        val scanEnd = scanEnd(data, i)
                        out.write(data, i, scanEnd - i)
                        i = scanEnd
                    }
                }
            }
        }
    }

    /** Whether a segment ([marker], payload at [at] of [size] bytes) stays. */
    private fun keep(marker: Int, data: ByteArray, at: Int, size: Int): Boolean = when (marker) {
        0xE0 -> ascii(data, at, minOf(4, size)) == keptApp0
        0xE2 -> size >= 12 && ascii(data, at, 11) == "ICC_PROFILE"
        0xEE -> size >= 5 && ascii(data, at, 5) == "Adobe"
        in 0xE1..0xEF -> false
        0xFE -> false
        else -> true
    }

    /** Index of the first marker after entropy-coded data starting at [from] (stuffed 0xFF00 and RSTn are data). */
    private fun scanEnd(data: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < data.size) {
            if (u(data, i) == 0xFF) {
                val next = u(data, i + 1)
                if (next != 0x00 && next !in 0xD0..0xD7 && next != 0xFF) return i
            }
            i++
        }
        throw NotStrippable("Photo JPEG incomplète.")
    }

    // MARK: - PNG

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val pngDropped = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")

    fun png(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        out.write(PNG_SIGNATURE)
        var i = 8
        while (i + 12 <= data.size) {
            val length = be32(data, i)
            if (length < 0 || i.toLong() + 12 + length > data.size) throw NotStrippable("Image PNG abîmée.")
            val type = ascii(data, i + 4, 4)
            val total = 12 + length
            if (type !in pngDropped) out.write(data, i, total)
            i += total
            if (type == "IEND") return out.toByteArray()
        }
        throw NotStrippable("Image PNG incomplète.")
    }

    // MARK: - WebP

    fun webp(data: ByteArray): ByteArray {
        val body = ByteArrayOutputStream(data.size)
        val riffEnd = minOf(data.size.toLong(), 8L + (le32(data, 4).toLong() and 0xFFFFFFFFL)).toInt()
        var vp8x = -1
        var i = 12
        while (i + 8 <= riffEnd) {
            val type = ascii(data, i, 4)
            val length = le32(data, i + 4)
            if (length < 0 || i.toLong() + 8 + length > data.size) throw NotStrippable("Image WebP abîmée.")
            if (type != "EXIF" && type != "XMP ") {
                if (type == "VP8X") vp8x = body.size() + 8
                body.write(data, i, 8 + length)
                // Chunks are padded to an even size (the pad byte may be missing at the very end of a file).
                if (length and 1 == 1) body.write(0)
            }
            i += 8 + length + (length and 1)
        }
        val chunks = body.toByteArray()
        // VP8X flags: bit 3 = EXIF present, bit 2 = XMP present.
        if (vp8x in chunks.indices) chunks[vp8x] = (chunks[vp8x].toInt() and 0x0C.inv()).toByte()
        val out = ByteArrayOutputStream(chunks.size + 12)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        val size = chunks.size + 4
        out.write(byteArrayOf((size and 0xFF).toByte(), (size shr 8 and 0xFF).toByte(), (size shr 16 and 0xFF).toByte(), (size ushr 24).toByte()))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(chunks)
        return out.toByteArray()
    }

    // MARK: - Helpers

    private fun u(data: ByteArray, i: Int) = data[i].toInt() and 0xFF

    private fun ascii(data: ByteArray, at: Int, n: Int): String =
        if (at < 0 || n <= 0 || at + n > data.size) "" else String(data, at, n, Charsets.ISO_8859_1)

    /** Big-endian 32-bit value (PNG), -1 past the end. */
    private fun be32(data: ByteArray, at: Int): Int =
        if (at + 4 > data.size) -1 else (u(data, at) shl 24) or (u(data, at + 1) shl 16) or (u(data, at + 2) shl 8) or u(data, at + 3)

    /** Little-endian 32-bit value (RIFF), -1 past the end. */
    private fun le32(data: ByteArray, at: Int): Int =
        if (at + 4 > data.size) -1 else u(data, at) or (u(data, at + 1) shl 8) or (u(data, at + 2) shl 16) or (u(data, at + 3) shl 24)
}
