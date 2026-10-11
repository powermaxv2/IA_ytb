package com.powermaxv2.ytdownloader.ui.tools.pdfjoin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPhase
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPlan
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinTool
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinUiState
import com.powermaxv2.ytdownloader.core.pdfjoin.SplitMode
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
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

const val PDF_JOIN_TITLE = "Assembleur PDF"
const val PDF_JOIN_COMMAND = "pdf"
const val PDF_JOIN_SUBTITLE = "Colle plusieurs PDF et photos en un seul, change l'ordre, tourne ou retire des pages."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PdfJoinActions(
    val onChooseFiles: () -> Unit = {},
    val onAddFiles: () -> Unit = {},
    val onTool: (JoinTool) -> Unit = {},
    val onSplit: (SplitMode) -> Unit = {},
    val onTap: (Long) -> Unit = {},
    val onDrop: (Long, Int) -> Unit = { _, _ -> },
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onRotate: (Long) -> Unit = {},
    val onRemove: (Long) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToPages: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * The Assembleur PDF (ToolRegistry.PDF_JOIN). Its state lives in [App.pdfJoin], so the pages and a PDF being written
 * survive leaving this screen.
 */
@Composable
fun PdfJoinTool(vm: MainViewModel) {
    val controller = remember { App.instance.pdfJoin }
    val state by controller.state.collectAsStateWithLifecycle()
    val bitmaps by controller.thumbs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.add(listOf(uri))) vm.toast("Un PDF est en cours d'écriture : attendez-le ou annulez-le.", success = false)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.add(uris)) vm.toast("Un PDF est en cours d'écriture.", success = false)
    }
    // Android 9 and older: saving to Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le PDF.", success = false)
    }

    val actions = remember(controller) {
        val choose = {
            try {
                picker.launch(arrayOf("application/pdf", "image/*"))
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        }
        PdfJoinActions(
            onChooseFiles = choose,
            onAddFiles = choose,
            onTool = controller::setTool,
            onSplit = controller::setSplit,
            onTap = controller::tap,
            onDrop = controller::moveTo,
            onMove = controller::move,
            onRotate = controller::rotate,
            onRemove = controller::remove,
            onStart = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToPages = controller::backToPages,
            onOpen = { openSavedFile(context, it, vm) },
            onShare = { shareSavedFiles(context, it, vm) },
        )
    }

    // One ImageBitmap per thumbnail, made once.
    val thumbs = remember(bitmaps) { bitmaps.mapValues { it.value.asImageBitmap() } }

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is JoinPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = PDF_JOIN_TITLE, command = PDF_JOIN_COMMAND, subtitle = PDF_JOIN_SUBTITLE, scrollState = scroll) {
        PdfJoinContent(state, thumbs, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.PdfJoinContent(state: JoinUiState, thumbs: Map<Long, ImageBitmap>, actions: PdfJoinActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160)) using SizeTransform(clip = false)
        },
        label = "pdfJoinPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                JoinPhase.Idle -> if (current.reading) ReadingStep() else IdleStep(current, actions)
                JoinPhase.Ready -> ReadyStep(current, thumbs, actions)
                is JoinPhase.Working -> WorkingStep(current, phase, actions)
                is JoinPhase.Done -> DoneStep(phase, thumbs, actions)
                is JoinPhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: JoinUiState, actions: PdfJoinActions) {
    FileDropZone(
        title = "Choisir des PDF et des photos",
        hint = "Plusieurs à la fois : vous les mettrez dans l'ordre ensuite",
        icon = Icons.Rounded.PictureAsPdf,
        onClick = actions.onChooseFiles,
    )
    state.notice?.let { Hint(it, warning = true, modifier = Modifier.padding(horizontal = 4.dp)) }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("L'Assembleur sait")
        Spacer(Modifier.height(8.dp))
        AbilityRow(JoinTool.JOIN, "Assembler", "PDF et photos en un seul PDF", 0)
        AbilityRow(JoinTool.SPLIT, "Séparer", "un PDF en plusieurs", 1)
        AbilityRow(JoinTool.REMOVE, "Retirer des pages", "et garder le reste", 2)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne, et les fichiers d'origine ne sont jamais modifiés. Les pages d'un PDF sont copiées telles quelles : le texte reste sélectionnable.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun AbilityRow(tool: JoinTool, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(tool.icon(), size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(if (terminal) detail.lowercase() else detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
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
        Text("Lecture des pages…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ReadyStep(state: JoinUiState, thumbs: Map<Long, ImageBitmap>, actions: PdfJoinActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    ToolChips(state.tool, actions.onTool)
    AnimatedVisibility(
        state.tool == JoinTool.SPLIT,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        OptionChips(SplitMode.entries, state.split, { it.label }, actions.onSplit)
    }
    state.notice?.let { Hint(it, warning = true, modifier = Modifier.padding(horizontal = 4.dp)) }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val summary = JoinPlan.summary(state.pages.size, state.pages.map { it.source }.distinct().size)
            Text(
                if (terminal) "> ${summary.uppercase()}" else summary,
                color = Palette.TextSecondary,
                fontSize = if (terminal) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (terminal) 2.sp else 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (state.reading) Modifier.shimmer() else Modifier),
            )
            Spacer(Modifier.width(8.dp))
            AddButton(actions.onAddFiles)
        }
        PageGrid(state, thumbs, actions.onTap, actions.onDrop)

        val selected = state.selected?.takeIf { state.tool == JoinTool.JOIN }
        val place = state.pages.indexOfFirst { it.id == selected }
        AnimatedVisibility(
            selected != null && place >= 0,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (selected != null && place >= 0) {
                SelectionBar(
                    number = place + 1,
                    count = state.pages.size,
                    onLeft = { actions.onMove(selected, -1) },
                    onRight = { actions.onMove(selected, +1) },
                    onRotate = { actions.onRotate(selected) },
                    onRemove = { actions.onRemove(selected) },
                )
            }
        }
        Hint(
            when (state.tool) {
                JoinTool.JOIN -> if (selected != null) "Appuyez longuement sur une page et glissez-la pour la déplacer" else "Touchez une page pour la tourner ou la retirer · glissez-la pour la déplacer"
                JoinTool.SPLIT -> if (state.split == SplitMode.EACH) "Chaque page devient un PDF · touchez une page pour couper à la place" else "Touchez la page où commence chaque nouveau PDF"
                JoinTool.REMOVE -> "Touchez les pages à retirer, touchez encore pour les garder"
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val blocker = state.blocker
    if (blocker != null && !(state.tool == JoinTool.REMOVE && state.marked.isEmpty())) {
        Hint(blocker, warning = true, modifier = Modifier.padding(horizontal = 4.dp))
    }
    val parts = state.parts
    val (label, subtitle) = when (state.tool) {
        JoinTool.JOIN -> "Créer le PDF (${JoinPlan.pages(state.pages.size)})" to null
        JoinTool.SPLIT -> (if (parts.size > 1) "Créer ${parts.size} PDF" else "Séparer") to null
        JoinTool.REMOVE -> {
            val n = state.marked.count { id -> state.pages.any { it.id == id } }
            (if (n > 0) "Retirer ${JoinPlan.pages(n)}" else "Retirer des pages") to
                (if (n > 0) "${JoinPlan.pages(state.pages.size - n)} restent" else null)
        }
    }
    MainButton(
        label,
        subtitle = subtitle,
        icon = state.tool.icon(),
        enabled = blocker == null && !state.reading,
        modifier = Modifier.staggeredEntrance(4, baseDelay = 120),
        onClick = actions.onStart,
    )
}

@Composable
private fun WorkingStep(state: JoinUiState, phase: JoinPhase.Working, actions: PdfJoinActions) {
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
        val step = if (phase.done < phase.total) "Page ${phase.done + 1} sur ${phase.total}" else "Enregistrement…"
        Text(
            if (terminal) "> $step" else step,
            color = Palette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (state.tool) {
                JoinTool.JOIN -> "Assemblage en un seul PDF"
                JoinTool.SPLIT -> "Séparation en ${state.parts.size} PDF"
                JoinTool.REMOVE -> "Nouveau PDF sans les pages retirées"
            },
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
    Hint(
        "Vous pouvez revenir à l'accueil : le PDF continue de s'écrire tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", Icons.Rounded.Close, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(phase: JoinPhase.Done, thumbs: Map<Long, ImageBitmap>, actions: PdfJoinActions) {
    val saved = phase.saved
    val single = saved.size == 1
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FannedPages(phase.cover.map { thumbs[it] })
        Spacer(Modifier.height(10.dp))
        val title = when {
            phase.tool == JoinTool.SPLIT -> "${saved.size} PDF, prêts"
            phase.tool == JoinTool.REMOVE -> "Pages retirées"
            else -> "Un seul PDF, prêt"
        }
        val where = saved.firstOrNull()?.location ?: "Download/Gulp"
        val subtitle = if (single) {
            "${saved.first().name} · ${JoinPlan.pages(phase.pages)} · ${bytes(phase.size)}"
        } else {
            "Enregistrés dans $where · ${bytes(phase.size)}"
        }
        DoneTitle(title, subtitle)
        if (single) {
            Spacer(Modifier.height(2.dp))
            Hint("Enregistré dans $where")
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        phase.lines.take(MAX_LINES).forEachIndexed { index, line ->
            ResultLine(line.name, line.pdf, line.detail, index)
        }
        if (phase.lines.size > MAX_LINES) Hint("et ${phase.lines.size - MAX_LINES} autres", modifier = Modifier.padding(start = 60.dp, bottom = 6.dp))
    }
    if (phase.drawn > 0) {
        Hint(
            (if (phase.drawn > 1) "${phase.drawn} pages viennent" else "Une page vient") +
                " d'un PDF abîmé : Gulp l'a recopiée en image, son texte ne se sélectionne plus.",
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    if (single) {
        MainButton("Ouvrir le PDF", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(saved.first()) }
        Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(saved) }
            SideButton("Modifier", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onBackToPages)
        }
    } else {
        MainButton("Partager les ${saved.size} PDF", icon = Icons.Rounded.Share, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onShare(saved) }
        Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            saved.firstOrNull()?.let { first -> SideButton("Ouvrir le 1er", Icons.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpen(first) } }
            SideButton("Modifier", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onBackToPages)
        }
    }
    SideButton("Autres fichiers", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}

private const val MAX_LINES = 8

@Composable
private fun DoneTitle(title: String, subtitle: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) title.uppercase() else title,
        color = Palette.TextPrimary,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
        textAlign = TextAlign.Center,
        modifier = Modifier.staggeredEntrance(0, baseDelay = 380),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (terminal) "# $subtitle" else subtitle,
        color = Palette.TextSecondary,
        fontSize = 13.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 380),
    )
}

@Composable
private fun FailedStep(state: JoinUiState, phase: JoinPhase.Failed, actions: PdfJoinActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Le PDF n'a pas pu être créé")
    if (state.pages.isNotEmpty()) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Pages", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onBackToPages)
            SideButton("Autres fichiers", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir d'autres fichiers", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}
