package com.powermaxv2.ytdownloader.core.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.powermaxv2.ytdownloader.core.chat.ChatExport
import com.powermaxv2.ytdownloader.core.chat.ChatMessage
import com.powermaxv2.ytdownloader.core.chat.ChatSample
import com.powermaxv2.ytdownloader.core.chat.WhatsAppChat
import com.powermaxv2.ytdownloader.core.files.FileIntake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

// Opening of an export for the Lecteur de discussion: the .txt, or the .zip WhatsApp shares. In a .zip "with
// media", the photos are shrunk to small thumbnails in memory to show in the bubbles and in the PDF. Nothing is
// written to storage and nothing is kept after leaving the tool.

/** A discussion ready to read. [photos] = thumbnails (JPEG bytes) by file name, for an export "with media". */
class ReaderChat(
    val name: String,
    val messages: List<ChatMessage>,
    val photos: Map<String, ByteArray> = emptyMap(),
    /** The made-up example discussion, not the user's. */
    val sample: Boolean = false,
)

/** What the Lecteur de discussion screen shows. */
sealed interface ReaderPhase {
    data object Idle : ReaderPhase
    data object Loading : ReaderPhase
    data class Ready(val chat: ReaderChat) : ReaderPhase
    data class Failed(val message: String) : ReaderPhase
}

object ReaderLoad {
    /** Thumbnails kept: beyond, the bubble says "Photo" with its name (a few MB of memory at most). */
    const val MAX_PHOTOS = 400
    private const val THUMB = 320
    private const val MAX_PHOTO_BYTES = 12 * 1024 * 1024

    private val PHOTO = Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
    private val PREFERRED = Regex("_chat\\.txt$|discussion|whatsapp chat|chat de whatsapp", RegexOption.IGNORE_CASE)

    /** Reads [uri] (content:// only) off the main thread. Never throws except on cancellation. */
    suspend fun load(context: Context, uri: Uri): ReaderPhase = withContext(Dispatchers.IO) {
        try {
            val name = runCatching { FileIntake.describe(context, uri).name }.getOrNull()
            val (text, photos) = context.contentResolver.openInputStream(uri)?.use { read(it) }
                ?: throw IOException("Impossible de lire ce fichier.")
            val messages = WhatsAppChat.parse(text)
            if (messages.isNullOrEmpty()) {
                ReaderPhase.Failed(ChatExport.NOT_WHATSAPP)
            } else {
                ReaderPhase.Ready(ReaderChat(WhatsAppChat.niceName(name), messages, photos))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatExport.ExportError) {
            ReaderPhase.Failed(e.message.orEmpty())
        } catch (e: OutOfMemoryError) {
            ReaderPhase.Failed(ChatExport.TOO_BIG)
        } catch (e: Exception) {
            ReaderPhase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Impossible de lire ce fichier.")
        }
    }

    /** The example discussion of "Vu, répondu", to try the reader without an export. */
    fun sample(): ReaderPhase = ReaderPhase.Ready(
        ReaderChat(ChatSample.NAME, WhatsAppChat.parse(ChatSample.text()).orEmpty(), sample = true),
    )

    /** The discussion text and, for a .zip, the thumbnails of its photos. */
    private fun read(input: InputStream): Pair<String, Map<String, ByteArray>> {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val head = ByteArray(4)
        val n = buffered.read(head).coerceAtLeast(0)
        buffered.reset()
        val isZip = n == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()
        if (!isZip) return ChatExport.readText(buffered) to emptyMap()

        var chat: String? = null
        var fallback: String? = null
        val photos = HashMap<String, ByteArray>()
        try {
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    // Read in memory only: the entry name is never used as a path.
                    val name = entry.name.substringAfterLast('/')
                    when {
                        name.endsWith(".txt", ignoreCase = true) && chat == null -> {
                            // readText() of a plain stream: the size cap and the BOM handling of Vu, répondu.
                            val text = ChatExport.readText(NonClosing(zip))
                            if (PREFERRED.containsMatchIn(name)) chat = text else if (fallback == null) fallback = text
                        }
                        PHOTO.containsMatchIn(name) && !name.startsWith("STK-", ignoreCase = true) && photos.size < MAX_PHOTOS -> {
                            runCatching { readCapped(zip)?.let { thumbnail(it) } }.getOrNull()?.let { photos[name] = it }
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw ChatExport.ExportError(ChatExport.BAD_ZIP)
        }
        val text = chat ?: fallback ?: throw ChatExport.ExportError(ChatExport.NO_TXT_IN_ZIP)
        return text to photos
    }

    /** The entry's bytes, or null when it is bigger than a photo should be. */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_PHOTO_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** A photo shrunk to about [THUMB] px and re-encoded: kilobytes in memory, not megabytes. */
    private fun thumbnail(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB && bounds.outHeight / (sample * 2) >= THUMB) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** Lets readText() read one zip entry without closing the whole zip when it is done. */
    private class NonClosing(private val zip: ZipInputStream) : InputStream() {
        override fun read(): Int = zip.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = zip.read(b, off, len)
        override fun close() = Unit
    }
}
