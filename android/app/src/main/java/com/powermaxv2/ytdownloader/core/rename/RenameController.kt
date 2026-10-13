package com.powermaxv2.ytdownloader.core.rename

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.MimeTypes
import com.powermaxv2.ytdownloader.core.files.SafeNames
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
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A picked file: its document [uri] and what the rule needs ([source].key is the URI). */
data class RenameFile(val uri: Uri, val source: RenameSource, val mime: String)

/** A renamed file ([key] = its key before), to put its old name back. */
data class UndoStep(val key: String, val uri: Uri, val name: String, val oldName: String)

data class RenameOutcome(
    /** Files renamed in place (their new name as the phone wrote it). */
    val renamed: List<UndoStep> = emptyList(),
    /** Renamed copies, for files whose folder does not allow renaming. */
    val copies: List<SavedFile> = emptyList(),
    /** Names of the files that could not be renamed. */
    val failed: List<String> = emptyList(),
    /** True once "Annuler" put the old names back. */
    val undone: Boolean = false,
) {
    val total: Int get() = renamed.size + copies.size
}

sealed interface RenamePhase {
    data object Idle : RenamePhase

    data object Reading : RenamePhase

    data class Unsupported(val message: String) : RenamePhase

    data object Ready : RenamePhase

    data class Working(val done: Int, val total: Int, val undoing: Boolean = false) : RenamePhase

    data class Done(val outcome: RenameOutcome) : RenamePhase

    data class Failed(val message: String, val debug: String? = null) : RenamePhase
}

data class RenameUiState(
    val phase: RenamePhase = RenamePhase.Idle,
    val files: List<RenameFile> = emptyList(),
    val rule: RenameRule = RenameRule(),
    /** Folder mode: the folder's name and every name in it (files left out of the list still hold theirs). */
    val folder: String? = null,
    val folderNames: Set<String> = emptySet(),
) {
    val taken: Set<String> get() = if (folderNames.isEmpty()) emptySet() else folderNames - files.map { it.source.name }.toSet()

    fun preview(): RenamePreview = RenamePlan.preview(files.map { it.source }, rule, taken)
}

/**
 * The Renommeur's state and work, held by the App. Files come from the system picker (OpenMultipleDocuments) or a
 * whole folder (OpenDocumentTree); each is renamed in place with [DocumentsContract.renameDocument]. A file whose
 * folder refuses renaming (photos picked from a gallery app, some cloud drives) gets a renamed copy in the phone's
 * Gulp folders instead, and the original is left alone. The last renaming can be undone.
 */
class RenameController(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(RenameUiState())
    val state: StateFlow<RenameUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var workJob: Job? = null

    val busy: Boolean get() = _state.value.phase is RenamePhase.Working

    fun pickFiles(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        val keep = if (add && _state.value.folder == null) _state.value.files else emptyList()
        read { describeFiles(keep, uris) }
        return true
    }

    fun pickFolder(tree: Uri): Boolean {
        if (busy) return false
        read { describeFolder(tree) }
        return true
    }

    private fun read(block: suspend () -> RenameUiState) {
        readJob?.cancel()
        _state.update { it.copy(phase = RenamePhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                RenameUiState(phase = RenamePhase.Unsupported("Android a refusé l'accès : choisis les fichiers à nouveau."))
            } catch (e: Exception) {
                RenameUiState(phase = RenamePhase.Unsupported(e.message ?: "Impossible de lire ces fichiers."))
            }
            _state.update { current -> if (current.phase is RenamePhase.Working) current else next.copy(rule = current.rule) }
        }
    }

    private suspend fun describeFiles(keep: List<RenameFile>, uris: List<Uri>): RenameUiState {
        val known = keep.map { it.uri }.toSet()
        val fresh = uris.distinct().filter { it !in known }
        if (keep.size + fresh.size > MAX_FILES) {
            return RenameUiState(phase = RenamePhase.Unsupported("$MAX_FILES fichiers au plus à la fois."), files = keep)
        }
        val files = keep + fresh.map { uri ->
            coroutineContext.ensureActive()
            describe(uri)
        }
        if (files.isEmpty()) return RenameUiState(phase = RenamePhase.Unsupported("Aucun fichier."))
        return RenameUiState(RenamePhase.Ready, files)
    }

    private suspend fun describeFolder(tree: Uri): RenameUiState {
        FileIntake.requireContent(tree)
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val folderName = runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, treeId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: treeId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Dossier" }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        val names = HashSet<String>()
        val found = ArrayList<Triple<Uri, String, Pair<String, Pair<Long?, Int>>>>()
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val name = c.getString(1) ?: continue
                names += name
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val modified = if (c.isNull(3)) null else c.getLong(3).takeIf { it > 0 }
                found += Triple(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), name, mime to (modified to c.getInt(4)))
            }
        } ?: throw IOException("Impossible de lire ce dossier.")
        if (found.isEmpty()) return RenameUiState(phase = RenamePhase.Unsupported("« $folderName » ne contient aucun fichier."))
        if (found.size > MAX_FILES) return RenameUiState(phase = RenamePhase.Unsupported("« $folderName » a ${found.size} fichiers : $MAX_FILES au plus à la fois."))
        val files = found.sortedWith(compareBy(NaturalOrder) { it.second }).map { (uri, name, info) ->
            coroutineContext.ensureActive()
            val (mime, rest) = info
            val (modified, flags) = rest
            val resolved = MimeTypes.resolve(mime, name)
            val source = RenameSource(
                key = uri.toString(),
                name = name,
                takenAt = photoDate(uri, resolved),
                modifiedAt = modified,
                renamable = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
            )
            RenameFile(uri, source, resolved)
        }
        return RenameUiState(RenamePhase.Ready, files, folder = folderName, folderNames = names)
    }

    /** Name, dates and whether its folder lets Gulp rename it, for one picked file. Blocking. */
    private fun describe(uri: Uri): RenameFile {
        FileIntake.requireContent(uri)
        val resolver = context.contentResolver
        val isDocument = runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)
        var name: String? = null
        var modified: Long? = null
        var flags = 0
        val columns = if (isDocument) {
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)
        } else {
            arrayOf(OpenableColumns.DISPLAY_NAME)
        }
        runCatching {
            resolver.query(uri, columns, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (isDocument) {
                        if (!c.isNull(1)) modified = c.getLong(1).takeIf { it > 0 }
                        if (!c.isNull(2)) flags = c.getInt(2)
                    }
                }
            }
        }
        val display = name?.takeIf { it.isNotBlank() } ?: SafeNames.sanitize(uri.lastPathSegment?.substringAfterLast('/').orEmpty())
        val mime = MimeTypes.resolve(runCatching { resolver.getType(uri) }.getOrNull(), display)
        val source = RenameSource(
            key = uri.toString(),
            name = display,
            takenAt = photoDate(uri, mime),
            modifiedAt = modified,
            renamable = isDocument && flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
        )
        return RenameFile(uri, source, mime)
    }

    /** The shooting date of a photo (EXIF), null for anything else. */
    private fun photoDate(uri: Uri, mime: String): Long? {
        if (!mime.startsWith("image/")) return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                RenamePlan.parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME))
            }
        }.getOrNull()
    }

    fun setRule(rule: RenameRule) {
        _state.update { if (it.phase is RenamePhase.Working) it else it.copy(rule = rule) }
    }

    fun remove(file: RenameFile) {
        _state.update {
            if (it.phase != RenamePhase.Ready) return@update it
            val left = it.files - file
            if (left.isEmpty()) RenameUiState(rule = it.rule) else it.copy(files = left)
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { RenameUiState(rule = it.rule) }
    }

    fun backToEdit() {
        _state.update { if (it.files.isNotEmpty() && !busy) it.copy(phase = RenamePhase.Ready) else it }
    }

    /** Renames every file the preview marks as ready; the others are left alone. */
    fun start() {
        val current = _state.value
        if (current.files.isEmpty() || busy || current.phase is RenamePhase.Reading) return
        val rows = current.preview().ready
        if (rows.isEmpty()) return
        val byKey = current.files.associateBy { it.source.key }
        _state.update { it.copy(phase = RenamePhase.Working(0, rows.size)) }
        workJob = scope.launch(Dispatchers.IO) {
            try {
                val outcome = apply(rows, byKey)
                // The list now shows the new names, so a second rule starts from them.
                val renamed = outcome.renamed.associateBy { it.key }
                _state.update { state ->
                    val files = state.files.map { f ->
                        val step = renamed[f.source.key] ?: return@map f
                        f.copy(uri = step.uri, source = f.source.copy(key = step.uri.toString(), name = step.name))
                    }
                    val names = if (state.folderNames.isEmpty()) state.folderNames else state.folderNames - outcome.renamed.map { it.oldName }.toSet() + outcome.renamed.map { it.name }
                    state.copy(phase = RenamePhase.Done(outcome), files = files, folderNames = names)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(phase = RenamePhase.Failed("Le renommage a échoué.", e.toString())) }
            }
        }
    }

    private suspend fun apply(rows: List<RenameRow>, byKey: Map<String, RenameFile>): RenameOutcome {
        val inPlace = rows.filter { it.source.renamable }
        val copies = rows.filterNot { it.source.renamable }
        val detour = RenamePlan.needsDetour(inPlace)
        val uris = HashMap<String, Uri>()
        val failed = ArrayList<String>()
        val stuck = HashSet<String>()
        var done = 0
        fun step() {
            done++
            _state.update { s -> (s.phase as? RenamePhase.Working)?.let { s.copy(phase = it.copy(done = done.coerceAtMost(it.total))) } ?: s }
        }
        // 1. Files whose new name is still held by a file of the batch first take a temporary name.
        for (row in inPlace) {
            val file = byKey[row.source.key] ?: continue
            uris[row.source.key] = file.uri
            if (row.source.key !in detour) continue
            coroutineContext.ensureActive()
            val temp = ".gulp-${UUID.randomUUID().toString().take(8)}-${row.source.name}".take(120)
            val moved = rename(file.uri, temp)
            if (moved == null) {
                failed += row.source.name
                stuck += row.source.key
            } else {
                uris[row.source.key] = moved
            }
        }
        // 2. Every file takes its new name.
        val renamed = ArrayList<UndoStep>()
        for (row in inPlace) {
            coroutineContext.ensureActive()
            if (row.source.key in stuck) {
                step()
                continue
            }
            val uri = uris[row.source.key] ?: continue
            val moved = rename(uri, row.newName)
            if (moved == null) {
                failed += row.source.name
                // A file stuck on its temporary name gets its old name back.
                if (row.source.key in detour) rename(uri, row.source.name)
            } else {
                renamed += UndoStep(row.source.key, moved, nameOf(moved) ?: row.newName, row.source.name)
            }
            step()
        }
        // 3. The others get a renamed copy.
        val saved = ArrayList<SavedFile>()
        if (copies.isNotEmpty()) {
            val dir = WorkDir.create(context)
            try {
                for (row in copies) {
                    coroutineContext.ensureActive()
                    val file = byKey[row.source.key] ?: continue
                    val copy = runCatching { copyAs(file, row.newName, dir) }.getOrNull()
                    if (copy == null) failed += row.source.name else saved += copy
                    step()
                }
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
        return RenameOutcome(renamed, saved, failed)
    }

    /** Puts back the names the last renaming changed (renamed copies stay). */
    fun undo() {
        val current = _state.value
        val outcome = (current.phase as? RenamePhase.Done)?.outcome ?: return
        if (outcome.undone || outcome.renamed.isEmpty() || busy) return
        val steps = outcome.renamed
        _state.update { it.copy(phase = RenamePhase.Working(0, steps.size, undoing = true)) }
        workJob = scope.launch(Dispatchers.IO) {
            try {
                // Same two passes: every file first takes a temporary name, then its old one.
                val temps = steps.map { s -> s to (rename(s.uri, ".gulp-${UUID.randomUUID().toString().take(8)}-${s.oldName}".take(120)) ?: s.uri) }
                val back = HashMap<String, Pair<Uri, String>>()
                temps.forEachIndexed { index, (s, uri) ->
                    coroutineContext.ensureActive()
                    val moved = rename(uri, s.oldName)
                    back[s.uri.toString()] = (moved ?: uri) to (moved?.let(::nameOf) ?: s.oldName)
                    _state.update { st -> st.copy(phase = RenamePhase.Working(index + 1, steps.size, undoing = true)) }
                }
                _state.update { state ->
                    val files = state.files.map { f ->
                        val b = back[f.source.key] ?: return@map f
                        f.copy(uri = b.first, source = f.source.copy(key = b.first.toString(), name = b.second))
                    }
                    val names = if (state.folderNames.isEmpty()) state.folderNames else state.folderNames - steps.map { it.name }.toSet() + back.values.map { it.second }
                    state.copy(phase = RenamePhase.Done(outcome.copy(undone = true)), files = files, folderNames = names)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(phase = RenamePhase.Failed("Impossible de remettre les anciens noms.", e.toString())) }
            }
        }
    }

    /** The document's new URI, or null when the provider refused. */
    private fun rename(uri: Uri, name: String): Uri? = try {
        DocumentsContract.renameDocument(context.contentResolver, uri, name)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun nameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private suspend fun copyAs(file: RenameFile, newName: String, dir: File): SavedFile {
        val job = coroutineContext
        val out = FileIntake.uniqueFile(dir, SafeNames.sanitize(newName))
        val input = context.contentResolver.openInputStream(file.uri) ?: throw IOException("Impossible de lire ${file.source.name}.")
        input.use { i -> out.outputStream().use { o -> FileIntake.copyCapped(i, o, FileIntake.MEDIA_MAX_BYTES) { job.ensureActive() } } }
        return MediaExporter.export(context, out).also { out.delete() }
    }

    companion object {
        const val MAX_FILES = 500
    }
}
