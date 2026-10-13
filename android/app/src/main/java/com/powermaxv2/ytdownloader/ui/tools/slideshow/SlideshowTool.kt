package com.powermaxv2.ytdownloader.ui.tools.slideshow

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.slideshow.SlideFit
import com.powermaxv2.ytdownloader.core.slideshow.SlideFormat
import com.powermaxv2.ytdownloader.core.slideshow.SlideMusic
import com.powermaxv2.ytdownloader.core.slideshow.SlidePhase
import com.powermaxv2.ytdownloader.core.slideshow.SlidePhoto
import com.powermaxv2.ytdownloader.core.slideshow.SlidePlan
import com.powermaxv2.ytdownloader.core.slideshow.SlideTransition
import com.powermaxv2.ytdownloader.core.slideshow.SlideUiState
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
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val SLIDESHOW_TITLE = "Diaporama"
const val SLIDESHOW_COMMAND = "slideshow"
const val SLIDESHOW_SUBTITLE = "Quelques photos, une transition, ta musique : Gulp en fait une petite vidéo."

private val PHOTO_TYPES = arrayOf("image/*")
private val MUSIC_TYPES = arrayOf("audio/*", "video/*")

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class SlideActions(
    val onAddPhotos: () -> Unit = {},
    val onRemove: (Long) -> Unit = {},
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onCycleDuration: (Long) -> Unit = {},
    val onAllDurations: (Long) -> Unit = {},
    val onFormat: (SlideFormat) -> Unit = {},
    val onFit: (SlideFit) -> Unit = {},
    val onTransition: (SlideTransition) -> Unit = {},
    val onMusic: () -> Unit = {},
    val onRemoveMusic: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onMake: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/** The Diaporama (ToolRegistry.SLIDESHOW). Its state lives in [App.slideshow], so a video keeps being made off screen. */
@Composable
fun SlideshowTool(vm: MainViewModel) {
    val controller = remember { App.instance.slideshow }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.add(uris)) vm.toast("Une vidéo est en cours de montage.", success = false)
    }
    val music = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.pickMusic(uri)
    }
    // Android 9 and older: saving to Movies needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la vidéo.", success = false)
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    val actions = remember(controller) {
        SlideActions(
            onAddPhotos = { launch { photos.launch(PHOTO_TYPES) } },
            onRemove = controller::remove,
            onMove = controller::move,
            onCycleDuration = controller::cycleDuration,
            onAllDurations = controller::setAllDurations,
            onFormat = controller::setFormat,
            onFit = controller::setFit,
            onTransition = controller::setTransition,
            onMusic = { launch { music.launch(MUSIC_TYPES) } },
            onRemoveMusic = controller::removeMusic,
            onDismissNotice = controller::dismissNotice,
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
    LaunchedEffect(state.phase::class) { if (state.phase !is SlidePhase.Editing) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = SLIDESHOW_TITLE, command = SLIDESHOW_COMMAND, subtitle = SLIDESHOW_SUBTITLE, scrollState = scroll) {
        SlideshowContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests; [still] stops the preview). */
@Composable
fun ColumnScope.SlideshowContent(state: SlideUiState, actions: SlideActions, still: Boolean = false) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "slideshowPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                SlidePhase.Idle -> if (current.adding > 0) ReadingStep(current.adding) else IdleStep(current, actions)
                SlidePhase.Editing -> EditStep(current, actions, still)
                is SlidePhase.Working -> WorkingStep(current, phase, actions, still)
                is SlidePhase.Done -> DoneStep(current, phase, actions, still)
                is SlidePhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Le diaporama n'a pas abouti")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onMake)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Modifier", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Recommencer", Icons.Rounded.AddPhotoAlternate, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: SlideUiState, actions: SlideActions) {
    FileDropZone(
        title = "Choisir des photos",
        hint = "Jusqu'à ${SlidePlan.MAX_PHOTOS} : JPEG, PNG, HEIC, WebP…",
        icon = Icons.Rounded.Slideshow,
        onClick = actions.onAddPhotos,
    )
    state.notice?.let { Hint(it, warning = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Comment ça marche")
        Spacer(Modifier.height(8.dp))
        HowRow(Icons.Rounded.SwapVert, "Range tes photos", "l'ordre, et combien de temps chacune", 0)
        HowRow(Icons.Rounded.Slideshow, "Choisis l'effet", "fondu, glissé ou zoom Ken Burns", 1)
        HowRow(Icons.Rounded.MusicNote, "Ajoute ta musique", "elle s'arrête en douceur à la fin", 2)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. La vidéo (MP4) part dans la Galerie, album Gulp.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun HowRow(icon: ImageVector, title: String, detail: String, index: Int) {
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
        Column(Modifier.weight(1f)) {
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReadingStep(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(14.dp))
        Text("Lecture de $count photo${if (count > 1) "s" else ""}…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: SlideUiState, actions: SlideActions, still: Boolean) {
    val photos = state.photos
    // The show, playing as it will come out.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SlidePreview(photos, state.format, state.fit, state.transition, height = 280.dp, paused = still)
        Spacer(Modifier.height(8.dp))
        Text(
            summary(state),
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
            fontFamily = if (LocalSkin.current == Skin.TERMINAL) FontFamily.Monospace else null,
            textAlign = TextAlign.Center,
        )
    }
    state.notice?.let { Hint(it, warning = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Effet")
        OptionChips(SlideTransition.entries.toList(), state.transition, { it.label }, actions.onTransition)
        BlockLabel("Format")
        OptionChips(SlideFormat.entries.toList(), state.format, { "${it.label} · ${ratioOf(it)}" }, actions.onFormat)
        BlockLabel("Photos")
        OptionChips(SlideFit.entries.toList(), state.fit, { it.label }, actions.onFit)
        Text(state.fit.detail, color = Palette.TextSecondary, fontSize = 12.5.sp)
        BlockLabel("Chaque photo dure")
        OptionChips(SlidePlan.DURATIONS_MS, state.commonDurationMs ?: -1L, SlidePlan::seconds, actions.onAllDurations)
        if (state.commonDurationMs == null) {
            Text("Durées sur mesure : touche une durée dans la liste pour la changer.", color = Palette.TextSecondary, fontSize = 12.5.sp)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("${photos.size} photo${if (photos.size > 1) "s" else ""}, dans l'ordre")
        photos.forEachIndexed { i, photo -> PhotoRow(i, photos.size, photo, actions) }
        if (state.adding > 0) {
            Text("Lecture de ${state.adding} photo${if (state.adding > 1) "s" else ""}…", color = Palette.TextSecondary, fontSize = 13.sp, modifier = Modifier.shimmer())
        }
        if (photos.size < SlidePlan.MAX_PHOTOS) {
            SideButton("Ajouter des photos", Icons.Rounded.AddPhotoAlternate, Modifier.fillMaxWidth(), onClick = actions.onAddPhotos)
        }
    }
    MusicCard(state.music, actions)
    AnimatedVisibility(
        visible = state.refusal == null && state.adding == 0,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        MainButton(
            "Créer la vidéo",
            subtitle = "${clock(state.totalMs)} · ${state.format.width}×${state.format.height}" + if (state.music != null) " · avec musique" else " · sans son",
            icon = Icons.Rounded.Movie,
            modifier = Modifier.staggeredEntrance(4, baseDelay = 120),
            onClick = actions.onMake,
        )
    }
}

private fun ratioOf(format: SlideFormat): String = when (format) {
    SlideFormat.VERTICAL -> "9:16"
    SlideFormat.SQUARE -> "1:1"
    SlideFormat.LANDSCAPE -> "16:9"
}

private fun summary(state: SlideUiState): String {
    val n = state.photos.size
    return "${clock(state.totalMs)} · $n photo${if (n > 1) "s" else ""} · ${state.transition.label} · ${state.format.label}"
}

@Composable
private fun PhotoRow(index: Int, count: Int, photo: SlidePhoto, actions: SlideActions) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index.coerceAtMost(8), key = photo.id, baseDelay = 120),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SlideThumb(photo.thumb, size = 54.dp)
            Text(
                "${index + 1}",
                color = Palette.OnAccent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.start)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(photo.name, color = Palette.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // Tap to change: 1,5 s → 2 s → 3 s → 4 s → 5 s → 7 s.
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.start.copy(alpha = 0.14f))
                    .bounceClick(pressedScale = 0.9f) { actions.onCycleDuration(photo.id) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = accent.start, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (terminal) "[${SlidePlan.seconds(photo.durationMs)}]" else SlidePlan.seconds(photo.durationMs),
                    color = accent.start,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        RowIcon(Icons.Rounded.KeyboardArrowUp, "Plus tôt", enabled = index > 0) { actions.onMove(photo.id, -1) }
        RowIcon(Icons.Rounded.KeyboardArrowDown, "Plus tard", enabled = index < count - 1) { actions.onMove(photo.id, 1) }
        RowIcon(Icons.Rounded.Close, "Retirer", enabled = true) { actions.onRemove(photo.id) }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Palette.TextPrimary else Palette.TextTertiary.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MusicCard(music: SlideMusic?, actions: SlideActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Musique")
        if (music == null) {
            SideButton("Ajouter une musique", Icons.Rounded.MusicNote, Modifier.fillMaxWidth(), onClick = actions.onMusic)
            Text("Facultatif. Elle tourne en boucle si elle est plus courte, et s'éteint en fondu à la fin.", color = Palette.TextSecondary, fontSize = 12.5.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindTile(Icons.Rounded.MusicNote, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(music.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(music.durationMs?.let { clock(it) } ?: "Durée inconnue", color = Palette.TextSecondary, fontSize = 12.5.sp)
                }
                RowIcon(Icons.Rounded.Refresh, "Changer", enabled = true, onClick = actions.onMusic)
                RowIcon(Icons.Rounded.Close, "Retirer", enabled = true, onClick = actions.onRemoveMusic)
            }
        }
    }
}

@Composable
private fun WorkingStep(state: SlideUiState, phase: SlidePhase.Working, actions: SlideActions, still: Boolean) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) {
            TerminalProgress(phase.progress)
        } else {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(phase.progress, size = 200.dp)
                SlidePreview(state.photos, state.format, state.fit, state.transition, height = 120.dp, paused = still, modifier = Modifier.width(150.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (terminal) "> ${phase.step}" else phase.step, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(summary(state), color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    Hint(
        "Vous pouvez revenir à l'accueil : le montage continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: SlideUiState, phase: SlidePhase.Done, actions: SlideActions, still: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("Diaporama prêt", "${clock(phase.durationMs)} · dans ${phase.saved.location}")
        Spacer(Modifier.height(14.dp))
        SlidePreview(state.photos, state.format, state.fit, state.transition, height = 220.dp, paused = still)
    }
    MainButton(
        "Regarder",
        subtitle = phase.saved.name,
        icon = Icons.Rounded.PlayArrow,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
    ) { actions.onOpen(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(phase.saved) }
        SideButton("Modifier", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onBackToEdit)
    }
    SideButton("Nouveau diaporama", Icons.Rounded.AddPhotoAlternate, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}
