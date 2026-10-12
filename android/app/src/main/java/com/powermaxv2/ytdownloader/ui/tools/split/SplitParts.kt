package com.powermaxv2.ytdownloader.ui.tools.split

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.split.SplitPlan
import com.powermaxv2.ytdownloader.core.trim.TrimRange
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

// The Découpe statut's pieces: the ring cut in as many arcs as there are statuses (like WhatsApp's status ring),
// one row per part, and the hand-off of the whole series to WhatsApp.

/**
 * A ring of [count] arcs drawing themselves one after the other when [count] changes. [filled] (0..count, may be
 * fractional) colours the arcs already made; null = all coloured (a preview). While [active] the arc being made
 * breathes.
 */
@Composable
internal fun SplitRing(count: Int, modifier: Modifier = Modifier, size: Dp = 168.dp, filled: Float? = null, active: Boolean = false, center: @Composable () -> Unit = {}) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val track = Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.10f else 0.12f)
    val reveal = remember(count) { Animatable(0f) }
    LaunchedEffect(count) { reveal.animateTo(1f, tween(500 + 70 * count.coerceAtMost(12), easing = FastOutSlowInEasing)) }
    val shownFill by animateFloatAsState(filled ?: count.toFloat(), spring(dampingRatio = 0.8f, stiffness = 120f), label = "statusFill")
    val pulse = rememberInfiniteTransition(label = "statusPulse")
    val glow by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "statusGlow")
    val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "statusSpin")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (count <= 0) return@Canvas
            val stroke = this.size.minDimension * 0.065f
            val inset = stroke / 2 + 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val cap = if (skin == Skin.TERMINAL || count > 12) StrokeCap.Butt else StrokeCap.Round
            // Round caps stick out of each arc by half the stroke: the gap makes room for them, so the cuts show.
            val capDegrees = if (cap == StrokeCap.Round) Math.toDegrees((stroke / (arcSize.width / 2)).toDouble()).toFloat() else 0f
            val gap = if (count == 1) 0f else (if (count > 30) 2f else if (count > 12) 4f else 7f) + capDegrees
            val sweep = (360f / count - gap).coerceAtLeast(0.5f)
            val base = -90f + (if (active) spin else 0f)
            for (i in 0 until count) {
                // Each arc grows in turn: the reveal runs over the arcs like a hand drawing the ring.
                val local = (reveal.value * count - i).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val start = base + i * (sweep + gap) + gap / 2
                drawArc(track, start, sweep * local, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = cap))
                val part = (shownFill - i).coerceIn(0f, 1f)
                if (part > 0f) {
                    val alpha = if (active && part < 1f) glow else 1f
                    drawArc(accent.start.copy(alpha = alpha), start, sweep * local * part, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = cap))
                }
            }
        }
        center()
    }
}

/** Centre of a ring: a big number and a word under it. */
@Composable
internal fun RingCenter(value: String, word: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
        Text(if (terminal) word.uppercase() else word, color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** One status of the series: its picture in a little ring, its number and its times. */
@Composable
internal fun PartRow(index: Int, count: Int, range: TrimRange, thumb: Bitmap?, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = if (terminal) RoundedCornerShape(LocalTokens.current.cardRadius) else CircleShape
    Row(modifier.fillMaxWidth().staggeredEntrance(index, key = count, baseDelay = 120).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(accent.brush),
            )
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .clip(shape)
                    .background(Palette.Surface)
                    .padding(2.dp)
                    .clip(shape)
                    .background(Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = "Statut ${SplitPlan.number(index, count)}"
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${SplitPlan.short(range.startMs)} → ${SplitPlan.short(range.endMs)}",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                fontFamily = if (terminal) FontFamily.Monospace else null,
            )
        }
        Text(
            SplitPlan.short(range.durationMs),
            color = accent.start,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.tint(0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A saved status: its number and its name. */
@Composable
internal fun SavedRow(index: Int, count: Int, file: SavedFile) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().staggeredEntrance(index, baseDelay = 260).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.tint(0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(SplitPlan.number(index, count), color = accent.start, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(file.name, color = Palette.TextPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** Parts listed before "… et N autres". */
internal const val PARTS_SHOWN = 6

@Composable
internal fun MoreParts(hidden: Int) {
    Text(
        "… et $hidden autre${if (hidden > 1) "s" else ""}",
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 56.dp, top = 2.dp),
    )
}

/** WhatsApp packages, the usual one first. */
private val whatsApp = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Hands [files] to WhatsApp in order (it then asks where: « Mon statut » posts them one after the other). Without
 * WhatsApp, the usual share sheet.
 */
internal fun shareToWhatsApp(context: Context, files: List<SavedFile>, vm: MainViewModel) {
    if (files.isEmpty()) return
    for (pkg in whatsApp) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("video/*")
            .setPackage(pkg)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(files.map { it.uri }))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Not installed: the next one, then the share sheet.
        } catch (e: SecurityException) {
            break
        }
    }
    shareSavedFiles(context, files, vm)
}

/** Background of the thumbnail-less preview tiles. */
internal val Scrim = Color.Black.copy(alpha = 0.55f)
