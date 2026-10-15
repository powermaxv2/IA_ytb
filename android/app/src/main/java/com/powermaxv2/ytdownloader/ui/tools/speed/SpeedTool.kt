package com.powermaxv2.ytdownloader.ui.tools.speed

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.speed.SpeedController
import com.powermaxv2.ytdownloader.core.speed.SpeedPhase
import com.powermaxv2.ytdownloader.core.speed.SpeedPlan
import com.powermaxv2.ytdownloader.core.speed.SpeedSound
import com.powermaxv2.ytdownloader.core.speed.SpeedSource
import com.powermaxv2.ytdownloader.core.speed.SpeedUiState
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

const val SPEED_TITLE = "Vitesse vidéo"
const val SPEED_COMMAND = "speed"
const val SPEED_SUBTITLE = "Passe une vidéo au ralenti ou en accéléré, avec le son qui suit, une voix déformée, ou sans le son."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class SpeedActions(
    val onChoose: () -> Unit = {},
    val onSpeed: (Double) -> Unit = {},
    val onSound: (SpeedSound) -> Unit = {},
    val onGo: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
)

/** The Vitesse vidéo (ToolRegistry.VIDEO_SPEED). Its state lives in [SpeedController.shared], so a change keeps going off screen. */
@Composable
fun SpeedTool(vm: MainViewModel) {
    val controller = remember { SpeedController.shared }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !controller.pick(uri)) vm.toast("Une vidéo est en cours : attendez-la ou annulez-la.", success = false)
    }
    // Android 9 and older: saving to Movies needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer la vidéo.", success = false)
    }
    val actions = remember(controller) {
        SpeedActions(
            onChoose = {
                try {
                    picker.launch(arrayOf("video/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onSpeed = controller::setSpeed,
            onSound = controller::setSound,
            onGo = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
            },
            onCancel = controller::cancel,
            onReset = controller::reset,
            onBackToEdit = controller::backToEdit,
            onOpen = { openSavedFile(context, it, vm) },
            onShare = { shareSavedFiles(context, listOf(it), vm) },
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is SpeedPhase.Ready) scroll.animateScrollTo(0) }
    ToolScaffold(vm, title = SPEED_TITLE, command = SPEED_COMMAND, subtitle = SPEED_SUBTITLE, scrollState = scroll) {
        SpeedContent(state, actions)
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.SpeedContent(state: SpeedUiState, actions: SpeedActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "speedPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                SpeedPhase.Idle -> IdleStep(actions)
                SpeedPhase.Reading -> ReadingStep()
                is SpeedPhase.Unsupported -> {
                    ToolErrorCard(phase.message)
                    SideButton("Choisir une autre vidéo", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChoose)
                }
                SpeedPhase.Ready -> current.source?.let { EditStep(current, it, actions) }
                is SpeedPhase.Working -> WorkingStep(current, phase, actions)
                is SpeedPhase.Done -> DoneStep(current, phase, actions)
                is SpeedPhase.Failed -> {
                    ToolErrorCard(phase.message, phase.debug, title = "La vidéo n'a pas pu changer de vitesse")
                    MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onGo)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBackToEdit)
                        SideButton("Autre vidéo", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
                    }
                }
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: SpeedActions) {
    FileDropZone(
        title = "Choisir une vidéo",
        hint = "MP4, MOV, MKV, WebM, 3GP…",
        icon = Icons.Rounded.Speed,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("De ×0,25 à ×8")
        DemoDial()
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. L'original n'est jamais modifié : Gulp enregistre une copie dans la Galerie (Movies › Gulp).",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
        Text("Lecture de la vidéo…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EditStep(state: SpeedUiState, source: SpeedSource, actions: SpeedActions) {
    SourceCard(state, source, actions)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlockLabel("Choisis la vitesse")
        SpeedDial(state.speed, actions.onSpeed)
        OptionChips(SpeedPlan.SPEEDS, state.speed, { SpeedPlan.label(it) }, actions.onSpeed)
        FilmStrip(state.frames, state.speed)
        DurationBars(source.durationMs, state.outputMs)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Le son")
        if (source.hasAudio) {
            OptionChips(SpeedSound.entries.toList(), state.sound, { it.label }, actions.onSound)
            Hint(soundHint(state))
        } else {
            Hint("Cette vidéo n'a pas de son : elle restera muette.")
        }
    }
    MainButton(
        if (SpeedPlan.isSlow(state.speed)) "Ralentir la vidéo" else "Accélérer la vidéo",
        subtitle = listOfNotNull(
            SpeedPlan.label(state.speed),
            SpeedPlan.clock(state.outputMs),
            (if (source.hasAudio) state.sound else SpeedSound.MUTE).label.lowercase(),
        ).joinToString(" · "),
        icon = Icons.Rounded.Speed,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onGo,
    )
}

private fun soundHint(state: SpeedUiState): String = when (state.sound) {
    SpeedSound.NATURAL -> if (SpeedPlan.isSlow(state.speed)) {
        "Le son est étiré sans devenir grave : les voix restent naturelles, juste plus lentes."
    } else {
        "Le son est compressé sans devenir aigu : les voix restent naturelles, juste plus rapides."
    }
    SpeedSound.TAPE -> if (SpeedPlan.isSlow(state.speed)) "Comme une cassette ralentie : les voix deviennent graves." else "Comme une cassette accélérée : les voix deviennent aiguës, façon dessin animé."
    SpeedSound.MUTE -> "Pas de son du tout : pratique pour y poser une musique ensuite."
}

@Composable
private fun SourceCard(state: SpeedUiState, source: SpeedSource, actions: SpeedActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val poster = state.frames.firstOrNull()
        if (poster != null) {
            Image(
                poster.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 76.dp, height = 52.dp)
                    .clip(RoundedCornerShape(LocalTokens.current.fieldRadius / 2)),
            )
        } else {
            KindTile(Icons.Rounded.Movie)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
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

private fun meta(source: SpeedSource): String = buildList {
    add(SpeedPlan.clock(source.durationMs))
    val w = source.width
    val h = source.height
    if (w != null && h != null) add("${minOf(w, h)}p")
    source.fps?.let { add("${it.toInt()} i/s") }
    if (!source.hasAudio) add("muette")
    source.file.size?.let { add(bytes(it)) }
}.joinToString(" · ")

@Composable
private fun WorkingStep(state: SpeedUiState, phase: SpeedPhase.Working, actions: SpeedActions) {
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
            "${state.source?.file?.name.orEmpty()} · ${SpeedPlan.clock(state.source?.durationMs ?: 0L)} → ${SpeedPlan.clock(state.outputMs)}",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilmStrip(state.frames, state.speed, height = 72)
    }
    Hint(
        "Vous pouvez revenir à l'accueil : la vidéo continue tant que Gulp reste ouvert. Un ralenti prend plus de temps qu'un accéléré.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: SpeedUiState, phase: SpeedPhase.Done, actions: SpeedActions) {
    val what = if (SpeedPlan.isSlow(phase.speed)) "Ralenti prêt" else "Accéléré prêt"
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneHeader(what, "${SpeedPlan.label(phase.speed)} · ${phase.sound.label.lowercase()}")
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KindTile(Icons.Rounded.Movie)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(phase.saved.name, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${SpeedPlan.clock(phase.durationMs)} · ${bytes(phase.saved.size)} · ${phase.saved.location}",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.source?.let {
            Spacer(Modifier.height(16.dp))
            DurationBars(it.durationMs, phase.durationMs)
        }
    }
    MainButton("Ouvrir", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(phase.saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(phase.saved) }
        SideButton("Autre vidéo", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = actions.onReset)
    }
    SideButton("Essayer une autre vitesse", Icons.Rounded.Speed, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onBackToEdit)
}
