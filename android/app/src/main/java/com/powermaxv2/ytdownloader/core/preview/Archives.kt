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
