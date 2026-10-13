package com.powermaxv2.ytdownloader.ui.tools.signpdf

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.signpdf.SignBox
import com.powermaxv2.ytdownloader.core.signpdf.SignPlan
import com.powermaxv2.ytdownloader.core.signpdf.SignPoint
import com.powermaxv2.ytdownloader.core.signpdf.SignatureStore
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.roundToInt

// Pieces of the Signature PDF tool: the pad the signature is drawn on, the page with the signature to place, the
// ink and initials pickers, and the pen that writes while the PDF is saved.

/** Paper of the pad and of the pages: the signature is always shown on white, as it will print. */
internal val PaperWhite = Color(0xFFFDFCF8)
private val Ruler = Color(0xFF8A8F98)

/** Pad width / height. */
internal const val PAD_ASPECT = 2.4f

/** A loose cursive "signature" in a [w] × [h] box, drawn by the idle card and the working pen: its name, then a flourish. */
internal fun sampleSignature(w: Float, h: Float): List<Path> = listOf(
    Path().apply {
        moveTo(0.06f * w, 0.70f * h)
        cubicTo(0.10f * w, 0.20f * h, 0.20f * w, 0.05f * h, 0.22f * w, 0.35f * h)
        cubicTo(0.24f * w, 0.70f * h, 0.14f * w, 0.92f * h, 0.18f * w, 0.62f * h)
        cubicTo(0.24f * w, 0.30f * h, 0.32f * w, 0.62f * h, 0.36f * w, 0.60f * h)
        cubicTo(0.40f * w, 0.40f * h, 0.44f * w, 0.42f * h, 0.45f * w, 0.60f * h)
        cubicTo(0.47f * w, 0.78f * h, 0.52f * w, 0.30f * h, 0.56f * w, 0.52f * h)
        cubicTo(0.60f * w, 0.74f * h, 0.62f * w, 0.20f * h, 0.66f * w, 0.18f * h)
        cubicTo(0.70f * w, 0.16f * h, 0.64f * w, 0.80f * h, 0.70f * w, 0.62f * h)
        cubicTo(0.76f * w, 0.44f * h, 0.80f * w, 0.56f * h, 0.84f * w, 0.50f * h)
        cubicTo(0.88f * w, 0.44f * h, 0.92f * w, 0.48f * h, 0.95f * w, 0.40f * h)
    },
    Path().apply {
        moveTo(0.12f * w, 0.84f * h)
        cubicTo(0.40f * w, 0.76f * h, 0.70f * w, 0.80f * h, 0.92f * w, 0.72f * h)
    },
)

/** [paths] drawn one after the other up to [progress] (0..1) of their total length, and where the pen is. */
private fun partial(paths: List<Path>, progress: Float): Pair<List<Path>, Offset?> {
    val measures = paths.map { path -> PathMeasure().apply { setPath(path, false) } }
    var budget = measures.sumOf { it.length.toDouble() }.toFloat() * progress.coerceIn(0f, 1f)
    val out = ArrayList<Path>()
    var tip: Offset? = null
    for (measure in measures) {
        if (budget <= 0f) break
        val upTo = minOf(measure.length, budget)
        out += Path().also { measure.getSegment(0f, upTo, it, true) }
        tip = measure.getPosition(upTo)
        budget -= measure.length
    }
    return out to tip
}

/** A pen writing [sampleSignature] again and again (idle card when no signature exists, and the working step). */
@Composable
internal fun WritingPen(modifier: Modifier = Modifier, ink: Color = Color(SignatureStore.INK_BLACK), periodMs: Int = 2600) {
    val transition = rememberInfiniteTransition(label = "pen")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "penT")
    val accent = LocalAccent.current.start
    Canvas(modifier) {
        val write = (t / 0.78f).coerceIn(0f, 1f)
        val fade = if (t > 0.86f) 1f - (t - 0.86f) / 0.14f else 1f
        val (drawn, tip) = partial(sampleSignature(size.width, size.height), FastOutSlowInEasing.transform(write))
        drawn.forEach { drawPath(it, ink.copy(alpha = fade), style = Stroke(width = size.height * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        // The nib follows the end of the line.
        if (tip != null && write in 0.01f..0.99f) {
            drawCircle(accent.copy(alpha = 0.25f), radius = size.height * 0.11f, center = tip)
            drawCircle(accent, radius = size.height * 0.04f, center = tip)
        }
    }
}

/**
 * The pad: a sheet with a signing line; the finger draws strokes, kept in [strokes] as pad fractions. A hint fades
 * away at the first stroke.
 */
@Composable
internal fun SignaturePad(strokes: MutableList<List<SignPoint>>, ink: Color, modifier: Modifier = Modifier) {
    val live = remember { mutableStateListOf<SignPoint>() }
    val hint by animateFloatAsState(if (strokes.isEmpty() && live.isEmpty()) 1f else 0f, tween(260), label = "padHint")
    val transition = rememberInfiniteTransition(label = "pad")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "padMarch")
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 6.dp else 20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(PAD_ASPECT)
            .clip(shape)
            .background(PaperWhite)
            .border(1.dp, LocalAccent.current.start.copy(alpha = 0.35f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    live.clear()
                    live.add(SignPoint(down.position.x / w, down.position.y / h))
                    drag(down.id) { change ->
                        change.consume()
                        val p = change.position
                        live.add(SignPoint((p.x / w).coerceIn(0f, 1f), (p.y / h).coerceIn(0f, 1f)))
                    }
                    strokes.add(live.toList())
                    live.clear()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.74f
            drawLine(
                Ruler.copy(alpha = 0.55f),
                Offset(size.width * 0.08f, y),
                Offset(size.width * 0.92f, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), -march * 18f),
            )
            // The "×" where one signs.
            val x0 = size.width * 0.08f
            val s = size.height * 0.06f
            drawLine(Ruler, Offset(x0, y - s * 2.6f), Offset(x0 + s, y - s * 1.6f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Ruler, Offset(x0 + s, y - s * 2.6f), Offset(x0, y - s * 1.6f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            val width = SignatureStore.STROKE * size.height
            strokes.forEach { drawInk(it, ink, width) }
            drawInk(live, ink, width)
        }
        Text(
            if (terminal) "# signe ici avec le doigt" else "Signe ici avec le doigt",
            color = Ruler.copy(alpha = hint),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = (1f - hint) * -8.dp.toPx() },
        )
    }
}

/** A stroke of pad fractions, smoothed through its midpoints like the saved picture. */
private fun DrawScope.drawInk(stroke: List<SignPoint>, ink: Color, width: Float) {
    if (stroke.isEmpty()) return
    val points = stroke.map { Offset(it.x * size.width, it.y * size.height) }
    if (points.size == 1) {
        drawCircle(ink, radius = width / 2f, center = points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            val q = points[i + 1]
            quadraticTo(p.x, p.y, (p.x + q.x) / 2f, (p.y + q.y) / 2f)
        }
        lineTo(points.last().x, points.last().y)
    }
    drawPath(path, ink, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The two inks: a round swatch each, the chosen one ringed and popped. */
@Composable
internal fun InkPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(SignatureStore.INK_BLACK to "Noir", SignatureStore.INK_BLUE to "Bleu").forEach { (ink, label) ->
            val chosen = ink == selected
            val scale by animateFloatAsState(if (chosen) 1.12f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "ink")
            Row(
                Modifier.bounceClick { onSelect(ink) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .border(2.dp, if (chosen) LocalAccent.current.start else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(ink)),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (chosen) Palette.TextPrimary else Palette.TextSecondary, fontSize = 14.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/**
 * The page with the signature on it: drag the signature to move it, pinch it or pull its corner to resize it, or tap
 * the page to bring it there. [caption] is written under the signature as it will be in the PDF.
 */
@Composable
internal fun PagePlacer(
    page: ImageBitmap?,
    pageAspect: Float,
    signature: ImageBitmap?,
    signatureAspect: Float,
    box: SignBox?,
    caption: String?,
    onMove: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val radius = if (terminal) 4.dp else 12.dp
    // Tall pages are kept to a phone's height, centred.
    val maxWidth = if (pageAspect < 0.55f) 300.dp else 600.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .aspectRatio(pageAspect.coerceIn(0.3f, 3f))
                .graphicsLayer {
                    shadowElevation = 10.dp.toPx()
                    shape = RoundedCornerShape(radius)
                    clip = true
                }
                .background(PaperWhite),
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val move by rememberUpdatedState(onMove)
            val resize by rememberUpdatedState(onResize)
            val scale by rememberUpdatedState(onScale)
            val current by rememberUpdatedState(box)
            if (page != null) {
                Image(page, contentDescription = "Page du PDF", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().shimmer())
            }
            // Tap anywhere: the signature comes to the finger, centred on it.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(wPx, hPx, signatureAspect) {
                        detectTapGestures { at ->
                            val b = current ?: return@detectTapGestures
                            val sw = b.width * wPx
                            val sh = sw / signatureAspect.coerceAtLeast(0.05f)
                            move((at.x - sw / 2f) / wPx - b.x, (at.y - sh / 2f) / hPx - b.y)
                        }
                    },
            )
            if (box != null && signature != null) {
                val sigW = box.width * wPx
                val sigH = sigW / signatureAspect.coerceAtLeast(0.05f)
                val capGap = sigW * SignPlan.CAPTION_GAP
                val capH = sigW * SignPlan.CAPTION_RATIO
                val land = remember { Animatable(0f) }
                LaunchedEffect(Unit) { land.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f)) }
                val transition = rememberInfiniteTransition(label = "placer")
                val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "placerMarch")
                val glow by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "placerGlow")
                Box(
                    Modifier
                        .offset { IntOffset((box.x * wPx).roundToInt(), (box.y * hPx).roundToInt()) }
                        .size(with(density) { sigW.toDp() }, with(density) { (sigH + if (caption != null) capGap + capH else 0f).toDp() })
                        .graphicsLayer {
                            val l = land.value
                            alpha = l.coerceIn(0f, 1f)
                            scaleX = 1.35f - 0.35f * l
                            scaleY = 1.35f - 0.35f * l
                        }
                        .drawBehind {
                            drawRoundRect(
                                accent.copy(alpha = 0.10f),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                            )
                            drawRoundRect(
                                accent,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), -march * 20f)),
                            )
                        }
                        .pointerInput(wPx, hPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (zoom != 1f) scale(zoom)
                                if (pan != Offset.Zero) move(pan.x / wPx, pan.y / hPx)
                            }
                        },
                ) {
                    Image(
                        signature,
                        contentDescription = "Signature",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { sigH.toDp() }),
                    )
                    caption?.let {
                        Text(
                            it,
                            color = Color(0xFF16181D),
                            fontSize = with(density) { (capH * 0.8f).toSp() },
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .height(with(density) { capH.toDp() }),
                        )
                    }
                }
                // The corner handle: pull it to make the signature bigger or smaller.
                val handle = 30.dp
                val handlePx = with(density) { handle.toPx() }
                val footH = sigH + if (caption != null) capGap + capH else 0f
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (box.x * wPx + sigW - handlePx / 2f).roundToInt(),
                                (box.y * hPx + footH - handlePx / 2f).roundToInt(),
                            )
                        }
                        .size(handle)
                        .graphicsLayer {
                            scaleX = glow * land.value
                            scaleY = glow * land.value
                        }
                        .pointerInput(wPx) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                resize(amount.x / wPx)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(2.5.dp, Color.White, CircleShape),
                    )
                }
            }
        }
    }
}

/** The saved signature shown on white, or the pen writing a sample when there is none. */
@Composable
internal fun SignatureSheet(signature: ImageBitmap?, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(if (terminal) 6.dp else 16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(PAD_ASPECT + 0.4f)
            .clip(shape)
            .background(PaperWhite)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (signature != null) {
            val pop = remember(signature) { Animatable(0f) }
            LaunchedEffect(signature) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
            Image(
                signature,
                contentDescription = "Ma signature",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = pop.value.coerceIn(0f, 1f)
                        scaleX = 0.85f + 0.15f * pop.value
                        scaleY = 0.85f + 0.15f * pop.value
                    },
            )
        } else {
            WritingPen(Modifier.fillMaxSize().padding(vertical = 6.dp), ink = Ruler.copy(alpha = 0.8f))
        }
    }
}

/** The initials field: a few capitals, the keyboard opens in capitals. */
@Composable
internal fun InitialsField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.fieldRadius)
    val terminal = LocalSkin.current == Skin.TERMINAL
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(SignPlan.INITIALS_MAX + 2)) },
        singleLine = true,
        textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = tokens.body, letterSpacing = 2.sp),
        cursorBrush = SolidColor(accent.start),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Palette.Surface2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (terminal) "> " else "Initiales  ", color = Palette.TextSecondary, fontSize = 14.sp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("MG", color = Palette.TextTertiary, fontSize = 17.sp, letterSpacing = 2.sp)
                    inner()
                }
            }
        },
    )
}

/** "‹ Page 3 / 5 ›": the page shown, with arrows that bounce; the number rolls when it changes. */
@Composable
internal fun PageStepper(page: Int, pages: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Arrow("‹", enabled = page > 0, color = accent) { onPage(page - 1) }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 2 } + fadeOut())
            },
            label = "signPage",
        ) { p ->
            val text = "Page ${p + 1} / $pages"
            Text(if (terminal) "[ $text ]" else text, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Arrow("›", enabled = page < pages - 1, color = accent) { onPage(page + 1) }
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
