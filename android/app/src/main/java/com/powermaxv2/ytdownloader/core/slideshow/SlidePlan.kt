package com.powermaxv2.ytdownloader.core.slideshow

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import java.util.Locale
import kotlin.math.roundToInt

// The Diaporama's arithmetic and FFmpeg command: how long each photo stays, where the transitions start, and the
// filter graph that turns still pictures (already drawn at the video's size by SlideFrames) into one MP4. Pure: unit
// tested in SlidePlanTest.

/** Shape of the video. Sizes stay under 1280 px so a phone encodes it in seconds, not minutes. */
enum class SlideFormat(val label: String, val detail: String, val width: Int, val height: Int) {
    VERTICAL("Vertical", "9:16 · statut, story", 720, 1280),
    SQUARE("Carré", "1:1 · fil d'actu", 960, 960),
    LANDSCAPE("Paysage", "16:9 · télé, ordi", 1280, 720),
}

/** How a photo of another shape fills the picture. */
enum class SlideFit(val label: String, val detail: String) {
    FILL("Remplir", "Recadrée au centre, sans bandes"),
    FIT("Entière", "Toute la photo, sur son reflet flouté"),
}

/** What happens between two photos. [xfade]: FFmpeg's xfade transition, null for a plain cut. */
enum class SlideTransition(val label: String, val xfade: String?) {
    CUT("Coupe", null),
    FADE("Fondu", "fade"),
    SLIDE("Glisser", "slideleft"),
    /** Ken Burns: each photo slowly zooms in or out (and pans), with a fade between photos. */
    KEN_BURNS("Ken Burns", "fade"),
}

/** Where a photo is drawn in the frame (pixels; may stick out of it when cropped). */
data class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

/** One photo of the show: [durationMs] on screen (transition included). */
data class Slide(val id: Long, val name: String, val durationMs: Long)

object SlidePlan {
    const val FPS = 30
    const val MAX_PHOTOS = 60

    /** Durations a photo can take, in order (a tap on a photo's duration moves to the next one). */
    val DURATIONS_MS = listOf(1_500L, 2_000L, 3_000L, 4_000L, 5_000L, 7_000L)
    const val DEFAULT_DURATION_MS = 3_000L

    /** Length of a transition; shortened to half the shortest photo so a photo is never only a transition. */
    const val TRANSITION_MS = 600L

    /** Black fade at the very start and at the very end. */
    const val FADE_IN_MS = 400L
    const val FADE_OUT_MS = 600L

    /** How much a Ken Burns photo is zoomed at its widest (1.15 = 15 %). */
    const val ZOOM = 0.15

    /** Ken Burns frames are drawn this many times larger, so the slow zoom does not wobble on whole pixels. */
    const val KEN_BURNS_SCALE = 2

    /** The duration after [ms] in [DURATIONS_MS], back to the first after the last. */
    fun nextDuration(ms: Long): Long {
        val i = DURATIONS_MS.indexOf(ms)
        return DURATIONS_MS[if (i < 0) DURATIONS_MS.indexOfFirst { it > ms }.coerceAtLeast(0) else (i + 1) % DURATIONS_MS.size]
    }

    /** "1,5 s", "3 s". */
    fun seconds(ms: Long): String {
        val tenths = (ms / 100.0).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10} s" else "${tenths / 10},${tenths % 10} s"
    }

    /** Frames a photo of [ms] lasts (at least 1). */
    fun frames(ms: Long): Int = (ms * FPS / 1000.0).roundToInt().coerceAtLeast(1)

    /** Frames of one transition for [slides] and [transition]: 0 for a cut or a single photo. */
    fun transitionFrames(slides: List<Slide>, transition: SlideTransition): Int {
        if (transition.xfade == null || slides.size < 2) return 0
        val shortest = slides.minOf { frames(it.durationMs) }
        return minOf(frames(TRANSITION_MS), shortest / 2).coerceAtLeast(1)
    }

    /** Frames of the whole video: photos end to end, minus the overlaps of the transitions. */
    fun totalFrames(slides: List<Slide>, transition: SlideTransition): Int {
        if (slides.isEmpty()) return 0
        return slides.sumOf { frames(it.durationMs) } - (slides.size - 1) * transitionFrames(slides, transition)
    }

    fun totalMs(slides: List<Slide>, transition: SlideTransition): Long = totalFrames(slides, transition) * 1000L / FPS

    /** Frame where the k-th transition (from 1) starts: the photos before it, minus the earlier overlaps. */
    fun offsetFrames(slides: List<Slide>, transition: SlideTransition, k: Int): Int {
        val t = transitionFrames(slides, transition)
        return slides.take(k).sumOf { frames(it.durationMs) } - k * t
    }

    /** Why the show cannot be made, or null when it can. */
    fun refusal(count: Int): String? = when {
        count == 0 -> "Ajoutez au moins une photo."
        count > MAX_PHOTOS -> "$MAX_PHOTOS photos au plus : retirez-en ${count - MAX_PHOTOS}."
        else -> null
    }

    /** Pixel size SlideFrames draws photo frames at (doubled for Ken Burns). */
    fun frameSize(format: SlideFormat, transition: SlideTransition): Pair<Int, Int> {
        val k = if (transition == SlideTransition.KEN_BURNS) KEN_BURNS_SCALE else 1
        return format.width * k to format.height * k
    }

    /**
     * Where a [srcW] × [srcH] photo goes in a [dstW] × [dstH] frame: scaled to cover it, centred (FILL, the sides
     * cut off), or scaled to fit inside it, centred (FIT, the rest shows the blurred backdrop).
     */
    fun placement(srcW: Int, srcH: Int, dstW: Int, dstH: Int, fit: SlideFit): Placement {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0) { "Taille vide" }
        val sx = dstW.toFloat() / srcW
        val sy = dstH.toFloat() / srcH
        val scale = if (fit == SlideFit.FILL) maxOf(sx, sy) else minOf(sx, sy)
        val w = srcW * scale
        val h = srcH * scale
        return Placement((dstW - w) / 2f, (dstH - h) / 2f, w, h)
    }

    /** "Diaporama 13-10-2026 14h05.mp4". */
    fun outputName(day: Int, month: Int, year: Int, hour: Int, minute: Int): String =
        String.format(Locale.ROOT, "Diaporama %02d-%02d-%04d %02dh%02d.mp4", day, month, year, hour, minute)

    /**
     * FFmpeg arguments turning the [photos] (JPEG files drawn by SlideFrames at [frameSize], one per slide, in
     * order) and an optional [music] file into [output], an H.264 / AAC MP4. All paths are absolute and inside
     * [root] (the tools' work dir), like every Gulp FFmpeg call. The music loops when it is shorter than the show
     * and fades out at the end.
     */
    fun args(
        root: String,
        photos: List<String>,
        slides: List<Slide>,
        music: String?,
        output: String,
        format: SlideFormat,
        transition: SlideTransition,
    ): List<String> {
        require(photos.isNotEmpty() && photos.size == slides.size) { "Une photo par diapositive" }
        require((photos + listOfNotNull(music, output)).all { FfmpegArgs.isInside(root, it) }) { "Chemins hors du dossier de travail" }
        require(output !in photos && output != music) { "Entrée et sortie identiques" }
        val kenBurns = transition == SlideTransition.KEN_BURNS
        val args = mutableListOf("-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1")
        // Whitelists are options of each input: repeated before every -i.
        fun input(vararg options: String, path: String) {
            args += listOf("-protocol_whitelist", "file", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST)
            args += options
            args += listOf("-i", path)
        }
        photos.forEachIndexed { i, path ->
            if (kenBurns) {
                // One still frame: zoompan turns it into the whole photo's frames.
                input("-f", "image2", "-pattern_type", "none", "-framerate", "$FPS", path = path)
            } else {
                // Looped a frame longer than needed; trim cuts it to the exact frame count.
                input("-f", "image2", "-pattern_type", "none", "-loop", "1", "-framerate", "$FPS", "-t", secs((frames(slides[i].durationMs) + 1) * 1000L / FPS + 1), path = path)
            }
        }
        if (music != null) input("-stream_loop", "-1", path = music)
        args += listOf("-filter_complex", filterGraph(slides, format, transition, music != null))
        args += listOf("-map", "[vout]")
        if (music != null) args += listOf("-map", "[aout]")
        args += listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "21", "-pix_fmt", "yuv420p", "-r", "$FPS")
        if (music != null) args += listOf("-c:a", "aac", "-b:a", "160k")
        args += listOf("-t", secs(totalMs(slides, transition)), "-movflags", "+faststart", output)
        return args
    }

    /** The -filter_complex graph: one stream per photo, joined by cuts or xfades, black fades at both ends, music. */
    fun filterGraph(slides: List<Slide>, format: SlideFormat, transition: SlideTransition, music: Boolean): String {
        val w = format.width
        val h = format.height
        val parts = mutableListOf<String>()
        val common = "setsar=1,format=yuv420p,settb=AVTB"
        slides.forEachIndexed { i, slide ->
            val n = frames(slide.durationMs)
            parts += if (transition == SlideTransition.KEN_BURNS) {
                "[$i:v]${zoompan(i, n, w, h)},$common[v$i]"
            } else {
                "[$i:v]scale=$w:$h,fps=$FPS,trim=end_frame=$n,setpts=PTS-STARTPTS,$common[v$i]"
            }
        }
        val totalFrames = totalFrames(slides, transition)
        val total = totalFrames * 1000L / FPS
        val fadeOut = minOf(FADE_OUT_MS, total / 3)
        val fadeIn = minOf(FADE_IN_MS, total / 3)
        val fades = "fade=t=in:st=0:d=${secs(fadeIn)},fade=t=out:st=${secs(total - fadeOut)}:d=${secs(fadeOut)}"
        val xfade = transition.xfade
        when {
            slides.size == 1 -> parts += "[v0]$fades[vout]"
            xfade == null -> {
                val inputs = slides.indices.joinToString("") { "[v$it]" }
                parts += "${inputs}concat=n=${slides.size}:v=1:a=0,$fades[vout]"
            }
            else -> {
                val t = transitionFrames(slides, transition)
                var last = "v0"
                for (k in 1 until slides.size) {
                    val out = "x$k"
                    val offset = offsetFrames(slides, transition, k)
                    parts += "[$last][v$k]xfade=transition=$xfade:duration=${frameSecs(t)}:offset=${frameSecs(offset)}[$out]"
                    last = out
                }
                parts += "[$last]$fades[vout]"
            }
        }
        if (music) {
            val m = slides.size
            val musicOut = minOf(1_500L, total / 2)
            parts += "[$m:a]atrim=end=${secs(total)},asetpts=PTS-STARTPTS," +
                "afade=t=in:st=0:d=${secs(minOf(500L, total / 4))},afade=t=out:st=${secs(total - musicOut)}:d=${secs(musicOut)}[aout]"
        }
        return parts.joinToString(";")
    }

    /**
     * Ken Burns on photo [i] of [n] frames: even photos zoom in on the centre, odd ones zoom out while sliding
     * sideways, so two photos in a row never move the same way.
     */
    fun zoompan(i: Int, n: Int, w: Int, h: Int): String {
        val z = String.format(Locale.ROOT, "%.2f", ZOOM)
        val last = (n - 1).coerceAtLeast(1)
        return if (i % 2 == 0) {
            "zoompan=z=1+$z*on/$last:x=(iw-iw/zoom)/2:y=(ih-ih/zoom)/2:d=$n:s=${w}x$h:fps=$FPS"
        } else {
            "zoompan=z=1+$z-$z*on/$last:x=(iw-iw/zoom)*on/$last:y=(ih-ih/zoom)/2:d=$n:s=${w}x$h:fps=$FPS"
        }
    }

    /** "12.345" (FFmpeg seconds). */
    fun secs(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d.%03d", v / 1_000, v % 1_000)
    }

    /** Frames as exact seconds ("1.533333"): xfade offsets must fall on frames. */
    fun frameSecs(frames: Int): String = String.format(Locale.ROOT, "%.6f", frames.toDouble() / FPS)
}
