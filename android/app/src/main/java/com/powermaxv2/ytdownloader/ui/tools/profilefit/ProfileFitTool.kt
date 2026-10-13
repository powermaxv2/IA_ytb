package com.powermaxv2.ytdownloader.ui.tools.profilefit

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.profilefit.FitMargin
import com.powermaxv2.ytdownloader.core.profilefit.FitOptions
import com.powermaxv2.ytdownloader.core.profilefit.FitPhase
import com.powermaxv2.ytdownloader.core.profilefit.FitShape
import com.powermaxv2.ytdownloader.core.profilefit.FitUiState
import com.powermaxv2.ytdownloader.core.profilefit.ProfileFit
import com.powermaxv2.ytdownloader.core.profilefit.ProfileFitHolder
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

const val PROFILE_FIT_TITLE = "Photo de profil"
const val PROFILE_FIT_COMMAND = "profile"
const val PROFILE_FIT_SUBTITLE = "Ta photo en entier dans le rond WhatsApp, sur un fond flou ou uni."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class FitActions(
    val onChoose: () -> Unit = {},
    val onOptions: (FitOptions) -> Unit = {},
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/**
 * Photo de profil entière (ToolRegistry.PROFILE_FIT): a portrait or a group photo, shrunk whole into WhatsApp's
 * round on a backdrop, saved as a square photo to pick in WhatsApp. State in [ProfileFitHolder], so the photo
 * and its settings are still there after a trip home.
 */
@Composable
fun ProfileFitTool(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = remember { ProfileFitHolder.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(uri)) vm.toast("Un enregistrement est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.save() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la photo.", success = false)
    }

    val actions = FitActions(
        onChoose = {
            try {
                picker.launch(MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
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
    LaunchedEffect(state.phase::class) { if (state.phase != FitPhase.Ready && state.phase != FitPhase.Saving) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = PROFILE_FIT_TITLE, command = PROFILE_FIT_COMMAND, subtitle = PROFILE_FIT_SUBTITLE, scrollState = scroll) {
        ProfileFitContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ProfileFitContent(state: FitUiState, actions: FitActions) {
    AnimatedContent(
        targetState = state,
        // Saving stays on the settings screen (the button and the ring change), without a page transition.
        contentKey = { if (it.phase == FitPhase.Saving) FitPhase.Ready::class else it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "profileFitPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                FitPhase.Idle -> IdleStep(actions)
                FitPhase.Reading -> ReadingStep()
                is FitPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre photo", Icons.Rounded.AccountCircle, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                FitPhase.Ready, FitPhase.Saving -> ReadyStep(current, actions)
                is FitPhase.Done -> DoneStep(current, phase, actions)
                is FitPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La photo n'a pas été enregistrée")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onSave)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
                        SideButton("Autre photo", Icons.Rounded.AccountCircle, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: FitActions) {
    FileDropZone(
        title = "Choisir une photo",
        hint = "Portrait, groupe, paysage : elle rentre en entier dans le rond",
        icon = Icons.Rounded.AccountCircle,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Pourquoi ta photo est coupée")
        Spacer(Modifier.height(12.dp))
        FitDemo()
        Spacer(Modifier.height(10.dp))
        Text(
            terminalize("WhatsApp garde le carré du milieu et le montre dans un rond : les côtés et les coins disparaissent. Gulp réduit la photo pour qu'elle tienne en entier."),
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
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Palette.TextPrimary.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(terminalize("Lecture de la photo…"), color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReadyStep(state: FitUiState, actions: FitActions) {
    val photo = state.photo ?: return
    val saving = state.phase == FitPhase.Saving
    val options = state.options
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileStage(state.preview, busy = saving)
        Spacer(Modifier.height(16.dp))
        FitCompare(
            photo,
            state.preview,
            hidden = ProfileFit.hiddenByWhatsApp(photo.width, photo.height),
            shown = ProfileFit.shownByGulp(options.shape, photo.width, photo.height),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Fond")
        Spacer(Modifier.height(10.dp))
        BackdropSwatches(photo, options.backdrop, { actions.onOptions(options.copy(backdrop = it)) })
        Spacer(Modifier.height(16.dp))
        BlockLabel("La photo tient")
        Spacer(Modifier.height(8.dp))
        OptionChips(FitShape.entries, options.shape, { it.label }, { actions.onOptions(options.copy(shape = it)) })
        Spacer(Modifier.height(16.dp))
        BlockLabel("Marge")
        Spacer(Modifier.height(8.dp))
        OptionChips(FitMargin.entries, options.margin, { it.label }, { actions.onOptions(options.copy(margin = it)) })
    }
    MainButton(
        if (saving) "Enregistrement…" else "Enregistrer dans la Galerie",
        subtitle = "Carré ${ProfileFit.OUTPUT_SIDE} × ${ProfileFit.OUTPUT_SIDE}, en JPG",
        icon = Icons.Rounded.Download,
        enabled = !saving,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 120),
        onClick = actions.onSave,
    )
    SideButton("Une autre photo", Icons.Rounded.AccountCircle, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 120), onClick = actions.onReset)
}

@Composable
private fun DoneStep(state: FitUiState, phase: FitPhase.Done, actions: FitActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("Photo de profil prête", "Enregistrée dans Galerie › Gulp")
        Spacer(Modifier.height(16.dp))
        ProfileStage(state.preview, maxSide = 220.dp)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Dans WhatsApp")
        listOf(
            "Ouvre Réglages, puis touche ta photo.",
            "Touche l'appareil photo, puis Galerie.",
            "Choisis « ${phase.saved.name} » dans l'album Gulp et valide sans recadrer.",
        ).forEachIndexed { index, line -> StepLine(index + 1, line) }
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
private fun StepLine(number: Int, text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(Modifier.staggeredEntrance(number, baseDelay = 220), verticalAlignment = Alignment.Top) {
        Text(
            if (terminal) "$number>" else "$number.",
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(22.dp),
        )
        Text(text, color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Start)
    }
}

@Composable
private fun terminalize(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text
