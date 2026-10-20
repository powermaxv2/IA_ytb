package com.powermaxv2.ytdownloader.ui.tools.collage

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.collage.Collage
import com.powermaxv2.ytdownloader.core.collage.CollageBackground
import com.powermaxv2.ytdownloader.core.collage.CollageCell
import com.powermaxv2.ytdownloader.core.collage.CollageFormat
import com.powermaxv2.ytdownloader.core.collage.CollageLayout
import com.powermaxv2.ytdownloader.core.collage.CollageOptions
import com.powermaxv2.ytdownloader.core.collage.CollagePhoto
import com.powermaxv2.ytdownloader.core.collage.CollageSpacing
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Pieces of "Collage photos": the live stage (each photo a tile that springs to its cell when the layout, the shape
// or the order changes, and that lifts when touched for a swap), the layout tiles, the background swatches, and the
// looping demo of the empty screen.

@Composable
private fun rememberImage(bitmap: Bitmap): ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

/** Colour of [background] on screen ([CollageBackground.COLOR] mixes the photos' main colours). */
internal fun backgroundOf(background: CollageBackground, photos: List<CollagePhoto>): Color =
    Color(Collage.backgroundColor(background, Collage.mix(photos.map { it.color })))

/**
 * The collage as it will be saved, live: the picture's shape, its background, and one tile per photo. Tiles are
 * keyed by photo, so a new order makes them fly to their new place; [selected] lifts one with an accent ring and
 * [onTouch] gets the touched position. [numbers] shows each photo's place in the order.
 */
@Composable
internal fun CollageStage(
    photos: List<CollagePhoto>,
    options: CollageOptions,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    numbers: Boolean = true,
    busy: Boolean = false,
    onTouch: ((Int) -> Unit)? = null,
) {
    val (pw, ph) = Collage.size(options.format, 1000)
    val ratio = pw.toFloat() / ph
    val background by animateColorAsState(backgroundOf(options.background, photos), tween(420), label = "collageBackground")
    val loop = rememberInfiniteTransition(label = "collageStage")
    val breathe by loop.animateFloat(
        1f,
        if (busy) 0.97f else 1f,
        infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "collageBreathe",
    )
    val terminal = LocalSkin.current == Skin.TERMINAL
    // The shape itself springs when the format changes (square → story…).
    val shownRatio by animateFloatAsState(ratio, spring(dampingRatio = 0.75f, stiffness = 220f), label = "collageRatio")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxW = maxWidth
        val maxH = 420.dp
        val width = if (maxW / shownRatio > maxH) maxH * shownRatio else maxW
        Box(
            Modifier
                .width(width)
                .aspectRatio(shownRatio)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                }
                .shadow(if (terminal) 0.dp else 10.dp, RoundedCornerShape(if (terminal) 0.dp else 6.dp))
                .background(background)
                .then(if (terminal) Modifier.border(1.dp, LocalAccent.current.start) else Modifier),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                if (boxW > 0f && boxH > 0f && photos.isNotEmpty()) {
                    // Cells in a 1000 px picture of the real shape, scaled to the box (keeps the gaps in proportion).
                    val cells = Collage.cells(photos.size, options, pw, ph)
                    val sx = boxW / pw
                    val sy = boxH / ph
                    val radiusPx = Collage.radius(options, pw, ph) * sx
                    photos.forEachIndexed { position, photo ->
                        key(photo.id) {
                            val cell = cells[position]
                            PhotoTile(
                                photo = photo,
                                position = position,
                                target = Rect(cell.left * sx, cell.top * sy, cell.right * sx, cell.bottom * sy),
                                rotation = cell.rotation,
                                print = options.layout == CollageLayout.SCATTER,
                                radius = radiusPx,
                                lifted = selected == position,
                                dimmed = selected != null && selected != position,
                                number = if (numbers) position + 1 else null,
                                onTouch = onTouch,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(
    photo: CollagePhoto,
    position: Int,
    target: Rect,
    rotation: Float,
    print: Boolean,
    radius: Float,
    lifted: Boolean,
    dimmed: Boolean,
    number: Int?,
    onTouch: ((Int) -> Unit)?,
) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val image = rememberImage(photo.bitmap)
    val rect by animateRectAsState(target, spring(dampingRatio = 0.72f, stiffness = 240f), label = "tileRect")
    val turn by animateFloatAsState(rotation, spring(dampingRatio = 0.6f, stiffness = 200f), label = "tileTurn")
    val lift by animateFloatAsState(if (lifted) 1.07f else 1f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "tileLift")
    val fade by animateFloatAsState(if (dimmed) 0.62f else 1f, tween(220), label = "tileFade")
    // Each photo drops in once, one after the other.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(position.coerceAtMost(8) * 70L)
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    val shape = RoundedCornerShape(with(density) { (if (print) radius * 0.5f else radius).toDp() })
    val w = with(density) { rect.width.coerceAtLeast(1f).toDp() }
    val h = with(density) { rect.height.coerceAtLeast(1f).toDp() }
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(w, h)
            .graphicsLayer {
                rotationZ = turn + (1f - enter.value) * 14f
                val s = lift * (0.55f + 0.45f * enter.value)
                scaleX = s
                scaleY = s
                alpha = enter.value.coerceIn(0f, 1f) * fade
            }
            .then(if (print || lifted) Modifier.shadow(if (lifted) 12.dp else 4.dp, shape) else Modifier)
            .clip(shape)
            .then(if (print) Modifier.background(Color.White) else Modifier)
            .semantics {
                contentDescription = "Photo ${position + 1}"
                selected = lifted
                if (onTouch != null) role = Role.Button
            }
            .then(if (onTouch != null) Modifier.bounceClick(pressedScale = 0.94f) { onTouch(position) } else Modifier),
    ) {
        val inner = if (print) {
            val cell = CollageCell(0, 0f, 0f, rect.width, rect.height)
            val p = Collage.printPhoto(cell)
            Modifier
                .offset { IntOffset(p.left.roundToInt(), p.top.roundToInt()) }
                .size(with(density) { p.width.coerceAtLeast(1f).toDp() }, with(density) { p.height.coerceAtLeast(1f).toDp() })
        } else {
            Modifier.fillMaxSize()
        }
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = inner)
        if (lifted) Box(Modifier.fillMaxSize().border(3.dp, accent.start, shape))
        if (number != null) NumberBadge(number, lifted, Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

@Composable
private fun NumberBadge(number: Int, lifted: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pop = remember(number) { Animatable(0.4f) }
    LaunchedEffect(number) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
    Box(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(22.dp)
            .clip(if (terminal) RoundedCornerShape(0.dp) else CircleShape)
            .background(if (lifted) accent.start else Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
        )
    }
}

/** The four layouts as small tiles, each drawn with the real cells for [count] photos in the [format]. */
@Composable
internal fun LayoutTiles(count: Int, options: CollageOptions, onSelect: (CollageLayout) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CollageLayout.entries.forEach { layout ->
            LayoutTile(layout, count, options.format, layout == options.layout, Modifier.weight(1f)) { onSelect(layout) }
        }
    }
}

@Composable
private fun LayoutTile(layout: CollageLayout, count: Int, format: CollageFormat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(tokens.chipRadius)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 500f))
        }
    }
    val fill by animateColorAsState(if (selected) accent.start.copy(alpha = if (terminal) 0.18f else 0.14f) else Palette.Surface2, tween(220), label = "layoutFill")
    val ink by animateColorAsState(if (selected) accent.start else Palette.TextTertiary, tween(220), label = "layoutInk")
    val n = count.coerceIn(Collage.MIN_PHOTOS, Collage.MAX_PHOTOS)
    Column(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent.start else Palette.Stroke, shape)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = layout.label
            }
            .bounceClick(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (pw, ph) = Collage.size(format, 100)
        Canvas(Modifier.height(46.dp).aspectRatio(pw.toFloat() / ph)) {
            val cells = Collage.cells(n, CollageOptions(layout = layout, format = format, spacing = CollageSpacing.WIDE), pw, ph)
            val k = size.width / pw
            drawRoundRect(ink.copy(alpha = 0.16f), cornerRadius = CornerRadius(3.dp.toPx()))
            cells.forEach { c ->
                val cell = c
                rotate(cell.rotation, Offset(cell.centerX * k, cell.centerY * k)) {
                    drawRoundRect(
                        ink.copy(alpha = if (cell.index == 0) 0.95f else 0.6f),
                        topLeft = Offset(cell.left * k, cell.top * k),
                        size = Size(cell.width * k, cell.height * k),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (terminal) layout.label.uppercase() else layout.label,
            color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Round swatches for the background; the chosen one pops and wears a check. */
@Composable
internal fun BackgroundSwatches(photos: List<CollagePhoto>, selected: CollageBackground, onSelect: (CollageBackground) -> Unit) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CollageBackground.entries.forEach { bg ->
            val chosen = bg == selected
            val pop = remember { Animatable(1f) }
            LaunchedEffect(chosen) {
                if (chosen) {
                    pop.snapTo(0.7f)
                    pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 480f))
                }
            }
            val color = backgroundOf(bg, photos)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .size(44.dp)
                        .clip(if (terminal) RoundedCornerShape(0.dp) else CircleShape)
                        .background(
                            if (bg == CollageBackground.COLOR && photos.size > 1) {
                                Brush.sweepGradient(photos.map { Color(it.color) } + Color(photos.first().color))
                            } else {
                                Brush.linearGradient(listOf(color, color))
                            },
                        )
                        .border(if (chosen) 3.dp else 1.dp, if (chosen) accent.start else Palette.Stroke, if (terminal) RoundedCornerShape(0.dp) else CircleShape)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = chosen
                            contentDescription = bg.label
                        }
                        .bounceClick { onSelect(bg) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        val dark = bg == CollageBackground.BLACK || bg == CollageBackground.COLOR
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(bg.label, color = if (chosen) Palette.TextPrimary else Palette.TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Empty screen: four coloured photos that keep rearranging themselves (grid → one big → strip → pêle-mêle),
 * each one springing to its new place.
 */
@Composable
internal fun CollageDemo(modifier: Modifier = Modifier) {
    val layouts = CollageLayout.entries
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            step = (step + 1) % layouts.size
        }
    }
    val options = CollageOptions(layout = layouts[step], spacing = CollageSpacing.WIDE, background = CollageBackground.CREAM)
    val skies = remember {
        listOf(
            listOf(Color(0xFFFF8A65), Color(0xFFFFD54F)),
            listOf(Color(0xFF4FC3F7), Color(0xFF81C784)),
            listOf(Color(0xFFBA68C8), Color(0xFFF06292)),
            listOf(Color(0xFF26A69A), Color(0xFF9CCC65)),
        )
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(170.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF4ECDD))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val side = with(density) { maxWidth.toPx() }
                val cells = Collage.cells(skies.size, options, 1000, 1000)
                val k = side / 1000f
                skies.forEachIndexed { i, sky ->
                    val c = cells[i]
                    DemoTile(sky, Rect(c.left * k, c.top * k, c.right * k, c.bottom * k), c.rotation, options.layout == CollageLayout.SCATTER)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            layouts[step].label,
            color = LocalAccent.current.start,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
        )
    }
}

@Composable
private fun DemoTile(sky: List<Color>, target: Rect, rotation: Float, print: Boolean) {
    val density = LocalDensity.current
    val rect by animateRectAsState(target, spring(dampingRatio = 0.66f, stiffness = 200f), label = "demoRect")
    val turn by animateFloatAsState(rotation, spring(dampingRatio = 0.55f, stiffness = 180f), label = "demoTurn")
    val shine = rememberInfiniteTransition(label = "demoShine")
    val sun by shine.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "demoSun")
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.coerceAtLeast(1f).toDp() }, with(density) { rect.height.coerceAtLeast(1f).toDp() })
            .graphicsLayer { rotationZ = turn }
            .then(if (print) Modifier.shadow(3.dp, RoundedCornerShape(2.dp)) else Modifier)
            .background(if (print) Color.White else Color.Transparent),
    ) {
        Canvas(Modifier.fillMaxSize().padding(if (print) 4.dp else 0.dp)) {
            drawRect(Brush.verticalGradient(sky))
            // A sun drifting and a hill: just enough to read as a photo.
            drawCircle(Color.White.copy(alpha = 0.75f), radius = size.minDimension * 0.13f, center = Offset(size.width * (0.25f + 0.5f * sun), size.height * 0.32f))
            drawOval(Color.Black.copy(alpha = 0.16f), topLeft = Offset(-size.width * 0.2f, size.height * 0.68f), size = Size(size.width * 1.4f, size.height * 0.7f))
            drawRect(Color.White.copy(alpha = 0.2f), style = Stroke(1f))
        }
    }
}
