package com.powermaxv2.ytdownloader.core.photodup

import com.powermaxv2.ytdownloader.core.Fmt

// Doublons photos' pure part: the picture fingerprint (dHash), which photos are the same or look the same, which
// one to keep, what is pre-checked, and the sentences the screen shows. No Android here (unit tested).

/** One photo of the gallery. [id] is its MediaStore id; [album] is its folder as the gallery shows it ("Camera"). */
data class Photo(
    val id: Long,
    val name: String,
    val size: Long,
    /** Date taken (or added), in ms; 0 when unknown. */
    val taken: Long,
    val width: Int,
    val height: Int,
    val album: String,
) {
    val pixels: Long get() = width.toLong() * height
}

/** How alike the photos of a group are. */
enum class DupKind(val label: String) {
    /** Byte for byte the same file. */
    EXACT("Identiques"),

    /** The same picture, saved twice (resized, recompressed, sent back by WhatsApp) or shot in a burst. */
    SIMILAR("Ressemblantes"),
}

/** Photos that are copies of each other: [keep] is the one suggested to stay, [others] can go. */
data class DupGroup(val kind: DupKind, val keep: Photo, val others: List<Photo>) {
    val photos: List<Photo> get() = listOf(keep) + others

    /** What deleting the [others] frees. */
    val wasted: Long get() = others.sumOf { it.size }
}

/** What the scan found. */
data class DupReport(
    val photos: List<Photo> = emptyList(),
    val groups: List<DupGroup> = emptyList(),
) {
    val totalBytes: Long get() = photos.sumOf { it.size }
    val exact: List<DupGroup> get() = groups.filter { it.kind == DupKind.EXACT }
    val similar: List<DupGroup> get() = groups.filter { it.kind == DupKind.SIMILAR }
    val wastedBytes: Long get() = groups.sumOf { it.wasted }
}

/** An album of the gallery, for the choice "where to look". */
data class Album(val name: String, val count: Int)

object PhotoDupFacts {
    /** Side of the grey picture the fingerprint is made from: 9 × 8 pixels give 8 × 8 = 64 comparisons. */
    const val HASH_W = 9
    const val HASH_H = 8

    /** Two fingerprints at most this many bits apart are the same picture (out of 64). */
    const val SIMILAR_MAX = 6

    /** Photos smaller than this are not compared (icons, tiny thumbnails). */
    const val MIN_SIZE = 8_000L

    /**
     * Grey levels (0..255) of an image of [w] × [h] ARGB [pixels], averaged down to [HASH_W] × [HASH_H] (each
     * cell is the mean of the pixels it covers, so the result does not depend on the image's size).
     */
    fun greyCells(pixels: IntArray, w: Int, h: Int): IntArray {
        require(w > 0 && h > 0 && pixels.size >= w * h)
        val sum = LongArray(HASH_W * HASH_H)
        val count = IntArray(HASH_W * HASH_H)
        for (y in 0 until h) {
            val cy = (y * HASH_H / h).coerceAtMost(HASH_H - 1)
            for (x in 0 until w) {
                val cx = (x * HASH_W / w).coerceAtMost(HASH_W - 1)
                val p = pixels[y * w + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Luma (ITU-R BT.601), in integers.
                sum[cy * HASH_W + cx] += (299L * r + 587L * g + 114L * b) / 1000
                count[cy * HASH_W + cx]++
            }
        }
        return IntArray(sum.size) { if (count[it] == 0) 0 else (sum[it] / count[it]).toInt() }
    }

    /** dHash of [HASH_W] × [HASH_H] grey [cells]: bit set when a cell is brighter than its right neighbour. */
    fun dHash(cells: IntArray): Long {
        require(cells.size == HASH_W * HASH_H)
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_H) {
            for (x in 0 until HASH_W - 1) {
                if (cells[y * HASH_W + x] > cells[y * HASH_W + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Fingerprint of a whole image. */
    fun dHash(pixels: IntArray, w: Int, h: Int): Long = dHash(greyCells(pixels, w, h))

    /** How many bits two fingerprints differ by (0 = same picture, 64 = nothing in common). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** A fingerprint of a flat picture (all cells alike: black screen, white page) says nothing: never compared. */
    fun flat(hash: Long): Boolean = java.lang.Long.bitCount(hash) <= 2 || java.lang.Long.bitCount(hash) >= 62

    /** Groups of photos that could be byte-for-byte copies: same size, at least two. Only these need a content hash. */
    fun sameSize(photos: List<Photo>): List<List<Photo>> =
        photos.filter { it.size >= MIN_SIZE }.groupBy { it.size }.values.filter { it.size > 1 }

    /**
     * The groups: first the exact copies (same [contents] hash and size), then, among what is left (one photo per
     * exact group), the photos whose [prints] are at most [SIMILAR_MAX] apart and whose shapes match. Groups come
     * biggest waste first, exact ones before similar ones.
     */
    fun groups(photos: List<Photo>, contents: Map<Long, String>, prints: Map<Long, Long>): List<DupGroup> {
        val exact = photos.filter { contents[it.id] != null }
            .groupBy { contents.getValue(it.id) to it.size }
            .values
            .filter { it.size > 1 }
            .map { group ->
                val ordered = group.sortedWith(exactOrder)
                DupGroup(DupKind.EXACT, ordered.first(), ordered.drop(1))
            }
        val hidden = exact.flatMap { g -> g.others.map { it.id } }.toSet()
        val candidates = photos.filter { it.id !in hidden && it.size >= MIN_SIZE && prints[it.id]?.let { p -> !flat(p) } == true }
        val similar = clusters(candidates, prints).map { group ->
            val ordered = group.sortedWith(bestOrder)
            DupGroup(DupKind.SIMILAR, ordered.first(), ordered.drop(1))
        }
        return exact.sortedWith(byWaste) + similar.sortedWith(byWaste)
    }

    /** What is checked when the results appear: every extra copy of the exact groups; similar photos are left to the user. */
    fun defaultSelection(groups: List<DupGroup>): Set<Long> =
        groups.filter { it.kind == DupKind.EXACT }.flatMap { g -> g.others.map { it.id } }.toSet()

    /** True when the photos have about the same shape (a crop of a photo is a different picture). */
    fun sameShape(a: Photo, b: Photo): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return true
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        // A photo turned a quarter turn keeps the same picture once Android shows it upright.
        return kotlin.math.abs(ra - rb) / maxOf(ra, rb) < 0.06 || kotlin.math.abs(ra - 1 / rb) / maxOf(ra, 1 / rb) < 0.06
    }

    // Photos close enough link up; every photo of a group is close to the group's first one (no endless chains).
    private fun clusters(photos: List<Photo>, prints: Map<Long, Long>): List<List<Photo>> {
        val sorted = photos.sortedBy { it.taken }
        val used = BooleanArray(sorted.size)
        val out = ArrayList<List<Photo>>()
        for (i in sorted.indices) {
            if (used[i]) continue
            val seed = sorted[i]
            val seedPrint = prints.getValue(seed.id)
            val group = arrayListOf(seed)
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                val other = sorted[j]
                if (distance(seedPrint, prints.getValue(other.id)) <= SIMILAR_MAX && sameShape(seed, other)) {
                    group += other
                    used[j] = true
                }
            }
            if (group.size > 1) {
                used[i] = true
                out += group
            }
        }
        return out
    }

    /** Exact copies: the first saved stays (the original; the later ones are copies), then the shortest name. */
    private val exactOrder = compareBy<Photo>({ if (it.taken > 0) it.taken else Long.MAX_VALUE }, { it.name.length }, { it.id })

    /** Look-alikes: the sharpest stays (most pixels, then the heaviest file, less compressed), then the oldest. */
    private val bestOrder = compareByDescending<Photo> { it.pixels }.thenByDescending { it.size }.thenBy { it.taken }.thenBy { it.id }

    private val byWaste = compareByDescending<DupGroup> { it.wasted }.thenBy { it.keep.id }

    /** Why the photo kept in a group is the one to keep. */
    fun keepReason(group: DupGroup): String = when {
        group.kind == DupKind.EXACT -> "La plus ancienne"
        group.others.any { it.pixels < group.keep.pixels } -> "La plus nette"
        else -> "La plus lourde"
    }

    fun bytes(value: Long): String = Fmt.bytes(value.toDouble())

    fun photos(count: Int): String = if (count > 1) "$count photos" else "$count photo"

    /** "4 032 × 3 024" (empty when unknown). */
    fun dimensions(photo: Photo): String = if (photo.width > 0 && photo.height > 0) "${photo.width} × ${photo.height}" else ""

    /** Main button: "Supprimer 12 photos · 48 Mo". */
    fun deleteLabel(bytes: Long, count: Int): String =
        if (count == 0) "Coche les photos à supprimer" else "Supprimer ${photos(count)} · ${bytes(bytes)}"

    /** Body of the confirmation, when Android will ask once more ([toTrash] = into the gallery's bin). */
    fun confirmText(count: Int, bytes: Long, toTrash: Boolean, keptGroups: Int): String {
        val what = if (count > 1) "Ces $count photos" else "Cette photo"
        val how = if (toTrash) {
            "${if (count > 1) "iront" else "ira"} dans la corbeille d'Android : elle la garde 30 jours, puis l'efface. La place (${bytes(bytes)}) revient alors."
        } else {
            "${if (count > 1) "seront supprimées" else "sera supprimée"} du téléphone pour de bon, et ${bytes(bytes)} libérés tout de suite."
        }
        val keep = if (keptGroups > 0) " Dans chaque groupe, au moins une photo reste." else ""
        return "$what $how Android te demande de confirmer.$keep"
    }

    /** True when the user checked every photo of a group: nothing of that picture would stay. */
    fun wholeGroupChecked(groups: List<DupGroup>, selection: Set<Long>): Boolean =
        groups.any { g -> g.photos.all { it.id in selection } }

    /** Lines of the summary once deleted. */
    fun doneLines(deleted: List<Photo>, failed: Int, toTrash: Boolean, before: Long): List<String> {
        val lines = ArrayList<String>()
        val freed = deleted.sumOf { it.size }
        deleted.groupBy { it.album }.entries.sortedByDescending { e -> e.value.sumOf { it.size } }.take(4).forEach { (album, list) ->
            lines += "$album : ${photos(list.size)}, ${bytes(list.sumOf { it.size })}"
        }
        if (before > 0 && !toTrash) {
            val ratio = freed * 100.0 / before
            val percent = if (freed > 0 && ratio < 0.5) "moins de 1" else Math.round(ratio).toString()
            lines += "La galerie passe de ${bytes(before)} à ${bytes((before - freed).coerceAtLeast(0))} ($percent % de moins)"
        }
        if (toTrash) lines += "Elles sont dans la corbeille d'Android 30 jours, le temps de changer d'avis"
        if (failed > 0) lines += if (failed > 1) "$failed photos n'ont pas pu être supprimées" else "1 photo n'a pas pu être supprimée"
        return lines
    }
}
