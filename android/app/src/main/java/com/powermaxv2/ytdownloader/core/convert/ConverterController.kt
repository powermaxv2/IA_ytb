package com.powermaxv2.ytdownloader.core.convert

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** The file(s) given to the converter: one file, or several photos. */
data class ConvertInput(val files: List<LocalFile>, val kind: FileKind, val category: Category) {
    val multiple: Boolean get() = files.size > 1
    val name: String get() = if (multiple) "${files.size} photos" else files.first().name

    /** Total size when every provider told it. */
    val size: Long? get() = files.map { it.size }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0L }

    /** Extension shown on the "MKV → MP4" badge. */
    val badge: String get() = if (multiple) "PHOTOS" else SafeNames.extensionOf(files.first().name).uppercase().ifEmpty { category.label.uppercase() }
}

sealed interface ConvertPhase {
    /** Nothing chosen yet. */
    data object Idle : ConvertPhase

    /** Reading the name, size and first bytes of the chosen file. */
    data object Reading : ConvertPhase

    /** A file is ready: pick a target and options. */
    data object Ready : ConvertPhase

    /** Gulp does not convert this file. [uri] lets the user open it in Aperçu instead. */
    data class Unsupported(val name: String, val message: String, val uri: Uri?) : ConvertPhase

    /** [progress] 0..1, or negative when unknown; [step] says what happens ("Page 3 sur 12"). */
    data class Converting(val progress: Float, val step: String) : ConvertPhase

    data class Done(val saved: List<SavedFile>) : ConvertPhase

    /** [debug] = last lines of FFmpeg's log, shown under "Détails". */
    data class Failed(val message: String, val debug: String? = null) : ConvertPhase
}

data class ConverterUiState(
    val phase: ConvertPhase = ConvertPhase.Idle,
    val input: ConvertInput? = null,
    val target: Target? = null,
    val options: ConvertOptions = ConvertOptions(),
) {
    val targets: List<Target> get() = input?.let { Formats.targets(it.category, it.multiple) }.orEmpty()
}

/**
 * The Convertisseur's state and work, held by the App (not by the screen): a conversion keeps running when the
 * user leaves the tool, and the screen finds it again on return. One conversion at a time.
 */
class ConverterController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var convertJob: Job? = null

    val busy: Boolean get() = _state.value.phase is ConvertPhase.Converting

    /**
     * Takes the chosen file(s): several files must all be photos ([Formats.MAX_IMAGES] at most). Returns false
     * (and changes nothing) while a conversion runs.
     */
    fun pick(uris: List<Uri>): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        readJob?.cancel()
        _state.update { it.copy(phase = ConvertPhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConverterUiState(phase = ConvertPhase.Unsupported("Fichier", e.message ?: "Impossible de lire ce fichier.", null))
            }
            _state.update { current -> if (current.phase is ConvertPhase.Converting) current else next.copy(options = current.options) }
        }
        return true
    }

    private fun describe(uris: List<Uri>): ConverterUiState {
        if (uris.size > Formats.MAX_IMAGES) {
            return ConverterUiState(
                phase = ConvertPhase.Unsupported("${uris.size} fichiers", "${Formats.MAX_IMAGES} photos au plus à la fois.", null),
            )
        }
        val files = uris.map { uri ->
            val info = FileIntake.describe(context, uri)
            val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
            info to FileKind.detect(info.name, info.mime, head)
        }
        if (files.size > 1) {
            val notPhoto = files.firstOrNull { (info, kind) -> kind != FileKind.IMAGE || Formats.refusal(kind, info.name) != null }
            if (notPhoto != null) {
                return ConverterUiState(
                    phase = ConvertPhase.Unsupported(
                        "${files.size} fichiers",
                        "Plusieurs fichiers à la fois : seulement des photos (pour en faire un PDF ou les convertir ensemble). « ${notPhoto.first.name} » n'en est pas une.",
                        null,
                    ),
                )
            }
            val input = ConvertInput(files.map { it.first }, FileKind.IMAGE, Category.IMAGE)
            // Too many photos for one PDF: start on JPG (the PDF tile then explains its limit).
            val target = if (files.size > Formats.MAX_PDF_PHOTOS) Target.JPG else Formats.defaultTarget(Category.IMAGE, input.name, multiple = true)
            return ConverterUiState(ConvertPhase.Ready, input, target)
        }
        val (info, kind) = files.single()
        val refusal = Formats.refusal(kind, info.name)
        val category = Category.of(kind)
        if (refusal != null || category == null) {
            return ConverterUiState(
                phase = ConvertPhase.Unsupported(info.name, refusal ?: "Gulp ne sait pas convertir ce type de fichier.", info.uri),
            )
        }
        val input = ConvertInput(listOf(info), kind, category)
        return ConverterUiState(ConvertPhase.Ready, input, Formats.defaultTarget(category, info.name))
    }

    fun selectTarget(target: Target) {
        _state.update { if (it.phase == ConvertPhase.Ready && target in it.targets) it.copy(target = target) else it }
    }

    fun setOptions(options: ConvertOptions) {
        _state.update { if (it.phase is ConvertPhase.Converting) it else it.copy(options = options) }
    }

    /** Back to the empty screen (keeps the options). */
    fun reset() {
        if (busy) return
        readJob?.cancel()
        _state.update { ConverterUiState(options = it.options) }
    }

    /** From a result or an error, back to the same file to choose another format or try again. */
    fun backToFile() {
        _state.update { if (it.input != null && !busy) it.copy(phase = ConvertPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        convertJob?.cancel()
        _state.update { if (it.phase is ConvertPhase.Converting) it.copy(phase = ConvertPhase.Ready) else it }
    }

    /**
     * Each conversion gets a number; a job only writes the state while its number is the current one, so a
     * cancelled job still finishing a blocking step (decode, copy) never overwrites the next conversion.
     */
    private val generation = AtomicInteger(0)

    fun convert() {
        val current = _state.value
        val input = current.input ?: return
        val target = current.target ?: return
        if (busy || current.phase is ConvertPhase.Reading) return
        if (input.multiple && target == Target.PDF && input.files.size > Formats.MAX_PDF_PHOTOS) {
            _state.update { it.copy(phase = ConvertPhase.Failed("Un PDF réunit ${Formats.MAX_PDF_PHOTOS} photos au plus : choisissez-en moins, ou un autre format.")) }
            return
        }
        val options = current.options
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = ConvertPhase.Converting(-1f, "Préparation…")) }
        val previous = convertJob
        convertJob = scope.launch(Dispatchers.IO) {
            // A cancelled conversion may still be leaving a blocking step: let it finish before starting.
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer la conversion.")
                return@launch
            }
            try {
                val outputs = Conversion(context, engine, dir) { value, step -> progress(token, value, step) }.run(input, target, options)
                val saved = ArrayList<SavedFile>(outputs.size)
                outputs.forEachIndexed { index, file ->
                    coroutineContext.ensureActive()
                    progress(token, -1f, if (outputs.size > 1) "Enregistrement ${index + 1} sur ${outputs.size}…" else "Enregistrement…")
                    saved.add(MediaExporter.export(context, file))
                }
                update(token) { it.copy(phase = ConvertPhase.Done(saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: DocumentError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Fichier trop volumineux.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Ce fichier ne peut pas être ouvert.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisissez le fichier à nouveau, ou autorisez le stockage.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifiez l'espace libre et réessayez.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour ce fichier.", e.toString())
            } catch (e: StackOverflowError) {
                fail(token, Nesting.MESSAGE, e.toString())
            } catch (e: Throwable) {
                // Nothing a file does may take the app (and the running downloads) down with it.
                fail(token, FfmpegErrors.GENERIC, e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private inline fun update(token: Int, crossinline change: (ConverterUiState) -> ConverterUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is ConvertPhase.Converting) it.copy(phase = ConvertPhase.Converting(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = ConvertPhase.Failed(message, debug)) }
    }
}

/** One conversion inside its work dir [dir]: copies the input in, writes the outputs to `dir/out`, returns them. */
private class Conversion(
    private val context: Context,
    private val engine: Engine,
    private val dir: File,
    private val progress: (Float, String) -> Unit,
) {
    private val outDir = File(dir, "out").apply { mkdirs() }

    suspend fun run(input: ConvertInput, target: Target, options: ConvertOptions): List<File> {
        val category = input.category
        val single = input.files.first()
        return when (Formats.method(category, target)) {
            Method.FFMPEG -> {
                progress(-1f, "Copie du fichier…")
                val copy = FileIntake.copyToWork(context, single.uri, FileIntake.MEDIA_MAX_BYTES, dir)
                // FFmpeg only ever sees fixed names ("input.mkv" → "output.mp4"), never the user's.
                val source = rename(copy, "input.${Formats.safeExt(SafeNames.extensionOf(single.name))}")
                val work = File(dir, "output.${target.ext}")
                progress(0f, "Conversion…")
                FfmpegRunner(context, engine).run(
                    FfmpegArgs.build(root, source.absolutePath, work.absolutePath, target, options),
                    work,
                    target,
                    mediaDuration(source),
                ) { progress(it, "Conversion…") }
                source.delete()
                listOf(publish(work, Formats.outputName(single.name, target)))
            }
            Method.BITMAP, Method.BITMAP_FFMPEG -> input.files.mapIndexed { index, file ->
                coroutineContext.ensureActive()
                val step = if (input.multiple) "Photo ${index + 1} sur ${input.files.size}" else "Conversion…"
                progress(index.toFloat() / input.files.size, step)
                val source = FileIntake.copyToWork(context, file.uri, IMAGE_MAX_BYTES, dir)
                val name = Formats.outputName(file.name, target)
                val bitmap = Images.decode(source)
                val out = try {
                    if (target == Target.BMP || target == Target.TIFF) {
                        val png = File(dir, "input.png").also { it.delete() }
                        Images.encode(bitmap, Target.PNG, 100, png)
                        bitmap.recycle()
                        val work = File(dir, "output.${target.ext}").also { it.delete() }
                        FfmpegRunner(context, engine).run(
                            FfmpegArgs.build(root, png.absolutePath, work.absolutePath, target, options),
                            work,
                            target,
                            null,
                        ) { }
                        png.delete()
                        publish(work, name)
                    } else {
                        FileIntake.uniqueFile(outDir, name).also { Images.encode(bitmap, target, options.quality.imageQuality, it) }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                source.delete()
                progress((index + 1).toFloat() / input.files.size, step)
                out
            }
            Method.IMAGES_TO_PDF -> {
                val count = input.files.size
                val copies = input.files.mapIndexed { index, file ->
                    coroutineContext.ensureActive()
                    progress(-1f, if (count > 1) "Copie des photos ${index + 1} sur $count…" else "Copie de la photo…")
                    FileIntake.copyToWork(context, file.uri, IMAGE_MAX_BYTES, dir)
                }
                val out = FileIntake.uniqueFile(outDir, Formats.outputName(single.name, Target.PDF))
                progress(0f, "Mise en page…")
                Images.imagesToPdf(copies, out) { done -> progress(done.toFloat() / count, "Page $done sur $count") }
                listOf(out)
            }
            Method.PDF_TO_IMAGES -> {
                progress(-1f, "Copie du PDF…")
                val source = FileIntake.copyToWork(context, single.uri, PDF_MAX_BYTES, dir)
                Images.pdfToImages(source, target, options.quality.imageQuality, outDir, SafeNames.baseOf(single.name)) { done, total ->
                    progress(done.toFloat() / total, "Page $done sur $total")
                }
            }
            Method.MARKDOWN_TO_HTML, Method.CSV_TO_JSON, Method.JSON_TO_CSV -> {
                progress(-1f, "Lecture…")
                val source = FileIntake.copyToWork(context, single.uri, CsvJson.MAX_BYTES.toLong(), dir)
                val text = TextDecode.decode(source.readBytes())
                progress(-1f, "Conversion…")
                val result = when (target) {
                    Target.HTML -> MarkdownHtml.render(text, SafeNames.baseOf(single.name))
                    Target.JSON -> CsvJson.csvToJson(text)
                    else -> CsvJson.jsonToCsv(text)
                }
                val out = FileIntake.uniqueFile(outDir, Formats.outputName(single.name, target))
                out.writeText(result, Charsets.UTF_8)
                listOf(out)
            }
        }
    }

    /** The tools' work root, the only place FFmpeg may read or write ([FfmpegArgs.build] checks it). */
    private val root: String = WorkDir.root(context).absolutePath

    private fun rename(file: File, name: String): File {
        val target = File(file.parentFile, name)
        if (file.absolutePath == target.absolutePath) return file
        target.delete()
        if (!file.renameTo(target)) throw ConversionError("Impossible de préparer le fichier pour la conversion.")
        return target
    }

    /** Moves FFmpeg's fixed-name output to `out/` under the name the user will see. */
    private fun publish(work: File, name: String): File {
        val dest = FileIntake.uniqueFile(outDir, name)
        if (!work.renameTo(dest)) {
            dest.delete()
            throw ConversionError("Impossible d'enregistrer le fichier converti.")
        }
        return dest
    }

    /** Length of a video / song in seconds, read from its container (null when unknown). */
    private fun mediaDuration(file: File): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { it / 1000.0 }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        /** Biggest photo copied in (each). */
        const val IMAGE_MAX_BYTES: Long = 64L * 1024 * 1024

        /** Biggest PDF copied in. */
        const val PDF_MAX_BYTES: Long = 200L * 1024 * 1024
    }
}
