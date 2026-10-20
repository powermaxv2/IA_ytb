package com.powermaxv2.ytdownloader.core.blur

import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.profilefit.ProfileFit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Flouter une photo: the user hides parts of a photo (a face, a plate, a number, an address) before sharing it.
// Zones are drawn with the finger (or found by the face detector) and kept in 0..1 of the photo, so the same
// zones drive the small live preview and the full-size save. This file is the pure part (geometry and the pixel
// work on plain ARGB arrays): tested on the JVM.

/** How a zone is hidden. */
enum class BlurEffect(val label: String) {
    /** Soft blur: pixelated first, then blurred, so no detail survives. */
    BLUR("Flou"),

    /** Big squares of the zone's colours. */
    PIXELS("Pixels"),

    /** Plain black: nothing of the zone is left at all. */
    BAR("Noir"),
}

/**
 * How much is wiped. The cell (square of the pixels, or blur radius) is the larger of a share of the photo's long
 * side and a part of the zone's short side: a zone is at most [cells] cells across, whatever its size.
 */
enum class BlurStrength(val label: String, val photoShare: Float, val cells: Int) {
    LIGHT("Léger", 1f / 90f, 9),
    MEDIUM("Moyen", 1f / 60f, 6),
    STRONG("Fort", 1f / 40f, 4),
}

/** Shape of a zone. */
enum class ZoneShape(val label: String) {
    RECT("Rectangle"),
    OVAL("Ovale"),
}

/** One hidden part of the photo, in 0..1 of its width and height. */
data class BlurZone(val id: Int, val x: Float, val y: Float, val w: Float, val h: Float, val shape: ZoneShape = ZoneShape.RECT) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val centerX: Float get() = x + w / 2f
    val centerY: Float get() = y + h / 2f

    fun contains(px: Float, py: Float, slop: Float = 0f): Boolean =
        px >= x - slop && px <= right + slop && py >= y - slop && py <= bottom + slop
}

data class BlurOptions(
    val effect: BlurEffect = BlurEffect.BLUR,
    val strength: BlurStrength = BlurStrength.STRONG,
    /** Shape of the next zone drawn by hand (found faces are always ovals). */
    val shape: ZoneShape = ZoneShape.RECT,
)

/** A zone in pixels of a picture: [left, right) × [top, bottom). */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object BlurPlan {
    /** Smallest side of a zone, in 0..1 of the photo (a tap-sized zone, still visible). */
    const val MIN_SIDE = 0.04f

    /** Zones kept at most on one photo. */
    const val MAX_ZONES = 40

    /** Colour of [BlurEffect.BAR]: near black, like a marker pen. */
    const val BAR_COLOR: Int = 0xFF111214.toInt()

    /** A zone from the two corners of a finger's drag (any direction), kept inside the photo and not too small. */
    fun fromDrag(id: Int, x0: Float, y0: Float, x1: Float, y1: Float, shape: ZoneShape): BlurZone {
        val left = min(x0, x1).coerceIn(0f, 1f)
        val top = min(y0, y1).coerceIn(0f, 1f)
        val right = max(x0, x1).coerceIn(0f, 1f)
        val bottom = max(y0, y1).coerceIn(0f, 1f)
        return fit(BlurZone(id, left, top, right - left, bottom - top, shape))
    }

    /** [zone] grown to [MIN_SIDE] around its centre if needed, then pushed back inside the photo. */
    fun fit(zone: BlurZone): BlurZone {
        val w = zone.w.coerceIn(MIN_SIDE, 1f)
        val h = zone.h.coerceIn(MIN_SIDE, 1f)
        val x = (zone.centerX - w / 2f).coerceIn(0f, 1f - w)
        val y = (zone.centerY - h / 2f).coerceIn(0f, 1f - h)
        return zone.copy(x = x, y = y, w = w, h = h)
    }

    /** [zone] moved by ([dx], [dy]), stopped at the photo's edges. */
    fun move(zone: BlurZone, dx: Float, dy: Float): BlurZone =
        zone.copy(x = (zone.x + dx).coerceIn(0f, 1f - zone.w), y = (zone.y + dy).coerceIn(0f, 1f - zone.h))

    /** [zone] with its bottom-right corner pulled by ([dw], [dh]); its top-left corner stays. */
    fun resize(zone: BlurZone, dw: Float, dh: Float): BlurZone =
        zone.copy(w = (zone.w + dw).coerceIn(MIN_SIDE, 1f - zone.x), h = (zone.h + dh).coerceIn(MIN_SIDE, 1f - zone.y))

    /** The zone under the finger (the last drawn wins, it is on top), or null. */
    fun hit(zones: List<BlurZone>, px: Float, py: Float, slop: Float = 0f): BlurZone? = zones.lastOrNull { it.contains(px, py, slop) }

    /**
     * A face found by the detector, as an oval zone: [eyes] is the distance between the eyes, ([midX], [midY]) the
     * point between them, all in pixels of a [width] × [height] picture. A face is about 2.6 eye distances wide
     * and 3.4 tall, its centre a little under the eyes.
     */
    fun faceZone(id: Int, midX: Float, midY: Float, eyes: Float, width: Int, height: Int): BlurZone {
        require(width > 0 && height > 0)
        val w = eyes * 2.6f / width
        val h = eyes * 3.4f / height
        val cx = midX / width
        val cy = (midY + eyes * 0.35f) / height
        return fit(BlurZone(id, cx - w / 2f, cy - h / 2f, w, h, ZoneShape.OVAL))
    }

    /** The [found] faces that are not already covered (their centre inside a zone the user has), with fresh ids. */
    fun newFaces(existing: List<BlurZone>, found: List<BlurZone>, nextId: Int): List<BlurZone> {
        val kept = mutableListOf<BlurZone>()
        for (face in found) {
            if ((existing + kept).any { it.contains(face.centerX, face.centerY) }) continue
            kept += face.copy(id = nextId + kept.size)
        }
        return kept
    }

    /** Where [zone] falls in a [width] × [height] picture; at least one pixel. */
    fun box(zone: BlurZone, width: Int, height: Int): PixelBox {
        val left = (zone.x * width).roundToInt().coerceIn(0, width - 1)
        val top = (zone.y * height).roundToInt().coerceIn(0, height - 1)
        val right = (zone.right * width).roundToInt().coerceIn(left + 1, width)
        val bottom = (zone.bottom * height).roundToInt().coerceIn(top + 1, height)
        return PixelBox(left, top, right, bottom)
    }

    /** Side of a pixel square (or blur radius) for a [box] of a [width] × [height] picture. */
    fun cell(strength: BlurStrength, width: Int, height: Int, box: PixelBox): Int {
        val byPhoto = max(width, height) * strength.photoShare
        val byZone = min(box.width, box.height).toFloat() / strength.cells
        return max(byPhoto, byZone).roundToInt().coerceAtLeast(3)
    }

    /**
     * Hides every zone of a [width] × [height] ARGB picture, in place. Zones are done in order, each one on the
     * pixels left by the ones before (an overlap is hidden twice, never undone).
     */
    fun apply(pixels: IntArray, width: Int, height: Int, zones: List<BlurZone>, effect: BlurEffect, strength: BlurStrength) {
        require(pixels.size == width * height)
        for (zone in zones) {
            val b = box(zone, width, height)
            val region = IntArray(b.width * b.height)
            for (row in 0 until b.height) System.arraycopy(pixels, (b.top + row) * width + b.left, region, row * b.width, b.width)
            hideRegion(region, b.width, b.height, effect, cell(strength, width, height, b), zone.shape)
            for (row in 0 until b.height) System.arraycopy(region, row * b.width, pixels, (b.top + row) * width + b.left, b.width)
        }
    }

    /** Hides a whole [w] × [h] region (an oval keeps its corners as they were). */
    fun hideRegion(region: IntArray, w: Int, h: Int, effect: BlurEffect, cell: Int, shape: ZoneShape) {
        require(region.size == w * h)
        val hidden = when (effect) {
            BlurEffect.BAR -> IntArray(region.size) { BAR_COLOR }
            BlurEffect.PIXELS -> pixelate(region, w, h, cell)
            // The squares first: whatever survives the blur is already a mean of many pixels.
            BlurEffect.BLUR -> ProfileFit.blur(pixelate(region, w, h, max(2, cell / 2)), w, h, cell)
        }
        if (shape == ZoneShape.RECT) {
            hidden.copyInto(region)
            return
        }
        val rx = w / 2f
        val ry = h / 2f
        for (y in 0 until h) {
            val dy = (y + 0.5f - ry) / ry
            for (x in 0 until w) {
                val dx = (x + 0.5f - rx) / rx
                if (dx * dx + dy * dy <= 1f) region[y * w + x] = hidden[y * w + x]
            }
        }
    }

    /** Each [cell] × [cell] square of a [w] × [h] picture filled with its mean colour (alpha kept as its mean). */
    fun pixelate(pixels: IntArray, w: Int, h: Int, cell: Int): IntArray {
        require(pixels.size == w * h && cell > 0)
        val out = IntArray(pixels.size)
        var top = 0
        while (top < h) {
            val bottom = min(top + cell, h)
            var left = 0
            while (left < w) {
                val right = min(left + cell, w)
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (y in top until bottom) for (x in left until right) {
                    val p = pixels[y * w + x]
                    a += p ushr 24
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                }
                val n = ((bottom - top) * (right - left)).toLong()
                val mean = ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
                for (y in top until bottom) out.fill(mean, y * w + left, y * w + right)
                left = right
            }
            top = bottom
        }
        return out
    }

    /** "0 zone" is never shown: "Aucune zone", "1 zone cachée", "3 zones cachées". */
    fun zonesText(count: Int): String = when (count) {
        0 -> "Aucune zone"
        1 -> "1 zone cachée"
        else -> "$count zones cachées"
    }

    /** Name of the saved photo: "vacances.heic" → "vacances-floute.jpg". */
    fun outputName(source: String): String {
        val base = SafeNames.baseOf(SafeNames.sanitize(source, "photo")).ifBlank { "photo" }
        return SafeNames.sanitize("$base-floute.jpg", "photo-floute.jpg")
    }
}
