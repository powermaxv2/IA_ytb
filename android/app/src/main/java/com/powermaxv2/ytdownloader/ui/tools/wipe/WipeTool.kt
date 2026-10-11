package com.powermaxv2.ytdownloader.ui.tools.wipe

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.wipe.WipeChoices
import com.powermaxv2.ytdownloader.core.wipe.WipeKind
import com.powermaxv2.ytdownloader.core.wipe.WipePhase
import com.powermaxv2.ytdownloader.core.wipe.WipePhoto
import com.powermaxv2.ytdownloader.core.wipe.WipeReport
import com.powermaxv2.ytdownloader.core.wipe.WipeUiState
import com.powermaxv2.ytdownloader.core.wipe.wipes
import com.powermaxv2.ytdownloader.core.wipe.with
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
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
import com.powermaxv2.ytdownloader.ui.tools.shareSavedFiles

const val WIPE_TITLE = "Gomme"
const val WIPE_COMMAND = "wipe"
const val WIPE_SUBTITLE = "Efface ce que tes photos disent de toi (lieu, téléphone, nom) avant de les envoyer."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class WipeActions(
    val onChoose: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRemove: (WipePhoto) -> Unit = {},
    val onChoices: (WipeChoices) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (List<SavedFile>) -> Unit = {},
)

/**
 * The Gomme (ToolRegistry.WIPER): erases what photos tell about whoever took them. Its state lives in [App.wiper],
 * so a cleaning keeps going when the user leaves this screen.
 */
@Composable
fun WipeTool(vm: MainViewModel) {
    val controller = remember { App.instance.wiper }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Un nettoyage est en cours.", success = false)
    }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris, add = true)) vm.toast("Un nettoyage est en cours.", success = false)
    }
    fun launch(launcher: () -> Unit) {
        try {
            launcher()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }
    // Android 10+: without "position des contenus multimédias", Android blanks the GPS of the photos Gulp reads,
    // and the screen could not show where they were taken. Asked once per visit, before the first pick.
    var askedLocation by rememberSaveable { mutableStateOf(false) }
    var addNext by remember { mutableStateOf(false) }
    val mediaLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (addNext) launch { adder.launch(MIME_TYPES) } else launch { picker.launch(MIME_TYPES) }
    }
    fun choose(add: Boolean) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !askedLocation &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            askedLocation = true
            addNext = add
            mediaLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else if (add) {
            launch { adder.launch(MIME_TYPES) }
        } else {
            launch { picker.launch(MIME_TYPES) }
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les photos.", success = false)
    }

    val actions = WipeActions(
        onChoose = { choose(add = false) },
        onAdd = { choose(add = true) },
        onRemove = controller::remove,
        onChoices = controller::setChoices,
        onStart = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.start()
        },
        onCancel = controller::cancel,
        onReset = controller::reset,
        onBack = controller::backToPhotos,
        onOpen = { openSavedFile(context, it, vm) },
        onShare = { shareSavedFiles(context, it, vm) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is WipePhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = WIPE_TITLE, command = WIPE_COMMAND, subtitle = WIPE_SUBTITLE, scrollState = scroll) {
        WipeContent(state, actions)
    }
}

private val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.WipeContent(state: WipeUiState, actions: WipeActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "wipePhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                WipePhase.Idle -> IdleStep(actions)
                WipePhase.Reading -> ReadingStep()
                is WipePhase.Unsupported -> UnsupportedStep(phase, actions)
                WipePhase.Ready -> ReadyStep(current, actions)
                is WipePhase.Working -> WorkingStep(current, phase, actions)
                is WipePhase.Done -> DoneStep(current, phase, actions)
                is WipePhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: WipeActions) {
    FileDropZone(
        title = "Choisir des photos",
        hint = "JPG, PNG, WebP ou HEIC · une ou plusieurs à la fois",
        icon = EraserIcon,
        onClick = actions.onChoose,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Ce qu'une photo peut dire de toi")
        Spacer(Modifier.height(4.dp))
        FactRow(WipeKind.LOCATION, "L'endroit exact, à quelques mètres près", checked = true, index = 0, showSwitch = false) {}
        FactRow(WipeKind.DEVICE, "La marque, le modèle, le n° de série", checked = true, index = 1, showSwitch = false) {}
        FactRow(WipeKind.AUTHOR, "Ton nom, le copyright", checked = true, index = 2, showSwitch = false) {}
        FactRow(WipeKind.DATE, "Le jour et l'heure : gardés si tu veux", checked = false, index = 3, showSwitch = false) {}
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Les photos d'origine ne sont jamais modifiées : Gulp enregistre des copies propres dans la Galerie.",
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
        Text("Lecture des photos…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun UnsupportedStep(phase: WipePhase.Unsupported, actions: WipeActions) {
    ToolErrorCard(phase.message)
    SideButton("Choisir d'autres photos", EraserIcon, Modifier.fillMaxWidth(), onClick = actions.onChoose)
}

@Composable
private fun ReadyStep(state: WipeUiState, actions: WipeActions) {
    val photos = state.photos
    val facts = photos.map { it.facts }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel(if (photos.size > 1) "${photos.size} photos choisies" else "1 photo choisie")
        Spacer(Modifier.height(12.dp))
        PhotoStrip(photos.map { it.file.uri }, onRemove = { actions.onRemove(photos[it]) }, onAdd = actions.onAdd.takeIf { photos.size < 50 })
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Ce que tes photos racontent")
        Spacer(Modifier.height(6.dp))
        WipeKind.entries.forEachIndexed { index, kind ->
            Separator()
            FactRow(kind, WipeReport.detail(kind, facts), state.choices.wipes(kind), index) { on ->
                actions.onChoices(state.choices.with(kind, on))
            }
        }
    }
    MainButton(
        "Effacer et enregistrer",
        icon = EraserIcon,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 120),
        onClick = actions.onStart,
    )
    val heic = photos.count { it.file.mime.contains("hei", ignoreCase = true) || it.file.name.lowercase().let { n -> n.endsWith(".heic") || n.endsWith(".heif") } }
    val note = "Les photos d'origine ne sont pas modifiées. Gulp crée des copies propres, l'image reste identique." +
        if (heic > 0) " Les HEIC sont enregistrées en JPG." else ""
    Text(
        if (LocalSkin.current == Skin.TERMINAL) "# $note" else note,
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .staggeredEntrance(3, baseDelay = 120),
    )
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.TextPrimary.copy(alpha = if (LocalSkin.current == Skin.TERMINAL) 0.14f else 0.08f)),
    )
}

@Composable
private fun WorkingStep(state: WipeUiState, phase: WipePhase.Working, actions: WipeActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val current = phase.done.coerceAtMost(state.photos.size - 1).coerceAtLeast(0)
    val progress by animateFloatAsState(
        if (phase.total > 0) phase.done.toFloat() / phase.total else 0f,
        spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "wipeProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EraserSweep(state.photos.getOrNull(current)?.file?.uri, current)
        Spacer(Modifier.height(22.dp))
        val step = if (phase.total > 1) "Nettoyage ${(phase.done + 1).coerceAtMost(phase.total)}/${phase.total}…" else "Nettoyage…"
        Text(
            if (terminal) "> $step" else step,
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.photos.getOrNull(current)?.file?.name.orEmpty(),
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (terminal) TerminalProgress(progress) else ProgressBar(progress)
    }
    Hint(
        "Tu peux revenir à l'accueil : le nettoyage continue tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
private fun DoneStep(state: WipeUiState, phase: WipePhase.Done, actions: WipeActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val saved = phase.results.map { it.saved }
    val count = saved.size
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LockBadge()
        Spacer(Modifier.height(6.dp))
        val title = if (count > 1) "$count photos propres" else "Photo propre"
        Text(
            if (terminal) title.uppercase() else title,
            color = Palette.TextPrimary,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(0, baseDelay = 250),
        )
        Spacer(Modifier.height(4.dp))
        val where = if (count > 1) "enregistrées dans Galerie › Gulp" else "enregistrée dans Galerie › Gulp"
        Text(
            if (terminal) "# $where" else where,
            color = Palette.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(1, baseDelay = 250),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        CleanStrip(saved.map { it.uri })
        Spacer(Modifier.height(10.dp))
        WipeReport.doneLines(
            before = state.photos.map { it.facts },
            choices = state.choices,
            after = phase.results.map { it.after },
            reencoded = phase.results.count { it.reencoded },
        ).forEachIndexed { index, line -> ResultLine(line, index) }
    }
    MainButton(
        if (count > 1) "Partager les $count photos" else "Partager la photo",
        icon = Icons.Rounded.Share,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 200),
    ) { actions.onShare(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Galerie", Icons.Rounded.Image, Modifier.weight(1f)) { saved.firstOrNull()?.let(actions.onOpen) }
        SideButton("D'autres photos", EraserIcon, Modifier.weight(1f), onClick = actions.onReset)
    }
}

@Composable
private fun FailedStep(state: WipeUiState, phase: WipePhase.Failed, actions: WipeActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Le nettoyage n'a pas abouti")
    if (state.photos.isNotEmpty()) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
            SideButton("Autres photos", EraserIcon, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir d'autres photos", EraserIcon, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}
