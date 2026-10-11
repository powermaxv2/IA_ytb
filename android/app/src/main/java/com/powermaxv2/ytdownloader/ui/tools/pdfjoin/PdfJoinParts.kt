package com.powermaxv2.ytdownloader.ui.tools.pdfjoin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.SafeNames
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPage
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPlan
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinTool
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinUiState
import com.powermaxv2.ytdownloader.core.pdfjoin.SplitMode
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Pieces of the Assembleur PDF drawn in the three skins: the "Assembler / Séparer / Retirer" chips, the grid of
// pages (long press and slide to move one, the others make room), the bar of the chosen page and the fanned pages
// of the result.

internal fun JoinTool.icon(): ImageVector = when (this) {
    JoinTool.JOIN -> Icons.Rounded.PictureAsPdf
    JoinTool.SPLIT -> Icons.Rounded.CallSplit
    JoinTool.REMOVE -> Icons.Rounded.Delete
}

/** The three jobs of the tool as chips with an icon; the chosen one is filled and pops. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolChips(selected: JoinTool, onSelect: (JoinTool) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JoinTool.entries.forEachIndexed { index, tool ->
            ToolChip(tool.label, tool.icon(), tool == selected, Modifier.staggeredEntrance(index, baseDelay = 20)) { onSelect(tool) }
        }
    }
}

@Composable
private fun ToolChip(text: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 480f))
        }
    }
    val fill = when {
        !selected -> Modifier
        skin == Skin.CLASSIC -> Modifier.background(accent.horizontal(), shape)
        else -> Modifier.background(accent.start, shape)
    }
    val ink by animateColorAsState(
        when {
            !selected -> Palette.TextSecondary
            skin == Skin.TERMINAL -> Palette.Background
            else -> Palette.OnAccent
        },
        tween(200),
        label = "chipInk",
    )
    Row(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .bounceClick(onClick = onClick)
            .height(if (skin == Skin.TERMINAL) 42.dp else 46.dp)
            .clip(shape)
            .then(fill)
            .border(1.dp, if (selected) Color.Transparent else Palette.Stroke, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            if (skin == Skin.TERMINAL) text.uppercase() else text,
            color = ink,
            fontSize = if (skin == Skin.TERMINAL) 13.sp else 15.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = if (skin == Skin.TERMINAL) 1.5.sp else 0.sp,
            maxLines = 1,
        )
    }
}

/** "+ Ajouter" in the header of the pages. */
@Composable
internal fun AddButton(onClick: () -> Unit) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp)
    Row(
        Modifier
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .height(40.dp)
            .clip(shape)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.TextPrimary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (skin == Skin.TERMINAL) "AJOUTER" else "Ajouter",
            color = Palette.TextPrimary,
            fontSize = if (skin == Skin.TERMINAL) 12.5.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (skin == Skin.TERMINAL) 1.5.sp else 0.sp,
        )
    }
}

// MARK: - The grid of pages

private const val COLUMNS = 3

/** Height / width of a cell's page area: a little taller than A4 so a portrait page fills it. */
private const val PAGE_RATIO = 1.32f

/**
 * Every page of [state] in a grid of three. A tap is [onTap]; a long press lifts the page, which follows the finger
 * while the others slide out of its way, and lets it go with [onDrop] (its id, the place it was dropped at).
 */
@Composable
internal fun PageGrid(
    state: JoinUiState,
    thumbs: Map<Long, ImageBitmap>,
    onTap: (Long) -> Unit,
    onDrop: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = state.pages
    val sourceNames = state.sources.map { it.file.name }
    val density = LocalDensity.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val drop by rememberUpdatedState(onDrop)

    // While a page is held (and until the controller has its new order), the grid follows this order.
    val ids = pages.map { it.id }
    val currentIds by rememberUpdatedState(ids)
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    var dragged by remember { mutableStateOf<Long?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(ids) { if (dragged == null) dragOrder = null }
    val order = dragOrder ?: ids

    // Part numbers for Séparer: the first page of each part wears "PDF n".
    val partStarts: Map<Long, Int> = if (state.tool == JoinTool.SPLIT) {
        val pieces = JoinPlan.pieces(pages, state.split, state.cuts)
        pieces.mapIndexed { i, piece -> piece.first().id to i + 1 }.toMap()
    } else {
        emptyMap()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 12.dp
        val rowGap = 10.dp
        val cellW = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val pageH = cellW * PAGE_RATIO
        val labelH = 28.dp
        val cellH = pageH + labelH
        val rows = (pages.size + COLUMNS - 1) / COLUMNS
        val cellWPx = with(density) { (cellW + gap).toPx() }
        val cellHPx = with(density) { (cellH + rowGap).toPx() }

        fun slot(index: Int): Offset = Offset((index % COLUMNS) * cellWPx, (index / COLUMNS) * cellHPx)

        fun slotAt(point: Offset): Int {
            val col = (point.x / cellWPx).toInt().coerceIn(0, COLUMNS - 1)
            val row = (point.y / cellHPx).toInt().coerceAtLeast(0)
            return (row * COLUMNS + col).coerceIn(0, (currentIds.size - 1).coerceAtLeast(0))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(if (rows == 0) 0.dp else cellH * rows + rowGap * (rows - 1)),
        ) {
            pages.forEach { page ->
                val place = order.indexOf(page.id).coerceAtLeast(0)
                val isDragged = dragged == page.id
                val target = slot(place)
                val position = if (isDragged) dragStart + dragDelta else target
                PageCell(
                    page = page,
                    number = place + 1,
                    sourceName = sourceNames.getOrElse(page.source) { "" },
                    thumb = thumbs[page.id],
                    state = state,
                    part = partStarts[page.id],
                    cut = state.tool == JoinTool.SPLIT && state.split == SplitMode.CUTS && page.id in state.cuts && place > 0,
                    lifted = isDragged,
                    position = position,
                    width = cellW,
                    pageHeight = pageH,
                    entrance = place,
                    onTap = { onTap(page.id) },
                    gesture = Modifier.pointerInput(page.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val now = dragOrder ?: currentIds
                                dragOrder = now
                                dragged = page.id
                                dragStart = slot(now.indexOf(page.id).coerceAtLeast(0))
                                dragDelta = Offset.Zero
                                view.tick(haptics)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDelta += amount
                                val now = dragOrder ?: currentIds
                                val center = dragStart + dragDelta + Offset(cellWPx / 2, cellHPx / 3)
                                val to = slotAt(center)
                                val from = now.indexOf(page.id)
                                if (from >= 0 && to != from) {
                                    dragOrder = now.toMutableList().apply { add(to, removeAt(from)) }
                                    view.tick(haptics)
                                }
                            },
                            onDragEnd = {
                                val now = dragOrder ?: currentIds
                                val to = now.indexOf(page.id)
                                dragged = null
                                if (now == currentIds) dragOrder = null
                                if (to >= 0) drop(page.id, to)
                            },
                            onDragCancel = {
                                dragged = null
                                dragOrder = null
                            },
                        )
                    },
                )
            }
        }
    }
}

/** One page of the grid: the picture (turned with a spring), its number and the file it comes from. */
@Composable
private fun PageCell(
    page: JoinPage,
    number: Int,
    sourceName: String,
    thumb: ImageBitmap?,
    state: JoinUiState,
    part: Int?,
    cut: Boolean,
    lifted: Boolean,
    position: Offset,
    width: Dp,
    pageHeight: Dp,
    entrance: Int,
    onTap: () -> Unit,
    gesture: Modifier,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val terminal = skin == Skin.TERMINAL
    val selected = state.tool == JoinTool.JOIN && state.selected == page.id
    val marked = state.tool == JoinTool.REMOVE && page.id in state.marked

    // Glide to its place (the dragged page sticks to the finger instead).
    val offset = remember { Animatable(position, Offset.VectorConverter) }
    LaunchedEffect(position, lifted) {
        if (lifted) offset.snapTo(position) else offset.animateTo(position, spring(dampingRatio = 0.78f, stiffness = 420f))
    }
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    val markScale by animateFloatAsState(if (marked) 0.86f else 1f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "mark")
    val fade by animateFloatAsState(if (marked) 0.35f else 1f, tween(220), label = "markFade")

    // The angle only ever turns clockwise, a quarter at a time: 270° → 0° keeps going round.
    val angle = remember { Animatable(page.rotation.toFloat()) }
    LaunchedEffect(page.rotation) {
        val current = ((angle.value % 360f) + 360f) % 360f
        val step = ((page.rotation - current) % 360f + 360f) % 360f
        if (step != 0f) angle.animateTo(angle.value + step, spring(dampingRatio = 0.62f, stiffness = 260f))
    }

    // Selection ring pops in.
    val ring by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 520f), label = "ring")

    Column(
        Modifier
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .zIndex(if (lifted) 2f else if (offset.isRunning) 1f else 0f)
            .width(width)
            .staggeredEntrance(entrance, key = page.id, baseDelay = 60)
            .graphicsLayer {
                val s = (1f + 0.08f * lift) * (if (ring > 0f) 1f + 0.02f * ring else 1f)
                scaleX = s
                scaleY = s
                rotationZ = -2.5f * lift
            }
            .then(gesture)
            .bounceClick(pressedScale = 0.95f, onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(pageHeight),
            contentAlignment = Alignment.Center,
        ) {
            // The page fitted in the cell for its current angle (a landscape page turned upright fills more of it).
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val rad = Math.toRadians(angle.value.toDouble())
                val c = abs(cos(rad)).toFloat()
                val s = abs(sin(rad)).toFloat()
                val w = page.widthPt.coerceAtLeast(1f)
                val h = page.heightPt.coerceAtLeast(1f)
                val fit = min(maxWidth.value / (w * c + h * s), maxHeight.value / (w * s + h * c))
                val radius = when (skin) {
                    Skin.CLASSIC -> 10.dp
                    Skin.PAPER -> 8.dp
                    Skin.TERMINAL -> 3.dp
                }
                val shape = RoundedCornerShape(radius)
                Box(
                    Modifier
                        .size((w * fit).dp, (h * fit).dp)
                        .graphicsLayer {
                            rotationZ = angle.value
                            scaleX = markScale
                            scaleY = markScale
                            alpha = fade
                        }
                        .shadow(if (skin == Skin.PAPER) 3.dp + 14.dp * lift else 10.dp * lift, shape, clip = false)
                        .clip(shape)
                        .background(Color.White),
                ) {
                    if (thumb != null) {
                        Image(thumb, contentDescription = "Page $number", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    } else {
                        BlankPage(Modifier.fillMaxSize())
                    }
                }
                if (ring > 0.01f) {
                    Box(
                        Modifier
                            .size((w * fit).dp + 10.dp, (h * fit).dp + 10.dp)
                            .graphicsLayer {
                                rotationZ = angle.value
                                alpha = ring
                                scaleX = 0.9f + 0.1f * ring
                                scaleY = 0.9f + 0.1f * ring
                            }
                            .border(3.dp, accent.start, RoundedCornerShape(radius + 5.dp)),
                    )
                }
            }
            // Retirer: a big × over the page left out.
            AnimatedVisibility(
                marked,
                enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 520f), initialScale = 0.3f) + fadeIn(),
                exit = scaleOut(targetScale = 0.4f) + fadeOut(),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Palette.Danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Page retirée", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            // Séparer: "PDF n" on the first page of each part, scissors where the user cut.
            if (part != null) {
                PartBadge(part, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 6.dp))
            }
            if (cut) CutMark(pageHeight, Modifier.align(Alignment.CenterStart).offset(x = (-9).dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                color = if (selected) accent.start else Palette.TextPrimary,
                fontSize = if (terminal) 14.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (marked) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                shortName(sourceName),
                color = Palette.TextTertiary,
                fontSize = if (terminal) 12.5.sp else 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** "facture-mars.pdf" → "facture-mars", "IMG_2041.jpg" → "photo". */
internal fun shortName(name: String): String {
    val ext = SafeNames.extensionOf(name)
    if (ext != "pdf" && ext.isNotEmpty()) return "photo"
    return SafeNames.baseOf(name).ifBlank { name }
}

/** A page whose picture is not drawn yet: a title bar and lines that shimmer. */
@Composable
private fun BlankPage(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "blank")
    val glow by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blankGlow")
    val accent = LocalAccent.current.start
    Canvas(modifier.padding(10.dp)) {
        val bar = size.height * 0.035f
        drawRoundRect(accent.copy(alpha = 0.55f * glow), size = Size(size.width * 0.45f, bar * 1.3f), cornerRadius = CornerRadius(bar))
        val grey = Color(0xFFD9D2CC).copy(alpha = glow)
        listOf(0.7f, 0.92f, 0.6f, 0.86f, 0.5f).forEachIndexed { i, f ->
            val y = bar * 4f + i * bar * 2.4f
            drawRoundRect(grey, topLeft = Offset(0f, y), size = Size(size.width * f, bar), cornerRadius = CornerRadius(bar))
        }
    }
}

@Composable
private fun PartBadge(part: Int, modifier: Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pop = remember(part) { Animatable(0.4f) }
    LaunchedEffect(part) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f)) }
    Text(
        if (terminal) "PDF_$part" else "PDF $part",
        color = if (terminal) Palette.Background else Palette.OnAccent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .background(accent.start)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Dashed line and scissors in the gap before a page that starts a new PDF. */
@Composable
private fun CutMark(height: Dp, modifier: Modifier) {
    val accent = LocalAccent.current.start
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
    Box(modifier.width(18.dp).height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleY = pop.value }) {
            drawLine(
                accent,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
            )
        }
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = (1f - pop.value) * -90f
                }
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ContentCut, contentDescription = "Nouveau PDF ici", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

// MARK: - The chosen page

/** "Page 3 choisie" with ← → (move), turn and remove. */
@Composable
internal fun SelectionBar(
    number: Int,
    count: Int,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRotate: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val tokens = LocalTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.fieldRadius))
            .background(Palette.Surface2)
            .padding(start = 16.dp, end = 6.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (terminal) "> page $number" else "Page $number choisie",
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        BarIcon(Icons.Rounded.ChevronLeft, "Mettre la page plus tôt", Palette.TextSecondary, enabled = number > 1, onClick = onLeft)
        BarIcon(Icons.Rounded.ChevronRight, "Mettre la page plus tard", Palette.TextSecondary, enabled = number < count, onClick = onRight)
        BarIcon(Icons.Rounded.RotateRight, "Tourner la page", LocalAccent.current.start, onClick = onRotate)
        BarIcon(Icons.Rounded.Delete, "Retirer la page", Palette.Danger, onClick = onRemove)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    // Each tap gives the icon a little turn or nudge, so the action is felt.
    var taps by remember { mutableStateOf(0) }
    val spin by animateFloatAsState(
        if (icon == Icons.Rounded.RotateRight) taps * 90f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "barSpin",
    )
    Box(
        Modifier
            .size(44.dp)
            .bounceClick(enabled = enabled, pressedScale = 0.82f) {
                taps++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = spin },
        )
    }
}

// MARK: - Result

/**
 * Up to three pages of the result fanned out like papers on a desk: they fly up one after the other, settle with a
 * spring and keep floating gently.
 */
@Composable
internal fun FannedPages(covers: List<ImageBitmap?>, modifier: Modifier = Modifier) {
    val shown = covers.take(3).ifEmpty { listOf(null) }
    val skin = LocalSkin.current
    val float = rememberInfiniteTransition(label = "fan")
    val bob by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fanBob")
    Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        // Last drawn on top: the first page of the result.
        shown.indices.reversed().forEach { i ->
            val depth = shown.size - 1 - i
            val fly = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(120L + depth * 110L)
                fly.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 180f))
            }
            val angle = when (depth) {
                0 -> 6f
                1 -> -3f
                else -> -9f
            }
            val dx = when (depth) {
                0 -> 26.dp
                1 -> 0.dp
                else -> (-26).dp
            }
            val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 3.dp else 10.dp)
            Box(
                Modifier
                    .size(118.dp, 160.dp)
                    .graphicsLayer {
                        val p = fly.value
                        translationX = dx.toPx() * p
                        translationY = (1f - p) * 140.dp.toPx() + bob * (2 + depth).dp.toPx()
                        rotationZ = angle * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .shadow(12.dp, shape)
                    .clip(shape)
                    .background(Color.White),
            ) {
                val image = shown[i]
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    BlankPage(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** A line of the result card: a PDF or photo tile, the name and where its pages are. */
@Composable
internal fun ResultLine(name: String, pdf: Boolean, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 420)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.powermaxv2.ytdownloader.ui.tools.convert.KindTile(if (pdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (terminal) name.lowercase() else name,
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(if (terminal) "# $detail" else detail, color = Palette.TextSecondary, fontSize = 13.5.sp, maxLines = 2)
        }
    }
}
