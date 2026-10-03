package com.powermaxv2.ytdownloader.core.files

import java.util.Locale

/**
 * MIME types by extension, for when Android's MimeTypeMap (or the app that shared a file) does not know one.
 * Pure (no Android types) so it is unit tested; [com.powermaxv2.ytdownloader.core.MediaExporter] and
 * [FileIntake] use it as their fallback.
 */
object MimeTypes {
    const val OCTET_STREAM = "application/octet-stream"

    val byExtension: Map<String, String> = mapOf(
        // Video
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "3gp" to "video/3gpp",
        // Audio
        "m4a" to "audio/mp4", "mp3" to "audio/mpeg", "opus" to "audio/ogg", "ogg" to "audio/ogg",
        "flac" to "audio/flac", "wav" to "audio/wav", "aac" to "audio/aac",
        // Images
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif", "gif" to "image/gif",
        "bmp" to "image/bmp", "tif" to "image/tiff", "tiff" to "image/tiff",
        // Documents (saved under Download/Gulp)
        "pdf" to "application/pdf", "html" to "text/html", "htm" to "text/html", "json" to "application/json",
        "csv" to "text/csv", "md" to "text/markdown", "markdown" to "text/markdown", "txt" to "text/plain",
        "zip" to "application/zip",
    )

    /** MIME type of a file name from its extension, or null when unknown. */
    fun fromName(name: String): String? = byExtension[SafeNames.extensionOf(name)]

    /** A usable type: [mime] unless it is missing or the generic octet-stream, then the extension's, then octet-stream. */
    fun resolve(mime: String?, name: String): String {
        val given = mime?.trim()?.lowercase(Locale.ROOT)?.substringBefore(';')?.takeIf { it.isNotEmpty() && it != OCTET_STREAM && '/' in it }
        return given ?: fromName(name) ?: OCTET_STREAM
    }
}
