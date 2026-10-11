package com.powermaxv2.ytdownloader.core.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.convert.ConversionError
import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.convert.FfmpegErrors
import com.powermaxv2.ytdownloader.core.convert.FfmpegInput
import com.powermaxv2.ytdownloader.core.convert.FfmpegRunner
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.files.FileIntake
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.FileTooLarge
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.files.UnsupportedSource
import com.powermaxv2.ytdownloader.core.files.WorkDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

/** A photo given to the tool, already cut to its middle square. */
class StickerPhoto(val file: LocalFile, val square: Bitmap)

/** The video or GIF given to the tool: [square] is the picture at the start of the clip. */
class StickerMotion(val file: LocalFile, val durationMs: Long, val gif: Boolean, val square: Bitmap)

/** A pack on the phone, with its tray icon for the list. */
class SavedPack(val pack: StickerPack, val tray: Bitmap?)

sealed interface StickerPhase {
    data object Idle : StickerPhase

    data object Reading : StickerPhase

    data class Unsupported(val message: String) : StickerPhase

    data object Ready : StickerPhase

    /** [progress] 0..1, or negative when unknown. */
    data class Working(val progress: Float, val step: String) : StickerPhase

    /** [stickers] = the stickers drawn small, [sizes] = the bytes of each one. */
    data class Done(
        val pack: StickerPack,
        val stickers: List<Bitmap>,
        val sizes: List<Int>,
        val saved: List<SavedFile> = emptyList(),
        val saveFailed: Boolean = false,
    ) : StickerPhase

    data class Failed(val message: String, val debug: String? = null) : StickerPhase
}

data class StickerUiState(
    val phase: StickerPhase = StickerPhase.Idle,
    val photos: List<StickerPhoto> = emptyList(),
    val motion: StickerMotion? = null,
    val style: StickerStyle = StickerStyle(),
    val clip: StickerClip = StickerClip(),
    val name: String = "",
    val emoji: String = StickerRules.DEFAULT_EMOJI,
    /** Which picture the big preview shows. */
    val selected: Int = 0,
    /** Magic-wand touches of each photo (0..1 of the picture). */
    val seeds: Map<Int, List<Pair<Float, Float>>> = emptyMap(),
    /** The selected sticker, drawn at full size. */
    val preview: Bitmap? = null,
    /** Every sticker of the pack, drawn small (the strip under the preview). */
    val thumbs: List<Bitmap> = emptyList(),
    val packs: List<SavedPack> = emptyList(),
    val whatsApp: Boolean = true,
) {
    val animated: Boolean get() = motion != null
    val count: Int get() = if (motion != null) 1 else photos.size

    /** The stickers the pack will hold (with the variants that complete it to 3). */
    val plan: List<Pair<Int, StickerVariant>> get() = if (count == 0) emptyList() else StickerRules.plan(count, style.hasText, animated)
}

/**
 * The Stickers tool's state and work, held by the App like the other tools. Photos are cut to a square when chosen;
 * each change of style redraws the preview. "Créer le pack" draws every sticker at 512 px, encodes it as WebP under
 * WhatsApp's limits (frames read by FFmpeg and assembled by [AnimatedWebp] for a video or a GIF), and saves the pack
 * in Gulp's files, where WhatsApp reads it through [StickerProvider].
 */
class StickerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: Engine,
) {
    private val _state = MutableStateFlow(StickerUiState())
    val state: StateFlow<StickerUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var drawJob: Job? = null
    private var frameJob: Job? = null
    private var workJob: Job? = null
    private val generation = AtomicInteger(0)

    val busy: Boolean get() = _state.value.phase is StickerPhase.Working

    init {
        refreshPacks()
    }

    /** Reads the packs on the phone and whether WhatsApp is installed (on opening the tool, after a change). */
    fun refreshPacks() {
        scope.launch(Dispatchers.IO) {
            val packs = StickerStore.list(context).map { SavedPack(it, trayOf(it)) }
            val whatsApp = WhatsAppStickers.installed(context).isNotEmpty()
            _state.update { it.copy(packs = packs, whatsApp = whatsApp) }
        }
    }

    private fun trayOf(pack: StickerPack): Bitmap? =
        StickerStore.asset(context, pack.id, StickerRules.TRAY_FILE)?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

    /** Photos (one or several) or one video / GIF. False while a pack is being made. */
    fun pick(uris: List<Uri>, add: Boolean = false): Boolean {
        if (busy) return false
        if (uris.isEmpty()) return true
        readJob?.cancel()
        val keep = if (add && _state.value.motion == null) _state.value.photos else emptyList()
        _state.update { it.copy(phase = StickerPhase.Reading) }
        readJob = scope.launch(Dispatchers.IO) {
            val next = try {
                describe(keep, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Picked.Refused("Android a refusé l'accès : choisis le fichier à nouveau.")
            } catch (e: OutOfMemoryError) {
                Picked.Refused("Pas assez de mémoire pour ces images.")
            } catch (e: Exception) {
                Picked.Refused(e.message ?: "Impossible de lire ce fichier.")
            }
            _state.update { current ->
                if (current.phase is StickerPhase.Working) return@update current
                when (next) {
                    is Picked.Refused -> current.copy(phase = StickerPhase.Unsupported(next.message), photos = keep, motion = null)
                    is Picked.Photos -> current.copy(phase = StickerPhase.Ready, photos = next.photos, motion = null, selected = if (add) current.selected else 0, seeds = if (add) current.seeds else emptyMap(), preview = null, thumbs = emptyList())
                    is Picked.Motion -> current.copy(phase = StickerPhase.Ready, photos = emptyList(), motion = next.motion, clip = StickerRules.clamp(StickerClip(), next.motion.durationMs), selected = 0, seeds = emptyMap(), preview = null, thumbs = emptyList())
                }
            }
            redraw()
        }
        return true
    }

    private sealed interface Picked {
        class Photos(val photos: List<StickerPhoto>) : Picked

        class Motion(val motion: StickerMotion) : Picked

        class Refused(val message: String) : Picked
    }

    private suspend fun describe(keep: List<StickerPhoto>, uris: List<Uri>): Picked {
        val known = keep.map { it.file.uri }.toSet()
        val infos = uris.filter { it !in known }.map { uri ->
            val info = FileIntake.describe(context, uri)
            val head = runCatching { FileIntake.readHead(context, uri) }.getOrDefault(ByteArray(0))
            Triple(info, head, FileKind.detect(info.name, info.mime, head))
        }
        val moving = infos.filter { (_, head, kind) -> kind == FileKind.VIDEO || isGif(head) }
        if (moving.isNotEmpty()) {
            if (infos.size > 1 || keep.isNotEmpty()) return Picked.Refused("Une vidéo ou un GIF se transforme seul : un pack est soit tout fixe, soit tout animé.")
            return readMotion(moving.first().first, moving.first().second)
        }
        val photos = infos.filter { it.third == FileKind.IMAGE }
        if (photos.isEmpty()) {
            val name = infos.firstOrNull()?.first?.name
            return Picked.Refused((if (name != null) "« $name » n'est ni une photo ni une vidéo. " else "") + "Les stickers se font à partir de photos, d'une vidéo ou d'un GIF.")
        }
        if (keep.size + photos.size > StickerRules.MAX_STICKERS) return Picked.Refused("Un pack WhatsApp a ${StickerRules.MAX_STICKERS} stickers au plus.")
        val dir = WorkDir.create(context)
        try {
            val read = photos.map { (info, _, _) ->
                coroutineContext.ensureActive()
                val copy = FileIntake.copyToWork(context, info.uri, PHOTO_MAX_BYTES, dir)
                val bitmap = Images.decode(copy, maxSide = DECODE_SIDE)
                copy.delete()
                StickerPhoto(info, StickerArt.square(bitmap).also { if (it !== bitmap) bitmap.recycle() })
            }
            return Picked.Photos(keep + read)
        } finally {
            WorkDir.delete(context, dir)
        }
    }

    private fun isGif(head: ByteArray) = head.size >= 6 && String(head, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }

    private suspend fun readMotion(info: LocalFile, head: ByteArray): Picked {
        if (isGif(head)) {
            val dir = WorkDir.create(context)
            try {
                val copy = FileIntake.copyToWork(context, info.uri, GIF_MAX_BYTES, dir) { }
                val gif = GifInfo.read(copy.readBytes()) ?: return Picked.Refused("Ce GIF est abîmé ou vide.")
                val first = BitmapFactory.decodeFile(copy.absolutePath) ?: return Picked.Refused("Ce GIF ne s'ouvre pas sur ce téléphone.")
                val square = StickerArt.square(first).also { first.recycle() }
                return Picked.Motion(StickerMotion(info, gif.durationMs.coerceAtLeast(100), gif = true, square = square))
            } finally {
                WorkDir.delete(context, dir)
            }
        }
        FfmpegInput.refusal(head)?.let { return Picked.Refused(it) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, info.uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return Picked.Refused("Gulp ne trouve pas la durée de cette vidéo : elle est peut-être abîmée.")
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return Picked.Refused("Ce fichier n'a pas d'image.")
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return Picked.Refused("Gulp ne sait pas lire cette vidéo sur ce téléphone.")
            val square = StickerArt.square(frame).also { frame.recycle() }
            return Picked.Motion(StickerMotion(info, duration, gif = false, square = square))
        } catch (e: RuntimeException) {
            return Picked.Refused("Gulp ne sait pas lire cette vidéo sur ce téléphone.")
        } finally {
            runCatching { retriever.release() }
        }
    }

    // MARK: - Choices

    fun setStyle(style: StickerStyle) = edit { it.copy(style = style) }

    fun setName(name: String) = edit(redraw = false) { it.copy(name = name.take(StickerRules.NAME_MAX)) }

    fun setEmoji(emoji: String) = edit(redraw = false) { it.copy(emoji = emoji) }

    fun select(index: Int) = edit { if (index in 0 until it.plan.size) it.copy(selected = index) else it }

    /** A magic-wand touch on the big preview (0..1 of the sticker), for the selected photo. */
    fun touch(x: Float, y: Float) = edit { s ->
        val (source, variant) = s.plan.getOrNull(s.selected) ?: return@edit s
        if (s.motion != null) return@edit s
        val point = StickerRules.toSource(s.style, variant, x, y) ?: return@edit s
        val seeds = (s.seeds[source].orEmpty() + point).takeLast(MAX_TOUCHES)
        s.copy(seeds = s.seeds + (source to seeds))
    }

    /** Forgets the magic-wand touches of the selected photo. */
    fun clearTouches() = edit { s ->
        val source = s.plan.getOrNull(s.selected)?.first ?: return@edit s
        s.copy(seeds = s.seeds - source)
    }

    fun removeSelected() = edit { s ->
        val source = s.plan.getOrNull(s.selected)?.first ?: return@edit s
        if (s.motion != null || s.photos.size <= 1) return@edit StickerUiState(packs = s.packs, whatsApp = s.whatsApp, style = s.style)
        val seeds = s.seeds.filterKeys { it != source }.mapKeys { (k, _) -> if (k > source) k - 1 else k }
        s.copy(photos = s.photos.filterIndexed { i, _ -> i != source }, seeds = seeds, selected = 0)
    }

    fun setClip(clip: StickerClip) {
        edit(redraw = false) { s -> s.motion?.let { s.copy(clip = StickerRules.clamp(clip, it.durationMs)) } ?: s }
    }

    /** The start handle was let go: the picture at the new start is read (videos), then the preview redrawn. */
    fun settleClip() {
        val motion = _state.value.motion ?: return
        if (motion.gif) return
        frameJob?.cancel()
        frameJob = scope.launch(Dispatchers.IO) {
            delay(120)
            val retriever = MediaMetadataRetriever()
            val square = try {
                retriever.setDataSource(context, motion.file.uri)
                retriever.getFrameAtTime(_state.value.clip.startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                    StickerArt.square(frame).also { frame.recycle() }
                }
            } catch (e: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            coroutineContext.ensureActive()
            if (square != null) {
                _state.update { s -> if (s.motion?.file?.uri == motion.file.uri) s.copy(motion = StickerMotion(motion.file, motion.durationMs, false, square)) else s }
                redraw()
            }
        }
    }

    private inline fun edit(redraw: Boolean = true, crossinline change: (StickerUiState) -> StickerUiState) {
        var changed = false
        _state.update { s ->
            if (s.phase != StickerPhase.Ready) return@update s
            change(s).also { changed = it != s }
        }
        if (changed && redraw) redraw()
    }

    /** Draws the preview and the strip again for the current choices (the last change wins). */
    private fun redraw() {
        drawJob?.cancel()
        drawJob = scope.launch(Dispatchers.Default) {
            delay(60)
            val s = _state.value
            val plan = s.plan
            if (plan.isEmpty()) return@launch
            val selected = s.selected.coerceIn(0, plan.size - 1)
            fun draw(entry: Pair<Int, StickerVariant>): Bitmap? {
                val square = s.motion?.square ?: s.photos.getOrNull(entry.first)?.square ?: return null
                return StickerArt.render(square, s.style, entry.second, s.seeds[entry.first].orEmpty())
            }
            val preview = try {
                draw(plan[selected])
            } catch (e: OutOfMemoryError) {
                null
            }
            coroutineContext.ensureActive()
            _state.update { if (it.photos === s.photos && it.motion === s.motion) it.copy(preview = preview) else it }
            val thumbs = plan.map { entry ->
                coroutineContext.ensureActive()
                val big = (if (entry == plan[selected]) preview else draw(entry)) ?: return@launch
                StickerArt.scaled(big, THUMB).also { if (it !== big && big !== preview) big.recycle() }
            }
            coroutineContext.ensureActive()
            _state.update { if (it.photos === s.photos && it.motion === s.motion) it.copy(thumbs = thumbs) else it }
        }
    }

    fun reset() {
        if (busy) return
        readJob?.cancel()
        drawJob?.cancel()
        _state.update { StickerUiState(style = it.style, emoji = it.emoji, packs = it.packs, whatsApp = it.whatsApp) }
        refreshPacks()
    }

    /** From an error: back to the same pictures and choices. */
    fun backToEdit() {
        _state.update { if (it.count > 0 && !busy) it.copy(phase = StickerPhase.Ready) else it }
    }

    fun cancel() {
        generation.incrementAndGet()
        workJob?.cancel()
        _state.update { if (it.phase is StickerPhase.Working) it.copy(phase = StickerPhase.Ready) else it }
    }

    fun deletePack(id: String) {
        scope.launch(Dispatchers.IO) {
            StickerStore.delete(context, id)
            refreshPacks()
        }
    }

    /** Shows a pack already on the phone (from "Mes packs"), to add it to WhatsApp again or save it. */
    fun openPack(pack: StickerPack) {
        if (busy) return
        scope.launch(Dispatchers.IO) {
            val files = pack.stickers.mapNotNull { StickerStore.asset(context, pack.id, it) }
            val pictures = files.mapNotNull { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.let { StickerArt.scaled(it, THUMB) } }
            _state.update { it.copy(phase = StickerPhase.Done(pack, pictures, files.map { f -> f.length().toInt() })) }
        }
    }

    // MARK: - Making the pack

    fun start() {
        val current = _state.value
        if (busy || current.phase !is StickerPhase.Ready && current.phase !is StickerPhase.Failed || current.count == 0) return
        val token = generation.incrementAndGet()
        _state.update { it.copy(phase = StickerPhase.Working(0f, "Préparation…")) }
        val previous = workJob
        workJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val dir = try {
                WorkDir.create(context)
            } catch (e: IOException) {
                fail(token, e.message ?: "Impossible de préparer les stickers.")
                return@launch
            }
            try {
                val made = if (current.motion != null) animated(token, current, current.motion, dir) else stills(token, current)
                progress(token, -1f, "Rangement du pack…")
                val pack = StickerStore.save(context, current.name, current.motion != null, made.map { it.bytes }, made.first().tray, current.emoji)
                update(token) { it.copy(phase = StickerPhase.Done(pack, made.map { m -> m.thumb }, made.map { m -> m.bytes.size })) }
                refreshPacks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionError) {
                fail(token, e.message ?: FfmpegErrors.GENERIC, e.debug)
            } catch (e: FileTooLarge) {
                fail(token, e.message ?: "Fichier trop volumineux.")
            } catch (e: UnsupportedSource) {
                fail(token, e.message ?: "Ce fichier ne peut pas être ouvert.")
            } catch (e: SecurityException) {
                fail(token, "Android a refusé l'accès : choisis le fichier à nouveau.", e.toString())
            } catch (e: IOException) {
                fail(token, "Lecture ou écriture impossible : vérifie l'espace libre et réessaie.", e.toString())
            } catch (e: OutOfMemoryError) {
                fail(token, "Pas assez de mémoire pour faire ces stickers.", e.toString())
            } catch (e: Throwable) {
                fail(token, "Les stickers n'ont pas pu être faits.", e.toString())
            } finally {
                withContext(NonCancellable) { WorkDir.delete(context, dir) }
            }
        }
    }

    private class Made(val bytes: ByteArray, val thumb: Bitmap, val tray: ByteArray)

    private suspend fun stills(token: Int, s: StickerUiState): List<Made> {
        val plan = s.plan
        return plan.mapIndexed { i, (source, variant) ->
            coroutineContext.ensureActive()
            progress(token, i.toFloat() / plan.size, "Sticker ${i + 1}/${plan.size}…")
            val sticker = StickerArt.render(s.photos[source].square, s.style, variant, s.seeds[source].orEmpty())
            try {
                val bytes = StickerArt.encodeStill(sticker)
                if (!StickerRules.fits(bytes.size, animated = false)) throw ConversionError("Un sticker reste trop lourd pour WhatsApp, même très compressé.")
                Made(bytes, StickerArt.scaled(sticker, THUMB), if (i == 0) StickerArt.tray(sticker) else ByteArray(0))
            } finally {
                sticker.recycle()
            }
        }
    }

    private suspend fun animated(token: Int, s: StickerUiState, motion: StickerMotion, dir: File): List<Made> {
        progress(token, -1f, "Copie du fichier…")
        val job = coroutineContext
        val copy = FileIntake.copyToWork(context, motion.file.uri, if (motion.gif) GIF_MAX_BYTES else FileIntake.MEDIA_MAX_BYTES, dir) { job.ensureActive() }
        val head = copy.inputStream().use { input -> ByteArray(FileKind.HEAD_SIZE).let { b -> b.copyOf(maxOf(0, input.read(b))) } }
        val ext = if (motion.gif) "gif" else FfmpegInput.workExt(head, motion.file.name)
        val input = File(dir, "input.$ext")
        if (!copy.renameTo(input)) throw ConversionError("Impossible de préparer le fichier.")
        val framesDir = File(dir, "frames").apply { mkdirs() }
        val clip = StickerRules.clamp(s.clip, motion.durationMs)
        val args = frameArgs(WorkDir.root(context).absolutePath, input.absolutePath, File(framesDir, "f%03d.png").absolutePath, clip)
        progress(token, 0f, "Lecture des images…")
        FfmpegRunner(context, engine).run(args, File(framesDir, "f001.png"), null, clip.lengthMs / 1000.0) { progress(token, it * 0.3f, "Lecture des images…") }
        input.delete()
        val frameFiles = framesDir.listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name }.orEmpty().take(MAX_FRAMES)
        if (frameFiles.isEmpty()) throw ConversionError("Aucune image n'a pu être lue dans ce passage.")
        val plan = s.plan
        return plan.mapIndexed { i, (_, variant) ->
            coroutineContext.ensureActive()
            val base = 0.3f + 0.7f * i / plan.size
            val span = 0.7f / plan.size
            progress(token, base, "Sticker ${i + 1}/${plan.size} : dessin…")
            // REVERSE is the normal drawing played backwards.
            val drawVariant = if (variant == StickerVariant.REVERSE) StickerVariant.NORMAL else variant
            val drawn = frameFiles.map { f ->
                coroutineContext.ensureActive()
                val raw = BitmapFactory.decodeFile(f.absolutePath) ?: throw ConversionError("Une image du passage est illisible.")
                val square = StickerArt.square(raw).also { raw.recycle() }
                StickerArt.render(square, s.style, drawVariant).also { square.recycle() }
            }
            try {
                progress(token, base + span * 0.4f, "Sticker ${i + 1}/${plan.size} : compression…")
                val bytes = encodeAnimated(drawn, clip.boomerang, variant == StickerVariant.REVERSE)
                Made(bytes, StickerArt.scaled(drawn.first(), THUMB), if (i == 0) StickerArt.tray(drawn.first()) else ByteArray(0))
            } finally {
                drawn.forEach(Bitmap::recycle)
            }
        }
    }

    /** The lightest good-looking animated WebP: fewer frames per second and lower quality until it fits. */
    private suspend fun encodeAnimated(frames: List<Bitmap>, boomerang: Boolean, reverse: Boolean): ByteArray {
        val cache = HashMap<Pair<Int, Int>, ByteArray>()
        for ((fps, quality) in StickerRules.ANIMATED_LADDER) {
            coroutineContext.ensureActive()
            val order = StickerRules.order(StickerRules.subsample(frames.size, StickerRules.SOURCE_FPS, fps), boomerang, reverse)
            val frameMs = StickerRules.frameMs(fps)
            if (order.size.toLong() * frameMs > StickerRules.MAX_ANIMATED_MS) continue
            // Quick estimate before encoding every frame: the frames already encoded at this quality.
            val bytes = order.map { index -> cache.getOrPut(index to quality) { StickerArt.webp(frames[index], quality) } }
            if (bytes.sumOf { it.size } > StickerRules.ANIMATED_MAX_BYTES) continue
            val file = AnimatedWebp.assemble(bytes, List(order.size) { frameMs }, StickerRules.SIZE, StickerRules.SIZE)
            if (StickerRules.fits(file.size, animated = true)) return file
        }
        throw ConversionError("Ce passage reste trop lourd pour WhatsApp (500 Ko) : choisis un passage plus court ou sans aller-retour.")
    }

    /** Saves the stickers of the shown pack in Galerie › Gulp as .webp files. */
    fun saveToGallery() {
        val done = _state.value.phase as? StickerPhase.Done ?: return
        if (done.saved.isNotEmpty()) return
        scope.launch(Dispatchers.IO) {
            val dir = runCatching { WorkDir.create(context) }.getOrNull() ?: return@launch
            try {
                val saved = done.pack.stickers.mapIndexedNotNull { i, name ->
                    val source = StickerStore.asset(context, done.pack.id, name) ?: return@mapIndexedNotNull null
                    val out = File(dir, "${safe(done.pack.name)} ${i + 1}.webp")
                    source.copyTo(out, overwrite = true)
                    MediaExporter.export(context, out).also { out.delete() }
                }
                _state.update { s -> (s.phase as? StickerPhase.Done)?.takeIf { it.pack.id == done.pack.id }?.let { s.copy(phase = it.copy(saved = saved)) } ?: s }
            } catch (e: Exception) {
                _state.update { s -> (s.phase as? StickerPhase.Done)?.takeIf { it.pack.id == done.pack.id }?.let { s.copy(phase = it.copy(saveFailed = true)) } ?: s }
            } finally {
                WorkDir.delete(context, dir)
            }
        }
    }

    private fun safe(name: String) = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").take(60).trim().ifEmpty { "Sticker" }

    private inline fun update(token: Int, crossinline change: (StickerUiState) -> StickerUiState) {
        _state.update { if (generation.get() == token) change(it) else it }
    }

    private fun progress(token: Int, value: Float, step: String) {
        update(token) { if (it.phase is StickerPhase.Working) it.copy(phase = StickerPhase.Working(value, step)) else it }
    }

    private fun fail(token: Int, message: String, debug: String? = null) {
        update(token) { it.copy(phase = StickerPhase.Failed(message, debug)) }
    }

    companion object {
        private const val PHOTO_MAX_BYTES: Long = 64L * 1024 * 1024
        private const val GIF_MAX_BYTES: Long = 64L * 1024 * 1024
        private const val DECODE_SIDE = 1600
        private const val THUMB = 160
        private const val MAX_TOUCHES = 12
        private val MAX_FRAMES = (StickerRules.DEFAULT_CLIP_MS * StickerRules.SOURCE_FPS / 1000).toInt() + 1

        /** FFmpeg: the clip's frames, [StickerRules.SOURCE_FPS] a second, as square 512 px PNG (transparency kept). */
        fun frameArgs(root: String, input: String, pattern: String, clip: StickerClip): List<String> {
            require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, pattern)) { "Chemins hors du dossier de travail" }
            val seconds = { ms: Long -> "%d.%03d".format(java.util.Locale.ROOT, ms / 1000, ms % 1000) }
            return listOf(
                "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
                "-protocol_whitelist", "file,pipe", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST + ",gif",
                "-ss", seconds(clip.startMs), "-i", input, "-t", seconds(clip.lengthMs),
                "-map", "0:v:0", "-an",
                "-vf", "fps=${StickerRules.SOURCE_FPS},crop=min(iw\\,ih):min(iw\\,ih),scale=${StickerRules.SIZE}:${StickerRules.SIZE}:flags=lanczos,format=rgba",
                "-frames:v", MAX_FRAMES.toString(),
                pattern,
            )
        }
    }
}
