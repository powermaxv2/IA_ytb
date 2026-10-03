package com.powermaxv2.ytdownloader.ui.skin.classic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.ChoiceChips
import com.powermaxv2.ytdownloader.ui.LocalDownloadGate
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.RemoteImage
import com.powermaxv2.ytdownloader.ui.SectionTitle
import com.powermaxv2.ytdownloader.ui.SegmentedControl
import com.powermaxv2.ytdownloader.ui.SelectableTile
import com.powermaxv2.ytdownloader.ui.TileGrid
import com.powermaxv2.ytdownloader.ui.modeIcon
import com.powermaxv2.ytdownloader.ui.resultCard
import com.powermaxv2.ytdownloader.ui.staggeredEntrance

/** Thumbnail with the platform logo and the duration. */
@Composable
private fun HeroThumbnail(url: String?, duration: Double?, platform: Platform) {
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
                .fillMaxSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(6.dp),
        ) {
            PlatformGlyph(platform, Modifier.size(20.dp))
        }
        duration?.let {
            Text(
                Fmt.duration(it),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// MARK: - Single video (YouTube video, Instagram reel…)

@Composable
fun ClassicVideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    val gate = LocalDownloadGate.current
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val qualities = info.qualityOptions
    val hasVideo = qualities.isNotEmpty()
    var mode by remember(info) { mutableStateOf(if (hasVideo) MediaMode.VIDEO else MediaMode.AUDIO) }
    var quality by remember(info) { mutableStateOf(qualities.firstOrNull()) }
    var container by remember(info) { mutableStateOf(settings.container) }
    var audioFormat by remember(info) { mutableStateOf(settings.audioFormat) }
    val platform = Links.platform(sourceUrl)

    Column(Modifier.resultCard(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroThumbnail(info.thumbnail, info.duration, platform)
        Column(Modifier.staggeredEntrance(0, key = info.id), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                info.title,
                color = Palette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(info.uploader, info.viewCount?.let { Fmt.views(it) }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(180)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(140)))
            },
            label = "mode",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current == MediaMode.VIDEO) {
                    SectionTitle("Qualité")
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
                    SectionTitle("Format", Modifier.padding(top = 2.dp))
                    ChoiceChips(VideoContainer.entries, container, { it.title }, { container = it })
                    AnimatedContent(targetState = container, label = "containerInfo") {
                        Text(it.subtitle, color = Palette.TextTertiary, fontSize = 12.5.sp)
                    }
                } else {
                    SectionTitle("Format audio")
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
        PrimaryButton(
            "Télécharger",
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            icon = Icons.Rounded.Download,
            subtitle = if (mode == MediaMode.VIDEO) "${quality?.label ?: "Max"} · ${container.title}" else "Audio · ${audioFormat.title}",
        ) {
            gate { vm.downloadVideo(info, sourceUrl, mode, quality, container, audioFormat) }
        }
    }
}
