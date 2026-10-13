package com.powermaxv2.ytdownloader.ui.tools.vault

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.vault.VaultCode
import com.powermaxv2.ytdownloader.core.vault.VaultItem
import com.powermaxv2.ytdownloader.core.vault.VaultKind
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Pieces of the Coffre-fort: the padlock that opens, the code dots, the keypad and the tiles of the grid.

@Composable
internal fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/**
 * The vault's padlock, drawn: its shackle lifts and swings open when [open] turns true, a ring of light turns around
 * it while [working], and it bounces once at each change.
 */
@Composable
internal fun VaultPadlock(open: Boolean, modifier: Modifier = Modifier, working: Boolean = false, size: Float = 120f) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val body = if (terminal) accent.start else Palette.TextPrimary
    val lift by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 180f), label = "shackle")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(open) {
        pop.snapTo(0.86f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
    }
    val transition = rememberInfiniteTransition(label = "padlock")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "padlockSpin")
    val glow by transition.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "padlockGlow")
    Canvas(
        modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2, h / 2)
        // Halo and ring.
        drawCircle(Brush.radialGradient(listOf(accent.start.copy(alpha = glow * 0.5f), Color.Transparent), c, w * 0.5f), w * 0.5f, c)
        if (working) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, accent.start, accent.end, Color.Transparent), c),
                startAngle = spin,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(w * 0.06f, h * 0.06f),
                size = Size(w * 0.88f, h * 0.88f),
                style = Stroke(w * 0.035f, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(accent.start.copy(alpha = 0.25f), w * 0.44f, c, style = Stroke(w * 0.012f))
        }
        // Shackle: rises, then swings on its left leg.
        val bodyW = w * 0.44f
        val bodyH = h * 0.32f
        val bodyTop = h * 0.48f
        val left = c.x - bodyW / 2
        val sw = w * 0.055f
        val shackleW = bodyW * 0.62f
        val rise = lift * h * 0.09f
        val swing = (lift - 0.4f).coerceAtLeast(0f) / 0.6f * 28f
        val pivot = Offset(c.x - shackleW / 2, bodyTop - rise)
        val rad = (-swing) * PI.toFloat() / 180f
        fun turn(p: Offset): Offset {
            val dx = p.x - pivot.x
            val dy = p.y - pivot.y
            return Offset(pivot.x + dx * cos(rad) - dy * sin(rad), pivot.y + dx * sin(rad) + dy * cos(rad))
        }
        val top = bodyTop - h * 0.17f - rise
        val path = androidx.compose.ui.graphics.Path().apply {
            val a = turn(Offset(c.x - shackleW / 2, bodyTop + sw - rise))
            moveTo(a.x, a.y)
            val b = turn(Offset(c.x - shackleW / 2, top + shackleW / 2))
            lineTo(b.x, b.y)
            // Half circle on top, as segments (it turns with the shackle).
            for (i in 0..18) {
                val t = PI.toFloat() + i / 18f * PI.toFloat()
                val p = turn(Offset(c.x + cos(t) * shackleW / 2, top + shackleW / 2 + sin(t) * shackleW / 2))
                lineTo(p.x, p.y)
            }
            val d = turn(Offset(c.x + shackleW / 2, bodyTop + sw - rise))
            lineTo(d.x, d.y)
        }
        drawPath(path, body, style = Stroke(sw, cap = StrokeCap.Round))
        // Body and keyhole.
        drawRoundRect(
            brush = if (terminal) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) else accent.brush,
            topLeft = Offset(left, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.06f),
        )
        if (terminal) {
            drawRoundRect(body, Offset(left, bodyTop), Size(bodyW, bodyH), CornerRadius(w * 0.02f), style = Stroke(w * 0.022f))
        }
        val hole = if (terminal) body else Color.White
        drawCircle(hole, w * 0.035f, Offset(c.x, bodyTop + bodyH * 0.42f))
        drawRoundRect(hole, Offset(c.x - w * 0.012f, bodyTop + bodyH * 0.42f), Size(w * 0.024f, bodyH * 0.3f), CornerRadius(w * 0.012f))
    }
}

/** The dots of the code: at least [VaultCode.MIN], one more appears per digit, each pops in with a spring. */
@Composable
internal fun CodeDots(entered: Int, shake: Any?, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val slots = maxOf(VaultCode.MIN, entered).coerceAtMost(VaultCode.MAX)
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == null || shake == 0) return@LaunchedEffect
        listOf(-14f, 12f, -9f, 6f, -3f, 0f).forEach { offset.animateTo(it, tween(55)) }
    }
    Row(
        modifier.graphicsLayer { translationX = offset.value * density },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slots) { i ->
            val filled = i < entered
            val scale by animateFloatAsState(if (filled) 1f else 0.55f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "dot$i")
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(if (terminal) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (filled) accent.start else Color.Transparent)
                    .border(2.dp, if (filled) accent.start else Palette.TextTertiary, if (terminal) RoundedCornerShape(2.dp) else CircleShape),
            )
        }
    }
}

/** The vault's own keypad (no system keyboard, nothing it could learn): digits, erase, and the fingerprint. */
@Composable
internal fun VaultKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onFingerprint: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEachIndexed { k, digit ->
                    Key(Modifier.staggeredEntrance(r * 3 + k, baseDelay = 80), enabled, onClick = { onDigit(digit) }) {
                        KeyLabel(digit.toString())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (onFingerprint != null) {
                Key(Modifier.staggeredEntrance(9, baseDelay = 80), enabled, quiet = true, onClick = onFingerprint) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = "Empreinte", tint = LocalAccent.current.start, modifier = Modifier.size(30.dp))
                }
            } else {
                Spacer(Modifier.size(KEY))
            }
            Key(Modifier.staggeredEntrance(10, baseDelay = 80), enabled, onClick = { onDigit('0') }) { KeyLabel("0") }
            Key(Modifier.staggeredEntrance(11, baseDelay = 80), enabled, quiet = true, onClick = onErase) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer", tint = Palette.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private val KEY = 72.dp

@Composable
private fun Key(modifier: Modifier, enabled: Boolean, quiet: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(4.dp) else CircleShape
    Box(
        modifier
            .size(KEY)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .bounceClick(enabled = enabled, pressedScale = 0.86f, onClick = onClick)
            .clip(shape)
            .background(
                when {
                    quiet -> Color.Transparent
                    skin == Skin.TERMINAL -> Color.Transparent
                    else -> Palette.Surface2
                },
            )
            .border(if (quiet) 0.dp else 1.dp, if (quiet) Color.Transparent else Palette.Stroke, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun KeyLabel(text: String) {
    val tokens = LocalTokens.current
    Text(
        text,
        color = Palette.TextPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = if (tokens.mono) FontFamily.Monospace else tokens.display,
    )
}

/** Icon of a kind of file. */
internal fun kindIcon(kind: VaultKind): ImageVector = when (kind) {
    VaultKind.PHOTO -> Icons.Rounded.Image
    VaultKind.VIDEO -> Icons.Rounded.Movie
    VaultKind.AUDIO -> Icons.Rounded.MusicNote
    VaultKind.PDF -> Icons.Rounded.PictureAsPdf
    VaultKind.TEXT -> Icons.Rounded.Description
    VaultKind.OTHER -> Icons.Rounded.Attachment
}

/** Two colours per kind, for the tiles that have no picture. */
private fun kindColors(kind: VaultKind): Pair<Color, Color> = when (kind) {
    VaultKind.PHOTO -> Color(0xFF7C4DFF) to Color(0xFF26C6DA)
    VaultKind.VIDEO -> Color(0xFFFF5252) to Color(0xFFFF9100)
    VaultKind.AUDIO -> Color(0xFF00BFA5) to Color(0xFF64DD17)
    VaultKind.PDF -> Color(0xFFE53935) to Color(0xFFFF7043)
    VaultKind.TEXT -> Color(0xFF546E7A) to Color(0xFF90A4AE)
    VaultKind.OTHER -> Color(0xFF5C6BC0) to Color(0xFFAB47BC)
}

/** A file of the grid: its picture (or its kind's icon), its name, and the tick when it is selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultTile(
    item: VaultItem,
    thumb: ImageBitmap?,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 4.dp else tokens.fieldRadius)
    val inset by animateFloatAsState(if (selected) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "tileInset")
    val (from, to) = kindColors(item.kind)
    Box(
        modifier
            .aspectRatio(1f)
            .staggeredEntrance(index, key = item.id, baseDelay = 40)
            .graphicsLayer {
                scaleX = inset
                scaleY = inset
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(from.copy(alpha = 0.85f), to.copy(alpha = 0.85f))))
            .border(if (selected) 3.dp else 0.dp, if (selected) accent.start else Color.Transparent, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (thumb != null) {
            Image(thumb, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(kindIcon(item.kind), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    item.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (item.kind == VaultKind.VIDEO && thumb != null) {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
        AnimatedVisibility(
            selected,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 600f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Choisi", tint = accent.start, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** The grid: three tiles a row (the screen already scrolls, so no lazy grid). */
@Composable
internal fun VaultGrid(
    items: List<VaultItem>,
    thumbs: Map<String, ImageBitmap>,
    selected: Set<String>,
    onClick: (VaultItem) -> Unit,
    onLongClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { k, item ->
                    VaultTile(
                        item = item,
                        thumb = thumbs[item.id],
                        selected = item.id in selected,
                        index = r * 3 + k,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A line of the warning, with a small coloured marker that pops in. */
@Composable
internal fun WarningLine(index: Int, text: String, danger: Boolean = false) {
    val accent = LocalAccent.current
    Row(Modifier.staggeredEntrance(index, baseDelay = 120), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (danger) Palette.Danger else accent.start),
        )
        Spacer(Modifier.width(10.dp))
        Text(skinText(text), color = if (danger) Palette.Danger else Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (danger) FontWeight.Bold else FontWeight.Normal)
    }
}
