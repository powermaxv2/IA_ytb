package com.powermaxv2.ytdownloader.core.status

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** One status of the folder. [key] is its file name, stable while WhatsApp keeps it. */
data class StatusItem(
    val uri: Uri,
    val name: String,
    val kind: StatusKind,
    val size: Long,
    val modifiedAt: Long,
    val durationMs: Long? = null,
) {
    val key: String get() = name
}

data class StatusKept(val item: StatusItem, val saved: SavedFile)

sealed interface StatusPhase {
    /** No folder yet (or Android forgot it): the "how it works" screen and its button. */
    data object NeedsFolder : StatusPhase

    data object Loading : StatusPhase

    /** The user picked another folder than WhatsApp's statuses. */
    data class WrongFolder(val message: String) : StatusPhase

    /** The grid (it may be empty: no status seen in WhatsApp lately). */
    data object Ready : StatusPhase

    data class Working(val done: Int, val total: Int) : StatusPhase

    data class Done(val kept: List<StatusKept>) : StatusPhase

    data class Failed(val message: String, val debug: String? = null) : StatusPhase
}

data class StatusUiState(
    val phase: StatusPhase = StatusPhase.NeedsFolder,
    val app: StatusApp = StatusApp.WHATSAPP,
    val items: List<StatusItem> = emptyList(),
    val filter: StatusFilter = StatusFilter.ALL,
    /** Names of the statuses already kept (they show a check, "Tout enregistrer" skips them). */
    val saved: Set<String> = emptySet(),
    /** Names being kept one by one, from their own button. */
    val saving: Set<String> = emptySet(),
    /** When the list was read: the rings count the hours left from it. */
    val now: Long = 0L,
    /** A short message for a toast (a single save that failed); the screen clears it once shown. */
    val message: String? = null,
) {
    val shown: List<StatusItem> get() = items.filter { filter.accepts(it.kind) }
    val photos: Int get() = items.count { it.kind == StatusKind.PHOTO }
    val videos: Int get() = items.count { it.kind == StatusKind.VIDEO }

    /** What "Tout enregistrer" keeps: the shown statuses not kept yet. */
    val toKeep: List<StatusItem> get() = shown.filter { it.key !in saved && it.key !in saving }
}

/**
 * The Statuts tool, held by the App. WhatsApp keeps the statuses its user has seen in a hidden ".Statuses" folder,
 * for about a day. Since Android 11 only the system folder picker can give an app access to it: the user picks it
 * once, Android remembers the permission ([Context.getContentResolver] persisted URI permissions), and the folder
 * is read again each time the tool opens. Kept statuses are copies in Pictures/Gulp or Movies/Gulp; the folder is
 * only read, never changed.
 */
class StatusController(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("statuses", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        StatusUiState(app = runCatching { StatusApp.valueOf(prefs.getString(KEY_APP, null) ?: "") }.getOrDefault(StatusApp.WHATSAPP)),
    )
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var workJob: Job? = null

    val busy: Boolean get() = _state.value.phase is StatusPhase.Working

    /** Where the system picker should open for [app]: its statuses folder (Android goes as near as it can). */
    fun initialUri(app: StatusApp = _state.value.app): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, initialDocumentId(app))

    /**
     * Reads the folder again (new statuses, expired ones gone). [quiet] = the screen came back to the front: only
     * the grid (or the first screen, when a folder is known) is refreshed, a result or an error stays on screen.
     */
    fun refresh(quiet: Boolean = false) {
        if (busy) return
        if (quiet && _state.value.phase.let { it !is StatusPhase.Ready && it !is StatusPhase.NeedsFolder }) return
        val app = _state.value.app
        val folder = folderOf(app)
        if (folder == null) {
            _state.update { it.copy(phase = StatusPhase.NeedsFolder, items = emptyList()) }
            return
        }
        listJob?.cancel()
        if (!quiet || _state.value.phase !is StatusPhase.Ready) _state.update { it.copy(phase = StatusPhase.Loading) }
        listJob = scope.launch(Dispatchers.IO) {
            // Nothing changes under a running copy, nor after the user switched to the other app meanwhile.
            fun settle(change: (StatusUiState) -> StatusUiState) =
                _state.update { if (it.phase is StatusPhase.Working || it.app != app) it else change(it) }
            try {
                val items = list(folder.first, folder.second)
                val saved = keptNames(items)
                settle { it.copy(phase = StatusPhase.Ready, items = items, saved = saved, now = System.currentTimeMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                forgetFolder(app)
                settle { it.copy(phase = StatusPhase.NeedsFolder, items = emptyList(), message = "Android a retiré l'accès au dossier : autorise-le à nouveau.") }
            } catch (e: FileNotFoundException) {
                forgetFolder(app)
                settle { it.copy(phase = StatusPhase.NeedsFolder, items = emptyList(), message = "Le dossier des statuts a disparu : autorise-le à nouveau.") }
            } catch (e: Exception) {
                settle { it.copy(phase = StatusPhase.Failed("Impossible de lire les statuts.", e.toString())) }
            }
        }
    }

    fun setApp(app: StatusApp) {
        if (busy || app == _state.value.app) return
        prefs.edit().putString(KEY_APP, app.name).apply()
        _state.update { it.copy(app = app, items = emptyList(), filter = StatusFilter.ALL) }
        refresh()
    }

    fun setFilter(filter: StatusFilter) = _state.update { it.copy(filter = filter) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** The user picked [tree] in the system picker: it must be (or hold) a ".Statuses" folder of WhatsApp. */
    fun folderPicked(tree: Uri) {
        if (busy) return
        listJob?.cancel()
        _state.update { it.copy(phase = StatusPhase.Loading) }
        listJob = scope.launch(Dispatchers.IO) {
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            val statuses = treeId?.let { statusesFolderOf(it) ?: search(tree, it, statusesSearchDepth(it)) }
            if (treeId == null || statuses == null) {
                _state.update {
                    it.copy(
                        phase = StatusPhase.WrongFolder(
                            "Ce n'est pas le dossier des statuts. Laisse Android ouvrir le dossier proposé (.Statuses) " +
                                "et touche « Utiliser ce dossier », puis « Autoriser ».",
                        ),
                    )
                }
                return@launch
            }
            runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val app = StatusApp.of(statuses)
            prefs.edit()
                .putString(KEY_APP, app.name)
                .putString(KEY_TREE + app.name, tree.toString())
                .putString(KEY_FOLDER + app.name, statuses)
                .apply()
            _state.update { it.copy(app = app) }
            refresh()
        }
    }

    /** "Changer de dossier": forgets the folder of the current app (Android drops the permission too). */
    fun changeFolder() {
        if (busy) return
        forgetFolder(_state.value.app)
        _state.update { it.copy(phase = StatusPhase.NeedsFolder, items = emptyList()) }
    }

    fun backToGrid() {
        if (busy) return
        refresh(quiet = false)
    }

    fun cancel() {
        workJob?.cancel()
    }

    /** Keeps one status from its own button, without leaving the grid. */
    fun keepOne(item: StatusItem) {
        val current = _state.value
        if (busy || item.key in current.saving) return
        _state.update { it.copy(saving = it.saving + item.key) }
        scope.launch(Dispatchers.IO) {
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                _state.update { it.copy(saving = it.saving - item.key, message = "Impossible de préparer la copie.") }
                return@launch
            }
            try {
                copy(item, dir)
                _state.update { it.copy(saving = it.saving - item.key, saved = it.saved + item.key) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(saving = it.saving - item.key, message = failure(e)) }
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    /** "Tout enregistrer": keeps every shown status not kept yet, then shows them. */
    fun keepAll() {
        val current = _state.value
        val items = current.toKeep
        if (busy || items.isEmpty()) return
        _state.update { it.copy(phase = StatusPhase.Working(0, items.size)) }
        workJob = scope.launch(Dispatchers.IO) {
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                _state.update { it.copy(phase = StatusPhase.Failed(e.message ?: "Impossible de préparer la copie.")) }
                return@launch
            }
            val kept = ArrayList<StatusKept>()
            try {
                items.forEachIndexed { index, item ->
                    coroutineContext.ensureActive()
                    kept += StatusKept(item, copy(item, dir))
                    _state.update { it.copy(phase = StatusPhase.Working(index + 1, items.size), saved = it.saved + item.key) }
                }
                _state.update { it.copy(phase = StatusPhase.Done(kept)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = if (kept.isEmpty()) StatusPhase.Ready else StatusPhase.Done(kept)) }
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(phase = StatusPhase.Failed(failure(e), e.toString())) }
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private fun failure(e: Throwable): String = when (e) {
        is SecurityException -> "Android a refusé l'accès au statut : autorise le dossier à nouveau."
        is FileNotFoundException -> "Ce statut vient de disparaître de WhatsApp."
        is IOException -> "Copie impossible : vérifie l'espace libre et réessaie."
        is OutOfMemoryError -> "Pas assez de mémoire pour ce statut."
        else -> "La copie du statut a échoué."
    }

    /** Copies [item] as a new file of the gallery, named by [keptName]; remembers it as kept. */
    private suspend fun copy(item: StatusItem, dir: File): SavedFile {
        val job = coroutineContext
        val file = FileIntake.uniqueFile(dir, keptName(item.name, item.modifiedAt))
        val input = context.contentResolver.openInputStream(item.uri) ?: throw FileNotFoundException(item.name)
        input.use { source -> file.outputStream().use { FileIntake.copyCapped(source, it, MAX_BYTES) { job.ensureActive() } } }
        val saved = MediaExporter.export(context, file)
        file.delete()
        remember(item.key)
        return saved
    }

    // MARK: - Folder

    /** The tree the user granted for [app] and the document id of its ".Statuses" folder, if Android still allows it. */
    private fun folderOf(app: StatusApp): Pair<Uri, String>? {
        val tree = prefs.getString(KEY_TREE + app.name, null)?.let(Uri::parse) ?: return null
        val folder = prefs.getString(KEY_FOLDER + app.name, null) ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        return if (granted) tree to folder else null
    }

    private fun forgetFolder(app: StatusApp) {
        prefs.getString(KEY_TREE + app.name, null)?.let { tree ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.edit().remove(KEY_TREE + app.name).remove(KEY_FOLDER + app.name).apply()
    }

    /** Looks for ".Statuses" under the picked folder, [depth] levels down at most. */
    private fun search(tree: Uri, documentId: String, depth: Int): String? {
        if (depth <= 0) return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val folders = ArrayList<String>()
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) continue
                val id = c.getString(0) ?: continue
                if (c.getString(1) == STATUSES) return id
                folders += id
            }
        }
        return folders.firstNotNullOfOrNull { search(tree, it, depth - 1) }
    }

    /** The statuses of the folder, newest first. */
    private suspend fun list(tree: Uri, folder: String): List<StatusItem> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        val items = ArrayList<StatusItem>()
        val cursor = context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        ) ?: throw FileNotFoundException(folder)
        cursor.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val kind = statusKind(name, c.getString(2)) ?: continue
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if (size <= 0L) continue // still being downloaded by WhatsApp
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                items += StatusItem(uri, name, kind, size, if (c.isNull(4)) 0L else c.getLong(4))
            }
        }
        return items
            .sortedByDescending { it.modifiedAt }
            .map { if (it.kind == StatusKind.VIDEO) it.copy(durationMs = duration(it.uri)) else it }
    }

    private fun duration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    // MARK: - Kept statuses

    /** Names kept before that are still in the folder (the others expired: forgotten so the list stays small). */
    private fun keptNames(items: List<StatusItem>): Set<String> {
        val present = items.map { it.key }.toSet()
        val kept = prefs.getStringSet(KEY_SAVED, emptySet()).orEmpty().filterTo(HashSet()) { it in present }
        prefs.edit().putStringSet(KEY_SAVED, kept).apply()
        return kept
    }

    @Synchronized
    private fun remember(key: String) {
        val kept = HashSet(prefs.getStringSet(KEY_SAVED, emptySet()).orEmpty())
        kept += key
        prefs.edit().putStringSet(KEY_SAVED, kept).apply()
    }

    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        private const val KEY_APP = "app"
        private const val KEY_TREE = "tree_"
        private const val KEY_FOLDER = "folder_"
        private const val KEY_SAVED = "saved"
        private const val MAX_BYTES: Long = 512L * 1024 * 1024
    }
}
