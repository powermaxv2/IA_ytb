package com.powermaxv2.ytdownloader.ui.tools.pdfimages

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.pdfimages.ImagesFormat
import com.powermaxv2.ytdownloader.core.pdfimages.PdfPageInfo
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.sin

// PDF en images: the moving pieces of the screen.

private fun cornerOf(skin: Skin): Dp = when (skin) {
    Skin.CLASSIC -> 10.dp
    Skin.PAPER -> 6.dp
    Skin.TERMINAL -> 2.dp
}

/**
 * The explainer of the empty screen, played in a loop: a PDF stands on the left, its pages slide out one after the
 * other, turn into photos with a "JPG" corner and land fanned out on the right, then everything starts again.
 */
@Composable
internal fun PageBurst(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "burst")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "burstTime")
    val corner = cornerOf(skin)
    BoxWithConstraints(modifier.fillMaxWidth().height(170.dp)) {
        val w = maxWidth
        // The PDF.
        Box(
            Modifier
                .offset(x = w * 0.08f, y = 22.dp)
                .size(92.dp, 124.dp)
                .graphicsLayer { scaleX = 1f - 0.03f * pulse(t, 0.05f); scaleY = scaleX }
                .shadow(if (skin == Skin.TERMINAL) 0.dp else 8.dp, RoundedCornerShape(corner))
                .clip(RoundedCornerShape(corner))
                .background(Color.White)
                .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, RoundedCornerShape(corner)) else Modifier),
        ) {
            SketchLines(Modifier.fillMaxSize().padding(12.dp), accent.start, seed = 0)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.brush)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        // Three pages fly out, one after the other.
        for (i in 0 until 3) {
            val start = 0.08f + i * 0.17f
            val p = ((t - start) / 0.32f).coerceIn(0f, 1f)
            val land = spring01(p)
            // Everything fades away at the end of the loop.
            val out = ((t - 0.88f) / 0.12f).coerceIn(0f, 1f)
            val fromX = w * 0.08f + 4.dp
            val toX = w * (0.48f + 0.13f * i)
            val angle = listOf(-10f, 2f, 12f)[i]
            Box(
                Modifier
                    .offset(x = fromX + (toX - fromX) * land, y = 26.dp + (-26).dp * sin(Math.PI * p).toFloat() + (6 * i).dp * land)
                    .size(84.dp, 110.dp)
                    .graphicsLayer {
                        alpha = if (p <= 0f) 0f else (1f - out)
                        rotationZ = angle * land
                        val pop = 1f + 0.08f * sin(Math.PI * p).toFloat()
                        scaleX = pop
                        scaleY = pop
                    }
                    .shadow(if (skin == Skin.TERMINAL) 0.dp else 10.dp, RoundedCornerShape(corner))
                    .clip(RoundedCornerShape(corner))
                    .background(Color.White)
                    .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, RoundedCornerShape(corner)) else Modifier),
            ) {
                // The page "develops" into a photo: lines first, then a picture fades in over them.
                SketchLines(Modifier.fillMaxSize().padding(10.dp), accent.start, seed = i + 1)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f) }
                        .background(photoBrush(i, accent.start, accent.end)),
                ) {
                    MiniLandscape(Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .graphicsLayer {
                            val s = spring01(((p - 0.7f) / 0.3f).coerceIn(0f, 1f))
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(if (i == 1) "PNG" else "JPG", color = accent.start, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** 0 → 1 with a little overshoot, like a spring settling. */
private fun spring01(p: Float): Float {
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    val e = 1f - (1f - p) * (1f - p) * (1f - p)
    return e + 0.12f * sin(Math.PI * p).toFloat() * (1f - p)
}

private fun pulse(t: Float, at: Float): Float = (1f - kotlin.math.abs(t - at) / 0.05f).coerceIn(0f, 1f)

private fun photoBrush(i: Int, a: Color, b: Color): Brush = when (i) {
    0 -> Brush.verticalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFFFE0B2)))
    1 -> Brush.verticalGradient(listOf(a.copy(alpha = 0.85f), b.copy(alpha = 0.85f)))
    else -> Brush.verticalGradient(listOf(Color(0xFF9BE15D), Color(0xFF00C9A7)))
}

/** Grey text lines with a coloured title bar: what a page looks like from afar. */
@Composable
private fun SketchLines(modifier: Modifier, accent: Color, seed: Int) {
    Canvas(modifier) {
        val bar = size.height * 0.06f
        drawRoundRect(accent.copy(alpha = 0.7f), size = Size(size.width * (0.5f + 0.1f * (seed % 2)), bar * 1.2f), cornerRadius = CornerRadius(bar))
        val grey = Color(0xFFD9D2CC)
        val widths = listOf(0.92f, 0.7f, 0.85f, 0.6f, 0.8f, 0.5f)
        widths.indices.forEach { k ->
            val y = bar * 3f + k * bar * 2.1f
            if (y + bar < size.height) {
                drawRoundRect(grey, topLeft = Offset(0f, y), size = Size(size.width * widths[(k + seed) % widths.size], bar * 0.8f), cornerRadius = CornerRadius(bar))
            }
        }
    }
}

/** A sun and two hills: the "photo" a page becomes. */
@Composable
private fun MiniLandscape(modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.11f, center = Offset(size.width * 0.72f, size.height * 0.28f))
        val hill = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, size.height * 0.72f)
            quadraticBezierTo(size.width * 0.3f, size.height * 0.45f, size.width * 0.6f, size.height * 0.7f)
            quadraticBezierTo(size.width * 0.8f, size.height * 0.58f, size.width, size.height * 0.66f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(hill, Color.Black.copy(alpha = 0.22f))
    }
}

// MARK: - Page grid

/**
 * The pages as thumbnails, three per row: a tap chooses or leaves out a page. A chosen page has the accent ring and a
 * check that pops; a page left out shrinks a little and fades.
 */
@Composable
internal fun PageGrid(
    pages: List<PdfPageInfo>,
    thumbs: Map<Int, ImageBitmap>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pages.chunked(COLUMNS).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chunk.forEachIndexed { col, page ->
                    val number = page.index + 1
                    PageCell(
                        page = page,
                        thumb = thumbs[number],
                        chosen = number in selected,
                        entrance = row * COLUMNS + col,
                        onTap = { onToggle(number) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COLUMNS = 3

@Composable
private fun PageCell(page: PdfPageInfo, thumb: ImageBitmap?, chosen: Boolean, entrance: Int, onTap: () -> Unit, modifier: Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val terminal = skin == Skin.TERMINAL
    val corner = cornerOf(skin)
    val scale by animateFloatAsState(if (chosen) 1f else 0.9f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "cellScale")
    val fade by animateFloatAsState(if (chosen) 1f else 0.42f, tween(200), label = "cellFade")
    val ring by animateFloatAsState(if (chosen) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "cellRing")
    val number = page.index + 1
    Column(
        modifier
            .staggeredEntrance(entrance, key = page.index, baseDelay = 60)
            .bounceClick(pressedScale = 0.93f, onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val aspect = (page.widthPt / page.heightPt.coerceAtLeast(1f)).coerceIn(0.4f, 2.2f)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(corner)
            Box(
                Modifier
                    .then(if (aspect < 0.72f) Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true) else Modifier.fillMaxWidth().aspectRatio(aspect))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = fade
                    }
                    .shadow(if (terminal) 0.dp else (2 + 6 * ring).dp, shape)
                    .clip(shape)
                    .background(Color.White)
                    .border(if (ring > 0.02f) (2.5f * ring).dp else 1.dp, if (ring > 0.02f) accent.start.copy(alpha = ring) else Palette.Stroke, shape),
            ) {
                if (thumb != null) {
                    Image(thumb, contentDescription = "Page $number", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                } else {
                    LoadingPage(Modifier.fillMaxSize())
                }
            }
            // The check pops in the corner.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .graphicsLayer {
                        scaleX = ring
                        scaleY = ring
                        alpha = ring.coerceIn(0f, 1f)
                    }
                    .size(24.dp)
                    .clip(if (terminal) RoundedCornerShape(2.dp) else CircleShape)
                    .background(accent.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (terminal) (if (chosen) "[x] " else "[ ] ") + number.toString().padStart(2, '0') else "$number",
            color = if (chosen) Palette.TextPrimary else Palette.TextTertiary,
            fontSize = if (terminal) 12.5.sp else 13.5.sp,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** A page whose thumbnail is not drawn yet: lines that shimmer. */
@Composable
private fun LoadingPage(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "loadingPage")
    val glow by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "loadingGlow")
    Box(modifier.graphicsLayer { alpha = glow }) {
        SketchLines(Modifier.fillMaxSize().padding(8.dp), LocalAccent.current.start, seed = 2)
    }
}

// MARK: - Working

/**
 * The page being turned into a picture: a light bar sweeps down it like a scanner, then a flash; the pictures made
 * so far pile up beside it with their count.
 */
@Composable
internal fun DevelopingPage(thumb: ImageBitmap?, aspect: Float, done: Int, format: ImagesFormat, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val corner = cornerOf(skin)
    val transition = rememberInfiniteTransition(label = "developing")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "sweep")
    val flash = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420))
        }
    }
    val pile = remember { Animatable(0f) }
    LaunchedEffect(done) {
        pile.snapTo(0.6f)
        pile.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
    }
    Row(modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val pageW = (150f * aspect.coerceIn(0.5f, 1.4f)).dp
        Box(
            Modifier
                .size(pageW, 180.dp)
                .shadow(if (skin == Skin.TERMINAL) 0.dp else 12.dp, RoundedCornerShape(corner))
                .clip(RoundedCornerShape(corner))
                .background(Color.White)
                .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, RoundedCornerShape(corner)) else Modifier),
        ) {
            if (thumb != null) {
                Image(thumb, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                SketchLines(Modifier.fillMaxSize().padding(14.dp), accent.start, seed = done)
            }
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * sweep
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, accent.start.copy(alpha = 0.35f), Color.Transparent), startY = y - 40f, endY = y + 8f),
                    topLeft = Offset(0f, y - 40f),
                    size = Size(size.width, 48f),
                )
                drawRect(accent.start, topLeft = Offset(0f, y), size = Size(size.width, 3f))
                drawRect(Color.White.copy(alpha = 0.8f * flash.value))
            }
        }
        Spacer(Modifier.width(22.dp))
        // The pile of pictures made so far.
        Box(Modifier.size(78.dp, 96.dp), contentAlignment = Alignment.Center) {
            for (k in 2 downTo 0) {
                Box(
                    Modifier
                        .size(64.dp, 80.dp)
                        .graphicsLayer {
                            rotationZ = listOf(-8f, 5f, -2f)[k] * pile.value
                            translationY = (k * 3).dp.toPx()
                            alpha = if (done > k) 1f else 0.25f
                        }
                        .shadow(if (skin == Skin.TERMINAL) 0.dp else 6.dp, RoundedCornerShape(corner))
                        .clip(RoundedCornerShape(corner))
                        .background(photoBrush(k, accent.start, accent.end)),
                ) { MiniLandscape(Modifier.fillMaxSize()) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        scaleX = pile.value
                        scaleY = pile.value
                    }
                    .clip(if (skin == Skin.TERMINAL) RoundedCornerShape(2.dp) else CircleShape)
                    .background(accent.brush)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("$done ${format.label}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
