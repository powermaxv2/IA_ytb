package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaInfo
import com.powermaxv2.ytdownloader.core.Platform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val engineState by vm.engine.state.collectAsStateWithLifecycle()
    val integrity by vm.engine.integrityAlert.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(vm.queuedPulse)
        AnimatedVisibility(
            visible = integrity != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Banner(
                icon = Icons.Rounded.Shield,
                text = integrity ?: "",
                color = Palette.Danger,
                onDismiss = vm.engine::dismissIntegrityAlert,
            )
        }
        EngineBanner(engineState)
        UrlBar(vm, enabled = engineState !is EngineState.Failed)
        AnimatedContent(
            targetState = vm.phase,
            transitionSpec = {
                (
                    fadeIn(tween(350, delayMillis = 90)) +
                        slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 6 } +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(350, delayMillis = 90))
                    ) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f)) using SizeTransform(clip = false)
            },
            label = "phase",
        ) { phase ->
            when (phase) {
                Phase.Idle -> EmptyCard()
                is Phase.Loading -> LoadingCard(phase.platform)
                is Phase.Failed -> ErrorCard(
                    message = phase.message,
                    onRetry = vm::analyze,
                    onLogin = if (phase.message.contains("Instagram")) ({ vm.showInstagramLogin = true }) else null,
                )
                is Phase.Ready -> when (val media = phase.media) {
                    is MediaInfo.Video -> VideoCard(media.info, media.sourceUrl, vm)
                    is MediaInfo.Playlist -> PlaylistCard(media.info, vm)
                    is MediaInfo.Photos -> PhotosCard(media.post, vm)
                }
            }
        }
    }
}

// MARK: - Header

@Composable
private fun Header(pulse: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedLogo(pulse, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "YT Downloader",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.staggeredEntrance(0),
            )
            Text(
                "YouTube & Instagram · vidéos, musique, photos",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.staggeredEntrance(1),
            )
        }
    }
}

/** Logo: orbiting gradient ring, bobbing arrow that drops through the box when something is queued. */
@Composable
fun AnimatedLogo(pulse: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "logo")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "ring")
    val bob by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val drop = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            drop.snapTo(0f)
            drop.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            drop.snapTo(0f)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(rotation) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, accent.start, accent.end, Color.Transparent)),
                    radius = size.minDimension / 2 - 1.5.dp.toPx(),
                    style = Stroke(2.5.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize(0.72f)
                .shadow(14.dp, RoundedCornerShape(13.dp), spotColor = accent.start, ambientColor = accent.start)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val d = drop.value
                        val travel = 30.dp.toPx()
                        val dropOffset = if (d < 0.5f) d * 2 * travel else (d - 1f) * 2 * travel
                        translationY = bob * 2.dp.toPx() + dropOffset
                        alpha = 1f - abs(sin(d * PI)).toFloat() * 0.9f
                    },
            )
        }
    }
}

// MARK: - Banners

@Composable
private fun Banner(icon: ImageVector, text: String, color: Color, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        onDismiss?.let { IconCircleButton(Icons.Rounded.Close, size = 28.dp, onClick = it) }
    }
}

@Composable
private fun EngineBanner(state: EngineState) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) },
        label = "engine",
    ) { current ->
        when (current) {
            EngineState.Starting -> Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp))
                    .shimmer()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = LocalAccent.current.start, strokeWidth = 2.dp)
                Text(
                    "Préparation du moteur yt-dlp… (quelques secondes au premier lancement)",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                )
            }
            is EngineState.Failed -> Banner(
                icon = Icons.Rounded.ErrorOutline,
                text = "Le moteur n'a pas pu démarrer : ${current.message}",
                color = Palette.Danger,
            )
            EngineState.Ready -> Spacer(Modifier.height(0.dp))
        }
    }
}

// MARK: - Link field

@Composable
private fun UrlBar(vm: MainViewModel, enabled: Boolean) {
    val accent = LocalAccent.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val platform = remember(vm.urlText) { Links.platformOfText(vm.urlText) }
    val transition = rememberInfiniteTransition(label = "border")
    val angle by transition.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "angle",
    )
    val outline by animateFloatAsState(if (focused) 1f else 0f, tween(300), label = "outline")
    val loading = vm.phase is Phase.Loading
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Palette.Stroke, shape)
            .drawWithContent {
                drawContent()
                if (outline > 0f) {
                    val r = size.maxDimension
                    val dx = cos(angle) * r
                    val dy = sin(angle) * r
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(accent.start, accent.end, Palette.Purple, accent.start),
                            start = Offset(center.x + dx, center.y + dy),
                            end = Offset(center.x - dx, center.y - dy),
                        ),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        alpha = outline,
                        style = Stroke(1.6.dp.toPx()),
                    )
                }
            }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedPlatformGlyph(platform, Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = vm.urlText,
                onValueChange = { vm.urlText = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(accent.start),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    vm.analyze()
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.urlText.isEmpty()) {
                            Text("Collez un lien YouTube ou Instagram…", color = Palette.TextTertiary, fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = vm.urlText.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconCircleButton(Icons.Rounded.Close, Modifier.padding(start = 8.dp), size = 28.dp) { vm.reset() }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Coller", Icons.Rounded.ContentPaste, Modifier.weight(1f)) {
                focus.clearFocus()
                vm.paste(clipboard.getText()?.text)
            }
            GradientButton(
                "Analyser",
                Modifier.weight(1.4f),
                icon = Icons.Rounded.Search,
                enabled = enabled && vm.urlText.isNotBlank(),
                loading = loading,
            ) {
                focus.clearFocus()
                vm.analyze()
            }
        }
    }
}

// MARK: - Placeholder cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrbitIcon()
        Text(
            "Collez un lien, ou partagez-le depuis YouTube ou Instagram",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(0, baseDelay = 120),
        )
        Row(
            Modifier.staggeredEntrance(1, baseDelay = 120),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(15.dp))
            Text(
                "Dans l'app : Partager → YT Downloader",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            val pills = listOf(
                Icons.Rounded.HighQuality to "Jusqu'en 8K",
                Icons.Rounded.MusicNote to "MP3 · M4A · FLAC",
                Icons.Rounded.VideoLibrary to "Playlists",
                Icons.Rounded.PlayArrow to "Reels",
                Icons.Rounded.PhotoLibrary to "Photos Insta",
            )
            pills.forEachIndexed { index, (icon, text) ->
                FeaturePill(icon, text, Modifier.staggeredEntrance(index, baseDelay = 260))
            }
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Palette.Stroke, CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(15.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Play icon floating inside a breathing halo, with three dots orbiting around it. */
@Composable
private fun OrbitIcon() {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "angle")
    val breathe by transition.animateFloat(
        0.9f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 - 6.dp.toPx()
            drawCircle(accent.start.copy(alpha = 0.14f), radius = r * 0.74f * breathe)
            drawCircle(Color.White.copy(alpha = 0.07f), radius = r, style = Stroke(1.dp.toPx()))
            val a = Math.toRadians(angle.toDouble())
            listOf(0.0 to Palette.Accent, 2.1 to Palette.InstaPink, 4.2 to Palette.Accent2).forEach { (shift, color) ->
                val point = Offset((cos(a + shift) * r).toFloat(), (sin(a + shift) * r).toFloat())
                drawCircle(color, radius = 4.dp.toPx(), center = center + point)
            }
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(44.dp)
                .floating(amplitude = 3.dp)
                .gradientTint(accent.brush),
        )
    }
}

@Composable
private fun LoadingCard(platform: Platform) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .shimmer(),
            contentAlignment = Alignment.Center,
        ) {
            PlatformGlyph(platform, Modifier.size(46.dp).pulsing())
        }
        listOf(0.92f, 0.6f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .shimmer(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), color = LocalAccent.current.start, strokeWidth = 2.dp)
            Text(
                if (platform == Platform.OTHER) "Analyse du lien…" else "Analyse du lien ${platform.label} et des qualités…",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onLogin: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .glass(tint = Palette.Danger.copy(alpha = 0.09f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Palette.Danger,
            modifier = Modifier
                .size(38.dp)
                .pulsing(0.96f, 1.08f, 900),
        )
        Text(message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Réessayer", Icons.Rounded.Refresh, compact = true, onClick = onRetry)
            onLogin?.let { SecondaryButton("Connexion Instagram", Icons.Rounded.Login, compact = true, onClick = it) }
        }
    }
}
