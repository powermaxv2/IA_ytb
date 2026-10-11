package com.powermaxv2.ytdownloader.ui.tools.status

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.status.StatusItem
import com.powermaxv2.ytdownloader.core.status.StatusKind
import com.powermaxv2.ytdownloader.core.status.durationLabel
import com.powermaxv2.ytdownloader.core.status.hoursLeft
import com.powermaxv2.ytdownloader.core.status.leftLabel
import com.powermaxv2.ytdownloader.core.status.lifeLeft
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Building blocks of the Statuts tool: its glyph (a status ring), the thumbnails of the grid with the ring of the
// hours they have left and their keep button, and the rings of contacts that turn on the first screen.

// MARK: - Glyph

/** A WhatsApp-like status ring: four arcs around a dot. */
val StatusIcon: ImageVector by lazy {
    ImageVector.Builder("Statuts", 24.dp, 24.dp, 24f, 24f).apply {
        val gap = 0.36f // radians left between two arcs
        for (i in 0 until 4) {
            val from = -PI / 2 + i * PI / 2 + gap / 2
            val to = from + PI / 2 - gap
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f + 9f * cos(from).toFloat(), 12f + 9f * sin(from).toFloat())
                arcTo(9f, 9f, 0f, false, true, 12f + 9f * cos(to).toFloat(), 12f + 9f * sin(to).toFloat())
            }
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.6f, 12f)
            arcTo(4.4f, 4.4f, 0f, false, true, 16.4f, 12f)
            arcTo(4.4f, 4.4f, 0f, false, true, 7.6f, 12f)
            close()
        }
    }.build()
}

// MARK: - Thumbnails

/** Sunset, sea, forest, dusk: what a status shows until (or instead of) its picture. */
private val placeholders = listOf(
    listOf(Color(0xFF2B5876), Color(0xFF4E4376), Color(0xFFF7797D), Color(0xFFFBD786)),
    listOf(Color(0xFF89C2D9), Color(0xFFA9D6E5), Color(0xFFE9D8A6), Color(0xFFD4A373)),
    listOf(Color(0xFF1B4332), Color(0xFF2D6A4F), Color(0xFF95D5B2)),
    listOf(Color(0xFFFFB4A2), Color(0xFFE5989B), Color(0xFF6D6875), Color(0xFF2B2D42)),
    listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
    listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
)

internal fun placeholder(index: Int): Brush = Brush.verticalGradient(placeholders[index % placeholders.size])

/** The picture of a status (first frame for a video), cropped to fill. */
@Composable
internal fun StatusPicture(item: StatusItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .apply { if (item.kind == StatusKind.VIDEO) decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(500) }
            .crossfade(true)
            .build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** Corner radius of a thumbnail in the current skin. */
@Composable
internal fun thumbRadius(): Dp = if (LocalSkin.current == Skin.TERMINAL) LocalTokens.current.cardRadius else 18.dp

/**
 * One status of the grid: its picture, the ring of the hours it has left (red in its last 3 hours), the length
 * of a video, and the keep button that turns into a check with a burst once the copy is in the gallery.
 */
@Composable
internal fun StatusTile(
    item: StatusItem,
    index: Int,
    now: Long,
    saved: Boolean,
    saving: Boolean,
    onOpen: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(thumbRadius())
    Box(
        modifier
            .popIn(index, item.key)
            .aspectRatio(9f / 14f)
            .clip(shape)
            .background(placeholder(index))
            .bounceClick(pressedScale = 0.95f, onClick = onOpen),
    ) {
        StatusPicture(item, Modifier.fillMaxSize())
        // Shade at the bottom so the badges read on a white picture.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))),
        )
        LifeRing(
            fraction = lifeLeft(item.modifiedAt, now),
            label = leftLabel(hoursLeft(item.modifiedAt, now)),
            urgent = hoursLeft(item.modifiedAt, now) <= 3,
            index = index,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(7.dp),
        )
        if (item.kind == StatusKind.VIDEO) DurationBadge(durationLabel(item.durationMs), Modifier.align(Alignment.BottomStart).padding(8.dp))
        KeepButton(saved, saving, onKeep, Modifier.align(Alignment.BottomEnd).padding(7.dp))
    }
}

/** Springs in from 70 % with a little turn, [index] by [index]. */
@Composable
internal fun Modifier.popIn(index: Int, key: Any?): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(60L + index.coerceAtMost(9) * 55L)
        p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = 0.7f + 0.3f * v
        scaleY = 0.7f + 0.3f * v
        rotationZ = (1f - v) * if (index % 2 == 0) -6f else 6f
    }
}

/** The day a status has left, as a ring that fills up to it when the tile appears. */
@Composable
private fun LifeRing(fraction: Float, label: String, urgent: Boolean, index: Int, modifier: Modifier = Modifier) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        delay(220L + index.coerceAtMost(9) * 55L)
        sweep.animateTo(fraction, tween(700, easing = FastOutSlowInEasing))
    }
    val color = if (urgent) Color(0xFFFF5C5C) else Color.White
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(Color.Black.copy(alpha = 0.45f))
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (terminal) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun DurationBadge(text: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        text?.let {
            Spacer(Modifier.width(2.dp))
            Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Download button → spinning ring while copying → check with a burst once kept. */
@Composable
private fun KeepButton(saved: Boolean, saving: Boolean, onKeep: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(4.dp) else CircleShape
    val state = when {
        saved -> 2
        saving -> 1
        else -> 0
    }
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (saved) SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(40.dp))
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(120)))
            },
            label = "keep",
        ) { s ->
            when (s) {
                2 -> Box(
                    Modifier
                        .size(28.dp)
                        .clip(shape)
                        .background(if (skin == Skin.CLASSIC) accent.brush else SolidColor(accent.start))
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), shape)
                        .semantics { contentDescription = "Statut gardé" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = if (skin == Skin.TERMINAL) Color.Black else Color.White, modifier = Modifier.size(17.dp))
                }
                1 -> Spinner(Modifier.size(28.dp))
                else -> Box(
                    Modifier
                        .bounceClick(pressedScale = 0.8f, onClick = onKeep)
                        .size(32.dp)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.93f))
                        .semantics { contentDescription = "Garder ce statut" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = Color(0xFF2A0F0A), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val turn by rememberInfiniteTransition(label = "keepSpin").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "keepTurn",
    )
    Canvas(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.93f)),
    ) {
        val stroke = 2.6.dp.toPx()
        val inset = stroke * 1.6f
        drawArc(
            accent,
            turn,
            250f,
            false,
            Offset(inset, inset),
            Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

// MARK: - Rings of contacts (first screen)

/**
 * Four contacts' status rings, side by side: each ring is cut in segments like WhatsApp's, turns slowly and
 * breathes in turn, the first ones already seen (grey arcs).
 */
@Composable
internal fun ContactRings(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "rings")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "ringTurn")
    val breathe by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "ringBreathe")
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        listOf(3, 2, 4, 1).forEachIndexed { i, segments ->
            val phase = ((breathe * 4f) - i).let { if (it < 0) it + 4f else it }
            val pulse = if (phase < 1f) sin(phase * PI).toFloat() else 0f
            Box(
                Modifier
                    .popIn(i, "ring$i")
                    .graphicsLayer {
                        scaleX = 1f + 0.08f * pulse
                        scaleY = 1f + 0.08f * pulse
                    }
                    .size(62.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val gap = if (segments > 1) 14f else 0f
                    val each = 360f / segments
                    for (s in 0 until segments) {
                        val seen = s < segments / 2
                        drawArc(
                            if (seen) Palette.TextPrimary.copy(alpha = 0.25f) else accent.start,
                            turn * (if (i % 2 == 0) 1f else -1f) + s * each + gap / 2,
                            each - gap,
                            false,
                            Offset(inset, inset),
                            arcSize,
                            style = Stroke(stroke, cap = if (terminal) StrokeCap.Butt else StrokeCap.Round),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(if (terminal) RoundedCornerShape(4.dp) else CircleShape)
                        .background(placeholder(i)),
                )
            }
        }
    }
}

/** A small "gardé" pill, for the strip of the "done" screen. */
@Composable
internal fun KeptPill(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(450)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
    }
    Text(
        if (skin == Skin.TERMINAL) "GARDÉ" else "gardé",
        color = if (skin == Skin.TERMINAL) Color.Black else Color.White,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .graphicsLayer {
                translationY = (1f - pop.value) * 18.dp.toPx()
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 3.dp else 50.dp))
            .background(if (skin == Skin.CLASSIC) accent.brush else SolidColor(accent.start))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

