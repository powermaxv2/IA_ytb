package com.powermaxv2.ytdownloader.ui.tools.gif

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.gif.GifController
import com.powermaxv2.ytdownloader.core.gif.GifEffect
import com.powermaxv2.ytdownloader.core.gif.GifPace
import com.powermaxv2.ytdownloader.core.gif.GifPhase
import com.powermaxv2.ytdownloader.core.gif.GifPlan
import com.powermaxv2.ytdownloader.core.gif.GifSettings
import com.powermaxv2.ytdownloader.core.gif.GifShape
import com.powermaxv2.ytdownloader.core.gif.GifSize
import com.powermaxv2.ytdownloader.core.gif.GifSmooth
import com.powermaxv2.ytdownloader.core.gif.GifSource
import com.powermaxv2.ytdownloader.core.gif.GifSpeed
import com.powermaxv2.ytdownloader.core.gif.GifUiState
import com.powermaxv2.ytdownloader.core.gif.GifVideo
import com.powermaxv2.ytdownloader.core.trim.TrimRange
import com.powermaxv2.ytdownloader.core.trim.TrimSource
import com.powermaxv2.ytdownloader.core.trim.TrimStrip
import com.powermaxv2.ytdownloader.core.trim.TrimUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import com.powermaxv2.ytdownloader.ui.tools.trim.TimeReadout
import com.powermaxv2.ytdownloader.ui.tools.trim.TrimActions
import com.powermaxv2.ytdownloader.ui.tools.trim.TrimTimeline

const val GIF_TITLE = "Créateur de GIF"
const val GIF_COMMAND = "gif"
const val GIF_SUBTITLE = "Un bout de vidéo ou quelques photos en GIF léger : boomerang, ralenti, légende, à partager partout."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class GifActions(
    val onSource: (GifSource) -> Unit = {},
    val onChooseVideo: () -> Unit = {},
    val onAddPhotos: () -> Unit = {},
    val onRemovePhoto: (Long) -> Unit = {},
    val onMovePhoto: (Long, Int) -> Unit = { _, _ -> },
    val onDismissNotice: () -> Unit = {},
    val onStart: (Long) -> Unit = {},
    val onEnd: (Long) -> Unit = {},
    val onSettle: () -> Unit = {},
    val onSettings: (GifSettings) -> Unit = {},
    val onMake: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/** The Créateur de GIF (ToolRegistry.GIF_MAKER). Its state lives in [GifController.shared]: a GIF keeps being made off screen. */
@Composable
fun GifTool(vm: MainViewModel) {
    val controller = remember { GifController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val video = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pickVideo(uri)) vm.toast("Un GIF est en cours : attendez-le ou annulez-le.", success = false)
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.addPhotos(uris)) vm.toast("Un GIF est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le GIF.", success = false)
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    val actions = remember(controller) {
        GifActions(
            onSource = controller::setSource,
            onChooseVideo = { launch { video.launch(arrayOf("video/*")) } },
            onAddPhotos = { launch { photos.launch(arrayOf("image/*")) } },
            onRemovePhoto = controller::removePhoto,
            onMovePhoto = controller::movePhoto,
            onDismissNotice = controller::dismissNotice,
            onStart = controller::setStart,
            onEnd = controller::setEnd,
            onSettle = controller::settle,
            onSettings = controller::setSettings,
            onMake = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onOpen = { openSavedFile(context, it, vm) },
            onShare = { shareSavedFiles(context, listOf(it), vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is GifPhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = GIF_TITLE, command = GIF_COMMAND, subtitle = GIF_SUBTITLE, scrollState = scroll) {
        GifContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests; [still] freezes the preview). */
@Composable
fun ColumnScope.GifContent(state: GifUiState, actions: GifActions, still: Boolean = false) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class to it.source },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "gifPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val editable = current.phase == GifPhase.Idle || current.phase == GifPhase.Ready || current.phase is GifPhase.Unsupported
            if (editable) SourceSwitch(current.source, actions.onSource, Modifier.staggeredEntrance(0))
            when (val phase = current.phase) {
                GifPhase.Idle -> IdleStep(current.source, actions)
                GifPhase.Reading -> ReadingStep(current.source)
                is GifPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton(
                        if (current.source == GifSource.VIDEO) "Choisir une autre vidéo" else "Choisir d'autres photos",
                        Icons.Rounded.UploadFile,
                        Modifier.fillMaxWidth(),
                        onClick = if (current.source == GifSource.VIDEO) actions.onChooseVideo else actions.onAddPhotos,
                    )
                }
                GifPhase.Ready -> EditStep(current, actions, still)
                is GifPhase.Working -> WorkingStep(current, phase, actions, still)
                is GifPhase.Done -> DoneStep(current, phase, actions, still)
                is GifPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Le GIF n'a pas pu être créé")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onMake)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Recommencer", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(source: GifSource, actions: GifActions) {
    when (source) {
        GifSource.VIDEO -> FileDropZone(
            title = "Choisir une vidéo",
            hint = "MP4, MOV, MKV, WebM… on garde jusqu'à 15 s",
            icon = Icons.Rounded.VideoLibrary,
            onClick = actions.onChooseVideo,
        )
        GifSource.PHOTOS -> FileDropZone(
            title = "Choisir des photos",
            hint = "De ${GifPlan.MIN_PHOTOS} à ${GifPlan.MAX_PHOTOS} photos, qui défilent en boucle",
            icon = Icons.Rounded.AddPhotoAlternate,
            onClick = actions.onAddPhotos,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ce que tu peux régler")
        Spacer(Modifier.height(8.dp))
        FeatureRow(Icons.Rounded.AllInclusive, "Boomerang ou à l'envers", "aller-retour", 0)
        FeatureRow(Icons.Rounded.Speed, "Ralenti ou accéléré", "×0,5 à ×2", 1)
        FeatureRow(Icons.Rounded.Title, "Légende", "texte style mème", 2)
        FeatureRow(Icons.Rounded.Gif, "Poids estimé", "petit, moyen, grand", 3)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Une seule palette de couleurs pour tout le GIF : plus léger et sans scintillement.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(icon, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
    }
}

@Composable
private fun ReadingStep(source: GifSource) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(16.dp),
    ) {
        Text(if (source == GifSource.VIDEO) "Lecture de la vidéo…" else "Lecture des photos…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: GifUiState, actions: GifActions, still: Boolean) {
    val settings = state.settings
    fun change(block: GifSettings.() -> GifSettings) = actions.onSettings(settings.block())
    state.notice?.let { notice ->
        Hint(notice, warning = true, modifier = Modifier.fillMaxWidth().bounceClick(pressedScale = 0.98f, onClick = actions.onDismissNotice))
    }
    // The preview, with the video's name or the photos under it.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
    ) {
        val frames = if (state.source == GifSource.VIDEO) state.preview.ifEmpty { state.strip } else state.photos.mapNotNull { it.thumb }
        GifPreview(state, frames, still)
        Spacer(Modifier.height(12.dp))
        when (state.source) {
            GifSource.VIDEO -> state.video?.let { VideoLine(it, actions) }
            GifSource.PHOTOS -> PhotoStrip(state.photos, actions.onMovePhoto, actions.onRemovePhoto, actions.onAddPhotos)
        }
    }
    if (state.source == GifSource.VIDEO) {
        state.video?.let { video ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(2, baseDelay = 60)
                    .toolCard()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BlockLabel("Le moment à garder · ${GifPlan.shortDuration(state.maxMs)} au plus")
                // The Découpeur's timeline and read-out, fed with this tool's passage.
                val trimState = TrimUiState(range = TrimRange(state.range.startMs, state.range.endMs), strip = TrimStrip(frames = state.strip))
                val trimSource = TrimSource(video.file, video.durationMs, true, video.width, video.height)
                val trimActions = TrimActions(onStart = actions.onStart, onEnd = actions.onEnd, onSettle = actions.onSettle)
                TrimTimeline(trimState, trimSource, trimActions)
                TimeReadout(trimState, trimActions)
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Effet")
        OptionChips(GifEffect.entries, settings.effect, { it.label }, { change { copy(effect = it) } })
        if (state.source == GifSource.VIDEO) {
            BlockLabel("Vitesse")
            OptionChips(GifSpeed.entries, settings.speed, { it.label }, { change { copy(speed = it) } })
        } else {
            BlockLabel("Rythme")
            OptionChips(GifPace.entries, settings.pace, { "${it.label} · ${GifPlan.shortDuration(it.frameMs)}" }, { change { copy(pace = it) } })
        }
        BlockLabel("Forme")
        OptionChips(GifShape.entries, settings.shape, { it.label }, { change { copy(shape = it) } })
        QrField(
            label = "Légende (facultatif)",
            value = settings.caption,
            onChange = { text -> change { copy(caption = text) } },
            placeholder = "Quand le café fait effet",
            maxLength = GifPlan.CAPTION_MAX,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(4, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Taille")
        OptionChips(GifSize.entries, settings.size, { "${it.label} · ${it.side} px" }, { change { copy(size = it) } })
        AnimatedVisibility(
            visible = state.source == GifSource.VIDEO,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BlockLabel("Fluidité")
                OptionChips(GifSmooth.entries, settings.smooth, { "${it.label} · ${it.fps} i/s" }, { change { copy(smooth = it) } })
            }
        }
        state.estimateBytes?.let { WeightGauge(it, state.frames, state.outputMs) }
    }
    val geometry = state.geometry
    MainButton(
        "Créer le GIF",
        subtitle = state.refusal ?: geometry?.let { "${it.width} × ${it.height} · ${GifPlan.shortDuration(state.outputMs)} · ${settings.effect.label.lowercase()}" },
        icon = Icons.Rounded.Gif,
        enabled = state.refusal == null,
        modifier = Modifier.staggeredEntrance(5, baseDelay = 120),
        onClick = actions.onMake,
    )
}

@Composable
private fun VideoLine(video: GifVideo, actions: GifActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(video.file.name, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(GifPlan.timecode(video.durationMs).substringBeforeLast('.'), "${minOf(video.width, video.height)}p", video.file.size?.let(::bytes)).joinToString(" · ")
            Text(if (terminal) "# $meta" else meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Text(
            if (terminal) "[CHANGER]" else "Changer",
            color = LocalAccent.current.start,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 8.dp)
                .bounceClick(pressedScale = 0.9f, onClick = actions.onChooseVideo)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun WorkingStep(state: GifUiState, phase: GifPhase.Working, actions: GifActions, still: Boolean) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) TerminalProgress(phase.progress) else ProgressRing(phase.progress, size = 176.dp)
        Spacer(Modifier.height(16.dp))
        Text(if (terminal) "> ${phase.step}" else phase.step, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        val geometry = state.geometry
        Text(
            listOfNotNull(geometry?.let { "${it.width} × ${it.height}" }, "${state.frames} images", state.settings.effect.label.lowercase()).joinToString(" · "),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
    Hint(
        "Tu peux revenir à l'accueil : le GIF continue de se créer tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: GifUiState, phase: GifPhase.Done, actions: GifActions, still: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("GIF prêt", "Enregistré dans ${phase.saved.location}")
        Spacer(Modifier.height(16.dp))
        val frames = if (state.source == GifSource.VIDEO) state.preview.ifEmpty { state.strip } else state.photos.mapNotNull { it.thumb }
        GifPreview(state, frames, still)
        Spacer(Modifier.height(14.dp))
        Text(phase.saved.name, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            "${phase.width} × ${phase.height} · ${phase.frames} images · ${GifPlan.shortDuration(phase.durationMs)} · ${bytes(phase.saved.size ?: 0L)}",
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    MainButton("Partager", icon = Icons.Rounded.Share, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onShare(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Ouvrir", Icons.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpen(phase.saved) }
        SideButton("Retoucher", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
    }
    SideButton("Nouveau GIF", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}
