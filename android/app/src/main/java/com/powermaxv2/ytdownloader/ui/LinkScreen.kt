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
        Header(pane.platform, onSettings = vm::openSettings)
        UpdateBanner(vm)
        AnimatedVisibility(
            visible = integrity != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            NoticeBanner(Icons.Rounded.Shield, integrity ?: "", Palette.Danger, onDismiss = vm.engine::dismissIntegrityAlert)
        }
        EngineBanner(engineState)
        LinkCard(vm, pane, enabled = engineState !is EngineState.Failed)
        AnimatedContent(
            targetState = pane.phase,
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 320f)) { it / 10 }) togetherWith
                    fadeOut(tween(150)) using SizeTransform(clip = false)
            },
            label = "phase",
        ) { phase ->
            when (phase) {
                Phase.Idle -> GuideCard(pane.platform)
                is Phase.Loading -> LoadingCard(phase.platform)
                is Phase.Failed -> ErrorCard(phase.message, onRetry = { vm.analyze(pane) })
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

private fun title(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Instagram"
    Platform.OTHER -> "Autres sites"
    else -> "YouTube"
}

private fun subtitle(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Reels, photos et carrousels publics"
    Platform.OTHER -> "X, TikTok, Facebook, Snapchat et liens vidéo"
    else -> "Vidéos jusqu'en 8K, musique, playlists"
}

@Composable
private fun Header(platform: Platform, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .panel(RoundedCornerShape(17.dp), Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (platform == Platform.OTHER) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .gradientTint(LocalAccent.current.brush),
                )
            } else {
                PlatformGlyph(platform, Modifier.size(29.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        ScreenTitle(title(platform), subtitle(platform), Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = onSettings)
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

// MARK: - Link field

/**
 * Link field and its main action: "Coller le lien" while the field is empty (paste + analysis in one tap),
 * "Analyser" once something is typed, with a paste button next to it.
 */
@Composable
private fun LinkCard(vm: MainViewModel, pane: LinkPane, enabled: Boolean) {
    val accent = LocalAccent.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val detected = remember(pane.urlText) { Links.platformOfText(pane.urlText) }
    val platform = if (pane.urlText.isBlank()) pane.platform else detected
    val outline by animateColorAsState(if (focused) accent.start.copy(alpha = 0.8f) else Palette.Stroke, label = "fieldOutline")
    val loading = pane.phase is Phase.Loading
    val fieldShape = RoundedCornerShape(18.dp)
    val paste = {
        focus.clearFocus()
        vm.paste(pane, clipboard.getText()?.text)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .panel()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(fieldShape)
                .background(Palette.Surface2)
                .border(1.dp, outline, fieldShape)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedPlatformGlyph(platform, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = pane.urlText,
                onValueChange = { pane.urlText = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(accent.start),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    vm.analyze(pane)
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (pane.urlText.isEmpty()) {
                            Text(
                                when (pane.platform) {
                                    Platform.INSTAGRAM -> "Collez un lien Instagram…"
                                    Platform.OTHER -> "Collez un lien vidéo…"
                                    else -> "Collez un lien YouTube…"
                                },
                                color = Palette.TextTertiary,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = pane.urlText.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconCircleButton(Icons.Rounded.Close, Modifier.padding(start = 6.dp), size = 32.dp, contentDescription = "Effacer") {
                    vm.reset(pane)
                }
            }
        }
        AnimatedContent(
            targetState = pane.urlText.isBlank(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "linkAction",
        ) { empty ->
            if (empty) {
                PrimaryButton("Coller le lien", Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentPaste, enabled = enabled, onClick = paste)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconCircleButton(
                        Icons.Rounded.ContentPaste,
                        size = 56.dp,
                        shape = RoundedCornerShape(18.dp),
                        contentDescription = "Coller",
                        onClick = paste,
                    )
                    PrimaryButton(
                        "Analyser",
                        Modifier.weight(1f),
                        icon = Icons.Rounded.Search,
                        enabled = enabled,
                        loading = loading,
                    ) {
                        focus.clearFocus()
                        vm.analyze(pane)
                    }
                }
            }
        }
    }
}

// MARK: - Empty state: how to

private fun bold(vararg parts: Pair<String, Boolean>): AnnotatedString = buildAnnotatedString {
    parts.forEach { (text, strong) ->
        if (strong) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } else append(text)
    }
}

private fun steps(platform: Platform): List<AnnotatedString> {
    val first = when (platform) {
        Platform.INSTAGRAM -> bold("Sur le reel ou la publication, touchez " to false, "Partager" to true, " puis " to false, "Copier le lien" to true, "." to false)
        Platform.OTHER -> bold("Copiez le lien de la vidéo : X, TikTok, Facebook, Snapchat ou un autre site." to false)
        else -> bold("Dans YouTube, touchez " to false, "Partager" to true, " puis " to false, "Copier le lien" to true, "." to false)
    }
    val last = if (platform == Platform.INSTAGRAM) {
        bold("Choisissez ce que vous gardez, puis " to false, "Télécharger" to true, "." to false)
    } else {
        bold("Choisissez la qualité, puis " to false, "Télécharger" to true, "." to false)
    }
    return listOf(first, bold("Revenez ici et touchez " to false, "Coller le lien" to true, "." to false), last)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideCard(platform: Platform) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .panel()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Comment faire", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            steps(platform).forEachIndexed { index, step ->
                StepRow(index + 1, step, Modifier.staggeredEntrance(index, baseDelay = 80))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Stroke),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(18.dp))
                Text(
                    "Plus rapide : dans l'app, Partager → YT Downloader ouvre le lien ici directement.",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            features(platform).forEachIndexed { index, chip ->
                FeatureChip(
                    chip.text,
                    Modifier.staggeredEntrance(index, baseDelay = 200),
                    icon = chip.icon,
                    platform = chip.platform,
                    dimmed = chip.dimmed,
                )
            }
        }
        val footnote = when (platform) {
            Platform.INSTAGRAM ->
                "Publications publiques uniquement : l'app ne se connecte à aucun compte Instagram, pour protéger le vôtre. " +
                    "Pas de stories ni de comptes privés."
            Platform.OTHER ->
                "Vidéos publiques uniquement : pas de vidéos d'amis ou de groupes privés sur Facebook, pas de photos " +
                    "sur X ni de diaporamas TikTok. Snapchat : liens Spotlight. BeReal : aucun lien public, rien à télécharger."
            else -> null
        }
        footnote?.let {
            Text(it, color = Palette.TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

private data class Feature(
    val text: String,
    val icon: ImageVector? = null,
    val platform: Platform? = null,
    val dimmed: Boolean = false,
)

private fun features(platform: Platform): List<Feature> = when (platform) {
    Platform.INSTAGRAM -> listOf(
        Feature("Reels", Icons.Rounded.PlayArrow),
        Feature("Carrousels", Icons.Rounded.ViewCarousel),
        Feature("Photos", Icons.Rounded.PhotoLibrary),
        Feature("Sans compte", Icons.Rounded.Shield),
    )
    Platform.OTHER -> listOf(
        Feature("X", platform = Platform.TWITTER),
        Feature("TikTok", platform = Platform.TIKTOK),
        Feature("Facebook", platform = Platform.FACEBOOK),
        Feature("Snapchat", platform = Platform.SNAPCHAT),
        Feature("BeReal", platform = Platform.BEREAL, dimmed = true),
        Feature("Tout lien vidéo", Icons.Rounded.Public),
    )
    else -> listOf(
        Feature("Jusqu'en 8K", Icons.Rounded.HighQuality),
        Feature("MP3 · M4A · FLAC", Icons.Rounded.MusicNote),
        Feature("Playlists", Icons.Rounded.VideoLibrary),
        Feature("Sous-titres", Icons.Rounded.Subtitles),
    )
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
        Text(message, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        TonalButton("Réessayer", Icons.Rounded.Refresh, onClick = onRetry)
    }
}
