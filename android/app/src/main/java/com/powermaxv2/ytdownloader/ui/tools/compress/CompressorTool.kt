package com.powermaxv2.ytdownloader.ui.tools.compress

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.powermaxv2.ytdownloader.core.compress.CompressItem
import com.powermaxv2.ytdownloader.core.compress.CompressPhase
import com.powermaxv2.ytdownloader.core.compress.CompressSettings
import com.powermaxv2.ytdownloader.core.compress.CompressorUiState
import com.powermaxv2.ytdownloader.core.compress.Level
import com.powermaxv2.ytdownloader.core.compress.Media
import com.powermaxv2.ytdownloader.core.compress.Mode
import com.powermaxv2.ytdownloader.core.compress.SizeGoal
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.TileGrid
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.FormatTile
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlin.math.roundToInt

const val COMPRESSOR_TITLE = "Compresseur"
const val COMPRESSOR_COMMAND = "compress"
const val COMPRESSOR_SUBTITLE = "Rend une photo, une vidéo, un son ou un PDF plus léger, ou range plusieurs fichiers dans un ZIP."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class CompressorActions(
    val onChooseFiles: () -> Unit = {},
    val onAddFiles: () -> Unit = {},
    val onRemove: (CompressItem) -> Unit = {},
    val onMode: (Mode) -> Unit = {},
    val onSettings: (CompressSettings) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToFiles: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * The Compresseur (ToolRegistry.COMPRESSOR). Its state lives in [App.compressor], so a compression keeps going when
 * the user leaves this screen; a file shared to "Compresser avec Gulp" arrives through [MainViewModel.pendingFile].
 */
@Composable
fun CompressorTool(vm: MainViewModel) {
    val controller = remember { App.instance.compressor }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(listOf(uri))) vm.toast("Une compression est en cours : attendez-la ou annulez-la.", success = false)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Une compression est en cours.", success = false)
    }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris, add = true)) vm.toast("Une compression est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures / Movies / Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le fichier.", success = false)
    }

    val actions = remember(controller) {
        fun launch(launcher: () -> Unit) {
            try {
                launcher()
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        }
        CompressorActions(
            onChooseFiles = { launch { picker.launch(arrayOf("*/*")) } },
            onAddFiles = { launch { adder.launch(arrayOf("*/*")) } },
            onRemove = controller::remove,
            onMode = controller::setMode,
            onSettings = controller::setSettings,
            onStart = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToFiles = controller::backToFiles,
            onOpen = { openFile(context, it, vm) },
            onShare = { shareFiles(context, it, vm) },
        )
    }

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is CompressPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = COMPRESSOR_TITLE, command = COMPRESSOR_COMMAND, subtitle = COMPRESSOR_SUBTITLE, scrollState = scroll) {
        CompressorContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.CompressorContent(state: CompressorUiState, actions: CompressorActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "compressorPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                CompressPhase.Idle -> IdleStep(actions)
                CompressPhase.Reading -> ReadingStep()
                is CompressPhase.Unsupported -> UnsupportedStep(phase, actions)
                CompressPhase.Ready -> ReadyStep(current, actions)
                is CompressPhase.Working -> WorkingStep(current, phase, actions)
                is CompressPhase.Done -> DoneStep(current, phase, actions)
                is CompressPhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: CompressorActions) {
    FileDropZone(
        title = "Choisir des fichiers",
        hint = "Un fichier pour l'alléger, plusieurs pour les alléger ou en faire un ZIP",
        icon = Icons.Rounded.Compress,
        onClick = actions.onChooseFiles,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Gulp sait alléger")
        Spacer(Modifier.height(8.dp))
        SupportRow(Media.IMAGE, "Photos", "JPG · PNG · WebP · HEIC", 0)
        SupportRow(Media.VIDEO, "Vidéos", "MP4 · MOV · MKV…", 1)
        SupportRow(Media.AUDIO, "Sons", "MP3 · M4A · WAV…", 2)
        SupportRow(Media.PDF, "PDF", "scans et pages photo", 3)
        SupportRow(Media.OTHER, "Tout le reste", "dans un ZIP", 4)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. L'original n'est jamais modifié : Gulp enregistre une copie plus légère.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
private fun UnsupportedStep(phase: CompressPhase.Unsupported, actions: CompressorActions) {
    ErrorCard(null, phase.message)
    SideButton("Choisir d'autres fichiers", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChooseFiles)
}

@Composable
private fun ReadyStep(state: CompressorUiState, actions: CompressorActions) {
    val settings = state.settings
    val canMix = state.multiple || state.zipOnly.isNotEmpty()
    if (canMix) {
        ModeSwitch(
            zip = state.mode == Mode.ZIP,
            onChange = { actions.onMode(if (it) Mode.ZIP else Mode.EACH) },
            modifier = Modifier.staggeredEntrance(0, baseDelay = 40),
        )
    }
    if (state.multiple) FileList(state, actions) else state.items.firstOrNull()?.let { SingleFileCard(it, actions) }

    val shrinking = state.mode == Mode.EACH || settings.shrinkBeforeZip
    val anyMedia = state.items.any { it.media != Media.OTHER }
    AnimatedVisibility(
        visible = shrinking && anyMedia,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier.staggeredEntrance(2, baseDelay = 100),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .toolCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlockLabel("Niveau")
            val levelOff = state.goalAllowed && settings.goal != null
            TileGrid(Level.entries, columns = 3) { index, level, cell ->
                FormatTile(
                    label = level.label,
                    detail = level.detail,
                    selected = !levelOff && level == settings.level,
                    index = index,
                    onClick = { actions.onSettings(settings.copy(level = level, goal = null)) },
                    modifier = cell,
                )
            }
            if (state.goalAllowed) {
                BlockLabel("Ou une taille précise")
                OptionChips(
                    listOf<SizeGoal?>(null) + SizeGoal.entries,
                    settings.goal,
                    { goal -> goal?.let { "${it.label} · ${it.bytes / 1_000_000} Mo" } ?: "Libre" },
                    { actions.onSettings(settings.copy(goal = it)) },
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlockLabel("Résultat estimé")
        SizeBars(state.totalSize, state.estimate, estimated = true)
        if (state.mode == Mode.ZIP && anyMedia) {
            SwitchRow(
                "Alléger les photos et vidéos d'abord",
                "Sinon le ZIP ne gagne presque rien sur elles",
                settings.shrinkBeforeZip,
            ) { actions.onSettings(settings.copy(shrinkBeforeZip = it)) }
        }
        if (state.hasVideo && shrinking && !(state.goalAllowed && settings.goal != null)) {
            SwitchRow(
                if (settings.level == Level.STRONG) "Réduire à 720p" else "Réduire à 1080p",
                "Une vidéo 4K se regarde très bien en plus petit sur un téléphone",
                settings.downscale,
            ) { actions.onSettings(settings.copy(downscale = it)) }
        }
        if (state.items.any { it.media == Media.PDF } && shrinking) {
            Hint("PDF : les pages sont refaites en images, le texte n'est plus sélectionnable.")
        }
    }

    val blocker = state.blocker
    blocker?.let { Hint(it, warning = true, modifier = Modifier.padding(horizontal = 4.dp)) }
    val zip = state.mode == Mode.ZIP
    MainButton(
        if (zip) "Créer le ZIP" else "Compresser",
        subtitle = summary(state),
        icon = if (zip) Icons.Rounded.FolderZip else Icons.Rounded.Compress,
        enabled = blocker == null,
        modifier = Modifier.staggeredEntrance(4, baseDelay = 120),
        onClick = actions.onStart,
    )
}

private fun summary(state: CompressorUiState): String {
    val count = state.items.size
    val what = if (count > 1) "$count fichiers" else state.items.firstOrNull()?.media?.label ?: ""
    val goal = state.settings.goal?.takeIf { state.goalAllowed }
    val how = goal?.let { "moins de ${it.bytes / 1_000_000} Mo" } ?: state.settings.level.label.lowercase()
    return if (state.mode == Mode.ZIP && !state.settings.shrinkBeforeZip) "$what · sans retoucher" else "$what · $how"
}

@Composable
private fun SingleFileCard(item: CompressItem, actions: CompressorActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, key = item.file.uri, baseDelay = 40)
            .toolCard()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(item.media.icon())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.file.name,
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val meta = meta(item)
            Text(if (terminal) "# $meta" else meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (terminal) "[CHANGER]" else "Changer",
            color = LocalAccent.current.start,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .bounceClick(pressedScale = 0.9f, onClick = actions.onChooseFiles)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .bounceClick(pressedScale = 0.97f, onClick = actions.onAddFiles)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (terminal) "AJOUTER D'AUTRES FICHIERS (ZIP)" else "Ajouter d'autres fichiers (pour un ZIP)",
            color = LocalAccent.current.start,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun meta(item: CompressItem): String = buildList {
    item.file.size?.let { add(bytes(it)) }
    add(item.media.label)
    val w = item.info.width
    val h = item.info.height
    if (w != null && h != null) add(if (item.media == Media.VIDEO) "${minOf(w, h)}p" else "$w × $h")
    item.info.durationSec?.let { d ->
        val s = d.roundToInt()
        add(if (s >= 60) "${s / 60} min ${(s % 60).toString().padStart(2, '0')}" else "$s s")
    }
    item.info.pages?.let { add(if (it > 1) "$it pages" else "1 page") }
}.joinToString(" · ")

@Composable
private fun FileList(state: CompressorUiState, actions: CompressorActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("${state.items.size} fichiers" + (state.totalSize?.let { " · ${bytes(it)}" } ?: ""))
        Spacer(Modifier.height(6.dp))
        val shown = state.items.take(MAX_ROWS)
        shown.forEachIndexed { index, item ->
            FileRow(item.file.name, item.file.size, item.media, index) { actions.onRemove(item) }
        }
        if (state.items.size > MAX_ROWS) {
            Hint("et ${state.items.size - MAX_ROWS} autres", modifier = Modifier.padding(start = 48.dp, top = 2.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick(pressedScale = 0.97f, onClick = actions.onAddFiles)
                .padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (terminal) "AJOUTER DES FICHIERS" else "Ajouter des fichiers",
                color = LocalAccent.current.start,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private const val MAX_ROWS = 8

@Composable
private fun WorkingStep(state: CompressorUiState, phase: CompressPhase.Working, actions: CompressorActions) {
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
        Text(
            if (terminal) "> ${phase.step}" else phase.step,
            color = Palette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (state.multiple) "${state.items.size} fichiers" else state.items.firstOrNull()?.file?.name.orEmpty(),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!state.multiple) {
            Spacer(Modifier.height(20.dp))
            SizeBars(state.totalSize, phase.written, estimated = false)
            Spacer(Modifier.height(10.dp))
            Hint("La taille « Après » grandit pendant que Gulp écrit le fichier.", modifier = Modifier.fillMaxWidth())
        }
    }
    Hint(
        "Vous pouvez revenir à l'accueil : la compression continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: CompressorUiState, phase: CompressPhase.Done, actions: CompressorActions) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val saved = phase.savedFiles
    val before = phase.before
    val after = phase.after
    val gain = if (before > 0) ((1.0 - after.toDouble() / before) * 100).roundToInt().coerceIn(0, 99) else 0
    val zip = state.mode == Mode.ZIP
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
        if (saved.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (terminal) "DÉJÀ LÉGER" else "Déjà bien compressé",
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Gulp n'a pas réussi à faire plus léger sans abîmer le fichier : gardez l'original. Essayez « Maxi » ou une taille précise.",
                color = Palette.TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            GainCounter(gain)
            Text(
                if (terminal) "> ${bytes(before - after)} GAGNÉS" else "${bytes(before - after)} gagnés",
                color = Palette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            val where = saved.first().location
            Text(
                if (saved.size > 1) "${saved.size} fichiers enregistrés dans $where" else "Enregistré dans $where",
                color = Palette.TextSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            SizeBars(before, after, estimated = false)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                phase.results.take(4).forEachIndexed { index, result ->
                    ResultRow(result.name, result.before, result.after, result.saved != null, index) { result.saved?.let(actions.onOpen) }
                }
                if (phase.results.size > 4) Hint("et ${phase.results.size - 4} autres", modifier = Modifier.padding(start = 4.dp))
            }
            val kept = phase.results.count { it.saved == null }
            if (kept > 0 && !zip) {
                Spacer(Modifier.height(8.dp))
                Hint(if (kept > 1) "$kept fichiers déjà légers ont été laissés tels quels." else "Un fichier déjà léger a été laissé tel quel.")
            }
        }
    }
    saved.firstOrNull()?.let { first ->
        MainButton("Ouvrir", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(first) }
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (saved.isNotEmpty()) SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(saved) }
        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToFiles)
    }
    SideButton("Autres fichiers", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}

@Composable
private fun ResultRow(name: String, before: Long, after: Long, saved: Boolean, index: Int, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 300)
            .bounceClick(enabled = saved, pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
            .background(Palette.Surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (terminal) "+ $name" else name,
            color = Palette.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(bytes(after), color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        val pct = if (before > 0) ((1.0 - after.toDouble() / before) * 100).roundToInt() else 0
        Text(
            if (saved && pct > 0) "−$pct %" else "=",
            color = if (saved && pct > 0) Palette.Success else Palette.TextTertiary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FailedStep(state: CompressorUiState, phase: CompressPhase.Failed, actions: CompressorActions) {
    ErrorCard(phase.debug, phase.message, title = if (LocalSkin.current == Skin.TERMINAL) "ERREUR" else "La compression n'a pas abouti")
    if (state.items.isNotEmpty()) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToFiles)
            SideButton("Autres fichiers", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir d'autres fichiers", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}

@Composable
private fun ErrorCard(debug: String?, message: String, title: String? = null) {
    var details by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                title?.let {
                    Text(it, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                }
                Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        debug?.takeIf { it.isNotBlank() }?.let { log ->
            Spacer(Modifier.height(10.dp))
            Text(
                if (details) "Masquer les détails" else "Détails",
                color = LocalAccent.current.start,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bounceClick { details = !details },
            )
            AnimatedVisibility(details, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(
                    log.lines().takeLast(14).joinToString("\n"),
                    color = Palette.TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Background.copy(alpha = 0.6f))
                        .padding(10.dp),
                )
            }
        }
    }
}

// MARK: - Intents

private fun openFile(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce fichier", success = false) }
}

private fun shareFiles(context: Context, files: List<SavedFile>, vm: MainViewModel) {
    if (files.isEmpty()) return
    val intent = if (files.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(files.first().mime)
            .putExtra(Intent.EXTRA_STREAM, files.first().uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(files.map { it.uri }))
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
