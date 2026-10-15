package com.powermaxv2.ytdownloader.ui.tools.recorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.powermaxv2.ytdownloader.core.recorder.MemoInfo
import com.powermaxv2.ytdownloader.core.recorder.Memos
import com.powermaxv2.ytdownloader.core.recorder.RecPhase
import com.powermaxv2.ytdownloader.core.recorder.RecorderController
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import com.powermaxv2.ytdownloader.ui.tools.voice.VoiceWave
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

const val REC_TITLE = "Enregistreur vocal"
const val REC_COMMAND = "rec"
const val REC_SUBTITLE = "Un toucher pour enregistrer un mémo, un autre pour le garder. Réécoute, renomme, partage."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class RecActions(
    val onRecord: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onPlay: (String) -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onSpeed: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onRenameSave: () -> Unit = {},
    val onRenameCancel: () -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onExport: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteCancel: () -> Unit = {},
    val onMicSettings: () -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class RecUiState(
    val phase: RecPhase = RecPhase.IDLE,
    val elapsedMs: Long = 0,
    val live: List<Float> = emptyList(),
    val memos: List<MemoInfo> = emptyList(),
    /** The memo whose player is unfolded. */
    val open: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    /** The memo being renamed, and the name typed so far. */
    val renaming: String? = null,
    val draft: String = "",
    /** The memo waiting for "Supprimer ?" to be confirmed. */
    val confirmDelete: String? = null,
    /** The memo just saved: its card arrives with a flash. */
    val justSaved: String? = null,
    /** The microphone was refused for good: a card leads to the app's settings. */
    val micDenied: Boolean = false,
    /** Clock for "Aujourd'hui, 19h20"; fixed in the screenshot tests. */
    val now: LocalDateTime = LocalDateTime.now(),
)

/**
 * Enregistreur vocal (ToolRegistry.VOICE_RECORDER): records a voice memo from the microphone, plays it back (with
 * speed and seeking on its outline), renames, shares, copies it to Music/Gulp or deletes it. Memos stay in Gulp's
 * private folder until the user sends one somewhere.
 */
@Composable
fun RecorderTool(vm: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val controller = remember { RecorderController.shared }
    val rec by controller.state.collectAsState()
    val scope = rememberCoroutineScope()

    var micDenied by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    var loadedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { controller.refresh() }

    fun releasePlayer() {
        player[0]?.let { runCatching { it.release() } }
        player[0] = null
        loadedFor = null
        playing = false
    }

    fun begin() {
        releasePlayer()
        if (!controller.start()) vm.toast("Le micro ne s'ouvre pas : une autre app l'utilise peut-être", success = false)
    }

    fun stopAndKeep(quiet: Boolean = false) {
        val saved = controller.stop()
        if (saved == null) {
            if (!quiet) vm.toast("Trop court : rien n'a été gardé", success = false)
        } else {
            open = saved
            position = 0
            if (quiet) vm.toast("Enregistrement gardé")
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micDenied = false
            begin()
        } else {
            val activity = context.findActivity()
            micDenied = activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            vm.toast("Gulp a besoin du micro pour enregistrer", success = false)
        }
    }

    // Android cuts the microphone of an app in the background: leaving the screen keeps what was recorded.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (controller.state.value.phase != RecPhase.IDLE) stopAndKeep(quiet = true)
                player[0]?.let { if (it.isPlaying) it.pause() }
                playing = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (controller.state.value.phase != RecPhase.IDLE) controller.stop()
            releasePlayer()
            view.keepScreenOn = false
        }
    }
    // The screen stays on while a memo is being recorded.
    LaunchedEffect(rec.phase) { view.keepScreenOn = rec.phase != RecPhase.IDLE }

    // The playhead follows the player.
    LaunchedEffect(playing) {
        while (playing) {
            player[0]?.let { position = runCatching { it.currentPosition.toLong() }.getOrDefault(position) }
            delay(80)
        }
    }

    fun prepare(fileName: String): MediaPlayer? {
        if (loadedFor == fileName) return player[0]
        releasePlayer()
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(controller.file(fileName).absolutePath)
            mp.prepare()
            mp.setOnCompletionListener {
                playing = false
                position = 0
            }
            player[0] = mp
            loadedFor = fileName
            mp
        } catch (e: Exception) {
            runCatching { mp.release() }
            vm.toast("Ce mémo ne se lit pas", success = false)
            null
        }
    }

    fun applySpeed(mp: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= 23) runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
    }

    val actions = RecActions(
        onRecord = {
            when (rec.phase) {
                RecPhase.IDLE -> if (hasMic(context)) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
                else -> stopAndKeep()
            }
        },
        onPause = controller::pause,
        onResume = controller::resume,
        onDiscard = {
            controller.discard()
            vm.toast("Enregistrement jeté")
        },
        onOpen = { name ->
            if (open != name) {
                releasePlayer()
                position = 0
                open = name
            } else {
                open = null
                releasePlayer()
            }
            renaming = null
            confirmDelete = null
        },
        onPlay = { name ->
            if (rec.phase == RecPhase.IDLE) {
                if (open != name) {
                    open = name
                    position = 0
                    renaming = null
                    confirmDelete = null
                }
                val mp = prepare(name)
                if (mp != null) {
                    if (mp.isPlaying) {
                        mp.pause()
                        playing = false
                    } else {
                        applySpeed(mp)
                        mp.start()
                        playing = true
                    }
                }
            }
        },
        onSeek = { fraction ->
            val name = open
            val memo = rec.memos.firstOrNull { it.fileName == name }
            if (name != null && memo != null) {
                prepare(name)?.let { mp ->
                    val to = Memos.seekTo(fraction, mp.duration.toLong().takeIf { it > 0 } ?: memo.durationMs)
                    mp.seekTo(to.toInt())
                    position = to
                }
            }
        },
        onSpeed = {
            speed = Memos.nextSpeed(speed)
            player[0]?.let { if (it.isPlaying) applySpeed(it) }
        },
        onRename = { name ->
            renaming = name
            draft = SafeTitle.of(name)
            confirmDelete = null
        },
        onDraft = { draft = it.take(Memos.MAX_TITLE) },
        onRenameSave = {
            val from = renaming
            if (from != null) {
                if (loadedFor == from) releasePlayer()
                val to = controller.rename(from, draft)
                if (to == null) {
                    vm.toast(if (Memos.cleanTitle(draft) == null) "Donne-lui un nom" else "Un mémo porte déjà ce nom", success = false)
                } else {
                    if (open == from) open = to
                    renaming = null
                }
            }
        },
        onRenameCancel = { renaming = null },
        onShare = { name -> scope.launch { share(context, controller, name, vm) } },
        onExport = { name ->
            scope.launch {
                val saved = controller.export(name)
                if (saved == null) vm.toast("Copie impossible", success = false) else vm.toast("Copié dans ${saved.location}")
            }
        },
        onDelete = { name ->
            confirmDelete = name
            renaming = null
        },
        onDeleteConfirm = {
            confirmDelete?.let { name ->
                if (loadedFor == name) releasePlayer()
                controller.delete(name)
                if (open == name) open = null
                vm.toast("Mémo supprimé")
            }
            confirmDelete = null
        },
        onDeleteCancel = { confirmDelete = null },
        onMicSettings = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )

    ToolScaffold(vm, title = REC_TITLE, command = REC_COMMAND, subtitle = REC_SUBTITLE) {
        RecContent(
            RecUiState(
                phase = rec.phase,
                elapsedMs = rec.elapsedMs,
                live = rec.live,
                memos = rec.memos,
                open = open,
                playing = playing,
                positionMs = position,
                speed = speed,
                renaming = renaming,
                draft = draft,
                confirmDelete = confirmDelete,
                justSaved = rec.justSaved,
                micDenied = micDenied && !hasMic(context),
            ),
            actions,
        )
    }
}

/** The name shown in the rename field: the file name without ".m4a". */
private object SafeTitle {
    fun of(fileName: String): String = MemoInfo(fileName, 0, 0, 0).title
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.RecContent(state: RecUiState, actions: RecActions) {
    RecDeck(state, actions)

    if (state.micDenied) {
        Column(
            Modifier
                .fillMaxWidth()
                .toolCard()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.MicOff, contentDescription = null, tint = Palette.Danger)
                Spacer(Modifier.width(10.dp))
                Text(recText("Le micro est refusé à Gulp"), color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Autorise-le dans les réglages de Gulp (Autorisations → Micro), puis reviens ici.",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            MiniAction("Ouvrir les réglages", Icons.Rounded.Mic, onClick = actions.onMicSettings)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .staggeredEntrance(1, baseDelay = 120),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlockLabel("Mes mémos", Modifier.weight(1f))
        Text(recText(Memos.countText(state.memos)), color = Palette.TextTertiary, fontSize = 12.5.sp)
    }

    if (state.memos.isEmpty()) {
        EmptyMemos(Modifier.staggeredEntrance(2, baseDelay = 160))
    }
    state.memos.forEachIndexed { i, memo ->
        key(memo.fileName) {
            MemoCard(memo, state, actions, Modifier.staggeredEntrance(i + 2, baseDelay = 140))
        }
    }

    Hint(
        "Les mémos restent dans Gulp, sur le téléphone. « Musique » en met une copie dans Music/Gulp ; si tu quittes " +
            "l'écran pendant un enregistrement, il s'arrête et se garde.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/** The recording deck: timer, live bars, and the big button with Jeter / Pause on its sides. */
@Composable
private fun RecDeck(state: RecUiState, actions: RecActions) {
    val recording = state.phase != RecPhase.IDLE
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecTimer(state.elapsedMs, state.phase)
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "recStatus",
        ) { phase ->
            Text(
                recText(
                    when (phase) {
                        RecPhase.IDLE -> "Prêt à enregistrer"
                        RecPhase.RECORDING -> "J'écoute…"
                        RecPhase.PAUSED -> "En pause"
                    },
                ),
                color = if (phase == RecPhase.RECORDING) LocalAccent.current.start else Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        LiveBars(state.live, state.phase, RecorderController.LIVE_BARS, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = DeckArrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(SideSlot), contentAlignment = Alignment.Center) {
                if (recording) RoundAction(DiscardIcon, "Jeter", actions.onDiscard)
            }
            RecordButton(state.phase, state.live.lastOrNull() ?: 0f, actions.onRecord)
            Box(Modifier.width(SideSlot), contentAlignment = Alignment.Center) {
                when (state.phase) {
                    RecPhase.RECORDING -> RoundAction(Icons.Rounded.Pause, "Pause", actions.onPause)
                    RecPhase.PAUSED -> RoundAction(Icons.Rounded.PlayArrow, "Reprendre", actions.onResume)
                    RecPhase.IDLE -> Unit
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            recText(if (recording) "Touche le carré pour garder le mémo" else "Touche le micro pour commencer"),
            color = Palette.TextTertiary,
            fontSize = 12.5.sp,
        )
    }
}

/** One memo: play, name and facts; unfolded, its outline with the playhead, speed, and the actions. */
@Composable
private fun MemoCard(memo: MemoInfo, state: RecUiState, actions: RecActions, modifier: Modifier = Modifier) {
    val open = state.open == memo.fileName
    val accent = LocalAccent.current.start
    // A fresh memo arrives with an accent flash that fades.
    val flash = remember { Animatable(if (state.justSaved == memo.fileName) 1f else 0f) }
    LaunchedEffect(Unit) { if (flash.value > 0f) flash.animateTo(0f, tween(1_600)) }
    val pop = remember { Animatable(if (state.justSaved == memo.fileName) 0.9f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .toolCard()
            .background(accent.copy(alpha = 0.18f * flash.value))
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 420f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(open && state.playing, onClick = { actions.onPlay(memo.fileName) })
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .pointerInput(memo.fileName) { detectTapGestures { actions.onOpen(memo.fileName) } },
            ) {
                Text(
                    memo.title,
                    color = Palette.TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Memos.whenText(memo.createdAt, state.now)} · ${Memos.duration(memo.durationMs)} · ${Memos.size(memo.size)}",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!open && memo.wave.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            VoiceWave(Memos.downsample(memo.wave, 48), progress = 0f, height = 22f)
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val total = memo.durationMs.coerceAtLeast(1)
                val wave = memo.wave.ifEmpty { List(48) { 0.25f } }
                Box(Modifier.pointerInput(memo.fileName) { detectTapGestures { actions.onSeek(it.x / size.width) } }) {
                    VoiceWave(Memos.downsample(wave, 64), progress = state.positionMs.toFloat() / total, live = state.playing, height = 52f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Memos.duration(state.positionMs)} / ${Memos.duration(memo.durationMs)}",
                        color = Palette.TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    SpeedPill(state.speed, actions.onSpeed)
                }
                when {
                    state.renaming == memo.fileName -> {
                        QrField(label = "Nom du mémo", value = state.draft, onChange = actions.onDraft, placeholder = "Idée de cadeau", maxLength = Memos.MAX_TITLE)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniAction("Garder ce nom", Icons.Rounded.Check, onClick = actions.onRenameSave)
                            MiniAction("Annuler", Icons.Rounded.Close, onClick = actions.onRenameCancel)
                        }
                    }
                    state.confirmDelete == memo.fileName -> {
                        Text(recText("Supprimer ce mémo pour de bon ?"), color = Palette.Danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniAction("Supprimer", Icons.Rounded.DeleteOutline, onClick = actions.onDeleteConfirm)
                            MiniAction("Garder", Icons.Rounded.Close, onClick = actions.onDeleteCancel)
                        }
                    }
                    else -> {
                        DrawnRule()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniAction("Partager", Icons.Rounded.Share, onClick = { actions.onShare(memo.fileName) })
                            MiniAction("Renommer", Icons.Rounded.Edit, onClick = { actions.onRename(memo.fileName) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniAction("Musique", Icons.Rounded.LibraryMusic, onClick = { actions.onExport(memo.fileName) })
                            MiniAction("Supprimer", Icons.Rounded.DeleteOutline, onClick = { actions.onDelete(memo.fileName) })
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Intents

private fun hasMic(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Shares a copy of the memo, under its name, with the app the user picks (WhatsApp, mail…). */
private suspend fun share(context: Context, controller: RecorderController, fileName: String, vm: MainViewModel) {
    val file = controller.shareCopy(fileName) ?: return vm.toast("Partage impossible", success = false)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("audio/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager le mémo")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
