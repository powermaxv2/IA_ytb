package com.powermaxv2.ytdownloader.ui.tools.scan

import android.graphics.Bitmap
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.scan.Pt
import com.powermaxv2.ytdownloader.core.scan.Quad
import com.powermaxv2.ytdownloader.core.scan.ScanGeometry
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens

// Pieces of the Scanner drawn in every skin: the corner editor, the page pictures and the capture hero.

/** [bitmap] as a Compose image, converted once per bitmap. */
@Composable
internal fun rememberImage(bitmap: Bitmap?): ImageBitmap? = remember(bitmap) { bitmap?.takeIf { !it.isRecycled }?.asImageBitmap() }

/**
 * The whole photo with the page's frame on top: the four corners are dragged with a finger (a touch that starts
 * away from the corners scrolls the screen as usual). The photo turns with [rotation], animated.
 */
@Composable
internal fun CornerEditor(
    photo: Bitmap?,
    aspect: Float,
    quad: Quad,
    rotation: Int,
    onQuad: (Quad) -> Unit,
    modifier: Modifier = Modifier,
    limit: Dp = 440.dp,
) {
    val accent = LocalAccent.current
    val image = rememberImage(photo)
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    // The frame follows the finger here, and goes to the controller when the finger lifts.
    var live by remember { mutableStateOf(quad) }
    var active by remember { mutableIntStateOf(-1) }
    LaunchedEffect(quad) { if (active < 0) live = quad }
    val latestOnQuad by rememberUpdatedState(onQuad)
    val turn by animateFloatAsState(
        rotation.toFloat(),
        spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "turn",
    )
    val sideways = ScanGeometry.normalize(rotation) % 180 != 0
    val shown = if (sideways) 1f / aspect else aspect

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF15100D)),
        contentAlignment = Alignment.Center,
    ) {
        val boxW = maxWidth
        // Size of the turned photo, fitted in the box; the unturned layer has the swapped size.
        val fitW: Dp
        val fitH: Dp
        if (boxW / shown <= limit) {
            fitW = boxW
            fitH = boxW / shown
        } else {
            fitH = limit
            fitW = limit * shown
        }
        val layerW = if (sideways) fitH else fitW
        val layerH = if (sideways) fitW else fitH
        Box(Modifier.size(fitW, fitH), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(layerW, layerH)
                    .graphicsLayer {
                        rotationZ = turn
                        // While turning the layer's sides swap: shrink a bit so it never spills out.
                        val k = (turn % 90f) / 90f
                        val wobble = 1f - 0.12f * (4f * k * (1f - k))
                        scaleX = wobble
                        scaleY = wobble
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val touch = Pt(down.position.x / w, down.position.y / h)
                            val corner = live.nearestCorner(touch, radius = 0.09f, w = w, h = h)
                            if (corner < 0) return@awaitEachGesture
                            down.consume()
                            active = corner
                            var last = live
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChange() != Offset.Zero) {
                                    val moved = last.with(corner, Pt(change.position.x / w, change.position.y / h))
                                    if (moved.isValid) {
                                        last = moved
                                        live = moved
                                    }
                                    change.consume()
                                }
                            }
                            active = -1
                            latestOnQuad(last)
                        }
                    },
            ) {
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().shimmer())
                }
                FrameOverlay(live, active, accent.start, Modifier.fillMaxSize())
            }
        }
    }
}

/** Dims what is outside the page, draws its frame and the four handles (the dragged one pulses bigger). */
@Composable
private fun FrameOverlay(quad: Quad, active: Int, color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "handles")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "handlePulse")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "march")
    val grow by animateFloatAsState(if (active >= 0) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "grow")
    val terminal = LocalSkin.current == Skin.TERMINAL
    Canvas(modifier) {
        val pts = quad.points.map { Offset(it.x * size.width, it.y * size.height) }
        val frame = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addPath(frame)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.5f))
        drawPath(frame, color.copy(alpha = 0.10f))
        val dash = if (terminal) {
            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -march * 24f)
        } else {
            null
        }
        drawPath(frame, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
        // Thirds inside the page: help to see if the text runs straight.
        val guide = Color.White.copy(alpha = 0.22f)
        for (t in listOf(1f / 3f, 2f / 3f)) {
            drawLine(guide, lerp(pts[0], pts[1], t), lerp(pts[3], pts[2], t), 1.dp.toPx())
            drawLine(guide, lerp(pts[0], pts[3], t), lerp(pts[1], pts[2], t), 1.dp.toPx())
        }
        pts.forEachIndexed { i, p ->
            val dragged = i == active
            val r = 11.dp.toPx() * (1f + if (dragged) 0.45f * grow else 0f)
            // A ring keeps growing out of every handle: "drag me".
            drawCircle(color.copy(alpha = (1f - pulse) * 0.55f), radius = r + 14.dp.toPx() * pulse, center = p, style = Stroke(2.dp.toPx()))
            if (terminal) {
                drawRect(color, topLeft = p - Offset(r, r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
                drawRect(Color.Black, topLeft = p - Offset(r * 0.45f, r * 0.45f), size = androidx.compose.ui.geometry.Size(r * 0.9f, r * 0.9f))
            } else {
                drawCircle(Color.White, radius = r + 3.dp.toPx(), center = p)
                drawCircle(color, radius = r, center = p)
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** A finished page as a sheet of paper: its picture, a shadow, and a soft "flip in" the first time it shows. */
@Composable
internal fun PageSheet(bitmap: Bitmap?, modifier: Modifier = Modifier, aspect: Float = 1f / 1.414f, highlighted: Boolean = false) {
    val image = rememberImage(bitmap)
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 2.dp else 6.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 220f)) }
    val ratio = image?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: aspect
    Box(
        modifier
            .aspectRatio(ratio.coerceIn(0.4f, 2.5f))
            .graphicsLayer {
                val p = appear.value
                rotationY = (1f - p) * -40f
                scaleX = 0.85f + 0.15f * p
                scaleY = 0.85f + 0.15f * p
                alpha = p.coerceIn(0f, 1f)
                cameraDistance = 12f * density
            }
            .shadow(if (highlighted) 14.dp else 5.dp, shape)
            .clip(shape)
            .background(Color.White)
            .then(if (highlighted) Modifier.border(2.dp, accent.start, shape) else Modifier),
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().shimmer())
        }
    }
}

/** The round "lens" of the capture card: rings breathe out of it and a scan line sweeps across. */
@Composable
internal fun CaptureLens(modifier: Modifier = Modifier, diameter: Dp = 104.dp) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val transition = rememberInfiniteTransition(label = "lens")
    val ring by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)), label = "ring")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep")
    val density = LocalDensity.current
    Box(modifier.size(diameter * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = diameter.toPx() / 2f
            for (k in 0..1) {
                val p = (ring + k * 0.5f) % 1f
                if (terminal) {
                    val s = base * 2f * (1f + 0.55f * p)
                    drawRect(
                        accent.start.copy(alpha = (1f - p) * 0.45f),
                        topLeft = center - Offset(s / 2f, s / 2f),
                        size = androidx.compose.ui.geometry.Size(s, s),
                        style = Stroke(2.dp.toPx()),
                    )
                } else {
                    drawCircle(accent.start.copy(alpha = (1f - p) * 0.35f), radius = base * (1f + 0.55f * p), style = Stroke(3.dp.toPx()))
                }
            }
        }
        val shape = if (terminal) RoundedCornerShape(6.dp) else CircleShape
        Box(
            Modifier
                .size(diameter)
                .clip(shape)
                .background(if (terminal) accent.start else Color.Transparent)
                .then(if (terminal) Modifier else Modifier.background(accent.brush)),
            contentAlignment = Alignment.Center,
        ) {
            ScanGlyph(color = if (terminal) Palette.OnAccent else Color.White, modifier = Modifier.size(diameter * 0.5f))
            val travel = with(density) { (diameter * 0.6f).toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .graphicsLayer { translationY = (sweep - 0.5f) * travel }
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

/** A sheet with its corners marked: the Scanner's symbol. */
@Composable
internal fun ScanGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.09f
        val l = s * 0.28f
        val inset = stroke / 2f
        val w = size.width - inset
        val h = size.height - inset
        // Corner brackets.
        listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(w, inset) to Offset(-1f, 1f),
            Offset(w, h) to Offset(-1f, -1f),
            Offset(inset, h) to Offset(1f, -1f),
        ).forEach { (corner, dir) ->
            drawLine(color, corner, corner + Offset(l * dir.x, 0f), stroke, StrokeCap.Round)
            drawLine(color, corner, corner + Offset(0f, l * dir.y), stroke, StrokeCap.Round)
        }
        // Lines of text.
        val left = s * 0.3f
        listOf(0.36f to 0.7f, 0.5f to 0.62f, 0.64f to 0.7f).forEach { (y, right) ->
            drawLine(color, Offset(left, s * y), Offset(s * right, s * y), stroke * 0.8f, StrokeCap.Round)
        }
    }
}

/** Round number badge of a page. */
@Composable
internal fun PageNumber(number: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(
        modifier
            .size(26.dp)
            .shadow(3.dp, if (terminal) RoundedCornerShape(3.dp) else CircleShape)
            .clip(if (terminal) RoundedCornerShape(3.dp) else CircleShape)
            .background(accent.brush),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = Palette.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Three sheets fanned out (the result), each one dealt in with a spring. */
@Composable
internal fun SheetFan(bitmaps: List<Bitmap?>, modifier: Modifier = Modifier) {
    val shown = bitmaps.take(3).reversed()
    Box(modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        shown.forEachIndexed { i, bitmap ->
            val fromTop = shown.size - 1 - i
            val angle = when (fromTop) {
                0 -> 0f
                1 -> 9f
                else -> -10f
            }
            val dx = when (fromTop) {
                0 -> 0f
                1 -> 30f
                else -> -30f
            }
            val deal = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(120L + fromTop * 90L)
                deal.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f))
            }
            PageSheet(
                bitmap,
                Modifier
                    .height(150.dp)
                    .offset(y = 4.dp)
                    .graphicsLayer {
                        val p = deal.value
                        rotationZ = angle * p
                        translationX = dx * p * density
                        translationY = (1f - p) * 40f * density
                        alpha = p.coerceIn(0f, 1f)
                    },
            )
        }
    }
}

@Composable
internal fun Dashed(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val transition = rememberInfiniteTransition(label = "dash")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dashMarch")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = if (terminal) 2.dp.toPx() else 8.dp.toPx()
            drawRoundRect(
                accent.start.copy(alpha = 0.8f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                style = Stroke(
                    2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -march * 24f),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
