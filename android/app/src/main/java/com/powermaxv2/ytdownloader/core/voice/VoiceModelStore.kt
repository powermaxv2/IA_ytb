package com.powermaxv2.ytdownloader.core.voice

import android.content.Context
import com.powermaxv2.ytdownloader.core.Http
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class VoiceModelError(message: String, val debug: String? = null) : Exception(message)

/**
 * The French model in the app's private files (`files/voice/vosk-model-small-fr-0.22`). Installed once: downloaded
 * into the cache, refused unless its size and SHA-256 are the published ones, unpacked into a temporary folder
 * (only the model's own files, never outside it), checked, then renamed into place in one step.
 */
class VoiceModelStore(private val context: Context) {
    private val root: File get() = File(context.filesDir, "voice")
    private val modelDir: File get() = File(root, VoiceModelFiles.ID)

    fun status(): VoiceModelStatus = if (installed()) VoiceModelStatus.READY else VoiceModelStatus.MISSING

    private fun installed(): Boolean = VoiceModelFiles.REQUIRED.all { File(modelDir, it).isFile }

    fun delete() {
        root.deleteRecursively()
    }

    /** The model folder, installing it first if needed; [report] gets each step (progress 0..1 or -1). */
    suspend fun ensure(report: (VoiceStep, Float, String?) -> Unit): File {
        if (installed()) return modelDir
        // Leftovers of an interrupted install (another model version, a half-unpacked folder).
        root.deleteRecursively()
        root.mkdirs()
        if (root.usableSpace in 1 until VoiceModelFiles.NEEDED_FREE_BYTES) {
            throw VoiceModelError("Il faut environ ${VoiceModelFiles.NEEDED_FREE_BYTES / 1_000_000} Mo libres pour installer le modèle français. Libère de la place et réessaie.")
        }
        val zip = File(context.cacheDir, "${VoiceModelFiles.ID}.zip")
        try {
            report(VoiceStep.DOWNLOAD, 0f, "0 / ${VoiceModelFiles.downloadLabel}")
            val job = coroutineContext
            var tooBig = false
            val sha = try {
                Http.download(VoiceModelFiles.URL, zip, shouldStop = { !job.isActive || tooBig }) { done, _ ->
                    if (done > VoiceModelFiles.ZIP_BYTES) tooBig = true
                    report(VoiceStep.DOWNLOAD, (done.toFloat() / VoiceModelFiles.ZIP_BYTES).coerceIn(0f, 1f), "${done / 1_000_000} / ${VoiceModelFiles.downloadLabel}")
                }
            } catch (e: InterruptedException) {
                coroutineContext.ensureActive()
                throw VoiceModelError("Le fichier du modèle n'est pas celui attendu : il n'a pas été installé.")
            } catch (e: IOException) {
                throw VoiceModelError(
                    "Le modèle français (${VoiceModelFiles.downloadLabel}) n'a pas pu être téléchargé. Il faut Internet la première fois seulement : vérifie la connexion et réessaie.",
                    e.toString(),
                )
            }
            if (zip.length() != VoiceModelFiles.ZIP_BYTES || !sha.equals(VoiceModelFiles.ZIP_SHA256, ignoreCase = true)) {
                throw VoiceModelError("Le fichier du modèle n'est pas celui attendu : il n'a pas été installé.", "size=${zip.length()} sha256=$sha")
            }
            report(VoiceStep.UNPACK, -1f, null)
            unpack(zip)
        } finally {
            zip.delete()
        }
        if (!installed()) throw VoiceModelError("Le modèle français est incomplet. Réessaie.")
        return modelDir
    }

    private suspend fun unpack(zip: File) {
        val temp = File(root, "${VoiceModelFiles.ID}.part")
        temp.deleteRecursively()
        temp.mkdirs()
        val written = HashSet<String>()
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                coroutineContext.ensureActive()
                val path = VoiceModelFiles.target(entry.name) ?: continue
                val out = File(temp, path)
                if (!out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) continue
                out.parentFile?.mkdirs()
                var total = 0L
                out.outputStream().use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > VoiceModelFiles.MAX_ENTRY_BYTES) throw VoiceModelError("Le fichier du modèle n'est pas celui attendu.")
                        output.write(buffer, 0, n)
                    }
                }
                written += path
            }
        }
        if (!VoiceModelFiles.complete(written)) {
            temp.deleteRecursively()
            throw VoiceModelError("Le modèle français est incomplet. Réessaie.", written.sorted().joinToString())
        }
        if (!temp.renameTo(modelDir)) {
            temp.deleteRecursively()
            throw VoiceModelError("Le modèle français n'a pas pu être installé.")
        }
    }
}
