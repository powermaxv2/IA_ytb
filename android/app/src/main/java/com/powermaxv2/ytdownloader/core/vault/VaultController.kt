package com.powermaxv2.ytdownloader.core.vault

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.powermaxv2.ytdownloader.core.MediaExporter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Coffre-fort's state (ToolRegistry.VAULT), one per app: the key stays in memory only while the vault is open,
 * and [lock] forgets it (leaving the tool, or Gulp going to the background).
 */
class VaultController private constructor(private val app: Context) {
    val dir = File(app.filesDir, "vault")
    private val store = VaultStore(dir)
    private val prefs = app.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    /** Small pictures of the open vault, by item id. */
    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<String, Bitmap>> = _thumbs.asStateFlow()

    /** What the viewer shows: a picture, or the start of a text. */
    private val _picture = MutableStateFlow<Bitmap?>(null)
    val picture: StateFlow<Bitmap?> = _picture.asStateFlow()
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    private var key: VaultKey? = null
    private var pendingCode: String? = null
    private var originals: List<Uri> = emptyList()
    private var work: Job? = null
    private var ticker: Job? = null

    /** A file picker, the fingerprint or another app is in front on purpose: going to the background must not lock. */
    @Volatile var expectingReturn = false

    private val openDir get() = File(app.cacheDir, "gulp-work/vault-open")
    private val outDir get() = File(app.cacheDir, "gulp-work/vault-out")

    /** Shows the right first screen: the keypad if a vault exists, else the warning. */
    fun refresh() {
        if (key != null) return
        val fingerprint = VaultBiometric.available(app)
        _state.value = VaultUiState(
            phase = if (store.exists) locked() else VaultPhase.Setup(CodeStep.WARNING),
            fingerprintAvailable = fingerprint,
            fingerprintOn = fingerprint && VaultBiometric.enabled(dir),
        )
        tick()
    }

    // MARK: - Code

    fun acceptWarning() = setPhase(VaultPhase.Setup(CodeStep.CODE, changing = isOpen))

    /** Back from a step of choosing a code. */
    fun back() {
        val phase = _state.value.phase as? VaultPhase.Setup ?: return
        pendingCode = null
        when {
            phase.step == CodeStep.CONFIRM -> setPhase(phase.copy(step = CodeStep.CODE, mismatch = false))
            phase.changing -> setPhase(VaultPhase.Open)
            phase.step == CodeStep.CODE -> setPhase(VaultPhase.Setup(CodeStep.WARNING))
        }
    }

    /** A full code typed on the keypad (choosing one, or opening the vault). */
    fun submit(code: String) {
        when (val phase = _state.value.phase) {
            is VaultPhase.Setup -> when (phase.step) {
                CodeStep.WARNING -> Unit
                CodeStep.CODE -> if (VaultCode.isValid(code)) {
                    pendingCode = code
                    setPhase(phase.copy(step = CodeStep.CONFIRM, mismatch = false))
                }
                CodeStep.CONFIRM -> if (code == pendingCode) {
                    pendingCode = null
                    if (phase.changing) changeCode(code) else create(code)
                } else {
                    pendingCode = null
                    setPhase(phase.copy(step = CodeStep.CODE, mismatch = true))
                }
            }
            is VaultPhase.Locked -> if (phase.waitSeconds == 0) unlock(code)
            else -> Unit
        }
    }

    private fun create(code: String) {
        setPhase(VaultPhase.Busy("Création du coffre…"))
        work = scope.launch {
            val made = runCatching { withContext(Dispatchers.Default) { store.create(code) } }
            made.onSuccess { opened(it) }.onFailure { setPhase(VaultPhase.Setup(CodeStep.CODE)) }
        }
    }

    private fun changeCode(code: String) {
        val current = key ?: return
        setPhase(VaultPhase.Busy("Nouveau code…"))
        work = scope.launch {
            runCatching { withContext(Dispatchers.Default) { store.changeCode(current, code) } }
            setPhase(VaultPhase.Open)
            _state.update { it.copy(settingsOpen = false) }
        }
    }

    private fun unlock(code: String) {
        setPhase(VaultPhase.Busy("Ouverture du coffre…"))
        work = scope.launch {
            val result = runCatching { withContext(Dispatchers.Default) { store.unlock(code) } }
            result.onSuccess {
                prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_UNTIL, 0L).apply()
                opened(it)
            }.onFailure { error ->
                val failures = prefs.getInt(KEY_FAILURES, 0) + 1
                val wait = VaultThrottle.waitMillis(failures)
                prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_UNTIL, if (wait > 0) System.currentTimeMillis() + wait else 0L).apply()
                val message = if (error is WrongCodeException) "Code incorrect" else "Le coffre ne s'ouvre pas : ${error.message}"
                setPhase(locked(error = message, shake = failures))
                tick()
            }
        }
    }

    /** Opens with the fingerprint; [context] must be the activity. */
    fun unlockWithFingerprint(context: Context, toast: (String) -> Unit) {
        if (!_state.value.fingerprintOn || _state.value.phase !is VaultPhase.Locked) return
        expectingReturn = true
        VaultBiometric.unlock(context, dir, onFail = { message ->
            expectingReturn = false
            if (message != null) toast(message)
            _state.update { it.copy(fingerprintOn = VaultBiometric.enabled(dir)) }
        }) { raw ->
            expectingReturn = false
            setPhase(VaultPhase.Busy("Ouverture du coffre…"))
            work = scope.launch {
                val result = runCatching { withContext(Dispatchers.Default) { store.unlockRaw(raw) } }
                raw.fill(0)
                result.onSuccess { opened(it) }.onFailure {
                    VaultBiometric.disable(dir)
                    _state.update { s -> s.copy(fingerprintOn = false) }
                    setPhase(locked(error = "L'empreinte ne correspond plus à ce coffre, tape ton code."))
                }
            }
        }
    }

    fun setFingerprint(context: Context, on: Boolean, toast: (String) -> Unit) {
        val current = key ?: return
        if (!on) {
            VaultBiometric.disable(dir)
            _state.update { it.copy(fingerprintOn = false) }
            return
        }
        expectingReturn = true
        VaultBiometric.enable(context, dir, current.export()) { ok, message ->
            expectingReturn = false
            _state.update { it.copy(fingerprintOn = ok) }
            if (ok) toast("Empreinte activée") else if (message != null) toast(message)
        }
    }

    fun forgot(show: Boolean) {
        val phase = _state.value.phase as? VaultPhase.Locked ?: return
        setPhase(phase.copy(forgot = show))
    }

    // MARK: - Open vault

    private suspend fun opened(opened: VaultKey) {
        key = opened
        val items = withContext(Dispatchers.IO) {
            runCatching { store.cleanOrphans(opened) }
            store.items(opened)
        }
        _state.update {
            it.copy(
                phase = VaultPhase.Open,
                items = items,
                selected = emptySet(),
                usedBytes = store.usedBytes(),
                fingerprintOn = it.fingerprintAvailable && VaultBiometric.enabled(dir),
            )
        }
        loadThumbs(items)
    }

    private fun loadThumbs(items: List<VaultItem>) {
        val current = key ?: return
        scope.launch(Dispatchers.IO) {
            items.filter { it.hasThumb && it.id !in _thumbs.value }.forEach { item ->
                if (key !== current) return@launch
                val bytes = runCatching { store.thumb(current, item) }.getOrNull() ?: return@forEach
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
                _thumbs.update { it + (item.id to bitmap) }
            }
        }
    }

    fun filter(kind: VaultKind?) = _state.update { it.copy(filter = if (it.filter == kind) null else kind) }

    fun toggle(item: VaultItem) = _state.update {
        it.copy(selected = if (item.id in it.selected) it.selected - item.id else it.selected + item.id)
    }

    fun selectAll() = _state.update { s ->
        val ids = s.shown.map { it.id }.toSet()
        s.copy(selected = if (s.selected.containsAll(ids)) emptySet() else ids)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet(), confirmDelete = false) }

    fun settings(open: Boolean) = _state.update { it.copy(settingsOpen = open, confirmDestroy = false) }

    fun confirmDelete(show: Boolean) = _state.update { it.copy(confirmDelete = show) }

    fun confirmDestroy(show: Boolean) = _state.update { it.copy(confirmDestroy = show) }

    fun newCode() {
        _state.update { it.copy(settingsOpen = false) }
        setPhase(VaultPhase.Setup(CodeStep.CODE, changing = true))
    }

    fun dismissImport() = _state.update { it.copy(lastImport = null) }

    /** Seals the picked files into the vault. */
    fun import(uris: List<Uri>) {
        val current = key ?: return
        if (uris.isEmpty() || _state.value.busy) return
        work = scope.launch {
            var added = 0
            var failed = 0
            val kept = mutableListOf<Uri>()
            uris.forEachIndexed { i, uri ->
                if (!isActive || key !== current) return@launch
                val meta = withContext(Dispatchers.IO) { describe(uri) }
                _state.update { it.copy(importing = VaultImport(i, uris.size, meta.name, 0f), lastImport = null) }
                val item = withContext(Dispatchers.IO) {
                    runCatching {
                        val thumb = thumbnail(uri, meta.mime)
                        app.contentResolver.openInputStream(uri)!!.use { input ->
                            store.add(current, meta.name, meta.mime, input, thumb) { done ->
                                if (meta.size > 0) {
                                    val f = (done.toFloat() / meta.size).coerceIn(0f, 1f)
                                    _state.update { s -> s.copy(importing = s.importing?.copy(fraction = f)) }
                                }
                            }
                        }
                    }.getOrNull()
                }
                if (item == null) {
                    failed++
                } else {
                    added++
                    kept += uri
                    _state.update { it.copy(items = it.items + item) }
                }
            }
            originals = kept
            _state.update {
                it.copy(
                    importing = null,
                    lastImport = VaultImportDone(added, failed, originals = kept.size),
                    usedBytes = store.usedBytes(),
                )
            }
            loadThumbs(_state.value.items)
        }
    }

    /** Deletes the originals of the last import from the phone, where the app that gave them allows it. */
    fun deleteOriginals() {
        val uris = originals
        if (uris.isEmpty()) return
        originals = emptyList()
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                uris.count { uri ->
                    runCatching {
                        if (DocumentsContract.isDocumentUri(app, uri)) {
                            DocumentsContract.deleteDocument(app.contentResolver, uri)
                        } else {
                            app.contentResolver.delete(uri, null, null) > 0
                        }
                    }.getOrDefault(false)
                }
            }
            _state.update { it.copy(lastImport = it.lastImport?.copy(deleted = deleted)) }
        }
    }

    /** Takes the selected files out of the vault for good. */
    fun deleteSelected() {
        val current = key ?: return
        val ids = _state.value.selected
        scope.launch {
            withContext(Dispatchers.IO) { store.remove(current, ids) }
            _thumbs.update { it - ids }
            _state.update { s ->
                s.copy(items = s.items.filterNot { it.id in ids }, selected = emptySet(), confirmDelete = false, usedBytes = store.usedBytes())
            }
        }
    }

    /** Writes the selected files (or [only]) back to the phone's folders (Pictures/Gulp, Movies/Gulp, Download/Gulp…). */
    fun export(only: VaultItem? = null, toast: (String, Boolean) -> Unit) {
        val current = key ?: return
        val items = only?.let(::listOf) ?: _state.value.items.filter { it.id in _state.value.selected }
        if (items.isEmpty() || _state.value.busy) return
        work = scope.launch {
            var saved = 0
            items.forEachIndexed { i, item ->
                _state.update { it.copy(exporting = i to items.size) }
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(outDir, item.id).apply { mkdirs() }
                        val file = File(dir, item.name)
                        try {
                            store.open(current, item).use { input -> file.outputStream().use { input.copyTo(it) } }
                            MediaExporter.export(app, file)
                        } finally {
                            dir.deleteRecursively()
                        }
                    }.isSuccess
                }
                if (ok) saved++
            }
            _state.update { it.copy(exporting = null, selected = emptySet()) }
            if (saved == items.size) {
                toast(if (saved == 1) "Sorti du coffre, dans tes dossiers Gulp" else "$saved fichiers sortis du coffre", true)
            } else {
                toast("${items.size - saved} fichier(s) n'ont pas pu sortir", false)
            }
        }
    }

    /** Opens [item] in the viewer: pictures and texts are read in memory, nothing is written. */
    fun view(item: VaultItem?) {
        _picture.value = null
        _text.value = null
        _state.update { it.copy(viewing = item, viewError = null) }
        val current = key ?: return
        item ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (item.kind) {
                        VaultKind.PHOTO -> {
                            val bytes = store.readAll(current, item, MAX_PICTURE)
                            _picture.value = decodeSampled(bytes, 2560) ?: error("photo illisible")
                        }
                        VaultKind.TEXT -> {
                            val bytes = store.open(current, item).use { it.readNBytesCompat(MAX_TEXT) }
                            _text.value = String(bytes, Charsets.UTF_8)
                        }
                        else -> Unit
                    }
                }
            }
            result.onFailure { e ->
                _state.update { it.copy(viewError = if (e is VaultCorruptException && item.size > MAX_PICTURE) "Trop grande pour l'aperçu : ouvre-la avec une app." else "Ce fichier ne s'affiche pas ici.") }
            }
        }
    }

    /**
     * Opens [item] in another app: a plain copy is made in Gulp's private work folder for it, and deleted when the
     * vault locks (and at every launch of Gulp).
     */
    fun openOutside(context: Context, item: VaultItem, toast: (String, Boolean) -> Unit) {
        val current = key ?: return
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(openDir, item.id).apply { mkdirs() }
                    File(dir, item.name).also { out -> store.open(current, item).use { input -> out.outputStream().use { input.copyTo(it) } } }
                }.getOrNull()
            } ?: return@launch toast("Ce fichier ne s'ouvre pas", false)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            expectingReturn = true
            runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }.onFailure {
                expectingReturn = false
                toast("Aucune app ne peut ouvrir ce fichier", false)
            }
        }
    }

    /** Closes the vault: the key is wiped, the pictures dropped, the copies made for other apps deleted. */
    fun lock() {
        work?.cancel()
        key?.wipe()
        key = null
        pendingCode = null
        originals = emptyList()
        _thumbs.value = emptyMap()
        _picture.value = null
        _text.value = null
        scope.launch(Dispatchers.IO) {
            openDir.deleteRecursively()
            outDir.deleteRecursively()
        }
        refresh()
    }

    /** Deletes the whole vault (code forgotten, or asked from the settings). Nothing can bring the files back. */
    fun destroy() {
        work?.cancel()
        key?.wipe()
        key = null
        VaultBiometric.disable(dir)
        store.destroy()
        prefs.edit().clear().apply()
        _thumbs.value = emptyMap()
        refresh()
    }

    // MARK: - Helpers

    private val isOpen get() = key != null

    private fun setPhase(phase: VaultPhase) = _state.update { it.copy(phase = phase) }

    private fun locked(error: String? = null, shake: Int = 0): VaultPhase.Locked =
        VaultPhase.Locked(error = error, waitSeconds = waitSeconds(), shake = shake)

    private fun waitSeconds(): Int {
        val left = prefs.getLong(KEY_UNTIL, 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    /** Counts the waiting time down on the keypad. */
    private fun tick() {
        if (waitSeconds() == 0) return
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val phase = _state.value.phase as? VaultPhase.Locked ?: break
                val left = waitSeconds()
                setPhase(phase.copy(waitSeconds = left, error = if (left == 0) null else phase.error))
                if (left == 0) break
                delay(1_000)
            }
        }
    }

    private data class Meta(val name: String, val mime: String, val size: Long)

    private fun describe(uri: Uri): Meta {
        var name: String? = null
        var size = -1L
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val finalName = name ?: uri.lastPathSegment ?: "fichier"
        val mime = app.contentResolver.getType(uri) ?: MediaExporter.mimeOf(finalName)
        return Meta(finalName, mime, size)
    }

    /** A small JPEG of a photo or of a video's first frame, for the grid; null for anything else. */
    private fun thumbnail(uri: Uri, mime: String): ByteArray? = runCatching {
        val bitmap: Bitmap? = when {
            mime.startsWith("image/") -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, THUMB * 2) }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
            mime.startsWith("video/") -> MediaMetadataRetriever().run {
                try {
                    setDataSource(app, uri)
                    if (Build.VERSION.SDK_INT >= 27) getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB * 2, THUMB * 2) else frameAtTime
                } finally {
                    release()
                }
            }
            else -> null
        }
        bitmap?.let { compressThumb(it) }
    }.getOrNull()

    private fun compressThumb(source: Bitmap): ByteArray {
        val scale = THUMB.toFloat() / minOf(source.width, source.height).coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }

    private fun decodeSampled(bytes: ByteArray, max: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, max) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "until"
        private const val THUMB = 256
        private const val MAX_PICTURE = 60L * 1024 * 1024
        private const val MAX_TEXT = 200 * 1024

        @Volatile private var instance: VaultController? = null

        fun get(context: Context): VaultController =
            instance ?: synchronized(this) { instance ?: VaultController(context.applicationContext).also { instance = it } }
    }
}
