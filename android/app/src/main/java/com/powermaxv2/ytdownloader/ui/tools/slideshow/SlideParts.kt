package com.powermaxv2.ytdownloader.ui.tools.slideshow

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.slideshow.SlideFit
import com.powermaxv2.ytdownloader.core.slideshow.SlideFormat
import com.powermaxv2.ytdownloader.core.slideshow.SlidePhoto
import com.powermaxv2.ytdownloader.core.slideshow.SlidePlan
import com.powermaxv2.ytdownloader.core.slideshow.SlideTransition
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay

// The Diaporama's pieces: a live preview that plays the show with the chosen transition and format (before anything
// is made), and its timeline, one segment per photo.

/**
 * Plays [photos] in a frame of [format]'s shape, each for its own duration, with [transition] between them (Ken
 * Burns photos slowly zoom). [paused] shows the first photo still (screenshot tests).
 */
@Composable
internal fun SlidePreview(
    photos: List<SlidePhoto>,
    format: SlideFormat,
    fit: SlideFit,
    transition: SlideTransition,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    paused: Boolean = false,
) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    var playing by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val count = photos.size
    // A photo removed under the preview: start over rather than point past the end.
    val index = if (playing < count) playing else 0
    LaunchedEffect(photos.map { it.id to it.durationMs }, transition, paused) {
        if (paused || count == 0) return@LaunchedEffect
        while (true) {
            if (playing >= count) playing = 0
            val duration = photos.getOrNull(playing)?.durationMs ?: SlidePlan.DEFAULT_DURATION_MS
            val start = withFrameMillis { it }
            var now = start
            while (now - start < duration) {
                now = withFrameMillis { it }
                elapsed = now - start
            }
            if (count > 1) {
                playing = (playing + 1) % count
            } else {
                elapsed = 0L
                delay(16)
            }
        }
    }
    val ratio = format.width.toFloat() / format.height
    val shape = RoundedCornerShape(if (terminal) LocalTokens.current.cardRadius else 22.dp)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .height(height)
                .aspectRatio(ratio)
                .clip(shape)
                .background(Color.Black),
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = { spec(transition, initialState, targetState) },
                label = "slide",
                modifier = Modifier.fillMaxSize(),
            ) { i ->
                val photo = photos.getOrNull(i)
                SlideFrame(photo?.thumb?.asImageBitmap(), fit, kenBurns = transition == SlideTransition.KEN_BURNS && !paused, zoomIn = i % 2 == 0, durationMs = photo?.durationMs ?: 3_000L)
            }
            // Number of the photo, top left, like a story counter.
            Text(
                "${(index + 1).coerceAtMost(count.coerceAtLeast(1))}/${count.coerceAtLeast(1)}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                SlidePlan.seconds(photos.getOrNull(index)?.durationMs ?: 0L),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.start.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        SlideTimeline(photos, index, if (paused) 0f else (elapsed.toFloat() / (photos.getOrNull(index)?.durationMs ?: 1L)).coerceIn(0f, 1f))
    }
}

private fun spec(transition: SlideTransition, from: Int, to: Int): ContentTransform {
    val forward = to > from || (from > 0 && to == 0)
    return when (transition) {
        SlideTransition.CUT -> EnterTransition.None togetherWith ExitTransition.None
        SlideTransition.FADE, SlideTransition.KEN_BURNS ->
            fadeIn(tween(SlidePlan.TRANSITION_MS.toInt(), easing = LinearEasing)) togetherWith fadeOut(tween(SlidePlan.TRANSITION_MS.toInt(), easing = LinearEasing))
        SlideTransition.SLIDE -> {
            val sign = if (forward) 1 else -1
            slideInHorizontally(tween(SlidePlan.TRANSITION_MS.toInt(), easing = FastOutSlowInEasing)) { sign * it } togetherWith
                slideOutHorizontally(tween(SlidePlan.TRANSITION_MS.toInt(), easing = FastOutSlowInEasing)) { -sign * it }
        }
    }
}

/** One photo in the preview frame: cropped, or whole over its own dimmed copy; slowly zooming for Ken Burns. */
@Composable
private fun SlideFrame(image: ImageBitmap?, fit: SlideFit, kenBurns: Boolean, zoomIn: Boolean, durationMs: Long) {
    val zoom = remember { Animatable(if (zoomIn) 1f else 1f + SlidePlan.ZOOM.toFloat()) }
    LaunchedEffect(kenBurns) {
        if (!kenBurns) return@LaunchedEffect
        zoom.animateTo(if (zoomIn) 1f + SlidePlan.ZOOM.toFloat() else 1f, tween((durationMs + SlidePlan.TRANSITION_MS).toInt(), easing = LinearEasing))
    }
    val scale = if (kenBurns) zoom.value else 1f
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (image == null) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
            return@Box
        }
        if (fit == SlideFit.FIT) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
            )
        }
        Image(
            image,
            contentDescription = null,
            contentScale = if (fit == SlideFit.FILL) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // Odd photos drift sideways while they zoom out, like the video.
                    if (kenBurns && !zoomIn) translationX = (scale - 1f) * size.width * 0.5f
                },
        )
    }
}

/** One segment per photo, as wide as its duration; the current one fills up as it plays. */
@Composable
internal fun SlideTimeline(photos: List<SlidePhoto>, current: Int, fraction: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val track = Palette.TextPrimary.copy(alpha = 0.12f)
    val total = photos.sumOf { it.durationMs }.coerceAtLeast(1L)
    BoxWithConstraints(modifier.fillMaxWidth().height(8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = if (photos.size > 30) 1.5f else 4f
            val usable = size.width - gap * (photos.size - 1).coerceAtLeast(0)
            var x = 0f
            photos.forEachIndexed { i, photo ->
                val w = (usable * photo.durationMs / total).coerceAtLeast(1f)
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
                val filled = when {
                    i < current -> 1f
                    i == current -> fraction
                    else -> 0f
                }
                if (filled > 0f) drawRoundRect(accent.start, Offset(x, 0f), Size(w * filled, size.height), r)
                x += w + gap
            }
        }
    }
}

/** A photo's thumbnail in the list (or an icon while it is read). */
@Composable
internal fun SlideThumb(bitmap: Bitmap?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 4.dp else 14.dp))
            .background(Palette.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(size * 0.45f).width(size))
        }
    }
}

/** "0:24", "1:05". */
internal fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
