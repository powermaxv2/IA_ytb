package com.powermaxv2.ytdownloader.ui.tools.convert

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.convert.AudioBitrate
import com.powermaxv2.ytdownloader.core.convert.Category
import com.powermaxv2.ytdownloader.core.convert.ConvertInput
import com.powermaxv2.ytdownloader.core.convert.ConvertOptions
import com.powermaxv2.ytdownloader.core.convert.ConvertPhase
import com.powermaxv2.ytdownloader.core.convert.ConverterUiState
import com.powermaxv2.ytdownloader.core.convert.Family
import com.powermaxv2.ytdownloader.core.convert.Formats
import com.powermaxv2.ytdownloader.core.convert.Quality
import com.powermaxv2.ytdownloader.core.convert.Resolution
import com.powermaxv2.ytdownloader.core.convert.Target
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.TileGrid
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold

const val CONVERTER_TITLE = "Convertisseur"
const val CONVERTER_COMMAND = "convert"
const val CONVERTER_SUBTITLE = "Change le format d'une vidéo, d'un son, d'une photo ou d'un document."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ConverterActions(
    val onChooseFile: () -> Unit = {},
    val onTarget: (Target) -> Unit = {},
    val onOptions: (ConvertOptions) -> Unit = {},
    val onConvert: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToFile: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
    val onPreview: (Uri) -> Unit = {},
)

/**
 * The Convertisseur (ToolRegistry.CONVERTER). Its state lives in [App.converter], so a conversion keeps going
 * when the user leaves this screen; a file shared to "Convertir avec Gulp" arrives through [MainViewModel.pendingFile].
 */
@Composable
fun ConverterTool(vm: MainViewModel) {
    val controller = remember { App.instance.converter }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A file can arrive while the tool is already open (Partager → Convertir avec Gulp): keyed on the URI.
    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(listOf(uri))) vm.toast("Une conversion est en cours : attendez-la ou annulez-la.", success = false)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Une conversion est en cours.", success = false)
    }
    // Android 9 and older: saving to Movies / Music / Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            controller.convert()
        } else {
            vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le fichier converti.", success = false)
        }
    }

    val actions = remember(controller) {
        ConverterActions(
            onChooseFile = {
                try {
                    picker.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onTarget = controller::selectTarget,
            onOptions = controller::setOptions,
            onConvert = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.convert()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToFile = controller::backToFile,
            onOpen = { openFile(context, it, vm) },
            onShare = { shareFiles(context, it, vm) },
            onPreview = { vm.openToolWithFile(ToolRegistry.PREVIEW, it) },
        )
    }

    val scroll = rememberScrollState()
    // A new step starts at the top of the screen (the progress ring, the result).
    LaunchedEffect(state.phase::class) { if (state.phase !is ConvertPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = CONVERTER_TITLE, command = CONVERTER_COMMAND, subtitle = CONVERTER_SUBTITLE, scrollState = scroll) {
        ConverterContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ConverterContent(state: ConverterUiState, actions: ConverterActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "converterPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                ConvertPhase.Idle -> IdleStep(actions)
                ConvertPhase.Reading -> ReadingStep()
                is ConvertPhase.Unsupported -> UnsupportedStep(phase, actions)
                ConvertPhase.Ready -> ReadyStep(current, actions)
                is ConvertPhase.Converting -> ConvertingStep(current, phase, actions)
                is ConvertPhase.Done -> DoneStep(current, phase, actions)
                is ConvertPhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: ConverterActions) {
    FileDropZone(
        title = "Choisir un fichier",
        hint = "Vidéo, son, photo, PDF, Markdown, CSV ou JSON — ou plusieurs photos pour en faire un PDF",
        icon = Icons.Rounded.UploadFile,
        onClick = actions.onChooseFile,
    )
    SupportedCard()
    LimitsCard()
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Palette.TextPrimary.copy(alpha = 0.08f)),
            )
            Text("Lecture du fichier…", color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun UnsupportedStep(phase: ConvertPhase.Unsupported, actions: ConverterActions) {
    FileCard(name = phase.name, meta = "Non convertible", icon = kindIcon(null), onChange = actions.onChooseFile)
    Row(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(phase.message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(phase.message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
    phase.uri?.let { uri ->
        MainButton("Voir dans Aperçu", icon = Icons.Rounded.Preview) { actions.onPreview(uri) }
    }
    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChooseFile)
    LimitsCard(initiallyOpen = true, index = 2)
}

@Composable
private fun ReadyStep(state: ConverterUiState, actions: ConverterActions) {
    val input = state.input ?: return
    val target = state.target ?: return
    FileCard(input, onChange = actions.onChooseFile)
    TargetPicker(state, actions)
    OptionsCard(input, target, state.options, actions.onOptions)
    MainButton(
        "Convertir en ${target.label}",
        subtitle = Formats.summary(input.category, target, state.options),
        icon = Icons.Rounded.SwapHoriz,
        modifier = Modifier.staggeredEntrance(4, baseDelay = 120),
        onClick = actions.onConvert,
    )
    LimitsCard(index = 5)
}

@Composable
private fun ConvertingStep(state: ConverterUiState, phase: ConvertPhase.Converting, actions: ConverterActions) {
    val input = state.input
    val target = state.target
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (input != null && target != null) FormatBadges(input.badge, target.label)
        Spacer(Modifier.height(20.dp))
        if (terminal) TerminalProgress(phase.progress) else ProgressRing(phase.progress)
        Spacer(Modifier.height(16.dp))
        Text(
            if (terminal) "> ${phase.step}" else phase.step,
            color = Palette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        input?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it.name,
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
    Text(
        "Vous pouvez revenir à l'accueil : la conversion continue tant que Gulp reste ouvert.",
        color = Palette.TextTertiary,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: ConverterUiState, phase: ConvertPhase.Done, actions: ConverterActions) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val saved = phase.saved
    val first = saved.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(120.dp))
            AnimatedCheck(Modifier.size(68.dp))
        }
        Spacer(Modifier.height(10.dp))
        val title = "Converti en ${state.target?.label ?: "nouveau format"}"
        Text(
            if (terminal) "OK · ${title.uppercase()}" else "$title !",
            color = Palette.TextPrimary,
            fontSize = if (terminal) 18.sp else 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        val where = first?.location?.let { "dans $it" } ?: ""
        Text(
            if (saved.size > 1) "${saved.size} fichiers enregistrés $where" else "Enregistré $where",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            saved.take(3).forEachIndexed { index, file ->
                SavedRow(file, index) { actions.onOpen(file) }
            }
            if (saved.size > 3) {
                Text(
                    "et ${saved.size - 3} autres",
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
    if (first != null) {
        MainButton("Ouvrir", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(first) }
        Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(saved) }
            SideButton("Voir dans Aperçu", Icons.Rounded.Preview, Modifier.weight(1f)) { actions.onPreview(first.uri) }
        }
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Autre format", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToFile)
        SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
    }
}

@Composable
private fun SavedRow(file: SavedFile, index: Int, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 300)
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
            .background(Palette.Surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (terminal) "+ ${file.name}" else file.name,
            color = Palette.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(Fmt.bytes(file.size.toDouble()), color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
    }
}

@Composable
private fun FailedStep(state: ConverterUiState, phase: ConvertPhase.Failed, actions: ConverterActions) {
    var details by remember { mutableStateOf(false) }
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(phase.message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (terminal) "ERREUR" else "La conversion n'a pas abouti",
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(phase.message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        state.input?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.name, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        phase.debug?.takeIf { it.isNotBlank() }?.let { debug ->
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
                    debug.lines().takeLast(14).joinToString("\n"),
                    color = Palette.TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
    if (state.input != null) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onConvert)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToFile)
            SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}

// MARK: - Pieces of the Ready step

@Composable
private fun FileCard(input: ConvertInput, onChange: () -> Unit) {
    val meta = buildList {
        input.size?.let { add(Fmt.bytes(it.toDouble())) }
        add(if (input.multiple) "Photos" else "${input.category.label} ${input.badge}")
    }.joinToString(" · ")
    FileCard(input.name, meta, kindIcon(input.kind), onChange)
}

/** The chosen file: icon, name, size and kind; it springs in when the file changes. */
@Composable
private fun FileCard(name: String, meta: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val appear = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.92f + 0.08f * p
                scaleY = 0.92f + 0.08f * p
                translationY = (1f - p) * 18.dp.toPx()
            }
            .toolCard()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(if (terminal) "# $meta" else meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (terminal) "[CHANGER]" else "Changer",
            color = LocalAccent.current.start,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .bounceClick(pressedScale = 0.9f, onClick = onChange)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun TargetPicker(state: ConverterUiState, actions: ConverterActions) {
    val input = state.input ?: return
    val groups = state.targets.groupBy { it.family }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Convertir en")
        var index = 0
        groups.forEach { (family, targets) ->
            if (groups.size > 1) {
                val title = when {
                    family == Family.AUDIO && input.category == Category.VIDEO -> "Le son seul"
                    else -> family.label
                }
                Text(
                    if (LocalSkin.current == Skin.TERMINAL) "-- ${title.lowercase()}" else title,
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val start = index
            TileGrid(targets, columns = 3) { i, target, modifier ->
                FormatTile(
                    label = target.label,
                    detail = Formats.detail(input.category, target, input.multiple),
                    selected = target == state.target,
                    index = start + i,
                    onClick = { actions.onTarget(target) },
                    modifier = modifier,
                )
            }
            index += targets.size
        }
    }
}

@Composable
private fun OptionsCard(input: ConvertInput, target: Target, options: ConvertOptions, onOptions: (ConvertOptions) -> Unit) {
    val copy = Formats.allowsCopy(target)
    val resolution = Formats.usesResolution(target, options)
    val quality = Formats.usesQuality(input.category, target, options)
    val bitrate = Formats.usesAudioBitrate(target, options)
    val any = copy || resolution || quality || bitrate
    AnimatedVisibility(
        visible = any,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn() + scaleIn(initialScale = 0.96f),
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier.staggeredEntrance(2, baseDelay = 100),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .toolCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (copy) {
                BlockLabel("Encodage")
                OptionChips(
                    listOf(false, true),
                    options.copyStreams,
                    { if (it) "Copie rapide" else "Réencoder en H.265" },
                    { onOptions(options.copy(copyStreams = it)) },
                )
                if (options.copyStreams) {
                    Text(
                        "Les pistes sont copiées telles quelles : rapide et sans perte, mais la taille ne change pas.",
                        color = Palette.TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            if (resolution) {
                BlockLabel("Définition")
                OptionChips(Resolution.entries, options.resolution, { it.label }, { onOptions(options.copy(resolution = it)) })
            }
            if (quality) {
                BlockLabel(if (target == Target.GIF) "Fluidité" else "Qualité")
                OptionChips(Quality.entries, options.quality, { it.label }, { onOptions(options.copy(quality = it)) })
            }
            if (bitrate) {
                BlockLabel("Son")
                OptionChips(AudioBitrate.entries, options.audioBitrate, { it.label }, { onOptions(options.copy(audioBitrate = it)) })
            }
            if (target == Target.GIF) {
                Text(
                    "Les ${Formats.GIF_MAX_SECONDS} premières secondes, sans le son : un GIF pèse vite très lourd.",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
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
