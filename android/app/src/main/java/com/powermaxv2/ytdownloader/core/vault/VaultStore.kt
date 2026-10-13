package com.powermaxv2.ytdownloader.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale

// The Coffre-fort on disk (plain JVM, tested off the phone), in the app's private folder (filesDir/vault):
//   vault.key       the master key, wrapped by the code (PBKDF2 rounds and salt in the clear, the rest sealed)
//   index.bin       the list of the files (names, types, sizes, dates), sealed
//   items/<id>.bin  each file, sealed in chunks
//   items/<id>.jpg  its small picture for the grid, sealed
// Nothing in there can be read without the code: not even the names.

/** What a file in the vault is, for its icon and how the viewer shows it. */
enum class VaultKind(val label: String) {
    PHOTO("Photo"), VIDEO("Vidéo"), AUDIO("Son"), PDF("PDF"), TEXT("Texte"), OTHER("Fichier");

    companion object {
        fun of(mime: String): VaultKind = when {
            mime.startsWith("image/") -> PHOTO
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            mime == "application/pdf" -> PDF
            mime.startsWith("text/") || mime == "application/json" -> TEXT
            else -> OTHER
        }
    }
}

/** One file in the vault (only known once it is open). */
data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long,
    val hasThumb: Boolean,
) {
    val kind: VaultKind get() = VaultKind.of(mime)
}

/** The master key of an open vault. [wipe] zeroes it when the vault locks. */
class VaultKey internal constructor(internal val bytes: ByteArray) {
    fun wipe() = bytes.fill(0)

    /** A copy of the raw key, to wrap it for the fingerprint unlock. */
    fun export(): ByteArray = bytes.copyOf()
}

/** Rules of the code: digits only, typed on the vault's own keypad. */
object VaultCode {
    const val MIN = 6
    const val MAX = 12

    fun isValid(code: String): Boolean = code.length in MIN..MAX && code.all { it in '0'..'9' }

    /** Too easy to guess: one digit repeated, or a run up or down (123456, 987654). */
    fun isWeak(code: String): Boolean {
        if (code.length < 2) return true
        if (code.all { it == code[0] }) return true
        val steps = code.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}

/** Waiting time after wrong codes: free for the first 4, then 30 s, doubling, up to 15 min. */
object VaultThrottle {
    const val FREE_TRIES = 4

    fun waitMillis(failures: Int): Long {
        if (failures <= FREE_TRIES) return 0L
        val doubling = (failures - FREE_TRIES - 1).coerceAtMost(5)
        return (30_000L shl doubling).coerceAtMost(15 * 60_000L)
    }
}

class VaultStore(val dir: File) {
    private val keyFile = File(dir, "vault.key")
    private val indexFile = File(dir, "index.bin")
    private val itemsDir = File(dir, "items")

    val exists: Boolean get() = keyFile.isFile

    /** Makes a new, empty vault protected by [code]; fails if one is already there. */
    @Synchronized
    fun create(code: String, iterations: Int = VaultCrypto.ITERATIONS): VaultKey {
        require(VaultCode.isValid(code)) { "invalid code" }
        check(!exists) { "vault already exists" }
        itemsDir.mkdirs()
        val key = VaultKey(VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES))
        writeKeyFile(key, code, iterations)
        writeIndex(key, emptyList())
        return key
    }

    /** Opens the vault with [code]; [WrongCodeException] when it is not the right one. */
    fun unlock(code: String): VaultKey {
        val bytes = readKeyFile()
        val header = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(4).also(header::readFully)
        if (!magic.contentEquals(KEY_MAGIC)) throw VaultCorruptException("not a vault key")
        val iterations = header.readInt()
        if (iterations !in 1..10_000_000) throw VaultCorruptException("bad rounds")
        val salt = ByteArray(VaultCrypto.SALT_BYTES).also(header::readFully)
        val headerSize = 4 + 4 + VaultCrypto.SALT_BYTES
        val wrap = VaultCrypto.deriveKey(code, salt, iterations)
        try {
            val master = VaultCrypto.open(wrap, bytes.copyOfRange(headerSize, bytes.size), bytes.copyOfRange(0, headerSize))
            return VaultKey(master)
        } finally {
            wrap.fill(0)
        }
    }

    /** Opens the vault with its raw master key (fingerprint); [WrongCodeException] when it is not this vault's. */
    fun unlockRaw(master: ByteArray): VaultKey {
        val key = VaultKey(master.copyOf())
        readIndex(key) // throws when the key is not this vault's
        return key
    }

    /** New code, same files: only the wrapped key is written again. */
    @Synchronized
    fun changeCode(key: VaultKey, newCode: String, iterations: Int = VaultCrypto.ITERATIONS) {
        require(VaultCode.isValid(newCode)) { "invalid code" }
        writeKeyFile(key, newCode, iterations)
    }

    @Synchronized
    fun items(key: VaultKey): List<VaultItem> = readIndex(key)

    /**
     * Seals [input] into the vault as [name]. The index is written last, so a file cut off half way (phone off)
     * is just an orphan that [cleanOrphans] removes.
     */
    fun add(
        key: VaultKey,
        name: String,
        mime: String,
        input: InputStream,
        thumb: ByteArray? = null,
        now: Long = System.currentTimeMillis(),
        onProgress: (Long) -> Unit = {},
    ): VaultItem {
        itemsDir.mkdirs()
        val id = newId()
        val data = File(itemsDir, "$id.bin")
        val size = try {
            data.outputStream().buffered().use { VaultCrypto.encryptStream(key.bytes, id, input, it, onProgress = onProgress) }
        } catch (e: Exception) {
            data.delete()
            throw e
        }
        if (thumb != null) File(itemsDir, "$id.jpg").writeBytes(VaultCrypto.seal(key.bytes, thumb, thumbAad(id)))
        val item = VaultItem(id, cleanName(name), mime.ifBlank { "application/octet-stream" }, size, now, thumb != null)
        synchronized(this) { writeIndex(key, readIndex(key) + item) }
        return item
    }

    /** The plain bytes of [item], streamed. */
    fun open(key: VaultKey, item: VaultItem): InputStream =
        VaultCrypto.decryptStream(key.bytes, item.id, File(itemsDir, "${checkId(item.id)}.bin").inputStream().buffered())

    /** The whole of [item] in memory, if it is at most [limit] bytes. */
    fun readAll(key: VaultKey, item: VaultItem, limit: Long): ByteArray {
        if (item.size > limit) throw VaultCorruptException("file larger than $limit bytes")
        return File(itemsDir, "${checkId(item.id)}.bin").inputStream().buffered().use { VaultCrypto.decryptAll(key.bytes, item.id, it, limit) }
    }

    /** The small picture of [item], or null. */
    fun thumb(key: VaultKey, item: VaultItem): ByteArray? {
        if (!item.hasThumb) return null
        val file = File(itemsDir, "${checkId(item.id)}.jpg")
        if (!file.isFile) return null
        return runCatching { VaultCrypto.open(key.bytes, file.readBytes(), thumbAad(item.id)) }.getOrNull()
    }

    /** Takes [ids] out of the vault for good (index first, then the files). */
    @Synchronized
    fun remove(key: VaultKey, ids: Set<String>) {
        writeIndex(key, readIndex(key).filterNot { it.id in ids })
        ids.forEach { id ->
            if (ID.matches(id)) {
                File(itemsDir, "$id.bin").delete()
                File(itemsDir, "$id.jpg").delete()
            }
        }
    }

    /** Deletes files left by an import cut off half way (in items/ but not in the index). */
    @Synchronized
    fun cleanOrphans(key: VaultKey): Int {
        val known = readIndex(key).map { it.id }.toSet()
        var removed = 0
        itemsDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in known) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    /** Space the vault takes on the phone. */
    fun usedBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes the whole vault: the files cannot be got back. */
    @Synchronized
    fun destroy() {
        dir.deleteRecursively()
    }

    // MARK: - Files

    private fun writeKeyFile(key: VaultKey, code: String, iterations: Int) {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val header = ByteBuffer.allocate(4 + 4 + salt.size).put(KEY_MAGIC).putInt(iterations).put(salt).array()
        val wrap = VaultCrypto.deriveKey(code, salt, iterations)
        try {
            atomicWrite(keyFile, header + VaultCrypto.seal(wrap, key.bytes, header))
        } finally {
            wrap.fill(0)
        }
    }

    private fun readKeyFile(): ByteArray {
        if (!keyFile.isFile) throw IOException("no vault")
        return keyFile.readBytes()
    }

    private fun readIndex(key: VaultKey): List<VaultItem> {
        if (!indexFile.isFile) throw VaultCorruptException("index missing")
        val plain = VaultCrypto.open(key.bytes, indexFile.readBytes(), INDEX_AAD)
        val input = DataInputStream(ByteArrayInputStream(plain))
        if (input.readInt() != INDEX_VERSION) throw VaultCorruptException("unknown index version")
        val count = input.readInt()
        return List(count) {
            VaultItem(
                id = checkId(input.readUTF()),
                name = input.readUTF(),
                mime = input.readUTF(),
                size = input.readLong(),
                addedAt = input.readLong(),
                hasThumb = input.readBoolean(),
            )
        }
    }

    private fun writeIndex(key: VaultKey, items: List<VaultItem>) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(INDEX_VERSION)
            out.writeInt(items.size)
            items.forEach {
                out.writeUTF(it.id)
                out.writeUTF(it.name)
                out.writeUTF(it.mime)
                out.writeLong(it.size)
                out.writeLong(it.addedAt)
                out.writeBoolean(it.hasThumb)
            }
        }
        atomicWrite(indexFile, VaultCrypto.seal(key.bytes, bytes.toByteArray(), INDEX_AAD))
    }

    /** Writes next to [file] then renames, so a crash leaves the old file or the new one, never half of one. */
    private fun atomicWrite(file: File, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
        }
    }

    companion object {
        private val KEY_MAGIC = "GVK1".toByteArray(Charsets.US_ASCII)
        private val INDEX_AAD = "gulp-vault-index".toByteArray(Charsets.US_ASCII)
        private const val INDEX_VERSION = 1
        private val ID = Regex("[0-9a-f]{32}")

        private fun thumbAad(id: String) = "thumb:$id".toByteArray(Charsets.US_ASCII)

        private fun newId(): String = VaultCrypto.randomBytes(16).joinToString("") { "%02x".format(Locale.ROOT, it) }

        private fun checkId(id: String): String = id.also { if (!ID.matches(it)) throw VaultCorruptException("bad id") }

        /** A file name kept short and without folders, or "fichier" when there is nothing left. */
        fun cleanName(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[\\u0000-\\u001F]"), "")
                .trim()
                .let { if (it.length > 120) it.take(80) + "…" + it.takeLast(39) else it }
                .ifEmpty { "fichier" }
    }
}
