package com.powermaxv2.ytdownloader.ui.tools.mylink

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.direct.Countries
import com.powermaxv2.ytdownloader.core.direct.Country
import com.powermaxv2.ytdownloader.core.direct.DirectNumbers
import com.powermaxv2.ytdownloader.core.mylink.MyImageFormat
import com.powermaxv2.ytdownloader.core.mylink.MyLink
import com.powermaxv2.ytdownloader.core.mylink.MyLinkImage
import com.powermaxv2.ytdownloader.core.mylink.MyLinks
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import com.powermaxv2.ytdownloader.core.qr.QrImage
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.direct.CountryButton
import com.powermaxv2.ytdownloader.ui.tools.direct.CountryChips
import com.powermaxv2.ytdownloader.ui.tools.direct.NumberVerdict
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.qr.QrPicture
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

const val MYLINK_TITLE = "Mon lien WhatsApp"
const val MYLINK_COMMAND = "mylink"
const val MYLINK_SUBTITLE = "Ton lien wa.me et son QR code, pour que n'importe qui t'écrive sur WhatsApp en un geste."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class MyLinkActions(
    val onNumber: (String) -> Unit = {},
    val onCountries: () -> Unit = {},
    val onCountry: (Country) -> Unit = {},
    val onKeep: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onFormat: (MyImageFormat) -> Unit = {},
    val onSave: () -> Unit = {},
    val onShareLink: () -> Unit = {},
    val onShareImage: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onTest: () -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class MyLinkUiState(
    val number: String = "",
    val country: Country = Countries.france,
    val message: String = "",
    /** The number kept on the phone (international digits), null before the first "Garder". */
    val keptDigits: String? = null,
    /** The number form is open again after "Modifier". */
    val editing: Boolean = false,
    val countriesOpen: Boolean = false,
    val format: MyImageFormat = MyImageFormat.CARD,
    /** The last picture saved, and the link + format it was made for (saving the same one twice reuses it). */
    val saved: SavedFile? = null,
    val savedFor: String? = null,
) {
    val link: MyLink get() = MyLinks.build(number, country, message)

    /** The number is the kept one and the form is closed: only the compact card shows. */
    val compact: Boolean get() = !editing && keptDigits != null && (link as? MyLink.Ready)?.digits == keptDigits

    val imageKey: String? get() = (link as? MyLink.Ready)?.let { "${it.link}|$format" }
    val isSaved: Boolean get() = saved != null && savedFor == imageKey
}

/**
 * Mon lien WhatsApp (ToolRegistry.MY_LINK): the user's number, kept on the phone after the first time, becomes
 * WhatsApp's official wa.me link and a QR code (with a message already written for whoever opens it), to copy,
 * share, or save as a card to print. Nothing is sent anywhere: the link only works once someone opens it.
 */
@Composable
fun MyLinkTool(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var state by remember {
        val kept = prefs.getString(KEY_DIGITS, null)?.takeIf { MyLinks.restore(it).isNotEmpty() }
        val iso = prefs.getString(KEY_COUNTRY, null) ?: Countries.default(Locale.getDefault().country).iso
        mutableStateOf(
            MyLinkUiState(
                number = MyLinks.restore(kept),
                country = Countries.byIso(iso) ?: Countries.france,
                message = prefs.getString(KEY_MESSAGE, null).orEmpty(),
                keptDigits = kept,
            ),
        )
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

    /** Saves the current picture once (the same one is not saved twice), then [then] with it. */
    fun saveImage(then: (SavedFile) -> Unit) {
        val ready = state.link as? MyLink.Ready ?: return
        val key = state.imageKey
        state.saved?.takeIf { state.savedFor == key }?.let { then(it); return }
        val format = state.format
        val message = state.message
        scope.launch {
            val saved = runCatching {
                val bitmap = withContext(Dispatchers.Default) { MyLinkImage.render(ready, message, format) } ?: error("lien trop long")
                val file = QrImage.writePng(bitmap, File(context.cacheDir, "gulp-work/mylink"), MyLinks.fileName(ready.digits, format))
                bitmap.recycle()
                MediaExporter.export(context, file).also { file.delete() }
            }
            saved.onSuccess {
                state = state.copy(saved = it, savedFor = key)
                then(it)
            }.onFailure {
                vm.toast("L'image n'a pas pu être enregistrée.", success = false)
            }
        }
    }

    val actions = MyLinkActions(
        onNumber = { state = state.copy(number = it.take(40)) },
        onCountries = { state = state.copy(countriesOpen = !state.countriesOpen) },
        onCountry = {
            prefs.edit().putString(KEY_COUNTRY, it.iso).apply()
            state = state.copy(country = it, countriesOpen = false)
        },
        onKeep = {
            val ready = state.link as? MyLink.Ready
            if (ready != null) {
                prefs.edit().putString(KEY_DIGITS, ready.digits).apply()
                state = state.copy(keptDigits = ready.digits, editing = false, countriesOpen = false, number = "+${ready.digits}")
            }
        },
        onEdit = { state = state.copy(editing = true) },
        onMessage = {
            val message = it.take(MyLinks.MAX_MESSAGE)
            prefs.edit().putString(KEY_MESSAGE, message).apply()
            state = state.copy(message = message)
        },
        onFormat = { state = state.copy(format = it) },
        onSave = { withStorage { saveImage { vm.toast("Enregistré dans ${it.location}") } } },
        onShareLink = {
            (state.link as? MyLink.Ready)?.let { share(context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, MyLinks.shareText(it.link)), vm) }
        },
        onShareImage = {
            withStorage {
                saveImage { file ->
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType(file.mime)
                        .putExtra(Intent.EXTRA_STREAM, file.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    share(context, intent, vm)
                }
            }
        },
        onCopy = {
            (state.link as? MyLink.Ready)?.let {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Mon lien WhatsApp", it.link))
                // Android 13+ shows its own "Copié" bubble.
                if (Build.VERSION.SDK_INT < 33) vm.toast("Lien copié")
            }
        },
        onTest = {
            (state.link as? MyLink.Ready)?.let {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.link)))
                } catch (_: ActivityNotFoundException) {
                    vm.toast("Aucune appli ne peut ouvrir ce lien", success = false)
                }
            }
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = MYLINK_TITLE, command = MYLINK_COMMAND, subtitle = MYLINK_SUBTITLE, scrollState = scroll) {
        MyLinkContent(state, actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.MyLinkContent(state: MyLinkUiState, actions: MyLinkActions) {
    val link = state.link
    val ready = link as? MyLink.Ready

    // The number: the compact card once kept, else the form.
    AnimatedContent(
        targetState = state.compact,
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 80)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(140)) + scaleOut(targetScale = 0.96f))
        },
        modifier = Modifier.fillMaxWidth().staggeredEntrance(0, baseDelay = 40),
        label = "myNumber",
    ) { compact ->
        if (compact && ready != null) {
            IdentityCard(ready, onEdit = actions.onEdit)
        } else {
            NumberForm(state, actions)
        }
    }

    // Optional message people find already written.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrField(
            label = "Message prêt (facultatif)",
            value = state.message,
            onChange = actions.onMessage,
            singleLine = false,
            placeholder = "Ce que la personne trouvera déjà écrit en t'ouvrant.",
            maxLength = MyLinks.MAX_MESSAGE,
        )
        QuickChips(MyLinks.QUICK, onPick = actions.onMessage)
    }

    // The code, the hero of the screen.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val matrix = remember(ready?.link) { ready?.takeIf { it.qrFits }?.let { QrCodec.encode(it.link) } }
        AnimatedContent(
            targetState = matrix == null,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
            label = "myCode",
        ) { empty ->
            if (empty || matrix == null) {
                CodePlaceholder(
                    when {
                        ready != null -> "Ce message est trop long pour un QR code lisible : raccourcis-le (les émojis prennent beaucoup de place)."
                        else -> "Écris ton numéro : ton QR code apparaît ici."
                    },
                )
            } else {
                ScanHalo { QrPicture(matrix, Modifier.widthIn(max = 250.dp).fillMaxWidth()) }
            }
        }
        Spacer(Modifier.height(14.dp))
        LinkLine(ready)
        AnimatedVisibility(ready != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(14.dp))
                OptionChips(
                    MyImageFormat.entries,
                    state.format,
                    { if (it == MyImageFormat.CARD) "Carte à imprimer" else "QR seul" },
                    actions.onFormat,
                )
            }
        }
    }

    val canImage = ready?.qrFits == true
    MainButton(
        if (state.isSaved) "Enregistrée dans la Galerie" else "Enregistrer l'image",
        icon = if (state.isSaved) Icons.Rounded.Check else Icons.Rounded.Download,
        subtitle = if (state.format == MyImageFormat.CARD) "Carte 1080 × 1350 avec ton numéro" else "QR code seul, haute définition",
        enabled = canImage,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 80),
        onClick = actions.onSave,
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 80), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager le lien", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShareLink() }
        SideButton("Partager l'image", Icons.Rounded.Image, Modifier.weight(1f)) { if (canImage) actions.onShareImage() }
    }
    AnimatedVisibility(ready != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            MiniAction("Copier le lien", Icons.Rounded.ContentCopy, onClick = actions.onCopy)
            MiniAction("Tester", Icons.AutoMirrored.Rounded.OpenInNew, onClick = actions.onTest)
        }
    }
    if (state.isSaved) {
        Hint("L'image est dans ${state.saved?.location} : la partager ne l'enregistre pas une deuxième fois.", modifier = Modifier.padding(horizontal = 8.dp))
    }
    Hint(
        "Ton numéro reste sur ce téléphone. Le lien wa.me est celui de WhatsApp : la personne qui le scanne voit ton numéro " +
            "et la discussion s'ouvre avec ton message déjà écrit, qu'elle peut changer avant d'envoyer.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/** Country, number, what Gulp reads in it, and "Garder ce numéro". */
@Composable
private fun NumberForm(state: MyLinkUiState, actions: MyLinkActions) {
    val ready = state.link as? MyLink.Ready
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel(if (state.keptDigits == null) "Ton numéro WhatsApp" else "Changer de numéro")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CountryButton(state.country, state.countriesOpen, onClick = actions.onCountries)
            QrField(
                label = "Numéro",
                value = state.number,
                onChange = actions.onNumber,
                keyboard = KeyboardType.Phone,
                placeholder = "06 12 34 56 78",
                maxLength = 40,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(
            state.countriesOpen,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CountryChips(state.country, onPick = actions.onCountry)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(state.number.isNotEmpty(), enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                MiniAction("Effacer", Icons.Rounded.Close) { actions.onNumber("") }
            }
        }
        NumberVerdict(DirectNumbers.parse(state.number, state.country))
        PrimaryButton(
            text = "Garder ce numéro",
            icon = Icons.Rounded.Save,
            subtitle = ready?.pretty ?: "Il restera sur ce téléphone",
            enabled = ready != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.onKeep,
        )
    }
}

// MARK: - Intents

private const val PREFS = "mylink"
private const val KEY_DIGITS = "digits"
private const val KEY_COUNTRY = "country"
private const val KEY_MESSAGE = "message"

private fun share(context: Context, intent: Intent, vm: MainViewModel) {
    try {
        context.startActivity(Intent.createChooser(intent, "Partager"))
    } catch (_: ActivityNotFoundException) {
        vm.toast("Partage impossible.", success = false)
    }
}
