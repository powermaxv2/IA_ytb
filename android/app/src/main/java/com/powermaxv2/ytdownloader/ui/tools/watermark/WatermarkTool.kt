package com.powermaxv2.ytdownloader.ui.tools.watermark

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
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
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.watermark.Watermark
import com.powermaxv2.ytdownloader.core.watermark.WatermarkController
import com.powermaxv2.ytdownloader.core.watermark.WatermarkMode
import com.powermaxv2.ytdownloader.core.watermark.WatermarkPhase
import com.powermaxv2.ytdownloader.core.watermark.WatermarkStyle
import com.powermaxv2.ytdownloader.core.watermark.WatermarkUiState
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
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import kotlin.math.roundToInt

const val WATERMARK_TITLE = "Filigrane"
const val WATERMARK_COMMAND = "watermark"
const val WATERMARK_SUBTITLE = "Écris « Copie pour location » en travers d'une image ou d'un PDF, pour qu'il ne serve qu'à ça."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class WatermarkActions(
    val onChoose: () -> Unit = {},
    val onStyle: (WatermarkStyle) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/**
 * The Filigrane (ToolRegistry id "watermark"): a see-through text across a photo or every page of a PDF, saved as a
 * new file. Its state lives in [WatermarkController.shared], so the work goes on when the user leaves this screen.
 */
@Composable
fun WatermarkTool(vm: MainViewModel) {
    val controller = remember { WatermarkController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(uri)) vm.toast("Un filigrane est en cours.", success = false)
    }
    // Android 9 and older: saving to Pictures / Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le fichier.", success = false)
    }

    val actions = WatermarkActions(
        onChoose = {
            try {
                picker.launch(MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onStyle = controller::setStyle,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onCancel = controller::cancel,
        onEdit = controller::edit,
        onReset = controller::reset,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, listOf(it), vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is WatermarkPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = WATERMARK_TITLE, command = WATERMARK_COMMAND, subtitle = WATERMARK_SUBTITLE, scrollState = scroll) {
        WatermarkContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/*", "application/pdf")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.WatermarkContent(state: WatermarkUiState, actions: WatermarkActions) {
    val image: ImageBitmap? = remember(state.preview) { state.preview?.asImageBitmap() }
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "watermarkPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                WatermarkPhase.Idle -> IdleStep(current, actions)
                WatermarkPhase.Reading -> ReadingStep()
                is WatermarkPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                WatermarkPhase.Ready -> ReadyStep(current, image, actions)
                is WatermarkPhase.Working -> WorkingStep(current, image, phase, actions)
                is WatermarkPhase.Done -> DoneStep(current, image, phase, actions)
                is WatermarkPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Le filigrane n'a pas été ajouté")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onEdit)
                        SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: WatermarkUiState, actions: WatermarkActions) {
    FileDropZone(
        title = "Choisir une image ou un PDF",
        hint = "Pièce d'identité, fiche de paie, avis d'impôt… JPG, PNG, WebP, HEIC ou PDF",
        icon = Icons.Rounded.BrandingWatermark,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Pourquoi un filigrane")
        Spacer(Modifier.height(12.dp))
        WatermarkPreview(null, 1.42f, state.style, state.label, loop = true)
        Spacer(Modifier.height(12.dp))
        Text(
            terminalAware("Une copie barrée « Copie pour location » ne peut pas être réutilisée pour un crédit ou un autre dossier."),
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Le fichier d'origine n'est jamais modifié : Gulp enregistre une copie avec le filigrane.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ReadingStep() {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(16.dp),
    ) {
        Spacer(Modifier.height(180.dp))
        Text("Ouverture du fichier…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ReadyStep(state: WatermarkUiState, image: ImageBitmap?, actions: WatermarkActions) {
    val source = state.source
    val style = state.style
    PreviewCard(state, image, index = 0)
    // Text.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Texte")
        Spacer(Modifier.height(10.dp))
        QrField(
            label = "Écrit en travers",
            value = style.text,
            onChange = { actions.onStyle(style.copy(text = it)) },
            placeholder = Watermark.DEFAULT_TEXT,
            maxLength = Watermark.MAX_TEXT,
        )
        Spacer(Modifier.height(10.dp))
        OptionChips(
            Watermark.presets,
            selected = Watermark.presets.firstOrNull { it == style.text.trim() } ?: "",
            label = { it },
            onSelect = { actions.onStyle(style.copy(text = it)) },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow("Ajouter la date", "« – ${state.date} » à la fin du texte", style.withDate) { actions.onStyle(style.copy(withDate = it)) }
    }
    // Look.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Disposition")
        Spacer(Modifier.height(10.dp))
        OptionChips(WatermarkMode.entries, style.mode, label = { it.label }, onSelect = { actions.onStyle(style.copy(mode = it)) })
        Spacer(Modifier.height(16.dp))
        BlockLabel("Couleur")
        Spacer(Modifier.height(6.dp))
        InkSwatches(style.ink, onSelect = { actions.onStyle(style.copy(ink = it)) })
        Spacer(Modifier.height(12.dp))
        SettingSlider(
            "Opacité",
            "${(style.opacity * 100).roundToInt()} %",
            style.opacity,
            Watermark.MIN_OPACITY..Watermark.MAX_OPACITY,
            onChange = { actions.onStyle(style.copy(opacity = it)) },
        )
        SettingSlider(
            "Taille",
            sizeName(style.size),
            style.size,
            Watermark.MIN_SIZE..Watermark.MAX_SIZE,
            onChange = { actions.onStyle(style.copy(size = it)) },
        )
        SettingSlider(
            "Angle",
            "${-style.angle.roundToInt()}°",
            -style.angle,
            -90f..90f,
            // Snaps to the usual angles (0°, 30°, 45°, 90°) when close.
            onChange = { actions.onStyle(style.copy(angle = -snapAngle(it))) },
        )
    }
    val where = savedWhere(source?.pdf == true)
    MainButton(
        "Ajouter le filigrane",
        subtitle = "Copie enregistrée dans $where",
        icon = Icons.Rounded.BrandingWatermark,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onStart,
    )
    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 120), onClick = actions.onReset)
}

/** The preview with the file's name and what it is under it. */
@Composable
private fun PreviewCard(state: WatermarkUiState, image: ImageBitmap?, index: Int, sweep: Boolean = false) {
    val source = state.source
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        val aspect = source?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1.42f
        WatermarkPreview(image, aspect, state.style, state.label, sweep = sweep)
        source?.let {
            Spacer(Modifier.height(10.dp))
            Text(it.file.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val size = it.file.size?.let { bytes -> " · " + Fmt.bytes(bytes.toDouble()) }.orEmpty()
            val what = if (it.pdf) "PDF · ${pagesText(it.pages)}" else "Image"
            Text(terminalAware(what + size + if (it.pdf && it.pages > 1) " · aperçu de la page 1" else ""), color = Palette.TextTertiary, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun WorkingStep(state: WatermarkUiState, image: ImageBitmap?, phase: WatermarkPhase.Working, actions: WatermarkActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "watermarkProgress",
    )
    PreviewCard(state, image, index = 0, sweep = true)
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val step = when {
            state.source?.pdf == true && phase.total > 1 -> "Page ${(phase.done + 1).coerceAtMost(phase.total)}/${phase.total}…"
            else -> "Filigrane en cours…"
        }
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (terminal) TerminalProgress(progress) else StampProgress(progress)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: WatermarkUiState, image: ImageBitmap?, phase: WatermarkPhase.Done, actions: WatermarkActions) {
    val saved = phase.saved
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        DoneHeader("Filigrane ajouté", "${saved.name}\nenregistré dans ${savedWhere(state.source?.pdf == true)}")
        Spacer(Modifier.height(14.dp))
        WatermarkPreview(image, state.source?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1.42f, state.style, state.label)
    }
    MainButton(
        if (state.source?.pdf == true) "Partager le PDF" else "Partager l'image",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
    ) { actions.onShare(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Ouvrir", Icons.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpen(saved) }
        SideButton("Modifier", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onEdit)
    }
    SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}

// MARK: - Words

@Composable
private fun terminalAware(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text

internal fun savedWhere(pdf: Boolean): String = if (pdf) "Téléchargements › Gulp" else "Galerie › Gulp"

internal fun pagesText(pages: Int): String = if (pages > 1) "$pages pages" else "1 page"

internal fun sizeName(size: Float): String = when {
    size < 0.045f -> "Petit"
    size < 0.075f -> "Moyen"
    size < 0.11f -> "Grand"
    else -> "Très grand"
}

/** The slider's angle (counter-clockwise, degrees), pulled onto 0, ±30, ±45, ±60 or ±90 within 3°. */
internal fun snapAngle(value: Float): Float {
    val near = listOf(-90f, -60f, -45f, -30f, 0f, 30f, 45f, 60f, 90f).minBy { kotlin.math.abs(it - value) }
    return if (kotlin.math.abs(near - value) <= 3f) near else value.roundToInt().toFloat()
}
