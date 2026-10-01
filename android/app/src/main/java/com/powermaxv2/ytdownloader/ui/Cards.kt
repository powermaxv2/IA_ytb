package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.InstagramMedia
import com.powermaxv2.ytdownloader.core.InstagramPost
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.PlaylistEntry
import com.powermaxv2.ytdownloader.core.PlaylistInfo
import com.powermaxv2.ytdownloader.core.PlaylistQuality
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo

private fun modeIcon(mode: MediaMode) = if (mode == MediaMode.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(450).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** Thumbnail with a slow Ken Burns zoom/pan, platform badge and duration. */
@Composable
private fun HeroThumbnail(url: String?, duration: Double?, platform: Platform) {
    val transition = rememberInfiniteTransition(label = "kenBurns")
    val zoom by transition.animateFloat(
        1f, 1.1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "zoom",
    )
    val pan by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(13000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pan",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        RemoteImage(
            url,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan * 8.dp.toPx()
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(6.dp),
        ) {
            PlatformGlyph(platform, Modifier.size(20.dp))
        }
        duration?.let {
            Text(
                Fmt.duration(it),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

// MARK: - Single video (YouTube video, Instagram reel…)

@Composable
fun VideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val qualities = info.qualityOptions
    val hasVideo = qualities.isNotEmpty()
    var mode by remember(info) { mutableStateOf(if (hasVideo) MediaMode.VIDEO else MediaMode.AUDIO) }
    var quality by remember(info) { mutableStateOf(qualities.firstOrNull()) }
    var container by remember(info) { mutableStateOf(settings.container) }
    var audioFormat by remember(info) { mutableStateOf(settings.audioFormat) }
    val platform = Links.platform(sourceUrl)

    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroThumbnail(info.thumbnail, info.duration, platform)
        Column(Modifier.staggeredEntrance(0, key = info.id), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                info.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(info.uploader, info.viewCount?.let { Fmt.views(it) }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
        }
        if (hasVideo) {
            SegmentedControl(
                options = MediaMode.entries,
                selected = mode,
                label = { it.title },
                icon = ::modeIcon,
                onSelect = { mode = it },
                modifier = Modifier.staggeredEntrance(1, key = info.id),
            )
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 340f)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
            },
            label = "mode",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current == MediaMode.VIDEO) {
                    SectionLabel("Qualité")
                    TileGrid(qualities, columns = 2) { index, option, cell ->
                        SelectableTile(
                            title = option.label,
                            badge = option.badge,
                            detail = option.detail,
                            selected = quality == option,
                            highlight = index == 0 && qualities.size > 1,
                            onClick = { quality = option },
                            modifier = cell.staggeredEntrance(index, key = info.id to "video"),
                        )
                    }
                    SectionLabel("Format")
                    ChipRow(VideoContainer.entries, container, { it.title }, { container = it })
                    AnimatedContent(targetState = container, label = "containerInfo") {
                        Text(it.subtitle, color = Palette.TextTertiary, fontSize = 12.sp)
                    }
                } else {
                    SectionLabel("Format audio")
                    val audioSize = info.audioEstimatedSize
                    TileGrid(AudioFormat.entries, columns = 2) { index, format, cell ->
                        SelectableTile(
                            title = format.title,
                            badge = format.badge,
                            detail = format.subtitle + (audioSize?.let { " · ~" + Fmt.bytes(it) } ?: ""),
                            selected = audioFormat == format,
                            onClick = { audioFormat = format },
                            modifier = cell.staggeredEntrance(index, key = info.id to "audio"),
                        )
                    }
                }
            }
        }
        val label = if (mode == MediaMode.VIDEO) {
            "Télécharger · ${quality?.label ?: "Max"} · ${container.title}"
        } else {
            "Télécharger · ${audioFormat.title}"
        }
        GradientButton(label, Modifier.fillMaxWidth(), icon = Icons.Rounded.Download) {
            gate { vm.downloadVideo(info, sourceUrl, mode, quality, container, audioFormat) }
        }
    }
}

// MARK: - Playlists and carousels of videos

@Composable
fun PlaylistCard(playlist: PlaylistInfo, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val available = remember(playlist) { playlist.entries.filterNot { it.isUnavailable } }
    var selected by remember(playlist) { mutableStateOf(available.map { it.id }.toSet()) }
    var mode by remember(playlist) { mutableStateOf(MediaMode.VIDEO) }
    var quality by remember(playlist) { mutableStateOf(PlaylistQuality.ladder.first()) }
    var container by remember(playlist) { mutableStateOf(settings.container) }
    var audioFormat by remember(playlist) { mutableStateOf(settings.audioFormat) }
    var expanded by remember(playlist) { mutableStateOf(false) }
    val accent = LocalAccent.current
    val preview = 40

    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 300f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.floating(2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull("${playlist.entries.size} vidéos", playlist.uploader).joinToString(" · "),
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                )
            }
        }
        SegmentedControl(MediaMode.entries, mode, { it.title }, ::modeIcon, { mode = it })
        AnimatedContent(targetState = mode, label = "playlistMode") { current ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current == MediaMode.VIDEO) {
                    SectionLabel("Qualité maximale")
                    ChipRow(PlaylistQuality.ladder, quality, { it.title }, { quality = it })
                    SectionLabel("Format")
                    ChipRow(VideoContainer.entries, container, { it.title }, { container = it })
                } else {
                    SectionLabel("Format audio")
                    ChipRow(AudioFormat.entries, audioFormat, { "${it.title} · ${it.badge}" }, { audioFormat = it })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size} / ${available.size} sélectionnées", color = Palette.TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            val all = selected.size == available.size
            SecondaryButton(if (all) "Aucune" else "Toutes", null, compact = true) {
                selected = if (all) emptySet() else available.map { it.id }.toSet()
            }
        }
        val shown = if (expanded) playlist.entries else playlist.entries.take(preview)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEachIndexed { index, entry ->
                PlaylistRow(
                    entry = entry,
                    checked = entry.id in selected,
                    modifier = Modifier.staggeredEntrance(index, key = playlist.id),
                ) {
                    if (!entry.isUnavailable) selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                }
            }
        }
        if (!expanded && playlist.entries.size > preview) {
            SecondaryButton("Afficher les ${playlist.entries.size - preview} autres", Icons.Rounded.ExpandMore, Modifier.fillMaxWidth()) {
                expanded = true
            }
        }
        val count = selected.size
        val what = when {
            mode == MediaMode.AUDIO -> if (count > 1) "pistes audio" else "piste audio"
            count > 1 -> "vidéos"
            else -> "vidéo"
        }
        GradientButton(
            "Télécharger $count $what",
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Download,
            enabled = count > 0,
        ) {
            gate {
                vm.downloadPlaylist(playlist, playlist.entries.filter { it.id in selected }, mode, quality, container, audioFormat)
            }
        }
    }
}

@Composable
private fun PlaylistRow(entry: PlaylistEntry, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val background by animateColorAsState(
        if (checked) LocalAccent.current.start.copy(alpha = 0.10f) else Color.Transparent, label = "rowBg",
    )
    Row(
        modifier
            .bounceClick(enabled = !entry.isUnavailable, pressedScale = 0.97f, onClick = onToggle)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .graphicsLayer { alpha = if (entry.isUnavailable) 0.4f else 1f }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedCheckbox(checked && !entry.isUnavailable)
        Box(
            Modifier
                .width(88.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            RemoteImage(entry.thumbnail, Modifier.fillMaxSize())
            entry.duration?.let {
                Text(
                    Fmt.duration(it),
                    color = Color.White,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Text(entry.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

// MARK: - Instagram photos / carousels

@Composable
fun PhotosCard(post: InstagramPost, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    var selected by remember(post) { mutableStateOf(post.items.indices.toSet()) }

    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformGlyph(Platform.INSTAGRAM, Modifier.size(38.dp).pulsing(0.97f, 1.03f, 2200))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.owner?.let { "@$it" } ?: "Instagram", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val counts = listOfNotNull(
                    "Story".takeIf { post.isStory },
                    post.photoCount.takeIf { it > 0 }?.let { if (it > 1) "$it photos" else "1 photo" },
                    post.videoCount.takeIf { it > 0 }?.let { if (it > 1) "$it vidéos" else "1 vidéo" },
                ).joinToString(" · ")
                Text(counts, color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
        }
        post.caption?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        TileGrid(post.items, columns = 3, equalHeight = false) { index, media, cell ->
            MediaThumb(
                media = media,
                selected = index in selected,
                modifier = cell
                    .aspectRatio(1f)
                    .staggeredEntrance(index, key = post.shortcode),
            ) {
                selected = if (index in selected) selected - index else selected + index
            }
        }
        val count = selected.size
        GradientButton(
            if (count > 1) "Télécharger $count éléments" else "Télécharger $count élément",
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Download,
            enabled = count > 0,
        ) {
            gate { vm.downloadPhotos(post, selected.toList()) }
        }
    }
}

@Composable
private fun MediaThumb(media: InstagramMedia, selected: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val accent = LocalAccent.current
    val dim by animateFloatAsState(if (selected) 0f else 0.55f, tween(250), label = "dim")
    val outline by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "outline")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .bounceClick(pressedScale = 0.93f, onClick = onToggle)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Palette.Stroke, shape)
            .drawWithContent {
                drawContent()
                drawRect(Color.Black, alpha = dim)
                if (outline > 0f) {
                    drawRoundRect(
                        Brush.linearGradient(listOf(accent.start, accent.end)),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        alpha = outline,
                        style = Stroke(2.5.dp.toPx()),
                    )
                }
            },
    ) {
        RemoteImage(media.thumbnail, Modifier.fillMaxSize())
        if (media.isVideo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedCheckbox(
            selected,
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}
