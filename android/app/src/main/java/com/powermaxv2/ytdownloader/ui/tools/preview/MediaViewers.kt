package com.powermaxv2.ytdownloader.ui.tools.preview

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.RequiresApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.preview.PreviewFile
import com.powermaxv2.ytdownloader.core.preview.Waveform
import com.powermaxv2.ytdownloader.core.preview.WaveformData
import com.powermaxv2.ytdownloader.core.preview.WaveformDecoder
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// Image, video and sound viewers of the Aperçu. Every native object (MediaPlayer, VideoView, animated drawable)
// is released when the viewer leaves; decodes are bounded (images ≤ 4096 px, animated GIF frames ≤ 2048 px).

/** Longest side of a decoded image in the viewer. */
private const val IMAGE_MAX_SIDE = 4096

/** Longest side of the frames of an animated GIF / WebP. */
private const val ANIMATED_MAX_SIDE = 2048

// MARK: - Zoom

/** Zoom and pan of a viewer; values outside a gesture spring back into bounds. */
@Stable
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size = IntSize.Zero

    fun clampOffset(o: Offset, s: Float = scale): Offset {
        val maxX = (s - 1f) * size.width / 2f
        val maxY = (s - 1f) * size.height / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Pinch to zoom (1×–6×), pan while zoomed, double tap to zoom on a point or back out (animated), zoom below 1×
 * springs back. A single finger at 1× is left to the page (scrolling, the PDF pager).
 */
internal fun Modifier.zoomable(state: ZoomState, scope: kotlinx.coroutines.CoroutineScope): Modifier = this
    .clipToBounds()
    .onSizeChanged { state.size = it }
    .pointerInput(state) {
        detectTapGestures(onDoubleTap = { tap ->
            scope.launch {
                val from = state.scale
                val fromOffset = state.offset
                val to = if (from > 1.2f) 1f else 2.5f
                // Keep the tapped point under the finger.
                val center = Offset(state.size.width / 2f, state.size.height / 2f)
                val target = if (to == 1f) Offset.Zero else state.clampOffset((center - tap) * (to - 1f), to)
                animate(0f, 1f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f)) { p, _ ->
                    state.scale = from + (to - from) * p
                    state.offset = fromOffset + (target - fromOffset) * p
                }
            }
        })
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (fingers >= 2 || state.scale > 1.01f) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val newScale = (state.scale * zoom).coerceIn(0.6f, 6f)
                    state.scale = newScale
                    state.offset = state.clampOffset(state.offset + pan, newScale.coerceAtLeast(1f))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (state.scale < 1f) {
                val from = state.scale
                val fromOffset = state.offset
                scope.launch {
                    animate(0f, 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f)) { p, _ ->
                        state.scale = from + (1f - from) * p
                        state.offset = fromOffset * (1f - p)
                    }
                }
            }
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }

/** "Pincez pour zoomer" hint at the bottom of a zoomable viewer. */
@Composable
internal fun ZoomHint(modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (terminal) "PINCER / DOUBLE TOUCHER" else "Pincez ou touchez deux fois",
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}

// MARK: - Image

@Composable
internal fun ImageViewer(file: PreviewFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoom = remember(file.uri) { ZoomState() }
    var failed by remember(file.uri) { mutableStateOf(false) }
    var loaded by remember(file.uri) { mutableStateOf(false) }
    val animated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        (file.mime == "image/gif" || file.name.endsWith(".gif", ignoreCase = true) || file.mime == "image/webp")
    ViewerFrame(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (LocalTokens.current.isLight) Color(0xFFF1E6DB) else Color.Black.copy(alpha = 0.35f))
                .then(if (!loaded && !failed) Modifier.shimmer() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ce téléphone ne sait pas afficher cette image (format non pris en charge ou fichier abîmé).",
                        color = Palette.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val gif = if (animated) rememberAnimatedPainter(file.uri) else null
                if (gif != null) {
                    LaunchedEffect(gif) { loaded = true }
                    Image(gif, contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().zoomable(zoom, scope))
                } else {
                    AsyncImage(
                        model = remember(file.uri) {
                            ImageRequest.Builder(context)
                                .data(file.uri)
                                // Bounded decode: never more than 4096 px on the longest side, never upscaled.
                                .size(IMAGE_MAX_SIDE)
                                .precision(Precision.INEXACT)
                                .crossfade(250)
                                .build()
                        },
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        onSuccess = { loaded = true },
                        onError = { failed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomable(zoom, scope),
                    )
                }
                if (loaded) ZoomHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }
    }
}

/**
 * Animated GIF / WebP on Android 9+ (ImageDecoder's AnimatedImageDrawable, frames ≤ [ANIMATED_MAX_SIDE] px),
 * stopped and detached when it leaves; null while decoding, for a still image, or when decoding fails (the
 * caller then shows it with Coil, first frame only).
 */
@Composable
private fun rememberAnimatedPainter(uri: Uri): Painter? {
    val context = LocalContext.current
    var painter by remember(uri) { mutableStateOf<DrawablePainter?>(null) }
    LaunchedEffect(uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val drawable = withContext(Dispatchers.IO) { decodeAnimated(context, uri) }
            if (drawable != null) painter = DrawablePainter(drawable).also { drawable.start() }
        }
    }
    DisposableEffect(uri) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) painter?.dispose()
            painter = null
        }
    }
    return painter
}

/** An animated GIF / WebP as a drawable (frames bounded), or null for a still image or a failure. */
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimated(context: android.content.Context, uri: Uri): AnimatedImageDrawable? = try {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > ANIMATED_MAX_SIDE) {
            val s = ANIMATED_MAX_SIDE.toFloat() / longest
            decoder.setTargetSize(max(1, (info.size.width * s).roundToInt()), max(1, (info.size.height * s).roundToInt()))
        }
    }
    (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
} catch (e: Throwable) {
    null
}

/** Draws an animating [Drawable] in Compose: each frame it asks for invalidates a state read in onDraw. */
@RequiresApi(Build.VERSION_CODES.P)
private class DrawablePainter(private val drawable: AnimatedImageDrawable) : Painter() {
    private var tick by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    // Drawable keeps only a weak reference to its callback: this field holds it.
    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            tick++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what)
        }
    }

    init {
        drawable.callback = callback
    }

    override val intrinsicSize: Size
        get() = Size(drawable.intrinsicWidth.coerceAtLeast(1).toFloat(), drawable.intrinsicHeight.coerceAtLeast(1).toFloat())

    override fun DrawScope.onDraw() {
        @Suppress("UNUSED_EXPRESSION")
        tick
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    fun dispose() {
        drawable.stop()
        drawable.callback = null
        handler.removeCallbacksAndMessages(null)
    }
}

// MARK: - Play / pause, scrub bar

/** Round (square in Terminal) play button whose triangle morphs into the two pause bars. */
@Composable
internal fun PlayPauseButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val t by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "playMorph")
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.buttonRadius) else CircleShape
    val ink = if (skin == Skin.TERMINAL) accent.start else Palette.OnAccent
    val base = modifier
        .size(56.dp)
        .bounceClick(enabled = enabled, pressedScale = 0.88f, onClick = onClick)
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .clip(shape)
    val filled = when (skin) {
        Skin.CLASSIC -> base.background(accent.brush)
        Skin.PAPER -> base.background(accent.start)
        Skin.TERMINAL -> base.background(accent.start.copy(alpha = 0.12f)).border(1.dp, accent.start, shape)
    }
    Canvas(filled) {
        val s = size.minDimension
        fun lerp(a: Offset, b: Offset) = a + (b - a) * t
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        // Left and right halves of the play triangle → the two pause bars.
        val left = listOf(
            lerp(p(0.36f, 0.26f), p(0.32f, 0.27f)),
            lerp(p(0.56f, 0.38f), p(0.45f, 0.27f)),
            lerp(p(0.56f, 0.62f), p(0.45f, 0.73f)),
            lerp(p(0.36f, 0.74f), p(0.32f, 0.73f)),
        )
        val right = listOf(
            lerp(p(0.56f, 0.38f), p(0.55f, 0.27f)),
            lerp(p(0.76f, 0.50f), p(0.68f, 0.27f)),
            lerp(p(0.76f, 0.50f), p(0.68f, 0.73f)),
            lerp(p(0.56f, 0.62f), p(0.55f, 0.73f)),
        )
        for (quad in listOf(left, right)) {
            val path = Path().apply {
                moveTo(quad[0].x, quad[0].y)
                for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
                close()
            }
            drawPath(path, ink)
        }
    }
}

/** Seek bar: a track, the played part in the accent, a thumb that grows while dragged; tap or drag to seek. */
@Composable
internal fun ScrubBar(fraction: Float, enabled: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: fraction
    val thumb by animateFloatAsState(if (dragging != null) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "thumb")
    val track = Palette.TextPrimary.copy(alpha = 0.12f)
    val ink = Palette.TextPrimary
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragging?.let(onSeek)
                        dragging = null
                    },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val h = if (skin == Skin.TERMINAL) 4.dp.toPx() else 5.dp.toPx()
        val y = size.height / 2
        val cap = if (skin == Skin.TERMINAL) StrokeCap.Butt else StrokeCap.Round
        drawLine(track, Offset(h / 2, y), Offset(size.width - h / 2, y), strokeWidth = h, cap = cap)
        val x = (size.width - h) * shown.coerceIn(0f, 1f) + h / 2
        if (skin == Skin.CLASSIC) {
            drawLine(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.start, accent.end)), Offset(h / 2, y), Offset(x, y), strokeWidth = h, cap = cap)
        } else {
            drawLine(accent.start, Offset(h / 2, y), Offset(x, y), strokeWidth = h, cap = cap)
        }
        val r = (6.dp.toPx() + 3.dp.toPx() * thumb)
        if (skin == Skin.TERMINAL) {
            drawRect(accent.start, Offset(x - r * 0.5f, y - r), Size(r, r * 2))
        } else {
            drawCircle(if (skin == Skin.PAPER) ink else Color.White, r, Offset(x, y))
            drawCircle(accent.start, r * 0.55f, Offset(x, y))
        }
    }
}

/** "0:42 / 3:15" (Terminal: "[00:42/03:15]"). */
@Composable
internal fun TimeLabel(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pos = Fmt.duration(positionMs / 1000.0)
    val dur = if (durationMs > 0) Fmt.duration(durationMs / 1000.0) else "--:--"
    Text(
        if (terminal) "[$pos/$dur]" else "$pos / $dur",
        color = Palette.TextSecondary,
        fontSize = 12.5.sp,
        fontFamily = MonoFamily,
        modifier = modifier,
    )
}

/** Play button + scrub bar + time, in a row. */
@Composable
internal fun MediaControls(
    playing: Boolean,
    ready: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlayPauseButton(playing, ready, onToggle)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ScrubBar(Waveform.progress(positionMs, durationMs), ready && durationMs > 0, onSeek)
            TimeLabel(positionMs, durationMs)
        }
    }
}

// MARK: - Video

@Composable
internal fun VideoViewer(file: PreviewFile, modifier: Modifier = Modifier) {
    // The VideoView of this file (a plain holder: it is set from the AndroidView factory).
    val holder = remember(file.uri) { arrayOfNulls<VideoView>(1) }
    var ready by remember(file.uri) { mutableStateOf(false) }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var duration by remember(file.uri) { mutableLongStateOf(0L) }
    var position by remember(file.uri) { mutableLongStateOf(0L) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(playing, ready) {
        val v = holder[0] ?: return@LaunchedEffect
        while (playing) {
            position = v.currentPosition.toLong()
            delay(200)
        }
    }
    DisposableEffect(file.uri) {
        onDispose {
            holder[0]?.stopPlayback()
            holder[0] = null
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ViewerFrame(height = viewerHeight() * 0.7f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(if (!ready && error == null) Modifier.shimmer() else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (error != null) {
                    Text(error.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                } else {
                    key(file.uri) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setOnPreparedListener { mp ->
                                        duration = mp.duration.toLong().coerceAtLeast(0)
                                        ready = true
                                        // Show the first frame.
                                        seekTo(1)
                                    }
                                    setOnCompletionListener {
                                        playing = false
                                        position = duration
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        error = "Ce téléphone ne sait pas lire cette vidéo (codec non pris en charge ou fichier abîmé)."
                                        playing = false
                                        true
                                    }
                                    setVideoURI(file.uri)
                                    holder[0] = this
                                }
                            },
                        )
                    }
                }
            }
        }
        MediaControls(
            playing = playing,
            ready = ready && error == null,
            positionMs = position,
            durationMs = duration,
            onToggle = {
                val v = holder[0] ?: return@MediaControls
                if (playing) v.pause() else v.start()
                playing = !playing
            },
            onSeek = { f ->
                val v = holder[0] ?: return@MediaControls
                val ms = (duration * f).toLong()
                v.seekTo(ms.toInt())
                position = ms
            },
        )
    }
}

// MARK: - Sound

@Composable
internal fun AudioViewer(file: PreviewFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file.uri) { MediaPlayer() }
    var ready by remember(file.uri) { mutableStateOf(false) }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var duration by remember(file.uri) { mutableLongStateOf(0L) }
    var position by remember(file.uri) { mutableLongStateOf(0L) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }
    var waveform by remember(file.uri) { mutableStateOf<WaveformData?>(null) }
    var decoding by remember(file.uri) { mutableStateOf(true) }
    // Token of the current decode: a late result of a previous file never lands here.
    val generation = remember { intArrayOf(0) }

    DisposableEffect(file.uri) {
        try {
            player.setDataSource(context, file.uri)
            player.setOnPreparedListener {
                duration = it.duration.toLong().coerceAtLeast(0)
                ready = true
            }
            player.setOnCompletionListener {
                playing = false
                position = duration
            }
            player.setOnErrorListener { _, _, _ ->
                error = "Ce téléphone ne sait pas lire ce son."
                playing = false
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            error = "Ce téléphone ne sait pas lire ce son."
        }
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }
    LaunchedEffect(file.uri) {
        val token = ++generation[0]
        decoding = true
        val result = WaveformDecoder.decode(context, file.uri)
        if (token == generation[0]) {
            waveform = result
            decoding = false
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition.toLong() }.getOrDefault(position)
            delay(80)
        }
    }
    val seek: (Float) -> Unit = { f ->
        if (ready) {
            val ms = (duration * f).toLong()
            runCatching { player.seekTo(ms.toInt()) }
            position = ms
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ViewerFrame(height = viewerHeight() * 0.5f) {
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
                val wave = waveform
                when {
                    decoding -> WaveformPlaceholder(Modifier.fillMaxSize())
                    wave == null -> Text(
                        "Forme d'onde indisponible pour ce format, mais la lecture peut fonctionner.",
                        color = Palette.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    else -> WaveformBars(
                        bars = wave.bars,
                        progress = Waveform.progress(position, duration),
                        onSeek = seek,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (waveform?.partial == true) {
            NoteBanner("Forme d'onde du début seulement : Gulp analyse au plus 10 minutes de son.")
        }
        error?.let { NoteBanner(it) }
        MediaControls(
            playing = playing,
            ready = ready && error == null,
            positionMs = position,
            durationMs = duration,
            onToggle = {
                if (!ready) return@MediaControls
                if (playing) player.pause() else player.start()
                playing = !playing
            },
            onSeek = seek,
        )
    }
}

/** Bars of the waveform: they rise in a staggered wave when they arrive; played bars take the accent. */
@Composable
internal fun WaveformBars(bars: FloatArray, progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val rise = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { rise.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val played by animateFloatAsState(progress, tween(120, easing = LinearEasing), label = "playhead")
    val idle = Palette.TextPrimary.copy(alpha = if (LocalTokens.current.isLight) 0.22f else 0.25f)
    // Draw ~120 bars on a phone: each one is the loudest of a group of buckets.
    Canvas(
        modifier.pointerInput(bars) {
            detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
        },
    ) {
        val count = (size.width / 6.dp.toPx()).toInt().coerceIn(24, bars.size.coerceAtLeast(24))
        val step = size.width / count
        val barW = (step * 0.62f).coerceAtLeast(1f)
        val mid = size.height / 2
        for (i in 0 until count) {
            val from = i * bars.size / count
            val to = ((i + 1) * bars.size / count).coerceAtLeast(from + 1).coerceAtMost(bars.size)
            var v = 0f
            for (b in from until to) if (bars[b] > v) v = bars[b]
            // Staggered rise: bar i starts a little after bar i-1.
            val local = ((rise.value * 1.6f) - i.toFloat() / count * 0.6f).coerceIn(0f, 1f)
            val h = (v.coerceAtLeast(0.03f) * size.height * 0.92f * local).coerceAtLeast(2.dp.toPx())
            val x = i * step + (step - barW) / 2
            val color = if ((i + 0.5f) / count <= played) accent.start else idle
            if (skin == Skin.TERMINAL) {
                drawRect(color, Offset(x, mid - h / 2), Size(barW, h))
            } else {
                drawLine(color, Offset(x + barW / 2, mid - h / 2), Offset(x + barW / 2, mid + h / 2), strokeWidth = barW, cap = StrokeCap.Round)
            }
        }
        // Playhead.
        val px = size.width * played
        drawLine(if (skin == Skin.CLASSIC) accent.end else accent.start, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
    }
}

/** While the sound is analysed: a soft sine of bars breathing. */
@Composable
private fun WaveformPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wavePlaceholder")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "wavePhase")
    val color = Palette.TextPrimary.copy(alpha = 0.14f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val count = (size.width / 6.dp.toPx()).toInt().coerceAtLeast(12)
            val step = size.width / count
            val mid = size.height / 2
            for (i in 0 until count) {
                val a = kotlin.math.sin((i.toFloat() / count * 4f - phase * 2f) * Math.PI.toFloat())
                val h = (0.25f + 0.2f * a) * size.height
                val x = i * step + step / 2
                drawLine(color, Offset(x, mid - h / 2), Offset(x, mid + h / 2), strokeWidth = step * 0.6f, cap = StrokeCap.Round)
            }
        }
        Text("Analyse du son…", color = Palette.TextSecondary, fontSize = 12.5.sp)
    }
}
