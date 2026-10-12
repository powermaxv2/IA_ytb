package com.powermaxv2.ytdownloader.core.sticker

import java.io.ByteArrayOutputStream

/**
 * Writes an animated WebP from still WebP frames. Android only encodes still WebP (Bitmap.compress), so each frame
 * is encoded there and its image chunks (ALPH + VP8, or VP8L) are moved into ANMF frames of one RIFF file, as the
 * WebP container spec describes. Pure Kotlin: no image decoding happens here.
 */
object AnimatedWebp {
    /** One image chunk of a still WebP. */
    class Chunk(val fourCc: String, val data: ByteArray)

    /** What a still WebP holds: its size and the chunks to copy into a frame. */
    class Still(val width: Int, val height: Int, val chunks: List<Chunk>, val hasAlpha: Boolean)

    /** Reads a still WebP made by Android (simple VP8, simple VP8L, or VP8X + ALPH + VP8). */
    fun parse(bytes: ByteArray): Still {
        require(bytes.size >= 20 && ascii(bytes, 0) == "RIFF" && ascii(bytes, 8) == "WEBP") { "Pas un fichier WebP" }
        val end = minOf(bytes.size, 8 + u32(bytes, 4))
        var at = 12
        var width = 0
        var height = 0
        var alpha = false
        val chunks = ArrayList<Chunk>()
        while (at + 8 <= end) {
            val fourCc = ascii(bytes, at)
            val size = u32(bytes, at + 4)
            val start = at + 8
            require(size >= 0 && start + size <= end) { "WebP tronqué" }
            val data = bytes.copyOfRange(start, start + size)
            when (fourCc) {
                "VP8X" -> {
                    require(size >= 10) { "VP8X trop court" }
                    alpha = data[0].toInt() and 0x10 != 0
                    width = u24(data, 4) + 1
                    height = u24(data, 7) + 1
                }
                "ANIM", "ANMF" -> throw IllegalArgumentException("Cette image est déjà animée")
                "ALPH" -> {
                    alpha = true
                    chunks += Chunk(fourCc, data)
                }
                "VP8 " -> {
                    if (width == 0) {
                        // Key frame header: 3 bytes frame tag, 3 bytes start code, then 14-bit width and height.
                        require(size >= 10) { "VP8 trop court" }
                        width = u16(data, 6) and 0x3FFF
                        height = u16(data, 8) and 0x3FFF
                    }
                    chunks += Chunk(fourCc, data)
                }
                "VP8L" -> {
                    require(size >= 5 && data[0].toInt() and 0xFF == 0x2F) { "VP8L invalide" }
                    val bits = u32(data, 1)
                    if (width == 0) {
                        width = (bits and 0x3FFF) + 1
                        height = ((bits ushr 14) and 0x3FFF) + 1
                    }
                    alpha = alpha || (bits ushr 28) and 1 != 0
                    chunks += Chunk(fourCc, data)
                }
                // ICCP, EXIF, XMP and unknown chunks are left out of the frames.
            }
            at = start + size + (size and 1)
        }
        require(chunks.any { it.fourCc == "VP8 " || it.fourCc == "VP8L" }) { "WebP sans image" }
        require(width > 0 && height > 0) { "Taille de l'image inconnue" }
        return Still(width, height, chunks, alpha)
    }

    /**
     * An animated WebP of [frames] (still WebP bytes, all [width] × [height]), shown [durationsMs] each, looping
     * forever. Frames replace each other (no blending, disposed to transparent), so cut-out stickers do not smear.
     */
    fun assemble(frames: List<ByteArray>, durationsMs: List<Int>, width: Int, height: Int): ByteArray {
        require(frames.isNotEmpty()) { "Aucune image" }
        require(frames.size == durationsMs.size) { "Une durée par image" }
        require(width in 1..16384 && height in 1..16384) { "Taille invalide" }
        val stills = frames.map(::parse)
        val alpha = stills.any { it.hasAlpha }
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))
        // VP8X: animation flag (0x02), alpha flag (0x10), canvas size - 1.
        val vp8x = ByteArray(10)
        vp8x[0] = ((if (alpha) 0x10 else 0) or 0x02).toByte()
        put24(vp8x, 4, width - 1)
        put24(vp8x, 7, height - 1)
        chunk(body, "VP8X", vp8x)
        // ANIM: transparent background, loop forever.
        chunk(body, "ANIM", ByteArray(6))
        stills.forEachIndexed { index, still ->
            require(still.width <= width && still.height <= height) { "Image ${index + 1} plus grande que la toile" }
            val frame = ByteArrayOutputStream()
            val header = ByteArray(16)
            // Offset X/2, Y/2 = 0; frame size - 1; duration; flags: do not blend (0x02), dispose to background (0x01).
            put24(header, 6, still.width - 1)
            put24(header, 9, still.height - 1)
            put24(header, 12, durationsMs[index].coerceIn(1, 0xFFFFFF))
            header[15] = 0x03
            frame.write(header)
            still.chunks.forEach { chunk(frame, it.fourCc, it.data) }
            chunk(body, "ANMF", frame.toByteArray())
        }
        val payload = body.toByteArray()
        val out = ByteArrayOutputStream(payload.size + 8)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    /** Frame count and total duration of an animated WebP (null when it is not one): used to check what is written. */
    fun describe(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 20 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WEBP") return null
        val end = minOf(bytes.size, 8 + u32(bytes, 4))
        var at = 12
        var frames = 0
        var total = 0
        while (at + 8 <= end) {
            val size = u32(bytes, at + 4)
            if (size < 0 || at + 8 + size > end) return null
            if (ascii(bytes, at) == "ANMF" && size >= 16) {
                frames++
                total += u24(bytes, at + 8 + 12)
            }
            at += 8 + size + (size and 1)
        }
        return if (frames == 0) null else frames to total
    }

    private fun chunk(out: ByteArrayOutputStream, fourCc: String, data: ByteArray) {
        out.write(fourCc.toByteArray(Charsets.US_ASCII))
        out.write(le32(data.size))
        out.write(data)
        if (data.size and 1 == 1) out.write(0)
    }

    private fun ascii(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u24(b: ByteArray, at: Int) = u16(b, at) or ((b[at + 2].toInt() and 0xFF) shl 16)

    private fun u32(b: ByteArray, at: Int) = u24(b, at) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun put24(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte()
        b[at + 1] = (v ushr 8).toByte()
        b[at + 2] = (v ushr 16).toByte()
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
}
