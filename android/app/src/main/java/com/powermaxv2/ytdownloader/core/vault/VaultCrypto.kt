package com.powermaxv2.ytdownloader.core.vault

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// The Coffre-fort's cryptography, plain JVM (tested off the phone). Every file of the vault is AES-256-GCM:
// - the master key (random, 32 bytes) is wrapped by a key derived from the code (PBKDF2-HMAC-SHA256, salted);
// - files are sealed in chunks of 64 KiB, each chunk with its own nonce and authenticated with the file's id, its
//   index and a "last chunk" flag, so a chunk swapped, moved, dropped or a file cut short fails to open.

/** The code was wrong (or the vault file was tampered with: GCM cannot tell the two apart). */
class WrongCodeException : GeneralSecurityException("wrong code")

/** A vault file that does not look like one (other magic, cut short, tag mismatch). */
class VaultCorruptException(message: String, cause: Throwable? = null) : IOException(message, cause)

object VaultCrypto {
    const val KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val TAG_BYTES = 16
    const val CHUNK = 64 * 1024

    /** Default PBKDF2 rounds: about a second on a mid-range phone. Stored in the vault, so it can grow later. */
    const val ITERATIONS = 310_000

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    /**
     * PBKDF2-HMAC-SHA256 of [code]. Android 8+ (and the JVM) have it built in; Android 7 does not, so it falls back on
     * the same algorithm written out (same bytes either way, see the tests).
     */
    fun deriveKey(code: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(iterations > 0)
        return try {
            val spec = PBEKeySpec(code.toCharArray(), salt, iterations, KEY_BYTES * 8)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } catch (_: NoSuchAlgorithmException) {
            pbkdf2(code.toByteArray(Charsets.UTF_8), salt, iterations, KEY_BYTES)
        } catch (_: InvalidKeySpecException) {
            pbkdf2(code.toByteArray(Charsets.UTF_8), salt, iterations, KEY_BYTES)
        }
    }

    /** PBKDF2 (RFC 8018) with HMAC-SHA256, written out. */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(password.isNotEmpty()) { "empty code" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val out = ByteArray(length)
        val u = ByteArray(hLen)
        val t = ByteArray(hLen)
        var block = 1
        var offset = 0
        while (offset < length) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(block).array())
            mac.doFinal(u, 0)
            u.copyInto(t)
            repeat(iterations - 1) {
                mac.update(u)
                mac.doFinal(u, 0)
                for (i in 0 until hLen) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val n = minOf(hLen, length - offset)
            t.copyInto(out, offset, 0, n)
            offset += n
            block++
        }
        return out
    }

    // MARK: - Small blobs (the wrapped key, the index, the thumbnails)

    /** [plain] sealed with [key]: a random nonce followed by the ciphertext and its tag. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = gcm(Cipher.ENCRYPT_MODE, key, iv)
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plain)
    }

    /** Opens what [seal] made; [WrongCodeException] when the key or the [aad] do not match, or the bytes changed. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        if (sealed.size < IV_BYTES + TAG_BYTES) throw VaultCorruptException("sealed blob too short")
        val cipher = gcm(Cipher.DECRYPT_MODE, key, sealed.copyOfRange(0, IV_BYTES))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (_: GeneralSecurityException) {
            throw WrongCodeException()
        }
    }

    // MARK: - Files, in chunks

    private val FILE_MAGIC = "GVF1".toByteArray(Charsets.US_ASCII)
    private const val PREFIX_BYTES = 8

    /**
     * Copies [input] into [output] sealed with [key], bound to [id]. Returns the number of plain bytes read.
     * [onProgress] gets the plain bytes done so far after each chunk.
     */
    fun encryptStream(
        key: ByteArray,
        id: String,
        input: InputStream,
        output: OutputStream,
        chunk: Int = CHUNK,
        onProgress: (Long) -> Unit = {},
    ): Long {
        val prefix = randomBytes(PREFIX_BYTES)
        output.write(FILE_MAGIC)
        output.write(prefix)
        output.write(ByteBuffer.allocate(4).putInt(chunk).array())
        val source = PushbackInputStream(input, 1)
        val buffer = ByteArray(chunk)
        var index = 0
        var total = 0L
        while (true) {
            val n = readFully(source, buffer)
            val last = n < chunk || peekEnd(source)
            val cipher = gcm(Cipher.ENCRYPT_MODE, key, nonce(prefix, index))
            cipher.updateAAD(chunkAad(id, index, last))
            output.write(cipher.doFinal(buffer, 0, n))
            total += n
            onProgress(total)
            if (last) break
            index++
        }
        output.flush()
        return total
    }

    /** An [InputStream] of the plain bytes of a file [encryptStream] wrote for [id]; fails with [VaultCorruptException]. */
    fun decryptStream(key: ByteArray, id: String, input: InputStream): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(FILE_MAGIC.size)
        val prefix = ByteArray(PREFIX_BYTES)
        try {
            data.readFully(magic)
            data.readFully(prefix)
        } catch (e: EOFException) {
            throw VaultCorruptException("file header cut short", e)
        }
        if (!magic.contentEquals(FILE_MAGIC)) throw VaultCorruptException("not a vault file")
        val chunk = data.readInt()
        if (chunk <= 0 || chunk > 16 * 1024 * 1024) throw VaultCorruptException("bad chunk size")
        return ChunkReader(key, id, prefix, chunk, PushbackInputStream(input, 1))
    }

    /** Decrypts a whole file into memory (thumbnails, pictures, texts). */
    fun decryptAll(key: ByteArray, id: String, input: InputStream, limit: Long = Long.MAX_VALUE): ByteArray {
        val out = ByteArrayOutputStream()
        decryptStream(key, id, input).use { plain ->
            val buffer = ByteArray(CHUNK)
            while (true) {
                val n = plain.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > limit) throw VaultCorruptException("file larger than $limit bytes")
            }
        }
        return out.toByteArray()
    }

    private class ChunkReader(
        private val key: ByteArray,
        private val id: String,
        private val prefix: ByteArray,
        private val chunk: Int,
        private val source: PushbackInputStream,
    ) : InputStream() {
        private val sealed = ByteArray(chunk + TAG_BYTES)
        private var plain = ByteArray(0)
        private var position = 0
        private var index = 0
        private var finished = false

        private fun fill(): Boolean {
            while (position >= plain.size) {
                if (finished) return false
                val n = readFully(source, sealed)
                if (n < TAG_BYTES) throw VaultCorruptException("file cut short")
                val last = n < sealed.size || peekEnd(source)
                val cipher = gcm(Cipher.DECRYPT_MODE, key, nonce(prefix, index))
                cipher.updateAAD(chunkAad(id, index, last))
                plain = try {
                    cipher.doFinal(sealed, 0, n)
                } catch (e: GeneralSecurityException) {
                    throw VaultCorruptException("chunk $index does not open", e)
                }
                position = 0
                index++
                finished = last
            }
            return true
        }

        override fun read(): Int = if (fill()) plain[position++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, plain.size - position)
            plain.copyInto(b, off, position, position + n)
            position += n
            return n
        }

        override fun close() = source.close()
    }

    private fun gcm(mode: Int, key: ByteArray, iv: ByteArray): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, iv))
    }

    private fun nonce(prefix: ByteArray, index: Int): ByteArray = prefix + ByteBuffer.allocate(4).putInt(index).array()

    private fun chunkAad(id: String, index: Int, last: Boolean): ByteArray =
        id.toByteArray(Charsets.UTF_8) + ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array()

    /** Reads until [buffer] is full or the stream ends; the count read. */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var n = 0
        while (n < buffer.size) {
            val r = input.read(buffer, n, buffer.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun peekEnd(input: PushbackInputStream): Boolean {
        val b = input.read()
        if (b < 0) return true
        input.unread(b)
        return false
    }
}
