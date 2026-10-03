package com.powermaxv2.ytdownloader.ui.skin.terminal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import com.powermaxv2.ytdownloader.core.QualityOption
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalDownloadGate
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.RemoteImage
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

// MARK: - Thumbnail

/** Animated equalizer bars, shown behind the thumbnail until the image covers them. */
@Composable
private fun Equalizer(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val transition = rememberInfiniteTransition(label = "termEq")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "termEqPhase")
    Canvas(modifier) {
        val bars = 6
        val barW = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val maxH = size.height * 0.42f
        val total = bars * barW + (bars - 1) * gap
        val startX = (size.width - total) / 2f
        val baseY = size.height * 0.72f
        for (i in 0 until bars) {
            val wave = (sin((phase + i * 0.17f) * 2f * PI.toFloat()) + 1f) / 2f
            val h = maxH * (0.3f + 0.7f * wave)
            drawRect(accent, topLeft = Offset(startX + i * (barW + gap), baseY - h), size = Size(barW, h))
        }
    }
}

@Composable
private fun TermThumbnail(url: String?) {
    Box(
        Modifier
            .size(width = 116.dp, height = 66.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Surface2),
    ) {
        Equalizer(Modifier.fillMaxSize())
        RemoteImage(url, Modifier.fillMaxSize())
    }
}

// MARK: - Mode switch

/** Two flat segments; the accent block slides under the selected one. */
@Composable
private fun TermSegmented(selected: MediaMode, onSelect: (MediaMode) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val options = MediaMode.entries
    val index = options.indexOf(selected).coerceAtLeast(0)
    val view = LocalView.current
    val haptics = LocalHaptics.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .termPanel(),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.85f, stiffness = 500f), label = "termSegment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .background(accent),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val color by animateColorAsState(if (isSelected) Palette.OnAccent else Palette.TextSecondary, label = "termSegmentText")
                val label = if (option == MediaMode.VIDEO) "VIDÉO+SON" else "AUDIO SEUL"
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            view.tick(haptics)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isSelected) "[ $label ]" else label,
                        color = color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// MARK: - Option list

/** "2160p 4K 60 HDR". */
private fun qualityTitle(option: QualityOption): String = buildList {
    add(option.label)
    if (option.height != null) {
        add(if (option.badge == "Full HD") "FHD" else option.badge.uppercase())
    }
    if (option.maxFps > 0) add(option.maxFps.toString())
    if (option.hdr) add("HDR")
}.joinToString(" ")

/** One list row: "> " slides in and the text turns accent when selected. */
@Composable
private fun TermRow(
    title: String,
    trailing: String?,
    selected: Boolean,
    max: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val selectedFill = accent.copy(alpha = 0.08f).compositeOver(Palette.Surface)
    val fill by animateColorAsState(if (selected) selectedFill else Palette.Surface, tween(180), label = "termRowFill")
    val ink by animateColorAsState(if (selected) accent else Palette.TextPrimary, tween(180), label = "termRowInk")
    val prefix by animateDpAsState(if (selected) 22.dp else 0.dp, spring(dampingRatio = 0.75f, stiffness = 520f), label = "termRowPrefix")
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                view.tick(haptics)
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(prefix).clip(RoundedCornerShape(0.dp))) {
            Text(">", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Text(
            title,
            color = ink,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(it, color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
        }
        if (max) {
            Spacer(Modifier.width(8.dp))
            Text(
                "MAX",
                color = Palette.OnAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Vertical list of rows separated by 1dp lines. */
@Composable
private fun <T> TermList(
    options: List<T>,
    selected: T?,
    key: Any,
    title: (T) -> String,
    trailing: (T) -> String?,
    isMax: (Int) -> Boolean,
    onSelect: (T) -> Unit,
) {
    val stroke = Palette.Stroke
    Column(
        Modifier
            .fillMaxWidth()
            .termPanel(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(stroke),
                )
            }
            TermRow(
                title = title(option),
                trailing = trailing(option),
                selected = option == selected,
                max = isMax(index),
                onClick = { onSelect(option) },
                modifier = Modifier.staggeredEntrance(index, key = key),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermContainers(selected: VideoContainer, onSelect: (VideoContainer) -> Unit) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoContainer.entries.forEach { option ->
            val isSelected = option == selected
            val color by animateColorAsState(if (isSelected) accent else Palette.TextSecondary, label = "termTagText")
            val border by animateColorAsState(if (isSelected) accent else Palette.Stroke, label = "termTagBorder")
            val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
            Text(
                "[ ${option.title.uppercase()} ]",
                color = color,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .background(Palette.Surface)
                    .border(1.dp, border, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        view.tick(haptics)
                        onSelect(option)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

// MARK: - Single video

@Composable
fun TerminalVideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val qualities = info.qualityOptions
    val hasVideo = qualities.isNotEmpty()
    var mode by remember(info) { mutableStateOf(if (hasVideo) MediaMode.VIDEO else MediaMode.AUDIO) }
    var quality by remember(info) { mutableStateOf(qualities.firstOrNull()) }
    var container by remember(info) { mutableStateOf(settings.container) }
    var audioFormat by remember(info) { mutableStateOf(settings.audioFormat) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .termPanel()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TermThumbnail(info.thumbnail)
            Column(
                Modifier
                    .weight(1f)
                    .staggeredEntrance(0, key = info.id),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    info.title,
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(info.uploader, info.duration?.let { Fmt.duration(it) }).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (hasVideo) {
            TermSegmented(mode, { mode = it }, Modifier.staggeredEntrance(1, key = info.id))
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(tween(150)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(120)))
            },
            label = "termMode",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current == MediaMode.VIDEO) {
                    TermList(
                        options = qualities,
                        selected = quality,
                        key = info.id to "video",
                        title = { qualityTitle(it) },
                        trailing = { option -> option.estimatedSize?.let { "~" + Fmt.bytes(it) } },
                        isMax = { it == 0 && qualities.size > 1 },
                        onSelect = { quality = it },
                    )
                    TermLabel("> FORMAT")
                    TermContainers(container) { container = it }
                } else {
                    TermLabel("> FORMAT AUDIO" + (info.audioEstimatedSize?.let { " · ~" + Fmt.bytes(it) } ?: ""))
                    TermList(
                        options = AudioFormat.entries,
                        selected = audioFormat,
                        key = info.id to "audio",
                        title = { it.title.uppercase() },
                        trailing = { it.badge.uppercase() },
                        isMax = { false },
                        onSelect = { audioFormat = it },
                    )
                }
            }
        }
        TermButton(
            "TÉLÉCHARGER ↓",
            Modifier.fillMaxWidth(),
            big = true,
            subtitle = if (mode == MediaMode.VIDEO) {
                "${quality?.label ?: "Max"} · ${container.title}".uppercase()
            } else {
                "AUDIO · ${audioFormat.title.uppercase()}"
            },
        ) {
            gate { vm.downloadVideo(info, sourceUrl, mode, quality, container, audioFormat) }
        }
    }
}

// MARK: - Recent download line

/** Discrete accent blocks; negative [progress] = a short run of blocks sweeping across. */
@Composable
private fun BlockProgress(progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val stroke = Palette.Stroke
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "termBlocks")
    val transition = rememberInfiniteTransition(label = "termBlockSweep")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "termBlockPhase")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val border = 1.dp.toPx()
        drawRect(stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(border))
        val inset = 2.dp.toPx()
        val block = 6.dp.toPx()
        val gap = 2.dp.toPx()
        val inner = size.width - inset * 2
        val count = floor((inner + gap) / (block + gap)).toInt().coerceAtLeast(1)
        val h = size.height - inset * 2
        val run = 5
        val head = (phase * (count + run)).toInt()
        for (i in 0 until count) {
            val on = if (indeterminate) i in (head - run) until head else i < (animated * count).toInt()
            if (on) drawRect(accent, topLeft = Offset(inset + i * (block + gap), inset), size = Size(block, h))
        }
    }
}

/** "[file] title" + status, then a block progress bar while active; tap opens Fichiers. */
@Composable
fun TerminalRecentDownload(vm: MainViewModel) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val job = jobs.firstOrNull { it.isActive } ?: jobs.firstOrNull()
    // Keep the last job while the line collapses (written after composition).
    val last = remember { mutableStateOf<DownloadJob?>(null) }
    SideEffect { if (job != null) last.value = job }
    val current = job ?: last.value
    AnimatedVisibility(
        visible = job != null,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        if (current == null) return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    view.tick(haptics)
                    vm.selectTab(Tab.DOWNLOADS)
                }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "[file] " + current.title,
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                val (status, color) = when (current.state) {
                    JobState.DONE -> "terminé" to Palette.Success
                    JobState.FAILED -> "échec" to Palette.Danger
                    JobState.CANCELED -> "annulé" to Palette.TextTertiary
                    JobState.QUEUED -> "en attente" to accent
                    else -> "en cours" to accent
                }
                Text(status, color = color, fontSize = 12.5.sp, maxLines = 1)
            }
            if (current.isActive) {
                BlockProgress(if (current.state == JobState.QUEUED) 0f else current.progress)
            }
        }
    }
}
