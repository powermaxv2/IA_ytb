package com.powermaxv2.ytdownloader.core.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.powermaxv2.ytdownloader.core.Fmt
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

// Files the user hands to a tool (picked with the system picker, or shared to Gulp). The rules:
// only content:// URIs, names sanitized before they touch a path, copies capped in size and kept in a private
// work dir (cacheDir/gulp-work/<random>/) that is deleted after use and wiped at every app start.

/** A file the user chose, as the content provider describes it (nothing is read or copied yet). */
data class LocalFile(
    val uri: Uri,
    /** Display name, already sanitized ([SafeNames.sanitize]). */
    val name: String,
    /** Size in bytes when the provider tells it. */
    val size: Long?,
    /** MIME type from the provider, else from the extension, else "application/octet-stream". */
    val mime: String,
)

/** A file is over the size a tool accepts ([limit] bytes); the message is French and shown as is. */
class FileTooLarge(val limit: Long) : IOException("Fichier trop volumineux : ${Fmt.bytes(limit.toDouble())} au maximum.")

/** The URI is not a content:// one (file:// and anything else are refused). */
class UnsupportedSource : IOException("Ce fichier ne peut pas être ouvert : choisissez-le depuis le sélecteur de fichiers.")

/** Private scratch folders of the tools: `cacheDir/gulp-work/<random>/`. */
object WorkDir {
    const val ROOT = "gulp-work"

    fun root(context: Context): File = File(context.cacheDir, ROOT)

    /** A new, empty folder of our own; delete it with [delete] once the job is done. */
    fun create(context: Context): File {
        val dir = File(root(context), UUID.randomUUID().toString())
        if (!dir.mkdirs()) throw IOException("Impossible de préparer le dossier de travail.")
        return dir
    }

    /** Deletes a folder made by [create] (anything outside the work root is left alone). */
    fun delete(context: Context, dir: File) {
        if (isInside(root(context), dir)) dir.deleteRecursively()
    }

    /** Removes every leftover work folder (crash, force-stop): called from `App.onCreate`. */
    fun wipeAll(context: Context) {
        root(context).deleteRecursively()
    }

    /** Whether [file] is strictly inside [root] (not [root] itself), after resolving `..` and symlinks. */
    fun isInside(root: File, file: File): Boolean {
        val r = root.canonicalFile
        var f: File? = file.canonicalFile.parentFile
        while (f != null) {
            if (f == r) return true
            f = f.parentFile
        }
        return false
    }
}

/** Reads what the user handed over: description, first bytes, capped text, capped copy into a [WorkDir]. */
object FileIntake {
    /** Default cap of [copyToWork] (audio / video): 2 Gio. Tools pass smaller caps for images or documents. */
    const val MEDIA_MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Most text read into memory by [readText]: 1 Mio. */
    const val TEXT_MAX_BYTES: Int = 1024 * 1024

    /** Throws [UnsupportedSource] unless [uri] is a content:// URI. */
    fun requireContent(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw UnsupportedSource()
    }

    /** Name, size and type of [uri] from the provider (OpenableColumns); the name is sanitized. Blocking: call off the main thread. */
    fun describe(context: Context, uri: Uri): LocalFile {
        requireContent(uri)
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s).takeIf { it >= 0 }
                }
            }
        }
        val safe = SafeNames.sanitize(name ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty())
        val mime = MimeTypes.resolve(runCatching { resolver.getType(uri) }.getOrNull(), safe)
        return LocalFile(uri, safe, size, mime)
    }

    /** Up to [max] first bytes of [uri] (for [FileKind.detect]); empty for an empty file. */
    fun readHead(context: Context, uri: Uri, max: Int = FileKind.HEAD_SIZE): ByteArray {
        requireContent(uri)
        return open(context, uri).use { input -> readUpTo(input, max) }
    }

    /**
     * The file as text, at most [maxBytes] bytes (UTF-8, invalid bytes replaced). Returns the text and whether
     * it was cut.
     */
    fun readText(context: Context, uri: Uri, maxBytes: Int = TEXT_MAX_BYTES): Pair<String, Boolean> {
        requireContent(uri)
        return open(context, uri).use { input ->
            val bytes = readUpTo(input, maxBytes + 1)
            val cut = bytes.size > maxBytes
            String(if (cut) bytes.copyOf(maxBytes) else bytes, Charsets.UTF_8) to cut
        }
    }

    /**
     * Copies [uri] into [dir] (a fresh [WorkDir] by default) under its sanitized name and returns the copy.
     * Throws [FileTooLarge] as soon as more than [maxBytes] are read (the partial copy is deleted) or when the
     * provider already reports a bigger size. Blocking: call off the main thread.
     */
    fun copyToWork(context: Context, uri: Uri, maxBytes: Long = MEDIA_MAX_BYTES, dir: File? = null): File {
        val info = describe(context, uri)
        if (info.size != null && info.size > maxBytes) throw FileTooLarge(maxBytes)
        val folder = dir ?: WorkDir.create(context)
        val target = File(folder, info.name)
        try {
            open(context, uri).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw FileTooLarge(maxBytes)
                        output.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            if (dir == null) WorkDir.delete(context, folder)
            throw e
        }
        return target
    }

    private fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Impossible de lire ce fichier.")

    private fun readUpTo(input: InputStream, max: Int): ByteArray {
        val out = ByteArray(max)
        var read = 0
        while (read < max) {
            val n = input.read(out, read, max - read)
            if (n < 0) break
            read += n
        }
        return if (read == max) out else out.copyOf(read)
    }
}
