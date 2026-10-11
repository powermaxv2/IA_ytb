package com.powermaxv2.ytdownloader.ui.tools.sticker

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.sticker.SavedPack
import com.powermaxv2.ytdownloader.core.sticker.StickerRules
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlin.math.PI
import kotlin.math.sin

// Pieces of the Stickers tool: its icon, the big sticker on its checkerboard (it floats, flips when it changes and
// takes magic-wand touches), the strip of the pack, the emoji row, the saved packs and the pack once made.

/** A sticker with its corner peeling off. */
val StickerIcon: ImageVector by lazy {
    ImageVector.Builder("Stickers", 24.dp, 24.dp, 24f, 24f).apply {
        // Body: a rounded square whose bottom-right corner is folded back.
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 3f)
            lineTo(17f, 3f)
            arcTo(4f, 4f, 0f, false, true, 21f, 7f)
            lineTo(21f, 13f)
            lineTo(13f, 21f)
            lineTo(7f, 21f)
            arcTo(4f, 4f, 0f, false, true, 3f, 17f)
            lineTo(3f, 7f)
            arcTo(4f, 4f, 0f, false, true, 7f, 3f)
            close()
        }
        // The peeled corner.
        path(fill = SolidColor(Color.Black), fillAlpha = 0.55f) {
            moveTo(21f, 14.6f)
            lineTo(14.6f, 21f)
            lineTo(14.6f, 17.6f)
            arcTo(3f, 3f, 0f, false, true, 17.6f, 14.6f)
            close()
        }
    }.build()
}

/** Squares showing through the transparent parts of a sticker, in the skin's colours. */
private fun Modifier.checkerboard(light: Color, dark: Color, cell: Dp = 12.dp): Modifier = drawBehind {
    val c = cell.toPx()
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else c
        while (x < size.width) {
            drawRect(dark, Offset(x, y), Size(c, c))
            x += 2 * c
        }
        y += c
        row++
    }
}

/**
 * The selected sticker, big, on a checkerboard. It floats (Classique), sits on a paper shadow that lifts (Papier)
 * or shows its pixels behind a scanline (Terminal); each new drawing flips in. [onTouch] (0..1 of the sticker)
 * makes it a magic-wand target.
 */
@Composable
internal fun StickerStage(
    sticker: Bitmap?,
    key: Any?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onTouch: ((Float, Float) -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val float = rememberInfiniteTransition(label = "stickerFloat")
    val phase by float.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "stickerPhase")
    val flip = remember { Animatable(0f) }
    LaunchedEffect(key) {
        flip.snapTo(if (skin == Skin.TERMINAL) 0f else 70f)
        flip.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 24.dp)
    val (light, dark) = when (skin) {
        Skin.CLASSIC -> Color(0xFF1C1C22) to Color(0xFF24242C)
        Skin.PAPER -> Color(0xFFFFFCF5) to Color(0xFFF1EADC)
        Skin.TERMINAL -> Color(0xFF050805) to Color(0xFF0B140B)
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .checkerboard(light, dark)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start.copy(alpha = 0.5f), shape) else Modifier)
            .then(
                if (onTouch != null) {
                    Modifier.pointerInput(onTouch) {
                        detectTapGestures { p -> onTouch(p.x / size.width, p.y / size.height) }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (sticker == null) {
            Box(
                Modifier
                    .fillMaxSize(0.62f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.TextPrimary.copy(alpha = 0.06f))
                    .shimmer(),
            )
        } else {
            val image = remember(sticker) { sticker.asImageBitmap() }
            val wave = sin(phase * 2 * PI).toFloat()
            if (skin == Skin.PAPER) {
                // The paper shadow under the sticker, offset as if one corner were lifted.
                Box(
                    Modifier
                        .fillMaxSize(0.84f)
                        .graphicsLayer {
                            translationX = 10.dp.toPx() + wave * 2.dp.toPx()
                            translationY = 12.dp.toPx()
                            rotationZ = -2f
                        }
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0x22000000)),
                )
            }
            Image(
                image,
                contentDescription = "Aperçu du sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.84f)
                    .graphicsLayer {
                        cameraDistance = 14 * density
                        rotationY = flip.value
                        when (skin) {
                            Skin.CLASSIC -> {
                                translationY = wave * 6.dp.toPx()
                                rotationZ = wave * 2.5f
                            }
                            Skin.PAPER -> rotationZ = -2f + wave * 0.8f
                            Skin.TERMINAL -> Unit
                        }
                    },
            )
            if (skin == Skin.TERMINAL) {
                // A scanline sweeping down.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val y = size.height * phase
                            drawRect(accent.start.copy(alpha = 0.10f), Offset(0f, y), Size(size.width, 3.dp.toPx()))
                        },
                )
            }
        }
        caption?.let {
            Text(
                if (skin == Skin.TERMINAL) "> $it" else it,
                color = if (skin == Skin.PAPER) Palette.TextSecondary else Color.White.copy(alpha = 0.8f),
                fontSize = 11.5.sp,
                fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (skin == Skin.PAPER) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** The stickers of the pack in a row: the selected one is outlined and lifted; [onAdd] adds a photo. */
@Composable
internal fun StickerStrip(thumbs: List<Bitmap>, count: Int, selected: Int, onSelect: (Int) -> Unit, onAdd: (() -> Unit)?) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val chosen = i == selected
            val lift by animateLift(chosen)
            val shape = RoundedCornerShape(if (terminal) 4.dp else 16.dp)
            Box(
                Modifier
                    .staggeredEntrance(i, baseDelay = 60)
                    .size(64.dp)
                    .graphicsLayer {
                        translationY = -lift * 4.dp.toPx()
                        scaleX = 1f + lift * 0.06f
                        scaleY = 1f + lift * 0.06f
                    }
                    .clip(shape)
                    .background(Palette.TextPrimary.copy(alpha = 0.06f))
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) accent.start else Palette.Stroke, shape)
                    .bounceClick(pressedScale = 0.9f) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                val thumb = thumbs.getOrNull(i)
                if (thumb != null) {
                    val image = remember(thumb) { thumb.asImageBitmap() }
                    Image(image, contentDescription = "Sticker ${i + 1}", modifier = Modifier.fillMaxSize().padding(4.dp))
                } else {
                    Box(Modifier.fillMaxSize().shimmer())
                }
            }
        }
        if (onAdd != null) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(if (terminal) 4.dp else 16.dp))
                    .drawBehind {
                        drawRoundRect(
                            accent.start.copy(alpha = 0.7f),
                            cornerRadius = CornerRadius(if (terminal) 4.dp.toPx() else 16.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    .bounceClick(pressedScale = 0.9f, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Ajouter une photo", tint = accent.start, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun animateLift(on: Boolean): androidx.compose.runtime.State<Float> =
    androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "stripLift")

/** The emojis WhatsApp files the stickers under; the chosen one pops. */
@Composable
internal fun EmojiRow(selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StickerRules.EMOJIS.forEach { emoji ->
            val chosen = emoji == selected
            val pop by androidx.compose.animation.core.animateFloatAsState(if (chosen) 1.18f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "emojiPop")
            Box(
                Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    }
                    .clip(CircleShape)
                    .background(if (chosen) accent.start.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (chosen) accent.start else Color.Transparent, CircleShape)
                    .bounceClick(pressedScale = 0.85f) { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 19.sp)
            }
        }
    }
}

/** A pack already made: its tray icon, its name and size; touch to open it, the bin to delete it. */
@Composable
internal fun PackRow(saved: SavedPack, index: Int, onOpen: () -> Unit, onDelete: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pack = saved.pack
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 120)
            .bounceClick(pressedScale = 0.98f, onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = remember(saved.tray) { saved.tray?.asImageBitmap() }
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(if (terminal) 4.dp else 14.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
            else Icon(StickerIcon, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (terminal) pack.name.uppercase() else pack.name,
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${pack.emoji}  ${StickerRules.countText(pack.stickers.size, pack.animated)} · ${StickerRules.kiloText(pack.bytes.toInt())}",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .bounceClick(pressedScale = 0.85f, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer le pack", tint = Palette.TextTertiary, modifier = Modifier.size(21.dp))
        }
    }
}

/**
 * The finished pack: stickers dealt in a grid, each one landing with a spring and then floating on its own beat
 * (Classique), slightly askew on paper (Papier) or with its weight under it (Terminal).
 */
@Composable
internal fun PackGrid(stickers: List<Bitmap>, sizes: List<Int>) {
    val skin = LocalSkin.current
    val float = rememberInfiniteTransition(label = "packFloat")
    val phase by float.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "packPhase")
    val images: List<ImageBitmap> = remember(stickers) { stickers.map { it.asImageBitmap() } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = 3
        val gap = 10.dp
        val cell = (maxWidth - gap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            images.chunked(columns).forEachIndexed { row, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { col, image ->
                        val i = row * columns + col
                        val drop = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(90L * i)
                            drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
                        }
                        val wave = sin((phase + i * 0.17f) * 2 * PI).toFloat()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                image,
                                contentDescription = "Sticker ${i + 1}",
                                modifier = Modifier
                                    .size(cell)
                                    .graphicsLayer {
                                        val d = drop.value
                                        alpha = d.coerceIn(0f, 1f)
                                        scaleX = 0.4f + 0.6f * d
                                        scaleY = 0.4f + 0.6f * d
                                        rotationZ = when (skin) {
                                            Skin.CLASSIC -> (1f - d) * -25f + wave * 3f
                                            Skin.PAPER -> (1f - d) * -25f + (if (i % 2 == 0) -3f else 2.5f)
                                            Skin.TERMINAL -> 0f
                                        }
                                        translationY = if (skin == Skin.CLASSIC) wave * 4.dp.toPx() else 0f
                                    },
                            )
                            if (skin == Skin.TERMINAL) {
                                sizes.getOrNull(i)?.let {
                                    Text(StickerRules.kiloText(it), color = Palette.TextTertiary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** While the pack is made: a sticker peeling off and sticking back, over and over. */
@Composable
internal fun PeelingSticker(preview: Bitmap?, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val loop = rememberInfiniteTransition(label = "peel")
    val peel by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "peelAmount")
    val image = remember(preview) { preview?.asImageBitmap() }
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(118.dp)
                .graphicsLayer {
                    cameraDistance = 12 * density
                    rotationX = peel * 28f
                    rotationZ = -peel * 10f
                    translationY = -peel * 10.dp.toPx()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.15f, 0.9f)
                }
                .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 26.dp))
                .background(if (image == null) accent.start.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Icon(StickerIcon, contentDescription = null, tint = accent.start, modifier = Modifier.size(54.dp))
        }
    }
}

/** A small card wrapper used by the steps. */
@Composable
internal fun StepCard(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
    ) { content() }
}

/** Space between the parts of a card. */
@Composable
internal fun Gap(height: Dp = 12.dp) = Spacer(Modifier.height(height))
