package com.powermaxv2.ytdownloader.ui.tools.status

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.powermaxv2.ytdownloader.core.status.StatusLength
import com.powermaxv2.ytdownloader.core.status.StatusPhase
import com.powermaxv2.ytdownloader.core.status.StatusPlan
import com.powermaxv2.ytdownloader.core.status.StatusUiState
import com.powermaxv2.ytdownloader.core.trim.TrimSource
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val STATUS_TITLE = "Découpe statut"
const val STATUS_COMMAND = "split"
const val STATUS_SUBTITLE = "Coupe une longue vidéo en morceaux d'une minute, prêts à poster à la suite en statut WhatsApp."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class StatusActions(
    val onChoose: () -> Unit = {},
    val onLength: (StatusLength) -> Unit = {},
    val onEqual: (Boolean) -> Unit = {},
    val onExact: (Boolean) -> Unit = {},
    val onCut: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onWhatsApp: (List<SavedFile>) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
)

/** The Découpe statut (ToolRegistry.STATUS_SPLIT). Its state lives in [App.statusSplit], so a series keeps going off screen. */
@Composable
fun StatusTool(vm: MainViewModel) {
    val controller = remember { App.instance.statusSplit }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(uri)) vm.toast("Une découpe est en cours : attendez-la ou annulez-la.", success = false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une découpe est en cours.", success = false)
    }
    // Android 9 and older: saving to Movies needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les statuts.", success = false)
    }
    val actions = remember(controller) {
        StatusActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("video/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onLength = controller::setLength,
            onEqual = controller::setEqual,
            onExact = controller::setExact,
            onCut = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onWhatsApp = { shareToWhatsApp(context, it, vm) },
            onShare = { shareSavedFiles(context, it, vm) },
            onOpen = { openSavedFile(context, it, vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is StatusPhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = STATUS_TITLE, command = STATUS_COMMAND, subtitle = STATUS_SUBTITLE, scrollState = scroll) {
        StatusContent(state, actions)
    }
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
                StatusPhase.Idle -> IdleStep(actions)
                StatusPhase.Reading -> ReadingStep()
                is StatusPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre vidéo", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                StatusPhase.Ready -> current.source?.let { EditStep(current, it, actions) }
                is StatusPhase.Working -> WorkingStep(current, phase, actions)
                is StatusPhase.Done -> DoneStep(phase, actions)
                is StatusPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La découpe n'a pas abouti")
                    if (phase.saved.isNotEmpty()) {
                        val n = phase.saved.size
                        Hint(
                            "$n statut${if (n > 1) "s" else ""} déjà enregistré${if (n > 1) "s" else ""} dans ${phase.saved.first().location}.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onCut)
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
private fun IdleStep(actions: StatusActions) {
    FileDropZone(
        title = "Choisir une vidéo",
        hint = "Plus d'une minute : MP4, MOV, MKV, WebM…",
        icon = Icons.Rounded.DonutLarge,
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
        HowRow(Icons.Rounded.ContentCut, "Gulp coupe la vidéo", "en morceaux d'1 min ou 30 s", 0)
        HowRow(Icons.Rounded.PhotoLibrary, "Les range dans la Galerie", "album Gulp, dans l'ordre", 1)
        HowRow(Icons.AutoMirrored.Rounded.Send, "Tu les postes d'un coup", "WhatsApp › Mon statut", 2)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. La vidéo d'origine n'est jamais modifiée.",
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
private fun EditStep(state: StatusUiState, source: TrimSource, actions: StatusActions) {
    val parts = state.parts
    val refusal = state.refusal
    SourceCard(state, source, actions)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Un statut dure")
        OptionChips(StatusLength.entries.toList(), state.length, { it.label }, actions.onLength)
        SwitchRow(
            "Parts égales",
            "Tous les statuts de la même durée. Sinon, 1 min pile et le reste à la fin.",
            state.equal,
            actions.onEqual,
        )
        SwitchRow(
            "Coupe à la seconde près",
            "Plus lent : la vidéo est réencodée. Sinon, coupe instantanée sur l'image clé la plus proche.",
            state.exact,
            actions.onExact,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BlockLabel("Tes statuts")
        if (refusal != null) {
            Hint(refusal, warning = true)
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                StatusRing(parts.size) { RingCenter("${parts.size}", "statuts") }
            }
            parts.take(PARTS_SHOWN).forEachIndexed { i, part -> PartRow(i, parts.size, part, state.thumb(part)) }
            if (parts.size > PARTS_SHOWN) MoreParts(parts.size - PARTS_SHOWN)
        }
    }
    AnimatedVisibility(
        visible = refusal == null,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        MainButton(
            "Couper en ${parts.size} statuts",
            subtitle = buttonSubtitle(state),
            icon = Icons.Rounded.ContentCut,
            modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
            onClick = actions.onCut,
        )
    }
}

private fun buttonSubtitle(state: StatusUiState): String {
    val parts = state.parts
    val each = if (state.equal) "${StatusPlan.short(parts.firstOrNull()?.durationMs ?: 0L)} chacun" else "${state.length.label} chacun"
    return each + if (state.copies) " · sans réencodage" else " · réencodé"
}

@Composable
private fun SourceCard(state: StatusUiState, source: TrimSource, actions: StatusActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ratio = ((source.width ?: 9).toFloat() / (source.height ?: 16).coerceAtLeast(1)).coerceIn(0.5f, 2f)
            Box(
                Modifier
                    .width(if (ratio < 1f) 64.dp else 104.dp)
                    .aspectRatio(ratio.coerceIn(0.5625f, 1.78f))
                    .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                    .background(Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                val frame = state.frames.firstOrNull()
                if (frame != null) {
                    Image(frame.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(26.dp))
                }
                Text(
                    StatusPlan.short(source.durationMs),
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Scrim)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
}

private fun meta(source: TrimSource): String = buildList {
    add(StatusPlan.short(source.durationMs))
    val w = source.width
    val h = source.height
    if (w != null && h != null) add("${minOf(w, h)}p")
    source.file.size?.let { add(bytes(it)) }
}.joinToString(" · ")

@Composable
private fun WorkingStep(state: StatusUiState, phase: StatusPhase.Working, actions: StatusActions) {
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
            val filled = if (phase.progress < 0f) 0f else phase.progress * phase.count
            StatusRing(phase.count, size = 184.dp, filled = filled, active = true) {
                RingCenter("${phase.index + 1}/${phase.count}", "en cours")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (terminal) "> ${phase.step}" else phase.step, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            state.source?.file?.name.orEmpty(),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    Hint(
        "Chaque statut part dans la Galerie dès qu'il est prêt. Vous pouvez revenir à l'accueil : la découpe continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(phase: StatusPhase.Done, actions: StatusActions) {
    val saved = phase.saved
    val n = saved.size
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("$n statuts prêts", "Dans ${saved.firstOrNull()?.location ?: "la Galerie"}, dans l'ordre")
        Spacer(Modifier.height(14.dp))
        saved.take(PARTS_SHOWN).forEachIndexed { i, file -> SavedRow(i, n, file) }
        if (n > PARTS_SHOWN) MoreParts(n - PARTS_SHOWN)
    }
    MainButton(
        "Poster sur WhatsApp",
        subtitle = "Choisis « Mon statut » : ils partent dans l'ordre",
        icon = Icons.AutoMirrored.Rounded.Send,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
    ) { actions.onWhatsApp(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(saved) }
        SideButton("Voir le 1er", Icons.Rounded.OpenInNew, Modifier.weight(1f)) { saved.firstOrNull()?.let(actions.onOpen) }
    }
    SideButton("Autre vidéo", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
    val note = when {
        phase.redone > 0 -> "Sans réencodage, sauf ${phase.redone} statut${if (phase.redone > 1) "s" else ""} recoupé${if (phase.redone > 1) "s" else ""} à la seconde près pour tenir dans la durée."
        phase.copied -> "Sans réencodage : qualité d'origine. Un statut peut reprendre une fraction de seconde de la fin du précédent."
        else -> null
    }
    note?.let {
        Hint(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
}
