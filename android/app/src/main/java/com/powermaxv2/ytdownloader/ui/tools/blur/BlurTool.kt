package com.powermaxv2.ytdownloader.ui.tools.blur

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Face
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.blur.BlurEffect
import com.powermaxv2.ytdownloader.core.blur.BlurHolder
import com.powermaxv2.ytdownloader.core.blur.BlurOptions
import com.powermaxv2.ytdownloader.core.blur.BlurPhase
import com.powermaxv2.ytdownloader.core.blur.BlurPlan
import com.powermaxv2.ytdownloader.core.blur.BlurStrength
import com.powermaxv2.ytdownloader.core.blur.BlurUiState
import com.powermaxv2.ytdownloader.core.blur.FaceSearch
import com.powermaxv2.ytdownloader.core.blur.ZoneShape
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
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val BLUR_TITLE = "Flouter"
const val BLUR_COMMAND = "blur"
const val BLUR_SUBTITLE = "Cache un visage, une plaque ou une adresse avant d'envoyer la photo."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class BlurActions(
    val onChoose: () -> Unit = {},
    val onDraw: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    val onSelect: (Int?) -> Unit = {},
    val onBeginEdit: () -> Unit = {},
    val onMove: (Int, Float, Float) -> Unit = { _, _, _ -> },
    val onResize: (Int, Float, Float) -> Unit = { _, _, _ -> },
    val onDelete: (Int) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onFaces: () -> Unit = {},
    val onOptions: (BlurOptions) -> Unit = {},
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/**
 * Flouter une photo (ToolRegistry.PHOTO_BLUR): the user drags over what must not be seen (a face, a plate, a
 * number, an address), or lets Gulp find the faces, picks blur, pixels or black, and saves a hidden copy.
 * State in [BlurHolder], so the photo and its zones are still there after a trip home.
 */
@Composable
fun BlurTool(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = remember { BlurHolder.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(uri)) vm.toast("Un enregistrement est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.save() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la photo.", success = false)
    }

    val actions = BlurActions(
        onChoose = {
            try {
                picker.launch(MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onDraw = controller::draw,
        onSelect = controller::select,
        onBeginEdit = controller::beginEdit,
        onMove = controller::move,
        onResize = controller::resize,
        onDelete = controller::delete,
        onUndo = controller::undo,
        onClear = controller::clear,
        onFaces = controller::findFaces,
        onOptions = controller::setOptions,
        onSave = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.save()
        },
        onBack = controller::back,
        onReset = controller::reset,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, listOf(it), vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase != BlurPhase.Ready && state.phase != BlurPhase.Saving) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = BLUR_TITLE, command = BLUR_COMMAND, subtitle = BLUR_SUBTITLE, scrollState = scroll) {
        BlurContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.BlurContent(state: BlurUiState, actions: BlurActions) {
    AnimatedContent(
        targetState = state,
        // Zones, options and saving stay on the same page: only a change of step slides.
        contentKey = { if (it.phase == BlurPhase.Saving) BlurPhase.Ready::class else it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "blurPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                BlurPhase.Idle -> IdleStep(actions)
                BlurPhase.Reading -> ReadingStep()
                is BlurPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre photo", Icons.Rounded.Image, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                BlurPhase.Ready, BlurPhase.Saving -> ReadyStep(current, actions)
                is BlurPhase.Done -> DoneStep(current, phase, actions)
                is BlurPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La photo n'a pas été enregistrée")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onSave)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Zones", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
                        SideButton("Autre photo", Icons.Rounded.Image, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: BlurActions) {
    FileDropZone(
        title = "Choisir une photo",
        hint = "Un visage, une plaque, une adresse : tu caches ce que tu veux",
        icon = Icons.Rounded.BlurOn,
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
        Spacer(Modifier.height(12.dp))
        BlurDemo()
        Spacer(Modifier.height(10.dp))
        Text(
            terminalize("Glisse le doigt sur ce qu'il faut cacher, ou laisse Gulp trouver les visages. Flou, pixels ou noir : le détail est effacé pour de bon dans la copie."),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. La photo d'origine n'est pas modifiée.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ReadingStep() {
    Box(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.TextPrimary.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(terminalize("Lecture de la photo…"), color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReadyStep(state: BlurUiState, actions: BlurActions) {
    val photo = state.photo ?: return
    val saving = state.phase == BlurPhase.Saving
    val options = state.options
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlurStage(
            preview = state.preview,
            aspect = photo.aspect,
            zones = state.zones,
            selected = state.selected,
            searching = state.faces == FaceSearch.Searching,
            busy = saving,
            actions = actions,
        )
        Spacer(Modifier.height(10.dp))
        ZoneCounter(state.zones.size, state.faces)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SideButton(
                if (state.faces == FaceSearch.Searching) "Recherche…" else "Visages",
                Icons.Rounded.Face,
                Modifier.weight(1f),
                onClick = actions.onFaces,
            )
            SideButton("Annuler", Icons.AutoMirrored.Rounded.Undo, Modifier.weight(1f), onClick = actions.onUndo)
        }
        AnimatedVisibility(state.selected != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SideButton(
                "Retirer cette zone",
                Icons.Rounded.Delete,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { state.selected?.let(actions.onDelete) }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Cacher avec")
        Spacer(Modifier.height(10.dp))
        EffectTiles(options.effect) { actions.onOptions(options.copy(effect = it)) }
        AnimatedVisibility(options.effect != BlurEffect.BAR, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(16.dp))
                BlockLabel("Force")
                Spacer(Modifier.height(8.dp))
                OptionChips(BlurStrength.entries, options.strength, { it.label }, { actions.onOptions(options.copy(strength = it)) })
            }
        }
        Spacer(Modifier.height(16.dp))
        BlockLabel(if (state.selected != null) "Forme de la zone" else "Forme des zones")
        Spacer(Modifier.height(8.dp))
        val shape = state.zones.firstOrNull { it.id == state.selected }?.shape ?: options.shape
        OptionChips(ZoneShape.entries, shape, { it.label }, { actions.onOptions(options.copy(shape = it)) })
    }
    MainButton(
        when {
            saving -> "Enregistrement…"
            state.zones.isEmpty() -> "Dessine une zone d'abord"
            else -> "Enregistrer dans la Galerie"
        },
        subtitle = "JPG, sans le lieu ni les infos de la photo",
        icon = Icons.Rounded.Download,
        enabled = !saving && state.zones.isNotEmpty(),
        modifier = Modifier.staggeredEntrance(2, baseDelay = 120),
        onClick = actions.onSave,
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 120), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Tout effacer", Icons.Rounded.Refresh, Modifier.weight(1f), onClick = actions.onClear)
        SideButton("Autre photo", Icons.Rounded.Image, Modifier.weight(1f), onClick = actions.onReset)
    }
}

@Composable
private fun DoneStep(state: BlurUiState, phase: BlurPhase.Done, actions: BlurActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("Photo floutée", "Enregistrée dans Galerie › Gulp")
        Spacer(Modifier.height(16.dp))
        val image = rememberImage(state.preview)
        val photo = state.photo
        if (image != null && photo != null) {
            Image(
                image,
                contentDescription = "Photo floutée",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(photo.aspect.coerceIn(0.3f, 3f))
                    .staggeredEntrance(1, baseDelay = 120)
                    .clip(RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 4.dp else 18.dp)),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            terminalize("${BlurPlan.zonesText(state.zones.size)} · ${phase.saved.name}"),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    MainButton(
        "Partager la photo",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 200),
    ) { actions.onShare(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Retoucher", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
        SideButton("Galerie", Icons.Rounded.Image, Modifier.weight(1f)) { actions.onOpen(phase.saved) }
    }
}

@Composable
internal fun terminalize(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text
