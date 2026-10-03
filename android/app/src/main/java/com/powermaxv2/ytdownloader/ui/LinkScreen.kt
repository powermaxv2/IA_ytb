package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.MediaInfo
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.skin.SkinGuide
import com.powermaxv2.ytdownloader.ui.skin.SkinHeader
import com.powermaxv2.ytdownloader.ui.skin.SkinLinkCard
import com.powermaxv2.ytdownloader.ui.skin.SkinRecentDownload
import com.powermaxv2.ytdownloader.ui.skin.SkinVideoCard

/** Content of the YouTube, Instagram and Autres tabs: each has its own link field and result. */
@Composable
fun LinkScreen(vm: MainViewModel, pane: LinkPane, contentPadding: PaddingValues) {
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
        SkinHeader(pane.platform, engineState, onSettings = vm::openSettings)
        UpdateBanner(vm)
        AnimatedVisibility(
            visible = integrity != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            NoticeBanner(Icons.Rounded.Shield, integrity ?: "", Palette.Danger, onDismiss = vm.engine::dismissIntegrityAlert)
        }
        EngineBanner(engineState)
        SkinLinkCard(vm, pane, enabled = engineState !is EngineState.Failed)
        AnimatedContent(
            targetState = pane.phase,
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 320f)) { it / 10 }) togetherWith
                    fadeOut(tween(150)) using SizeTransform(clip = false)
            },
            label = "phase",
        ) { phase ->
            when (phase) {
                Phase.Idle -> SkinGuide(pane.platform)
                is Phase.Loading -> LoadingCard(phase.platform)
                is Phase.Failed -> ErrorCard(phase.message, onRetry = { vm.analyze(pane) })
                is Phase.Ready -> when (val media = phase.media) {
                    is MediaInfo.Video -> SkinVideoCard(media.info, media.sourceUrl, vm)
                    is MediaInfo.Playlist -> PlaylistCard(media.info, vm)
                    is MediaInfo.Photos -> PhotosCard(media.post, vm)
                }
            }
        }
        SkinRecentDownload(vm)
    }
}

// MARK: - Engine

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
                    .panel(RoundedCornerShape(18.dp))
                    .shimmer()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = LocalAccent.current.start, strokeWidth = 2.dp)
                Text("Préparation du moteur… (quelques secondes au premier lancement)", color = Palette.TextSecondary, fontSize = 13.sp)
            }
            is EngineState.Failed -> NoticeBanner(
                icon = Icons.Rounded.ErrorOutline,
                text = "Le moteur n'a pas pu démarrer : ${current.message}",
                color = Palette.Danger,
            )
            EngineState.Ready -> Spacer(Modifier.height(0.dp))
        }
    }
}

// MARK: - Loading and errors

@Composable
private fun LoadingCard(platform: Platform) {
    Column(
        Modifier
            .fillMaxWidth()
            .panel()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Surface2)
                .shimmer(),
            contentAlignment = Alignment.Center,
        ) {
            PlatformGlyph(platform, Modifier.size(44.dp).pulsing())
        }
        listOf(0.9f, 0.55f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Palette.Surface2)
                    .shimmer(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), color = LocalAccent.current.start, strokeWidth = 2.dp)
            Text(
                if (platform == Platform.OTHER) "Analyse du lien…" else "Analyse du lien ${platform.label} et des qualités…",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .panel()
            .background(Palette.Danger.copy(alpha = 0.07f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Palette.Danger.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(28.dp))
        }
        Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        TonalButton("Réessayer", Icons.Rounded.Refresh, onClick = onRetry)
    }
}
