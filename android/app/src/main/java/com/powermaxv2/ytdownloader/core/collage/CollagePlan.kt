package com.powermaxv2.ytdownloader.core.collage

import com.powermaxv2.ytdownloader.core.files.SafeNames
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// Collage photos: 2 to 9 photos put together in one picture, ready to send (a grid, one big photo and the others
// around it, a strip, or a "pêle-mêle" of tilted prints). This file is the pure part: where each photo goes, how it
// is cropped to fill its cell, the output size and name. Tested on the JVM; drawn by CollageRender (saved file) and
// by the live preview of the screen (same cells, animated).

/** How the photos are laid out. */
enum class CollageLayout(val label: String) {
    /** Rows of equal height, as even as possible; the whole picture is filled. */
    GRID("Grille"),

    /** The first photo big, the others in a grid beside it (below it on a tall picture). */
    HERO("Une grande"),

    /** One photo after the other: a column on a tall or square picture, a row on a wide one. */
    STRIP("Bande"),

    /** Tilted prints with a white border, scattered over the background like on a table. */
    SCATTER("Pêle-mêle"),
}

/** Shape of the picture. */
enum class CollageFormat(val label: String, val ratioW: Int, val ratioH: Int) {
    SQUARE("Carré", 1, 1),
    PORTRAIT("Portrait 4:5", 4, 5),
    STORY("Story 9:16", 9, 16),
    LANDSCAPE("Paysage 16:9", 16, 9),
    ;

    val tall: Boolean get() = ratioH >= ratioW
}

/** Space between the photos and around them, in thousandths of the picture's short side. */
enum class CollageSpacing(val label: String, val permille: Int) {
    NONE("Collées", 0),
    THIN("Fin", 12),
    WIDE("Large", 30),
}

/** What shows between the photos. */
enum class CollageBackground(val label: String) {
    WHITE("Blanc"),
    BLACK("Noir"),
    CREAM("Crème"),
    COLOR("Leurs couleurs"),
}

data class CollageOptions(
    val layout: CollageLayout = CollageLayout.GRID,
    val format: CollageFormat = CollageFormat.SQUARE,
    val spacing: CollageSpacing = CollageSpacing.THIN,
    val background: CollageBackground = CollageBackground.WHITE,
    val rounded: Boolean = false,
    /** Changes the tilt and the jitter of the pêle-mêle prints ("Mélanger"). */
    val seed: Int = 7,
)

/** Where photo number [index] is drawn: a rectangle of the picture, in pixels, turned by [rotation] degrees. */
data class CollageCell(
    val index: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** The part of a source photo (in its own pixels) that fills a cell: centred, as big as the cell's shape allows. */
data class CollageCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object Collage {
    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 9

    /** Long side of the saved picture (a 1:1 collage is 2048 × 2048). */
    const val OUTPUT_LONG_SIDE = 2048

    /** Pêle-mêle prints: white border (share of the print's short side) and the wider bottom band. */
    const val PRINT_BORDER = 0.05f
    const val PRINT_BOTTOM = 0.16f

    /** Corner radius when [CollageOptions.rounded], in share of the picture's short side. */
    const val ROUND_RADIUS = 0.028f

    /** Width and height of the picture for [format], its long side being [longSide]. */
    fun size(format: CollageFormat, longSide: Int = OUTPUT_LONG_SIDE): Pair<Int, Int> {
        require(longSide > 0)
        val long = max(format.ratioW, format.ratioH).toFloat()
        val w = (longSide * format.ratioW / long).roundToInt()
        val h = (longSide * format.ratioH / long).roundToInt()
        return w to h
    }

    /** Space between photos, in pixels, for a [width] × [height] picture. */
    fun gap(spacing: CollageSpacing, width: Int, height: Int): Float = min(width, height) * spacing.permille / 1000f

    /** Corner radius of each photo, in pixels (0 when not rounded). */
    fun radius(options: CollageOptions, width: Int, height: Int): Float =
        if (options.rounded) min(width, height) * ROUND_RADIUS else 0f

    /**
     * How many photos go on each row of a grid of [count] photos in a [width] × [height] box: about as many columns
     * as the box's shape asks for, rows as even as possible, and the shorter rows first (a 5-photo grid is 2 then 3).
     */
    fun rows(count: Int, width: Float, height: Float): List<Int> {
        require(count > 0 && width > 0f && height > 0f)
        val columns = ceil(sqrt(count * width / height.toDouble())).toInt().coerceIn(1, count)
        val rowCount = ceil(count / columns.toDouble()).toInt()
        val small = count / rowCount
        val bigRows = count % rowCount
        return List(rowCount) { row -> if (row >= rowCount - bigRows) small + 1 else small }
    }

    /** Every photo's cell for [count] photos on a [width] × [height] picture laid out with [options]. */
    fun cells(count: Int, options: CollageOptions, width: Int, height: Int): List<CollageCell> {
        require(count > 0 && width > 0 && height > 0)
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = gap(options.spacing, width, height)
        return when (options.layout) {
            CollageLayout.GRID -> grid(0, count, gap, gap, w - 2 * gap, h - 2 * gap, gap)
            CollageLayout.STRIP -> strip(count, gap, w, h, vertical = h >= w)
            CollageLayout.HERO -> hero(count, gap, w, h)
            CollageLayout.SCATTER -> scatter(count, w, h, options.seed)
        }
    }

    /** [count] cells from number [first], in rows filling the box ([left], [top], [width], [height]). */
    private fun grid(first: Int, count: Int, left: Float, top: Float, width: Float, height: Float, gap: Float): List<CollageCell> {
        if (count <= 0) return emptyList()
        val rows = rows(count, width, height)
        val rowHeight = (height - gap * (rows.size - 1)) / rows.size
        val out = ArrayList<CollageCell>(count)
        var index = first
        rows.forEachIndexed { r, inRow ->
            val cellWidth = (width - gap * (inRow - 1)) / inRow
            val y = top + r * (rowHeight + gap)
            for (c in 0 until inRow) {
                out += CollageCell(index++, left + c * (cellWidth + gap), y, cellWidth, rowHeight)
            }
        }
        return out
    }

    private fun strip(count: Int, gap: Float, w: Float, h: Float, vertical: Boolean): List<CollageCell> {
        val inner = (if (vertical) h else w) - gap * (count + 1)
        val step = inner / count
        return List(count) { i ->
            val along = gap + i * (step + gap)
            if (vertical) CollageCell(i, gap, along, w - 2 * gap, step) else CollageCell(i, along, gap, step, h - 2 * gap)
        }
    }

    /** First photo on [HERO_SHARE] of the long side; the others share the rest in a grid. */
    private fun hero(count: Int, gap: Float, w: Float, h: Float): List<CollageCell> {
        val innerW = w - 2 * gap
        val innerH = h - 2 * gap
        if (count == 1) return listOf(CollageCell(0, gap, gap, innerW, innerH))
        // The others get a bit more room when there are many of them.
        val share = if (count <= 4) HERO_SHARE else HERO_SHARE - 0.08f
        return if (h >= w) {
            val bigH = (innerH - gap) * share
            listOf(CollageCell(0, gap, gap, innerW, bigH)) +
                grid(1, count - 1, gap, gap + bigH + gap, innerW, innerH - bigH - gap, gap)
        } else {
            val bigW = (innerW - gap) * share
            listOf(CollageCell(0, gap, gap, bigW, innerH)) +
                grid(1, count - 1, gap + bigW + gap, gap, innerW - bigW - gap, innerH, gap)
        }
    }

    /**
     * Pêle-mêle: a grid without gaps, each print a bit smaller than its cell, nudged and tilted by a few degrees
     * (the same [seed] always gives the same table). Prints keep a photo-like 4:5 or 5:4 shape.
     */
    private fun scatter(count: Int, w: Float, h: Float, seed: Int): List<CollageCell> {
        val margin = min(w, h) * 0.04f
        val base = grid(0, count, margin, margin, w - 2 * margin, h - 2 * margin, 0f)
        val random = Random(seed * 31 + count)
        return base.map { cell ->
            val scale = SCATTER_SCALE
            val shapeW = if (cell.width >= cell.height) 5f else 4f
            val shapeH = if (cell.width >= cell.height) 4f else 5f
            val fit = min(cell.width * scale / shapeW, cell.height * scale / shapeH)
            val pw = shapeW * fit
            val ph = shapeH * fit
            val dx = (random.nextFloat() - 0.5f) * cell.width * 0.10f
            val dy = (random.nextFloat() - 0.5f) * cell.height * 0.10f
            val sign = if (cell.index % 2 == 0) -1f else 1f
            val angle = sign * (2.5f + random.nextFloat() * 6f)
            CollageCell(cell.index, cell.centerX - pw / 2f + dx, cell.centerY - ph / 2f + dy, pw, ph, angle)
        }
    }

    /** The photo's place inside a pêle-mêle print (white border, wider at the bottom). */
    fun printPhoto(cell: CollageCell): CollageCell {
        val border = min(cell.width, cell.height) * PRINT_BORDER
        val bottom = min(cell.width, cell.height) * PRINT_BOTTOM
        return cell.copy(
            left = cell.left + border,
            top = cell.top + border,
            width = cell.width - 2 * border,
            height = cell.height - border - bottom,
        )
    }

    /** The centred part of a [sourceWidth] × [sourceHeight] photo that has the shape of a [cellWidth] × [cellHeight] cell. */
    fun crop(sourceWidth: Int, sourceHeight: Int, cellWidth: Float, cellHeight: Float): CollageCrop {
        require(sourceWidth > 0 && sourceHeight > 0 && cellWidth > 0f && cellHeight > 0f)
        val cellRatio = cellWidth / cellHeight
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        return if (sourceRatio > cellRatio) {
            val keep = (sourceHeight * cellRatio).roundToInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - keep) / 2
            CollageCrop(left, 0, left + keep, sourceHeight)
        } else {
            val keep = (sourceWidth / cellRatio).roundToInt().coerceIn(1, sourceHeight)
            val top = (sourceHeight - keep) / 2
            CollageCrop(0, top, sourceWidth, top + keep)
        }
    }

    /** [order] after moving the photo at [from] to [to] (the others shift, like dragging in a list). */
    fun <T> move(order: List<T>, from: Int, to: Int): List<T> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        val out = order.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /** [order] with the photos at [a] and [b] swapped. */
    fun <T> swap(order: List<T>, a: Int, b: Int): List<T> {
        if (a !in order.indices || b !in order.indices || a == b) return order
        val out = order.toMutableList()
        out[a] = order[b]
        out[b] = order[a]
        return out
    }

    /** [order] shuffled with [seed]; never returns the same order when it can avoid it. */
    fun <T> shuffle(order: List<T>, seed: Int): List<T> {
        if (order.size < 2) return order
        val random = Random(seed)
        repeat(8) {
            val out = order.shuffled(random)
            if (out != order) return out
        }
        return order.drop(1) + order.first()
    }

    /**
     * The background for [CollageBackground.COLOR]: the mean of each photo's main colour (ARGB, already a bit dark
     * from ProfileFit.backdropColor), so no single photo wins. Dark grey when there is none.
     */
    fun mix(colors: List<Int>): Int {
        if (colors.isEmpty()) return 0xFF202020.toInt()
        var r = 0
        var g = 0
        var b = 0
        colors.forEach {
            r += (it shr 16) and 0xFF
            g += (it shr 8) and 0xFF
            b += it and 0xFF
        }
        val n = colors.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** ARGB of a plain [background] ([CollageBackground.COLOR] takes [mixed]). */
    fun backgroundColor(background: CollageBackground, mixed: Int): Int = when (background) {
        CollageBackground.WHITE -> 0xFFFFFFFF.toInt()
        CollageBackground.BLACK -> 0xFF000000.toInt()
        CollageBackground.CREAM -> 0xFFF4ECDD.toInt()
        CollageBackground.COLOR -> mixed
    }

    /** "collage-4-photos.jpg". */
    fun outputName(count: Int): String = SafeNames.sanitize("collage-$count-photos.jpg", "collage.jpg")

    /** "2048 × 2048" for the button's subtitle. */
    fun sizeLabel(format: CollageFormat): String {
        val (w, h) = size(format)
        return "$w × $h"
    }

    private const val HERO_SHARE = 0.62f
    private const val SCATTER_SCALE = 0.9f
}
