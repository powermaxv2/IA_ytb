package com.powermaxv2.ytdownloader.ui.tools.qr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import com.powermaxv2.ytdownloader.core.qr.QrDraft
import com.powermaxv2.ytdownloader.core.qr.QrHistory
import com.powermaxv2.ytdownloader.core.qr.QrHistoryEntry
import com.powermaxv2.ytdownloader.core.qr.QrImage
import com.powermaxv2.ytdownloader.core.qr.QrKind
import com.powermaxv2.ytdownloader.core.qr.QrMatrix
import com.powermaxv2.ytdownloader.core.qr.QrPayload
import com.powermaxv2.ytdownloader.core.qr.ScanResult
import com.powermaxv2.ytdownloader.core.qr.WifiSecurity
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val QR_TITLE = "QR code"
const val QR_COMMAND = "qr"
const val QR_SUBTITLE = "Lis un QR code avec l'appareil photo, ou crée le tien pour un lien, un texte, un Wi-Fi ou un contact."

/** Where the scanner is. */
sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Camera : ScanPhase
    data object Reading : ScanPhase
    data class Result(val payload: QrPayload) : ScanPhase
    data class Failed(val message: String) : ScanPhase
}

/** Everything the screen shows. [saved] is the image of [savedFor] (the code text) once saved to the Gallery. */
data class QrUiState(
    val create: Boolean = false,
    val phase: ScanPhase = ScanPhase.Idle,
    val draft: QrDraft = QrDraft(),
    val history: List<QrHistoryEntry> = emptyList(),
    val now: Long = 0L,
    val torch: Boolean = false,
    val saved: SavedFile? = null,
    val savedFor: String? = null,
) {
    val code: String? get() = draft.encoded()
    val isSaved: Boolean get() = saved != null && savedFor == code
}

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class QrActions(
    val onTab: (Boolean) -> Unit = {},
    val onCamera: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onImage: () -> Unit = {},
    val onScanned: (ScanResult) -> Unit = {},
    val onCameraError: (String) -> Unit = {},
    val onTorch: () -> Unit = {},
    val onMain: (QrPayload) -> Unit = {},
    val onCopy: (label: String, value: String, secret: Boolean) -> Unit = { _, _, _ -> },
    val onShareText: (String) -> Unit = {},
    val onHistory: (QrHistoryEntry) -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onDraft: (QrDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onShareImage: () -> Unit = {},
)

/**
 * The QR code tool (ToolRegistry.QR_CODE). Scanner: live camera (CAMERA permission asked on first use) or a picture
 * from the phone; Créer: a link, a text, a Wi-Fi network or a contact as a code, saved as a PNG in Pictures/Gulp.
 * Nothing leaves the phone, and a scanned link opens only when the user taps it.
 */
@Composable
fun QrTool(vm: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(QrUiState(history = QrHistory.decode(vm.prefs.qrHistory), now = System.currentTimeMillis()))
    }

    fun show(result: ScanResult) {
        val entry = QrHistoryEntry(result.text, result.format, System.currentTimeMillis())
        val history = QrHistory.add(state.history, entry)
        vm.prefs.qrHistory = QrHistory.encode(history)
        state = state.copy(phase = ScanPhase.Result(entry.payload), history = history, now = entry.time, torch = false)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            state = state.copy(phase = ScanPhase.Camera)
        } else {
            vm.toast("Sans l'appareil photo, Gulp peut lire le code sur une image (capture d'écran, photo).", success = false)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = state.copy(phase = ScanPhase.Reading, create = false)
        scope.launch {
            val read = runCatching { QrImage.decode(context, uri) }.getOrNull()
            if (read != null) {
                show(read)
            } else {
                state = state.copy(
                    phase = ScanPhase.Failed(
                        "Aucun code trouvé sur cette image. Essayez une image plus nette, où le code est bien visible et pas coupé.",
                    ),
                )
            }
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer l'image.", success = false)
        afterPermission = null
    }

    fun withStorage(block: () -> Unit) {
        val needs = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            afterPermission = block
            storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            block()
        }
    }

    /** Saves the current code once (the same code is not saved twice), then [then] with the saved image. */
    fun saveCode(then: (SavedFile) -> Unit) {
        val text = state.code ?: return
        state.saved?.takeIf { state.savedFor == text }?.let { then(it); return }
        val draft = state.draft
        scope.launch {
            val saved = runCatching {
                val matrix = QrCodec.encode(text) ?: error("code trop long")
                val bitmap = withContext(Dispatchers.Default) { QrImage.render(matrix) }
                val file = QrImage.writePng(bitmap, File(context.cacheDir, "gulp-work/qr"), draft.fileName)
                bitmap.recycle()
                MediaExporter.export(context, file).also { file.delete() }
            }
            saved.onSuccess {
                state = state.copy(saved = it, savedFor = text)
                then(it)
            }.onFailure {
                vm.toast("L'image n'a pas pu être enregistrée.", success = false)
            }
        }
    }

    val actions = remember {
        QrActions(
            onTab = { create -> state = state.copy(create = create, torch = false, phase = if (state.phase == ScanPhase.Camera) ScanPhase.Idle else state.phase) },
            onCamera = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    state = state.copy(phase = ScanPhase.Camera, create = false)
                } else {
                    camera.launch(Manifest.permission.CAMERA)
                }
            },
            onStop = { state = state.copy(phase = ScanPhase.Idle, torch = false, now = System.currentTimeMillis()) },
            onImage = {
                try {
                    picker.launch("image/*")
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucune galerie sur ce téléphone.", success = false)
                }
            },
            onScanned = { result ->
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                show(result)
            },
            onCameraError = { state = state.copy(phase = ScanPhase.Failed(it), torch = false) },
            onTorch = { state = state.copy(torch = !state.torch) },
            onMain = { payload -> mainAction(context, payload, vm) },
            onCopy = { label, value, secret -> copy(context, label, value, secret, vm) },
            onShareText = { text -> shareText(context, text, vm) },
            onHistory = { entry -> state = state.copy(phase = ScanPhase.Result(entry.payload), create = false, now = System.currentTimeMillis()) },
            onClearHistory = {
                vm.prefs.qrHistory = null
                state = state.copy(history = emptyList())
            },
            onDraft = { draft -> state = state.copy(draft = draft) },
            onSave = { withStorage { saveCode { vm.toast("Enregistré dans ${it.location}") } } },
            onShareImage = { withStorage { saveCode { shareImage(context, it, vm) } } },
        )
    }

    BackHandler(enabled = !state.create && state.phase != ScanPhase.Idle) { actions.onStop() }

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class, state.create) { scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = QR_TITLE, command = QR_COMMAND, subtitle = QR_SUBTITLE, scrollState = scroll) {
        QrContent(state, actions) {
            QrCameraPreview(state.torch, actions.onScanned, actions.onCameraError, Modifier.fillMaxSize())
        }
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests); [camera] fills the live viewfinder. */
@Composable
fun ColumnScope.QrContent(state: QrUiState, actions: QrActions, camera: @Composable BoxScope.() -> Unit) {
    TabSwitch(state.create, actions.onTab)
    AnimatedContent(
        targetState = state,
        contentKey = { if (it.create) "create" else it.phase::class.simpleName + ((it.phase as? ScanPhase.Result)?.payload?.raw ?: "") },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "qrPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (current.create) {
                CreateStep(current, actions)
            } else {
                when (val phase = current.phase) {
                    ScanPhase.Idle -> IdleStep(current, actions)
                    ScanPhase.Camera -> CameraStep(current, actions, camera)
                    ScanPhase.Reading -> ReadingStep()
                    is ScanPhase.Result -> ResultStep(current, phase.payload, actions)
                    is ScanPhase.Failed -> FailedStep(phase, actions)
                }
            }
        }
    }
}

private val sample: QrMatrix? by lazy { QrCodec.encode("WIFI:T:WPA;S:Maison-5G;P:soleil-2024;;") }

// MARK: - Scanner

@Composable
private fun IdleStep(state: QrUiState, actions: QrActions) {
    Viewfinder(
        title = "Scanner avec l'appareil photo",
        hint = "Gulp lit le code tout seul dès qu'il le voit",
        height = 300.dp,
        onClick = actions.onCamera,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 160),
    ) { SampleScene(sample, "Wi-Fi invités", compact = true) }
    SideButton("Lire depuis une image", Icons.Rounded.Image, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 160), onClick = actions.onImage)
    HistoryCard(state.history, state.now, actions.onHistory, actions.onClearHistory, index = 4)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(5, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Gulp sait lire")
        Spacer(Modifier.height(8.dp))
        ReadsRow(Icons.Rounded.Link, "Liens", "ouvre le site", 0)
        ReadsRow(Icons.Rounded.Wifi, "Wi-Fi", "réseau + mot de passe", 1)
        ReadsRow(Icons.Rounded.Person, "Contacts", "ajoute au carnet", 2)
        ReadsRow(Icons.Rounded.TextFields, "Textes", "copier · partager", 3)
        ReadsRow(Icons.Rounded.ViewWeek, "Codes-barres", "EAN · UPC · Code 128", 4)
    }
    QrHint(
        "Tout se passe sur le téléphone, hors ligne. Gulp n'ouvre jamais un lien sans que vous le touchiez.",
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun CameraStep(state: QrUiState, actions: QrActions, camera: @Composable BoxScope.() -> Unit) {
    Viewfinder(
        title = "Visez le code",
        hint = "Rapprochez-vous s'il est petit",
        height = 440.dp,
        torch = state.torch,
        onTorch = actions.onTorch,
        scene = camera,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Une image", Icons.Rounded.Image, Modifier.weight(1f), onClick = actions.onImage)
        SideButton("Annuler", null, Modifier.weight(1f), onClick = actions.onStop)
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
        Text("Lecture de l'image…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun FailedStep(phase: ScanPhase.Failed, actions: QrActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(phase.message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(phase.message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
    MainButton("Scanner avec l'appareil photo", icon = Icons.Rounded.CameraAlt, onClick = actions.onCamera)
    SideButton("Choisir une autre image", Icons.Rounded.Image, Modifier.fillMaxWidth(), onClick = actions.onImage)
}

/** Label and icon of the main button for a scanned code. */
private fun mainLabel(payload: QrPayload): Pair<String, ImageVector> = when (payload) {
    is QrPayload.Link -> "Ouvrir le lien" to Icons.Rounded.OpenInNew
    is QrPayload.Wifi -> "Se connecter" to Icons.Rounded.Wifi
    is QrPayload.Contact -> "Ajouter aux contacts" to Icons.Rounded.PersonAdd
    is QrPayload.Email -> "Écrire l'e-mail" to Icons.Rounded.Email
    is QrPayload.Phone -> "Appeler" to Icons.Rounded.Phone
    is QrPayload.Sms -> "Écrire le SMS" to Icons.Rounded.Sms
    is QrPayload.Geo -> "Voir sur une carte" to Icons.Rounded.Place
    is QrPayload.Product -> "Chercher ce produit" to Icons.Rounded.Search
    is QrPayload.Text -> "Copier le texte" to Icons.Rounded.ContentCopy
}

@Composable
private fun ResultStep(state: QrUiState, payload: QrPayload, actions: QrActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KindBadge(payload)
        Text(
            payload.title,
            color = Palette.TextPrimary,
            fontSize = if (terminal) 24.sp else 27.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.staggeredEntrance(0, baseDelay = 120),
        )
        var row = 1
        fun next() = row++
        when (payload) {
            is QrPayload.Link -> ValueRow("Adresse complète", payload.url, next()) { actions.onCopy("Lien", payload.url, false) }
            is QrPayload.Wifi -> {
                if (payload.password.isNotEmpty()) ValueRow("Mot de passe", payload.password, next()) { actions.onCopy("Mot de passe", payload.password, true) }
                ValueRow("Sécurité", payload.security.label + if (payload.hidden) " · réseau masqué" else "", next(), null)
            }
            is QrPayload.Contact -> {
                payload.phones.forEach { p -> ValueRow("Téléphone", p, next()) { actions.onCopy("Téléphone", p, false) } }
                payload.emails.forEach { e -> ValueRow("E-mail", e, next()) { actions.onCopy("E-mail", e, false) } }
                if (payload.organization.isNotEmpty()) ValueRow("Société", payload.organization, next(), null)
                if (payload.url.isNotEmpty()) ValueRow("Site", payload.url, next()) { actions.onCopy("Site", payload.url, false) }
            }
            is QrPayload.Email -> {
                if (payload.subject.isNotEmpty()) ValueRow("Objet", payload.subject, next(), null)
                if (payload.body.isNotEmpty()) ValueRow("Message", payload.body, next()) { actions.onCopy("Message", payload.body, false) }
            }
            is QrPayload.Phone -> ValueRow("Numéro", payload.number, next()) { actions.onCopy("Numéro", payload.number, false) }
            is QrPayload.Sms -> if (payload.message.isNotEmpty()) ValueRow("Message", payload.message, next()) { actions.onCopy("Message", payload.message, false) }
            is QrPayload.Geo -> ValueRow("Coordonnées", payload.title, next()) { actions.onCopy("Coordonnées", payload.title, false) }
            is QrPayload.Product -> ValueRow("Format", payload.format, next(), null)
            is QrPayload.Text -> if (payload.raw.length > payload.title.length) ValueRow("Texte complet", payload.raw, next()) { actions.onCopy("Texte", payload.raw, false) }
        }
        if (payload is QrPayload.Link && !payload.url.startsWith("https://", ignoreCase = true)) {
            QrHint("Lien non sécurisé (http) : vérifiez l'adresse avant de l'ouvrir.")
        }
    }
    val (label, icon) = mainLabel(payload)
    MainButton(label, icon = icon, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onMain(payload) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Copier", Icons.Rounded.ContentCopy, Modifier.weight(1f)) {
            actions.onCopy(payload.kindLabel, payload.raw, payload is QrPayload.Wifi)
        }
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShareText(payload.raw) }
    }
    SideButton("Scanner un autre code", Icons.Rounded.CameraAlt, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onCamera)
    HistoryCard(state.history.drop(1), state.now, actions.onHistory, actions.onClearHistory, index = 4)
}

// MARK: - Créer

@Composable
private fun CreateStep(state: QrUiState, actions: QrActions) {
    val draft = state.draft
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Que mettre dans le code ?")
        KindChips(draft.kind, { actions.onDraft(draft.copy(kind = it)) })
        when (draft.kind) {
            QrKind.LINK -> QrField("Adresse du site", draft.link, { actions.onDraft(draft.copy(link = it)) }, keyboard = KeyboardType.Uri, placeholder = "www.exemple.fr")
            QrKind.TEXT -> QrField(
                "Texte",
                draft.text,
                { actions.onDraft(draft.copy(text = it)) },
                singleLine = false,
                placeholder = "Un message, un code promo…",
                maxLength = QrCodec.MAX_CHARS,
            )
            QrKind.WIFI -> {
                QrField("Nom du réseau", draft.ssid, { actions.onDraft(draft.copy(ssid = it)) }, placeholder = "Maison-5G", maxLength = 32)
                QrField("Mot de passe", draft.password, { actions.onDraft(draft.copy(password = it)) }, secret = true, maxLength = 63)
                OptionChips(
                    listOf(WifiSecurity.WPA, WifiSecurity.WEP),
                    draft.security,
                    { if (it == WifiSecurity.WPA) "WPA / WPA2 / WPA3" else "WEP (ancien)" },
                    { actions.onDraft(draft.copy(security = it)) },
                )
            }
            QrKind.CONTACT -> {
                QrField("Nom", draft.name, { actions.onDraft(draft.copy(name = it)) }, placeholder = "Camille Martin", maxLength = 80)
                QrField("Téléphone", draft.phone, { actions.onDraft(draft.copy(phone = it)) }, keyboard = KeyboardType.Phone, maxLength = 30)
                QrField("E-mail", draft.email, { actions.onDraft(draft.copy(email = it)) }, keyboard = KeyboardType.Email, maxLength = 100)
            }
        }
    }
    val code = state.code
    val matrix = remember(code) { code?.let(QrCodec::encode) }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = matrix == null,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
            label = "qrPreview",
        ) { empty ->
            if (empty || matrix == null) {
                EmptyCode(if (code != null) "Ce texte est trop long pour un QR code lisible : raccourcissez-le." else "Remplissez le champ : votre code apparaît ici.")
            } else {
                QrPicture(matrix, Modifier.widthIn(max = 250.dp).fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))
        QrHint(draft.effect, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
    val ready = matrix != null
    MainButton(
        if (state.isSaved) "Enregistré dans la Galerie" else "Enregistrer dans la Galerie",
        icon = if (state.isSaved) Icons.Rounded.Check else Icons.Rounded.Download,
        enabled = ready,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 120),
        onClick = actions.onSave,
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 120), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager l'image", Icons.Rounded.Share, Modifier.weight(1f)) { if (ready) actions.onShareImage() }
        SideButton("Copier le texte", Icons.Rounded.ContentCopy, Modifier.weight(1f)) {
            code?.let { actions.onCopy("Texte du code", it, draft.kind == QrKind.WIFI) }
        }
    }
    if (state.isSaved) {
        QrHint("L'image est dans ${state.saved?.location} : partager l'envoie sans l'enregistrer une deuxième fois.", Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun EmptyCode(text: String) {
    val accent = LocalAccent.current.start
    Column(
        Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 4.dp else 22.dp))
            .background(Palette.TextPrimary.copy(alpha = 0.05f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}

// MARK: - Intents

private fun start(context: Context, intent: Intent, vm: MainViewModel, failure: String): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        vm.toast(failure, success = false)
        false
    } catch (e: SecurityException) {
        vm.toast(failure, success = false)
        false
    }

private fun mainAction(context: Context, payload: QrPayload, vm: MainViewModel) {
    when (payload) {
        is QrPayload.Link -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(payload.url)), vm, "Aucune app ne peut ouvrir ce lien.")
        is QrPayload.Wifi -> connectWifi(context, payload, vm)
        is QrPayload.Contact -> {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.NAME, payload.name)
            payload.phones.getOrNull(0)?.let { intent.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            payload.phones.getOrNull(1)?.let { intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
            payload.emails.getOrNull(0)?.let { intent.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            if (payload.organization.isNotEmpty()) intent.putExtra(ContactsContract.Intents.Insert.COMPANY, payload.organization)
            start(context, intent, vm, "Aucune app Contacts sur ce téléphone.")
        }
        is QrPayload.Email -> {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(payload.address))
                .putExtra(Intent.EXTRA_SUBJECT, payload.subject)
                .putExtra(Intent.EXTRA_TEXT, payload.body)
            start(context, intent, vm, "Aucune app d'e-mail sur ce téléphone.")
        }
        is QrPayload.Phone -> start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", payload.number, null)), vm, "Aucune app Téléphone.")
        is QrPayload.Sms -> start(
            context,
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", payload.number, null)).putExtra("sms_body", payload.message),
            vm,
            "Aucune app SMS.",
        )
        is QrPayload.Geo -> start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${payload.latitude},${payload.longitude}?q=${payload.latitude},${payload.longitude}")),
            vm,
            "Aucune app de cartes sur ce téléphone.",
        )
        is QrPayload.Product -> start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(payload.code))),
            vm,
            "Aucun navigateur sur ce téléphone.",
        )
        is QrPayload.Text -> copy(context, "Texte", payload.raw, false, vm)
    }
}

/**
 * Android 11+: the system "Enregistrer ce réseau ?" sheet (no permission needed). Older phones, WEP networks or a
 * refused password: the password is copied and the Wi-Fi settings open, so the user only has to paste it.
 */
private fun connectWifi(context: Context, wifi: QrPayload.Wifi, vm: MainViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.security != WifiSecurity.WEP && wifi.ssid.isNotEmpty()) {
        val suggestion = runCatching {
            WifiNetworkSuggestion.Builder()
                .setSsid(wifi.ssid)
                .setIsHiddenSsid(wifi.hidden)
                .apply { if (wifi.security == WifiSecurity.WPA) setWpa2Passphrase(wifi.password) }
                .build()
        }.getOrNull()
        if (suggestion != null) {
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
            if (start(context, intent, vm, "Ce téléphone ne propose pas d'ajouter le réseau : ouvrez les réglages Wi-Fi.")) return
        }
    }
    if (wifi.password.isNotEmpty()) copy(context, "Mot de passe", wifi.password, true, vm, quiet = true)
    val opened = start(context, Intent(Settings.ACTION_WIFI_SETTINGS), vm, "Réglages Wi-Fi introuvables.")
    if (opened) {
        vm.toast(if (wifi.password.isNotEmpty()) "Mot de passe copié : choisissez ${wifi.ssid} et collez-le." else "Choisissez ${wifi.ssid} dans la liste.")
    }
}

private fun copy(context: Context, label: String, value: String, secret: Boolean, vm: MainViewModel, quiet: Boolean = false) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, value)
    // Android 13+: a password is hidden in the clipboard preview.
    if (secret && Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
    // Android 13+ shows its own "Copié" bubble.
    if (!quiet && Build.VERSION.SDK_INT < 33) vm.toast("$label copié")
}

private fun shareText(context: Context, text: String, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    start(context, Intent.createChooser(intent, "Partager"), vm, "Partage impossible.")
}

private fun shareImage(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(file.mime)
        .putExtra(Intent.EXTRA_STREAM, file.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    start(context, Intent.createChooser(intent, "Partager"), vm, "Partage impossible.")
}
