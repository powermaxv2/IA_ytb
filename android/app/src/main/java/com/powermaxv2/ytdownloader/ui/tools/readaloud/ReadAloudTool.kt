package com.powermaxv2.ytdownloader.ui.tools.readaloud

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.readaloud.AudioOut
import com.powermaxv2.ytdownloader.core.readaloud.ReadAloudController
import com.powermaxv2.ytdownloader.core.readaloud.ReadAloudUiState
import com.powermaxv2.ytdownloader.core.readaloud.SpeechEngine
import com.powermaxv2.ytdownloader.core.readaloud.SpeechExport
import com.powermaxv2.ytdownloader.core.readaloud.SpeechPitch
import com.powermaxv2.ytdownloader.core.readaloud.SpeechRate
import com.powermaxv2.ytdownloader.core.readaloud.SpeechText
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.ProgressBar
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.DoneHeader
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.openSavedFile
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction

const val READ_ALOUD_TITLE = "Lecture à voix haute"
const val READ_ALOUD_COMMAND = "say"
const val READ_ALOUD_SUBTITLE = "Colle un texte ou ouvre un fichier : ton téléphone te le lit, phrase par phrase, et l'enregistre en audio."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ReadAloudActions(
    val onText: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onChoose: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onToggle: () -> Unit = {},
    val onSample: () -> Unit = {},
    val onSeek: (Int) -> Unit = {},
    val onSeekFraction: (Float) -> Unit = {},
    val onRate: (SpeechRate) -> Unit = {},
    val onPitch: (SpeechPitch) -> Unit = {},
    val onLanguage: (String) -> Unit = {},
    val onVoice: (String) -> Unit = {},
    val onFormat: (AudioOut) -> Unit = {},
    val onExport: () -> Unit = {},
    val onCancelExport: () -> Unit = {},
    val onDismissExport: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
    val onEngineSettings: () -> Unit = {},
)

/**
 * The Lecture à voix haute (ToolRegistry.READ_ALOUD): a text spoken by the phone's own voice, the word being said lit
 * on screen, and saved as an audio file. Its state lives in [ReadAloudController.shared]; the voice stops when the
 * user leaves the screen, a file being saved goes on.
 */
@Composable
fun ReadAloudTool(vm: MainViewModel) {
    val controller = remember { ReadAloudController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        controller.start()
        onDispose { controller.pause() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.open(uri)
    }
    // Android 9 and older: saving to Music needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.export() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer l'audio.", success = false)
    }

    val actions = ReadAloudActions(
        onText = controller::setText,
        onPaste = {
            val pasted = paste(context)
            if (pasted.isNullOrBlank()) vm.toast("Rien à coller", success = false) else controller.setText(pasted)
        },
        onChoose = {
            try {
                picker.launch(MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onClear = controller::clear,
        onToggle = controller::toggle,
        onSample = {
            controller.setText(SpeechText.SAMPLE)
            controller.play(0)
        },
        onSeek = controller::seek,
        onSeekFraction = { f ->
            val chunks = controller.state.value.chunks
            if (chunks.isNotEmpty()) {
                val total = chunks.sumOf { it.length }
                var acc = 0
                val target = chunks.indexOfFirst { c -> acc += c.length; acc >= f * total }
                controller.seek(if (target < 0) chunks.lastIndex else target)
            }
        },
        onRate = controller::setRate,
        onPitch = controller::setPitch,
        onLanguage = controller::setLanguage,
        onVoice = controller::setVoice,
        onFormat = controller::setFormat,
        onExport = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.export()
        },
        onCancelExport = controller::cancelExport,
        onDismissExport = controller::dismissExport,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, listOf(it), vm) },
        onEngineSettings = {
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { vm.toast("Ouvre Paramètres › Accessibilité › Synthèse vocale", success = false) }
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = READ_ALOUD_TITLE, command = READ_ALOUD_COMMAND, subtitle = READ_ALOUD_SUBTITLE, scrollState = scroll) {
        ReadAloudContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf(
    "text/*",
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text",
    "application/x-subrip",
    "application/octet-stream",
)

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ReadAloudContent(state: ReadAloudUiState, actions: ReadAloudActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL

    (state.engine as? SpeechEngine.Missing)?.let { missing ->
        ToolErrorCard(missing.message, title = "Pas de voix sur ce téléphone")
        SideButton("Réglages de la synthèse vocale", Icons.Rounded.Settings, Modifier.fillMaxWidth(), onClick = actions.onEngineSettings)
    }

    SourceBlock(state, actions)

    AnimatedVisibility(state.loading, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(
            if (terminal) "> LECTURE DU FICHIER…" else "Lecture du fichier…",
            color = Palette.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .toolCard()
                .shimmer()
                .padding(16.dp),
        )
    }
    state.loadError?.let { ToolErrorCard(it, title = "Ce fichier ne se lit pas") }

    PlayerCard(state, actions)
    VoiceSettings(state, actions)
    ExportBlock(state, actions)

    Hint(
        "Tout se passe sur le téléphone, avec sa propre voix. Une voix marquée « en ligne » envoie le texte au service de synthèse de Google.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

// MARK: - Text in

@Composable
private fun SourceBlock(state: ReadAloudUiState, actions: ReadAloudActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 300f)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = state.sourceName
        if (name == null) {
            QrField(
                label = "Ton texte",
                value = state.text,
                onChange = actions.onText,
                singleLine = false,
                placeholder = "Colle ou écris ici un article, un cours, un message…",
                maxLength = SpeechText.MAX_TYPED,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Coller", Icons.Rounded.ContentPaste, onClick = actions.onPaste)
                MiniAction("Fichier", Icons.Rounded.Description, onClick = actions.onChoose)
                AnimatedVisibility(state.text.isNotEmpty(), enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                    MiniAction("Effacer", Icons.Rounded.Close, onClick = actions.onClear)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindTile(Icons.Rounded.Description)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(state.sourceKind?.label, words(state.text)).joinToString(" · "),
                        color = Palette.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Autre fichier", Icons.Rounded.Description, onClick = actions.onChoose)
                MiniAction("Retirer", Icons.Rounded.Close, onClick = actions.onClear)
            }
        }
        if (state.hasText) {
            Text(
                "${state.chunks.size} phrases · ≈ ${SpeechText.duration(state.totalSeconds)} à ${state.rate.label}",
                color = Palette.TextTertiary,
                fontSize = 12.5.sp,
            )
        }
    }
}

private fun words(text: String): String {
    val n = text.split(Regex("""\s+""")).count { it.any(Char::isLetterOrDigit) }
    return if (n > 1) "%,d mots".format(n).replace(',', ' ') else "$n mot"
}

// MARK: - Player

@Composable
private fun PlayerCard(state: ReadAloudUiState, actions: ReadAloudActions) {
    val ready = state.engine == SpeechEngine.Ready && !state.busy
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SoundBars(state.playing, Modifier.fillMaxWidth())
        if (state.hasText) {
            KaraokeCard(
                previous = if (state.current > 0) state.chunkText(state.current - 1) else null,
                current = state.chunkText(state.current),
                next = if (state.current < state.chunks.lastIndex) state.chunkText(state.current + 1) else null,
                index = state.current,
                word = state.word,
                onPrevious = { actions.onSeek(state.current - 1) },
                onNext = { actions.onSeek(state.current + 1) },
            )
            ReadingStrip(state.progress, actions.onSeekFraction)
            Row(Modifier.fillMaxWidth()) {
                Text("Phrase ${state.current + 1} / ${state.chunks.size}", color = Palette.TextTertiary, fontSize = 12.5.sp)
                Spacer(Modifier.weight(1f))
                Text("reste ≈ ${SpeechText.duration(state.leftSeconds)}", color = Palette.TextTertiary, fontSize = 12.5.sp)
            }
            CenteredRow {
                SkipButton(Icons.Rounded.SkipPrevious, "Phrase précédente", ready && state.current > 0) { actions.onSeek(state.current - 1) }
                PlayButton(state.playing, ready, actions.onToggle)
                SkipButton(Icons.Rounded.SkipNext, "Phrase suivante", ready && state.current < state.chunks.lastIndex) { actions.onSeek(state.current + 1) }
            }
        } else {
            Text(
                "« ${SpeechText.SAMPLE} »",
                color = Palette.TextSecondary,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            )
            CenteredRow { PlayButton(false, ready, actions.onSample) }
            Hint("Touche ▶ pour entendre un exemple, puis colle ton texte au-dessus.", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun VoiceSettings(state: ReadAloudUiState, actions: ReadAloudActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    fun t(s: String) = if (terminal) s.uppercase() else s
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Vitesse")
        OptionChips(SpeechRate.entries, state.rate, { t(it.label) }, actions.onRate)
        BlockLabel("Ton de la voix", Modifier.padding(top = 4.dp))
        OptionChips(SpeechPitch.entries, state.pitch, { t(it.label) }, actions.onPitch)
        if (state.languages.size > 1) {
            BlockLabel("Langue", Modifier.padding(top = 4.dp))
            OptionChips(state.languages.map { it.code }, state.language ?: "", { code -> t(state.languages.first { it.code == code }.label) }, actions.onLanguage)
        }
        if (state.voices.size > 1) {
            BlockLabel("Voix", Modifier.padding(top = 4.dp))
            OptionChips(
                state.voices.map { it.name },
                state.voice ?: "",
                { name -> state.voices.first { it.name == name }.let { v -> t(if (v.offline) v.label else "${v.label} · en ligne") } },
                actions.onVoice,
            )
        }
    }
}

// MARK: - Saving

@Composable
private fun ExportBlock(state: ReadAloudUiState, actions: ReadAloudActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    AnimatedContent(
        targetState = state.export,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 8 }) togetherWith fadeOut(tween(160))
        },
        label = "readAloudExport",
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 40),
    ) { export ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (export) {
                SpeechExport.None -> {
                    Column(Modifier.fillMaxWidth().toolCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BlockLabel("Enregistrer en audio")
                        OptionChips(AudioOut.entries, state.format, { f -> if (terminal) "${f.label} · ${f.detail}".uppercase() else "${f.label} · ${f.detail}" }, actions.onFormat)
                    }
                    MainButton(
                        "Enregistrer en ${state.format.label}",
                        subtitle = if (state.hasText) "≈ ${SpeechText.duration(state.totalSeconds)} de son, dans Musique › Gulp" else "Écris ou ouvre d'abord un texte",
                        icon = Icons.Rounded.Download,
                        enabled = state.hasText && state.engine == SpeechEngine.Ready,
                        onClick = actions.onExport,
                    )
                }
                is SpeechExport.Working -> {
                    Column(Modifier.fillMaxWidth().toolCard().padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val step = if (export.encoding) "Compression en ${state.format.label}…" else "La voix enregistre… ${export.done}/${export.total}"
                        Text(if (terminal) "> ${step.uppercase()}" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        SoundBars(true, Modifier.fillMaxWidth(), bars = 20)
                        Spacer(Modifier.height(14.dp))
                        if (terminal) TerminalProgress(export.progress) else ProgressBar(export.progress, Modifier.fillMaxWidth(), height = 8.dp)
                    }
                    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancelExport)
                }
                is SpeechExport.Done -> {
                    Column(Modifier.fillMaxWidth().toolCard().padding(horizontal = 16.dp, vertical = 20.dp)) {
                        DoneHeader("Audio enregistré", "${export.saved.name} · ${SpeechText.duration(export.seconds)}\ndans Musique › Gulp")
                    }
                    MainButton("Partager l'audio", icon = Icons.Rounded.Share) { actions.onShare(export.saved) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Écouter", Icons.Rounded.Headphones, Modifier.weight(1f)) { actions.onOpen(export.saved) }
                        SideButton("Autre format", Icons.Rounded.Refresh, Modifier.weight(1f), onClick = actions.onDismissExport)
                    }
                }
                is SpeechExport.Failed -> {
                    ToolErrorCard(export.message, export.debug, title = "L'audio n'a pas été enregistré")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onExport)
                    SideButton("Fermer", Icons.Rounded.Close, Modifier.fillMaxWidth(), onClick = actions.onDismissExport)
                }
            }
        }
    }
}
