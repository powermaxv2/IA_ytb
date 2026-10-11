package com.powermaxv2.ytdownloader.core.chat

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

// The files of an export "with media": the .zip WhatsApp shares holds the discussion .txt and every photo, video,
// vocal, sticker and document it names. Each file is read once, as a stream, straight from the .zip: its weight,
// the length of vocals and videos (read in their headers), and a fingerprint of each sticker so the same sticker
// sent under 40 file names counts 40 times. Nothing is written to storage. Pure Kotlin: tested on the JVM.

/** One file of the .zip: [bytes] = its weight, [seconds] = length of a vocal or video, [hash] = sticker fingerprint. */
data class MediaFile(val name: String, val bytes: Long, val seconds: Double? = null, val hash: String? = null)

/**
 * What [MediaScanner] found in a .zip, by file name. [stickerImages] = each distinct sticker's file, by fingerprint;
 * [photos] = a few photos (the newest names), kept to draw the gallery.
 */
class MediaScan(
    val files: Map<String, MediaFile>,
    val stickerImages: Map<String, ByteArray> = emptyMap(),
    val photos: Map<String, ByteArray> = emptyMap(),
) {
    val isEmpty: Boolean get() = files.isEmpty()

    companion object {
        val EMPTY = MediaScan(emptyMap())
    }
}

/** Reads the media entries of a .zip one after the other ([add]), then gives the [result]. */
class MediaScanner(
    private val photosKept: Int = PHOTOS_KEPT,
    private val maxPhotoBytes: Int = 4 * 1024 * 1024,
    private val maxStickerBytes: Int = 1024 * 1024,
    private val maxStickerTotal: Long = 40L * 1024 * 1024,
) {
    private val files = HashMap<String, MediaFile>()
    private val stickers = LinkedHashMap<String, ByteArray>()
    private var stickerTotal = 0L
    private val photos = java.util.TreeMap<String, ByteArray>()

    /** One entry of the .zip, [entryName] as written in it (only its last part is used, never as a path). */
    fun add(entryName: String, input: InputStream) {
        val name = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty()) return
        val counting = CountingStream(input)
        val kind = WhatsAppChat.fileKind(name)
        var seconds: Double? = null
        var hash: String? = null
        when (kind) {
            Kind.STICKER -> {
                val bytes = readCapped(counting, maxStickerBytes)
                if (bytes != null) {
                    hash = fingerprint(bytes)
                    if (hash !in stickers && stickerTotal + bytes.size <= maxStickerTotal) {
                        stickers[hash] = bytes
                        stickerTotal += bytes.size
                    }
                }
            }
            Kind.VOICE -> seconds = runCatching { if (name.endsWith(".opus", true) || name.endsWith(".ogg", true)) MediaDurations.ogg(counting) else MediaDurations.mp4(counting) }.getOrNull()
            Kind.AUDIO -> seconds = runCatching { if (name.endsWith(".opus", true) || name.endsWith(".ogg", true)) MediaDurations.ogg(counting) else if (isMp4(name)) MediaDurations.mp4(counting) else null }.getOrNull()
            Kind.VIDEO, Kind.GIF -> seconds = if (isMp4(name)) runCatching { MediaDurations.mp4(counting) }.getOrNull() else null
            Kind.PHOTO -> {
                // Only the newest names are kept: WhatsApp names photos by date ("IMG-20260915-WA0003.jpg").
                val worth = photos.size < photosKept || name > photos.firstKey()
                if (worth) {
                    readCapped(counting, maxPhotoBytes)?.let {
                        photos[name] = it
                        if (photos.size > photosKept) photos.pollFirstEntry()
                    }
                }
            }
            else -> Unit
        }
        counting.drain()
        files[name] = MediaFile(name, counting.count, seconds?.takeIf { it > 0 && it < 48 * 3_600 }, hash)
    }

    fun result(): MediaScan = MediaScan(files.toMap(), stickers.toMap(), photos.toMap())

    private fun isMp4(name: String) = Regex("\\.(mp4|m4a|mov|3gp|aac)$", RegexOption.IGNORE_CASE).containsMatchIn(name)

    /** The whole stream, or null (and the stream left to drain) when it is longer than [max]. */
    private fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            out.write(buffer, 0, n)
            if (out.size() > max) return null
        }
    }

    companion object {
        const val PHOTOS_KEPT = 6

        /** 16 hex digits of the SHA-1 of a file: enough to tell stickers apart. */
        fun fingerprint(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-1").digest(bytes).take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Counts what is read through it; [drain] reads (and counts) the rest. */
private class CountingStream(private val input: InputStream) : InputStream() {
    var count = 0L
        private set

    override fun read(): Int = input.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also { if (it > 0) count += it }

    fun drain() {
        val buffer = ByteArray(64 * 1024)
        while (read(buffer, 0, buffer.size) >= 0) Unit
    }
}

/** Length of a vocal (Ogg Opus) or a video (MP4), read in the file's own headers. */
object MediaDurations {
    /**
     * Ogg (WhatsApp vocals, .opus): the last page's granule position, in 48 kHz samples, minus the "pre-skip"
     * written in the OpusHead page. Pages are walked one by one; their audio is skipped.
     */
    fun ogg(input: InputStream): Double? {
        val header = ByteArray(27)
        var preSkip = 0L
        var rate = 48_000.0
        var last = -1L
        var first = true
        while (true) {
            if (!readFully(input, header)) break
            if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() || header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()) break
            val granule = le(header, 6, 8)
            val segments = ByteArray(header[26].toInt() and 0xFF)
            if (!readFully(input, segments)) break
            val bodyLength = segments.sumOf { it.toInt() and 0xFF }
            if (first) {
                val body = ByteArray(bodyLength)
                if (!readFully(input, body)) break
                if (body.size >= 12 && String(body, 0, 8, Charsets.US_ASCII) == "OpusHead") {
                    preSkip = le(body, 10, 2)
                } else if (body.size >= 16 && String(body, 1, 6, Charsets.US_ASCII) == "vorbis") {
                    rate = le(body, 12, 4).toDouble().takeIf { it > 0 } ?: rate
                }
                first = false
            } else if (!skipFully(input, bodyLength.toLong())) {
                break
            }
            if (granule != -1L && granule > 0) last = granule
        }
        return if (last > 0) (last - preSkip).coerceAtLeast(0) / rate else null
    }

    /** MP4 / MOV / M4A: moov › mvhd gives a time scale and a duration. Boxes before it (the video itself) are skipped. */
    fun mp4(input: InputStream): Double? {
        val head = ByteArray(8)
        var inMoov = false
        var left = Long.MAX_VALUE // bytes left in moov once inside
        while (true) {
            if (!readFully(input, head)) return null
            var size = be(head, 0, 4)
            val type = String(head, 4, 4, Charsets.US_ASCII)
            var headerSize = 8L
            if (size == 1L) {
                val large = ByteArray(8)
                if (!readFully(input, large)) return null
                size = be(large, 0, 8)
                headerSize = 16
            } else if (size == 0L) {
                size = Long.MAX_VALUE
            }
            if (size < headerSize) return null
            if (inMoov) left -= size
            when {
                type == "moov" -> {
                    inMoov = true
                    left = size - headerSize
                }
                type == "mvhd" && inMoov -> {
                    val body = ByteArray(minOf(size - headerSize, 120L).toInt())
                    if (!readFully(input, body)) return null
                    val version = body[0].toInt()
                    val (scale, duration) = if (version == 1) be(body, 20, 4) to be(body, 24, 8) else be(body, 12, 4) to be(body, 16, 4)
                    return if (scale > 0 && duration > 0) duration.toDouble() / scale else null
                }
                else -> {
                    if (size == Long.MAX_VALUE) return null
                    if (!skipFully(input, size - headerSize)) return null
                }
            }
            if (inMoov && left <= 0) return null
        }
    }

    private fun le(b: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (i in n - 1 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun be(b: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun readFully(input: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) {
            val n = input.read(b, off, b.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var left = count
        val buffer = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (n < 0) return false
            left -= n
        }
        return true
    }
}
