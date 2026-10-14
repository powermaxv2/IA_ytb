package com.powermaxv2.ytdownloader.ui.tools.colors

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.colors.ColorFormat
import com.powermaxv2.ytdownloader.core.colors.ColorMath
import com.powermaxv2.ytdownloader.core.colors.PaletteExtractor
import com.powermaxv2.ytdownloader.core.colors.PickPhoto
import com.powermaxv2.ytdownloader.core.colors.Swatch
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

// Pieces of the Pipette: the photo you touch (with its marker and the magnifier under the finger), the big
// colour with its code rolling in, the code rows to copy, the palette bar, the kept colours, and the looping
// demo of the empty screen. Drawn in the three skins (round marker in Gulp and Papier, crosshair in Terminal).

/** 0xRRGGBB → an opaque Compose colour. */
internal fun opaque(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)

private fun inkOn(rgb: Int): Color = if (ColorMath.wantsWhiteText(rgb)) Color.White else Color(0xFF14110F)

/**
 * The photo, fitted in the card's width (460 dp tall at most). A touch or a drag moves the pipette
 * ([onTouch] gets fractions of the photo); while the finger is down a magnifier shows the pixels under it.
 * When the pipette jumps (a palette colour touched), the marker springs to its new place.
 */
@Composable
internal fun PickStage(photo: PickPhoto, x: Float, y: Float, color: Int, onTouch: (Float, Float) -> Unit) {
    val image = remember(photo) { photo.bitmap.asImageBitmap() }
    val terminal = LocalSkin.current == Skin.TERMINAL
    val paper = LocalSkin.current == Skin.PAPER
    val accent = LocalAccent.current.start
    val ink = Palette.TextPrimary
    var dragging by remember { mutableStateOf(false) }
    val mx = remember(photo) { Animatable(x) }
    val my = remember(photo) { Animatable(y) }
    LaunchedEffect(x, y, dragging) {
        if (dragging) {
            mx.snapTo(x)
            my.snapTo(y)
        } else {
            launch { mx.animateTo(x, spring(dampingRatio = 0.62f, stiffness = 240f)) }
            my.animateTo(y, spring(dampingRatio = 0.62f, stiffness = 240f))
        }
    }
    val loupe by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "loupe")
    val shown by animateColorAsState(opaque(color), tween(180), label = "pickedColor")
    val halo by rememberInfiniteTransition(label = "pickHalo")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "halo")
    val corner = if (terminal) LocalTokens.current.cardRadius else 22.dp

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val ratio = photo.width.toFloat() / photo.height
        val height = min(maxWidth / ratio, 460.dp)
        val width = height * ratio
        Canvas(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(corner))
                .then(if (paper) Modifier.border(2.dp, ink, RoundedCornerShape(corner)) else Modifier)
                .semantics { contentDescription = "Photo : touche-la pour prendre une couleur" }
                .pointerInput(photo) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        try {
                            onTouch(down.position.x / size.width, down.position.y / size.height)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                onTouch(change.position.x / size.width, change.position.y / size.height)
                            }
                        } finally {
                            dragging = false
                        }
                    }
                },
        ) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val at = Offset(mx.value * size.width, my.value * size.height)
            if (terminal) crosshair(at, shown, accent, halo) else ringMarker(at, shown, halo)
            if (loupe > 0.01f) magnifier(image, at, shown, terminal, accent, loupe)
        }
    }
}

/** Gulp / Papier: the colour in a white ring, a soft halo spreading out from it. */
private fun DrawScope.ringMarker(at: Offset, color: Color, halo: Float) {
    val r = 15.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.55f * (1f - halo)), radius = r + 18.dp.toPx() * halo, center = at, style = Stroke(2.dp.toPx()))
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = r + 2.dp.toPx(), center = at + Offset(0f, 1.5f.dp.toPx()))
    drawCircle(Color.White, radius = r + 1.5f.dp.toPx(), center = at)
    drawCircle(color, radius = r - 2.dp.toPx(), center = at)
}

/** Terminal: a crosshair in the accent with a square of the colour, blinking corners. */
private fun DrawScope.crosshair(at: Offset, color: Color, accent: Color, halo: Float) {
    val s = 12.dp.toPx()
    val w = 1.5f.dp.toPx()
    val gap = s + 4.dp.toPx()
    val len = 14.dp.toPx()
    drawLine(accent, at - Offset(gap + len, 0f), at - Offset(gap, 0f), w)
    drawLine(accent, at + Offset(gap, 0f), at + Offset(gap + len, 0f), w)
    drawLine(accent, at - Offset(0f, gap + len), at - Offset(0f, gap), w)
    drawLine(accent, at + Offset(0f, gap), at + Offset(0f, gap + len), w)
    drawRect(Color.Black, topLeft = at - Offset(s, s), size = Size(2 * s, 2 * s))
    drawRect(color, topLeft = at - Offset(s - w * 1.5f, s - w * 1.5f), size = Size(2 * s - 3 * w, 2 * s - 3 * w))
    drawRect(accent.copy(alpha = if (halo < 0.5f) 1f else 0.35f), topLeft = at - Offset(s, s), size = Size(2 * s, 2 * s), style = Stroke(w))
}

/** The pixels around the finger, 9 × bigger and sharp, above the finger (below it near the top edge). */
private fun DrawScope.magnifier(image: ImageBitmap, at: Offset, color: Color, terminal: Boolean, accent: Color, grow: Float) {
    val radius = 54.dp.toPx() * grow
    if (radius < 2f) return
    val lift = 92.dp.toPx()
    val cy = if (at.y - lift - radius >= 0f) at.y - lift else at.y + lift
    val center = Offset(at.x.coerceIn(radius, size.width - radius), cy)
    val cells = 13
    val cell = radius * 2 / cells
    val px = (at.x / size.width * image.width).toInt()
    val py = (at.y / size.height * image.height).toInt()
    val src = IntOffset((px - cells / 2).coerceIn(0, (image.width - cells).coerceAtLeast(0)), (py - cells / 2).coerceIn(0, (image.height - cells).coerceAtLeast(0)))
    val srcSize = IntSize(minOf(cells, image.width), minOf(cells, image.height))
    val topLeft = center - Offset(radius, radius)
    val shape = Path().apply {
        if (terminal) addRect(androidx.compose.ui.geometry.Rect(topLeft, Size(radius * 2, radius * 2)))
        else addOval(androidx.compose.ui.geometry.Rect(center, radius))
    }
    drawCircle(Color.Black.copy(alpha = 0.3f), radius = radius + 4.dp.toPx(), center = center + Offset(0f, 3.dp.toPx()))
    clipPath(shape) {
        drawImage(
            image,
            srcOffset = src,
            srcSize = srcSize,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
            filterQuality = FilterQuality.None,
        )
        // The pixel being read, outlined.
        val mid = topLeft + Offset((px - src.x) * cell, (py - src.y) * cell)
        drawRect(Color.White, topLeft = mid, size = Size(cell, cell), style = Stroke(2.dp.toPx()))
        drawRect(Color.Black, topLeft = mid - Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(cell + 2.dp.toPx(), cell + 2.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
    val rim = 6.dp.toPx()
    if (terminal) {
        drawRect(color, topLeft = topLeft, size = Size(radius * 2, radius * 2), style = Stroke(rim))
        drawRect(accent, topLeft = topLeft - Offset(rim / 2, rim / 2), size = Size(radius * 2 + rim, radius * 2 + rim), style = Stroke(1.dp.toPx()))
    } else {
        drawCircle(color, radius = radius, center = center, style = Stroke(rim))
        drawCircle(Color.White, radius = radius + rim / 2, center = center, style = Stroke(1.5f.dp.toPx()))
    }
}

/**
 * The colour under the pipette: a big disc that pops at each new colour, its code whose characters roll in one
 * after the other like a split-flap board, and its everyday name.
 */
@Composable
internal fun ColorHero(rgb: Int, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shown by animateColorAsState(opaque(rgb), tween(260), label = "heroColor")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(rgb) {
        pop.snapTo(0.84f)
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
    }
    val hex = ColorMath.hex(rgb)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(84.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape)
                .background(shown)
                .border(2.dp, Palette.TextPrimary.copy(alpha = 0.12f), if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Aa",
                color = inkOn(rgb),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row {
                if (terminal) Text("[", color = LocalAccent.current.start, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                hex.forEachIndexed { index, ch ->
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { -it } + fadeIn(tween(120, delayMillis = index * 28))) togetherWith
                                (slideOutVertically(tween(160)) { it } + fadeOut(tween(120)))
                        },
                        label = "hexChar$index",
                    ) { c ->
                        Text(
                            c.toString(),
                            color = Palette.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                if (terminal) Text("]", color = LocalAccent.current.start, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Crossfade(ColorMath.name(rgb), animationSpec = tween(220), label = "colorName") { name ->
                Text(
                    if (terminal) "# ${name.lowercase()}" else name,
                    color = Palette.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** The ways to write the colour; a touch copies one and its icon turns into a check for a moment. */
@Composable
internal fun FormatRows(formats: List<ColorFormat>, onCopy: (ColorFormat) -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    var copied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1300)
            copied = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.forEach { format ->
            val done = copied == format.label
            val flash by animateFloatAsState(if (done) 1f else 0f, tween(220), label = "copyFlash")
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        copied = format.label
                        onCopy(format)
                    }
                    .clip(RoundedCornerShape(if (terminal) LocalTokens.current.cardRadius else 14.dp))
                    .background(Palette.Surface2)
                    .background(accent.copy(alpha = 0.16f * flash))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (terminal) "${format.label}>" else format.label,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.width(54.dp),
                )
                Text(
                    format.value,
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(done, label = "copyIcon") { isDone ->
                    if (isDone) {
                        Text(if (terminal) "[OK]" else "Copié", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier ${format.label}", tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * The photo's palette: a bar where each colour takes its share (the segments grow in one after the other),
 * then one disc per colour with its share; the one under the pipette is ringed. A touch picks it.
 */
@Composable
internal fun PaletteStrip(palette: List<Swatch>, current: Int, onSelect: (Swatch) -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    val radius = if (terminal) LocalTokens.current.cardRadius else 12.dp
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(radius)),
        ) {
            palette.forEachIndexed { index, swatch ->
                val grow = remember(palette) { Animatable(0f) }
                LaunchedEffect(palette) {
                    delay(index * 90L)
                    grow.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 140f))
                }
                Box(
                    Modifier
                        .weight((swatch.share * grow.value).coerceAtLeast(0.0001f))
                        .height(34.dp)
                        .background(opaque(swatch.rgb)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            palette.forEachIndexed { index, swatch ->
                val pop = remember(palette) { Animatable(0f) }
                LaunchedEffect(palette) {
                    delay(200L + index * 70L)
                    pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
                }
                val selected = swatch.rgb == current
                val ring by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "swatchRing")
                Column(
                    Modifier
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .bounceClick { onSelect(swatch) }
                        .semantics { contentDescription = "${ColorMath.name(swatch.rgb)}, ${ColorMath.hex(swatch.rgb)}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape
                    Box(
                        Modifier
                            .size(46.dp)
                            .border(2.dp, accent.copy(alpha = ring), shape)
                            .padding(4.dp + 2.dp * (1f - ring))
                            .clip(shape)
                            .background(opaque(swatch.rgb)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${PaletteExtractor.percent(swatch.share)} %",
                        color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** The kept colours: a touch shows one again, a long press forgets it. */
@Composable
internal fun KeptRow(kept: List<Int>, current: Int, onRecall: (Int) -> Unit, onForget: (Int) -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        kept.forEachIndexed { index, rgb ->
            val pop = remember(rgb) { Animatable(0f) }
            LaunchedEffect(rgb) {
                delay(index * 40L)
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 360f))
            }
            val shape = if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = pop.value.coerceIn(0f, 1f)
                    }
                    .pointerInput(rgb) { detectTapGestures(onTap = { onRecall(rgb) }, onLongPress = { onForget(rgb) }) }
                    .semantics { contentDescription = "Couleur gardée ${ColorMath.hex(rgb)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 58.dp, height = 44.dp)
                        .clip(shape)
                        .background(opaque(rgb))
                        .border(2.dp, if (rgb == current) accent else Palette.TextPrimary.copy(alpha = 0.1f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (rgb == current) Icon(Icons.Rounded.Check, contentDescription = null, tint = inkOn(rgb), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(ColorMath.hex(rgb), color = Palette.TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** The empty screen's demo: a little seaside picture, the pipette hopping from the sun to the sea to the sand, each colour dropping into the strip below. */
@Composable
internal fun PipetteDemo(modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    val spots = DEMO_SPOTS
    val t by rememberInfiniteTransition(label = "pipetteDemo")
        .animateFloat(0f, spots.size.toFloat(), infiniteRepeatable(tween(spots.size * 1500, easing = LinearEasing)), label = "demoT")
    val index = floor(t).toInt().coerceIn(0, spots.size - 1)
    val local = t - index
    val travel = FastOutSlowInEasing.transform((local / 0.45f).coerceIn(0f, 1f))
    val from = spots[(index + spots.size - 1) % spots.size]
    val to = spots[index]
    val arrived = local > 0.45f
    val corner = if (terminal) LocalTokens.current.cardRadius else 18.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(corner)),
        ) {
            drawSeaside()
            val at = Offset(
                (from.x + (to.x - from.x) * travel) * size.width,
                (from.y + (to.y - from.y) * travel) * size.height - 10.dp.toPx() * kotlin.math.sin(travel * Math.PI).toFloat(),
            )
            val shown = if (arrived) opaque(to.rgb) else Color.White.copy(alpha = 0.6f)
            if (terminal) crosshair(at, shown, accent, local) else ringMarker(at, shown, if (arrived) (local - 0.45f) / 0.55f else 0f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spots.forEachIndexed { i, spot ->
                val filled = i < index || (i == index && arrived)
                val scale by animateFloatAsState(if (filled) 1f else 0.6f, spring(dampingRatio = 0.42f, stiffness = 380f), label = "demoSwatch$i")
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(if (terminal) LocalTokens.current.cardRadius else 10.dp))
                        .background(if (filled) opaque(spot.rgb) else Palette.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    if (filled && !terminal) Text(ColorMath.hex(spot.rgb), color = inkOn(spot.rgb), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    if (filled && terminal) Text(ColorMath.hex(spot.rgb).drop(1), color = inkOn(spot.rgb), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private class DemoSpot(val x: Float, val y: Float, val rgb: Int)

private val DEMO_SPOTS = listOf(
    DemoSpot(0.72f, 0.30f, 0xFFB347),
    DemoSpot(0.25f, 0.18f, 0x7EC8F2),
    DemoSpot(0.40f, 0.66f, 0x1B8FB0),
    DemoSpot(0.82f, 0.88f, 0xE8C989),
    DemoSpot(0.12f, 0.80f, 0xE4572E),
)

/** Sky, sun, sea and sand, with a red parasol: the demo picture. */
private fun DrawScope.drawSeaside() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5DB4EA), Color(0xFFA9DCF7)), endY = h * 0.55f), size = Size(w, h * 0.55f))
    drawCircle(Color(0xFFFFB347), radius = h * 0.14f, center = Offset(w * 0.72f, h * 0.30f))
    drawRect(Color(0xFF1B8FB0), topLeft = Offset(0f, h * 0.55f), size = Size(w, h * 0.22f))
    drawRect(Color(0xFF2AA7C9), topLeft = Offset(0f, h * 0.55f), size = Size(w, h * 0.03f))
    drawRect(Color(0xFFE8C989), topLeft = Offset(0f, h * 0.77f), size = Size(w, h * 0.23f))
    // Parasol.
    drawLine(Color(0xFF4A2C1A), Offset(w * 0.14f, h * 0.62f), Offset(w * 0.14f, h * 0.95f), 3.dp.toPx())
    drawArc(Color(0xFFE4572E), 180f, 180f, true, topLeft = Offset(w * 0.04f, h * 0.55f), size = Size(w * 0.2f, h * 0.22f))
}
