package com.powermaxv2.ytdownloader.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.powermaxv2.ytdownloader.core.files.MimeTypes
import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class SavedFile(val uri: Uri, val mime: String, val name: String, val size: Long, val location: String)

/**
 * Copies a finished file into the phone's public folders so it shows up in the Gallery / Music apps:
 * videos → Movies/Gulp, music → Music/Gulp, photos → Pictures/Gulp, documents and anything else → Download/Gulp.
 */
object MediaExporter {
    const val FOLDER = "Gulp"

    private enum class Kind(val directory: String) {
        VIDEO(Environment.DIRECTORY_MOVIES),
        AUDIO(Environment.DIRECTORY_MUSIC),
        IMAGE(Environment.DIRECTORY_PICTURES),

        /** Unknown type (".unknown_video"…): the media collections refuse it, the Download folder takes anything. */
        OTHER(Environment.DIRECTORY_DOWNLOADS),
    }

    /**
     * Type of a file to save, from its name: Android's table first, then [MimeTypes.byExtension] (video, audio,
     * images incl. gif / bmp / tiff / avi, and documents: pdf, html, json, csv, md, txt, zip). Anything that is
     * not video / audio / image lands in Download/Gulp.
     */
    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: MimeTypes.byExtension[ext] ?: MimeTypes.OCTET_STREAM
    }

    private fun kindOf(mime: String): Kind = when {
        mime.startsWith("video/") -> Kind.VIDEO
        mime.startsWith("audio/") -> Kind.AUDIO
        mime.startsWith("image/") -> Kind.IMAGE
        else -> Kind.OTHER
    }

    /** Characters refused by the shared storage file system (plus bidi controls and leading dots). */
    internal fun safeName(name: String): String = SafeNames.sanitize(name, fallback = "media")

    /** "video.mp4" + 2 → "video (2).mp4"; "archive" + 2 → "archive (2)" (never a trailing dot). */
    internal fun numberedName(name: String, index: Int): String {
        val base = SafeNames.baseOf(name)
        val ext = name.substring(base.length)
        return "$base ($index)$ext"
    }

    suspend fun export(context: Context, file: File): SavedFile = withContext(Dispatchers.IO) {
        val name = safeName(file.name)
        val mime = mimeOf(name)
        val kind = kindOf(mime)
        val location = "${kind.directory}/$FOLDER"
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportWithMediaStore(context, file, name, mime, kind, location)
        } else {
            exportLegacy(context, file, name, mime, kind)
        }
        SavedFile(uri, mime, name, file.length(), location)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportWithMediaStore(context: Context, file: File, name: String, mime: String, kind: Kind, location: String): Uri {
        val resolver = context.contentResolver
        val collection = when (kind) {
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, location)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Impossible de créer le fichier dans $location")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { it.copyTo(output, 256 * 1024) }
            } ?: throw IOException("Impossible d'écrire dans $location")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun exportLegacy(context: Context, file: File, name: String, mime: String, kind: Kind): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(kind.directory), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Autorisez l'accès au stockage pour enregistrer les fichiers.")
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, numberedName(name, n))
            n++
        }
        file.copyTo(target)
        val scanned = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Uri?> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime)) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            }
        }
        return scanned ?: Uri.fromFile(target)
    }
}
