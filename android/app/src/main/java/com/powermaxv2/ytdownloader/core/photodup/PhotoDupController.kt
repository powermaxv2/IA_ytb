package com.powermaxv2.ytdownloader.core.photodup

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** What Android lets Gulp see of the gallery. */
enum class GalleryAccess {
    /** Not asked yet, or refused. */
    NONE,

    /** Android 14+: only the photos the user picked in Android's window. */
    PARTIAL,
    FULL,
}

sealed interface DupPhase {
    data object Idle : DupPhase

    /** [step]: 0 = listing, 1 = fingerprints of each photo, 2 = byte-for-byte comparison of same-size photos. */
    data class Scanning(val step: Int, val done: Int, val total: Int, val photos: Int) : DupPhase

    data object Ready : DupPhase

    /** Android's own confirmation is on screen (Android 11+), or the photos are being deleted one by one. */
    data class Deleting(val count: Int) : DupPhase

    data class Done(val deleted: List<Photo>, val failed: Int, val toTrash: Boolean, val before: Long) : DupPhase

    data class Failed(val message: String) : DupPhase
}

data class DupUiState(
    val phase: DupPhase = DupPhase.Idle,
    val access: GalleryAccess = GalleryAccess.NONE,
    /** Albums of the gallery, most photos first (empty until access is given). */
    val albums: List<Album> = emptyList(),
    /** Where to look: null = the whole gallery. */
    val album: String? = null,
    val report: DupReport = DupReport(),
    val selection: Set<Long> = emptySet(),
    val kind: DupKind = DupKind.EXACT,
    /** The "are you sure" card is open. */
    val confirming: Boolean = false,
    /** Into Android's bin (30 days) rather than deleted for good. */
    val toTrash: Boolean = false,
) {
    val selected: List<Photo> get() = report.photos.filter { it.id in selection }
    val selectedBytes: Long get() = selected.sumOf { it.size }
    val galleryCount: Int get() = albums.sumOf { it.count }
}

/**
 * Doublons photos' state and work, held by the App. Reads the gallery through MediaStore (photos only), finds the
 * byte-for-byte copies (same size, then SHA-256) and the look-alikes (dHash of Android's thumbnail, kept in a small
 * cache so that the next scan is quick), and deletes what the user checked through Android's own confirmation
 * (Android 11+), into its bin or for good. Nothing leaves the phone.
 */
class PhotoDupController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(DupUiState())
    val state: StateFlow<DupUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var contents: Map<Long, String> = emptyMap()
    private var prints: Map<Long, Long> = emptyMap()

    /** The photos handed to Android's confirmation, waiting for its answer. */
    private var pending: List<Photo> = emptyList()

    private val busy: Boolean get() = _state.value.phase.let { it is DupPhase.Scanning || it is DupPhase.Deleting }

    /** The screen checked the permissions (on open, and after Android's window): list the albums when allowed. */
    fun setAccess(access: GalleryAccess) {
        val changed = access != _state.value.access
        _state.update { it.copy(access = access) }
        if (access != GalleryAccess.NONE && (changed || _state.value.albums.isEmpty()) && !busy) {
            scope.launch(Dispatchers.IO) {
                val albums = runCatching { albums() }.getOrDefault(emptyList())
                _state.update { s -> s.copy(albums = albums, album = s.album?.takeIf { a -> albums.any { it.name == a } }) }
            }
        }
    }

    fun setAlbum(album: String?) = _state.update { if (busy) it else it.copy(album = album) }

    fun scan() {
        if (busy || _state.value.access == GalleryAccess.NONE) return
        val album = _state.value.album
        job?.cancel()
        _state.update { it.copy(phase = DupPhase.Scanning(0, 0, 0, 0), confirming = false) }
        job = scope.launch(Dispatchers.IO) {
            val report = try {
                val photos = list(album)
                prints = fingerprints(photos)
                contents = contentHashes(photos)
                DupReport(photos, PhotoDupFacts.groups(photos, contents, prints))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _state.update { it.copy(phase = DupPhase.Failed("Android ne donne plus accès aux photos : autorise-le à nouveau."), access = GalleryAccess.NONE) }
                return@launch
            } catch (e: Exception) {
                _state.update { it.copy(phase = DupPhase.Failed(e.message ?: "Impossible de lire la galerie.")) }
                return@launch
            }
            _state.update {
                it.copy(
                    phase = DupPhase.Ready,
                    report = report,
                    selection = PhotoDupFacts.defaultSelection(report.groups),
                    kind = if (report.exact.isEmpty() && report.similar.isNotEmpty()) DupKind.SIMILAR else DupKind.EXACT,
                )
            }
        }
    }

    fun cancel() {
        if (_state.value.phase !is DupPhase.Scanning) return
        job?.cancel()
        _state.update { it.copy(phase = DupPhase.Idle) }
    }

    fun setKind(kind: DupKind) = _state.update { it.copy(kind = kind) }

    fun toggle(photo: Photo) = _state.update {
        if (it.phase != DupPhase.Ready) it else it.copy(selection = if (photo.id in it.selection) it.selection - photo.id else it.selection + photo.id)
    }

    /** "Garder la meilleure": checks every photo of [group] but the one kept (or unchecks them when they all are). */
    fun keepBest(group: DupGroup) = _state.update { s ->
        if (s.phase != DupPhase.Ready) return@update s
        val ids = group.others.map { it.id }
        val all = s.selection.containsAll(ids) && group.keep.id !in s.selection
        s.copy(selection = if (all) s.selection - ids.toSet() else s.selection - group.keep.id + ids)
    }

    /** The same for every group of [kind]. */
    fun keepBestEverywhere(kind: DupKind) = _state.update { s ->
        if (s.phase != DupPhase.Ready) return@update s
        val groups = s.report.groups.filter { it.kind == kind }
        val ids = groups.flatMap { g -> g.others.map { it.id } }
        val keeps = groups.map { it.keep.id }.toSet()
        val all = s.selection.containsAll(ids) && keeps.none { it in s.selection }
        s.copy(selection = if (all) s.selection - ids.toSet() else s.selection - keeps + ids)
    }

    fun setTrash(toTrash: Boolean) = _state.update { it.copy(toTrash = toTrash) }

    fun askConfirm() = _state.update { if (it.phase == DupPhase.Ready && it.selection.isNotEmpty()) it.copy(confirming = true) else it }

    fun dismissConfirm() = _state.update { it.copy(confirming = false) }

    /**
     * Android 11+: the request the screen hands to Android (its own "Supprimer ces photos ?" window); the answer
     * comes back to [onSystemAnswer]. Null on older Android, where [deleteDirectly] does the work.
     */
    fun deleteRequest(): IntentSender? {
        val s = _state.value
        if (busy || s.phase != DupPhase.Ready || s.selection.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        pending = s.selected
        val uris = pending.map(::uriOf)
        val request = runCatching {
            if (s.toTrash) MediaStore.createTrashRequest(context.contentResolver, uris, true) else MediaStore.createDeleteRequest(context.contentResolver, uris)
        }.getOrNull() ?: return null
        _state.update { it.copy(phase = DupPhase.Deleting(pending.size), confirming = false) }
        return request.intentSender
    }

    /** Android's window closed: [accepted] or not. What really went is checked in MediaStore. */
    fun onSystemAnswer(accepted: Boolean) {
        val targets = pending
        pending = emptyList()
        if (_state.value.phase !is DupPhase.Deleting) return
        if (!accepted || targets.isEmpty()) {
            _state.update { it.copy(phase = DupPhase.Ready) }
            return
        }
        scope.launch(Dispatchers.IO) {
            val still = runCatching { existing(targets.map { it.id }) }.getOrDefault(emptySet())
            finish(targets.filterNot { it.id in still }, failed = targets.count { it.id in still })
        }
    }

    /** Android 10 and older: deletes the checked photos one by one (no system window there). */
    fun deleteDirectly() {
        val s = _state.value
        if (busy || s.phase != DupPhase.Ready || s.selection.isEmpty()) return
        val targets = s.selected
        _state.update { it.copy(phase = DupPhase.Deleting(targets.size), confirming = false, toTrash = false) }
        job = scope.launch(Dispatchers.IO) {
            val deleted = ArrayList<Photo>()
            var failed = 0
            targets.forEach { photo ->
                // Android 10 only lets an app delete the photos it made itself (RecoverableSecurityException).
                val ok = runCatching { context.contentResolver.delete(uriOf(photo), null, null) > 0 }.getOrDefault(false)
                if (ok) deleted += photo else failed++
            }
            finish(deleted, failed)
        }
    }

    private fun finish(deleted: List<Photo>, failed: Int) {
        val start = _state.value
        val gone = deleted.map { it.id }.toSet()
        val photos = start.report.photos.filterNot { it.id in gone }
        contents = contents - gone
        prints = prints - gone
        val report = DupReport(photos, PhotoDupFacts.groups(photos, contents, prints))
        _state.update {
            it.copy(phase = DupPhase.Done(deleted, failed, it.toTrash, start.report.totalBytes), report = report, selection = emptySet())
        }
    }

    /** Back to the results after a deletion (what is left, exact copies checked again). */
    fun backToResults() = _state.update {
        if (it.phase is DupPhase.Done) it.copy(phase = DupPhase.Ready, selection = PhotoDupFacts.defaultSelection(it.report.groups)) else it
    }

    fun reset() {
        if (busy) return
        job?.cancel()
        _state.update { DupUiState(access = it.access, albums = it.albums, album = it.album, toTrash = it.toTrash) }
    }

    /** Small picture of a photo (null when Android has none). */
    suspend fun thumbnail(photo: Photo): Bitmap? = runCatching { loadThumb(photo, THUMB) }.getOrNull()

    // MARK: - Gallery

    private fun uriOf(photo: Photo): Uri = ContentUris.withAppendedId(COLLECTION, photo.id)

    private fun albums(): List<Album> {
        val counts = HashMap<String, Int>()
        context.contentResolver.query(COLLECTION, arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts.map { Album(it.key, it.value) }.sortedWith(compareByDescending<Album> { it.count }.thenBy { it.name })
    }

    private suspend fun list(album: String?): List<Photo> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val where = album?.let { "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?" }
        val args = album?.let { arrayOf(it) }
        val found = ArrayList<Photo>()
        context.contentResolver.query(COLLECTION, projection, where, args, "${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val size = if (c.isNull(2)) 0L else c.getLong(2)
                val taken = if (!c.isNull(3) && c.getLong(3) > 0) c.getLong(3) else if (c.isNull(4)) 0L else c.getLong(4) * 1000
                found += Photo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "photo",
                    size = size,
                    taken = taken,
                    width = if (c.isNull(5)) 0 else c.getInt(5),
                    height = if (c.isNull(6)) 0 else c.getInt(6),
                    album = c.getString(7) ?: "Galerie",
                )
                if (found.size % 200 == 0) _state.update { it.copy(phase = DupPhase.Scanning(0, 0, 0, found.size)) }
            }
        }
        return found
    }

    private fun existing(ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val left = HashSet<Long>()
        ids.chunked(400).forEach { chunk ->
            val where = "${MediaStore.Images.Media._ID} IN (${chunk.joinToString(",")})"
            context.contentResolver.query(COLLECTION, arrayOf(MediaStore.Images.Media._ID), where, null, null)?.use { c ->
                while (c.moveToNext()) left += c.getLong(0)
            }
        }
        return left
    }

    // MARK: - Fingerprints

    /** dHash of every photo, from Android's thumbnail; reused from the cache when the photo has not changed. */
    private suspend fun fingerprints(photos: List<Photo>): Map<Long, Long> {
        val cache = readCache()
        val result = HashMap<Long, Long>()
        val total = photos.size
        photos.forEachIndexed { i, photo ->
            coroutineContext.ensureActive()
            val key = cacheKey(photo)
            val print = cache[key] ?: runCatching { loadThumb(photo, HASH_SIDE)?.let(::hashOf) }.getOrNull()?.also { cache[key] = it }
            if (print != null) result[photo.id] = print
            if (i % 8 == 0 || i == total - 1) _state.update { it.copy(phase = DupPhase.Scanning(1, i + 1, total, total)) }
        }
        writeCache(cache)
        return result
    }

    private fun hashOf(bitmap: Bitmap): Long {
        val small = if (bitmap.width > HASH_SIDE * 2 || bitmap.height > HASH_SIDE * 2) Bitmap.createScaledBitmap(bitmap, HASH_SIDE, HASH_SIDE, true) else bitmap
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        return PhotoDupFacts.dHash(pixels, small.width, small.height)
    }

    private fun loadThumb(photo: Photo, side: Int): Bitmap? {
        val uri = uriOf(photo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return context.contentResolver.loadThumbnail(uri, Size(side, side), null)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** SHA-256 of the photos that share their size with another one. */
    private suspend fun contentHashes(photos: List<Photo>): Map<Long, String> {
        val groups = PhotoDupFacts.sameSize(photos)
        val total = groups.sumOf { it.size }
        var done = 0
        val result = HashMap<Long, String>()
        _state.update { it.copy(phase = DupPhase.Scanning(2, 0, total, photos.size)) }
        for (group in groups) {
            for (photo in group) {
                coroutineContext.ensureActive()
                runCatching { sha256(photo) }.getOrNull()?.let { result[photo.id] = it }
                done++
                _state.update { it.copy(phase = DupPhase.Scanning(2, done, total, photos.size)) }
            }
        }
        return result
    }

    private suspend fun sha256(photo: Photo): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uriOf(photo))!!.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // One line per photo: "id:size:taken<TAB>hash" (hex). A photo edited in place changes size and is hashed again.
    private val cacheFile: File get() = File(context.filesDir, "photodup/prints.tsv")

    private fun cacheKey(photo: Photo) = "${photo.id}:${photo.size}:${photo.taken}"

    private fun readCache(): HashMap<String, Long> {
        val map = HashMap<String, Long>()
        runCatching {
            cacheFile.takeIf { it.exists() }?.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) line.substring(tab + 1).toULongOrNull(16)?.let { map[line.substring(0, tab)] = it.toLong() }
            }
        }
        return map
    }

    private fun writeCache(map: Map<String, Long>) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.bufferedWriter().use { w -> map.forEach { (k, v) -> w.write(k); w.write("\t"); w.write(v.toULong().toString(16)); w.newLine() } }
        }
    }

    private companion object {
        val COLLECTION: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        /** Side of the thumbnail the fingerprint is made from. */
        const val HASH_SIDE = 64

        /** Side of the pictures of the results. */
        const val THUMB = 240
    }
}
