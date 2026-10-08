package com.powermaxv2.ytdownloader.core.chat

import android.content.Context
import android.net.Uri
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

// Opening of an export: the .txt WhatsApp writes, or the .zip it shares (the .txt inside is read in memory,
// nothing is extracted to storage). Everything stays on the phone and nothing is kept after leaving the tool.

/** What the "Vu, répondu" screen shows. */
sealed interface ChatPhase {
    data object Idle : ChatPhase
    data object Loading : ChatPhase

    /** [sample] = the made-up example discussion, not the user's. */
    data class Ready(val stats: ChatStats, val name: String, val sample: Boolean) : ChatPhase
    data class Failed(val message: String) : ChatPhase
}

object ChatExport {
    /** Biggest discussion read: 40 Mo of text is several hundred thousand messages. */
    const val MAX_TEXT_BYTES: Long = 40L * 1024 * 1024

    const val NOT_WHATSAPP =
        "Je n'ai pas reconnu d'export WhatsApp dans ce fichier. Vérifie qu'il s'agit bien du .txt exporté (ou du .zip) sans médias."
    const val NO_TXT_IN_ZIP = "Ce .zip ne contient pas de fichier .txt de discussion."
    const val BAD_ZIP = "Ce .zip est abîmé ou refusé par Android. Dézippe-le et choisis le fichier .txt qu'il contient."
    const val TOO_BIG = "Discussion trop longue : 40 Mo de texte au maximum."

    /** Reads and analyses [uri] (content:// only), off the main thread. Never throws except on cancellation. */
    suspend fun load(context: Context, uri: Uri): ChatPhase = withContext(Dispatchers.IO) {
        try {
            val info = FileIntake.describe(context, uri)
            val text = context.contentResolver.openInputStream(uri)?.use { readText(it) }
                ?: throw IOException("Impossible de lire ce fichier.")
            analyse(text, info.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExportError) {
            ChatPhase.Failed(e.message.orEmpty())
        } catch (e: OutOfMemoryError) {
            ChatPhase.Failed(TOO_BIG)
        } catch (e: Exception) {
            ChatPhase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Impossible de lire ce fichier.")
        }
    }

    /** The phase for the text of an export named [fileName]. */
    fun analyse(text: String, fileName: String?): ChatPhase {
        val messages = WhatsAppChat.parse(text)
        if (messages == null || messages.size < 2) return ChatPhase.Failed(NOT_WHATSAPP)
        return ChatPhase.Ready(ChatStats.of(messages), WhatsAppChat.niceName(fileName), sample = false)
    }

    fun sample(): ChatPhase = ChatPhase.Ready(ChatSample.stats, ChatSample.NAME, sample = true)

    /** A message for the user, shown as is. */
    class ExportError(message: String) : IOException(message)

    /** The discussion text of an export: the stream itself, or the chat .txt of a .zip. */
    fun readText(input: InputStream, maxBytes: Long = MAX_TEXT_BYTES): String {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val head = ByteArray(4)
        val n = buffered.read(head).coerceAtLeast(0)
        buffered.reset()
        val isZip = n == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()
        return if (isZip) textInZip(buffered, maxBytes) else decode(readCapped(buffered, maxBytes))
    }

    private val PREFERRED = Regex("_chat\\.txt$|discussion", RegexOption.IGNORE_CASE)

    private fun textInZip(input: InputStream, maxBytes: Long): String {
        var fallback: ByteArray? = null
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".txt", ignoreCase = true)) continue
                    // Read in memory only: the entry name is never used as a path.
                    val bytes = readCapped(zip, maxBytes)
                    if (PREFERRED.containsMatchIn(entry.name.substringAfterLast('/'))) return decode(bytes)
                    if (fallback == null) fallback = bytes
                }
            }
        } catch (e: ZipException) {
            throw ExportError(BAD_ZIP)
        }
        return decode(fallback ?: throw ExportError(NO_TXT_IN_ZIP))
    }

    private fun readCapped(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw ExportError(TOO_BIG)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** UTF-8 (what WhatsApp writes), with or without BOM; UTF-16 when it starts with its BOM. */
    private fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }
}
