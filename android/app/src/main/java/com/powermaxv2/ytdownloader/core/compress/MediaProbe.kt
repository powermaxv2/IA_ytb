package com.powermaxv2.ytdownloader.core.compress

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Reads, without copying the file, what the estimate needs: length and size of a video, length of a sound,
 * pixels of a photo, pages of a PDF. Every read may fail (odd file, provider without seeking): the field stays null.
 */
object MediaProbe {
    fun read(context: Context, uri: Uri, media: Media): MediaInfo = runCatching {
        when (media) {
            Media.VIDEO, Media.AUDIO -> retriever(context, uri, media == Media.VIDEO)
            Media.IMAGE -> image(context, uri)
            Media.PDF -> MediaInfo(pages = pdfPages(context, uri))
            Media.OTHER -> MediaInfo()
        }
    }.getOrDefault(MediaInfo())

    private fun retriever(context: Context, uri: Uri, video: Boolean): MediaInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000.0 }
            if (!video) return MediaInfo(durationSec = duration)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            MediaInfo(durationSec = duration, width = w, height = h)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun image(context: Context, uri: Uri): MediaInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return MediaInfo(width = options.outWidth.takeIf { it > 0 }, height = options.outHeight.takeIf { it > 0 })
    }

    private fun pdfPages(context: Context, uri: Uri): Int? =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd -> PdfRenderer(fd).use { it.pageCount } }
}
