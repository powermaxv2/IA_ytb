package com.powermaxv2.ytdownloader.core.speed

import com.powermaxv2.ytdownloader.core.convert.FfmpegArgs
import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The Vitesse vidéo's arithmetic: the speeds offered, the length of the result, the sound filters and the exact
// FFmpeg command. Pure (no Android types): unit tested in SpeedPlanTest.

/** What happens to the sound of the video. */
enum class SpeedSound(val label: String, val detail: String) {
    /** The sound follows the speed but keeps its pitch (atempo): voices stay natural. */
    NATURAL("Voix normale", "le son suit, même ton"),

    /** The sound is played faster or slower like a tape (asetrate): higher when sped up, deeper when slowed. */
    TAPE("Voix déformée", "aiguë ou grave"),

    /** No sound at all. */
    MUTE("Sans le son", "vidéo muette"),
}

object SpeedPlan {
    /** The speeds offered, slowest first (1 = unchanged is not one of them). */
    val SPEEDS: List<Double> = listOf(0.25, 0.5, 0.75, 1.25, 1.5, 2.0, 3.0, 4.0, 8.0)

    const val DEFAULT_SPEED = 0.5
    const val SLOWEST = 0.25
    const val FASTEST = 8.0

    /** Frame rate written when the source's is unknown, and the highest one written. */
    const val DEFAULT_FPS = 30.0
    const val MAX_FPS = 60.0

    /** Sample rate assumed when the source's is unknown. */
    const val DEFAULT_SAMPLE_RATE = 48_000

    /** One atempo filter only takes 0.5 to 2 on older FFmpeg builds: slower or faster is a chain of them. */
    private const val ATEMPO_MIN = 0.5
    private const val ATEMPO_MAX = 2.0

    fun isSlow(speed: Double): Boolean = speed < 1.0

    /** The offered speed closest to [speed] (a saved or dragged value). */
    fun nearest(speed: Double): Double = SPEEDS.minByOrNull { abs(ln(it) - ln(speed.coerceIn(SLOWEST, FASTEST))) } ?: DEFAULT_SPEED

    /** Where [speed] sits on the dial, 0 (×0.25) to 1 (×8): a log scale, so ×0.5 and ×2 are as far from ×1. */
    fun dial(speed: Double): Float = ((ln(speed.coerceIn(SLOWEST, FASTEST)) - ln(SLOWEST)) / (ln(FASTEST) - ln(SLOWEST))).toFloat()

    /** The speed at [position] (0..1) on the dial, snapped to the offered ones. */
    fun atDial(position: Float): Double {
        val p = position.coerceIn(0f, 1f).toDouble()
        return nearest(kotlin.math.exp(ln(SLOWEST) + p * (ln(FASTEST) - ln(SLOWEST))))
    }

    /** "×0,25", "×2", "×1,5". */
    fun label(speed: Double): String = "×" + number(speed)

    /** "0,25", "2", "1,5" (French decimals, no trailing zero). */
    fun number(speed: Double): String {
        val hundredths = (speed * 100).roundToLong()
        val whole = hundredths / 100
        val rest = hundredths % 100
        return when {
            rest == 0L -> "$whole"
            rest % 10 == 0L -> "$whole,${rest / 10}"
            else -> String.format(Locale.ROOT, "%d,%02d", whole, rest)
        }
    }

    /** "Ralenti" or "Accéléré". */
    fun mood(speed: Double): String = if (isSlow(speed)) "Ralenti" else "Accéléré"

    /** Length of the result for a source of [durationMs]. */
    fun outputMs(durationMs: Long, speed: Double): Long = (durationMs.coerceAtLeast(0L) / speed).roundToLong()

    /** atempo factors, each within 0.5..2, whose product is [speed]. */
    fun atempoChain(speed: Double): List<Double> {
        val factors = ArrayList<Double>()
        var rest = speed
        while (rest < ATEMPO_MIN - 1e-9) {
            factors += ATEMPO_MIN
            rest /= ATEMPO_MIN
        }
        while (rest > ATEMPO_MAX + 1e-9) {
            factors += ATEMPO_MAX
            rest /= ATEMPO_MAX
        }
        if (abs(rest - 1.0) > 1e-9 || factors.isEmpty()) factors += rest
        return factors
    }

    /** The audio filter for [sound] at [speed], or null when the result is muted. */
    fun audioFilter(speed: Double, sound: SpeedSound, sampleRate: Int?): String? {
        val rate = sampleRate?.takeIf { it in 8_000..192_000 } ?: DEFAULT_SAMPLE_RATE
        return when (sound) {
            SpeedSound.NATURAL -> atempoChain(speed).joinToString(",") { "atempo=" + decimal(it) }
            SpeedSound.TAPE -> "asetrate=${(rate * speed).roundToInt()},aresample=$rate"
            SpeedSound.MUTE -> null
        }
    }

    /**
     * Frame rate of the result: the source's (unknown → 30), at most 60. Sped up, the frames in excess are dropped;
     * slowed down, they are repeated (a phone's 120 / 240 fps clip slowed down keeps real frames up to 60 fps).
     */
    fun outputFps(sourceFps: Double?): Double = (sourceFps?.takeIf { it in 1.0..1_000.0 } ?: DEFAULT_FPS).coerceAtMost(MAX_FPS)

    /** setpts (new timing), fps (steady frame rate), even sides for H.264. */
    fun videoFilter(speed: Double, sourceFps: Double?): String =
        "setpts=PTS/${decimal(speed)},fps=${decimal(outputFps(sourceFps))},scale=trunc(iw/2)*2:trunc(ih/2)*2"

    /**
     * FFmpeg arguments writing [input] at [speed] into [output] (MP4, H.264 + AAC). Both paths are absolute and
     * inside [root] (the tools' work dir), like the Convertisseur's: FFmpeg never touches anything else.
     */
    fun args(
        root: String,
        input: String,
        output: String,
        speed: Double,
        sound: SpeedSound,
        hasAudio: Boolean,
        sourceFps: Double?,
        sampleRate: Int?,
    ): List<String> {
        require(FfmpegArgs.isInside(root, input) && FfmpegArgs.isInside(root, output)) { "Chemins hors du dossier de travail" }
        require(input != output) { "Entrée et sortie identiques" }
        require(speed in SLOWEST..FASTEST && speed != 1.0) { "Vitesse hors limites" }
        val head = listOf(
            "-nostdin", "-hide_banner", "-y", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe", "-format_whitelist", FfmpegArgs.FORMAT_WHITELIST,
            "-i", input,
        )
        val video = listOf(
            "-map", "0:v:0",
            "-vf", videoFilter(speed, sourceFps),
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
        )
        val filter = if (hasAudio) audioFilter(speed, sound, sampleRate) else null
        val audio = if (filter == null) listOf("-an") else listOf("-map", "0:a:0", "-af", filter, "-c:a", "aac", "-b:a", "192k")
        return head + video + audio + listOf("-movflags", "+faststart") + output
    }

    /** "concert (ralenti ×0,5).mp4", "concert (accéléré ×4).mp4". */
    fun outputName(sourceName: String, speed: Double): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(sourceName, fallback = "video")).ifBlank { "video" }
        val suffix = " (${mood(speed).lowercase(Locale.ROOT)} ${label(speed)}).mp4"
        return base.take((SafeNames.MAX_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
    }

    /** "0:50", "12:03", "1:02:03". */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 500) / 1_000
        val h = total / 3_600
        val m = (total / 60) % 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** "0.5", "2", "1.33333" (FFmpeg numbers). */
    fun decimal(value: Double): String {
        val text = String.format(Locale.ROOT, "%.5f", value).trimEnd('0').trimEnd('.')
        return text.ifEmpty { "0" }
    }
}
