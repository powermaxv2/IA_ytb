package com.powermaxv2.ytdownloader.ui.tools.photodup

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.photodup.DupGroup
import com.powermaxv2.ytdownloader.core.photodup.DupKind
import com.powermaxv2.ytdownloader.core.photodup.Photo
import com.powermaxv2.ytdownloader.core.photodup.PhotoDupFacts
import com.powermaxv2.ytdownloader.ui.AnimatedCheckbox
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Building blocks of Doublons photos: the photo tiles of a group, the group card, and the "two photos laid on
// each other" scan animation.

/** The tool's symbol. */
internal val PhotoDupIcon: ImageVector get() = Icons.Rounded.PhotoLibrary

/** Soft colours standing in for photos when Android gives no thumbnail (and in the screenshot tests). */
private val swatches = listOf(
    Color(0xFF6FA8DC), Color(0xFFF6B26B), Color(0xFF93C47D), Color(0xFFE06666), Color(0xFF8E7CC3), Color(0xFF76A5AF),
)

internal fun swatchOf(photo: Photo): Color = swatches[(photo.name.hashCode() and 0x7FFFFFFF) % swatches.size]

/** One photo of a group: the picture, its check, and the "À garder" badge on the one suggested to stay. */
@Composable
internal fun PhotoTile(
    photo: Photo,
    checked: Boolean,
    keep: String?,
    index: Int,
    thumbnail: (suspend (Photo) -> Bitmap?)?,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(if (terminal) 3.dp else 14.dp)
    val press by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "tileCheck")
    val bitmap by produceState<Bitmap?>(null, photo.id) {
        val loader = thumbnail ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { loader(photo) }.getOrNull() }
    }
    Column(modifier.staggeredEntrance(index, key = photo.id)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    // A checked photo sinks a little, as if pushed towards the bin.
                    val s = 1f - 0.08f * press
                    scaleX = s
                    scaleY = s
                }
                .clip(shape)
                .background(swatchOf(photo).copy(alpha = 0.55f))
                .then(
                    when {
                        keep != null -> Modifier.border(2.dp, Palette.Success, shape)
                        terminal -> Modifier.border(1.dp, Palette.TextPrimary.copy(alpha = 0.25f), shape)
                        else -> Modifier
                    },
                )
                .bounceClick(pressedScale = 0.95f, onClick = onToggle),
        ) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.Center).size(30.dp))
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = photo.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            // Red veil over what will go.
            Box(Modifier.fillMaxSize().background(Palette.Danger.copy(alpha = 0.32f * press)))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.25f * (1f - press))),
            ) {
                AnimatedCheckbox(checked)
            }
            keep?.let {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
                        .background(Palette.Success)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (terminal) "garder" else "À garder", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        val size = PhotoDupFacts.bytes(photo.size)
        Text(
            if (terminal) size.lowercase() else size,
            color = if (checked) Palette.Danger else Palette.TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        val detail = listOf(PhotoDupFacts.dimensions(photo), photo.album).filter { it.isNotBlank() }.joinToString(" · ")
        Text(
            if (terminal) detail.lowercase() else detail,
            color = Palette.TextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (keep != null) {
            Text(if (terminal) keep.lowercase() else keep, color = Palette.Success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A group: how many photos, what deleting the extra ones frees, the tiles three by three, and "Garder la meilleure". */
@Composable
internal fun GroupCard(
    group: DupGroup,
    selection: Set<Long>,
    index: Int,
    thumbnail: (suspend (Photo) -> Bitmap?)?,
    onToggle: (Photo) -> Unit,
    onKeepBest: (DupGroup) -> Unit,
) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(Modifier.fillMaxWidth().staggeredEntrance(index, key = group.keep.id).padding(horizontal = 6.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = "${PhotoDupFacts.photos(group.photos.size)} ${if (group.kind == DupKind.EXACT) "identiques" else "ressemblantes"}"
            Text(if (terminal) "> ${title.lowercase()}" else title, color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val gain = "−${PhotoDupFacts.bytes(group.wasted)}"
            Text(if (terminal) gain.lowercase() else gain, color = Palette.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        val reason = PhotoDupFacts.keepReason(group)
        group.photos.chunked(3).forEachIndexed { row, photos ->
            if (row > 0) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photos.forEachIndexed { i, photo ->
                    PhotoTile(
                        photo,
                        checked = photo.id in selection,
                        keep = if (photo.id == group.keep.id) reason else null,
                        index = row * 3 + i,
                        thumbnail = thumbnail,
                        modifier = Modifier.weight(1f),
                    ) { onToggle(photo) }
                }
                repeat(3 - photos.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val best = selection.containsAll(group.others.map { it.id }) && group.keep.id !in selection
        Spacer(Modifier.height(10.dp))
        KeepChip(if (best) "Seule la meilleure reste" else "Garder la meilleure", best) { onKeepBest(group) }
    }
}

/** Toggle under a group: checks every photo but the one kept. */
@Composable
internal fun KeepChip(text: String, on: Boolean, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val tint by animateFloatAsState(if (on) 1f else 0f, spring(stiffness = 400f), label = "keepChip")
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .background(Palette.Success.copy(alpha = 0.08f + 0.12f * tint))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Palette.Success, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 72f * tint })
        Spacer(Modifier.width(6.dp))
        Text(if (terminal) text.lowercase() else text, color = if (on) Palette.Success else Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The scan: two photo cards slide over each other and apart again while a beam reads them; a ring of dots fills
 * with the progress. [progress] null = still listing the gallery.
 */
@Composable
internal fun MatchScan(progress: Float?, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val transition = rememberInfiniteTransition(label = "matchScan")
    val slide by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "matchSlide")
    val beam by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "matchBeam")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "matchSpin")
    val shown by animateFloatAsState(progress ?: 0f, spring(dampingRatio = 0.9f, stiffness = 90f), label = "matchProgress")
    val ink = Palette.TextPrimary
    val card = if (skin == Skin.TERMINAL) Palette.Background else Color.White
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        // Ring of 36 dots: lit up to the progress, or chasing round while listing.
        for (k in 0 until 36) {
            val a = Math.toRadians((k * 10.0) - 90)
            val p = Offset(c.x + (r - 6.dp.toPx()) * kotlin.math.cos(a).toFloat(), c.y + (r - 6.dp.toPx()) * kotlin.math.sin(a).toFloat())
            val lit = if (progress == null) ((spin / 10f).toInt() - k + 36) % 36 < 6 else k < shown * 36
            drawCircle(if (lit) accent.start else ink.copy(alpha = 0.12f), (if (lit) 3.2f else 2.4f) * density, p)
        }
        val w = r * 0.78f
        val h = w * 0.78f
        val gap = w * 0.32f * (1f - slide)
        val corner = if (skin == Skin.TERMINAL) 2f * density else 10f * density
        fun photoCard(dx: Float, angle: Float, tone: Color) {
            rotate(angle, Offset(c.x + dx, c.y)) {
                val topLeft = Offset(c.x + dx - w / 2, c.y - h / 2)
                drawRoundRect(card, topLeft, Size(w, h), androidx.compose.ui.geometry.CornerRadius(corner))
                val pad = 6f * density
                drawRoundRect(tone, Offset(topLeft.x + pad, topLeft.y + pad), Size(w - 2 * pad, h - 2 * pad), androidx.compose.ui.geometry.CornerRadius(corner * 0.6f))
                // A little landscape: sun and hill, the same on both cards.
                drawCircle(Color.White.copy(alpha = 0.85f), w * 0.08f, Offset(topLeft.x + w * 0.72f, topLeft.y + h * 0.32f))
                drawCircle(Color.Black.copy(alpha = 0.18f), w * 0.42f, Offset(topLeft.x + w * 0.32f, topLeft.y + h * 1.05f))
                drawRoundRect(ink.copy(alpha = 0.18f), topLeft, Size(w, h), androidx.compose.ui.geometry.CornerRadius(corner), style = Stroke(1f * density))
            }
        }
        photoCard(-gap, -8f * (1f - slide), Color(0xFF6FA8DC))
        photoCard(gap, 8f * (1f - slide), Color(0xFF6FA8DC).copy(alpha = 0.85f))
        // Reading beam over the cards.
        val y = c.y - h / 2 + h * beam
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.start.copy(alpha = 0.35f), Color.Transparent), y - 14 * density, y + 14 * density),
            Offset(c.x - w, y - 14 * density),
            Size(2 * w, 28 * density),
        )
        drawLine(accent.start, Offset(c.x - w * 0.95f, y), Offset(c.x + w * 0.95f, y), strokeWidth = 2f * density)
    }
}

/** The kept photo and its copies flying off, for the deletion. */
@Composable
internal fun FlyingCopies(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val transition = rememberInfiniteTransition(label = "flying")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing)), label = "flyingT")
    val accent = LocalAccent.current
    val danger = Palette.Danger
    Canvas(modifier.size(size)) {
        val c = center
        val w = this.size.minDimension * 0.42f
        val h = w * 0.8f
        // Copies leave towards the top right, shrinking and fading.
        for (k in 0..2) {
            val p = ((t + k / 3f) % 1f)
            val dx = w * 0.9f * p
            val dy = -h * 1.1f * p
            val s = 1f - 0.6f * p
            drawRoundRect(
                danger.copy(alpha = (1f - p) * 0.8f),
                Offset(c.x - w * s / 2 + dx, c.y - h * s / 2 + dy),
                Size(w * s, h * s),
                androidx.compose.ui.geometry.CornerRadius(8f * density),
            )
        }
        drawRoundRect(accent.start, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8f * density))
        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.1f, Offset(c.x + w * 0.2f, c.y - h * 0.15f))
    }
}
