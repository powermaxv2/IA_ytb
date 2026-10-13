package com.powermaxv2.ytdownloader.ui.tools.reader

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.reader.ChatPdf
import com.powermaxv2.ytdownloader.core.reader.ChatReader
import com.powermaxv2.ytdownloader.core.reader.ReaderChat
import com.powermaxv2.ytdownloader.core.reader.ReaderLoad
import com.powermaxv2.ytdownloader.core.reader.ReaderPhase
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val READER_TITLE = "Lecteur"
const val READER_COMMAND = "lire"
const val READER_SUBTITLE = "Relis une discussion WhatsApp exportée comme dans l'appli, cherche dedans et garde-la en PDF."

/** Types offered by the file picker: WhatsApp exports are a .txt, or a .zip when shared. */
private val ExportTypes = arrayOf("text/plain", "application/zip", "application/x-zip-compressed", "application/octet-stream")

/** Where the PDF of the discussion is. */
sealed interface PdfState {
    data object None : PdfState

    /** Being written: [progress] from 0 to 1. */
    data class Working(val progress: Float) : PdfState
    data class Done(val file: SavedFile) : PdfState
}

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ReaderActions(
    val onChooseFile: () -> Unit = {},
    val onSample: () -> Unit = {},
    val onMe: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    /** +1 = next match (newer), -1 = previous. */
    val onStep: (Int) -> Unit = {},
    val onMonth: (Int) -> Unit = {},
    val onPdf: () -> Unit = {},
    val onOpenPdf: () -> Unit = {},
    val onSharePdf: () -> Unit = {},
)

/** The discussion as the ready screen draws it (stateless: also drawn by the screenshot tests). */
data class ReaderUiState(
    val chat: ReaderChat,
    val me: String?,
    val query: String = "",
    /** Which match of the search is shown (index in the list of matches). */
    val match: Int = 0,
    val pdf: PdfState = PdfState.None,
)

/** One opening of a file: the same file picked twice is read twice. */
private data class ReaderRequest(val uri: Uri, val serial: Int)

/**
 * Lecteur de discussion (ToolRegistry.CHAT_READER): a WhatsApp export read back in bubbles, day by day, with a
 * search, a jump to any month and an export to PDF. Everything happens on the phone; nothing is kept.
 */
@Composable
fun ReaderTool(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<ReaderRequest?>(null) }
    var phase by remember { mutableStateOf<ReaderPhase>(ReaderPhase.Idle) }
    var me by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var match by remember { mutableStateOf(0) }
    var pdf by remember { mutableStateOf<PdfState>(PdfState.None) }
    var pdfJob by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()

    fun open(uri: Uri) {
        request = ReaderRequest(uri, (request?.serial ?: 0) + 1)
    }

    fun show(next: ReaderPhase) {
        pdfJob?.cancel()
        pdf = PdfState.None
        query = ""
        match = 0
        phase = next
        me = (next as? ReaderPhase.Ready)?.chat?.messages?.let { ChatReader.guessMe(it) }
    }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        show(ReaderPhase.Loading)
        // Cancelled when another file comes in: a stale read never writes over the new one.
        show(ReaderLoad.load(context, r.uri))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }
    val chooseFile = {
        try {
            picker.launch(ExportTypes)
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }

    val ready = phase as? ReaderPhase.Ready
    if (ready == null) {
        val scroll = rememberScrollState()
        ToolScaffold(vm, title = READER_TITLE, command = READER_COMMAND, subtitle = READER_SUBTITLE, scrollState = scroll) {
            ReaderStartContent(
                phase,
                ReaderActions(onChooseFile = chooseFile, onSample = {
                    request = null
                    show(ReaderLoad.sample())
                }),
            )
        }
    } else {
        val chat = ready.chat
        val actions = ReaderActions(
            onChooseFile = chooseFile,
            onSample = {
                request = null
                show(ReaderLoad.sample())
            },
            onMe = {
                me = it
                pdf = PdfState.None
            },
            onQuery = {
                query = it.take(80)
                match = 0
            },
            onStep = { match += it },
            onMonth = { first ->
                scope.launch { list.animateScrollToItem(ReaderList.HEAD_ITEMS + ChatReader.rowOf(ChatReader.rows(chat.messages, me), first)) }
            },
            onPdf = {
                if (pdf !is PdfState.Working) {
                    pdfJob = scope.launch {
                        pdf = PdfState.Working(0f)
                        pdf = try {
                            PdfState.Done(ChatPdf.export(context, chat, me) { p -> pdf = PdfState.Working(p) })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            vm.toast("PDF impossible : ${e.message ?: "erreur"}", success = false)
                            PdfState.None
                        }
                        (pdf as? PdfState.Done)?.let { vm.toast("PDF enregistré dans ${it.file.location}") }
                    }
                }
            },
            onOpenPdf = { (pdf as? PdfState.Done)?.let { openSavedFile(context, it.file, vm) } },
            onSharePdf = { (pdf as? PdfState.Done)?.let { shareSavedFiles(context, listOf(it.file), vm) } },
        )

        val insets = WindowInsets.systemBars.asPaddingValues()
        ReaderScreen(
            state = ReaderUiState(chat, me, query, match, pdf),
            actions = actions,
            listState = list,
            onHome = vm::goHome,
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        )
    }
}

/** Before a discussion is open: pick a file (or the example), the reading, or why it failed. */
@Composable
fun ColumnScope.ReaderStartContent(phase: ReaderPhase, actions: ReaderActions) {
    AnimatedContent(
        targetState = phase,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "readerStep",
        modifier = Modifier.fillMaxWidth(),
    ) { p ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (p) {
                ReaderPhase.Loading -> ReaderLoading()
                is ReaderPhase.Failed -> {
                    ToolErrorCard(p.message)
                    FileDropZone(
                        title = "Choisir un autre export",
                        hint = "Le fichier .txt ou .zip d'une discussion WhatsApp",
                        icon = Icons.Rounded.UploadFile,
                        onClick = actions.onChooseFile,
                    )
                }
                else -> {
                    FileDropZone(
                        title = "Choisir un export",
                        hint = "Fichier .txt ou .zip d'une discussion WhatsApp",
                        icon = Icons.Rounded.UploadFile,
                        onClick = actions.onChooseFile,
                    )
                    ReaderDemo(Modifier.staggeredEntrance(3, baseDelay = 160))
                    SideButton("Voir un exemple", Icons.Rounded.AutoAwesome, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 160), onClick = actions.onSample)
                    ReaderHowTo(Modifier.staggeredEntrance(5, baseDelay = 160))
                }
            }
        }
    }
}
