package com.powermaxv2.ytdownloader.ui.tools.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatStats
import com.powermaxv2.ytdownloader.core.chat.Kind
import com.powermaxv2.ytdownloader.core.chat.MediaStats
import com.powermaxv2.ytdownloader.core.chat.StickerUse
import com.powermaxv2.ytdownloader.core.chat.duration
import com.powermaxv2.ytdownloader.core.chat.weight
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.max

// The cards of an export "with media" (1.14): the most used stickers on a podium, wobbling like stickers;
// who talks the most in vocals; who fills the phone (weight per kind, stacked bars); the last photos as a mosaic
// that pops in and slowly zooms. Images are decoded once, in memory only.

/** An image file (sticker .webp, photo thumbnail) decoded once; null when Android cannot read it. */
@Composable
internal fun rememberImage(bytes: ByteArray?, key: Any?): ImageBitmap? = remember(key, bytes) {
    bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
}

/** A sticker: its image, or its emoji on a white die-cut blob (example discussion); it sways gently. */
@Composable
internal fun Sticker(use: StickerUse, size: Dp, index: Int, modifier: Modifier = Modifier) {
    val image = rememberImage(use.image, use.hash)
    val skin = LocalSkin.current
    val sway = rememberInfiniteTransition(label = "sticker").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400 + index * 170, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val angle by sway
    val shape = when (skin) {
        Skin.TERMINAL -> RoundedCornerShape(3.dp)
        Skin.PAPER -> RoundedCornerShape(size / 5)
        Skin.CLASSIC -> CircleShape
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle * 5f + if (index % 2 == 0) -3f else 3f
                translationY = angle * 2.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (skin == Skin.PAPER) Modifier.shadow(4.dp, shape) else Modifier)
                    .clip(shape)
                    .background(Color.White)
                    .then(if (skin == Skin.TERMINAL) Modifier.border(2.dp, LocalAccent.current.start, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(use.emoji ?: "✨", fontSize = (size.value * 0.52f).sp)
            }
        }
    }
}

// MARK: - Top stickers

/** The 3 most used stickers on a podium (2nd, 1st, 3rd), then the next 7, then everyone's favourite. */
@Composable
internal fun TopStickers(stats: ChatStats, media: MediaStats, key: Any?) {
    val top = media.stickers.take(10)
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { rank ->
                val use = top.getOrNull(rank) ?: return@forEach
                val big = rank == 0
                Column(
                    Modifier.popIn(200 + (if (big) 0 else 180 * rank), key),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (big) Crown(delayMs = 900)
                    Sticker(use, if (big) 96.dp else 72.dp, rank)
                    CountUpText(
                        use.count.toDouble(),
                        { "× ${count(it)}" },
                        TextStyle(fontSize = if (big) 22.sp else 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
                        if (big) accent else Palette.TextPrimary,
                    )
                    Text(
                        use.topPerson,
                        color = personColorOf(stats, use.topPerson),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        val rest = top.drop(3)
        if (rest.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.forEachIndexed { i, use ->
                    Column(
                        Modifier
                            .weight(1f)
                            .popIn(700 + i * 70, key),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Sticker(use, 38.dp, i + 3)
                        Text("×${count(use.count)}", color = Palette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(7 - rest.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val favorites = stats.people.mapNotNull { p ->
            val pm = media.of(p.name) ?: return@mapNotNull null
            val use = media.stickers.firstOrNull { it.hash == pm.favoriteSticker } ?: return@mapNotNull null
            Triple(p, use, pm.favoriteStickerCount)
        }.take(6)
        if (favorites.isNotEmpty()) {
            Text(if (terminal) "> STICKER FÉTICHE DE CHACUN" else "Le sticker fétiche de chacun", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            favorites.forEachIndexed { i, (p, use, n) ->
                Row(
                    Modifier.slideIn(900 + i * 110, fromRight = i % 2 == 1, key = key),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(personColor(p.colorIndex))
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Sticker(use, 30.dp, i)
                    Spacer(Modifier.width(8.dp))
                    Text("${count(n)} fois", color = Palette.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun personColorOf(stats: ChatStats, name: String): Color =
    stats.people.firstOrNull { it.name == name }?.let { personColor(it.colorIndex) } ?: Palette.TextSecondary

// MARK: - Vocals and videos

@Composable
internal fun Vocals(stats: ChatStats, media: MediaStats, key: Any?) {
    val people = stats.people.mapNotNull { p -> media.of(p.name)?.takeIf { it.voiceCount > 0 }?.let { p to it } }
        .sortedByDescending { it.second.voiceSeconds }
    val most = people.firstOrNull()?.second?.voiceSeconds ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaKpi("de vocaux", media.voiceSeconds, Modifier.weight(1f), 0, key)
            MediaKpi("le plus long", media.longestVoice?.value, Modifier.weight(1f), 1, key)
            MediaKpi("en moyenne", if (media.voiceCount == 0) null else media.voiceSeconds / media.voiceCount, Modifier.weight(1f), 2, key)
        }
        people.take(8).forEachIndexed { i, (p, pm) ->
            val color = personColor(p.colorIndex)
            Column(Modifier.staggeredEntrance(i, key = key, baseDelay = 260), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        "${duration(pm.voiceSeconds)} · ${count(pm.voiceCount)} vocaux",
                        color = Palette.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatBar((pm.voiceSeconds / most).toFloat(), color, delayMs = 320 + i * 70, height = 8.dp)
                Text(
                    "moyenne ${duration(pm.averageVoice)} · le plus long ${duration(pm.longestVoice)}",
                    color = Palette.TextTertiary,
                    fontSize = 11.5.sp,
                )
            }
        }
        val lines = listOfNotNull(
            media.longestVoice?.let { "🎙️ Vocal record : ${duration(it.value)}, par ${it.person}" },
            if (media.videoCount > 0) {
                "🎬 ${count(media.videoCount)} vidéos, ${duration(media.videoSeconds)} en tout" +
                    (media.longestVideo?.let { ", la plus longue ${duration(it.value)} (${it.person})" } ?: "")
            } else {
                null
            },
            if (stats.timedCalls > 0) "📞 ${count(stats.timedCalls)} appels, ${duration(stats.callSeconds.toDouble())} au téléphone" else null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEachIndexed { i, line ->
                Text(line, color = Palette.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.slideIn(600 + i * 120, fromRight = false, key = key))
            }
        }
    }
}

@Composable
private fun MediaKpi(label: String, seconds: Double?, modifier: Modifier, index: Int, key: Any?) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 12.dp)
    Column(
        modifier
            .fillMaxHeight()
            .popIn(150 + index * 90, key)
            .clip(shape)
            .background(Palette.Surface2)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, Palette.Stroke, shape) else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CountUpText(
            seconds,
            { duration(it) },
            TextStyle(fontSize = if (skin == Skin.TERMINAL) 14.sp else 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display),
            LocalAccent.current.start,
        )
        Text(label, color = Palette.TextTertiary, fontSize = 10.5.sp, lineHeight = 13.sp, maxLines = 2)
    }
}

// MARK: - Weight

/** Kinds shown in the weight bars, with their colour; the rest is grouped as "autres". */
private val WeightKinds = listOf(
    Triple(Kind.PHOTO, "📷 photos", Color(0xFF4C5FD5)),
    Triple(Kind.VIDEO, "🎬 vidéos", Color(0xFFE4572E)),
    Triple(Kind.VOICE, "🎙️ vocaux", Color(0xFF1F9D6B)),
    Triple(Kind.STICKER, "✨ stickers", Color(0xFFD99A1E)),
    Triple(Kind.DOCUMENT, "📄 documents", Color(0xFF9B4DCA)),
)
private val OtherWeight = Color(0xFF8A8F98)

/** Megabytes sent by each person, one stacked bar per person split by kind; then documents and missing files. */
@Composable
internal fun Weight(stats: ChatStats, media: MediaStats, key: Any?) {
    val people = stats.people.mapNotNull { p -> media.of(p.name)?.takeIf { it.totalBytes > 0 }?.let { p to it } }
        .sortedByDescending { it.second.totalBytes }
    val most = max(1L, people.firstOrNull()?.second?.totalBytes ?: 1L)
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        people.take(8).forEachIndexed { i, (p, pm) ->
            val grown = rememberGrow(pm.totalBytes.toFloat() / most, delayMs = 300 + i * 80)
            Column(Modifier.staggeredEntrance(i, key = key, baseDelay = 220), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(personColor(p.colorIndex))
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    CountUpText(
                        pm.totalBytes.toDouble(),
                        { weight((it ?: 0.0).toLong()) },
                        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        Palette.TextPrimary,
                    )
                }
                val shape = RoundedCornerShape(if (skin == Skin.CLASSIC) 6.dp else 2.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(shape)
                        .background(if (skin == Skin.PAPER) Palette.TextPrimary.copy(alpha = 0.08f) else Palette.Surface2),
                ) {
                    Row(Modifier.fillMaxWidth(grown.coerceIn(0.001f, 1f)).fillMaxHeight()) {
                        val shown = WeightKinds.sumOf { pm.bytes(it.first) }
                        val total = max(1L, pm.totalBytes)
                        WeightKinds.forEach { (k, _, c) ->
                            val b = pm.bytes(k)
                            if (b > 0) Box(Modifier.weight(b.toFloat() / total).fillMaxHeight().background(c))
                        }
                        val other = pm.totalBytes - shown
                        if (other > 0) Box(Modifier.weight(other.toFloat() / total).fillMaxHeight().background(OtherWeight))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (skin == Skin.TERMINAL) 6.dp else 10.dp)) {
            WeightKinds.filter { k -> media.people.any { it.bytes(k.first) > 0 } }.forEach { (_, label, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(label.substringAfter(' '), color = Palette.TextTertiary, fontSize = if (skin == Skin.TERMINAL) 9.5.sp else 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        val lines = listOfNotNull(
            if (media.documents.isNotEmpty()) {
                "📄 " + media.documents.take(4).joinToString(", ") { "${count(it.second)} ${it.first}" } +
                    (media.biggestDocument?.let { " · le plus gros : ${weight(it.value.toLong())} (${it.person})" } ?: "")
            } else {
                null
            },
            if (media.missing > 0) {
                "👻 ${count(media.missing)} fichiers cités mais absents du .zip (jamais téléchargés, « vue unique » ou trop anciens)."
            } else {
                null
            },
        )
        lines.forEachIndexed { i, line ->
            Text(line, color = if (i == lines.lastIndex && media.missing > 0) Palette.TextTertiary else Palette.TextPrimary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.slideIn(700 + i * 120, fromRight = false, key = key))
        }
    }
}

// MARK: - Gallery

/** The last photos: one big, five small, each popping in, then all slowly zooming (thumbnails in memory only). */
@Composable
internal fun Gallery(media: MediaStats, key: Any?) {
    val photos = media.photos.take(6)
    val zoom by rememberInfiniteTransition(label = "gallery").animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val radius = if (LocalSkin.current == Skin.TERMINAL) 3.dp else 12.dp
    @Composable
    fun Tile(i: Int, modifier: Modifier) {
        val bytes = photos.getOrNull(i)
        val image = rememberImage(bytes, bytes)
        Box(
            modifier
                .popIn(150 + i * 110, key)
                .clip(RoundedCornerShape(radius))
                .background(Palette.Surface2),
        ) {
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val z = if (i % 2 == 0) zoom else 2.09f - zoom
                            scaleX = z
                            scaleY = z
                        },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(0, Modifier.weight(2f).aspectRatio(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (photos.size > 1) Tile(1, Modifier.fillMaxWidth().aspectRatio(1f))
                if (photos.size > 2) Tile(2, Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        if (photos.size > 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 3 until 6) {
                    if (i < photos.size) Tile(i, Modifier.weight(1f).aspectRatio(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
