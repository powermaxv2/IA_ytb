package com.powermaxv2.ytdownloader.ui.tools.rename

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.rename.NameBlock
import com.powermaxv2.ytdownloader.core.rename.NameCase
import com.powermaxv2.ytdownloader.core.rename.NameDateFormat
import com.powermaxv2.ytdownloader.core.rename.RenameController
import com.powermaxv2.ytdownloader.core.rename.RenameFile
import com.powermaxv2.ytdownloader.core.rename.RenameOrder
import com.powermaxv2.ytdownloader.core.rename.RenamePhase
import com.powermaxv2.ytdownloader.core.rename.RenamePlan
import com.powermaxv2.ytdownloader.core.rename.RenamePreview
import com.powermaxv2.ytdownloader.core.rename.RenameRule
import com.powermaxv2.ytdownloader.core.rename.RenameUiState
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val RENAME_TITLE = "Renommeur"
const val RENAME_COMMAND = "rename"
const val RENAME_SUBTITLE = "Renomme plein de fichiers d'un coup : numéro, date, texte remplacé, et l'aperçu avant de valider."

/** Most files listed in the preview; the others are counted under it. */
private const val PREVIEW_ROWS = 40

/** The tool's icon (home card, drop zone). */
val RenameIcon = Icons.Rounded.DriveFileRenameOutline

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class RenameActions(
    val onChooseFiles: () -> Unit = {},
    val onChooseFolder: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRemove: (RenameFile) -> Unit = {},
    val onRule: (RenameRule) -> Unit = {},
    val onStart: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * The Renommeur (ToolRegistry.RENAMER): builds a name from pieces, shows every file's new name live, then renames.
 * Its state lives in [App.renamer], so a renaming keeps going when the user leaves this screen.
 */
@Composable
fun RenameTool(vm: MainViewModel) {
    val controller = remember { App.instance.renamer }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pickFiles(uris)) vm.toast("Un renommage est en cours.", success = false)
    }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pickFiles(uris, add = true)) vm.toast("Un renommage est en cours.", success = false)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null && !controller.pickFolder(tree)) vm.toast("Un renommage est en cours.", success = false)
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    // Android 9 and older: the renamed copies (files whose folder refuses renaming) need the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les copies.", success = false)
    }

    val actions = RenameActions(
        onChooseFiles = { launch { picker.launch(arrayOf("*/*")) } },
        onChooseFolder = { launch { folder.launch(null) } },
        onAdd = { launch { adder.launch(arrayOf("*/*")) } },
        onRemove = controller::remove,
        onRule = controller::setRule,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && state.preview().copies > 0 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onUndo = controller::undo,
        onReset = controller::reset,
        onBack = controller::backToEdit,
        onShare = { shareSavedFiles(context, it, vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is RenamePhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = RENAME_TITLE, command = RENAME_COMMAND, subtitle = RENAME_SUBTITLE, scrollState = scroll) {
        RenameContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.RenameContent(state: RenameUiState, actions: RenameActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "renamePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                RenamePhase.Idle -> IdleStep(actions)
                RenamePhase.Reading -> ReadingStep()
                is RenamePhase.Unsupported -> UnsupportedStep(phase, actions)
                RenamePhase.Ready -> ReadyStep(current, actions)
                is RenamePhase.Working -> WorkingStep(phase)
                is RenamePhase.Done -> DoneStep(current, phase, actions)
                is RenamePhase.Failed -> FailedStep(phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: RenameActions) {
    FileDropZone(
        title = "Choisir des fichiers",
        hint = "Photos, vidéos, PDF, n'importe quoi · plusieurs à la fois",
        icon = RenameIcon,
        onClick = actions.onChooseFiles,
    )
    SideButton("Tout un dossier", Icons.Rounded.Folder, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 160), onClick = actions.onChooseFolder)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(4, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Par exemple")
        Spacer(Modifier.height(8.dp))
        listOf(
            "IMG_20261013_184207.jpg" to "2026-10-13_001.jpg",
            "IMG_20261013_190112.jpg" to "2026-10-13_002.jpg",
            "Scan (3).pdf" to "Facture EDF.pdf",
        ).forEachIndexed { index, (before, after) -> ExampleLine(before, after, index) }
    }
    Hint(
        "Tout se passe sur le téléphone. Tu vois chaque nouveau nom avant de valider, et un bouton remet les anciens noms.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ExampleLine(before: String, after: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 320)
            .padding(vertical = 5.dp),
    ) {
        Text(before, color = Palette.TextTertiary, fontSize = 12.5.sp, maxLines = 1)
        Text((if (terminal) "→ " else "↳ ") + after, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
        Text("Lecture des fichiers…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun UnsupportedStep(phase: RenamePhase.Unsupported, actions: RenameActions) {
    ToolErrorCard(phase.message)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Fichiers", RenameIcon, Modifier.weight(1f), onClick = actions.onChooseFiles)
        SideButton("Dossier", Icons.Rounded.Folder, Modifier.weight(1f), onClick = actions.onChooseFolder)
    }
}

@Composable
private fun ReadyStep(state: RenameUiState, actions: RenameActions) {
    val preview = remember(state.files, state.rule, state.folderNames) { state.preview() }
    val rule = state.rule
    FilesCard(state, preview, actions)
    RuleCard(rule, preview, actions)
    ReplaceCard(rule, actions)
    OptionsCard(rule, actions)
    PreviewCard(preview)
    val count = preview.ready.size
    MainButton(
        when (count) {
            0 -> "Rien à renommer"
            1 -> "Renommer 1 fichier"
            else -> "Renommer $count fichiers"
        },
        icon = RenameIcon,
        enabled = count > 0,
        subtitle = summary(preview),
        modifier = Modifier.staggeredEntrance(5, baseDelay = 120),
        onClick = actions.onStart,
    )
    if (preview.copies > 0) {
        Hint(
            "${preview.copies} fichier${if (preview.copies > 1) "s sont" else " est"} dans un dossier qui refuse qu'on les renomme : Gulp en fait une copie au nouveau nom dans le dossier Gulp du téléphone.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

/** "1 doublon · 2 inchangés", null when everything is renamed. */
private fun summary(preview: RenamePreview): String? = listOfNotNull(
    preview.clashes.takeIf { it > 0 }?.let { if (it > 1) "$it doublons" else "1 doublon" },
    preview.unchanged.takeIf { it > 0 }?.let { if (it > 1) "$it inchangés" else "1 inchangé" },
    preview.empty.takeIf { it > 0 }?.let { if (it > 1) "$it noms vides" else "1 nom vide" },
).joinToString(" · ").ifEmpty { null }

@Composable
private fun FilesCard(state: RenameUiState, preview: RenamePreview, actions: RenameActions) {
    val count = state.files.size
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BlockLabel(state.folder?.let { "Dossier « $it »" } ?: "Fichiers choisis")
            Spacer(Modifier.height(2.dp))
            Text(
                if (count > 1) "$count fichiers" else "1 fichier",
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (preview.copies > 0) Text("dont ${preview.copies} en copie", color = Palette.TextSecondary, fontSize = 12.5.sp)
        }
        if (state.folder == null && count < RenameController.MAX_FILES) {
            SideButton("Ajouter", Icons.AutoMirrored.Rounded.NoteAdd, onClick = actions.onAdd)
        } else {
            SideButton("Changer", Icons.Rounded.Folder, onClick = actions.onReset)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(rule: RenameRule, preview: RenamePreview, actions: RenameActions) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val blocks = rule.blocks
    val chosen = selected.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    fun edit(block: NameBlock) = actions.onRule(rule.copy(blocks = blocks.toMutableList().also { it[chosen] = block }))
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Nouveau nom")
        Spacer(Modifier.height(10.dp))
        OptionChips(
            options = RenameRule.presets.map { it.first },
            selected = RenameRule.presets.firstOrNull { it.second == rule }?.first ?: "",
            label = { it },
            onSelect = { label ->
                RenameRule.presets.firstOrNull { it.first == label }?.let { (_, preset) ->
                    selected = 0
                    actions.onRule(preset.copy(order = rule.order))
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        SampleName(preview.rows.firstOrNull()?.newName ?: "")
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEachIndexed { index, block ->
                BlockChip(
                    block,
                    selected = index == chosen,
                    index = index,
                    onClick = { selected = index },
                    onRemove = if (blocks.size > 1) {
                        {
                            actions.onRule(rule.copy(blocks = blocks.filterIndexed { i, _ -> i != index }))
                            if (selected >= index) selected = (selected - 1).coerceAtLeast(0)
                        }
                    } else {
                        null
                    },
                )
            }
        }
        blocks.getOrNull(chosen)?.let { block ->
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = block::class to chosen,
                transitionSpec = { (fadeIn(tween(200)) + expandVertically()) togetherWith (fadeOut(tween(120)) + shrinkVertically()) },
                label = "blockEditor",
            ) { _ -> BlockEditor(block, ::edit) }
        }
        Spacer(Modifier.height(12.dp))
        AddBlockRow { block ->
            if (blocks.size < MAX_BLOCKS) {
                actions.onRule(rule.copy(blocks = blocks + block))
                selected = blocks.size
            }
        }
    }
}

private const val MAX_BLOCKS = 8

/** The settings of the selected piece. */
@Composable
private fun BlockEditor(block: NameBlock, onChange: (NameBlock) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (block) {
            is NameBlock.Text -> QrField("Texte", block.text, { onChange(NameBlock.Text(it)) }, placeholder = "Vacances_", maxLength = 60)
            is NameBlock.Counter -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QrField(
                        "Commence à",
                        block.start.toString(),
                        { text -> text.filter(Char::isDigit).take(6).toIntOrNull()?.let { onChange(block.copy(start = it)) } ?: onChange(block.copy(start = 0)) },
                        modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Number,
                        maxLength = 6,
                    )
                    QrField(
                        "De … en …",
                        block.step.toString(),
                        { text -> onChange(block.copy(step = text.filter(Char::isDigit).take(4).toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                        modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Number,
                        maxLength = 4,
                    )
                }
                OptionChips(
                    options = (1..4).toList(),
                    selected = block.digits.coerceIn(1, 4),
                    label = { RenamePlan.counter(NameBlock.Counter(start = 1, digits = it), 0) },
                    onSelect = { onChange(block.copy(digits = it)) },
                )
            }
            is NameBlock.Date -> {
                OptionChips(NameDateFormat.entries, block.format, { it.label }, { onChange(NameBlock.Date(it)) })
                Hint("La date de la photo quand elle la connaît, sinon celle de la dernière modification du fichier.")
            }
            NameBlock.Original -> Hint("Le nom actuel de chaque fichier, sans son extension. Remplace un morceau juste en dessous.")
        }
    }
}

@Composable
private fun ReplaceCard(rule: RenameRule, actions: RenameActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Remplacer")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QrField("Chercher", rule.find, { actions.onRule(rule.copy(find = it)) }, modifier = Modifier.weight(1f), placeholder = "IMG_", maxLength = 60)
            QrField("Par", rule.replace, { actions.onRule(rule.copy(replace = it)) }, modifier = Modifier.weight(1f), placeholder = "rien", maxLength = 60)
        }
        AnimatedVisibility(rule.find.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            SwitchRow("Ignorer les majuscules", "« img » trouve aussi « IMG »", rule.ignoreCase) { actions.onRule(rule.copy(ignoreCase = it)) }
        }
    }
}

@Composable
private fun OptionsCard(rule: RenameRule, actions: RenameActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Majuscules")
        Spacer(Modifier.height(8.dp))
        OptionChips(NameCase.entries, rule.case, { it.label }, { actions.onRule(rule.copy(case = it)) })
        Spacer(Modifier.height(14.dp))
        BlockLabel("Numéroter dans l'ordre")
        Spacer(Modifier.height(8.dp))
        OptionChips(RenameOrder.entries, rule.order, { orderLabel(it) }, { actions.onRule(rule.copy(order = it)) })
    }
}

private fun orderLabel(order: RenameOrder) = when (order) {
    RenameOrder.PICKED -> "Du choix"
    RenameOrder.NAME -> "Des noms"
    RenameOrder.DATE -> "Des dates"
}

@Composable
private fun PreviewCard(preview: RenamePreview) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(4, baseDelay = 120)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Avant → après")
        Spacer(Modifier.height(4.dp))
        preview.rows.take(PREVIEW_ROWS).forEachIndexed { index, row -> PreviewRow(row, index) }
        val more = preview.rows.size - PREVIEW_ROWS
        if (more > 0) {
            Text(
                "… et $more autre${if (more > 1) "s" else ""}",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun WorkingStep(phase: RenamePhase.Working) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "renameProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) TerminalProgress(progress) else ProgressRing(progress, size = 176.dp)
        Spacer(Modifier.height(16.dp))
        val what = if (phase.undoing) "Retour aux anciens noms" else "Renommage"
        val step = "$what ${phase.done.coerceAtMost(phase.total)}/${phase.total}…"
        Text(
            if (terminal) "> $step" else step,
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
    Hint(
        "Tu peux revenir à l'accueil : le renommage continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun DoneStep(state: RenameUiState, phase: RenamePhase.Done, actions: RenameActions) {
    val outcome = phase.outcome
    val renamed = outcome.renamed.size
    val copies = outcome.copies.size
    val title = when {
        outcome.undone -> "Anciens noms remis"
        outcome.total == 0 -> "Rien n'a changé"
        outcome.total > 1 -> "${outcome.total} fichiers renommés"
        else -> "1 fichier renommé"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader(title, state.folder?.let { "dans « $it »" })
        Spacer(Modifier.height(12.dp))
        var line = 0
        if (renamed > 0 && !outcome.undone) ResultLine(if (renamed > 1) "$renamed renommés sur place" else "1 renommé sur place", line++)
        if (copies > 0) ResultLine(if (copies > 1) "$copies copies au nouveau nom dans ${outcome.copies.first().location}" else "1 copie au nouveau nom dans ${outcome.copies.first().location}", line++)
        if (outcome.failed.isNotEmpty()) {
            val names = outcome.failed.take(3).joinToString(", ") + if (outcome.failed.size > 3) "…" else ""
            ResultLine("${outcome.failed.size} refusé${if (outcome.failed.size > 1) "s" else ""} par le téléphone : $names", line, warning = true)
        }
    }
    if (!outcome.undone && renamed > 0) {
        MainButton("Remettre les anciens noms", icon = Icons.AutoMirrored.Rounded.Undo, modifier = Modifier.staggeredEntrance(1, baseDelay = 200), onClick = actions.onUndo)
    }
    if (copies > 0) {
        SideButton(if (copies > 1) "Partager les $copies copies" else "Partager la copie", Icons.Rounded.Share, Modifier.fillMaxWidth()) { actions.onShare(outcome.copies) }
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Autre règle", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
        SideButton("Autres fichiers", RenameIcon, Modifier.weight(1f), onClick = actions.onReset)
    }
}

@Composable
private fun FailedStep(phase: RenamePhase.Failed, actions: RenameActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Le renommage n'a pas abouti")
    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onBack)
    SideButton("Autres fichiers", RenameIcon, Modifier.fillMaxWidth(), onClick = actions.onReset)
}
