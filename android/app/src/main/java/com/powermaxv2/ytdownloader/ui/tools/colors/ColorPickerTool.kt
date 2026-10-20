package com.powermaxv2.ytdownloader.ui.tools.colors

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.colors.ColorFormat
import com.powermaxv2.ytdownloader.core.colors.ColorMath
import com.powermaxv2.ytdownloader.core.colors.ColorPickerHolder
import com.powermaxv2.ytdownloader.core.colors.PaletteExtractor
import com.powermaxv2.ytdownloader.core.colors.PickPhase
import com.powermaxv2.ytdownloader.core.colors.PickUiState
import com.powermaxv2.ytdownloader.core.colors.Swatch
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile

const val COLOR_PICKER_TITLE = "Pipette"
const val COLOR_PICKER_COMMAND = "pipette"
const val COLOR_PICKER_SUBTITLE = "Touche une photo pour prendre une couleur, et récupère toute sa palette."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PickActions(
    val onChoose: () -> Unit = {},
    val onTouch: (Float, Float) -> Unit = { _, _ -> },
    val onSelect: (Swatch) -> Unit = {},
    val onRecall: (Int) -> Unit = {},
    val onForget: (Int) -> Unit = {},
    val onKeep: () -> Unit = {},
    val onCopy: (ColorFormat) -> Unit = {},
    val onShare: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onReset: () -> Unit = {},
)

/**
 * Pipette (ToolRegistry.COLOR_PICKER): a photo from the phone, touched or swept with the finger to read a colour
 * (HEX, RVB, TSL, CMJN, copied in one touch), its main colours as a palette, kept colours, and the palette saved
 * as a picture. Everything stays on the phone. State in [ColorPickerHolder].
 */
@Composable
fun ColorPickerTool(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = remember { ColorPickerHolder.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.pick(uri)
    }
    val export = {
        controller.export { saved, error ->
            if (saved != null) vm.toast("Palette enregistrée dans Galerie › Gulp") else vm.toast(error ?: "L'enregistrement a échoué.", success = false)
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la palette.", success = false)
    }

    val actions = PickActions(
        onChoose = {
            try {
                picker.launch(MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onTouch = controller::touch,
        onSelect = controller::select,
        onRecall = controller::recall,
        onForget = {
            controller.forget(it)
            vm.toast("${ColorMath.hex(it)} retirée")
        },
        onKeep = {
            val already = state.color in state.kept
            controller.keep()
            if (!already) vm.toast("${ColorMath.hex(state.color)} gardée")
        },
        onCopy = {
            copy(context, it.value)
            vm.toast("${it.label} copié : ${it.value}")
        },
        onShare = { shareCodes(context, state, vm) },
        onExport = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export()
        },
        onOpen = { openSavedFile(context, it, vm) },
        onReset = controller::reset,
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase != PickPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = COLOR_PICKER_TITLE, command = COLOR_PICKER_COMMAND, subtitle = COLOR_PICKER_SUBTITLE, scrollState = scroll) {
        ColorPickerContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/gif", "image/bmp")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ColorPickerContent(state: PickUiState, actions: PickActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "colorPickerPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                PickPhase.Idle -> IdleStep(current, actions)
                PickPhase.Reading -> ReadingStep()
                is PickPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre photo", Icons.Rounded.Image, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                PickPhase.Ready -> ReadyStep(current, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: PickUiState, actions: PickActions) {
    FileDropZone(
        title = "Choisir une photo",
        hint = "Puis touche-la ou glisse le doigt dessus pour lire ses couleurs",
        icon = Icons.Rounded.Colorize,
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
        PipetteDemo()
        Spacer(Modifier.height(10.dp))
        Text(
            terminalize("Pose le doigt sur la photo : une loupe montre les pixels dessous et la couleur s'affiche en HEX, RVB, TSL et CMJN. Gulp sort aussi les couleurs principales de l'image."),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
    if (state.kept.isNotEmpty()) KeptCard(state, actions, index = 4)
    Hint(
        "Tout se passe sur le téléphone, hors ligne. La photo n'est pas modifiée.",
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
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.TextPrimary.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(terminalize("Lecture des couleurs…"), color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReadyStep(state: PickUiState, actions: PickActions) {
    val photo = state.photo ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PickStage(photo, state.x, state.y, state.color, actions.onTouch)
        Spacer(Modifier.height(10.dp))
        Hint("Touche ou glisse le doigt sur la photo")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        ColorHero(state.color)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Copier", Icons.Rounded.ContentCopy, Modifier.weight(1f)) { actions.onCopy(ColorMath.formats(state.color).first()) }
            val kept = state.color in state.kept
            SideButton(if (kept) "Gardée" else "Garder", if (kept) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, Modifier.weight(1f), onClick = actions.onKeep)
        }
        Spacer(Modifier.height(16.dp))
        FormatRows(ColorMath.formats(state.color), actions.onCopy)
    }
    if (state.palette.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(2, baseDelay = 120)
                .toolCard()
                .padding(16.dp),
        ) {
            BlockLabel("Palette de la photo")
            Spacer(Modifier.height(12.dp))
            PaletteStrip(state.palette, state.color, actions.onSelect)
        }
    }
    AnimatedVisibility(state.kept.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        KeptCard(state, actions, index = 3)
    }
    MainButton(
        if (state.exporting) "Enregistrement…" else "Enregistrer la palette",
        subtitle = "Image PNG avec les codes, dans Galerie › Gulp",
        icon = Icons.Rounded.Download,
        enabled = !state.exporting && state.palette.isNotEmpty(),
        modifier = Modifier.staggeredEntrance(4, baseDelay = 120),
        onClick = actions.onExport,
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(5, baseDelay = 120), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager les codes", Icons.Rounded.Share, Modifier.weight(1f), onClick = actions.onShare)
        SideButton("Autre photo", Icons.Rounded.Image, Modifier.weight(1f), onClick = actions.onChoose)
    }
    state.exported?.let { saved ->
        SideButton("Voir la palette enregistrée", Icons.Rounded.Image, Modifier.fillMaxWidth()) { actions.onOpen(saved) }
    }
}

@Composable
private fun KeptCard(state: PickUiState, actions: PickActions, index: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 120)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Couleurs gardées")
        Spacer(Modifier.height(12.dp))
        KeptRow(state.kept, state.color, actions.onRecall, actions.onForget)
        Spacer(Modifier.height(8.dp))
        Text(terminalize("Touche pour la revoir, appui long pour la retirer."), color = Palette.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun terminalize(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text

// MARK: - Intents

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Couleur", text))
}

/** The palette and the picked colour as text, to any app. */
private fun shareCodes(context: Context, state: PickUiState, vm: MainViewModel) {
    val name = state.photo?.name?.substringBeforeLast('.') ?: "photo"
    val text = buildString {
        append("Couleur : ${ColorMath.hex(state.color)} (${ColorMath.name(state.color)})")
        if (state.palette.isNotEmpty()) {
            append("\n\nPalette de « $name » :\n")
            append(PaletteExtractor.asText(state.palette))
        }
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager les couleurs")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
