package com.powermaxv2.ytdownloader.ui.tools.blur

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.blur.BlurEffect
import com.powermaxv2.ytdownloader.core.blur.BlurPlan
import com.powermaxv2.ytdownloader.core.blur.BlurZone
import com.powermaxv2.ytdownloader.core.blur.FaceSearch
import com.powermaxv2.ytdownloader.core.blur.ZoneShape
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlin.math.abs
import kotlin.math.min

// Pieces of "Flouter une photo": the stage where zones are drawn, moved and pulled with the finger (dashes that
// march, a pop when a zone lands, a scan line while faces are searched), the zone counter, the three effect tiles
// (each a little face being hidden in a loop) and the looping demo of the empty screen.

@Composable
internal fun rememberImage(bitmap: Bitmap?): ImageBitmap? = remember(bitmap) { bitmap?.asImageBitmap() }

private enum class DragMode { DRAW, MOVE, RESIZE }

/**
 * The photo with its hidden zones, and the gestures on it: drag on an empty part to draw a zone, drag a zone to
 * move it, pull the round handle of the chosen zone to size it, tap a zone to choose it (tap elsewhere: none).
 */
@Composable
internal fun BlurStage(
    preview: Bitmap?,
    aspect: Float,
    zones: List<BlurZone>,
    selected: Int?,
    searching: Boolean,
    busy: Boolean,
    actions: BlurActions,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val corner = if (terminal) 4.dp else 16.dp
    val image = rememberImage(preview)
    val ratio = aspect.coerceIn(0.4f, 2.5f)
    // Tall photos are kept to a phone's height, centred.
    val maxWidth = if (ratio < 0.8f) 340.dp else 600.dp

    val transition = rememberInfiniteTransition(label = "blurStage")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "march")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scan")
    val breathe by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "breathe")

    // The newest zone lands with a spring.
    val pop = remember { Animatable(1f) }
    val newest = zones.maxOfOrNull { it.id }
    LaunchedEffect(newest) {
        if (newest != null) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
    }

    var draft by remember { mutableStateOf<Rect?>(null) }
    val current by rememberUpdatedState(zones)
    val chosen by rememberUpdatedState(selected)
    val act by rememberUpdatedState(actions)

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(corner))
                .background(Palette.TextPrimary.copy(alpha = 0.06f)),
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val handleSlop = with(density) { 30.dp.toPx() }
            if (image != null) {
                Image(
                    image,
                    contentDescription = "Photo à flouter",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (busy) 0.6f + 0.4f * breathe else 1f },
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(wPx, hPx) {
                        detectTapGestures { at ->
                            act.onSelect(BlurPlan.hit(current, at.x / wPx, at.y / hPx, slop = 0.02f)?.id)
                        }
                    }
                    .pointerInput(wPx, hPx) {
                        var mode = DragMode.DRAW
                        var target = 0
                        var start = Offset.Zero
                        var end = Offset.Zero
                        detectDragGestures(
                            onDragStart = { at ->
                                val sel = current.firstOrNull { it.id == chosen }
                                val hit = BlurPlan.hit(current, at.x / wPx, at.y / hPx)
                                when {
                                    sel != null && abs(at.x - sel.right * wPx) < handleSlop && abs(at.y - sel.bottom * hPx) < handleSlop -> {
                                        mode = DragMode.RESIZE
                                        target = sel.id
                                        act.onBeginEdit()
                                    }
                                    hit != null -> {
                                        mode = DragMode.MOVE
                                        target = hit.id
                                        act.onBeginEdit()
                                        act.onSelect(hit.id)
                                    }
                                    else -> {
                                        mode = DragMode.DRAW
                                        start = at
                                        end = at
                                        draft = Rect(at, at)
                                    }
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                when (mode) {
                                    DragMode.MOVE -> act.onMove(target, amount.x / wPx, amount.y / hPx)
                                    DragMode.RESIZE -> act.onResize(target, amount.x / wPx, amount.y / hPx)
                                    DragMode.DRAW -> {
                                        end += amount
                                        draft = Rect(
                                            Offset(min(start.x, end.x), min(start.y, end.y)),
                                            Offset(maxOf(start.x, end.x), maxOf(start.y, end.y)),
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (mode == DragMode.DRAW) act.onDraw(start.x / wPx, start.y / hPx, end.x / wPx, end.y / hPx)
                                draft = null
                            },
                            onDragCancel = { draft = null },
                        )
                    },
            ) {
                val dash = 10.dp.toPx()
                for (zone in zones) {
                    val isSel = zone.id == selected
                    val scale = if (zone.id == newest) pop.value else 1f
                    val w = zone.w * size.width * scale
                    val h = zone.h * size.height * scale
                    val topLeft = Offset(zone.centerX * size.width - w / 2f, zone.centerY * size.height - h / 2f)
                    val stroke = Stroke(
                        (if (isSel) 2.5f else 1.6f).dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), -march * dash * 1.7f),
                    )
                    // A dark halo under the dashes keeps them visible on a light photo.
                    drawZoneShape(zone.shape, topLeft, Size(w, h), Color.Black.copy(alpha = 0.35f), Stroke(stroke.width + 2.dp.toPx()), terminal)
                    if (isSel) drawZoneShape(zone.shape, topLeft, Size(w, h), accent.copy(alpha = 0.16f), null, terminal)
                    drawZoneShape(zone.shape, topLeft, Size(w, h), if (isSel) accent else Color.White, stroke, terminal)
                    if (isSel) {
                        val handle = Offset(topLeft.x + w, topLeft.y + h)
                        drawCircle(accent.copy(alpha = 0.25f * breathe), 18.dp.toPx(), handle)
                        drawCircle(Color.White, 9.dp.toPx(), handle)
                        drawCircle(accent, 6.5.dp.toPx(), handle)
                    }
                }
                draft?.let { r ->
                    drawRect(Color.White.copy(alpha = 0.22f), r.topLeft, r.size)
                    drawRect(
                        accent,
                        r.topLeft,
                        r.size,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), -march * dash * 1.7f)),
                    )
                }
                if (searching) {
                    val y = scan * size.height
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), startY = y - 60.dp.toPx(), endY = y),
                        Offset(0f, y - 60.dp.toPx()),
                        Size(size.width, 60.dp.toPx()),
                    )
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawZoneShape(shape: ZoneShape, topLeft: Offset, size: Size, color: Color, stroke: Stroke?, square: Boolean) {
    val style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill
    when (shape) {
        ZoneShape.OVAL -> drawOval(color, topLeft, size, style = style)
        ZoneShape.RECT -> drawRoundRect(color, topLeft, size, CornerRadius(if (square) 0f else 6.dp.toPx()), style = style)
    }
}

/** "2 zones cachées", the number rolling up or down; a line under it says what to do or what the face search gave. */
@Composable
internal fun ZoneCounter(count: Int, faces: FaceSearch) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "zoneCount",
        ) { n ->
            val text = BlurPlan.zonesText(n)
            Text(
                if (terminal) "[$n] ${text.lowercase()}" else text,
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        val hint = when {
            faces is FaceSearch.Found && faces.added == 0 -> "Aucun nouveau visage trouvé : dessine la zone au doigt."
            faces is FaceSearch.Found -> if (faces.added == 1) "1 visage trouvé et caché." else "${faces.added} visages trouvés et cachés."
            count == 0 -> "Glisse le doigt sur la photo pour cacher une zone."
            else -> "Touche une zone pour la déplacer, tire son rond pour l'agrandir."
        }
        Text(terminalize(hint), color = Palette.TextSecondary, fontSize = 12.5.sp)
    }
}

/** The three ways to hide, each tile a little face hidden in a loop; the chosen one is outlined and pops. */
@Composable
internal fun EffectTiles(selected: BlurEffect, onSelect: (BlurEffect) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BlurEffect.entries.forEach { effect ->
            EffectTile(effect, effect == selected, Modifier.weight(1f)) { onSelect(effect) }
        }
    }
}

@Composable
private fun EffectTile(effect: BlurEffect, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) LocalTokens.current.cardRadius else 16.dp)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(chosen) {
        if (chosen) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    val outline by animateColorAsState(if (chosen) accent else Palette.Stroke, tween(200), label = "tileOutline")
    val transition = rememberInfiniteTransition(label = "tile")
    val t by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tileT",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(shape)
            .background(if (chosen) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(if (chosen) 2.dp else 1.dp, outline, shape)
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = chosen
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 2.dp else 12.dp)),
        ) {
            drawRect(SKY)
            val face = Offset(size.width / 2f, size.height * 0.52f)
            val r = size.minDimension * 0.3f
            drawFace(face, r)
            // Animated only while chosen: the others show the effect at rest.
            hideFace(effect, face, r, if (chosen) t else 1f)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (skin == Skin.TERMINAL) effect.label.uppercase() else effect.label,
            color = if (chosen) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private val SKY = Color(0xFF9AD3F5)
private val SKIN = Color(0xFFF2C29B)
private val HAIR = Color(0xFF4A3426)
private val INK = Color(0xFF1D1F24)

/** A cartoon face: hair, head, eyes and a smile. */
private fun DrawScope.drawFace(c: Offset, r: Float) {
    drawCircle(Color(0xFF5C8DF0), r * 1.25f, Offset(c.x, c.y + r * 1.75f))
    drawCircle(HAIR, r * 1.06f, Offset(c.x, c.y - r * 0.12f))
    drawCircle(SKIN, r, c)
    drawCircle(INK, r * 0.11f, Offset(c.x - r * 0.36f, c.y - r * 0.12f))
    drawCircle(INK, r * 0.11f, Offset(c.x + r * 0.36f, c.y - r * 0.12f))
    drawArc(INK, 20f, 140f, false, Offset(c.x - r * 0.38f, c.y - r * 0.05f), Size(r * 0.76f, r * 0.55f), style = Stroke(r * 0.09f, cap = StrokeCap.Round))
}

/** Colour of the cartoon face around [c] at a point: what a mosaic of it shows. */
private fun faceColor(p: Offset, c: Offset, r: Float): Color {
    val dx = p.x - c.x
    val dy = p.y - c.y
    val d2 = dx * dx + dy * dy
    return when {
        abs(dy + r * 0.12f) < r * 0.2f && abs(abs(dx) - r * 0.36f) < r * 0.2f -> Color(0xFF8B6A55)
        d2 < r * r -> SKIN
        d2 < (r * 1.06f) * (r * 1.06f) && dy < 0 -> HAIR
        else -> SKY
    }
}

/** Hides the face at [c] with [effect], [t] from 0 (not yet) to 1 (fully hidden). */
private fun DrawScope.hideFace(effect: BlurEffect, c: Offset, r: Float, t: Float) {
    val box = Rect(c.x - r * 1.15f, c.y - r * 1.25f, c.x + r * 1.15f, c.y + r * 1.1f)
    when (effect) {
        BlurEffect.PIXELS -> {
            if (t < 0.05f) return
            val cells = (14 - 9 * t).toInt().coerceAtLeast(4)
            val cw = box.width / cells
            val ch = box.height / cells
            for (i in 0 until cells) for (j in 0 until cells) {
                val o = Offset(box.left + i * cw, box.top + j * ch)
                drawRect(faceColor(o + Offset(cw / 2f, ch / 2f), c, r).copy(alpha = t.coerceIn(0f, 1f)), o, Size(cw + 0.5f, ch + 0.5f))
            }
        }
        BlurEffect.BLUR -> {
            val oval = Path().apply { addOval(box) }
            clipPath(oval) {
                drawRect(Brush.radialGradient(listOf(SKIN, SKIN.copy(alpha = 0.85f), HAIR.copy(alpha = 0.6f)), c, r * 1.3f), box.topLeft, box.size, alpha = t)
                drawCircle(Color.White.copy(alpha = 0.18f * t), r * 0.7f, Offset(c.x - r * 0.3f, c.y - r * 0.4f))
            }
        }
        BlurEffect.BAR -> {
            val w = box.width * t
            drawRoundRect(INK, Offset(box.left, c.y - r * 0.32f), Size(w, r * 0.42f), CornerRadius(r * 0.08f))
        }
    }
}

/**
 * The looping demo of the empty screen: a street photo; a finger draws a box over the car's plate, which turns to
 * pixels; a scan line passes and the face is found and blurred under an oval; then it starts over.
 */
@Composable
internal fun BlurDemo(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val measurer = rememberTextMeasurer()
    val transition = rememberInfiniteTransition(label = "blurDemo")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "demoTime")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "demoMarch")
    fun phase(from: Float, to: Float) = ((time - from) / (to - from)).coerceIn(0f, 1f)
    fun ease(x: Float) = x * x * (3f - 2f * x)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(if (terminal) 4.dp else 14.dp)),
    ) {
        val w = size.width
        val h = size.height
        val fade = 1f - phase(0.93f, 1f)
        // The street.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF7FC6F0), Color(0xFFFCE3B6))))
        drawRect(Color(0xFF8E9AA6), Offset(0f, h * 0.74f), Size(w, h * 0.26f))
        drawRect(Color(0xFFEDEFF2), Offset(0f, h * 0.84f), Size(w, h * 0.02f))
        // The person.
        val face = Offset(w * 0.22f, h * 0.38f)
        val r = h * 0.12f
        drawFace(face, r)
        // The car and its plate.
        val car = Rect(w * 0.44f, h * 0.48f, w * 0.94f, h * 0.8f)
        drawRoundRect(Color(0xFFE5484D), Offset(car.left + car.width * 0.18f, car.top - car.height * 0.32f), Size(car.width * 0.6f, car.height * 0.5f), CornerRadius(h * 0.06f))
        drawRoundRect(Color(0xFFB9E3FA), Offset(car.left + car.width * 0.26f, car.top - car.height * 0.24f), Size(car.width * 0.44f, car.height * 0.3f), CornerRadius(h * 0.03f))
        drawRoundRect(Color(0xFFE5484D), car.topLeft, car.size, CornerRadius(h * 0.05f))
        drawCircle(INK, h * 0.07f, Offset(car.left + car.width * 0.2f, car.bottom))
        drawCircle(INK, h * 0.07f, Offset(car.right - car.width * 0.2f, car.bottom))
        val plate = Rect(car.center.x - car.width * 0.2f, car.top + car.height * 0.42f, car.center.x + car.width * 0.2f, car.top + car.height * 0.78f)
        drawRoundRect(Color.White, plate.topLeft, plate.size, CornerRadius(h * 0.01f))
        drawRect(Color(0xFF2F56C9), plate.topLeft, Size(plate.width * 0.1f, plate.height))
        val label = measurer.measure("AB-123-CD", TextStyle(color = INK, fontSize = (plate.height * 0.55f).toSp(), fontWeight = FontWeight.Bold))
        drawText(label, topLeft = Offset(plate.left + plate.width * 0.55f - label.size.width / 2f, plate.center.y - label.size.height / 2f))

        val dash = 8.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), -march * dash * 1.7f)
        // 1. The finger draws a box over the plate.
        val zone = Rect(plate.left - h * 0.02f, plate.top - h * 0.02f, plate.right + h * 0.02f, plate.bottom + h * 0.02f)
        val drawT = ease(phase(0.08f, 0.32f))
        if (time in 0.06f..0.36f) {
            val corner = Offset(zone.left + zone.width * drawT, zone.top + zone.height * drawT)
            drawRect(Color.White.copy(alpha = 0.25f), zone.topLeft, Size(corner.x - zone.left, corner.y - zone.top))
            drawRect(accent, zone.topLeft, Size(corner.x - zone.left, corner.y - zone.top), style = Stroke(2.dp.toPx(), pathEffect = dashes))
            drawCircle(Color.White.copy(alpha = 0.5f), h * 0.07f, corner)
            drawCircle(Color.White, h * 0.035f, corner)
        }
        // 2. The plate turns to pixels.
        val pixT = phase(0.34f, 0.42f) * fade
        if (pixT > 0f) {
            val cols = 8
            val rows = 3
            val cw = zone.width / cols
            val ch = zone.height / rows
            for (i in 0 until cols) for (j in 0 until rows) {
                val grey = if (i == 0) Color(0xFF6E86D6) else listOf(Color(0xFFDADCE0), Color(0xFFB8BCC4), Color(0xFFEEEFF1), Color(0xFFA9AEB7))[(i * 7 + j * 3) % 4]
                val appear = ((pixT * (cols + rows)) - (i + j) * 0.5f).coerceIn(0f, 1f)
                drawRect(grey.copy(alpha = appear), Offset(zone.left + i * cw, zone.top + j * ch), Size(cw + 0.5f, ch + 0.5f))
            }
            drawRect(Color.White.copy(alpha = fade), zone.topLeft, zone.size, style = Stroke(1.6f.dp.toPx(), pathEffect = dashes))
        }
        // 3. A scan line looks for faces.
        val scanT = phase(0.46f, 0.64f)
        if (scanT > 0f && scanT < 1f) {
            val y = ease(scanT) * h
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), startY = y - h * 0.18f, endY = y), Offset(0f, y - h * 0.18f), Size(w, h * 0.18f))
            drawLine(accent, Offset(0f, y), Offset(w, y), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        // 4. The face is found: an oval pops and blurs it.
        val faceT = phase(0.58f, 0.7f) * fade
        if (faceT > 0f) {
            val pop = 1f + 0.25f * (1f - faceT) * kotlin.math.sin(faceT * 9f)
            hideFace(BlurEffect.BLUR, face, r, faceT)
            val ow = r * 2.3f * pop
            val oh = r * 2.35f * pop
            drawOval(Color.White.copy(alpha = fade), Offset(face.x - ow / 2f, face.y - r * 1.25f * pop), Size(ow, oh), style = Stroke(1.8f.dp.toPx(), pathEffect = dashes))
        }
        // 5. "Caché" stamp.
        val stampT = phase(0.74f, 0.8f) * fade
        if (stampT > 0f) {
            val badge = measurer.measure(if (terminal) "[OK] 2 ZONES" else "2 zones cachées", TextStyle(color = Color.White, fontSize = (h * 0.065f).toSp(), fontWeight = FontWeight.Bold))
            val pad = h * 0.03f
            val bw = badge.size.width + pad * 2
            val bh = badge.size.height + pad
            val s = 0.6f + 0.4f * stampT
            val origin = Offset(w - bw * s - h * 0.04f, h * 0.05f)
            drawRoundRect(accent.copy(alpha = stampT), origin, Size(bw * s, bh * s), CornerRadius(bh))
            if (stampT > 0.6f) drawText(badge, topLeft = Offset(origin.x + pad * s, origin.y + pad * 0.5f * s), alpha = stampT)
        }
    }
}
