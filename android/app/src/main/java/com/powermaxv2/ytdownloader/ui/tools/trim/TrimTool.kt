package com.powermaxv2.ytdownloader.ui.tools.trim

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.powermaxv2.ytdownloader.core.trim.TrimOutput
import com.powermaxv2.ytdownloader.core.trim.TrimPhase
import com.powermaxv2.ytdownloader.core.trim.TrimPlan
import com.powermaxv2.ytdownloader.core.trim.TrimSource
import com.powermaxv2.ytdownloader.core.trim.TrimUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SizeBars
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val TRIM_TITLE = "Découpeur"
const val TRIM_COMMAND = "cut"
const val TRIM_SUBTITLE = "Garde juste le bon moment d'une vidéo ou d'un son, ou tires-en le son, une sonnerie ou un GIF."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class TrimActions(
    val onChoose: () -> Unit = {},
    val onStart: (Long) -> Unit = {},
    val onEnd: (Long) -> Unit = {},
    val onSettle: () -> Unit = {},
    val onOutput: (TrimOutput) -> Unit = {},
    val onExact: (Boolean) -> Unit = {},
    val onCut: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onAgain: (TrimOutput) -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/** The Découpeur (ToolRegistry.TRIMMER). Its state lives in [App.trimmer], so a cut keeps going off screen. */
@Composable
fun TrimTool(vm: MainViewModel) {
    val controller = remember { App.instance.trimmer }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(uri)) vm.toast("Une coupe est en cours : attendez-la ou annulez-la.", success = false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une coupe est en cours.", success = false)
    }
    // Android 9 and older: saving to Movies / Music needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le passage.", success = false)
    }
    val actions = remember(controller) {
        TrimActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("video/*", "audio/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onStart = controller::setStart,
            onEnd = controller::setEnd,
            onSettle = controller::settle,
            onOutput = controller::setOutput,
            onExact = controller::setExact,
            onCut = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onAgain = controller::again,
            onOpen = { openSavedFile(context, it, vm) },
            onShare = { shareSavedFiles(context, listOf(it), vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is TrimPhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = TRIM_TITLE, command = TRIM_COMMAND, subtitle = TRIM_SUBTITLE, scrollState = scroll) {
        TrimContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.TrimContent(state: TrimUiState, actions: TrimActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "trimPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                TrimPhase.Idle -> IdleStep(actions)
                TrimPhase.Reading -> ReadingStep()
                is TrimPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                TrimPhase.Ready -> current.source?.let { EditStep(current, it, actions) }
                is TrimPhase.Working -> WorkingStep(current, phase, actions)
                is TrimPhase.Done -> DoneStep(current, phase, actions)
                is TrimPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La coupe n'a pas abouti")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onCut)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: TrimActions) {
    FileDropZone(
        title = "Choisir une vidéo ou un son",
        hint = "MP4, MOV, MKV, WebM, MP3, M4A, WAV…",
        icon = Icons.Rounded.ContentCut,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ce qui peut en sortir")
        Spacer(Modifier.height(8.dp))
        OutputRow(Icons.Rounded.Movie, "Vidéo", "instantané, même qualité", 0)
        OutputRow(Icons.Rounded.MusicNote, "Son seul", "M4A, ou le format d'origine", 1)
        OutputRow(Icons.Rounded.NotificationsActive, "Sonnerie", "MP3 · 40 s", 2)
        OutputRow(Icons.Rounded.Gif, "GIF", "muet · 30 s", 3)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. L'original n'est jamais modifié : Gulp enregistre le passage à part.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun OutputRow(icon: ImageVector, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(icon, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
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
        Text("Lecture du fichier…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: TrimUiState, source: TrimSource, actions: TrimActions) {
    SourceCard(state, source, actions)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlockLabel("Garde le passage que tu veux")
        TrimTimeline(state, source, actions)
        TimeReadout(state, actions)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Je veux")
        OptionChips(TrimPlan.outputs(source.hasVideo), state.output, { it.label }, actions.onOutput)
        AnimatedVisibility(
            visible = state.output == TrimOutput.VIDEO,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchRow(
                "Coupe à l'image près",
                "Plus lent : la vidéo est réencodée. Sinon, coupe instantanée sur l'image clé la plus proche.",
                state.exact,
                actions.onExact,
            )
        }
        Hint(outputHint(state))
    }
    MainButton(
        "Couper le passage",
        subtitle = "${TrimPlan.timecode(state.range.durationMs)} · ${state.output.label.lowercase()}" + if (state.copies) " · sans réencodage" else "",
        icon = Icons.Rounded.ContentCut,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onCut,
    )
}

private fun outputHint(state: TrimUiState): String = when (state.output) {
    TrimOutput.VIDEO -> if (state.copies) "Instantané et sans perte : la coupe peut commencer un peu plus tôt, sur l'image clé." else "La vidéo est réencodée en MP4 (H.264) : plus lent, coupe exacte."
    TrimOutput.AUDIO -> if (state.copies) "Le son garde son format d'origine, sans perte." else "Le son est enregistré en M4A (AAC 192 kb/s)."
    TrimOutput.RINGTONE -> "40 s au plus, avec un fondu au début et à la fin. Enregistrée dans Musique › Gulp : choisissez-la ensuite dans Réglages › Son."
    TrimOutput.GIF -> "30 s au plus, muet, 480 px. Pour envoyer une réaction ou une boucle."
}

@Composable
private fun SourceCard(state: TrimUiState, source: TrimSource, actions: TrimActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
    ) {
        if (source.hasVideo) {
            val ratio = ((source.width ?: 16).toFloat() / (source.height ?: 9).coerceAtLeast(1)).coerceIn(0.56f, 2.2f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(maxOf(ratio, 1.2f))
                    .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                    .background(Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                val frame = state.startFrame ?: state.strip.frames.firstOrNull()
                AnimatedContent(frame, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) }, label = "startFrame") { bitmap ->
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(42.dp))
                        }
                    }
                }
                Text(
                    "${TrimPlan.timecode(state.range.startMs)} / ${TrimPlan.timecode(source.durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!source.hasVideo) {
                KindTile(Icons.Rounded.MusicNote)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (source.hasVideo) 4.dp else 0.dp)) {
                Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = meta(source)
                Text(if (terminal) "# $meta" else meta, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (terminal) "[CHANGER]" else "Changer",
                color = LocalAccent.current.start,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .bounceClick(pressedScale = 0.9f, onClick = actions.onChoose)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

private fun meta(source: TrimSource): String = buildList {
    add(TrimPlan.timecode(source.durationMs).substringBeforeLast('.'))
    if (source.hasVideo) {
        val w = source.width
        val h = source.height
        if (w != null && h != null) add("${minOf(w, h)}p")
    } else {
        add("son")
    }
    source.file.size?.let { add(bytes(it)) }
}.joinToString(" · ")

@Composable
private fun WorkingStep(state: TrimUiState, phase: TrimPhase.Working, actions: TrimActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) TerminalProgress(phase.progress) else ProgressRing(phase.progress, size = 176.dp)
        Spacer(Modifier.height(16.dp))
        Text(if (terminal) "> ${phase.step}" else phase.step, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "${state.source?.file?.name.orEmpty()} · ${TrimPlan.timecode(state.range.startMs)} → ${TrimPlan.timecode(state.range.endMs)}",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    Hint(
        "Vous pouvez revenir à l'accueil : la coupe continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: TrimUiState, phase: TrimPhase.Done, actions: TrimActions) {
    val what = when (phase.output) {
        TrimOutput.VIDEO -> "Passage découpé"
        TrimOutput.AUDIO -> "Son extrait"
        TrimOutput.RINGTONE -> "Sonnerie prête"
        TrimOutput.GIF -> "GIF prêt"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader(what, if (phase.copied) "Sans réencodage, qualité d'origine" else "Enregistré dans ${phase.saved.location}")
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KindTile(
                when (phase.output) {
                    TrimOutput.VIDEO -> Icons.Rounded.Movie
                    TrimOutput.AUDIO -> Icons.Rounded.MusicNote
                    TrimOutput.RINGTONE -> Icons.Rounded.NotificationsActive
                    TrimOutput.GIF -> Icons.Rounded.Gif
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(phase.saved.name, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${TrimPlan.timecode(phase.durationMs)} · ${phase.saved.location}",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (phase.before != null) {
            Spacer(Modifier.height(16.dp))
            SizeBars(phase.before, phase.saved.size, estimated = false)
        }
    }
    MainButton("Ouvrir", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(phase.saved) }
        if (phase.output == TrimOutput.VIDEO && state.source?.hasVideo == true) {
            SideButton("Garder le son", Icons.Rounded.MusicNote, Modifier.weight(1f)) { actions.onAgain(TrimOutput.AUDIO) }
        } else {
            SideButton("Autre fichier", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
        }
    }
    SideButton("Couper un autre passage", Icons.Rounded.ContentCut, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onBackToEdit)
}
