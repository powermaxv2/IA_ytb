package com.powermaxv2.ytdownloader.core.sticker

/**
 * Frame count and length of a GIF, read from its blocks (MediaMetadataRetriever does not read GIF). Browsers show a
 * delay under 20 ms as 100 ms; Gulp does the same so the sticker keeps the speed people know.
 */
object GifInfo {
    data class Info(val width: Int, val height: Int, val frames: Int, val durationMs: Long)

    fun read(bytes: ByteArray): Info? {
        if (bytes.size < 13) return null
        val magic = String(bytes, 0, 6, Charsets.US_ASCII)
        if (magic != "GIF87a" && magic != "GIF89a") return null
        val width = u16(bytes, 6)
        val height = u16(bytes, 8)
        var at = 13
        val flags = bytes[10].toInt() and 0xFF
        if (flags and 0x80 != 0) at += 3 * (1 shl ((flags and 0x07) + 1))
        var frames = 0
        var total = 0L
        var pendingDelay = -1
        while (at < bytes.size) {
            when (bytes[at].toInt() and 0xFF) {
                0x21 -> {
                    if (at + 1 >= bytes.size) break
                    val label = bytes[at + 1].toInt() and 0xFF
                    at += 2
                    if (label == 0xF9 && at + 5 <= bytes.size && bytes[at].toInt() and 0xFF >= 4) {
                        pendingDelay = u16(bytes, at + 2) * 10
                    }
                    at = skipSubBlocks(bytes, at) ?: break
                }
                0x2C -> {
                    if (at + 10 > bytes.size) break
                    val local = bytes[at + 9].toInt() and 0xFF
                    at += 10
                    if (local and 0x80 != 0) at += 3 * (1 shl ((local and 0x07) + 1))
                    at += 1 // LZW minimum code size
                    at = skipSubBlocks(bytes, at) ?: break
                    frames++
                    total += if (pendingDelay < 20) 100 else pendingDelay
                    pendingDelay = -1
                }
                0x3B -> break
                else -> break
            }
        }
        if (frames == 0 || width == 0 || height == 0) return null
        return Info(width, height, frames, total)
    }

    private fun skipSubBlocks(b: ByteArray, start: Int): Int? {
        var at = start
        while (at < b.size) {
            val size = b[at].toInt() and 0xFF
            at += 1
            if (size == 0) return at
            at += size
        }
        return null
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
}
