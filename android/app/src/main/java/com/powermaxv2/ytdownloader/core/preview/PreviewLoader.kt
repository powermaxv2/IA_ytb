package com.powermaxv2.ytdownloader.core.preview

import android.content.Context
import android.net.Uri
import com.powermaxv2.ytdownloader.core.convert.Csv
import com.powermaxv2.ytdownloader.core.convert.CsvTable
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

// Opening a file in the Aperçu: what it is (name, size, type, first bytes) and, for text and archives, what is
// inside, read with caps. Images, sound, video and PDF are read by their viewers.

/** The file being previewed, as the info sheet shows it. */
data class PreviewFile(
    val uri: Uri,
    val name: String,
    val size: Long?,
    val mime: String,
    val kind: FileKind,
    /** First bytes (at most [HexDump.MAX_BYTES]) for the hex dump. */
    val head: ByteArray,
    /** Extension the bytes call for when the name's extension lies ([ExtensionCheck]), else null. */
    val expectedExtension: String?,
) {
    override fun equals(other: Any?): Boolean = other is PreviewFile && other.uri == uri && other.name == name && other.size == size &&
        other.mime == mime && other.kind == kind && other.head.contentEquals(head) && other.expectedExtension == expectedExtension
    override fun hashCode(): Int = uri.hashCode() * 31 + name.hashCode()
}

/** What the viewer draws. */
sealed interface PreviewBody {
    data object Image : PreviewBody
    data object Video : PreviewBody
    data object Audio : PreviewBody
    data object Pdf : PreviewBody
    data object Other : PreviewBody

    /** Plain text in wrapping rows. [note]: why it is shown raw (a JSON or Markdown that could not be laid out). */
    data class Text(val rows: List<String>, val cut: Boolean, val charset: String, val note: String? = null) : PreviewBody

    /** Highlighted code: [text] with "\n" line ends, its [tokens] and [lineStarts]. */
    class Code(
        val text: String,
        val lang: CodeLang,
        val tokens: List<Token>,
        val lineStarts: IntArray,
        val cut: Boolean,
        val charset: String,
        val note: String? = null,
    ) : PreviewBody {
        /** Longest line in characters (the width of the horizontal scroll), capped at [PreviewText.MAX_LINE_CHARS]. */
        val longestLine: Int by lazy {
            var max = 0
            for (i in lineStarts.indices) {
                val len = Highlighter.lineEnd(text, lineStarts, i) - lineStarts[i]
                if (len > max) max = len
            }
            max.coerceAtMost(PreviewText.MAX_LINE_CHARS)
        }
    }

    data class Markdown(val blocks: List<MdBlock>, val cut: Boolean) : PreviewBody
    data class Table(val table: CsvTable, val cut: Boolean) : PreviewBody
    data class Zip(val listing: ZipListing) : PreviewBody

    /** The content could not be shown (too big, damaged): the info sheet and the actions stay. */
    data class Notice(val message: String) : PreviewBody
}

data class PreviewDoc(val file: PreviewFile, val body: PreviewBody)

sealed interface PreviewPhase {
    data object Idle : PreviewPhase
    data object Loading : PreviewPhase
    data class Ready(val doc: PreviewDoc) : PreviewPhase
    data class Failed(val message: String) : PreviewPhase
}

object PreviewLoader {
    /** Bytes read to tell what a file is. */
    const val HEAD_BYTES = 64 * 1024

    /** Rows of a CSV shown as a table. */
    const val MAX_CSV_ROWS = 500

    /** Opens [uri]: blocking work on the IO dispatcher, every failure turned into a French message. */
    suspend fun load(context: Context, uri: Uri): PreviewPhase = withContext(Dispatchers.IO) {
        try {
            val info = FileIntake.describe(context, uri)
            val head = FileIntake.readHead(context, uri, HEAD_BYTES)
            val kind = FileKind.detect(info.name, info.mime, head)
            val file = PreviewFile(
                uri = uri,
                name = info.name,
                size = info.size,
                mime = info.mime,
                kind = kind,
                head = head.copyOf(minOf(head.size, HexDump.MAX_BYTES)),
                expectedExtension = ExtensionCheck.mismatch(info.name, head),
            )
            coroutineContext.ensureActive()
            PreviewPhase.Ready(PreviewDoc(file, body(context, file)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            PreviewPhase.Failed("Android a refusé l'accès à ce fichier. Choisissez-le à nouveau.")
        } catch (e: OutOfMemoryError) {
            PreviewPhase.Failed("Fichier trop lourd pour la mémoire du téléphone.")
        } catch (e: IOException) {
            PreviewPhase.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Impossible de lire ce fichier.")
        } catch (e: Throwable) {
            // StackOverflowError or anything else a parser throws: never let it escape.
            PreviewPhase.Failed("Gulp n'arrive pas à afficher ce fichier.")
        }
    }

    private suspend fun body(context: Context, file: PreviewFile): PreviewBody = when (file.kind) {
        FileKind.IMAGE -> PreviewBody.Image
        FileKind.VIDEO -> PreviewBody.Video
        FileKind.AUDIO -> PreviewBody.Audio
        FileKind.PDF -> PreviewBody.Pdf
        FileKind.ZIP -> zip(context, file)
        FileKind.TEXT, FileKind.CODE, FileKind.JSON, FileKind.MARKDOWN, FileKind.CSV -> text(context, file)
        FileKind.OTHER -> PreviewBody.Other
    }

    private fun text(context: Context, file: PreviewFile): PreviewBody {
        val raw = FileIntake.readHead(context, file.uri, PreviewText.MAX_BYTES + 1)
        val cut = raw.size > PreviewText.MAX_BYTES
        val decoded = PreviewText.decode(if (cut) raw.copyOf(PreviewText.MAX_BYTES) else raw, cut)
        return textBody(file.kind, file.name, decoded, cut)
    }

    /** The body of a text file already decoded (pure: also used by the screenshot tests). */
    fun textBody(kind: FileKind, name: String, decoded: DecodedText, cut: Boolean): PreviewBody {
        val text = decoded.text
        return when (kind) {
            FileKind.JSON -> {
                val pretty = if (cut) null else JsonPretty.format(text)
                val note = when {
                    pretty != null -> null
                    cut -> "JSON coupé au premier Mo : affiché tel quel."
                    else -> "JSON non valide ou trop imbriqué : affiché tel quel."
                }
                code(pretty ?: text, CodeLang.JSON, cut, decoded.charset, note)
            }
            FileKind.CODE -> code(text, CodeLang.fromName(name), cut, decoded.charset, null)
            FileKind.MARKDOWN -> try {
                PreviewBody.Markdown(MarkdownBlocks.parse(text), cut)
            } catch (e: TooDeep) {
                PreviewBody.Text(PreviewText.rows(text), cut, decoded.charset, "Markdown trop imbriqué pour être mis en forme : texte brut.")
            } catch (e: StackOverflowError) {
                PreviewBody.Text(PreviewText.rows(text), cut, decoded.charset, "Markdown trop imbriqué pour être mis en forme : texte brut.")
            }
            FileKind.CSV -> {
                val table = Csv.parse(text, maxRows = MAX_CSV_ROWS)
                PreviewBody.Table(table, cut || table.truncated)
            }
            else -> PreviewBody.Text(PreviewText.rows(text), cut, decoded.charset)
        }
    }

    private fun code(text: String, lang: CodeLang, cut: Boolean, charset: String, note: String?): PreviewBody.Code {
        val normalized = Highlighter.normalizeLineEnds(text)
        return PreviewBody.Code(normalized, lang, Highlighter.tokenize(normalized, lang), Highlighter.lineStarts(normalized), cut, charset, note)
    }

    /** Lists a ZIP from a capped copy in a work dir (ZipFile needs a real file); nothing is ever extracted. */
    private suspend fun zip(context: Context, file: PreviewFile): PreviewBody {
        val dir = WorkDir.create(context)
        try {
            val copy = try {
                FileIntake.copyToWork(context, file.uri, ZipNames.MAX_ARCHIVE_BYTES, dir)
            } catch (e: FileTooLarge) {
                return PreviewBody.Notice("Archive trop volumineuse pour l'aperçu : 512 Mo au maximum.")
            }
            coroutineContext.ensureActive()
            val listing = listZip(copy, Charsets.UTF_8) ?: listZip(copy, Charset.forName("IBM437"))
            return listing?.let { PreviewBody.Zip(it) } ?: PreviewBody.Notice("Archive illisible ou abîmée.")
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    /** Null when the archive cannot be read with [charset] (names in another encoding, damaged file). */
    private fun listZip(file: java.io.File, charset: Charset): ZipListing? = try {
        ZipFile(file, charset).use { zip ->
            val total = zip.size()
            val entries = ArrayList<ZipEntryInfo>(minOf(total, ZipNames.MAX_ENTRIES))
            val it = zip.entries()
            while (it.hasMoreElements() && entries.size < ZipNames.MAX_ENTRIES) {
                val e = it.nextElement()
                entries.add(ZipEntryInfo(e.name, e.size, e.compressedSize, e.isDirectory))
            }
            ZipNames.tree(entries, total, truncated = total > entries.size)
        }
    } catch (e: ZipException) {
        null
    } catch (e: IllegalArgumentException) {
        // Malformed names in this charset.
        null
    } catch (e: IOException) {
        null
    }

    /**
     * SHA-256 of [uri], read in 256 Kio blocks; [onProgress] gets 0..1 (or -1 when the size is unknown).
     * Cancellable between blocks.
     */
    suspend fun sha256(context: Context, uri: Uri, size: Long?, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        FileIntake.requireContent(uri)
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Impossible de lire ce fichier.")
        input.use { stream ->
            val buffer = ByteArray(256 * 1024)
            var total = 0L
            var lastReport = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                total += n
                if (total - lastReport >= 2L * 1024 * 1024) {
                    lastReport = total
                    onProgress(if (size != null && size > 0) (total.toFloat() / size).coerceIn(0f, 1f) else -1f)
                }
            }
        }
        onProgress(1f)
        HexDump.hex(digest.digest())
    }
}
