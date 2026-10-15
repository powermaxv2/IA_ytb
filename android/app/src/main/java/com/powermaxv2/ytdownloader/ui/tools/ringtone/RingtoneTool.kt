package com.powermaxv2.ytdownloader.ui.tools.ringtone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.ringtone.RingPhase
import com.powermaxv2.ytdownloader.core.ringtone.RingSource
import com.powermaxv2.ytdownloader.core.ringtone.RingUiState
import com.powermaxv2.ytdownloader.core.ringtone.RingtoneController
import com.powermaxv2.ytdownloader.core.ringtone.RingtoneFade
import com.powermaxv2.ytdownloader.core.ringtone.RingtonePlan
import com.powermaxv2.ytdownloader.core.ringtone.RingtoneUse
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
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val RING_TITLE = "Sonneries"
const val RING_COMMAND = "ring"
const val RING_SUBTITLE = "Coupe le meilleur moment d'un morceau et fais-en ta sonnerie, une notification ou ton réveil."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class RingtoneActions(
    val onChoose: () -> Unit = {},
    val onStart: (Long) -> Unit = {},
    val onEnd: (Long) -> Unit = {},
    val onSlide: (Long) -> Unit = {},
    val onBest: () -> Unit = {},
    val onPlay: () -> Unit = {},
    val onUse: (RingtoneUse) -> Unit = {},
    val onFade: (RingtoneFade) -> Unit = {},
    val onLouder: (Boolean) -> Unit = {},
    val onCut: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onSoundSettings: () -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/** Sonneries (ToolRegistry.RINGTONES). Its state lives in [RingtoneController.shared]: a cut keeps going off screen. */
@Composable
fun RingtoneTool(vm: MainViewModel) {
    val controller = remember { RingtoneController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm.pendingFile) {
        val uri = vm.consumePendingFile() ?: return@LaunchedEffect
        if (!controller.pick(uri)) vm.toast("Une coupe est en cours : attendez-la ou annulez-la.", success = false)
    }
    // Back from "Modifier les paramètres système": the sound is set if the user allowed it. The preview stops
    // when the screen goes away.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.resumed()
                Lifecycle.Event.ON_STOP -> controller.stopPreview()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.stopPreview()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une coupe est en cours.", success = false)
    }
    // Android 9 and older: saving to Ringtones / Notifications / Alarms needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le son.", success = false)
    }
    val actions = remember(controller) {
        RingtoneActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("audio/*", "video/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onStart = controller::setStart,
            onEnd = controller::setEnd,
            onSlide = controller::slideTo,
            onBest = controller::toBest,
            onPlay = controller::togglePreview,
            onUse = controller::setUse,
            onFade = controller::setFade,
            onLouder = controller::setLouder,
            onCut = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onApply = {
                if (!controller.applyDefault() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    vm.toast("Autorisez Gulp à modifier les paramètres système, puis revenez.", success = true)
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    try {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        vm.toast("Ce téléphone n'ouvre pas ce réglage : choisissez le son dans Réglages › Son.", success = false)
                    }
                }
            },
            onSoundSettings = {
                try {
                    context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Ouvrez Réglages › Son pour choisir le son.", success = false)
                }
            },
            onShare = { shareSavedFiles(context, listOf(it), vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is RingPhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = RING_TITLE, command = RING_COMMAND, subtitle = RING_SUBTITLE, scrollState = scroll) {
        RingtoneContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.RingtoneContent(state: RingUiState, actions: RingtoneActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "ringPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                RingPhase.Idle -> IdleStep(actions)
                RingPhase.Reading -> ReadingStep()
                is RingPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir un autre son", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                RingPhase.Ready -> current.source?.let { EditStep(current, it, actions) }
                is RingPhase.Working -> WorkingStep(current, phase, actions)
                is RingPhase.Done -> DoneStep(current, phase, actions)
                is RingPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "Le son n'a pas pu être créé")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onCut)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Autre son", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: RingtoneActions) {
    FileDropZone(
        title = "Choisir un morceau",
        hint = "MP3, M4A, WAV, OGG, FLAC… ou une vidéo",
        icon = Icons.Rounded.LibraryMusic,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Ce que Gulp en fait")
        Spacer(Modifier.height(6.dp))
        UseRow(RingtoneUse.RINGTONE, "quand on t'appelle", 0)
        UseRow(RingtoneUse.NOTIFICATION, "un petit son pour les messages", 1)
        UseRow(RingtoneUse.ALARM, "pour te réveiller en musique", 2)
    }
    Hint(
        "Gulp repère le refrain tout seul. Le son est rangé là où Android cherche ses sonneries, et peut devenir celui du téléphone en un geste.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun UseRow(use: RingtoneUse, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UseGlyph(use, active = true, tint = LocalAccent.current.start)
        Spacer(Modifier.width(10.dp))
        Text(if (terminal) use.label.uppercase() else use.label, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text(detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
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
        Text("Écoute du morceau…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: RingUiState, source: RingSource, actions: RingtoneActions) {
    SourceCard(source, actions)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Choisis le passage")
        WaveEditor(state, actions)
        RangeReadout(state, actions)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(state.playing, actions.onPlay)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.playing) "Écoute en cours…" else "Écouter le passage",
                    color = Palette.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Glisse le cadre pour le déplacer", color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            if (state.wave != null) {
                SideButton("Refrain", Icons.Rounded.AutoAwesome, onClick = actions.onBest)
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("C'est pour")
        UseTiles(state.use, actions.onUse)
        BlockLabel("Fondu")
        OptionChips(RingtoneFade.entries, state.fade, { it.label }, actions.onFade)
        SwitchRow(
            "Plus fort",
            "Monte un morceau trop calme au niveau des sonneries, sans saturer.",
            state.louder,
            actions.onLouder,
        )
    }
    MainButton(
        "Créer ${article(state.use)}",
        subtitle = "${RingtonePlan.length(state.range.durationMs)} · MP3 · ${state.use.directory}/Gulp",
        icon = Icons.Rounded.ContentCut,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onCut,
    )
}

private fun article(use: RingtoneUse): String = when (use) {
    RingtoneUse.RINGTONE -> "la sonnerie"
    RingtoneUse.NOTIFICATION -> "la notification"
    RingtoneUse.ALARM -> "le réveil"
}

@Composable
private fun SourceCard(source: RingSource, actions: RingtoneActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(Icons.Rounded.MusicNote)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.file.name, color = Palette.TextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                add(RingtonePlan.timecode(source.durationMs).substringBeforeLast('.'))
                add(if (source.hasVideo) "son d'une vidéo" else "son")
                source.file.size?.let { add(bytes(it)) }
            }.joinToString(" · ")
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

@Composable
private fun WorkingStep(state: RingUiState, phase: RingPhase.Working, actions: RingtoneActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) TerminalProgress(phase.progress) else ProgressRing(phase.progress, size = 168.dp)
        Spacer(Modifier.height(12.dp))
        Equalizer()
        Spacer(Modifier.height(12.dp))
        Text(if (terminal) "> ${phase.step}" else phase.step, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "${state.source?.file?.name.orEmpty()} · ${RingtonePlan.length(state.range.durationMs)}",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: RingUiState, phase: RingPhase.Done, actions: RingtoneActions) {
    val use = phase.use
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RingingPhone(use)
        DoneHeader(
            when (use) {
                RingtoneUse.RINGTONE -> "Sonnerie prête"
                RingtoneUse.NOTIFICATION -> "Notification prête"
                RingtoneUse.ALARM -> "Réveil prêt"
            },
            "Rangé dans ${phase.saved.location} : déjà dans la liste des sons du téléphone",
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(state.playing, actions.onPlay, size = 44.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(phase.saved.name, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${RingtonePlan.length(phase.durationMs)} · ${bytes(phase.saved.size)}", color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1)
            }
        }
    }
    if (phase.applied) {
        AppliedBanner(use)
    } else {
        MainButton(
            when (use) {
                RingtoneUse.RINGTONE -> "En faire ma sonnerie"
                RingtoneUse.NOTIFICATION -> "En faire mon son de notification"
                RingtoneUse.ALARM -> "En faire mon son de réveil"
            },
            subtitle = if (state.applyPending) "Autorisez Gulp, puis revenez ici" else "Remplace le son actuel du téléphone",
            icon = use.icon(),
            modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
            onClick = actions.onApply,
        )
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(phase.saved) }
        SideButton("Réglages Son", Icons.Rounded.Settings, Modifier.weight(1f), onClick = actions.onSoundSettings)
    }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Autre passage", Icons.Rounded.ContentCut, Modifier.weight(1f), onClick = actions.onBackToEdit)
        SideButton("Autre morceau", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
    }
}

@Composable
private fun AppliedBanner(use: RingtoneUse) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val text = when (use) {
        RingtoneUse.RINGTONE -> "C'est ta sonnerie maintenant"
        RingtoneUse.NOTIFICATION -> "C'est ton son de notification maintenant"
        RingtoneUse.ALARM -> "C'est ton son de réveil par défaut maintenant"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 120)
            .toolCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Palette.Success, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) "[OK] ${text.uppercase()}" else text, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
