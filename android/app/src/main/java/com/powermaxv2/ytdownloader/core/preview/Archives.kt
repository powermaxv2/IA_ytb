package com.powermaxv2.ytdownloader.core.preview

// The contents of a ZIP archive as the Aperçu lists them: never extracted, names checked for tricks. Pure.

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** One entry as the ZIP central directory gives it. Sizes are -1 when unknown. */
data class ZipEntryInfo(val name: String, val size: Long, val compressedSize: Long, val isDirectory: Boolean)

/** A row of the archive tree: [depth] 0 at the root, [label] the last part of the path. */
data class ZipRow(
    val path: String,
    val label: String,
    val depth: Int,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    /** Why the name is dangerous if extracted carelessly, or null. */
    val warning: String?,
)

/** What the viewer shows: the rows, the total number of entries and the sums. */
data class ZipListing(
    val rows: List<ZipRow>,
    /** Entries in the archive (may be more than listed). */
    val totalEntries: Int,
    /** The listing stopped at [ZipNames.MAX_ENTRIES]. */
    val truncated: Boolean,
    val totalSize: Long,
    val totalCompressed: Long,
    val suspicious: Int,
    /**
     * Android (14 and later) refused to open the archive because of a dangerous name; the rows come from our own
     * read of its central directory ([ZipCentralDirectory]).
     */
    val refusedByAndroid: Boolean = false,
) {
    val files: Int get() = rows.count { !it.isDirectory }
}

object ZipNames {
    /** Most entries listed. */
    const val MAX_ENTRIES = 5000

    /** Most rows drawn, implied folders and suspicious entries included. */
    const val MAX_ROWS = 5000

    /** Deepest path accepted in the tree (deeper names are flagged and listed flat). */
    const val MAX_DEPTH = 64

    /** Longest name accepted in the tree (longer names are flagged and listed flat). */
    const val MAX_NAME_CHARS = 1024

    /** Longest label drawn for one row. */
    const val MAX_LABEL_CHARS = 200

    /** Most bytes of an archive copied to the work dir to list it. */
    const val MAX_ARCHIVE_BYTES: Long = 512L * 1024 * 1024

    /**
     * Why [name] would be dangerous to extract (or to draw), in French, or null when it is a plain relative path:
     * parent folder jumps (".."), absolute paths ("/etc/…", "C:\…"), backslashes (Windows separators that some
     * tools follow), control characters, absurd length or depth.
     */
    fun warning(name: String): String? {
        if (name.length > MAX_NAME_CHARS) return "Nom anormalement long (${name.length} caractères)"
        if (name.count { it == '/' } > MAX_DEPTH) return "Dossiers imbriqués anormalement profond"
        if (name.any { it.code < 0x20 || it.code == 0x7F }) return "Caractères de contrôle dans le nom"
        if (name.startsWith("/") || name.startsWith("\\")) return "Chemin absolu"
        if (name.length >= 2 && name[0].isLetter() && name[1] == ':') return "Chemin absolu (lecteur Windows)"
        if (name.split('/', '\\').any { it == ".." }) return "Remonte hors du dossier (..)"
        if ('\\' in name) return "Séparateurs Windows (\\)"
        return null
    }

    /**
     * Whether a ZipException [message] is Android's refusal of a dangerous entry name ("Invalid zip entry path: …",
     * thrown by ZipFile and ZipInputStream for apps targeting Android 14+) rather than a damaged archive.
     */
    fun isPathRefusal(message: String?): Boolean = message?.contains("Invalid zip entry path") == true

    /** [name] made safe to draw: control characters become "\uFFFD", at most [MAX_LABEL_CHARS] characters. */
    fun display(name: String): String = buildString(minOf(name.length, MAX_LABEL_CHARS + 1)) {
        for (c in name.take(MAX_LABEL_CHARS)) append(if (c.code < 0x20 || c.code == 0x7F) '\uFFFD' else c)
        if (name.length > MAX_LABEL_CHARS) append('…')
    }

    /** A folder or file of the tree being built. */
    private class Node(val name: String, var isDirectory: Boolean) {
        val children = LinkedHashMap<String, Node>()
        var size = -1L
        var compressed = -1L
    }

    /**
     * The tree of [entries]: every folder (also the ones the archive only implies) before its contents, sub-folders
     * first then files by name, [ZipRow.depth] from the path. Suspicious names are listed flat, first, with their
     * warning. At most [MAX_ROWS] rows in all (then [ZipListing.truncated] is set); each name is walked once, so
     * the work is linear in the size of the names.
     */
    fun tree(entries: List<ZipEntryInfo>, totalEntries: Int = entries.size, truncated: Boolean = false): ZipListing {
        val root = Node("", true)
        val odd = ArrayList<ZipRow>()
        var nodes = 0
        var capped = false
        var totalSize = 0L
        var totalCompressed = 0L
        entries@ for (e in entries) {
            if (nodes + odd.size >= MAX_ROWS) {
                capped = true
                break
            }
            if (!e.isDirectory) {
                if (e.size > 0) totalSize += e.size
                if (e.compressedSize > 0) totalCompressed += e.compressedSize
            }
            val warning = warning(e.name)
            if (warning != null) {
                odd.add(ZipRow(display(e.name), display(e.name), 0, e.isDirectory, e.size, e.compressedSize, warning))
                continue
            }
            val parts = e.name.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty()) continue
            val isDir = e.isDirectory || e.name.endsWith("/")
            var node = root
            for (i in parts.indices) {
                val last = i == parts.lastIndex
                var child = node.children[parts[i]]
                if (child == null) {
                    if (nodes + odd.size >= MAX_ROWS) {
                        capped = true
                        break@entries
                    }
                    child = Node(parts[i], !last || isDir)
                    node.children[parts[i]] = child
                    nodes++
                } else if (!last || isDir) {
                    child.isDirectory = true
                }
                if (last && !isDir) {
                    child.size = e.size
                    child.compressed = e.compressedSize
                }
                node = child
            }
        }
        return ZipListing(
            rows = odd + flatten(root),
            totalEntries = totalEntries,
            truncated = truncated || capped,
            totalSize = totalSize,
            totalCompressed = totalCompressed,
            suspicious = odd.size,
        )
    }

    /** Depth-first rows of the tree, with an explicit stack (no recursion). */
    private fun flatten(root: Node): List<ZipRow> {
        val out = ArrayList<ZipRow>()
        // (node, depth, path of its parent)
        val stack = ArrayDeque<Triple<Node, Int, String>>()
        fun pushChildren(parent: Node, depth: Int, path: String) {
            val sorted = parent.children.values.sortedWith(
                compareBy<Node>({ !it.isDirectory }, { it.name.lowercase() }, { it.name }),
            )
            for (i in sorted.indices.reversed()) stack.addLast(Triple(sorted[i], depth, path))
        }
        pushChildren(root, 0, "")
        while (stack.isNotEmpty()) {
            val (node, depth, parentPath) = stack.removeLast()
            val path = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            out.add(
                ZipRow(
                    path = path,
                    label = display(node.name),
                    depth = depth,
                    isDirectory = node.isDirectory,
                    size = if (node.isDirectory) -1 else node.size,
                    compressedSize = if (node.isDirectory) -1 else node.compressed,
                    warning = null,
                ),
            )
            if (node.children.isNotEmpty()) pushChildren(node, depth + 1, path)
        }
        return out
    }
}

/**
 * Entry names and sizes read straight from a ZIP's central directory, for archives that `java.util.zip` refuses
 * to open (Android 14+ throws "Invalid zip entry path" on names with ".." or a leading "/"). Only the directory
 * records are read: nothing is decompressed, nothing extracted. Same caps as the normal listing. Pure JVM.
 */
object ZipCentralDirectory {
    /** Entries read (at most the cap) and the count the archive declares. */
    class Result(val entries: List<ZipEntryInfo>, val totalEntries: Int)

    private const val EOCD = 0x06054b50L
    private const val ZIP64_LOCATOR = 0x07064b50L
    private const val ZIP64_EOCD = 0x06064b50L
    private const val CENTRAL = 0x02014b50L
    private const val EOCD_SIZE = 22
    private const val CENTRAL_SIZE = 46

    /** Longest name read, in bytes: anything longer is flagged as abnormally long anyway ([ZipNames.warning]). */
    private const val MAX_NAME_BYTES = 4 * ZipNames.MAX_NAME_CHARS + 4

    /** The entries of [file], at most [maxEntries], or null when it is not a ZIP this reader understands. */
    fun read(file: File, maxEntries: Int = ZipNames.MAX_ENTRIES): Result? = try {
        RandomAccessFile(file, "r").use { read(it, maxEntries) }
    } catch (e: IOException) {
        null
    }

    private fun read(raf: RandomAccessFile, maxEntries: Int): Result? {
        val length = raf.length()
        if (length < EOCD_SIZE) return null
        // The end record is in the last 22 bytes, plus a comment of at most 65 535 bytes.
        val tailSize = minOf(length, (EOCD_SIZE + 0xFFFF).toLong()).toInt()
        val tail = ByteArray(tailSize)
        raf.seek(length - tailSize)
        raf.readFully(tail)
        var at = tailSize - EOCD_SIZE
        while (at >= 0 && u32(tail, at) != EOCD) at--
        if (at < 0) return null
        val eocdPos = length - tailSize + at
        var total = u16(tail, at + 10).toLong()
        var cdSize = u32(tail, at + 12)
        var cdOffset = u32(tail, at + 16)
        if (total == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
            // ZIP64: the locator sits just before the end record and points at the ZIP64 end record.
            if (eocdPos < 20) return null
            val locator = ByteArray(20)
            raf.seek(eocdPos - 20)
            raf.readFully(locator)
            if (u32(locator, 0) != ZIP64_LOCATOR) return null
            val z64 = u64(locator, 8)
            if (z64 < 0 || z64 + 56 > length) return null
            val record = ByteArray(56)
            raf.seek(z64)
            raf.readFully(record)
            if (u32(record, 0) != ZIP64_EOCD) return null
            total = u64(record, 32)
            cdSize = u64(record, 40)
            cdOffset = u64(record, 48)
        }
        if (total < 0 || cdSize < 0 || cdOffset < 0 || cdOffset > length || cdSize > length - cdOffset) return null
        val end = cdOffset + cdSize
        val count = minOf(total, maxEntries.toLong()).toInt()
        val entries = ArrayList<ZipEntryInfo>(minOf(count, 1024))
        val header = ByteArray(CENTRAL_SIZE)
        var pos = cdOffset
        for (i in 0 until count) {
            if (pos + CENTRAL_SIZE > end) break
            raf.seek(pos)
            raf.readFully(header)
            if (u32(header, 0) != CENTRAL) return null
            val flags = u16(header, 8)
            val compressed = u32(header, 20)
            val size = u32(header, 24)
            val nameLength = u16(header, 28)
            val extraLength = u16(header, 30)
            val commentLength = u16(header, 32)
            if (pos + CENTRAL_SIZE + nameLength > end) return null
            val nameBytes = ByteArray(minOf(nameLength, MAX_NAME_BYTES))
            raf.readFully(nameBytes)
            val name = String(nameBytes, if ((flags and 0x0800) != 0) Charsets.UTF_8 else legacyCharset)
            entries.add(
                ZipEntryInfo(
                    name = name,
                    // 0xFFFFFFFF: the real size is in a ZIP64 extra field; shown as unknown.
                    size = if (size == 0xFFFFFFFFL) -1 else size,
                    compressedSize = if (compressed == 0xFFFFFFFFL) -1 else compressed,
                    isDirectory = name.endsWith("/"),
                ),
            )
            pos += CENTRAL_SIZE.toLong() + nameLength + extraLength + commentLength
        }
        val declared = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Result(entries, maxOf(declared, entries.size))
    }

    /** Names without the UTF-8 flag are in the old DOS code page (what ZIP tools write by default). */
    private val legacyCharset: Charset = runCatching { Charset.forName("IBM437") }.getOrDefault(Charsets.ISO_8859_1)

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long = (u16(b, i).toLong()) or (u16(b, i + 2).toLong() shl 16)

    /** Negative when the top bit is set (no real archive is that big): callers reject it. */
    private fun u64(b: ByteArray, i: Int): Long = u32(b, i) or (u32(b, i + 4) shl 32)
}
