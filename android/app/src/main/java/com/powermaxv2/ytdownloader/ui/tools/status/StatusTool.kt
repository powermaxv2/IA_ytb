package com.powermaxv2.ytdownloader.ui.tools.status

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.status.StatusApp
import com.powermaxv2.ytdownloader.core.status.StatusFilter
import com.powermaxv2.ytdownloader.core.status.StatusItem
import com.powermaxv2.ytdownloader.core.status.StatusKind
import com.powermaxv2.ytdownloader.core.status.StatusPhase
import com.powermaxv2.ytdownloader.core.status.StatusUiState
import com.powermaxv2.ytdownloader.core.status.keptTitle
import com.powermaxv2.ytdownloader.core.status.keptWhere
import com.powermaxv2.ytdownloader.core.status.kindsLine
import com.powermaxv2.ytdownloader.core.status.statusCount
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val STATUS_TITLE = "Statuts"
const val STATUS_COMMAND = "status"
const val STATUS_SUBTITLE = "Garde les photos et vidéos des statuts WhatsApp avant qu'ils disparaissent."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class StatusActions(
    val onApp: (StatusApp) -> Unit = {},
    val onPickFolder: () -> Unit = {},
    val onChangeFolder: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onFilter: (StatusFilter) -> Unit = {},
    val onOpen: (StatusItem) -> Unit = {},
    val onKeep: (StatusItem) -> Unit = {},
    val onKeepAll: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpenSaved: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * Statuts (ToolRegistry.STATUSES): keeps the WhatsApp statuses the user has seen before they vanish. Its state
 * lives in [App.statuses]; the folder is read again each time the screen comes back to the front.
 */
@Composable
fun StatusTool(vm: MainViewModel) {
    val controller = remember { App.instance.statuses }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) controller.folderPicked(uri)
    }
    // Android 9 and older: saving to Pictures / Movies needs the storage permission.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les statuts.", success = false)
    }
    fun withStorage(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = action
            storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    LifecycleResumeEffect(Unit) {
        controller.refresh(quiet = true)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            vm.toast(it, success = false)
            controller.clearMessage()
        }
    }

    val actions = StatusActions(
        onApp = controller::setApp,
        onPickFolder = {
            try {
                folderPicker.launch(controller.initialUri())
            } catch (e: ActivityNotFoundException) {
                vm.toast("Ce téléphone n'a pas le sélecteur de dossiers d'Android.", success = false)
            }
        },
        onChangeFolder = controller::changeFolder,
        onRefresh = { controller.refresh(quiet = false) },
        onFilter = controller::setFilter,
        onOpen = { openStatus(context, it, vm) },
        onKeep = { item -> withStorage { controller.keepOne(item) } },
        onKeepAll = { withStorage { controller.keepAll() } },
        onCancel = controller::cancel,
        onBack = controller::backToGrid,
        onOpenSaved = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, it, vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is StatusPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = STATUS_TITLE, command = STATUS_COMMAND, subtitle = STATUS_SUBTITLE, scrollState = scroll) {
        StatusContent(state, actions)
    }
}

/** Opens a status in the phone's viewer (photo or video player), straight from WhatsApp's folder. */
private fun openStatus(context: Context, item: StatusItem, vm: MainViewModel) {
    val mime = if (item.kind == StatusKind.VIDEO) "video/*" else "image/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Voir le statut")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce statut", success = false) }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.StatusContent(state: StatusUiState, actions: StatusActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "statusPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                StatusPhase.NeedsFolder -> SetupStep(current, actions)
                StatusPhase.Loading -> LoadingStep()
                is StatusPhase.WrongFolder -> WrongFolderStep(phase, actions)
                StatusPhase.Ready -> if (current.items.isEmpty()) EmptyStep(current, actions) else GridStep(current, actions)
                is StatusPhase.Working -> WorkingStep(current, phase, actions)
                is StatusPhase.Done -> DoneStep(phase, actions)
                is StatusPhase.Failed -> FailedStep(phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun SetupStep(state: StatusUiState, actions: StatusActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactRings(Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        val title = "Les statuts de tes contacts"
        Text(
            if (terminal) title.uppercase() else title,
            color = Palette.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text("photos et vidéos, en qualité d'origine", color = Palette.TextSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Comment ça marche")
        Spacer(Modifier.height(6.dp))
        StepRow(0, Icons.Rounded.Visibility, "Regarde les statuts dans WhatsApp", "WhatsApp les garde sur le téléphone environ 24 h.")
        StepRow(1, Icons.Rounded.FolderOpen, "Autorise le dossier des statuts", "Une seule fois : Android ouvre le bon dossier, touche « Utiliser ce dossier » puis « Autoriser ».")
        StepRow(2, Icons.Rounded.Download, "Garde ceux que tu veux", "Un par un ou tous d'un coup, dans Galerie › Gulp.")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Application")
        Spacer(Modifier.height(10.dp))
        OptionChips(StatusApp.entries, state.app, { it.title }, actions.onApp)
    }
    MainButton(
        "Autoriser le dossier des statuts",
        icon = Icons.Rounded.FolderOpen,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onPickFolder,
    )
    Hint(
        "Hors ligne, rien n'est envoyé. Gulp ne lit que le dossier des statuts, jamais tes discussions, et ne le modifie pas.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun StepRow(index: Int, icon: ImageVector, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 220)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KindTile(icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${index + 1}. $title", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LoadingStep() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 9.dp
        val width = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .width(width)
                                .height(width * 14f / 9f)
                                .clip(RoundedCornerShape(thumbRadius()))
                                .background(Palette.TextPrimary.copy(alpha = 0.07f))
                                .shimmer(),
                        )
                    }
                }
            }
        }
    }
    Text(
        if (LocalSkin.current == Skin.TERMINAL) "> lecture des statuts…" else "Lecture des statuts…",
        color = Palette.TextSecondary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WrongFolderStep(phase: StatusPhase.WrongFolder, actions: StatusActions) {
    ToolErrorCard(phase.message, title = "Mauvais dossier")
    MainButton("Choisir le dossier des statuts", icon = Icons.Rounded.FolderOpen, onClick = actions.onPickFolder)
    SideButton("Retour", null, Modifier.fillMaxWidth(), onClick = actions.onChangeFolder)
}

@Composable
private fun HeaderCard(state: StatusUiState, actions: StatusActions, withFilters: Boolean) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = statusCount(state.items.size)
                Text(
                    if (terminal) title.uppercase() else title,
                    color = Palette.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                )
                Text(
                    "${kindsLine(state.photos, state.videos)} · ${state.app.title}",
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                )
            }
            Box(Modifier.size(44.dp)) {
                SpinOnTap(actions.onRefresh) { KindTile(Icons.Rounded.Refresh, size = 44.dp) }
            }
        }
        if (withFilters) {
            Spacer(Modifier.height(12.dp))
            OptionChips(
                StatusFilter.entries,
                state.filter,
                { f ->
                    val n = when (f) {
                        StatusFilter.ALL -> state.items.size
                        StatusFilter.PHOTOS -> state.photos
                        StatusFilter.VIDEOS -> state.videos
                    }
                    "${f.title} · $n"
                },
                actions.onFilter,
            )
        }
    }
}

/** Turns its content a full turn when tapped (the refresh button). */
@Composable
private fun SpinOnTap(onClick: () -> Unit, content: @Composable () -> Unit) {
    val turn = remember { Animatable(0f) }
    var spins by remember { mutableIntStateOf(0) }
    LaunchedEffect(spins) {
        if (spins > 0) {
            turn.snapTo(0f)
            turn.animateTo(360f, spring(dampingRatio = 0.7f, stiffness = 140f))
        }
    }
    Box(
        Modifier
            .graphicsLayer { rotationZ = turn.value }
            .bounceClick(pressedScale = 0.85f) {
                spins += 1
                onClick()
            },
    ) { content() }
}

@Composable
private fun GridStep(state: StatusUiState, actions: StatusActions) {
    HeaderCard(state, actions, withFilters = state.photos > 0 && state.videos > 0)
    val shown = state.shown
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 9.dp
        val width = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            shown.chunked(3).forEachIndexed { row, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { col, item ->
                        StatusTile(
                            item = item,
                            index = row * 3 + col,
                            now = state.now,
                            saved = item.key in state.saved,
                            saving = item.key in state.saving,
                            onOpen = { actions.onOpen(item) },
                            onKeep = { actions.onKeep(item) },
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
        }
    }
    val left = state.toKeep.size
    MainButton(
        if (left > 0) "Tout enregistrer · $left" else "Tout est gardé",
        icon = Icons.Rounded.Download,
        enabled = left > 0,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 200),
        onClick = actions.onKeepAll,
    )
    Hint(
        "Un statut disparaît environ 24 h après sa publication : le cercle montre le temps qu'il lui reste. Touche un statut pour le voir en grand.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Changer de dossier", Icons.Rounded.FolderOpen, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onChangeFolder)
}

@Composable
private fun EmptyStep(state: StatusUiState, actions: StatusActions) {
    HeaderCard(state, actions, withFilters = false)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactRings(Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("Aucun statut pour l'instant", color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Ouvre ${state.app.title}, regarde des statuts, puis reviens ici : ils apparaîtront tout seuls.",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
    MainButton("Actualiser", icon = Icons.Rounded.Refresh, onClick = actions.onRefresh)
    SideButton("Changer de dossier", Icons.Rounded.FolderOpen, Modifier.fillMaxWidth(), onClick = actions.onChangeFolder)
}

@Composable
private fun WorkingStep(state: StatusUiState, phase: StatusPhase.Working, actions: StatusActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "keepProgress",
    )
    val queue = state.shown.filter { it.key !in state.saved }
    val current = queue.firstOrNull() ?: state.shown.lastOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        current?.let { item ->
            Box(
                Modifier
                    .width(120.dp)
                    .popIn(0, item.key)
                    .clip(RoundedCornerShape(thumbRadius()))
                    .background(placeholder(phase.done)),
            ) {
                StatusPicture(item, Modifier.fillMaxWidth().height(186.dp))
                Box(Modifier.fillMaxWidth().height(186.dp).shimmer())
            }
        }
        Spacer(Modifier.height(20.dp))
        val step = if (phase.total > 1) "Copie ${(phase.done + 1).coerceAtMost(phase.total)}/${phase.total}…" else "Copie…"
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("vers Galerie › Gulp", color = Palette.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (terminal) TerminalProgress(progress) else ProgressBar(progress)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun ProgressBar(progress: Float) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(Palette.TextPrimary.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.horizontal() else Brush.horizontalGradient(listOf(accent.start, accent.start)))
                .shimmer(),
        )
    }
}

@Composable
private fun DoneStep(phase: StatusPhase.Done, actions: StatusActions) {
    val kept = phase.kept
    val saved = kept.map { it.saved }
    val photos = kept.count { it.item.kind == StatusKind.PHOTO }
    val videos = kept.size - photos
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        DoneHeader(keptTitle(kept.size), if (kept.size > 1) "enregistrés dans Galerie › Gulp" else "enregistré dans Galerie › Gulp")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val width = (maxWidth - gap * 3) / 4
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                kept.take(4).forEachIndexed { i, k ->
                    Box(
                        Modifier
                            .width(width)
                            .height(width * 14f / 9f)
                            .popIn(i, k.item.key)
                            .clip(RoundedCornerShape(thumbRadius()))
                            .background(placeholder(i)),
                    ) {
                        StatusPicture(k.item, Modifier.fillMaxSize())
                        KeptPill(Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Hint(keptWhere(photos, videos))
    }
    MainButton(
        if (kept.size > 1) "Partager les ${kept.size} statuts" else "Partager le statut",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 200),
    ) { actions.onShare(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Galerie", Icons.Rounded.Image, Modifier.weight(1f)) { saved.firstOrNull()?.let(actions.onOpenSaved) }
        SideButton("Statuts", StatusIcon, Modifier.weight(1f), onClick = actions.onBack)
    }
}

@Composable
private fun FailedStep(phase: StatusPhase.Failed, actions: StatusActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Les statuts n'ont pas pu être gardés")
    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onBack)
    SideButton("Changer de dossier", Icons.Rounded.FolderOpen, Modifier.fillMaxWidth(), onClick = actions.onChangeFolder)
}
