package com.powermaxv2.ytdownloader.core.cleaner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
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
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface CleanerPhase {
    /** No scan yet: the folder may or may not be chosen ([CleanerUiState.folder]). */
    data object Idle : CleanerPhase

    /** Walking the folder ([hashed] / [toHash] = files compared by content, once the walk is over). */
    data class Scanning(val files: Int, val bytes: Long, val hashed: Int = 0, val toHash: Int = 0) : CleanerPhase

    data object Ready : CleanerPhase

    data class Deleting(val done: Int, val total: Int) : CleanerPhase

    /** [before] = size of the media before deleting. */
    data class Done(val deleted: List<MediaFile>, val failed: Int, val before: Long) : CleanerPhase

    data class Failed(val message: String) : CleanerPhase
}

/** The three lists of the results. */
enum class CleanerTab(val label: String) { DUPLICATES("Doublons"), BIG("Gros fichiers"), TYPES("Par type") }

data class CleanerUiState(
    val phase: CleanerPhase = CleanerPhase.Idle,
    /** The chosen folder, as shown ("WhatsApp › Media"); null = not chosen yet. */
    val folder: String? = null,
    /** True when the chosen folder does not look like WhatsApp's. */
    val folderOdd: Boolean = false,
    val report: CleanerReport = CleanerReport(),
    val selection: Set<String> = emptySet(),
    val tab: CleanerTab = CleanerTab.DUPLICATES,
    /** The "are you sure" card is open. */
    val confirming: Boolean = false,
) {
    val selected: List<MediaFile> get() = report.files.filter { it.id in selection }
    val selectedBytes: Long get() = selected.sumOf { it.size }
}

/**
 * The Nettoyeur WhatsApp's state and work, held by the App. The user grants Gulp one folder once (Storage Access
 * Framework, kept across launches); the scan lists its files, compares same-size files by content (SHA-256 of
 * their start and end, then of the whole file), and deletes only what the user checked, after a confirmation.
 * Nothing leaves the phone.
 */
class CleanerController(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("cleaner", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        savedTree()?.takeIf { hasFolder() }?.let { CleanerUiState(folder = folderLabel(it), folderOdd = !looksLikeWhatsApp(it)) } ?: CleanerUiState(),
    )
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Content fingerprints of the scan, kept to rebuild the duplicate groups after a deletion. */
    private var fingerprints: Map<String, String> = emptyMap()

    private val busy: Boolean get() = _state.value.phase.let { it is CleanerPhase.Scanning || it is CleanerPhase.Deleting }

    /** Where the folder picker opens: WhatsApp's media folder (Android 11+ keeps it under Android/media). */
    fun initialUri(): Uri {
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Android/media/com.whatsapp/WhatsApp/Media" else "WhatsApp/Media"
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:$path")
    }

    /** The user picked [tree] in the folder picker: keep the access across launches, then scan it. */
    fun chooseFolder(tree: Uri) {
        if (busy) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        savedTree()?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs.edit().putString(KEY_TREE, tree.toString()).apply()
        _state.update { it.copy(folder = folderLabel(tree), folderOdd = !looksLikeWhatsApp(tree)) }
        scan()
    }

    /** True when a folder is chosen and Android still grants it. */
    fun hasFolder(): Boolean {
        val tree = savedTree() ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }
    }

    fun scan() {
        if (busy) return
        val tree = savedTree() ?: return
        job?.cancel()
        _state.update { it.copy(phase = CleanerPhase.Scanning(0, 0), confirming = false) }
        job = scope.launch(Dispatchers.IO) {
            val next = try {
                val files = walk(tree)
                val prints = fingerprint(tree, files)
                fingerprints = prints
                val duplicates = CleanerFacts.duplicates(files, prints)
                CleanerReport(files, duplicates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _state.update { it.copy(phase = CleanerPhase.Failed("Android ne donne plus accès à ce dossier : choisis-le à nouveau.")) }
                return@launch
            } catch (e: Exception) {
                _state.update { it.copy(phase = CleanerPhase.Failed(e.message ?: "Impossible de lire ce dossier.")) }
                return@launch
            }
            _state.update {
                it.copy(
                    phase = CleanerPhase.Ready,
                    report = next,
                    selection = CleanerFacts.defaultSelection(next.duplicates),
                    tab = if (next.duplicates.isEmpty()) CleanerTab.BIG else CleanerTab.DUPLICATES,
                )
            }
        }
    }

    fun cancel() {
        if (_state.value.phase !is CleanerPhase.Scanning) return
        job?.cancel()
        _state.update { it.copy(phase = CleanerPhase.Idle) }
    }

    fun setTab(tab: CleanerTab) = _state.update { it.copy(tab = tab) }

    fun toggle(file: MediaFile) = _state.update {
        if (it.phase != CleanerPhase.Ready) it else it.copy(selection = if (file.id in it.selection) it.selection - file.id else it.selection + file.id)
    }

    /** Checks (or unchecks, when all of them already are) [files]. */
    fun toggleAll(files: List<MediaFile>) = _state.update { state ->
        if (state.phase != CleanerPhase.Ready || files.isEmpty()) return@update state
        val ids = files.map { it.id }
        state.copy(selection = if (state.selection.containsAll(ids)) state.selection - ids.toSet() else state.selection + ids)
    }

    fun askConfirm() = _state.update { if (it.phase == CleanerPhase.Ready && it.selection.isNotEmpty()) it.copy(confirming = true) else it }

    fun dismissConfirm() = _state.update { it.copy(confirming = false) }

    /** Deletes the checked files (after the confirmation). */
    fun delete() {
        val start = _state.value
        val tree = savedTree()
        if (busy || start.phase != CleanerPhase.Ready || tree == null) return
        val targets = start.selected
        if (targets.isEmpty()) return
        val before = start.report.totalBytes
        _state.update { it.copy(phase = CleanerPhase.Deleting(0, targets.size), confirming = false) }
        // Not cancellable: a deletion half done is still reported exactly.
        job = scope.launch(Dispatchers.IO) {
            val deleted = ArrayList<MediaFile>()
            var failed = 0
            targets.forEachIndexed { index, file ->
                val ok = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, file.id))
                }.getOrDefault(false)
                if (ok) deleted += file else failed++
                _state.update { it.copy(phase = CleanerPhase.Deleting(index + 1, targets.size)) }
            }
            val gone = deleted.map { it.id }.toSet()
            val files = start.report.files.filterNot { it.id in gone }
            fingerprints = fingerprints - gone
            val report = CleanerReport(files, CleanerFacts.duplicates(files, fingerprints))
            _state.update { it.copy(phase = CleanerPhase.Done(deleted, failed, before), report = report, selection = emptySet()) }
        }
    }

    /** Back to the results after a deletion (what is left, nothing checked). */
    fun backToResults() = _state.update {
        if (it.phase is CleanerPhase.Done) it.copy(phase = CleanerPhase.Ready, selection = CleanerFacts.defaultSelection(it.report.duplicates)) else it
    }

    fun reset() {
        if (busy) return
        job?.cancel()
        _state.update { CleanerUiState(folder = it.folder, folderOdd = it.folderOdd) }
    }

    /** Small picture of a photo or a video of the folder (null when Android has none). */
    suspend fun thumbnail(file: MediaFile): Bitmap? {
        val tree = savedTree() ?: return null
        return runCatching {
            DocumentsContract.getDocumentThumbnail(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, file.id), Point(176, 176), null)
        }.getOrNull()
    }

    // MARK: - Walk and fingerprints

    private suspend fun walk(tree: Uri): List<MediaFile> {
        val resolver = context.contentResolver
        val found = ArrayList<MediaFile>()
        var bytes = 0L
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(DocumentsContract.getTreeDocumentId(tree) to "")
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parent, path) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!CleanerFacts.skippedFolder(name)) stack.addLast(id to if (path.isEmpty()) name else "$path/$name")
                        continue
                    }
                    if (CleanerFacts.ignored(name)) continue
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val modified = if (c.isNull(4)) 0L else c.getLong(4)
                    found += MediaFile(id, name, size, modified, path)
                    bytes += size
                }
            }
            _state.update { it.copy(phase = CleanerPhase.Scanning(found.size, bytes)) }
        }
        return found
    }

    /** Fingerprints of the files that share their size with another: start + end first, the whole file for the ones still alike. */
    private suspend fun fingerprint(tree: Uri, files: List<MediaFile>): Map<String, String> {
        val groups = CleanerFacts.sameSize(files)
        val total = groups.sumOf { it.size }
        var hashed = 0
        val scanning = _state.value.phase as? CleanerPhase.Scanning ?: CleanerPhase.Scanning(files.size, files.sumOf { it.size })
        fun progress() = _state.update { it.copy(phase = scanning.copy(hashed = hashed, toHash = total)) }
        progress()
        val result = HashMap<String, String>()
        for (group in groups) {
            val quick = group.mapNotNull { file ->
                coroutineContext.ensureActive()
                val print = runCatching { hash(tree, file, whole = false) }.getOrNull()
                hashed++
                progress()
                print?.let { file to it }
            }
            quick.groupBy { it.second }.values.filter { it.size > 1 }.forEach { alike ->
                alike.forEach { (file, print) ->
                    coroutineContext.ensureActive()
                    // A small file was read whole already; a big one is compared whole only now that its ends match.
                    val full = if (file.size <= 2 * EDGE) print else runCatching { hash(tree, file, whole = true) }.getOrNull()
                    full?.let { result[file.id] = it }
                }
            }
        }
        return result
    }

    private suspend fun hash(tree: Uri, file: MediaFile, whole: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id)
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val buffer = ByteBuffer.allocate(64 * 1024)
                fun read(from: Long, length: Long) {
                    channel.position(from)
                    var left = length
                    while (left > 0) {
                        buffer.clear()
                        if (left < buffer.capacity()) buffer.limit(left.toInt())
                        val n = channel.read(buffer)
                        if (n <= 0) break
                        digest.update(buffer.array(), 0, n)
                        left -= n
                    }
                }
                if (whole || file.size <= 2 * EDGE) {
                    var position = 0L
                    while (position < file.size) {
                        coroutineContext.ensureActive()
                        val step = minOf(8L * 1024 * 1024, file.size - position)
                        read(position, step)
                        position += step
                    }
                } else {
                    read(0, EDGE)
                    read(file.size - EDGE, EDGE)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // MARK: - Folder

    private fun savedTree(): Uri? = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    private companion object {
        const val KEY_TREE = "tree"
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

        /** Bytes read at each end of a file for the first comparison. */
        const val EDGE = 64L * 1024

        /** "primary:Android/media/com.whatsapp/WhatsApp/Media" → "WhatsApp › Media". */
        fun folderLabel(tree: Uri): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrDefault(tree.lastPathSegment.orEmpty())
            val parts = id.substringAfter(':').split('/').filter { it.isNotBlank() && it != "Android" && it != "media" && !it.startsWith("com.") }
            return parts.takeLast(2).joinToString(" › ").ifEmpty { "Stockage du téléphone" }
        }

        fun looksLikeWhatsApp(tree: Uri): Boolean =
            runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrDefault("").contains("whatsapp", ignoreCase = true)
    }
}
