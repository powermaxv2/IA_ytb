package com.powermaxv2.ytdownloader.ui.tools.signpdf

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.signpdf.SignPhase
import com.powermaxv2.ytdownloader.core.signpdf.SignPlan
import com.powermaxv2.ytdownloader.core.signpdf.SignPoint
import com.powermaxv2.ytdownloader.core.signpdf.SignStamp
import com.powermaxv2.ytdownloader.core.signpdf.SignUiState
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
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val SIGN_TITLE = "Signature PDF"
const val SIGN_COMMAND = "sign"
const val SIGN_SUBTITLE = "Dessine ta signature une fois, pose-la sur n'importe quel PDF."

/** Pictures the screen shows: the page being signed and the saved signature (decoded by the tool, given by tests). */
data class SignImages(val page: ImageBitmap? = null, val signature: ImageBitmap? = null)

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class SignActions(
    val onDraw: () -> Unit = {},
    val onCloseDraw: () -> Unit = {},
    val onInk: (Int) -> Unit = {},
    /** Saves the drawn strokes (pad fractions, pad aspect); false when there is too little ink. */
    val onSaveSignature: (List<List<SignPoint>>, Float) -> Boolean = { _, _ -> true },
    val onDeleteSignature: () -> Unit = {},
    val onChoose: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onMove: (Float, Float) -> Unit = { _, _ -> },
    val onResize: (Float) -> Unit = {},
    val onScale: (Float) -> Unit = {},
    val onStamp: (SignStamp) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
    val onTooLittle: () -> Unit = {},
)

/**
 * Signature PDF (ToolRegistry.SIGN_PDF): the signature is drawn once with a finger and kept; a PDF is opened, the
 * signature placed on a page, and a signed copy saved in Download/Gulp. The state lives in [App.signPdf].
 */
@Composable
fun SignPdfTool(vm: MainViewModel) {
    val controller = remember { App.instance.signPdf }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une signature est en cours d'enregistrement.", success = false)
    }
    // Android 9 and older: saving to Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le PDF.", success = false)
    }

    val signature by produceState<ImageBitmap?>(null, state.signature?.stamp) {
        value = state.signature?.let {
            withContext(Dispatchers.IO) { controller.signatureBitmap()?.asImageBitmap() }
        }
    }
    val page by produceState<ImageBitmap?>(null, state.doc, state.page) {
        value = null
        if (state.doc != null) value = controller.render(state.page, PAGE_RENDER_WIDTH)?.asImageBitmap()
    }

    val actions = SignActions(
        onDraw = controller::openPad,
        onCloseDraw = controller::closePad,
        onInk = controller::setInk,
        onSaveSignature = controller::saveSignature,
        onDeleteSignature = controller::deleteSignature,
        onChoose = {
            try {
                picker.launch(arrayOf("application/pdf"))
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onPage = controller::setPage,
        onMove = controller::move,
        onResize = controller::resize,
        onScale = controller::scale,
        onStamp = controller::setStamp,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onCancel = controller::cancel,
        onReset = controller::reset,
        onBack = controller::backToPage,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, listOf(it), vm) },
        onTooLittle = { vm.toast("Signe un peu plus grand, d'un trait.", success = false) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class, state.drawing) { if (state.phase !is SignPhase.Ready || state.drawing) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = SIGN_TITLE, command = SIGN_COMMAND, subtitle = SIGN_SUBTITLE, scrollState = scroll) {
        SignPdfContent(state, SignImages(page, signature), actions)
    }
}

/** Width the page is drawn at, in pixels: sharp on a phone, light on memory. */
private const val PAGE_RENDER_WIDTH = 1080

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.SignPdfContent(state: SignUiState, images: SignImages, actions: SignActions, today: LocalDate = LocalDate.now()) {
    AnimatedContent(
        targetState = state,
        contentKey = { if (it.drawing) "pad" else it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "signPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (current.drawing) {
                PadStep(current, actions)
            } else {
                when (val phase = current.phase) {
                    SignPhase.Idle -> IdleStep(current, images, actions)
                    SignPhase.Reading -> ReadingStep()
                    SignPhase.Ready -> ReadyStep(current, images, actions, today)
                    is SignPhase.Working -> WorkingStep(phase, actions)
                    is SignPhase.Done -> DoneStep(current, phase, actions)
                    is SignPhase.Failed -> FailedStep(current, phase, actions)
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: SignUiState, images: SignImages, actions: SignActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ma signature")
        Spacer(Modifier.height(12.dp))
        SignatureSheet(if (state.signature != null) images.signature else null)
        Spacer(Modifier.height(12.dp))
        if (state.signature == null) {
            val text = "Dessine-la une fois avec le doigt : Gulp la garde sur le téléphone pour tes prochains PDF."
            Text(if (terminal) "# $text" else text, color = Palette.TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            MainButton("Dessiner ma signature", icon = Icons.Rounded.Draw, onClick = actions.onDraw)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SideButton("Refaire", Icons.Rounded.Draw, Modifier.weight(1f), onClick = actions.onDraw)
                SideButton("Supprimer", Icons.Rounded.Delete, Modifier.weight(1f), onClick = actions.onDeleteSignature)
            }
        }
    }
    FileDropZone(
        title = "Choisir un PDF",
        hint = "Contrat, bail, devis, attestation… · 200 Mo au plus",
        icon = Icons.Rounded.PictureAsPdf,
        enabled = state.signature != null,
        onClick = actions.onChoose,
    )
    Hint(
        "Signature visuelle, comme au stylo sur papier : pas de certificat électronique. Le PDF d'origine n'est pas modifié, tout reste sur le téléphone.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun PadStep(state: SignUiState, actions: SignActions) {
    val strokes = remember { mutableStateListOf<List<SignPoint>>() }
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BlockLabel("Dessine ta signature", Modifier.weight(1f))
            InkPicker(state.ink, actions.onInk)
        }
        Spacer(Modifier.height(14.dp))
        SignaturePad(strokes, Color(state.ink))
        Spacer(Modifier.height(10.dp))
        val tip = "Tourne le téléphone pour plus de place. Le fond reste transparent sur le PDF."
        Text(if (terminal) "# $tip" else tip, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
    MainButton(
        "Garder cette signature",
        icon = Icons.Rounded.Check,
        enabled = strokes.isNotEmpty(),
        modifier = Modifier.staggeredEntrance(1, baseDelay = 80),
    ) {
        if (!actions.onSaveSignature(strokes.toList(), PAD_ASPECT)) actions.onTooLittle()
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 80), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Effacer un trait", Icons.AutoMirrored.Rounded.Undo, Modifier.weight(1f)) { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
        SideButton(if (state.signature != null) "Annuler" else "Plus tard", null, Modifier.weight(1f), onClick = actions.onCloseDraw)
    }
}

@Composable
private fun ReadingStep() {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(14.dp))
        Text("Ouverture du PDF…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ReadyStep(state: SignUiState, images: SignImages, actions: SignActions, today: LocalDate) {
    val doc = state.doc ?: return
    val terminal = LocalSkin.current == Skin.TERMINAL
    val caption = SignPlan.caption(state.stamp, today)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            doc.name,
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (doc.pages > 1) {
            Spacer(Modifier.height(8.dp))
            PageStepper(state.page, doc.pages, actions.onPage)
        }
        Spacer(Modifier.height(12.dp))
        PagePlacer(
            page = images.page,
            pageAspect = state.pageAspect,
            signature = images.signature,
            signatureAspect = state.signatureAspect,
            box = state.box,
            caption = caption,
            onMove = actions.onMove,
            onResize = actions.onResize,
            onScale = actions.onScale,
        )
        Spacer(Modifier.height(10.dp))
        val how = if (state.signature == null) "Dessine d'abord ta signature." else "Glisse la signature, tire le rond pour la grandir, ou touche la page pour l'y poser."
        Text(
            if (terminal) "# $how" else how,
            color = Palette.TextTertiary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Sous la signature")
        SwitchRow("Date du jour", "le ${today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", state.stamp.date) {
            actions.onStamp(state.stamp.copy(date = it))
        }
        InitialsField(state.stamp.initials, { actions.onStamp(state.stamp.copy(initials = it)) })
        AnimatedVisibility(
            doc.pages > 1 && state.stamp.cleanInitials.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchRow(
                "Parapher les autres pages",
                "Les initiales en bas à droite des ${doc.pages - 1} autres pages",
                state.stamp.initialsEverywhere,
            ) { actions.onStamp(state.stamp.copy(initialsEverywhere = it)) }
        }
    }
    if (state.signature == null) {
        MainButton("Dessiner ma signature", icon = Icons.Rounded.Draw, onClick = actions.onDraw)
    } else {
        MainButton(
            "Signer et enregistrer",
            subtitle = "Page ${state.page + 1} · Download › Gulp",
            icon = Icons.Rounded.Draw,
            modifier = Modifier.staggeredEntrance(2, baseDelay = 120),
            onClick = actions.onStart,
        )
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 120), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Ma signature", Icons.Rounded.Draw, Modifier.weight(1f), onClick = actions.onDraw)
        SideButton("Autre PDF", Icons.Rounded.PictureAsPdf, Modifier.weight(1f), onClick = actions.onChoose)
    }
}

@Composable
private fun WorkingStep(phase: SignPhase.Working, actions: SignActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(phase.progress, spring(dampingRatio = 0.9f, stiffness = 120f), label = "signProgress")
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PaperWhite)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            WritingPen(Modifier.fillMaxWidth().height(90.dp), periodMs = 1800)
        }
        Spacer(Modifier.height(20.dp))
        val step = if (phase.total > 1) "Signature… page ${phase.done.coerceAtLeast(1)}/${phase.total}" else "Signature…"
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        if (terminal) TerminalProgress(progress) else ProgressBar(progress)
    }
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
private fun DoneStep(state: SignUiState, phase: SignPhase.Done, actions: SignActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader("PDF signé", "enregistré dans ${phase.saved.location.replace("/", " › ")}")
        Spacer(Modifier.height(14.dp))
        val lines = buildList {
            add(phase.saved.name)
            add(if (phase.pages > 1) "Signé page ${phase.page + 1} sur ${phase.pages}" else "Signé")
            if (phase.paraphs > 0) add("Initiales sur ${phase.paraphs} autre${if (phase.paraphs > 1) "s" else ""} page${if (phase.paraphs > 1) "s" else ""}")
            if (state.stamp.date) add("Daté du jour")
        }
        lines.forEachIndexed { index, line ->
            Text(
                if (terminal) "> $line" else line,
                color = if (index == 0) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = if (index == 0) 14.sp else 13.sp,
                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .staggeredEntrance(index, baseDelay = 300),
            )
        }
    }
    MainButton("Partager le PDF signé", icon = Icons.Rounded.Share, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onShare(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Ouvrir", Icons.AutoMirrored.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpen(phase.saved) }
        SideButton("Autre PDF", Icons.Rounded.PictureAsPdf, Modifier.weight(1f), onClick = actions.onReset)
    }
    SideButton("Signer une autre page", Icons.Rounded.Draw, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onBack)
}

@Composable
private fun FailedStep(state: SignUiState, phase: SignPhase.Failed, actions: SignActions) {
    ToolErrorCard(phase.message, phase.debug, title = if (state.doc != null) "Le PDF n'a pas pu être signé" else "Ce PDF ne s'ouvre pas")
    if (state.doc != null) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Revenir à la page", null, Modifier.weight(1f), onClick = actions.onBack)
            SideButton("Autre PDF", Icons.Rounded.PictureAsPdf, Modifier.weight(1f), onClick = actions.onChoose)
        }
    } else {
        MainButton("Choisir un autre PDF", icon = Icons.Rounded.PictureAsPdf, onClick = actions.onChoose)
        SideButton("Retour", null, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}
