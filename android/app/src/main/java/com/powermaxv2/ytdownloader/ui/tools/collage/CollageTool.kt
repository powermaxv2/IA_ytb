package com.powermaxv2.ytdownloader.ui.tools.collage

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.collage.Collage
import com.powermaxv2.ytdownloader.core.collage.CollageFormat
import com.powermaxv2.ytdownloader.core.collage.CollageHolder
import com.powermaxv2.ytdownloader.core.collage.CollageLayout
import com.powermaxv2.ytdownloader.core.collage.CollageOptions
import com.powermaxv2.ytdownloader.core.collage.CollagePhase
import com.powermaxv2.ytdownloader.core.collage.CollageSpacing
import com.powermaxv2.ytdownloader.core.collage.CollageUiState
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

const val COLLAGE_TITLE = "Collage photos"
const val COLLAGE_COMMAND = "collage"
const val COLLAGE_SUBTITLE = "Plusieurs photos en une seule : grille, une grande, bande ou pêle-mêle."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class CollageActions(
    val onChoose: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOptions: (CollageOptions) -> Unit = {},
    val onTouch: (Int) -> Unit = {},
    val onShift: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Int) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/**
 * Collage photos (ToolRegistry.COLLAGE): 2 to 9 photos laid out in one picture (grid, one big, strip or pêle-mêle),
 * in a square, 4:5, story or 16:9 shape, saved as a JPEG in the Gallery. State in [CollageHolder], so the photos
 * and the settings are still there after a trip home.
 */
@Composable
fun CollageTool(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = remember { CollageHolder.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Un collage est en cours.", success = false)
    }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris, add = true)) vm.toast("Un collage est en cours.", success = false)
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.save() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le collage.", success = false)
    }

    val actions = CollageActions(
        onChoose = { launch { picker.launch(MIME_TYPES) } },
        onAdd = { launch { adder.launch(MIME_TYPES) } },
        onOptions = controller::setOptions,
        onTouch = controller::touch,
        onShift = controller::shift,
        onRemove = controller::remove,
        onShuffle = controller::shuffle,
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
    LaunchedEffect(state.phase::class) {
        if (state.phase != CollagePhase.Ready && state.phase != CollagePhase.Saving) scroll.animateScrollTo(0)
    }

    ToolScaffold(vm, title = COLLAGE_TITLE, command = COLLAGE_COMMAND, subtitle = COLLAGE_SUBTITLE, scrollState = scroll) {
        CollageContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.CollageContent(state: CollageUiState, actions: CollageActions) {
    AnimatedContent(
        targetState = state,
        // Saving stays on the settings screen (the stage breathes, the button changes), without a page transition.
        contentKey = { if (it.phase == CollagePhase.Saving) CollagePhase.Ready::class else it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "collagePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                CollagePhase.Idle -> IdleStep(actions)
                is CollagePhase.Reading -> ReadingStep(phase.count)
                is CollagePhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir d'autres photos", Icons.Rounded.AddPhotoAlternate, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                CollagePhase.Ready, CollagePhase.Saving -> ReadyStep(current, actions)
                is CollagePhase.Done -> DoneStep(current, phase, actions)
                is CollagePhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Le collage n'a pas été enregistré")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onSave)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
                        SideButton("Recommencer", Icons.Rounded.AddPhotoAlternate, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: CollageActions) {
    FileDropZone(
        title = "Choisir des photos",
        hint = "De ${Collage.MIN_PHOTOS} à ${Collage.MAX_PHOTOS} photos, dans l'ordre que tu veux",
        icon = Icons.Rounded.Dashboard,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Quatre façons de les assembler")
        Spacer(Modifier.height(14.dp))
        CollageDemo()
        Spacer(Modifier.height(12.dp))
        Text(
            terminalize("Choisis la forme (carré, story, 16:9), l'espace entre les photos et le fond. Touche deux photos pour les échanger."),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Les photos d'origine ne sont pas modifiées.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ReadingStep(count: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CollageDemo()
            Spacer(Modifier.height(12.dp))
            Text(
                terminalize(if (count == 1) "Lecture de la photo…" else "Lecture des $count photos…"),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ReadyStep(state: CollageUiState, actions: CollageActions) {
    val saving = state.phase == CollagePhase.Saving
    val options = state.options
    val count = state.photos.size
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CollageStage(
            state.photos,
            options,
            selected = state.selected,
            busy = saving,
            onTouch = if (saving) null else actions.onTouch,
        )
        Spacer(Modifier.height(14.dp))
        // A photo picked: move it, or take it out; otherwise the tip.
        AnimatedContent(
            targetState = state.selected,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "collageSelection",
        ) { selected ->
            if (selected != null && selected in state.photos.indices) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideButton("Avant", Icons.AutoMirrored.Rounded.ArrowBack, Modifier.weight(1f)) { actions.onShift(selected, -1) }
                    SideButton("Après", Icons.AutoMirrored.Rounded.ArrowForward, Modifier.weight(1f)) { actions.onShift(selected, 1) }
                    SideButton("Retirer", Icons.Rounded.Close, Modifier.weight(1f)) { actions.onRemove(selected) }
                }
            } else {
                Text(
                    terminalize("Touche une photo, puis une autre, pour les échanger."),
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        AnimatedVisibility(state.notice != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Hint(state.notice.orEmpty(), warning = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(1, baseDelay = 60), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Mélanger", Icons.Rounded.Shuffle, Modifier.weight(1f), onClick = actions.onShuffle)
        if (state.canAdd) SideButton("Ajouter", Icons.Rounded.AddPhotoAlternate, Modifier.weight(1f), onClick = actions.onAdd)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Disposition")
        Spacer(Modifier.height(10.dp))
        LayoutTiles(count, options) { actions.onOptions(options.copy(layout = it)) }
        Spacer(Modifier.height(16.dp))
        BlockLabel("Forme")
        Spacer(Modifier.height(8.dp))
        OptionChips(CollageFormat.entries, options.format, { it.label }, { actions.onOptions(options.copy(format = it)) })
        if (options.layout != CollageLayout.SCATTER) {
            Spacer(Modifier.height(16.dp))
            BlockLabel("Espace")
            Spacer(Modifier.height(8.dp))
            OptionChips(CollageSpacing.entries, options.spacing, { it.label }, { actions.onOptions(options.copy(spacing = it)) })
            Spacer(Modifier.height(16.dp))
            BlockLabel("Coins")
            Spacer(Modifier.height(8.dp))
            OptionChips(listOf(false, true), options.rounded, { if (it) "Arrondis" else "Droits" }, { actions.onOptions(options.copy(rounded = it)) })
        }
        Spacer(Modifier.height(16.dp))
        BlockLabel("Fond")
        Spacer(Modifier.height(10.dp))
        BackgroundSwatches(state.photos, options.background) { actions.onOptions(options.copy(background = it)) }
    }
    MainButton(
        when {
            saving -> "Enregistrement…"
            !state.enough -> "Ajoute au moins ${Collage.MIN_PHOTOS} photos"
            else -> "Enregistrer le collage"
        },
        subtitle = "$count photos · ${Collage.sizeLabel(options.format)}, en JPG",
        icon = Icons.Rounded.Download,
        enabled = !saving && state.enough,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onSave,
    )
    SideButton("Autres photos", Icons.Rounded.Dashboard, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 120), onClick = actions.onReset)
}

@Composable
private fun DoneStep(state: CollageUiState, phase: CollagePhase.Done, actions: CollageActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("Collage prêt", "Enregistré dans Galerie › Gulp")
        Spacer(Modifier.height(16.dp))
        CollageStage(state.photos, state.options, numbers = false)
        Spacer(Modifier.height(10.dp))
        Text(phase.saved.name, color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
    }
    MainButton(
        "Partager le collage",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
    ) { actions.onShare(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Retoucher", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
        SideButton("Galerie", Icons.Rounded.Image, Modifier.weight(1f)) { actions.onOpen(phase.saved) }
    }
}

@Composable
private fun terminalize(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text
