package com.powermaxv2.ytdownloader.ui.tools.resize

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.resize.FormatChoice
import com.powermaxv2.ytdownloader.core.resize.OutFormat
import com.powermaxv2.ytdownloader.core.resize.PercentPreset
import com.powermaxv2.ytdownloader.core.resize.QualityPreset
import com.powermaxv2.ytdownloader.core.resize.ResizeController
import com.powermaxv2.ytdownloader.core.resize.ResizeMode
import com.powermaxv2.ytdownloader.core.resize.ResizePhase
import com.powermaxv2.ytdownloader.core.resize.ResizePhoto
import com.powermaxv2.ytdownloader.core.resize.ResizePlan
import com.powermaxv2.ytdownloader.core.resize.ResizeSettings
import com.powermaxv2.ytdownloader.core.resize.ResizeUiState
import com.powermaxv2.ytdownloader.core.resize.SidePreset
import com.powermaxv2.ytdownloader.core.resize.WeightGoal
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.GainCounter
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SizeBars
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import com.powermaxv2.ytdownloader.ui.tools.wipe.PhotoStrip

const val RESIZE_TITLE = "Redimensionneur"
const val RESIZE_COMMAND = "resize"
const val RESIZE_SUBTITLE = "Réduis ou recadre plusieurs photos d'un coup : taille, forme, format et poids."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ResizeActions(
    val onChoose: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRemove: (ResizePhoto) -> Unit = {},
    val onSettings: (ResizeSettings) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * The Redimensionneur (ToolRegistry id "image-resizer"): several photos made smaller, cut to a shape, or brought
 * under a weight, saved as copies. Its state lives in [ResizeController.shared], so a batch keeps going when the
 * user leaves this screen.
 */
@Composable
fun ResizeTool(vm: MainViewModel) {
    val controller = remember { ResizeController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Un redimensionnement est en cours.", success = false)
    }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris, add = true)) vm.toast("Un redimensionnement est en cours.", success = false)
    }
    fun launch(launcher: () -> Unit) {
        try {
            launcher()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les photos.", success = false)
    }

    val actions = ResizeActions(
        onChoose = { launch { picker.launch(MIME_TYPES) } },
        onAdd = { launch { adder.launch(MIME_TYPES) } },
        onRemove = controller::remove,
        onSettings = controller::setSettings,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onCancel = controller::cancel,
        onReset = controller::reset,
        onBack = controller::backToPhotos,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, it, vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is ResizePhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = RESIZE_TITLE, command = RESIZE_COMMAND, subtitle = RESIZE_SUBTITLE, scrollState = scroll) {
        ResizeContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/bmp")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ResizeContent(state: ResizeUiState, actions: ResizeActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "resizePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                ResizePhase.Idle -> IdleStep(actions)
                ResizePhase.Reading -> ReadingStep()
                is ResizePhase.Unsupported -> UnsupportedStep(phase, actions)
                ResizePhase.Ready -> ReadyStep(current, actions)
                is ResizePhase.Working -> WorkingStep(current, phase, actions)
                is ResizePhase.Done -> DoneStep(phase, actions)
                is ResizePhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: ResizeActions) {
    FileDropZone(
        title = "Choisir des photos",
        hint = "JPG, PNG, WebP ou HEIC · une ou plusieurs à la fois",
        icon = ResizeIcon,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ce que Gulp peut faire")
        Spacer(Modifier.height(8.dp))
        ResizeDemo()
        Spacer(Modifier.height(8.dp))
        listOf(
            "Taille" to "le plus grand côté en pixels, ou un pourcentage",
            "Poids" to "sous 100 Ko, 500 Ko, 1 Mo… pour un formulaire",
            "Forme" to "carré, 4:5, 16:9, story, coupé au centre",
            "Format" to "JPG, PNG ou WebP, avec ou sans infos EXIF",
        ).forEachIndexed { index, (title, detail) -> FeatureLine(title, detail, index) }
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Les photos d'origine ne changent pas : Gulp enregistre des copies dans la Galerie.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun FeatureLine(title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 300)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(if (terminal) 0.dp else 4.dp))
                .background(LocalAccent.current.start),
        )
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Text(detail, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
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
        Text("Lecture des photos…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun UnsupportedStep(phase: ResizePhase.Unsupported, actions: ResizeActions) {
    ToolErrorCard(phase.message)
    SideButton("Choisir d'autres photos", ResizeIcon, Modifier.fillMaxWidth(), onClick = actions.onChoose)
}

@Composable
private fun ReadyStep(state: ResizeUiState, actions: ResizeActions) {
    val photos = state.photos
    val settings = state.settings
    fun set(change: ResizeSettings.() -> ResizeSettings) = actions.onSettings(settings.change())

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel(if (photos.size > 1) "${photos.size} photos choisies" else "1 photo choisie")
        Spacer(Modifier.height(12.dp))
        PhotoStrip(
            photos.map { it.file.uri },
            onRemove = { actions.onRemove(photos[it]) },
            onAdd = actions.onAdd.takeIf { photos.size < ResizePlan.MAX_PHOTOS },
        )
    }

    // New size: the mode, its presets, and the frame showing it on the first photo.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Nouvelle taille")
        Spacer(Modifier.height(10.dp))
        OptionChips(ResizeMode.entries, settings.mode, { it.label }, { set { copy(mode = it) } })
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            settings.mode,
            transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally { it / 6 }) togetherWith fadeOut(tween(120)) },
            label = "resizeMode",
        ) { mode ->
            when (mode) {
                ResizeMode.SIDE -> OptionChips(SidePreset.entries, settings.side, { "${it.label} · ${it.detail}" }, { set { copy(side = it) } })
                ResizeMode.PERCENT -> OptionChips(PercentPreset.entries, settings.percent, { it.label }, { set { copy(percent = it) } })
                ResizeMode.WEIGHT -> OptionChips(WeightGoal.entries, settings.weight, { "≤ ${it.label}" }, { set { copy(weight = it) } })
            }
        }
        val first = photos.firstNotNullOfOrNull { it.dims }
        if (first != null) {
            val to = ResizePlan.target(first, settings)
            val format = ResizePlan.format(photos.first().file.name, settings.format)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                SizeFrame(first, to)
                FormatBadge(badge(format, settings))
            }
            Spacer(Modifier.height(8.dp))
            DimsLine(first, to, extra = if (settings.mode == ResizeMode.WEIGHT) "≤ ${settings.weight.label}" else null)
            if (photos.size > 1) {
                Spacer(Modifier.height(4.dp))
                Hint("Exemple sur la 1re photo ; chaque photo garde ses proportions.", modifier = Modifier.fillMaxWidth())
            }
            if (ResizePlan.unchanged(first, settings)) {
                Spacer(Modifier.height(4.dp))
                Hint("Cette photo est déjà plus petite : Gulp ne l'agrandit pas.", modifier = Modifier.fillMaxWidth())
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 120)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Forme")
        Spacer(Modifier.height(10.dp))
        RatioTiles(settings.crop, { set { copy(crop = it) } })
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Format")
        Spacer(Modifier.height(10.dp))
        OptionChips(FormatChoice.entries, settings.format, { it.label }, { set { copy(format = it) } })
        val png = settings.format == FormatChoice.PNG
        AnimatedVisibility(
            settings.mode != ResizeMode.WEIGHT && !png,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                BlockLabel("Qualité")
                Spacer(Modifier.height(10.dp))
                OptionChips(QualityPreset.entries, settings.quality, { "${it.label} · ${it.quality}" }, { set { copy(quality = it) } })
            }
        }
        Spacer(Modifier.height(14.dp))
        SwitchRow(
            "Garder les infos EXIF",
            if (settings.keepExif) "Date, téléphone et lieu restent dans la copie" else "La copie ne dit ni où, ni quand, ni avec quoi",
            settings.keepExif,
        ) { on -> set { copy(keepExif = on) } }
    }

    MainButton(
        if (photos.size > 1) "Redimensionner les ${photos.size} photos" else "Redimensionner la photo",
        icon = ResizeIcon,
        modifier = Modifier.staggeredEntrance(4, baseDelay = 160),
        onClick = actions.onStart,
    )
    val note = "Les photos d'origine ne changent pas. Gulp enregistre des copies dans Galerie › Gulp."
    Text(
        if (LocalSkin.current == Skin.TERMINAL) "# $note" else note,
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .staggeredEntrance(5, baseDelay = 160),
    )
}

private fun badge(format: OutFormat, settings: ResizeSettings): String = when {
    settings.mode == ResizeMode.WEIGHT || format == OutFormat.PNG -> format.label
    else -> "${format.label} · ${settings.quality.quality}"
}

@Composable
private fun WorkingStep(state: ResizeUiState, phase: ResizePhase.Working, actions: ResizeActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val current = phase.done.coerceAtMost(state.photos.size - 1).coerceAtLeast(0)
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "resizeProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShrinkingPhoto(state.photos.getOrNull(current)?.file?.uri, current)
        Spacer(Modifier.height(22.dp))
        val step = if (phase.total > 1) "Photo ${(phase.done + 1).coerceAtMost(phase.total)}/${phase.total}…" else "Redimensionnement…"
        Text(
            if (terminal) "> $step" else step,
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.photos.getOrNull(current)?.file?.name.orEmpty(),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (terminal) TerminalProgress(progress) else ProgressBar(progress)
    }
    Hint(
        "Tu peux revenir à l'accueil : le travail continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
private fun DoneStep(phase: ResizePhase.Done, actions: ResizeActions) {
    val results = phase.results
    val saved = results.map { it.saved }
    val count = saved.size
    val before = results.sumOf { it.before }
    val after = results.sumOf { it.after }
    val gain = ResizePlan.gainPercent(before, after)
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader(
            if (count > 1) "$count photos prêtes" else "Photo prête",
            if (count > 1) "enregistrées dans Galerie › Gulp" else "enregistrée dans Galerie › Gulp",
        )
        if (gain > 0) GainCounter(gain, Modifier.staggeredEntrance(1, baseDelay = 200))
        Spacer(Modifier.height(10.dp))
        SizeBars(before, after, estimated = false, modifier = Modifier.staggeredEntrance(2, baseDelay = 200))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 160)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        results.forEachIndexed { index, result -> ResultRow(result, index) }
    }
    val missed = results.count { it.overWeight }
    if (missed > 0) {
        Hint(
            if (missed > 1) "$missed photos restent au-dessus du poids demandé, même toutes petites." else "Une photo reste au-dessus du poids demandé, même toute petite.",
            warning = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
    MainButton(
        if (count > 1) "Partager les $count photos" else "Partager la photo",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 200),
    ) { actions.onShare(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Galerie", Icons.Rounded.Image, Modifier.weight(1f)) { saved.firstOrNull()?.let(actions.onOpen) }
        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
    }
    SideButton("D'autres photos", ResizeIcon, Modifier.fillMaxWidth().staggeredEntrance(5, baseDelay = 200), onClick = actions.onReset)
}

@Composable
private fun FailedStep(state: ResizeUiState, phase: ResizePhase.Failed, actions: ResizeActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Le redimensionnement n'a pas abouti")
    if (state.photos.isNotEmpty()) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
            SideButton("Autres photos", ResizeIcon, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir d'autres photos", ResizeIcon, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}

