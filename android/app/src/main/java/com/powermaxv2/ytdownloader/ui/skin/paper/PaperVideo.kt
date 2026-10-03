package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.DownloadJob
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.JobState
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalDownloadGate
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.RemoteImage
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.TileGrid
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.pulsing
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF111216)

// MARK: - Thumbnail

/** 16:9 thumbnail with a centred accent play disc (breathing ring) and a black duration pill. */
@Composable
private fun PaperThumbnail(url: String?, duration: Double?, key: Any) {
    val accent = LocalAccent.current.start
    val pop = remember(key) { Animatable(0.4f) }
    LaunchedEffect(key) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface2),
    ) {
        RemoteImage(url, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = pop.value.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .pulsing(0.94f, 1.06f, 1600)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(30.dp))
            }
        }
        duration?.let {
            Text(
                Fmt.duration(it),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

// MARK: - Mode switch

/** "Vidéo + son" / "Audio seul" on a grey track; the black selected segment slides. */
@Composable
private fun PaperSegmented(selected: MediaMode, onSelect: (MediaMode) -> Unit, modifier: Modifier = Modifier) {
    val options = MediaMode.entries
    val index = options.indexOf(selected).coerceAtLeast(0)
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val ink = Palette.TextPrimary
    val segmentShape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface2)
            .padding(5.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.78f, stiffness = 420f), label = "paperSegment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(segmentShape)
                .background(ink),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val color by animateColorAsState(
                    if (isSelected) Palette.Surface else Palette.TextPrimary.copy(alpha = 0.72f), label = "paperSegmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .clickable {
                            view.tick(haptics)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.title, color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

// MARK: - Tiles and chips

/** White tile: big label + grey detail; the accent border springs in when selected, [max] adds a popping "MAX" badge. */
@Composable
private fun PaperTile(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    max: Boolean = false,
    badgeKey: Any? = Unit,
) {
    val accent = LocalAccent.current.start
    val shape = RoundedCornerShape(18.dp)
    val borderWidth by animateDpAsState(if (selected) 2.dp else 0.5.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "paperTileBorder")
    val borderColor by animateColorAsState(if (selected) accent else Color.Transparent, tween(200), label = "paperTileColor")
    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .bounceClick(onClick = onClick)
                .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(shape)
                .background(Palette.Surface)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (max) {
            val pop = remember(badgeKey) { Animatable(0f) }
            LaunchedEffect(badgeKey) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f)) }
            Text(
                "MAX",
                color = Palette.OnAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (-9).dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> PaperChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val accent = LocalAccent.current.start
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val borderWidth by animateDpAsState(if (isSelected) 2.dp else 0.5.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "paperChipBorder")
            val borderColor by animateColorAsState(if (isSelected) accent else Color.Transparent, tween(200), label = "paperChipColor")
            Box(
                Modifier
                    .bounceClick { onSelect(option) }
                    .height(40.dp)
                    .paperCard(CircleShape)
                    .border(borderWidth, borderColor, CircleShape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) Palette.TextPrimary else Palette.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PaperSectionTitle(title: String, note: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = Palette.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (note != null) {
            AnimatedContent(
                targetState = note,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "paperNote",
            ) {
                Text(it, color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** The current container in a few words, next to "Qualité". */
private fun containerNote(container: VideoContainer) = when (container) {
    VideoContainer.MP4 -> "MP4 · fusion audio auto"
    VideoContainer.COMPAT -> "H.264 · lisible partout"
    VideoContainer.MKV -> "MKV · conteneur flexible"
}

// MARK: - Single video

@Composable
fun PaperVideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val qualities = info.qualityOptions
    val hasVideo = qualities.isNotEmpty()
    var mode by remember(info) { mutableStateOf(if (hasVideo) MediaMode.VIDEO else MediaMode.AUDIO) }
    var quality by remember(info) { mutableStateOf(qualities.firstOrNull()) }
    var container by remember(info) { mutableStateOf(settings.container) }
    var audioFormat by remember(info) { mutableStateOf(settings.audioFormat) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .paperCard()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PaperThumbnail(info.thumbnail, info.duration, info.id)
            Column(
                Modifier
                    .padding(horizontal = 4.dp)
                    .staggeredEntrance(0, key = info.id),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    info.title,
                    color = Palette.TextPrimary,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    letterSpacing = (-0.3).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(info.uploader, info.viewCount?.let { Fmt.views(it) }).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (hasVideo) {
                PaperSegmented(mode, { mode = it }, Modifier.staggeredEntrance(1, key = info.id))
            }
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(180)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(140)))
            },
            label = "paperMode",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (current == MediaMode.VIDEO) {
                    PaperSectionTitle("Qualité", containerNote(container))
                    TileGrid(qualities, columns = 3) { index, option, cell ->
                        PaperTile(
                            title = option.label,
                            detail = option.detail,
                            selected = quality == option,
                            onClick = { quality = option },
                            modifier = cell.staggeredEntrance(index, key = info.id to "video"),
                            max = index == 0 && qualities.size > 1,
                            badgeKey = info.id,
                        )
                    }
                    PaperSectionTitle("Format", null)
                    PaperChips(VideoContainer.entries, container, { it.title }, { container = it })
                } else {
                    PaperSectionTitle("Format audio", info.audioEstimatedSize?.let { "~" + Fmt.bytes(it) })
                    TileGrid(AudioFormat.entries, columns = 2) { index, format, cell ->
                        PaperTile(
                            title = format.title,
                            detail = format.badge + " · " + format.subtitle,
                            selected = audioFormat == format,
                            onClick = { audioFormat = format },
                            modifier = cell.staggeredEntrance(index, key = info.id to "audio"),
                        )
                    }
                }
            }
        }
        PaperButton(
            "Télécharger",
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Download,
            subtitle = if (mode == MediaMode.VIDEO) "${quality?.label ?: "Max"} · ${container.title}" else "Audio · ${audioFormat.title}",
        ) {
            gate { vm.downloadVideo(info, sourceUrl, mode, quality, container, audioFormat) }
        }
    }
}

// MARK: - Recent download strip

/** Short label for the black square: "4K", "1080p", "MP3"…, or null to show the platform logo. */
private fun jobBadge(job: DownloadJob): String? {
    val head = job.summary.substringBefore(" · ").trim()
    if (head == "Audio") return job.summary.substringAfter(" · ", "").trim().takeIf { it.isNotEmpty() && it.length <= 4 }
    val p = head.removeSuffix("p").toIntOrNull() ?: return head.takeIf { it.isNotEmpty() && it.length <= 4 }
    return when {
        p >= 4320 -> "8K"
        p >= 2160 -> "4K"
        p >= 1440 -> "2K"
        else -> "${p}p"
    }
}

/** Black bar on a grey track; negative [progress] = indeterminate sweep. */
@Composable
private fun InkProgress(progress: Float, modifier: Modifier = Modifier) {
    val track = Palette.Surface2
    val ink = Palette.TextPrimary
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "inkProgress")
    val transition = rememberInfiniteTransition(label = "inkSweep")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "inkPhase")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (indeterminate) {
            val w = size.width * 0.32f
            val x = (size.width + w) * phase - w
            val left = max(0f, x)
            val right = min(size.width, x + w)
            if (right > left) drawRoundRect(ink, topLeft = Offset(left, 0f), size = Size(right - left, size.height), cornerRadius = radius)
        } else {
            drawRoundRect(ink, size = Size(max(size.height, size.width * animated), size.height), cornerRadius = radius)
        }
    }
}

/** Compact strip of the active download (else the latest one); tap opens Fichiers. */
@Composable
fun PaperRecentDownload(vm: MainViewModel) {
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val job = jobs.firstOrNull { it.isActive } ?: jobs.firstOrNull()
    AnimatedVisibility(
        visible = job != null,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        // Keep the last job while the strip collapses.
        val shown = remember { mutableStateOf<DownloadJob?>(null) }
        if (job != null) shown.value = job
        val current = shown.value ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick { vm.selectTab(Tab.DOWNLOADS) }
                .paperCard()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink),
                contentAlignment = Alignment.Center,
            ) {
                val badge = jobBadge(current)
                if (badge != null) {
                    Text(badge, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                } else {
                    PlatformGlyph(current.platform, Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    current.title,
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current.isActive) {
                    InkProgress(if (current.state == JobState.QUEUED) 0f else current.progress)
                }
                val statusColor = when (current.state) {
                    JobState.DONE -> Palette.Success
                    JobState.FAILED -> Palette.Danger
                    else -> Palette.TextTertiary
                }
                Text(current.status, color = statusColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
