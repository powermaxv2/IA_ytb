package com.powermaxv2.ytdownloader.core.readaloud

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** How the samples of a WAV file are laid out. */
data class WavFormat(val encoding: Int, val channels: Int, val sampleRate: Int, val bitsPerSample: Int) {
    val blockAlign: Int get() = channels * ((bitsPerSample + 7) / 8)
    val byteRate: Int get() = sampleRate * blockAlign
}

/** A WAV file's [format], and where its samples are: [dataSize] bytes from [dataOffset]. */
data class WavInfo(val format: WavFormat, val dataOffset: Long, val dataSize: Long) {
    val durationMs: Long get() = if (format.byteRate <= 0) 0 else dataSize * 1000 / format.byteRate
}

// The phone's voice writes one WAV file per block of text; this glues them into one, sample after sample, with
// a fresh header. Pure JVM: tested.
object WavJoin {
    private const val PCM = 1

    /** Header of [file]: the "fmt " and "data" chunks found among the others (LIST, fact…), sizes checked. */
    fun read(file: File): WavInfo = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        if (length < 12) throw IOException("Fichier audio vide")
        val riff = ByteArray(12)
        raf.readFully(riff)
        if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") {
            throw IOException("Ce n'est pas un fichier WAV")
        }
        var format: WavFormat? = null
        var pos = 12L
        val head = ByteArray(8)
        while (pos + 8 <= length) {
            raf.seek(pos)
            raf.readFully(head)
            val id = String(head, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    if (size < 16) throw IOException("En-tête WAV abîmé")
                    val fmt = ByteArray(16)
                    raf.readFully(fmt)
                    val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    format = WavFormat(
                        encoding = b.getShort(0).toInt() and 0xFFFF,
                        channels = b.getShort(2).toInt() and 0xFFFF,
                        sampleRate = b.getInt(4),
                        bitsPerSample = b.getShort(14).toInt() and 0xFFFF,
                    )
                }
                "data" -> {
                    val f = format ?: throw IOException("En-tête WAV abîmé")
                    // A voice that streams its file may leave the size at 0 or 0xFFFFFFFF: the rest of the file is sound.
                    val available = length - body
                    val data = if (size == 0L || size > available) available else size
                    return WavInfo(f, body, data - data % f.blockAlign.coerceAtLeast(1))
                }
            }
            pos = body + size + (size and 1)
        }
        throw IOException("Fichier WAV sans son")
    }

    /** The 44 bytes of a plain PCM WAV header for [dataSize] bytes of [format]. */
    fun header(format: WavFormat, dataSize: Long): ByteArray {
        require(dataSize in 0..(0xFFFFFFFFL - 36)) { "WAV trop long" }
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt((36 + dataSize).toInt())
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(format.encoding.toShort())
        b.putShort(format.channels.toShort())
        b.putInt(format.sampleRate)
        b.putInt(format.byteRate)
        b.putShort(format.blockAlign.toShort())
        b.putShort(format.bitsPerSample.toShort())
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(dataSize.toInt())
        return b.array()
    }

    /**
     * [parts] (WAV files of the same format) written one after the other into [output], with [gapMs] of silence
     * between two parts. [checkpoint] runs before each part (throw to stop). Returns the joined file's header.
     */
    fun join(parts: List<File>, output: File, gapMs: Int = 0, checkpoint: (() -> Unit)? = null): WavInfo {
        require(parts.isNotEmpty()) { "Aucun morceau" }
        val infos = parts.map(::read)
        val format = infos.first().format
        if (format.encoding != PCM) throw IOException("Format audio de la voix non pris en charge (${format.encoding})")
        if (infos.any { it.format != format }) throw IOException("La voix a changé de format en cours de route")
        val gap = gapBytes(format, gapMs)
        val total = infos.sumOf { it.dataSize } + gap * (parts.size - 1)
        RandomAccessFile(output, "rw").use { out ->
            out.setLength(0)
            out.write(header(format, total))
            val buffer = ByteArray(256 * 1024)
            // 8-bit PCM is unsigned: its silence is 128, not 0.
            val silence = ByteArray(gap.toInt()) { if (format.bitsPerSample == 8) 0x80.toByte() else 0 }
            parts.forEachIndexed { i, part ->
                checkpoint?.invoke()
                if (i > 0 && gap > 0) out.write(silence)
                val info = infos[i]
                RandomAccessFile(part, "r").use { input ->
                    input.seek(info.dataOffset)
                    var left = info.dataSize
                    while (left > 0) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        left -= n
                    }
                }
            }
        }
        return WavInfo(format, 44, total)
    }

    /** Bytes of [ms] milliseconds of silence, whole samples only. */
    fun gapBytes(format: WavFormat, ms: Int): Long {
        if (ms <= 0) return 0
        val frames = format.sampleRate.toLong() * ms / 1000
        return frames * format.blockAlign
    }
}
