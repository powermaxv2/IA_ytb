package com.powermaxv2.ytdownloader.ui.tools.wipe

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.wipe.DoneLine
import com.powermaxv2.ytdownloader.core.wipe.WipeKind
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// Building blocks of the Gomme: the eraser glyph, the photo thumbnails (with their check or "propre" badge), the
// "what your photos tell" rows with their switches, the eraser sweeping the tags off a photo, and the lock that
// closes on the result.

// MARK: - Eraser glyph

/** A pencil eraser, tilted: the tip (left) and the sleeve (right), split by a thin gap. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Gomme", 24.dp, 24.dp, 24f, 24f).apply {
        group(rotate = -40f, pivotX = 12f, pivotY = 12f) {
            path(fill = SolidColor(Color.Black)) { roundRect(2.5f, 7.5f, 9.6f, 16.5f, 2f) }
            path(fill = SolidColor(Color.Black)) { roundRect(10.9f, 7.5f, 21.5f, 16.5f, 2f) }
        }
        // The line the eraser leaves clean.
        path(fill = SolidColor(Color.Black)) { roundRect(12f, 19.6f, 21.5f, 21.2f, 0.8f) }
    }.build()
}

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
    moveTo(l + rad, t)
    lineTo(r - rad, t)
    arcTo(rad, rad, 0f, false, true, r, t + rad)
    lineTo(r, b - rad)
    arcTo(rad, rad, 0f, false, true, r - rad, b)
    lineTo(l + rad, b)
    arcTo(rad, rad, 0f, false, true, l, b - rad)
    lineTo(l, t + rad)
    arcTo(rad, rad, 0f, false, true, l + rad, t)
    close()
}

internal fun WipeKind.icon(): ImageVector = when (this) {
    WipeKind.LOCATION -> Icons.Rounded.Place
    WipeKind.DEVICE -> Icons.Rounded.Smartphone
    WipeKind.AUTHOR -> Icons.Rounded.Person
    WipeKind.DATE -> Icons.Rounded.Schedule
}

internal fun WipeKind.title(): String = when (this) {
    WipeKind.LOCATION -> "Lieu exact"
    WipeKind.DEVICE -> "Téléphone"
    WipeKind.AUTHOR -> "Auteur"
    WipeKind.DATE -> "Date et heure"
}

// MARK: - Thumbnails

/** Sky over sand, forest, sunset: what a thumbnail shows until (or instead of) the photo. */
private val placeholders = listOf(
    listOf(Color(0xFF8EC5DD), Color(0xFFE9D59E), Color(0xFFD7A673)),
    listOf(Color(0xFF1F4A38), Color(0xFF2E6B4F), Color(0xFF8FD3AE)),
    listOf(Color(0xFFF7B2A0), Color(0xFFE59AA2), Color(0xFF5B5F7A), Color(0xFF2D2F45)),
)

/** A square photo [uri], cropped, over a soft gradient while it loads. */
@Composable
internal fun PhotoThumb(uri: Uri, index: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) LocalTokens.current.cardRadius else 16.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(placeholders[index % placeholders.size])),
    ) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Width of a thumbnail when three sit side by side in [available] width, [gap] apart. */
private fun thumbSize(available: Dp, gap: Dp): Dp = (available - gap * 2) / 3

/**
 * The chosen photos, three per screen width (more scroll sideways), each popping in with its check badge; the
 * badge removes the photo. A last dashed tile adds more.
 */
@Composable
internal fun PhotoStrip(uris: List<Uri>, onRemove: (Int) -> Unit, onAdd: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val size = thumbSize(maxWidth, gap)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            uris.forEachIndexed { index, uri ->
                Box(Modifier.size(size).popIn(index, key = uri)) {
                    PhotoThumb(uri, index, Modifier.fillMaxSize())
                    CheckBadge(index, Modifier.align(Alignment.TopEnd).padding(7.dp)) { onRemove(index) }
                }
            }
            onAdd?.let { add -> AddTile(size, uris.size, add) }
        }
    }
}

/** Springs in from 60 % with a little turn, [index] by [index]. */
@Composable
private fun Modifier.popIn(index: Int, key: Any?): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(90L + index.coerceAtMost(6) * 70L)
        p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = 0.6f + 0.4f * v
        scaleY = 0.6f + 0.4f * v
        rotationZ = (1f - v) * -8f
    }
}

@Composable
private fun CheckBadge(index: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(320L + index.coerceAtMost(6) * 70L)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .semantics { contentDescription = "Retirer cette photo" }
            .bounceClick(pressedScale = 0.8f, onClick = onClick)
            .size(26.dp)
            .clip(CircleShape)
            .background(if (LocalSkin.current == Skin.CLASSIC) accent.brush else SolidColor(accent.start))
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = if (terminal) Color.Black else Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AddTile(size: Dp, index: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    val radius = if (LocalSkin.current == Skin.TERMINAL) LocalTokens.current.cardRadius else 16.dp
    Column(
        Modifier
            .size(size)
            .popIn(index, key = "add")
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(accent.copy(alpha = 0.08f))
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(radius)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(if (LocalSkin.current == Skin.TERMINAL) "AJOUTER" else "Ajouter", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** The saved copies, each with a "propre" pill that slides up once the shine has swept over it. */
@Composable
internal fun CleanStrip(uris: List<Uri>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val size = thumbSize(maxWidth, gap)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
            uris.forEachIndexed { index, uri ->
                Box(Modifier.size(size).popIn(index, key = uri)) {
                    PhotoThumb(uri, index, Modifier.fillMaxSize().shine(index))
                    CleanPill(index, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }
    }
}

/** A diagonal band of light that crosses the photo once, as if just wiped. */
@Composable
private fun Modifier.shine(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(380L + index.coerceAtMost(6) * 140L)
        p.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }
    return drawWithContent {
        drawContent()
        val v = p.value
        if (v > 0f && v < 1f) {
            val x = -size.width + v * size.width * 3f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width * 0.6f, size.height),
                ),
            )
        }
    }
}

@Composable
private fun CleanPill(index: Int, modifier: Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(700L + index.coerceAtMost(6) * 140L)
        rise.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
    }
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        modifier
            .graphicsLayer {
                alpha = rise.value.coerceIn(0f, 1f)
                translationY = (1f - rise.value) * 14.dp.toPx()
            }
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (terminal) "PROPRE" else "propre", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// MARK: - "Ce que tes photos racontent"

/**
 * One kind of information: its icon tile (the location one sends out radar rings while it is about to be erased),
 * the title, what the photos say ([detail], null = nothing found) and the switch.
 */
@Composable
internal fun FactRow(kind: WipeKind, detail: String?, checked: Boolean, index: Int, showSwitch: Boolean = true, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val found = detail != null
    val on = checked && found
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 220)
            .bounceClick(enabled = found && showSwitch, pressedScale = 0.98f) { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FactTile(kind, alert = on && showSwitch && kind == WipeKind.LOCATION, dim = !found)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.title(), color = Palette.TextPrimary, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                detail ?: if (terminal) "# rien trouvé" else "Rien trouvé",
                color = when {
                    !found -> Palette.TextTertiary
                    kind == WipeKind.LOCATION && on && showSwitch -> accent.start
                    else -> Palette.TextSecondary
                },
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!showSwitch) return@Row
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            enabled = found,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = accent.start,
                checkedBorderColor = accent.start,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.TextPrimary.copy(alpha = 0.06f),
                uncheckedBorderColor = Palette.TextPrimary.copy(alpha = 0.18f),
                disabledUncheckedTrackColor = Palette.TextPrimary.copy(alpha = 0.03f),
                disabledUncheckedBorderColor = Palette.TextPrimary.copy(alpha = 0.08f),
                disabledUncheckedThumbColor = Palette.TextTertiary,
            ),
        )
    }
}

@Composable
private fun FactTile(kind: WipeKind, alert: Boolean, dim: Boolean) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val radius = if (skin == Skin.TERMINAL) LocalTokens.current.cardRadius else 14.dp
    val shape = RoundedCornerShape(radius)
    val pulse = rememberInfiniteTransition(label = "radar")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "radarRing")
    Box(
        Modifier
            .size(46.dp)
            .drawBehind {
                if (alert) {
                    // Two rings leave the tile and fade: "your position is in there".
                    listOf(ring, (ring + 0.5f) % 1f).forEach { r ->
                        drawCircle(
                            accent.start.copy(alpha = (1f - r) * 0.45f),
                            radius = size.minDimension / 2 + r * 18.dp.toPx(),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
            .graphicsLayer { alpha = if (dim) 0.4f else 1f }
            .clip(shape)
            .then(
                when (skin) {
                    Skin.CLASSIC -> Modifier.background(accent.brush)
                    Skin.PAPER -> Modifier.background(accent.start)
                    Skin.TERMINAL -> Modifier.background(accent.start.copy(alpha = 0.12f)).border(1.dp, accent.start.copy(alpha = 0.6f), shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon(), contentDescription = null, tint = if (skin == Skin.TERMINAL) accent.start else Palette.OnAccent, modifier = Modifier.size(24.dp))
    }
}

// MARK: - Working: the eraser at work

/** Tags a photo could carry, wiped off the picture by the passing eraser. */
private val fakeTags = listOf("GPS 48.8650 N", "Pixel 8 Pro", "SN 4C1A-77", "« Maxime »")

/**
 * The photo being cleaned, big, with its hidden tags written over it; an eraser sweeps back and forth and every tag
 * it passes is gone, leaving a little trail of crumbs.
 */
@Composable
internal fun EraserSweep(uri: Uri?, index: Int, modifier: Modifier = Modifier, side: Dp = 184.dp) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val transition = rememberInfiniteTransition(label = "eraser")
    val sweep by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "eraserSweep",
    )
    val wobble by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse), label = "eraserWobble")
    val density = LocalDensity.current
    Box(modifier.size(side)) {
        if (uri != null) PhotoThumb(uri, index, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(placeholders[index % placeholders.size])))
        // The tags, only on the side the eraser has not reached yet.
        Column(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(left = sweep * this.size.width) { this@drawWithContent.drawContent() }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            fakeTags.forEach { tag ->
                Text(
                    tag,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(if (terminal) 2.dp else 8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        // Crumbs falling behind the eraser.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val x = sweep * this.size.width
                    for (i in 0 until 6) {
                        val t = ((sweep * 6 + i * 0.37f) % 1f)
                        val cx = x - t * 26.dp.toPx() - i * 3.dp.toPx()
                        val cy = this.size.height * (0.25f + 0.1f * i) + t * 22.dp.toPx()
                        if (cx > 0) drawCircle(Color.White.copy(alpha = (1f - t) * 0.8f), radius = (2.6f - i * 0.25f).dp.toPx(), center = Offset(cx, cy))
                    }
                },
        )
        // The eraser itself, riding the sweep line.
        val offsetX = with(density) { (sweep * side.toPx()).toDp() } - 22.dp
        val offsetY = side / 2 - 22.dp + (sin(sweep * PI.toFloat() * 4) * 18f).dp
        Box(
            Modifier
                .offset(x = offsetX, y = offsetY)
                .graphicsLayer { rotationZ = -8f + wobble * 16f }
                .size(44.dp)
                .clip(CircleShape)
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.brush else SolidColor(accent.start)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EraserIcon, contentDescription = null, tint = if (terminal) Color.Black else Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

// MARK: - Done

/** The lock: open, then it snaps shut with a burst. */
@Composable
internal fun LockBadge(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    var shut by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
        delay(120)
        shut = true
        scale.animateTo(1.12f, tween(90))
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    val breathe = rememberInfiniteTransition(label = "lockGlow")
    val glow by breathe.animateFloat(0.18f, 0.38f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lockGlowA")
    Box(modifier.size(124.dp), contentAlignment = Alignment.Center) {
        SuccessBurst(play = shut, color = accent.start, modifier = Modifier.size(124.dp))
        Box(
            Modifier
                .size(84.dp)
                .drawBehind { drawCircle(accent.start.copy(alpha = glow), radius = size.minDimension / 2 + 10.dp.toPx()) }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape)
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.brush else SolidColor(accent.start)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (shut) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = if (terminal) Color.Black else Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** A line of the result: an accent check that draws itself, or a quiet clock for what was kept on purpose. */
@Composable
internal fun ResultLine(line: DoneLine, index: Int) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(450L + index * 160L)
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 380)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (line.done) {
                Box(
                    Modifier
                        .size(22.dp)
                        .drawBehind {
                            val w = 2.6.dp.toPx()
                            val a = Offset(size.width * 0.12f, size.height * 0.52f)
                            val b = Offset(size.width * 0.40f, size.height * 0.80f)
                            val c = Offset(size.width * 0.90f, size.height * 0.22f)
                            val p = draw.value
                            val first = (p / 0.4f).coerceIn(0f, 1f)
                            val second = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            drawLine(accent, a, a + (b - a) * first, w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            if (second > 0f) drawLine(accent, b, b + (c - b) * second, w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        },
                )
            } else {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (terminal) (if (line.done) "+ " else "= ") + line.text else line.text,
            color = if (line.done) Palette.TextPrimary else Palette.TextTertiary,
            fontSize = 15.5.sp,
            lineHeight = 20.sp,
        )
    }
}
