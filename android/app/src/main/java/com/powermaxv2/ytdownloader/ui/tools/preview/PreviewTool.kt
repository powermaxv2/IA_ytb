package com.powermaxv2.ytdownloader.ui.tools.preview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.convert.Category
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.preview.PreviewBody
import com.powermaxv2.ytdownloader.core.preview.PreviewDoc
import com.powermaxv2.ytdownloader.core.preview.PreviewFile
import com.powermaxv2.ytdownloader.core.preview.PreviewLoader
import com.powermaxv2.ytdownloader.core.preview.PreviewPhase
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val PREVIEW_TITLE = "Aperçu"
const val PREVIEW_COMMAND = "preview"
const val PREVIEW_SUBTITLE = "Regarde ce qu'il y a dans une image, une vidéo, un son, un PDF, un texte ou une archive ZIP."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PreviewActions(
    val onChooseFile: () -> Unit = {},
    val onConvert: (Uri) -> Unit = {},
    val onOpenWith: (PreviewFile) -> Unit = {},
    val onHash: () -> Unit = {},
)

/** One opening of a file: the same file picked twice opens twice. */
private data class OpenRequest(val uri: Uri, val serial: Int)

/**
 * The Aperçu (ToolRegistry.PREVIEW): a file picked here, shared to "Aperçu avec Gulp" or opened with Gulp is
 * read with caps and shown by the viewer of its kind. Nothing is saved; leaving the tool forgets the file.
 */
@Composable
fun PreviewTool(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<OpenRequest?>(null) }
    var phase by remember { mutableStateOf<PreviewPhase>(PreviewPhase.Idle) }
    var hash by remember { mutableStateOf(HashState()) }
    val hashJob = remember { arrayOfNulls<Job>(1) }

    fun open(uri: Uri) {
        request = OpenRequest(uri, (request?.serial ?: 0) + 1)
    }

    // A file can arrive while the tool is already open (Partager → Aperçu avec Gulp): keyed on the URI.
    LaunchedEffect(vm.pendingFile) {
        vm.consumePendingFile()?.let { open(it) }
    }
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        hashJob[0]?.cancel()
        hash = HashState()
        phase = PreviewPhase.Loading
        // Cancelled when another file comes in: a stale load never writes over the new one.
        phase = PreviewLoader.load(context, r.uri)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }
    val actions = remember {
        PreviewActions(
            onChooseFile = {
                try {
                    picker.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onConvert = { vm.openToolWithFile(ToolRegistry.CONVERTER, it) },
            onOpenWith = { openWith(context, it, vm) },
            onHash = {
                val file = (phase as? PreviewPhase.Ready)?.doc?.file
                if (file != null && hashJob[0]?.isActive != true) {
                    hashJob[0] = scope.launch {
                        hash = HashState(progress = 0f)
                        hash = try {
                            val value = PreviewLoader.sha256(context, file.uri, file.size) { p ->
                                // Called from the reading thread: compose state accepts it.
                                if (hash.value == null) hash = HashState(progress = p)
                            }
                            HashState(progress = 1f, value = value)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            HashState(error = "Lecture impossible : l'empreinte n'a pas pu être calculée.")
                        }
                    }
                }
            },
        )
    }

    val scroll = rememberScrollState()
    LaunchedEffect(phase::class) { if (phase !is PreviewPhase.Idle) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = PREVIEW_TITLE, command = PREVIEW_COMMAND, subtitle = PREVIEW_SUBTITLE, scrollState = scroll) {
        PreviewContent(phase, actions, hash)
    }
}

private fun openWith(context: Context, file: PreviewFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce fichier", success = false) }
}

/** Steps of the screen, for the transitions. */
private enum class Step { IDLE, OPEN, FAILED }

/** Body of the tool for a [phase] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.PreviewContent(
    phase: PreviewPhase,
    actions: PreviewActions,
    hash: HashState = HashState(),
    infoInitiallyOpen: Boolean = false,
) {
    val step = when (phase) {
        PreviewPhase.Idle -> Step.IDLE
        is PreviewPhase.Failed -> Step.FAILED
        else -> Step.OPEN
    }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "previewStep",
        modifier = Modifier.fillMaxWidth(),
    ) { s ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (s) {
                Step.IDLE -> IdleStep(actions)
                Step.FAILED -> FailedStep((phase as? PreviewPhase.Failed)?.message.orEmpty(), actions)
                Step.OPEN -> OpenStep((phase as? PreviewPhase.Ready)?.doc, actions, hash, infoInitiallyOpen)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: PreviewActions) {
    FileDropZone(
        title = "Choisir un fichier",
        hint = "Image, vidéo, son, PDF, texte, code, CSV ou archive ZIP",
        icon = Icons.Rounded.UploadFile,
        onClick = actions.onChooseFile,
    )
    ShowsCard()
}

/** The file card stays while the viewer swaps from the loading skeleton to the content. */
@Composable
private fun OpenStep(doc: PreviewDoc?, actions: PreviewActions, hash: HashState, infoInitiallyOpen: Boolean) {
    InfoCard(doc?.file, doc?.body, hash, actions.onHash, initiallyOpen = infoInitiallyOpen)
    AnimatedContent(
        targetState = doc,
        contentKey = { it?.file?.uri },
        transitionSpec = {
            // The viewer grows out of the file card above it.
            (fadeIn(tween(240)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 260f), initialScale = 0.86f, transformOrigin = TransformOrigin(0.5f, 0f))) togetherWith
                fadeOut(tween(140))
        },
        label = "previewViewer",
        modifier = Modifier.fillMaxWidth(),
    ) { d ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d == null) LoadingViewer() else Viewer(d)
        }
    }
    AnimatedVisibility(
        visible = doc != null,
        enter = fadeIn(tween(300, delayMillis = 180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 },
    ) {
        if (doc != null) ActionsBlock(doc, actions)
    }
}

@Composable
private fun LoadingViewer() {
    ViewerFrame {
        Column(
            Modifier
                .fillMaxSize()
                .shimmer()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0.9f, 0.6f, 0.75f, 0.4f, 0.85f, 0.55f, 0.7f).forEach { SkeletonLine(it, 12.dp) }
        }
    }
}

@Composable
private fun Viewer(doc: PreviewDoc) {
    val body = doc.body
    when (body) {
        is PreviewBody.Text -> {
            if (body.cut) NoteBanner(CUT_NOTE)
            body.note?.let { NoteBanner(it) }
            TextViewer(body)
        }
        is PreviewBody.Code -> {
            if (body.cut) NoteBanner(CUT_NOTE)
            body.note?.let { NoteBanner(it) }
            CodeViewer(body)
        }
        is PreviewBody.Markdown -> {
            if (body.cut) NoteBanner(CUT_NOTE)
            MarkdownViewer(body)
        }
        is PreviewBody.Table -> {
            if (body.cut) NoteBanner("Aperçu limité au premier Mo et aux 500 premières lignes")
            TableViewer(body)
        }
        is PreviewBody.Zip -> ZipViewer(body)
        PreviewBody.Image -> ImageViewer(doc.file)
        PreviewBody.Video -> VideoViewer(doc.file)
        PreviewBody.Audio -> AudioViewer(doc.file)
        PreviewBody.Pdf -> PdfViewer(doc.file)
        PreviewBody.Other -> OtherViewer(doc.file)
        is PreviewBody.Notice -> NoticeViewer(body.message)
    }
}

private const val CUT_NOTE = "Aperçu limité au premier Mo"

@Composable
private fun OtherViewer(file: PreviewFile) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KindTile(previewIcon(FileKind.OTHER), size = 56.dp)
        Spacer(Modifier.padding(top = 12.dp))
        Text(
            if (terminal) "FORMAT NON AFFICHABLE" else "Gulp ne sait pas afficher ce type de fichier",
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            "Les détails ci-dessus disent ce qu'il contient ; « Ouvrir avec… » le confie à une autre app.",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoticeViewer(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ActionsBlock(doc: PreviewDoc, actions: PreviewActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (Category.of(doc.file.kind) != null) {
            MainButton(
                "Convertir",
                subtitle = "Ouvre le Convertisseur avec ce fichier",
                icon = Icons.Rounded.SwapHoriz,
                modifier = Modifier.staggeredEntrance(0, key = doc.file.uri, baseDelay = 200),
            ) { actions.onConvert(doc.file.uri) }
        }
        SideButton(
            "Ouvrir avec…",
            Icons.Rounded.OpenInNew,
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(1, key = doc.file.uri, baseDelay = 200),
        ) { actions.onOpenWith(doc.file) }
        SideButton(
            "Choisir un autre fichier",
            Icons.Rounded.UploadFile,
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(2, key = doc.file.uri, baseDelay = 200),
            onClick = actions.onChooseFile,
        )
    }
}

@Composable
private fun FailedStep(message: String, actions: PreviewActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (terminal) "ERREUR" else "Aperçu impossible",
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.padding(top = 8.dp))
        Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChooseFile)
}

/** What the Aperçu shows, on the empty screen. */
@Composable
private fun ShowsCard() {
    val rows = listOf(
        Triple(FileKind.IMAGE, "Image", "JPG, PNG, WebP, GIF animé, HEIC… zoom au doigt"),
        Triple(FileKind.VIDEO, "Vidéo et son", "Lecture, et la forme d'onde du son"),
        Triple(FileKind.PDF, "PDF", "Page par page, avec le zoom"),
        Triple(FileKind.CODE, "Texte et code", "Coloration Kotlin, Java, JS, Python, XML, YAML… (premier Mo)"),
        Triple(FileKind.CSV, "CSV, JSON, Markdown", "Tableau des 500 premières lignes, JSON indenté, Markdown mis en page"),
        Triple(FileKind.ZIP, "Archive ZIP", "La liste du contenu, sans rien extraire"),
    )
    val skin = LocalSkin.current
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Ce que l'Aperçu affiche")
        rows.forEachIndexed { index, (kind, title, detail) ->
            Row(Modifier.staggeredEntrance(index, baseDelay = 260), verticalAlignment = Alignment.CenterVertically) {
                KindTile(previewIcon(kind), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (skin == Skin.TERMINAL) title.uppercase() else title,
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
                }
            }
        }
        Row(Modifier.staggeredEntrance(rows.size, baseDelay = 260), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Tout se passe sur le téléphone : rien n'est envoyé ni enregistré.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}
