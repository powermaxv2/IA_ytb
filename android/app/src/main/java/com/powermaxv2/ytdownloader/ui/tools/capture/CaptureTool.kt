package com.powermaxv2.ytdownloader.ui.tools.capture

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.capture.CaptureController
import com.powermaxv2.ytdownloader.core.capture.CaptureFormat
import com.powermaxv2.ytdownloader.core.capture.CaptureMode
import com.powermaxv2.ytdownloader.core.capture.CapturePhase
import com.powermaxv2.ytdownloader.core.capture.CapturePlan
import com.powermaxv2.ytdownloader.core.capture.CaptureSource
import com.powermaxv2.ytdownloader.core.capture.CaptureUiState
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
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import java.util.Locale

const val CAPTURE_TITLE = "Capture d'image"
const val CAPTURE_COMMAND = "grab"
const val CAPTURE_SUBTITLE = "Choisis l'instant parfait d'une vidéo, image par image, et garde-le en photo nette."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class CaptureActions(
    val onChoose: () -> Unit = {},
    val onScrub: (Long) -> Unit = {},
    val onSettle: () -> Unit = {},
    val onStep: (Long) -> Unit = {},
    val onJump: (Long) -> Unit = {},
    val onHunt: () -> Unit = {},
    val onFormat: (CaptureFormat) -> Unit = {},
    val onMode: (CaptureMode) -> Unit = {},
    val onCapture: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/** The Capture d'image (ToolRegistry.FRAME_GRAB). Its state lives in [CaptureController.shared]. */
@Composable
fun CaptureTool(vm: MainViewModel) {
    val controller = remember { CaptureController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(uri)) vm.toast("Une capture est en cours : attendez-la ou annulez-la.", success = false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une capture est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.capture() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la photo.", success = false)
    }
    val actions = remember(controller) {
        CaptureActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("video/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onScrub = controller::scrub,
            onSettle = controller::settle,
            onStep = controller::step,
            onJump = controller::jump,
            onHunt = controller::hunt,
            onFormat = controller::setFormat,
            onMode = controller::setMode,
            onCapture = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.capture()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onOpen = { openSavedFile(context, it, vm) },
            onShare = { shareSavedFiles(context, it, vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is CapturePhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = CAPTURE_TITLE, command = CAPTURE_COMMAND, subtitle = CAPTURE_SUBTITLE, scrollState = scroll) {
        CaptureContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.CaptureContent(state: CaptureUiState, actions: CaptureActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "capturePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                CapturePhase.Idle -> IdleStep(actions)
                CapturePhase.Reading -> ReadingStep()
                is CapturePhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre vidéo", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                CapturePhase.Ready -> current.source?.let { EditStep(current, it, actions) }
                is CapturePhase.Working -> WorkingStep(current, phase, actions)
                is CapturePhase.Done -> DoneStep(phase, actions)
                is CapturePhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La capture n'a pas abouti")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onCapture)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Autre vidéo", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: CaptureActions) {
    FileDropZone(
        title = "Choisir une vidéo",
        hint = "MP4, MOV, MKV, WebM, 3GP…",
        icon = Icons.Rounded.CameraRoll,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Comment ça marche")
        Spacer(Modifier.height(8.dp))
        HowRow(Icons.Rounded.CameraRoll, "Glisse sur la pellicule", "puis image par image", 0)
        HowRow(Icons.Rounded.AutoAwesome, "Image la plus nette", "Gulp choisit pour toi", 1)
        HowRow(Icons.Rounded.PhotoCamera, "Capture", "pleine qualité, JPG ou PNG", 2)
        HowRow(Icons.Rounded.BurstMode, "Rafale", "${CapturePlan.BURST_COUNT} photos autour", 3)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. La vidéo n'est jamais modifiée : les photos vont dans Images › Gulp.",
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
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
    }
}

@Composable
private fun ReadingStep() {
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
        Text("Lecture de la vidéo…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: CaptureUiState, source: CaptureSource, actions: CaptureActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Viewfinder(state, source)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = meta(source)
                Text(if (terminal) "# $meta" else meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (terminal) "[CHANGER]" else "Changer",
                color = LocalAccent.current.start,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .bounceClick(pressedScale = 0.9f, onClick = actions.onChoose)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlockLabel("Trouve l'instant")
        CaptureStrip(state, source, actions)
        StepRow(actions)
        SharpnessGauge(state)
        SideButton(
            if (state.hunting) "Recherche…" else "Image la plus nette autour",
            Icons.Rounded.AutoAwesome,
            Modifier.fillMaxWidth(),
            onClick = actions.onHunt,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Je veux")
        OptionChips(CaptureMode.entries.toList(), state.mode, { it.label }, actions.onMode)
        OptionChips(CaptureFormat.entries.toList(), state.format, { it.label }, actions.onFormat)
        Hint(hint(state, source))
    }
    MainButton(
        if (state.mode == CaptureMode.BURST) "Capturer la rafale" else "Capturer cette image",
        subtitle = "${CapturePlan.timecode(state.positionMs)} · ${source.width}×${source.height} · ${state.format.label}",
        icon = Icons.Rounded.PhotoCamera,
        enabled = !state.hunting,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onCapture,
    )
}

private fun hint(state: CaptureUiState, source: CaptureSource): String {
    val what = when (state.format) {
        CaptureFormat.JPG -> "JPG qualité ${CapturePlan.JPG_QUALITY} : net et léger, idéal à envoyer."
        CaptureFormat.PNG -> "PNG : aucune perte, plus lourd, idéal à retoucher."
    }
    return when (state.mode) {
        CaptureMode.SINGLE -> "La photo garde la taille de la vidéo (${source.width}×${source.height}). $what"
        CaptureMode.BURST -> "${CapturePlan.BURST_COUNT} photos, ${CapturePlan.BURST_SPACING} images d'écart, centrées sur l'instant choisi : garde la meilleure. $what"
    }
}

private fun meta(source: CaptureSource): String = buildList {
    add(CapturePlan.timecode(source.durationMs).substringBeforeLast('.'))
    add("${minOf(source.width, source.height)}p")
    add(String.format(Locale.FRANCE, "%.0f im/s", source.fps))
    source.file.size?.let { add(bytes(it)) }
}.joinToString(" · ")

@Composable
private fun WorkingStep(state: CaptureUiState, phase: CapturePhase.Working, actions: CaptureActions) {
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
        Text(
            "${state.source?.file?.name.orEmpty()} · ${CapturePlan.timecode(state.positionMs)}",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(phase: CapturePhase.Done, actions: CaptureActions) {
    val count = phase.saved.size
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader(if (count == 1) "Photo enregistrée" else "$count photos enregistrées", "Dans ${phase.saved.firstOrNull()?.location ?: "Pictures/Gulp"}")
        Spacer(Modifier.height(18.dp))
        ShotRow(phase.shots)
        Spacer(Modifier.height(14.dp))
        phase.saved.take(1).forEach { file ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KindTile(Icons.Rounded.Image, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val total = phase.saved.sumOf { it.size }
                    Text(
                        "${phase.format.label} · ${bytes(total)}" + if (count > 1) " · $count fichiers" else "",
                        color = Palette.TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    MainButton("Ouvrir", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { phase.saved.firstOrNull()?.let(actions.onOpen) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(phase.saved) }
        SideButton("Autre vidéo", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
    }
    SideButton("Capturer un autre instant", Icons.Rounded.PhotoCamera, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onBackToEdit)
}
