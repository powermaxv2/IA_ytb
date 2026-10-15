package com.powermaxv2.ytdownloader.core.level

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

// Niveau à bulle: everything that is pure maths (no Android), so it is unit tested. The screen feeds it the
// "up" vector the accelerometer / gravity sensor gives (m/s², the phone's own axes) and draws what comes out.

/** The "up" vector measured by the phone, in its axes: x to the right of the screen, y to its top, z out of it. */
data class Gravity(val x: Float, val y: Float, val z: Float) {
    val norm: Float get() = sqrt(x * x + y * y + z * z)
}

/** How the phone is used. [AUTO] picks [FLAT] or [EDGE] from how it is held. */
enum class LevelMode { AUTO, FLAT, EDGE }

/** What the screen shows for one measure. */
sealed interface LevelReading {
    val level: Boolean

    /**
     * Phone lying on its back (a shelf, a table): [tiltX] > 0 = right side of the screen higher, [tiltY] > 0 = top
     * of the screen higher, in degrees. The bubble goes to the high side, like in a real vial.
     */
    data class Flat(val tiltX: Float, val tiltY: Float, override val level: Boolean) : LevelReading {
        /** Total slope, whatever the direction. */
        val tilt: Float get() = hypot(tiltX, tiltY)
    }

    /**
     * Phone standing on one of its edges (against a frame, on a shelf): [angle] > 0 = the right end of the edge
     * that touches is higher, in degrees. [snap] = which edge is down, as the turn of the screen (0, 90, 180, 270).
     */
    data class Edge(val angle: Float, val snap: Int, override val level: Boolean) : LevelReading
}

/** Zero offsets, set by "Calibrer" (a phone with a camera bump does not lie flat). */
data class Calibration(val flatX: Float = 0f, val flatY: Float = 0f, val edge: Float = 0f) {
    val isSet: Boolean get() = flatX != 0f || flatY != 0f || edge != 0f

    fun encode(): String = "$flatX;$flatY;$edge"

    companion object {
        /** Back from [encode]; anything unreadable gives no offset. */
        fun decode(text: String?): Calibration {
            val parts = text?.split(';')?.mapNotNull { it.toFloatOrNull() } ?: return Calibration()
            if (parts.size != 3 || parts.any { it.isNaN() || abs(it) > LevelMath.MAX_CALIBRATION }) return Calibration()
            return Calibration(parts[0], parts[1], parts[2])
        }
    }
}

object LevelMath {
    /** Within this many degrees the phone says "droit". */
    const val TOLERANCE = 0.5f

    /** A zero further than this is refused (the phone was not lying on anything flat). */
    const val MAX_CALIBRATION = 6f

    /** Below this, the sensor gives nothing usable (free fall, broken values). */
    private const val MIN_NORM = 2f

    /** Auto mode: the phone counts as lying flat under [FLAT_ENTER]° from flat, and stands again past [FLAT_LEAVE]°. */
    const val FLAT_ENTER = 40f
    const val FLAT_LEAVE = 50f

    /**
     * [g] in the axes of the screen as it is drawn now. Android turns the picture with the phone (auto-rotate) but
     * the sensor keeps the phone's axes: [rotation] is the turn of the display in degrees (0, 90, 180, 270).
     */
    fun toScreen(g: Gravity, rotation: Int): Gravity = when (((rotation % 360) + 360) % 360) {
        90 -> Gravity(-g.y, g.x, g.z)
        180 -> Gravity(-g.x, -g.y, g.z)
        270 -> Gravity(g.y, -g.x, g.z)
        else -> g
    }

    /** Degrees between the phone's back and the horizontal (0 = lying flat, 90 = standing). */
    fun angleFromFlat(g: Gravity): Float {
        val n = g.norm
        if (n < MIN_NORM) return 0f
        return deg(atan2(hypot(g.x, g.y), abs(g.z)))
    }

    /** The mode to use for [g]; [previous] gives a margin so the screen does not flicker between both at 45°. */
    fun resolve(mode: LevelMode, g: Gravity, previous: LevelMode? = null): LevelMode {
        if (mode != LevelMode.AUTO) return mode
        val a = angleFromFlat(g)
        return when (previous) {
            LevelMode.FLAT -> if (a > FLAT_LEAVE) LevelMode.EDGE else LevelMode.FLAT
            LevelMode.EDGE -> if (a < FLAT_ENTER) LevelMode.FLAT else LevelMode.EDGE
            else -> if (a < 45f) LevelMode.FLAT else LevelMode.EDGE
        }
    }

    /** The reading for [g] (screen axes) in [mode] ([LevelMode.AUTO] must be resolved first), minus [calibration]. */
    fun read(g: Gravity, mode: LevelMode, calibration: Calibration = Calibration()): LevelReading? {
        if (g.norm < MIN_NORM || g.x.isNaN() || g.y.isNaN() || g.z.isNaN()) return null
        return when (mode) {
            LevelMode.EDGE -> {
                val raw = deg(atan2(g.x, g.y))
                val snap = snap(raw)
                // atan2(x, y) grows as the right side of the screen rises.
                val angle = wrap(raw - snap) - calibration.edge
                LevelReading.Edge(angle, snap, abs(angle) <= TOLERANCE)
            }
            else -> {
                val z = abs(g.z).coerceAtLeast(0.0001f)
                val tx = deg(atan2(g.x, z)) - calibration.flatX
                val ty = deg(atan2(g.y, z)) - calibration.flatY
                LevelReading.Flat(tx, ty, hypot(tx, ty) <= TOLERANCE)
            }
        }
    }

    /** The new zero, so that [g] reads 0 in [mode]; null when the phone is too far from straight to be a zero. */
    fun calibrate(g: Gravity, mode: LevelMode, current: Calibration): Calibration? {
        val raw = read(g, mode) ?: return null
        return when (raw) {
            is LevelReading.Flat -> {
                if (abs(raw.tiltX) > MAX_CALIBRATION || abs(raw.tiltY) > MAX_CALIBRATION) null
                else current.copy(flatX = raw.tiltX, flatY = raw.tiltY)
            }
            is LevelReading.Edge -> {
                if (abs(raw.angle) > MAX_CALIBRATION) null else current.copy(edge = raw.angle)
            }
        }
    }

    /** Nearest quarter turn of [angle] (0, 90, 180, 270). */
    fun snap(angle: Float): Int = ((((angle / 90f).roundToInt() * 90) % 360) + 360) % 360

    /** [angle] brought back between -180 and 180. */
    fun wrap(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    /** Height difference over one metre for a slope of [degrees], in millimetres (what a DIY person measures). */
    fun mmPerMetre(degrees: Float): Int = (tan(abs(degrees) * PI / 180.0) * 1000.0).roundToInt()

    /** "0,4°" (French decimal comma, always one decimal, no minus sign). */
    fun degrees(value: Float): String {
        val tenths = (abs(value) * 10f).roundToInt()
        return "${tenths / 10},${tenths % 10}°"
    }

    /** What to do, in words, for [reading]. */
    fun advice(reading: LevelReading): String = when (reading) {
        is LevelReading.Edge -> when {
            reading.level -> "C'est droit"
            reading.angle > 0 -> "Le côté droit est trop haut"
            else -> "Le côté gauche est trop haut"
        }
        is LevelReading.Flat -> when {
            reading.level -> "C'est de niveau"
            abs(reading.tiltX) >= abs(reading.tiltY) ->
                if (reading.tiltX > 0) "Ça penche vers la gauche" else "Ça penche vers la droite"
            else -> if (reading.tiltY > 0) "Ça penche vers toi" else "Ça penche vers le haut de l'écran"
        }
    }

    /** Where the bubble sits, as a share of the vial's radius (-1..1 on each axis), for a reading of [tilt]°. */
    fun bubbleShare(tilt: Float, range: Float = 10f): Float = (tilt / range).coerceIn(-1f, 1f)

    private fun deg(rad: Float): Float = (rad * 180.0 / PI).toFloat()
}

/**
 * Smooths the sensor (an accelerometer shakes): an exponential average, quicker when the phone really moves so
 * the bubble does not lag behind.
 */
class LowPass(private val calm: Float = 0.12f, private val quick: Float = 0.5f) {
    private var last: Gravity? = null

    fun push(g: Gravity): Gravity {
        val prev = last ?: return g.also { last = it }
        val jump = hypot(hypot(g.x - prev.x, g.y - prev.y), g.z - prev.z)
        val a = if (jump > 1.5f) quick else calm
        val next = Gravity(prev.x + a * (g.x - prev.x), prev.y + a * (g.y - prev.y), prev.z + a * (g.z - prev.z))
        last = next
        return next
    }

    fun reset() {
        last = null
    }
}
