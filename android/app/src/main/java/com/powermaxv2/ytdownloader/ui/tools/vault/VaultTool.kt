package com.powermaxv2.ytdownloader.ui.tools.vault

import android.app.Activity
import android.content.ActivityNotFoundException
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.vault.CodeStep
import com.powermaxv2.ytdownloader.core.vault.VaultCode
import com.powermaxv2.ytdownloader.core.vault.VaultController
import com.powermaxv2.ytdownloader.core.vault.VaultImport
import com.powermaxv2.ytdownloader.core.vault.VaultImportDone
import com.powermaxv2.ytdownloader.core.vault.VaultItem
import com.powermaxv2.ytdownloader.core.vault.VaultKind
import com.powermaxv2.ytdownloader.core.vault.VaultPhase
import com.powermaxv2.ytdownloader.core.vault.VaultUiState
import com.powermaxv2.ytdownloader.core.vault.VaultWords
import com.powermaxv2.ytdownloader.ui.AnimatedCheckbox
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.ProgressBar
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction

const val VAULT_TITLE = "Coffre-fort"
const val VAULT_COMMAND = "vault"
const val VAULT_SUBTITLE = "Cache tes photos et fichiers dans un coffre chiffré, ouvert par ton code."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class VaultActions(
    val onUnderstood: () -> Unit = {},
    val onDigit: (Char) -> Unit = {},
    val onErase: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onFingerprint: () -> Unit = {},
    val onForgot: (Boolean) -> Unit = {},
    val onDestroy: () -> Unit = {},
    val onConfirmDestroy: (Boolean) -> Unit = {},
    val onLock: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onTap: (VaultItem) -> Unit = {},
    val onLongTap: (VaultItem) -> Unit = {},
    val onFilter: (VaultKind?) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onExportSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onConfirmDelete: (Boolean) -> Unit = {},
    val onDeleteOriginals: () -> Unit = {},
    val onDismissImport: () -> Unit = {},
    val onSettings: (Boolean) -> Unit = {},
    val onFingerprintSwitch: (Boolean) -> Unit = {},
    val onNewCode: () -> Unit = {},
    val onCloseViewer: () -> Unit = {},
    val onExport: (VaultItem) -> Unit = {},
    val onOpenOutside: (VaultItem) -> Unit = {},
)

/**
 * The Coffre-fort (ToolRegistry.VAULT): photos and files sealed with AES-256-GCM in Gulp's private folder, opened
 * with a code (and the fingerprint if asked). It locks when the user leaves it or Gulp goes to the background, and
 * Android is asked to keep it out of screenshots and of the recent apps.
 */
@Composable
fun VaultTool(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = remember { VaultController.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val bitmaps by controller.thumbs.collectAsStateWithLifecycle()
    val picture by controller.picture.collectAsStateWithLifecycle()
    val text by controller.text.collectAsStateWithLifecycle()
    // The code being typed: in memory only, never in the saved state.
    var code by remember { mutableStateOf("") }
    val thumbs = remember(bitmaps) { bitmaps.mapValues { it.value.asImageBitmap() } }
    val toast: (String, Boolean) -> Unit = { message, ok -> vm.toast(message, success = ok) }

    LaunchedEffect(Unit) { controller.refresh() }
    LaunchedEffect(state.phase::class, (state.phase as? VaultPhase.Setup)?.step) { code = "" }

    // No screenshots, no preview in the recent apps while the tool is on screen.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            controller.lock()
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!controller.expectingReturn) controller.lock()
                Lifecycle.Event.ON_RESUME -> controller.expectingReturn = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        controller.expectingReturn = false
        controller.import(uris)
    }

    // Fingerprint asked by itself once, when the tool opens on the keypad (the key on the keypad asks again).
    var autoAsked by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase is VaultPhase.Locked, state.fingerprintOn) {
        if (!autoAsked && state.phase is VaultPhase.Locked && state.fingerprintOn) {
            autoAsked = true
            controller.unlockWithFingerprint(context) { toast(it, false) }
        }
    }

    val actions = VaultActions(
        onUnderstood = controller::acceptWarning,
        onDigit = { if (code.length < VaultCode.MAX) code += it },
        onErase = { code = code.dropLast(1) },
        onSubmit = {
            val typed = code
            code = ""
            controller.submit(typed)
        },
        onBack = controller::back,
        onFingerprint = { controller.unlockWithFingerprint(context) { toast(it, false) } },
        onForgot = controller::forgot,
        onDestroy = controller::destroy,
        onConfirmDestroy = controller::confirmDestroy,
        onLock = controller::lock,
        onAdd = {
            controller.expectingReturn = true
            try {
                picker.launch(arrayOf("*/*"))
            } catch (_: ActivityNotFoundException) {
                controller.expectingReturn = false
                toast("Aucun sélecteur de fichiers sur ce téléphone.", false)
            }
        },
        onTap = { if (state.selecting) controller.toggle(it) else controller.view(it) },
        onLongTap = controller::toggle,
        onFilter = controller::filter,
        onSelectAll = controller::selectAll,
        onClearSelection = controller::clearSelection,
        onExportSelected = { controller.export(toast = toast) },
        onDeleteSelected = controller::deleteSelected,
        onConfirmDelete = controller::confirmDelete,
        onDeleteOriginals = controller::deleteOriginals,
        onDismissImport = controller::dismissImport,
        onSettings = controller::settings,
        onFingerprintSwitch = { on -> controller.setFingerprint(context, on) { toast(it, true) } },
        onNewCode = controller::newCode,
        onCloseViewer = { controller.view(null) },
        onExport = { controller.export(it, toast) },
        onOpenOutside = { controller.openOutside(context, it, toast) },
    )

    BackHandler(enabled = state.viewing != null || state.selecting || (state.phase as? VaultPhase.Setup)?.changing == true) {
        when {
            state.viewing != null -> actions.onCloseViewer()
            state.selecting -> actions.onClearSelection()
            else -> actions.onBack()
        }
    }

    val weak = (state.phase as? VaultPhase.Setup)?.step == CodeStep.CODE && code.length >= VaultCode.MIN && VaultCode.isWeak(code)
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        ToolScaffold(vm, title = VAULT_TITLE, command = VAULT_COMMAND, subtitle = VAULT_SUBTITLE, scrollState = scroll) {
            VaultContent(state, entered = code.length, weak = weak, thumbs = thumbs, actions = actions)
        }
        VaultViewer(state, picture?.asImageBitmap(), text, actions)
    }
}

/** Body of the tool for a [state]; [entered] = digits typed so far. */
@Composable
fun ColumnScope.VaultContent(
    state: VaultUiState,
    entered: Int,
    actions: VaultActions,
    weak: Boolean = false,
    thumbs: Map<String, ImageBitmap> = emptyMap(),
) {
    AnimatedContent(
        targetState = state,
        contentKey = { s -> s.phase.let { if (it is VaultPhase.Setup) "setup-${it.step}" else it::class.simpleName } },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(140))
        },
        label = "vaultPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                is VaultPhase.Setup -> if (phase.step == CodeStep.WARNING) WarningStep(actions) else CodeStepScreen(phase, entered, weak, actions)
                is VaultPhase.Locked -> LockedStep(current, phase, entered, actions)
                is VaultPhase.Busy -> BusyStep(phase.label)
                VaultPhase.Open -> OpenStep(current, thumbs, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun Title(text: String, subtitle: String?) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (terminal) text.uppercase() else text,
            color = Palette.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(if (terminal) "# $it" else it, color = Palette.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun WarningStep(actions: VaultActions) {
    var understood by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        VaultPadlock(open = false, size = 132f)
    }
    Title("Ton coffre-fort", "Ce que tu y mets ne se lit qu'avec ton code.")
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WarningLine(0, "Tes photos et fichiers sont chiffrés (AES-256) et rangés dans Gulp, illisibles sans le code, même leurs noms.")
        WarningLine(1, "Après l'import, Gulp te propose d'effacer les originaux de la Galerie.")
        WarningLine(2, "Le coffre se referme dès que tu quittes l'outil ou Gulp.")
        WarningLine(3, "Si tu oublies ton code, personne ne peut ouvrir le coffre, pas même Gulp : tes fichiers sont perdus pour de bon.", danger = true)
        WarningLine(4, "Désinstaller Gulp efface aussi le coffre. Garde une copie de ce qui compte ailleurs.", danger = true)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(5, baseDelay = 120)
            .bounceClick { understood = !understood }
            .toolCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCheckbox(understood)
        Spacer(Modifier.width(12.dp))
        Text(
            skinText("J'ai compris : si j'oublie mon code, mes fichiers sont perdus."),
            color = Palette.TextPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
        )
    }
    MainButton("Choisir mon code", icon = Icons.Rounded.Password, enabled = understood, modifier = Modifier.staggeredEntrance(6, baseDelay = 120), onClick = actions.onUnderstood)
}

@Composable
private fun CodeStepScreen(phase: VaultPhase.Setup, entered: Int, weak: Boolean, actions: VaultActions) {
    val confirm = phase.step == CodeStep.CONFIRM
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        VaultPadlock(open = phase.changing, size = 96f)
    }
    Title(
        when {
            confirm -> "Retape le code"
            phase.changing -> "Nouveau code"
            else -> "Choisis ton code"
        },
        if (confirm) "Pour être sûr qu'il n'y a pas de faute de frappe." else "De ${VaultCode.MIN} à ${VaultCode.MAX} chiffres. Plus il est long, plus il est sûr.",
    )
    CodeBlock(
        entered = entered,
        shake = if (phase.mismatch) 1 else 0,
        message = when {
            phase.mismatch -> "Les deux codes ne sont pas pareils, recommence."
            weak -> "Facile à deviner : préfère des chiffres sans suite logique."
            else -> null
        },
        danger = phase.mismatch,
        enabled = true,
        fingerprint = null,
        actions = actions,
    )
    MainButton(
        if (confirm) (if (phase.changing) "Changer le code" else "Créer le coffre") else "Continuer",
        icon = if (confirm) Icons.Rounded.Lock else null,
        enabled = entered >= VaultCode.MIN,
        onClick = actions.onSubmit,
    )
    SideButton("Retour", Icons.AutoMirrored.Rounded.ArrowBack, Modifier.fillMaxWidth(), onClick = actions.onBack)
}

@Composable
private fun CodeBlock(
    entered: Int,
    shake: Int,
    message: String?,
    danger: Boolean,
    enabled: Boolean,
    fingerprint: (() -> Unit)?,
    actions: VaultActions,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(vertical = 22.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CodeDots(entered, shake)
        AnimatedVisibility(message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                skinText(message.orEmpty()),
                color = if (danger) Palette.Danger else Palette.TextSecondary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        VaultKeypad(enabled = enabled, onDigit = actions.onDigit, onErase = actions.onErase, onFingerprint = fingerprint)
    }
}

@Composable
private fun LockedStep(state: VaultUiState, phase: VaultPhase.Locked, entered: Int, actions: VaultActions) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        VaultPadlock(open = false, size = 104f)
    }
    Title("Coffre verrouillé", if (state.fingerprintOn) "Pose ton doigt ou tape ton code." else "Tape ton code pour l'ouvrir.")
    val waiting = phase.waitSeconds > 0
    CodeBlock(
        entered = entered,
        shake = phase.shake,
        message = when {
            waiting -> "Trop d'essais : attends ${VaultWords.wait(phase.waitSeconds)}"
            else -> phase.error
        },
        danger = waiting || phase.error != null,
        enabled = !waiting,
        fingerprint = if (state.fingerprintOn) actions.onFingerprint else null,
        actions = actions,
    )
    MainButton("Ouvrir", icon = Icons.Rounded.LockOpen, enabled = !waiting && entered >= VaultCode.MIN, onClick = actions.onSubmit)
    AnimatedContent(phase.forgot, label = "forgot") { forgot ->
        if (!forgot) {
            Text(
                skinText("Code oublié ?"),
                color = LocalAccent.current.start,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .bounceClick { actions.onForgot(true) },
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .toolCard()
                    .background(Palette.Danger.copy(alpha = 0.07f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    skinText("Sans le code, le coffre ne s'ouvre plus : ni Gulp ni personne ne peut le retrouver. Tu peux seulement tout effacer et repartir d'un coffre vide."),
                    color = Palette.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                MainButton("Tout effacer et recommencer", icon = Icons.Rounded.DeleteForever, onClick = actions.onDestroy)
                SideButton("Annuler, je me souviens", null, Modifier.fillMaxWidth()) { actions.onForgot(false) }
            }
        }
    }
}

@Composable
private fun BusyStep(label: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VaultPadlock(open = false, working = true, size = 150f)
    }
    Title(label, "La clé se calcule à partir de ton code, exprès lentement : deviner un code devient très long.")
}

@Composable
private fun OpenStep(state: VaultUiState, thumbs: Map<String, ImageBitmap>, actions: VaultActions) {
    // Header: the open padlock, how much is inside, lock and settings.
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0)
            .toolCard()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultPadlock(open = true, size = 54f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(skinText("Coffre ouvert"), color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${VaultWords.files(state.items.size)} · ${VaultWords.size(state.usedBytes)}",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
            )
        }
        RoundIcon(Icons.Rounded.Settings, "Réglages", active = state.settingsOpen) { actions.onSettings(!state.settingsOpen) }
        Spacer(Modifier.width(8.dp))
        RoundIcon(Icons.Rounded.Lock, "Verrouiller", onClick = actions.onLock)
    }

    AnimatedVisibility(state.settingsOpen, enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsCard(state, actions)
    }

    state.importing?.let { ImportingCard(it) }
    state.exporting?.let { (done, total) ->
        Column(Modifier.fillMaxWidth().toolCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(skinText("Sortie du coffre… ${done + 1} / $total"), color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            ProgressBar((done + 0.5f) / total)
        }
    }
    AnimatedVisibility(state.lastImport != null && state.importing == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        state.lastImport?.let { ImportDoneCard(it, actions) }
    }

    if (state.selecting) {
        SelectionBar(state, actions)
    } else if (!state.busy) {
        FileDropZone(
            title = "Cacher des photos ou des fichiers",
            hint = "Photos, vidéos, PDF, tout type de fichier",
            icon = Icons.Rounded.AddPhotoAlternate,
            modifier = Modifier.staggeredEntrance(1),
            onClick = actions.onAdd,
        )
    }

    if (state.kinds.size >= 2) {
        Row(Modifier.fillMaxWidth().staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Tout", state.filter == null) { actions.onFilter(null) }
            state.kinds.take(4).forEach { kind -> FilterChip(kind.label, state.filter == kind) { actions.onFilter(kind) } }
        }
    }

    val shown = state.shown
    if (shown.isEmpty()) {
        Text(
            skinText(if (state.items.isEmpty()) "Le coffre est vide. Ajoute une photo ou un fichier : il disparaît des regards." else "Rien de ce type ici."),
            color = Palette.TextTertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .staggeredEntrance(3),
        )
    } else {
        if (!state.selecting) Hint("Touche pour ouvrir, appui long pour choisir.", modifier = Modifier.padding(horizontal = 4.dp))
        VaultGrid(shown, thumbs, state.selected, onClick = actions.onTap, onLongClick = actions.onLongTap)
    }

    Hint(
        "Tout est chiffré sur le téléphone (AES-256-GCM), rien n'est envoyé. Les captures d'écran sont bloquées ici.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(42.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .background(if (active) accent.tint(0.22f) else Palette.Surface2, if (LocalSkin.current == Skin.TERMINAL) androidx.compose.foundation.shape.RoundedCornerShape(4.dp) else androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) accent.start else Palette.TextPrimary, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(LocalTokens.current.chipRadius)
    Text(
        skinText(text),
        color = if (selected) Palette.OnAccent else Palette.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .background(if (selected) accent.start else Palette.Surface2, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ImportingCard(importing: VaultImport) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultPadlock(open = false, working = true, size = 40f)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(skinText("Chiffrement ${importing.done + 1} / ${importing.total}"), color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(importing.name, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ProgressBar((importing.done + importing.fraction) / importing.total.coerceAtLeast(1))
    }
}

@Composable
private fun ImportDoneCard(done: VaultImportDone, actions: VaultActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skinText(if (done.added == 1) "1 fichier caché dans le coffre" else "${done.added} fichiers cachés dans le coffre"),
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Fermer",
                tint = Palette.TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .bounceClick(onClick = actions.onDismissImport),
            )
        }
        if (done.failed > 0) Hint("${VaultWords.files(done.failed)} n'ont pas pu être lus.", warning = true)
        when {
            done.originals == 0 -> Unit
            done.deleted == null -> {
                Hint("Les originaux sont encore sur le téléphone (Galerie, Téléchargements…).")
                SideButton("Effacer les originaux du téléphone", Icons.Rounded.DeleteOutline, Modifier.fillMaxWidth(), onClick = actions.onDeleteOriginals)
            }
            done.deleted == done.originals -> Hint("Originaux effacés : ils ne sont plus que dans le coffre.")
            else -> Hint(
                "${done.deleted} original(aux) effacé(s) sur ${done.originals}. L'app qui a donné les autres refuse : efface-les toi-même depuis la Galerie.",
                warning = true,
            )
        }
    }
}

@Composable
private fun SelectionBar(state: VaultUiState, actions: VaultActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skinText(if (state.selected.size == 1) "1 choisi" else "${state.selected.size} choisis"),
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            RoundIcon(Icons.Rounded.SelectAll, "Tout choisir", onClick = actions.onSelectAll)
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Rounded.Close, "Annuler", onClick = actions.onClearSelection)
        }
        if (!state.confirmDelete) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Sortir du coffre", Icons.Rounded.Upload, onClick = actions.onExportSelected)
                MiniAction("Supprimer", Icons.Rounded.DeleteOutline) { actions.onConfirmDelete(true) }
            }
        } else {
            Text(
                skinText("Supprimer pour de bon ${VaultWords.files(state.selected.size)} ? Ils ne seront plus nulle part."),
                color = Palette.Danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Oui, supprimer", Icons.Rounded.DeleteForever, onClick = actions.onDeleteSelected)
                MiniAction("Non", Icons.Rounded.Close) { actions.onConfirmDelete(false) }
            }
        }
    }
}

@Composable
private fun SettingsCard(state: VaultUiState, actions: VaultActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Réglages du coffre")
        if (state.fingerprintAvailable) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick { actions.onFingerprintSwitch(!state.fingerprintOn) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(skinText("Ouvrir avec l'empreinte"), color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Le code marche toujours aussi.", color = Palette.TextSecondary, fontSize = 12.5.sp)
                }
                AnimatedCheckbox(state.fingerprintOn)
            }
        } else {
            Hint("Empreinte : il faut Android 11 et une empreinte enregistrée dans le téléphone.")
        }
        SideButton("Changer le code", Icons.Rounded.LockReset, Modifier.fillMaxWidth(), onClick = actions.onNewCode)
        if (!state.confirmDestroy) {
            SideButton("Supprimer le coffre", Icons.Rounded.DeleteForever, Modifier.fillMaxWidth()) { actions.onConfirmDestroy(true) }
        } else {
            Text(
                skinText("Tout ce qui est dans le coffre sera effacé, sans retour. Sors d'abord ce que tu veux garder."),
                color = Palette.Danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Oui, tout effacer", Icons.Rounded.DeleteForever, onClick = actions.onDestroy)
                MiniAction("Non", Icons.Rounded.Close) { actions.onConfirmDestroy(false) }
            }
        }
    }
}

// MARK: - Viewer

/** A file of the vault, full screen over the tool: a picture (pinch to zoom), a text, or what to do with it. */
@Composable
fun VaultViewer(state: VaultUiState, picture: ImageBitmap?, text: String?, actions: VaultActions) {
    val item = state.viewing
    AnimatedVisibility(item != null, enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f), exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.96f)) {
        val shown = remember { mutableStateOf(item) }
        if (item != null) shown.value = item
        val current = shown.value ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${current.kind.label} · ${VaultWords.size(current.size)}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
                }
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .bounceClick(pressedScale = 0.8f, onClick = actions.onCloseViewer),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.viewError != null -> ViewerNote(current, state.viewError)
                    current.kind == VaultKind.PHOTO && picture != null -> ZoomableImage(picture, current.name)
                    current.kind == VaultKind.TEXT && text != null -> Text(
                        text,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                    current.kind == VaultKind.PHOTO || current.kind == VaultKind.TEXT -> VaultPadlock(open = true, working = true, size = 90f)
                    else -> ViewerNote(current, "Gulp ne lit pas ce type ici : ouvre-le avec une app, une copie temporaire est faite puis effacée au verrouillage.")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SideButton("Ouvrir avec…", Icons.AutoMirrored.Rounded.OpenInNew, Modifier.weight(1f)) { actions.onOpenOutside(current) }
                SideButton("Sortir", Icons.Rounded.Upload, Modifier.weight(1f)) { actions.onExport(current) }
            }
        }
    }
}

@Composable
private fun ViewerNote(item: VaultItem, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(kindIcon(item.kind), contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
        Text(message, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
private fun ZoomableImage(picture: ImageBitmap, name: String) {
    var scale by remember(picture) { mutableFloatStateOf(1f) }
    var offset by remember(picture) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Image(
        picture,
        contentDescription = name,
        modifier = Modifier
            .fillMaxSize()
            .transformable(transform)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
