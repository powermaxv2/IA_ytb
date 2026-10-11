package com.powermaxv2.ytdownloader.ui.tools.cleaner

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.cleaner.CleanerFacts
import com.powermaxv2.ytdownloader.core.cleaner.CleanerPhase
import com.powermaxv2.ytdownloader.core.cleaner.CleanerTab
import com.powermaxv2.ytdownloader.core.cleaner.CleanerUiState
import com.powermaxv2.ytdownloader.core.cleaner.MediaFile
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

const val CLEANER_TITLE = "Nettoyeur WhatsApp"
const val CLEANER_COMMAND = "clean"
const val CLEANER_SUBTITLE = "Trouve les doublons et les gros médias de WhatsApp, et libère de la place."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class CleanerActions(
    val onChooseFolder: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onTab: (CleanerTab) -> Unit = {},
    val onToggle: (MediaFile) -> Unit = {},
    val onToggleAll: (List<MediaFile>) -> Unit = {},
    val onAskConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBackToResults: () -> Unit = {},
    val onReset: () -> Unit = {},
    /** Picture of a photo or a video of the folder; null = icons only. */
    val thumbnail: (suspend (MediaFile) -> Bitmap?)? = null,
    /** "Now", for the "older than 3 months" choices. */
    val now: Long = System.currentTimeMillis(),
)

/**
 * The Nettoyeur WhatsApp (ToolRegistry.CLEANER): lists what takes space in WhatsApp's folder (duplicates, big files,
 * each kind of media) and deletes what the user checks, after a confirmation. Its state lives in [App.cleaner].
 */
@Composable
fun CleanerTool(vm: MainViewModel) {
    val controller = remember { App.instance.cleaner }
    val state by controller.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) controller.chooseFolder(tree)
    }
    val actions = CleanerActions(
        onChooseFolder = {
            try {
                picker.launch(controller.initialUri())
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de dossier sur ce téléphone.", success = false)
            }
        },
        onScan = controller::scan,
        onCancel = controller::cancel,
        onTab = controller::setTab,
        onToggle = controller::toggle,
        onToggleAll = controller::toggleAll,
        onAskConfirm = controller::askConfirm,
        onDismissConfirm = controller::dismissConfirm,
        onDelete = controller::delete,
        onBackToResults = controller::backToResults,
        onReset = controller::reset,
        thumbnail = controller::thumbnail,
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is CleanerPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = CLEANER_TITLE, command = CLEANER_COMMAND, subtitle = CLEANER_SUBTITLE, scrollState = scroll) {
        CleanerContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.CleanerContent(state: CleanerUiState, actions: CleanerActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "cleanerPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                CleanerPhase.Idle -> IdleStep(current, actions)
                is CleanerPhase.Scanning -> ScanningStep(phase, actions)
                CleanerPhase.Ready -> ReadyStep(current, actions)
                is CleanerPhase.Deleting -> DeletingStep(phase)
                is CleanerPhase.Done -> DoneStep(phase, actions)
                is CleanerPhase.Failed -> FailedStep(phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: CleanerUiState, actions: CleanerActions) {
    val folder = state.folder
    if (folder == null) {
        FileDropZone(
            title = "Choisir le dossier WhatsApp",
            hint = "Une seule fois : Gulp s'en souvient",
            icon = BroomIcon,
            onClick = actions.onChooseFolder,
        )
        Hint(
            "Dans le sélecteur, ouvre Android › media › com.whatsapp › WhatsApp › Media, puis touche « Utiliser ce dossier » et autorise.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .staggeredEntrance(1, baseDelay = 120),
        )
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(0, baseDelay = 40)
                .toolCard()
                .padding(16.dp),
        ) {
            BlockLabel("Dossier choisi")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScanRadar(found = 0, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    Text("Prêt à analyser", color = Palette.TextSecondary, fontSize = 13.sp)
                }
            }
            if (state.folderOdd) {
                Spacer(Modifier.height(10.dp))
                Hint("Ce dossier ne ressemble pas à celui de WhatsApp. Choisis plutôt WhatsApp › Media.", warning = true)
            }
        }
        MainButton("Analyser le dossier", icon = BroomIcon, modifier = Modifier.staggeredEntrance(1, baseDelay = 80), onClick = actions.onScan)
        SideButton("Changer de dossier", Icons.Rounded.FolderOpen, Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 80), onClick = actions.onChooseFolder)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Ce que le Nettoyeur repère")
        Spacer(Modifier.height(6.dp))
        InfoRow(Icons.Rounded.ContentCopy, "Les doublons", "Le même mème reçu dans 5 groupes, comparé octet par octet", 0)
        InfoRow(Icons.Rounded.Straighten, "Les gros fichiers", "Les vidéos qui pèsent le plus, en tête", 1)
        InfoRow(Icons.Rounded.PieChart, "Ce qui prend la place", "Vidéos, photos, vocaux, stickers, statuts…", 2)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Rien n'est supprimé sans que tu l'aies coché et confirmé. Les sauvegardes des discussions ne sont jamais touchées.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(if (terminal) 3.dp else 11.dp))
                .background(accent.tint(if (terminal) 0.12f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(if (terminal) detail.lowercase() else detail, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ScanningStep(phase: CleanerPhase.Scanning, actions: CleanerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScanRadar(phase.files)
        Spacer(Modifier.height(18.dp))
        val comparing = phase.toHash > 0
        val step = if (comparing) "Recherche des doublons…" else "Inventaire du dossier…"
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        val found = "${CleanerFacts.files(phase.files)} · ${CleanerFacts.bytes(phase.bytes)}"
        Text(if (terminal) found.lowercase() else found, color = Palette.TextSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center)
        if (comparing) {
            Spacer(Modifier.height(16.dp))
            val progress = phase.hashed.toFloat() / phase.toHash
            if (terminal) TerminalProgress(progress) else ThinBar(progress)
            Spacer(Modifier.height(6.dp))
            val compared = "${phase.hashed} / ${phase.toHash} fichiers de même taille comparés"
            Text(if (terminal) "# $compared" else compared, color = Palette.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun ThinBar(progress: Float) {
    val accent = LocalAccent.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 120f), label = "thinBar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.TextPrimary.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0.03f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.horizontal() else androidx.compose.ui.graphics.SolidColor(accent.start)),
        )
    }
}

@Composable
private fun ReadyStep(state: CleanerUiState, actions: CleanerActions) {
    val report = state.report
    val terminal = LocalSkin.current == Skin.TERMINAL
    // Summary: the total counting up, the bar filling by kind.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel(state.folder?.let { "WhatsApp · $it" } ?: "WhatsApp")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountingBytes(report.totalBytes, Palette.TextPrimary, 40.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                CleanerFacts.files(report.files.size).let { if (terminal) it.lowercase() else it },
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        SpaceBar(report.shares)
        if (report.wastedBytes > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(2, baseDelay = 700)
                    .clip(RoundedCornerShape(if (terminal) 2.dp else 14.dp))
                    .background(Palette.Danger.copy(alpha = 0.09f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                val copies = report.duplicates.sumOf { it.copies.size }
                val text = "${CleanerFacts.bytes(report.wastedBytes)} de doublons · ${if (copies > 1) "$copies copies" else "1 copie"} en trop"
                Text(if (terminal) text.lowercase() else text, color = Palette.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    OptionChips(
        options = CleanerTab.entries.toList(),
        selected = state.tab,
        label = { tab ->
            when (tab) {
                CleanerTab.DUPLICATES -> "${tab.label} (${report.duplicates.size})"
                CleanerTab.BIG -> "${tab.label} (${report.big.size})"
                CleanerTab.TYPES -> tab.label
            }
        },
        onSelect = actions.onTab,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 80),
    )

    AnimatedContent(
        targetState = state.tab,
        transitionSpec = { fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(120)) },
        label = "cleanerTab",
        modifier = Modifier.fillMaxWidth(),
    ) { tab ->
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(2, baseDelay = 120)
                .toolCard()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tab) {
                CleanerTab.DUPLICATES -> DuplicatesList(state, actions)
                CleanerTab.BIG -> BigList(state, actions)
                CleanerTab.TYPES -> TypesList(state, actions)
            }
        }
    }

    val count = state.selection.size
    AnimatedVisibility(state.confirming, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        ConfirmCard(state, actions)
    }
    AnimatedVisibility(!state.confirming, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        MainButton(
            CleanerFacts.freeLabel(state.selectedBytes, count),
            icon = Icons.Rounded.DeleteForever,
            enabled = count > 0,
            modifier = Modifier.staggeredEntrance(3, baseDelay = 160),
            onClick = actions.onAskConfirm,
        )
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 160), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Analyser à nouveau", Icons.Rounded.Refresh, Modifier.weight(1f), onClick = actions.onScan)
        SideButton("Autre dossier", Icons.Rounded.FolderOpen, Modifier.weight(1f), onClick = actions.onChooseFolder)
    }
}

@Composable
private fun DuplicatesList(state: CleanerUiState, actions: CleanerActions) {
    val groups = state.report.duplicates
    if (groups.isEmpty()) {
        EmptyLine("Aucun doublon : chaque fichier n'est là qu'une fois.")
        return
    }
    var shown by rememberSaveable { mutableIntStateOf(GROUPS_STEP) }
    var row = 0
    groups.take(shown).forEachIndexed { g, group ->
        if (g > 0) Divider()
        GroupHeader(group.files.size, group.keep.size, group.wasted, Modifier.padding(top = 6.dp, bottom = 2.dp))
        group.files.forEachIndexed { i, file ->
            FileRow(
                file,
                checked = file.id in state.selection,
                index = row++,
                thumbnail = actions.thumbnail,
                tag = if (i == 0) "Original" else null,
            ) { actions.onToggle(file) }
        }
    }
    if (groups.size > shown) {
        MoreLine("Voir ${minOf(GROUPS_STEP, groups.size - shown)} groupes de plus (${groups.size - shown} restants)") { shown += GROUPS_STEP }
    }
}

@Composable
private fun BigList(state: CleanerUiState, actions: CleanerActions) {
    val big = state.report.big
    if (big.isEmpty()) {
        EmptyLine("Aucun fichier de plus de ${CleanerFacts.bytes(CleanerFacts.BIG_MIN)}.")
        return
    }
    val biggest = big.first().size.toFloat()
    big.forEachIndexed { i, file ->
        FileRow(file, file.id in state.selection, i, actions.thumbnail, barFraction = file.size / biggest) { actions.onToggle(file) }
    }
}

@Composable
private fun TypesList(state: CleanerUiState, actions: CleanerActions) {
    val shares = state.report.shares
    if (shares.isEmpty()) {
        EmptyLine("Le dossier est vide.")
        return
    }
    val terminal = LocalSkin.current == Skin.TERMINAL
    val biggest = shares.first().bytes.coerceAtLeast(1)
    shares.forEachIndexed { i, share ->
        if (i > 0) Divider()
        val all = state.report.files.filter { it.type == share.type }
        val old = CleanerFacts.olderThan(state.report.files, share.type, OLD_DAYS, actions.now)
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(i, baseDelay = 60)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = typeColor(share.type)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(if (terminal) 3.dp else 11.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(typeIcon(share.type), contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val label = share.type.label
                    Text(if (terminal) label.uppercase() else label, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    val detail = CleanerFacts.files(share.count)
                    Text(if (terminal) detail.lowercase() else detail, color = Palette.TextTertiary, fontSize = 12.sp)
                }
                Text(CleanerFacts.bytes(share.bytes).let { if (terminal) it.lowercase() else it }, color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            val grow by animateFloatAsState(share.bytes.toFloat() / biggest, tween(800, delayMillis = 100 + i * 60), label = "typeBar")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Palette.TextPrimary.copy(alpha = 0.06f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(grow.coerceIn(0.02f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(typeColor(share.type)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickChip("Tout (${all.size})", checked = state.selection.containsAll(all.map { it.id })) { actions.onToggleAll(all) }
                if (old.isNotEmpty() && old.size < all.size) {
                    PickChip("+ de 3 mois (${old.size})", checked = state.selection.containsAll(old.map { it.id })) { actions.onToggleAll(old) }
                }
            }
        }
    }
}

/** Small toggle under a kind of media: checks (or unchecks) a whole set of files. */
@Composable
private fun PickChip(text: String, checked: Boolean, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val tint by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = 400f), label = "pickChip")
    val shape = RoundedCornerShape(if (terminal) 2.dp else 50.dp)
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(Palette.Danger.copy(alpha = 0.06f + 0.10f * tint))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) {
            AnimatedCheck(Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(if (terminal) text.lowercase() else text, color = if (checked) Palette.Danger else Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConfirmCard(state: CleanerUiState, actions: CleanerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val kept = state.report.duplicates.count { group -> group.copies.any { it.id in state.selection } && group.keep.id !in state.selection }
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(state.selection.size)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            val title = "Supprimer pour de bon ?"
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            CleanerFacts.confirmText(state.selection.size, state.selectedBytes, kept),
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        MainButton("Oui, supprimer ${CleanerFacts.bytes(state.selectedBytes)}", icon = Icons.Rounded.DeleteForever, onClick = actions.onDelete)
        SideButton("Non, revenir", null, Modifier.fillMaxWidth(), onClick = actions.onDismissConfirm)
    }
}

@Composable
private fun DeletingStep(phase: CleanerPhase.Deleting) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress = if (phase.total > 0) phase.done.toFloat() / phase.total else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) {
            SweepingBroom()
            Spacer(Modifier.height(16.dp))
            TerminalProgress(progress)
        } else {
            Box(contentAlignment = Alignment.TopCenter) {
                ProgressRing(progress, size = 180.dp)
            }
            Spacer(Modifier.height(8.dp))
            SweepingBroom(size = 40.dp)
        }
        Spacer(Modifier.height(14.dp))
        val step = "Suppression ${phase.done.coerceAtMost(phase.total)}/${phase.total}…"
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    Hint(
        "Garde Gulp ouvert le temps de la suppression.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun DoneStep(phase: CleanerPhase.Done, actions: CleanerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    val freed = phase.deleted.sumOf { it.size }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(104.dp))
            AnimatedCheck(Modifier.size(60.dp))
        }
        Spacer(Modifier.height(6.dp))
        CountingBytes(freed, Palette.TextPrimary, 40.sp, durationMillis = 1400)
        val sub = if (phase.deleted.size > 1) "libérés · ${phase.deleted.size} fichiers supprimés" else "libérés · 1 fichier supprimé"
        Text(
            if (terminal) "# $sub" else sub,
            color = Palette.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = LocalTokens.current.body,
            modifier = Modifier.staggeredEntrance(1, baseDelay = 300),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CleanerFacts.doneLines(phase.deleted, phase.failed, phase.before).forEachIndexed { i, line ->
            Row(Modifier.staggeredEntrance(i, baseDelay = 360), verticalAlignment = Alignment.Top) {
                Text(if (terminal) "+" else "•", color = accent.start, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(if (terminal) line.lowercase() else line, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
    MainButton("Voir ce qui reste", icon = BroomIcon, modifier = Modifier.staggeredEntrance(2, baseDelay = 200), onClick = actions.onBackToResults)
    SideButton("Analyser à nouveau", Icons.Rounded.Refresh, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onScan)
}

@Composable
private fun FailedStep(phase: CleanerPhase.Failed, actions: CleanerActions) {
    ToolErrorCard(phase.message, title = "L'analyse n'a pas abouti")
    MainButton("Choisir le dossier", icon = Icons.Rounded.FolderOpen, onClick = actions.onChooseFolder)
    SideButton("Réessayer", Icons.Rounded.Refresh, Modifier.fillMaxWidth(), onClick = actions.onScan)
}

// MARK: - Small pieces

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(Palette.TextPrimary.copy(alpha = if (LocalSkin.current == Skin.TERMINAL) 0.14f else 0.08f)),
    )
}

@Composable
private fun EmptyLine(text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) "# $text" else text,
        color = Palette.TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 8.dp),
    )
}

@Composable
private fun MoreLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = LocalAccent.current.start,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

private const val GROUPS_STEP = 20
private const val OLD_DAYS = 90
