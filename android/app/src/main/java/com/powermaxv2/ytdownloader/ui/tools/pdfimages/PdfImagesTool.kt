package com.powermaxv2.ytdownloader.ui.tools.pdfimages

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
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import com.powermaxv2.ytdownloader.core.pdfimages.ImagesFormat
import com.powermaxv2.ytdownloader.core.pdfimages.ImagesMode
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesController
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesPhase
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesPlan
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesUiState
import com.powermaxv2.ytdownloader.core.pdfimages.Sharpness
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
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.FannedPages
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val PDF_IMAGES_TITLE = "PDF en images"
const val PDF_IMAGES_COMMAND = "pdf2img"
const val PDF_IMAGES_SUBTITLE = "Chaque page d'un PDF en image JPG ou PNG, ou les images qu'il contient, à envoyer ou retoucher."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PdfImagesActions(
    val onChoose: () -> Unit = {},
    val onToggle: (Int) -> Unit = {},
    val onAll: () -> Unit = {},
    val onNone: () -> Unit = {},
    val onPagesText: (String) -> Unit = {},
    val onMode: (ImagesMode) -> Unit = {},
    val onFormat: (ImagesFormat) -> Unit = {},
    val onSharpness: (Sharpness) -> Unit = {},
    val onZip: (Boolean) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * PDF en images (ToolRegistry id "pdf-images"): pages of a PDF drawn as JPG / PNG pictures, or the pictures placed
 * inside them taken out. Its state lives in [PdfImagesController.shared], so the work goes on off-screen.
 */
@Composable
fun PdfImagesTool(vm: MainViewModel) {
    val controller = remember { PdfImagesController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val thumbs by controller.thumbs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(uri)) vm.toast("Des images sont en cours de création.", success = false)
    }
    // Android 9 and older: saving to Pictures / Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les images.", success = false)
    }

    val actions = PdfImagesActions(
        onChoose = {
            try {
                picker.launch(arrayOf("application/pdf"))
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onToggle = controller::toggle,
        onAll = controller::selectAll,
        onNone = controller::selectNone,
        onPagesText = controller::setPagesText,
        onMode = controller::setMode,
        onFormat = controller::setFormat,
        onSharpness = controller::setSharpness,
        onZip = controller::setZip,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onCancel = controller::cancel,
        onEdit = controller::edit,
        onReset = controller::reset,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, it, vm) },
    )

    val images = remember(thumbs) { thumbs.mapValues { it.value.asImageBitmap() } }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is PdfImagesPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = PDF_IMAGES_TITLE, command = PDF_IMAGES_COMMAND, subtitle = PDF_IMAGES_SUBTITLE, scrollState = scroll) {
        PdfImagesContent(state, images, actions)
    }
}

/** Body of the tool for a [state] and the drawn [thumbs] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.PdfImagesContent(state: PdfImagesUiState, thumbs: Map<Int, ImageBitmap>, actions: PdfImagesActions) {
    val covers = remember(state.covers) { state.covers.map { it.asImageBitmap() } }
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "pdfImagesPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                PdfImagesPhase.Idle -> IdleStep(actions)
                PdfImagesPhase.Reading -> ReadingStep()
                is PdfImagesPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir un autre PDF", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                PdfImagesPhase.Ready -> ReadyStep(current, thumbs, actions)
                is PdfImagesPhase.Working -> WorkingStep(current, thumbs, phase, actions)
                is PdfImagesPhase.Done -> DoneStep(current, covers, phase, actions)
                PdfImagesPhase.NothingInside -> {
                    ToolErrorCard(
                        "Ces pages ne contiennent aucune image : leur contenu est du texte ou du dessin. Choisis « Pages entières » pour en faire des images.",
                        title = "Aucune image trouvée",
                    )
                    MainButton("Prendre les pages entières", icon = Icons.Rounded.Collections) {
                        actions.onEdit()
                        actions.onMode(ImagesMode.PAGES)
                    }
                    SideButton("Autre PDF", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
                }
                is PdfImagesPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Les images n'ont pas été créées")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onEdit)
                        SideButton("Autre PDF", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: PdfImagesActions) {
    FileDropZone(
        title = "Choisir un PDF",
        hint = "Facture, billet, plan, catalogue, cours… jusqu'à ${PdfImagesController.MAX_PAGES} pages",
        icon = Icons.Rounded.PictureAsPdf,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ce que fait l'outil")
        Spacer(Modifier.height(8.dp))
        PageBurst()
        Spacer(Modifier.height(8.dp))
        Text(
            terminalAware("Un billet ou une page de cours en image se partage partout : WhatsApp, statut, Instagram, galerie."),
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Le PDF n'est jamais modifié.",
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
        Text("Ouverture du PDF…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ReadyStep(state: PdfImagesUiState, thumbs: Map<Int, ImageBitmap>, actions: PdfImagesActions) {
    val source = state.source ?: return
    val terminal = LocalSkin.current == Skin.TERMINAL
    // What comes out.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val size = source.file.size?.let { " · " + Fmt.bytes(it.toDouble()) }.orEmpty()
        Text(terminalAware("PDF · ${PdfImagesPlan.pagesText(source.count)}$size"), color = Palette.TextTertiary, fontSize = 12.5.sp)
        Spacer(Modifier.height(14.dp))
        BlockLabel("Sortir")
        Spacer(Modifier.height(8.dp))
        OptionChips(ImagesMode.entries, state.mode, label = { it.label }, onSelect = actions.onMode)
        Spacer(Modifier.height(6.dp))
        Text(
            terminalAware(
                if (state.mode == ImagesMode.PAGES) {
                    "Une image par page, telle qu'on la voit."
                } else {
                    "Les photos et dessins posés dans les pages, à leur résolution d'origine (sans le texte autour)."
                },
            ),
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
        )
    }
    // Pages.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockLabel("Pages", Modifier.weight(1f))
            val count by animateFloatAsState(state.selected.size.toFloat(), spring(stiffness = 300f), label = "chosenCount")
            Text(
                "${count.toInt()} / ${source.count}",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Toutes", null, Modifier.weight(1f), onClick = actions.onAll)
            SideButton("Aucune", null, Modifier.weight(1f), onClick = actions.onNone)
        }
        Spacer(Modifier.height(10.dp))
        QrField(
            label = if (state.pagesError) "Pages (ex. 1-3, 7) : page inexistante" else "Pages (ex. 1-3, 7)",
            value = state.pagesText,
            onChange = actions.onPagesText,
            placeholder = "1-${source.count}",
            maxLength = 200,
        )
        Spacer(Modifier.height(14.dp))
        PageGrid(source.pages.take(PdfImagesController.MAX_THUMBS), thumbs, state.selected, actions.onToggle)
        if (source.count > PdfImagesController.MAX_THUMBS) {
            Spacer(Modifier.height(10.dp))
            Text(
                terminalAware("Les pages après la ${PdfImagesController.MAX_THUMBS}e se choisissent avec le champ « Pages »."),
                color = Palette.TextTertiary,
                fontSize = 12.5.sp,
            )
        }
    }
    // Format.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Format")
        Spacer(Modifier.height(8.dp))
        OptionChips(ImagesFormat.entries, state.format, label = { it.label }, onSelect = actions.onFormat)
        Spacer(Modifier.height(6.dp))
        Text(
            terminalAware(
                when {
                    state.mode == ImagesMode.EMBEDDED -> "Les images déjà en JPG sont copiées telles quelles, sans perte."
                    state.format == ImagesFormat.JPG -> "Léger, lu partout."
                    else -> "Sans perte, net pour le texte, plus lourd."
                },
            ),
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
        )
        AnimatedVisibility(
            state.mode == ImagesMode.PAGES,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                BlockLabel("Netteté")
                Spacer(Modifier.height(8.dp))
                OptionChips(Sharpness.entries, state.sharpness, label = { it.label }, onSelect = actions.onSharpness)
                Spacer(Modifier.height(6.dp))
                val size = state.firstSize?.let { (w, h) -> " · $w × $h px" }.orEmpty()
                Text(terminalAware("${state.sharpness.dpi} ppp, ${state.sharpness.detail}$size"), color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow("Tout dans un ZIP", "Un seul fichier dans Téléchargements › Gulp, au lieu d'une image par page dans la Galerie", state.zip, actions.onZip)
    }
    val chosen = state.selected.size
    val what = if (state.mode == ImagesMode.PAGES) "Créer ${PdfImagesPlan.imagesText(chosen)}" else "Extraire les images"
    MainButton(
        if (chosen == 0) "Choisis au moins une page" else what,
        subtitle = if (chosen == 0) null else "Enregistré dans ${savedWhere(state.zip)}",
        icon = Icons.Rounded.Collections,
        enabled = chosen > 0 && !state.pagesError,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onStart,
    )
    SideButton(
        if (terminal) "Autre PDF" else "Choisir un autre PDF",
        Icons.Rounded.UploadFile,
        Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 120),
        onClick = actions.onReset,
    )
}

@Composable
private fun WorkingStep(state: PdfImagesUiState, thumbs: Map<Int, ImageBitmap>, phase: PdfImagesPhase.Working, actions: PdfImagesActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "pdfImagesProgress",
    )
    val page = state.source?.pages?.getOrNull(phase.page - 1)
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DevelopingPage(
            thumb = thumbs[phase.page],
            aspect = page?.let { it.widthPt / it.heightPt.coerceAtLeast(1f) } ?: 0.707f,
            done = phase.done,
            format = state.format,
        )
        Spacer(Modifier.height(16.dp))
        val step = if (state.mode == ImagesMode.PAGES) {
            "Page ${phase.page} · ${(phase.done + 1).coerceAtMost(phase.total)}/${phase.total}"
        } else {
            "Recherche dans la page ${phase.page}…"
        }
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        if (terminal) TerminalProgress(progress) else com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing(progress, size = 150.dp)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: PdfImagesUiState, covers: List<ImageBitmap>, phase: PdfImagesPhase.Done, actions: PdfImagesActions) {
    val saved = phase.saved
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        val title = if (phase.pictures > 1) "${phase.pictures} images créées" else "1 image créée"
        val where = if (phase.zip) "${saved.firstOrNull()?.name}\ndans ${savedWhere(true)}" else "dans ${savedWhere(false)}"
        DoneHeader(title, where)
        Spacer(Modifier.height(10.dp))
        FannedPages(covers.ifEmpty { listOf(null, null, null).take(phase.pictures.coerceIn(1, 3)) })
    }
    MainButton(
        if (phase.zip) "Partager le ZIP" else if (saved.size > 1) "Partager les images" else "Partager l'image",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
    ) { actions.onShare(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        saved.firstOrNull()?.let { first ->
            SideButton("Ouvrir", Icons.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpen(first) }
        }
        SideButton("Autres pages", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onEdit)
    }
    SideButton("Autre PDF", Icons.Rounded.UploadFile, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}

// MARK: - Words

@Composable
private fun terminalAware(text: String): String = if (LocalSkin.current == Skin.TERMINAL) "# $text" else text

internal fun savedWhere(zip: Boolean): String = if (zip) "Téléchargements › Gulp" else "Galerie › Gulp"
