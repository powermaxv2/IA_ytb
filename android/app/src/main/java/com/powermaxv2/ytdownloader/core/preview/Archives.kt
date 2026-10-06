package com.powermaxv2.ytdownloader.core.preview

// The contents of a ZIP archive as the Aperçu lists them: never extracted, names checked for tricks. Pure.

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
) {
    val files: Int get() = rows.count { !it.isDirectory }
}

object ZipNames {
    /** Most entries listed. */
    const val MAX_ENTRIES = 5000

    /** Most bytes of an archive copied to the work dir to list it. */
    const val MAX_ARCHIVE_BYTES: Long = 512L * 1024 * 1024

    /**
     * Why [name] would be dangerous to extract, in French, or null when it is a plain relative path: parent
     * folder jumps (".."), absolute paths ("/etc/…", "C:\…"), backslashes (Windows separators that some tools
     * follow), control characters.
     */
    fun warning(name: String): String? {
        if (name.any { it.code < 0x20 || it.code == 0x7F }) return "Caractères de contrôle dans le nom"
        if (name.startsWith("/") || name.startsWith("\\")) return "Chemin absolu"
        if (name.length >= 2 && name[0].isLetter() && name[1] == ':') return "Chemin absolu (lecteur Windows)"
        if (name.split('/', '\\').any { it == ".." }) return "Remonte hors du dossier (..)"
        if ('\\' in name) return "Séparateurs Windows (\\)"
        return null
    }

    /** [name] made safe to draw: control characters become "�" (the warning tells why). */
    fun display(name: String): String = buildString(name.length) {
        for (c in name) append(if (c.code < 0x20 || c.code == 0x7F) '\uFFFD' else c)
    }

    /**
     * The tree of [entries]: sorted by path, every folder (also the ones the archive does not list, only implies)
     * before its contents, [depth] from the number of "/" in the path. Suspicious names are kept as they are, at
     * the root, with their warning.
     */
    fun tree(entries: List<ZipEntryInfo>, totalEntries: Int = entries.size, truncated: Boolean = false): ZipListing {
        val rows = LinkedHashMap<String, ZipRow>()
        val odd = ArrayList<ZipRow>()
        var totalSize = 0L
        var totalCompressed = 0L
        for (e in entries) {
            if (!e.isDirectory) {
                if (e.size > 0) totalSize += e.size
                if (e.compressedSize > 0) totalCompressed += e.compressedSize
            }
            val warning = warning(e.name)
            if (warning != null) {
                odd.add(ZipRow(e.name, display(e.name), 0, e.isDirectory, e.size, e.compressedSize, warning))
                continue
            }
            val path = e.name.trimEnd('/')
            if (path.isEmpty()) continue
            val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty()) continue
            // Implied parent folders.
            for (d in 1 until parts.size) {
                val dir = parts.subList(0, d).joinToString("/")
                if (dir !in rows) rows[dir] = ZipRow(dir, display(parts[d - 1]), d - 1, true, -1, -1, null)
            }
            val clean = parts.joinToString("/")
            val isDir = e.isDirectory || e.name.endsWith("/")
            if (clean in rows && rows[clean]!!.isDirectory && isDir) continue
            rows[clean] = ZipRow(clean, display(parts.last()), parts.size - 1, isDir, e.size, e.compressedSize, null)
        }
        val sorted = rows.values.sortedWith { a, b -> comparePaths(a, b) }
        return ZipListing(
            rows = odd + sorted,
            totalEntries = totalEntries,
            truncated = truncated,
            totalSize = totalSize,
            totalCompressed = totalCompressed,
            suspicious = odd.size,
        )
    }

    /** Folder contents right after the folder; inside a folder, sub-folders first, then files, by name. */
    private fun comparePaths(a: ZipRow, b: ZipRow): Int {
        val pa = a.path.split('/')
        val pb = b.path.split('/')
        val n = minOf(pa.size, pb.size)
        for (i in 0 until n) {
            if (pa[i] == pb[i]) continue
            // At the first difference: is each side a folder at this level?
            val aDir = i < pa.size - 1 || a.isDirectory
            val bDir = i < pb.size - 1 || b.isDirectory
            if (aDir != bDir) return if (aDir) -1 else 1
            val c = pa[i].compareTo(pb[i], ignoreCase = true)
            return if (c != 0) c else pa[i].compareTo(pb[i])
        }
        return pa.size - pb.size
    }
}
