package com.powermaxv2.ytdownloader.ui.tools.cleaner

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.cleaner.CleanerFacts
import com.powermaxv2.ytdownloader.core.cleaner.MediaFile
import com.powermaxv2.ytdownloader.core.cleaner.MediaType
import com.powermaxv2.ytdownloader.core.cleaner.TypeShare
import com.powermaxv2.ytdownloader.ui.AnimatedCheckbox
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Drawn pieces of the Nettoyeur WhatsApp: the colour of each kind of media, the space bar that fills itself,
// the sweeping radar of the scan, the numbers that count up, and the rows of files with their check.

/** Colour of a kind of media (the same in the bar, the legend and the rows). Terminal: shades of its green. */
@Composable
internal fun typeColor(type: MediaType): Color {
    if (LocalSkin.current == Skin.TERMINAL) {
        val alpha = 1f - (type.ordinal * 0.09f)
        return LocalAccent.current.start.copy(alpha = alpha.coerceAtLeast(0.25f))
    }
    return when (type) {
        MediaType.VIDEOS -> Color(0xFFFF3B30)
        MediaType.IMAGES -> Color(0xFFFF8A1F)
        MediaType.DOCUMENTS -> Color(0xFF6366F1)
        MediaType.VOICE -> Color(0xFF22C55E)
        MediaType.AUDIO -> Color(0xFF06B6D4)
        MediaType.GIFS -> Color(0xFFEAB308)
        MediaType.STICKERS -> Color(0xFFEC4899)
        MediaType.STATUSES -> Color(0xFF9B3FD6)
        MediaType.OTHER -> Color(0xFF8E8E93)
    }
}

internal fun typeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.VIDEOS -> Icons.Rounded.Movie
    MediaType.IMAGES -> Icons.Rounded.Image
    MediaType.DOCUMENTS -> Icons.Rounded.Description
    MediaType.VOICE -> Icons.Rounded.Mic
    MediaType.AUDIO -> Icons.Rounded.MusicNote
    MediaType.GIFS -> Icons.Rounded.Gif
    MediaType.STICKERS -> Icons.Rounded.EmojiEmotions
    MediaType.STATUSES -> Icons.Rounded.DonutLarge
    MediaType.OTHER -> Icons.Rounded.InsertDriveFile
}

/** A size that counts up from 0 when it appears (and glides when it changes). */
@Composable
internal fun CountingBytes(value: Long, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, durationMillis: Int = 1100) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    val shown by animateFloatAsState(value.toFloat(), tween(600), label = "bytes")
    val text = CleanerFacts.bytes((shown * progress.value).toLong())
    Text(
        if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The media split by kind: a bar whose segments grow one after the other, then the legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpaceBar(shares: List<TypeShare>, modifier: Modifier = Modifier) {
    val total = shares.sumOf { it.bytes }.coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1200, delayMillis = 150, easing = FastOutSlowInEasing)) }
    val terminal = LocalSkin.current == Skin.TERMINAL
    val colors = shares.map { typeColor(it.type) }
    val track = Palette.TextPrimary.copy(alpha = 0.08f)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (terminal) 18.dp else 16.dp)
                .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp)),
        ) {
            drawRect(track)
            var x = 0f
            val gap = 2.dp.toPx()
            shares.forEachIndexed { i, share ->
                val full = size.width * share.bytes / total
                // Each segment starts once the previous one is under way: the bar fills from left to right.
                val local = ((grow.value * shares.size) - i * 0.6f).coerceIn(0f, 1f)
                val w = (full * local - gap).coerceAtLeast(0f)
                if (w > 0f) drawRect(colors[i], Offset(x, 0f), Size(w, size.height))
                x += full
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shares.forEachIndexed { i, share ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.staggeredEntrance(i, baseDelay = 400)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(if (terminal) RoundedCornerShape(1.dp) else CircleShape)
                            .background(colors[i]),
                    )
                    Spacer(Modifier.width(6.dp))
                    val label = "${share.type.label} ${CleanerFacts.bytes(share.bytes)}"
                    Text(if (terminal) label.lowercase() else label, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
                }
            }
        }
    }
}

/** The scan: a radar sweep over the WhatsApp bubble, with dots popping where files are found. */
@Composable
internal fun ScanRadar(found: Int, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "radarSweep")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "radarPulse")
    val ink = Palette.TextPrimary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val ring = ink.copy(alpha = if (skin == Skin.PAPER) 0.18f else 0.12f)
            for (k in 1..3) drawCircle(ring, r * k / 3f - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
            // Echo wave leaving the centre.
            drawCircle(accent.start.copy(alpha = (1f - pulse) * 0.35f), r * pulse, style = Stroke(2.dp.toPx()))
            // The sweeping beam: a pie whose tail fades out.
            rotate(sweep) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, accent.start.copy(alpha = 0.45f)), center),
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = this.size,
                )
                drawLine(accent.start, center, Offset(center.x + r - 2.dp.toPx(), center.y), strokeWidth = 2.dp.toPx())
            }
            // Blips: fixed spots, more of them light up as files are found; each glows when the beam passes.
            val blips = (found / 40 + 1).coerceAtMost(BLIPS.size)
            for (i in 0 until blips) {
                val (angle, dist) = BLIPS[i]
                val a = Math.toRadians(angle.toDouble())
                val p = Offset(center.x + (r * dist * cos(a)).toFloat(), center.y + (r * dist * sin(a)).toFloat())
                val behind = ((sweep - angle - 90f + 720f) % 360f) / 360f
                drawCircle(accent.end.copy(alpha = (1f - behind).coerceIn(0.25f, 1f)), 4.dp.toPx(), p)
            }
        }
        Box(
            Modifier
                .size(size * 0.34f)
                .clip(CircleShape)
                .background(if (skin == Skin.TERMINAL) accent.start.copy(alpha = 0.14f) else accent.start),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BroomIcon,
                contentDescription = null,
                tint = if (skin == Skin.TERMINAL) accent.start else Palette.OnAccent,
                modifier = Modifier.size(size * 0.18f),
            )
        }
    }
}

private val BLIPS = listOf(
    20f to 0.62f, 140f to 0.45f, 250f to 0.78f, 75f to 0.85f, 310f to 0.35f, 190f to 0.7f,
    5f to 0.3f, 110f to 0.72f, 220f to 0.5f, 290f to 0.88f, 160f to 0.86f, 45f to 0.4f,
)

/** The tool's symbol (a broom). */
internal val BroomIcon: ImageVector get() = Icons.Rounded.CleaningServices

/** The broom sweeping back and forth, for the deletion. */
@Composable
internal fun SweepingBroom(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "broom")
    val swing by transition.animateFloat(-18f, 18f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "broomSwing")
    val accent = LocalAccent.current
    Icon(
        BroomIcon,
        contentDescription = null,
        tint = accent.start,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = swing
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f)
            },
    )
}

private val dayFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy", Locale.FRANCE) }

/** "Vidéos · 12 mars 2026" (or "Envoyé par toi" for the copies WhatsApp keeps of what the user sent). */
internal fun fileDetail(file: MediaFile): String {
    val last = file.folder.split('/').lastOrNull { it.isNotBlank() }.orEmpty()
    val where = when {
        last.equals("Sent", ignoreCase = true) -> "Envoyé par toi"
        last.equals("Private", ignoreCase = true) -> "Privé"
        else -> file.type.label
    }
    val day = if (file.modified > 0) dayFormat.get()?.format(Date(file.modified)) else null
    return listOfNotNull(where, day).joinToString(" · ")
}

/** Picture of a photo or a video (or the kind's icon while it loads, or when there is none). */
@Composable
internal fun MediaThumb(file: MediaFile, thumbnail: (suspend (MediaFile) -> Bitmap?)?, size: Dp = 44.dp) {
    val skin = LocalSkin.current
    val color = typeColor(file.type)
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 3.dp else 12.dp)
    val bitmap by produceState<Bitmap?>(null, file.id) {
        val loader = thumbnail ?: return@produceState
        if (file.type == MediaType.IMAGES || file.type == MediaType.VIDEOS || file.type == MediaType.STATUSES) {
            value = withContext(Dispatchers.IO) { runCatching { loader(file) }.getOrNull() }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = if (skin == Skin.TERMINAL) 0.10f else 0.16f))
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, color.copy(alpha = 0.6f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(typeIcon(file.type), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
    }
}

/** One file: picture, name, where and when, size, and its check. [tag] marks the copy that stays in a duplicate group. */
@Composable
internal fun FileRow(
    file: MediaFile,
    checked: Boolean,
    index: Int,
    thumbnail: (suspend (MediaFile) -> Bitmap?)?,
    tag: String? = null,
    barFraction: Float? = null,
    onToggle: () -> Unit,
) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    val tint by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = 300f), label = "rowTint")
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = file.id)
            .clip(RoundedCornerShape(if (terminal) 2.dp else 14.dp))
            .background(Palette.Danger.copy(alpha = 0.07f * tint))
            .bounceClick(pressedScale = 0.98f, onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumb(file, thumbnail)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.name,
                    color = Palette.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tag?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (terminal) "[${it.lowercase()}]" else it,
                        color = if (terminal) accent.start else Palette.Success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (terminal) Color.Transparent else Palette.Success.copy(alpha = 0.14f))
                            .padding(horizontal = if (terminal) 0.dp else 7.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val detail = fileDetail(file)
            Text(if (terminal) detail.lowercase() else detail, color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            barFraction?.let { fraction ->
                Spacer(Modifier.height(5.dp))
                val grown = remember(file.id) { Animatable(0f) }
                LaunchedEffect(file.id) { grown.animateTo(fraction, tween(700, delayMillis = 120 + index.coerceAtMost(12) * 40, easing = FastOutSlowInEasing)) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.TextPrimary.copy(alpha = 0.06f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(grown.value.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(typeColor(file.type)),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            CleanerFacts.bytes(file.size).let { if (terminal) it.lowercase() else it },
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        AnimatedCheckbox(checked)
    }
}

/** Title of a duplicate group: how many copies, their size, and what deleting the extra ones frees. */
@Composable
internal fun GroupHeader(count: Int, each: Long, wasted: Long, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val text = "$count exemplaires · ${CleanerFacts.bytes(each)} chacun"
        Text(if (terminal) "> ${text.lowercase()}" else text, color = Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        val gain = "−${CleanerFacts.bytes(wasted)}"
        Text(if (terminal) gain.lowercase() else gain, color = Palette.Danger, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}
