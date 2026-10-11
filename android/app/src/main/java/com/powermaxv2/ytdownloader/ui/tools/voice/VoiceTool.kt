package com.powermaxv2.ytdownloader.ui.tools.voice

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.voice.VoiceModelFiles
import com.powermaxv2.ytdownloader.core.voice.VoiceModelStatus
import com.powermaxv2.ytdownloader.core.voice.VoicePhase
import com.powermaxv2.ytdownloader.core.voice.VoiceSource
import com.powermaxv2.ytdownloader.core.voice.VoiceStep
import com.powermaxv2.ytdownloader.core.voice.VoiceText
import com.powermaxv2.ytdownloader.core.voice.VoiceUiState
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
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
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile

const val VOICE_TITLE = "Vocaux en texte"
const val VOICE_COMMAND = "voice"
const val VOICE_SUBTITLE = "Lis un vocal au lieu de l'écouter : Gulp l'écrit sur le téléphone, sans rien envoyer."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class VoiceActions(
    val onChoose: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onShareText: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onDeleteModel: () -> Unit = {},
)

/** Vocaux en texte (ToolRegistry.VOICE_TEXT). Its state lives in [App.voice], so a transcription keeps going off screen. */
@Composable
fun VoiceTool(vm: MainViewModel) {
    val controller = remember { App.instance.voice }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(uri)) vm.toast("Une transcription est en cours : attends-la ou annule-la.", success = false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une transcription est en cours.", success = false)
    }
    val actions = remember(controller) {
        VoiceActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("audio/*", "video/*", "application/ogg"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onStart = controller::start,
            onCancel = controller::cancel,
            onReset = controller::reset,
            onCopy = { text ->
                copy(context, text)
                vm.toast("Texte copié")
            },
            onShareText = { text -> shareText(context, text, vm) },
            onSave = controller::saveText,
            onOpen = { openSavedFile(context, it, vm) },
            onDeleteModel = {
                controller.deleteModel()
                vm.toast("Modèle supprimé : 68 Mo libérés")
            },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = VOICE_TITLE, command = VOICE_COMMAND, subtitle = VOICE_SUBTITLE, scrollState = scroll) {
        VoiceContent(state, actions)
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Vocal", text))
}

private fun shareText(context: Context, text: String, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager le texte")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.VoiceContent(state: VoiceUiState, actions: VoiceActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "voicePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                VoicePhase.Idle -> IdleStep(current, actions)
                VoicePhase.Reading -> ReadingStep()
                is VoicePhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir un autre fichier", Icons.Rounded.AudioFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                VoicePhase.Ready -> ReadyStep(current, actions)
                is VoicePhase.Preparing -> PreparingStep(current, phase, actions)
                is VoicePhase.Listening -> ListeningStep(current, phase, actions)
                is VoicePhase.Done -> DoneStep(current, phase, actions)
                is VoicePhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: VoiceUiState, actions: VoiceActions) {
    FileDropZone(
        title = "Choisir un vocal",
        hint = "Vocal WhatsApp (.opus), mémo (.m4a), MP3, WAV… ou le son d'une vidéo",
        icon = Icons.Rounded.RecordVoiceOver,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 120)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Plus besoin d'écouter")
        Spacer(Modifier.height(12.dp))
        BubbleToText()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 160)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Depuis WhatsApp")
        HowRow(1, "Appui long sur le vocal")
        HowRow(2, "Partager (la flèche en haut)")
        HowRow(3, "Transcrire avec Gulp")
    }
    ModelCard(state.model, actions, Modifier.staggeredEntrance(3, baseDelay = 200))
}

@Composable
private fun HowRow(n: Int, text: String) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
                .background(accent.start.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (terminal) "$n>" else "$n", color = accent.start, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Palette.TextPrimary, fontSize = 14.5.sp)
    }
}

/** Whether the French model is here (offline) or will be downloaded once; deletes it when it is here. */
@Composable
private fun ModelCard(model: VoiceModelStatus, actions: VoiceActions, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (model == VoiceModelStatus.READY) Icons.Rounded.OfflineBolt else Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = accent.start,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (model == VoiceModelStatus.READY) "Modèle français installé" else "Modèle français : ${VoiceModelFiles.downloadLabel}, une seule fois",
                color = Palette.TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (model == VoiceModelStatus.READY) "Tout se fait hors ligne. Aucun son ni texte ne quitte le téléphone." else "Téléchargé à la première transcription, puis tout se fait hors ligne. Aucun son ni texte ne quitte le téléphone.",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
        if (model == VoiceModelStatus.READY) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Supprimer le modèle",
                tint = Palette.TextTertiary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .padding(6.dp)
                    .bounceClick(onClick = actions.onDeleteModel),
            )
        }
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
        Text("Lecture du vocal…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

/** The picked file: name, length, size, its (pseudo) waveform. */
@Composable
private fun SourceCard(source: VoiceSource, wave: List<Float>, modifier: Modifier = Modifier, progress: Float = 1f, live: Boolean = false) {
    val accent = LocalAccent.current
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 2.dp else 14.dp))
                    .background(accent.start.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (source.video) Icons.Rounded.Videocam else Icons.Rounded.Mic, contentDescription = null, tint = accent.start, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    source.durationMs?.let { VoiceText.clock(it) },
                    source.file.size?.let { com.powermaxv2.ytdownloader.core.Fmt.bytes(it.toDouble()) },
                    if (source.video) "son de la vidéo" else null,
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        VoiceWave(wave.ifEmpty { sampleWave(source.file.name.hashCode()) }, progress = progress, live = live)
    }
}

@Composable
private fun ReadyStep(state: VoiceUiState, actions: VoiceActions) {
    val source = state.source ?: return
    SourceCard(source, state.wave, Modifier.staggeredEntrance(0, baseDelay = 40), progress = 0f)
    MainButton(
        "Transcrire en texte",
        icon = Icons.Rounded.ShortText,
        subtitle = if (state.model == VoiceModelStatus.MISSING) "Télécharge d'abord le modèle français (${VoiceModelFiles.downloadLabel})" else "Hors ligne, en français",
        modifier = Modifier.staggeredEntrance(1, baseDelay = 80),
        onClick = actions.onStart,
    )
    SideButton("Choisir un autre vocal", Icons.Rounded.AudioFile, Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 80), onClick = actions.onChoose)
    Hint(
        "Le vocal reste sur le téléphone : Gulp l'écoute avec un modèle de reconnaissance vocale installé dans l'app. Plus le son est clair, meilleur est le texte.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .staggeredEntrance(3, baseDelay = 120),
    )
}

@Composable
private fun PreparingStep(state: VoiceUiState, phase: VoicePhase.Preparing, actions: VoiceActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ListeningRing(phase.progress)
        Spacer(Modifier.height(18.dp))
        Text(
            if (terminal) "> ${phase.step.label.lowercase()}…" else "${phase.step.label}…",
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        val detail = phase.detail ?: when (phase.step) {
            VoiceStep.DOWNLOAD -> "Une seule fois, ensuite plus besoin d'Internet"
            VoiceStep.UNPACK -> "68 Mo dans les fichiers de Gulp"
            VoiceStep.DECODE -> state.source?.file?.name
            VoiceStep.LOAD -> "Le modèle se met à l'écoute"
        }
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (phase.step == VoiceStep.DOWNLOAD) {
            Spacer(Modifier.height(16.dp))
            if (terminal) TerminalProgress(phase.progress) else Bar(phase.progress)
        }
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun ListeningStep(state: VoiceUiState, phase: VoicePhase.Listening, actions: VoiceActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    state.source?.let { SourceCard(it, state.wave, progress = phase.progress, live = true) }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListeningRing(phase.progress, size = 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (terminal) "> ÉCOUTE…" else "J'écoute…", color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${VoiceText.clock(phase.heardMs)} / ${VoiceText.clock(phase.totalMs)}",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text("${(phase.progress * 100).toInt()} %", color = LocalAccent.current.start, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        TranscriptText(phase.sentences, phase.partial, writing = true)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: VoiceUiState, phase: VoicePhase.Done, actions: VoiceActions) {
    if (phase.empty) {
        ToolErrorCard(
            "Aucune parole reconnue dans ce son. Le vocal est peut-être silencieux, trop bruité, ou dans une autre langue que le français.",
            title = "Rien à écrire",
        )
        SideButton("Choisir un autre vocal", Icons.Rounded.AudioFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
        return
    }
    val text = phase.text
    DoneHeader("Vocal transcrit", state.source?.file?.name)
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 200),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        StatChip(VoiceText.clock(phase.durationMs), icon = Icons.Rounded.Schedule)
        StatChip(VoiceText.words(VoiceText.wordCount(phase.sentences)), icon = Icons.Rounded.ShortText)
        VoiceText.speed(phase.durationMs, phase.tookMs)?.let { StatChip(it, icon = Icons.Rounded.Speed) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 240)
            .toolCard()
            .padding(16.dp),
    ) {
        if (state.wave.isNotEmpty()) {
            VoiceWave(state.wave, height = 34f)
            Spacer(Modifier.height(12.dp))
        }
        TranscriptText(phase.sentences, "")
        if (VoiceText.unsureShare(phase.sentences) > 0f) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (LocalSkin.current == Skin.TERMINAL) "# mots grisés = le modèle hésite, relis-les" else "Les mots grisés sont ceux dont le modèle n'est pas sûr : relis-les.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
    MainButton("Copier le texte", icon = Icons.Rounded.ContentCopy, modifier = Modifier.staggeredEntrance(2, baseDelay = 260)) { actions.onCopy(text) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 280), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShareText(text) }
        val saved = phase.saved
        if (saved == null) {
            SideButton("En .txt", Icons.Rounded.Download, Modifier.weight(1f), onClick = actions.onSave)
        } else {
            SideButton("Ouvrir le .txt", Icons.Rounded.FolderOpen, Modifier.weight(1f)) { actions.onOpen(saved) }
        }
    }
    phase.saved?.let { Hint("Enregistré dans ${it.location} : « ${it.name} »", modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
    SideButton("Un autre vocal", Icons.Rounded.RecordVoiceOver, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 300), onClick = actions.onReset)
}

@Composable
private fun FailedStep(state: VoiceUiState, phase: VoicePhase.Failed, actions: VoiceActions) {
    ToolErrorCard(phase.message, phase.debug, title = "La transcription n'a pas abouti")
    if (state.source != null) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
    }
    SideButton("Choisir un autre vocal", Icons.Rounded.AudioFile, Modifier.fillMaxWidth(), onClick = actions.onReset)
}

@Composable
private fun Bar(progress: Float) {
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
                .height(10.dp)
                .clip(shape)
                .background(accent.horizontal())
                .shimmer(),
        )
    }
}
