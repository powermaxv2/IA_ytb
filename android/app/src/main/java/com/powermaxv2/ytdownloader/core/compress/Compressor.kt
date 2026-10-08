package com.powermaxv2.ytdownloader.core.compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegInput
import com.powermaxv2.ytdownloader.core.convert.FfmpegRunner
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.convert.Target
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A file made lighter: [file] null when the result was not lighter than the original ([before] bytes). */
class ShrinkOutput(val before: Long, val file: File?)

/**
 * One compression job inside its work dir [dir]: copies each input in (capped), writes the lighter file to
 * `dir/out` under the name the user will see, and never touches the original.
 */
class Compressor(
    private val context: Context,
    private val engine: Engine,
    private val dir: File,
    private val progress: (Float, String, Long?) -> Unit,
) {
    private val outDir = File(dir, "out").apply { mkdirs() }
    private val root: String = WorkDir.root(context).absolutePath

    /**
     * Makes [item] lighter with [settings]. [prefix] starts every step ("2/5 · "); [index] / [count] spread the
     * progress of several files over one bar.
     */
    suspend fun shrink(item: CompressItem, settings: CompressSettings, prefix: String = "", index: Int = 0, count: Int = 1): ShrinkOutput {
        val job = coroutineContext
        val checkpoint: () -> Unit = { job.ensureActive() }
        fun overall(p: Float): Float = if (p < 0) -1f else (index + p.coerceIn(0f, 1f)) / count
        progress(overall(0f).takeIf { count > 1 } ?: -1f, "${prefix}Copie du fichier…", null)
        val cap = when (item.media) {
            Media.IMAGE -> IMAGE_MAX_BYTES
            Media.PDF -> PDF_MAX_BYTES
            else -> FileIntake.MEDIA_MAX_BYTES
        }
        val source = FileIntake.copyToWork(context, item.file.uri, cap, dir, checkpoint)
        val before = source.length()
        val out = try {
            when (item.media) {
                Media.VIDEO, Media.AUDIO -> media(item, source, settings) { p, written -> progress(overall(p), "${prefix}Compression ${if (item.media == Media.VIDEO) "de la vidéo" else "du son"}…", written) }
                Media.IMAGE -> image(item, source, settings).also { progress(overall(1f), "${prefix}Photo prête", it.length()) }
                Media.PDF -> pdf(item, source, settings) { p, written -> progress(overall(p), "${prefix}Pages refaites…", written) }
                Media.OTHER -> throw ConversionError("« ${item.file.name} » ne se compresse pas seul : mettez-le dans un ZIP.")
            }
        } finally {
            source.delete()
        }
        // Not lighter (already well compressed): the original stays the best file.
        if (out.length() >= before * CompressPlan.MIN_GAIN) {
            out.delete()
            return ShrinkOutput(before, null)
        }
        return ShrinkOutput(before, out)
    }

    /** All [items] in one ZIP (photos / videos / sounds / PDF made lighter first when [CompressSettings.shrinkBeforeZip]). */
    suspend fun zip(items: List<CompressItem>, settings: CompressSettings): File {
        val job = coroutineContext
        val checkpoint: () -> Unit = { job.ensureActive() }
        val name = zipName(items)
        val zipFile = FileIntake.uniqueFile(outDir, name)
        val used = HashSet<String>()
        val staging = File(dir, "zip-in").apply { mkdirs() }
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                items.forEachIndexed { index, item ->
                    coroutineContext.ensureActive()
                    val prefix = "${index + 1}/${items.size} · "
                    val lighter = if (settings.shrinkBeforeZip && item.media != Media.OTHER) {
                        runCatching { shrink(item, settings, prefix, index, items.size).file }
                            .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
                    } else {
                        null
                    }
                    val file = lighter ?: run {
                        progress(index.toFloat() / items.size, "${prefix}Copie de ${item.file.name}…", zipFile.length())
                        FileIntake.copyToWork(context, item.file.uri, FileIntake.MEDIA_MAX_BYTES, staging, checkpoint)
                    }
                    // The entry keeps the user's name (the lighter file's name if it changed), unique inside the ZIP.
                    val entryName = uniqueEntry(used, if (lighter != null) lighter.name else item.file.name)
                    progress((index + 0.9f) / items.size, "${prefix}Ajout au ZIP…", zipFile.length())
                    add(zip, file, entryName, checkpoint)
                    file.delete()
                }
            }
        } catch (e: Throwable) {
            zipFile.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
        return zipFile
    }

    private fun add(zip: ZipOutputStream, file: File, name: String, checkpoint: () -> Unit) {
        val entry = ZipEntry(name)
        entry.time = file.lastModified()
        if (CompressPlan.alreadyCompressed(name)) {
            // Stored: deflating a JPG or an MP4 only costs time. A stored entry needs its size and CRC first.
            val crc = CRC32()
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    checkpoint()
                    val n = input.read(buffer)
                    if (n < 0) break
                    crc.update(buffer, 0, n)
                }
            }
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            entry.crc = crc.value
        } else {
            entry.method = ZipEntry.DEFLATED
            zip.setLevel(Deflater.BEST_COMPRESSION)
        }
        zip.putNextEntry(entry)
        file.inputStream().use { FileIntake.copyCapped(it, zip, Long.MAX_VALUE, checkpoint) }
        zip.closeEntry()
    }

    // MARK: - Video and sound

    private suspend fun media(item: CompressItem, copy: File, settings: CompressSettings, onProgress: (Float, Long?) -> Unit): File {
        val head = readHead(copy)
        FfmpegInput.refusal(head)?.let { throw ConversionError(it) }
        val source = rename(copy, "input.${FfmpegInput.workExt(head, item.file.name)}")
        val video = item.media == Media.VIDEO
        val duration = duration(source) ?: item.info.durationSec
        val info = item.info.copy(durationSec = duration)
        val ext = if (video) "mp4" else CompressPlan.audio(info, item.file.name, settings).ext
        val work = File(dir, "output.$ext").also { it.delete() }
        val args = if (video) {
            CompressPlan.videoArgs(root, source.absolutePath, work.absolutePath, CompressPlan.video(info, settings))
        } else {
            CompressPlan.audioArgs(root, source.absolutePath, work.absolutePath, CompressPlan.audio(info, item.file.name, settings))
        }
        onProgress(0f, 0L)
        val target = if (video) Target.MP4 else if (ext == "mp3") Target.MP3 else Target.M4A
        FfmpegRunner(context, engine).run(args, work, target, duration) { onProgress(it, work.length()) }
        source.delete()
        return publish(work, CompressPlan.outputName(item.file.name, ext))
    }

    // MARK: - Photo

    private suspend fun image(item: CompressItem, source: File, settings: CompressSettings): File {
        val plan = CompressPlan.image(settings.level)
        var bitmap = Images.decode(source, plan.maxSide)
        try {
            val alpha = bitmap.hasAlpha() && hasTransparentPixel(bitmap)
            val ext = CompressPlan.outputExt(Media.IMAGE, item.file.name, alpha)
            val format = if (alpha) webp() else Bitmap.CompressFormat.JPEG
            val opaque = if (!alpha && bitmap.hasAlpha()) onWhite(bitmap).also { bitmap.recycle(); bitmap = it } else bitmap
            val goal = settings.goal
            val bytes = if (goal == null) {
                encode(opaque, format, plan.quality)
            } else {
                fit(opaque, format, (goal.bytes * CompressPlan.GOAL_MARGIN).toLong())
            }
            val out = FileIntake.uniqueFile(outDir, CompressPlan.outputName(item.file.name, ext))
            out.writeBytes(bytes)
            return out
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** The best quality that fits in [limit] bytes; the photo shrinks (×0.75 a few times) when even quality 35 does not. */
    private suspend fun fit(start: Bitmap, format: Bitmap.CompressFormat, limit: Long): ByteArray {
        var bitmap = start
        var best: ByteArray? = null
        try {
            repeat(6) {
                coroutineContext.ensureActive()
                var lo = 35
                var hi = 90
                while (lo <= hi) {
                    val q = (lo + hi) / 2
                    val bytes = encode(bitmap, format, q)
                    if (bytes.size <= limit) {
                        best = bytes
                        lo = q + 1
                    } else {
                        hi = q - 1
                    }
                }
                best?.let { return it }
                val w = max(1, (bitmap.width * 0.75f).roundToInt())
                val h = max(1, (bitmap.height * 0.75f).roundToInt())
                val smaller = Bitmap.createScaledBitmap(bitmap, w, h, true)
                if (bitmap !== start) bitmap.recycle()
                bitmap = smaller
            }
            return encode(bitmap, format, 35)
        } finally {
            if (bitmap !== start) bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality.coerceIn(0, 100), out)) throw ConversionError("Impossible d'écrire la photo.")
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** Whether a pixel is really see-through (many PNGs carry an alpha channel that is opaque everywhere). */
    private fun hasTransparentPixel(bitmap: Bitmap): Boolean {
        val step = max(1, min(bitmap.width, bitmap.height) / 64)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 250) return true
                x += step
            }
            y += step
        }
        return false
    }

    private fun onWhite(bitmap: Bitmap): Bitmap {
        val copy = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return copy
    }

    // MARK: - PDF

    /**
     * Rebuilds the PDF from pictures of its pages (JPEG): scans and photo-heavy PDF get much lighter, a text-only
     * PDF often does not (then the original is kept). With a size goal, lighter settings are tried in turn.
     */
    private suspend fun pdf(item: CompressItem, source: File, settings: CompressSettings, onProgress: (Float, Long?) -> Unit): File {
        val name = CompressPlan.outputName(item.file.name, "pdf")
        val goal = settings.goal
        if (goal == null) return renderPdf(source, CompressPlan.pdf(settings.level), name, onProgress)
        val limit = (goal.bytes * CompressPlan.GOAL_MARGIN).toLong()
        var last: File? = null
        for ((i, plan) in CompressPlan.pdfLadder.withIndex()) {
            last?.delete()
            val ladder = CompressPlan.pdfLadder.size
            last = renderPdf(source, plan, name) { p, w -> onProgress((i + p) / ladder, w) }
            if (last.length() <= limit) return last
        }
        return last ?: throw ConversionError("Ce PDF n'a aucune page.")
    }

    private suspend fun renderPdf(source: File, plan: PdfPlan, name: String, onProgress: (Float, Long?) -> Unit): File {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.close()
            throw ConversionError("Ce PDF est protégé par un mot de passe : Gulp ne peut pas l'ouvrir.")
        } catch (e: Exception) {
            descriptor.close()
            throw ConversionError("Ce PDF est illisible ou abîmé.")
        }
        val out = FileIntake.uniqueFile(outDir, name)
        try {
            val count = renderer.pageCount
            if (count == 0) throw ConversionError("Ce PDF n'a aucune page.")
            if (count > CompressPlan.MAX_PDF_PAGES) throw ConversionError("${CompressPlan.MAX_PDF_PAGES} pages au plus pour un PDF.")
            JpegPdfWriter(out.outputStream().buffered()).use { writer ->
                for (index in 0 until count) {
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val longest = max(page.width, page.height).coerceAtLeast(1)
                        val scale = min(plan.dpi / 72f, PAGE_MAX_SIDE.toFloat() / longest)
                        val w = max(1, (page.width * scale).roundToInt())
                        val h = max(1, (page.height * scale).roundToInt())
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            writer.addPage(encode(bitmap, Bitmap.CompressFormat.JPEG, plan.quality), w, h, page.width.toFloat(), page.height.toFloat())
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    onProgress((index + 1).toFloat() / count, out.length())
                }
                writer.finish()
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            renderer.close()
            descriptor.close()
        }
        return out
    }

    // MARK: - Helpers

    private fun zipName(items: List<CompressItem>): String {
        if (items.size == 1) return SafeNames.withExtension(items.first().file.name, "zip")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.FRANCE).format(Date())
        return "Gulp $stamp (${items.size} fichiers).zip"
    }

    /** [name] made safe for a ZIP entry (no folders, no "..") and unique among [used] ("a.jpg", "a (2).jpg"…). */
    private fun uniqueEntry(used: MutableSet<String>, name: String): String {
        val safe = SafeNames.sanitize(name)
        var candidate = safe
        var n = 2
        while (!used.add(candidate.lowercase(Locale.ROOT))) {
            val ext = SafeNames.extensionOf(safe)
            candidate = SafeNames.baseOf(safe) + " ($n)" + if (ext.isEmpty()) "" else ".$ext"
            n++
        }
        return candidate
    }

    private fun readHead(file: File): ByteArray = file.inputStream().use { input ->
        val out = ByteArray(FileKind.HEAD_SIZE)
        var read = 0
        while (read < out.size) {
            val n = input.read(out, read, out.size - read)
            if (n < 0) break
            read += n
        }
        out.copyOf(read)
    }

    private fun rename(file: File, name: String): File {
        val target = File(file.parentFile, name)
        if (file.absolutePath == target.absolutePath) return file
        target.delete()
        if (!file.renameTo(target)) throw ConversionError("Impossible de préparer le fichier.")
        return target
    }

    private fun publish(work: File, name: String): File {
        val dest = FileIntake.uniqueFile(outDir, name)
        if (!work.renameTo(dest)) {
            dest.delete()
            throw ConversionError("Impossible d'enregistrer le fichier compressé.")
        }
        return dest
    }

    private fun duration(file: File): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000.0 }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val IMAGE_MAX_BYTES: Long = 64L * 1024 * 1024
        const val PDF_MAX_BYTES: Long = 200L * 1024 * 1024

        /** Longest side of a rendered PDF page. */
        const val PAGE_MAX_SIDE = 2400
    }
}
