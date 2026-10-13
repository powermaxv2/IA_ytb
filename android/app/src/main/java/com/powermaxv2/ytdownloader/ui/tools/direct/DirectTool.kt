package com.powermaxv2.ytdownloader.ui.tools.direct

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.core.direct.Countries
import com.powermaxv2.ytdownloader.core.direct.Country
import com.powermaxv2.ytdownloader.core.direct.DirectNumber
import com.powermaxv2.ytdownloader.core.direct.DirectNumbers
import com.powermaxv2.ytdownloader.core.direct.DirectRecents
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import java.util.Locale

const val DIRECT_TITLE = "Message direct"
const val DIRECT_COMMAND = "direct"
const val DIRECT_SUBTITLE = "Écris sur WhatsApp à un numéro sans l'enregistrer dans tes contacts."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class DirectActions(
    val onNumber: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onCountries: () -> Unit = {},
    val onCountry: (Country) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onOpen: (business: Boolean) -> Unit = {},
    val onCopyLink: () -> Unit = {},
    val onRecent: (String) -> Unit = {},
    val onForget: (String) -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class DirectUiState(
    val number: String = "",
    val country: Country = Countries.france,
    val message: String = "",
    val recents: List<String> = emptyList(),
    /** The list of countries is unfolded. */
    val countriesOpen: Boolean = false,
    /** WhatsApp Business is on the phone too: a second button opens the chat in it. */
    val business: Boolean = false,
    /** Neither WhatsApp is on the phone: the link opens in the browser. */
    val noWhatsApp: Boolean = false,
) {
    val parsed: DirectNumber get() = DirectNumbers.parse(number, country)
}

/** Ready-made first words, one touch to put them in the message. */
internal val DIRECT_QUICK = listOf(
    "Bonjour 👋",
    "Bonjour, je vous contacte au sujet de votre annonce.",
    "Bonjour, c'est bien vous qui m'avez appelé ?",
    "Merci, à bientôt !",
)

/**
 * Message direct (ToolRegistry.DIRECT_CHAT): a number, typed or pasted any way, opens its WhatsApp chat through
 * WhatsApp's own wa.me link, so it never has to be saved in the contacts. Only the last numbers are kept, here.
 */
@Composable
fun DirectTool(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var number by rememberSaveable { mutableStateOf("") }
    var countryIso by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_COUNTRY, null) ?: Countries.default(Locale.getDefault().country).iso)
    }
    var message by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(false) }
    var recents by remember { mutableStateOf(DirectRecents.decode(prefs.getString(KEY_RECENTS, null))) }
    val installed = remember { installedWhatsApps(context) }
    val country = Countries.byIso(countryIso) ?: Countries.france
    val state = DirectUiState(
        number = number,
        country = country,
        message = message,
        recents = recents,
        countriesOpen = open,
        business = WHATSAPP in installed && BUSINESS in installed,
        noWhatsApp = installed.isEmpty(),
    )

    fun keep(digits: String) {
        recents = DirectRecents.add(recents, digits)
        prefs.edit().putString(KEY_RECENTS, DirectRecents.encode(recents)).apply()
    }

    val actions = DirectActions(
        onNumber = { number = it.take(40) },
        onPaste = {
            val text = paste(context)
            val found = text?.let { DirectNumbers.find(it, country) }
            when {
                text.isNullOrBlank() -> vm.toast("Rien à coller", success = false)
                found == null -> vm.toast("Aucun numéro dans le texte copié", success = false)
                else -> number = found
            }
        },
        onCountries = { open = !open },
        onCountry = {
            countryIso = it.iso
            open = false
            prefs.edit().putString(KEY_COUNTRY, it.iso).apply()
        },
        onMessage = { message = it.take(DirectNumbers.MAX_MESSAGE) },
        onOpen = { business ->
            val ok = state.parsed as? DirectNumber.Ok
            if (ok != null) {
                keep(ok.digits)
                val app = when {
                    business -> BUSINESS
                    WHATSAPP in installed -> WHATSAPP
                    else -> installed.firstOrNull()
                }
                openChat(context, DirectNumbers.link(ok.digits, message), app, vm)
            }
        },
        onCopyLink = {
            val ok = state.parsed as? DirectNumber.Ok
            if (ok != null) {
                copy(context, DirectNumbers.link(ok.digits, message))
                if (Build.VERSION.SDK_INT < 33) vm.toast("Lien copié")
            }
        },
        onRecent = { digits ->
            number = "+$digits"
            open = false
        },
        onForget = { digits ->
            recents = DirectRecents.remove(recents, digits)
            prefs.edit().putString(KEY_RECENTS, DirectRecents.encode(recents)).apply()
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = DIRECT_TITLE, command = DIRECT_COMMAND, subtitle = DIRECT_SUBTITLE, scrollState = scroll) {
        DirectContent(state, actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.DirectContent(state: DirectUiState, actions: DirectActions) {
    val parsed = state.parsed
    val ready = parsed as? DirectNumber.Ok

    // Country, number, and what Gulp reads in it.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            MiniAction("Coller", Icons.Rounded.ContentPaste, onClick = actions.onPaste)
            AnimatedVisibility(state.number.isNotEmpty(), enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                MiniAction("Effacer", Icons.Rounded.Close) { actions.onNumber("") }
            }
        }
        NumberVerdict(parsed)
    }

    // Optional first message, with a few ready-made openings.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrField(
            label = "Message (facultatif)",
            value = state.message,
            onChange = actions.onMessage,
            singleLine = false,
            placeholder = "Écrit d'avance dans WhatsApp, tu pourras le changer.",
            maxLength = DirectNumbers.MAX_MESSAGE,
        )
        QuickMessages(onPick = actions.onMessage)
    }

    ChatPreview(ready, state.message, modifier = Modifier.staggeredEntrance(2, baseDelay = 40))

    Column(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 40), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            text = if (state.noWhatsApp) "Ouvrir le lien WhatsApp" else "Ouvrir dans WhatsApp",
            icon = Icons.AutoMirrored.Rounded.Send,
            subtitle = ready?.pretty,
            enabled = ready != null,
            modifier = Modifier.fillMaxWidth(),
        ) { actions.onOpen(false) }
        AnimatedVisibility(ready != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                if (state.business) MiniAction("WhatsApp Business", Icons.AutoMirrored.Rounded.Send) { actions.onOpen(true) }
                MiniAction("Copier le lien", Icons.Rounded.Link, onClick = actions.onCopyLink)
            }
        }
    }

    if (state.noWhatsApp) {
        Hint("WhatsApp n'est pas sur ce téléphone : le lien s'ouvrira dans le navigateur.", warning = true, modifier = Modifier.padding(horizontal = 8.dp))
    }

    if (state.recents.isNotEmpty()) {
        BlockLabel("Récents", Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(4, baseDelay = 60))
        state.recents.forEachIndexed { i, digits ->
            RecentRow(digits, index = 5 + i, onPick = { actions.onRecent(digits) }, onForget = { actions.onForget(digits) })
        }
    }

    Hint(
        "Gulp ouvre le lien officiel wa.me de WhatsApp : le numéro n'est pas ajouté à tes contacts. " +
            "Seuls les ${DirectRecents.MAX} derniers numéros restent ici, sur le téléphone, et la croix les efface.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

// MARK: - Intents

private const val PREFS = "direct"
private const val KEY_RECENTS = "recents"
private const val KEY_COUNTRY = "country"
private const val WHATSAPP = "com.whatsapp"
private const val BUSINESS = "com.whatsapp.w4b"

/** The WhatsApps on the phone (the manifest declares both in <queries>, so Android 11+ shows them). */
private fun installedWhatsApps(context: Context): List<String> = listOf(WHATSAPP, BUSINESS).filter { app ->
    try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(app, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(app, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/** Opens [link] in [app] (WhatsApp or Business), or in whatever opens links when it is null or refuses. */
private fun openChat(context: Context, link: String, app: String?, vm: MainViewModel) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    if (app != null) {
        try {
            context.startActivity(Intent(view).setPackage(app))
            return
        } catch (_: ActivityNotFoundException) {
            // Removed since the screen opened: the browser takes it.
        }
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        vm.toast("Aucune appli ne peut ouvrir ce lien", success = false)
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Lien WhatsApp", text))
}

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
