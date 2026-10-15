package com.powermaxv2.ytdownloader.ui.tools.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.password.Checked
import com.powermaxv2.ytdownloader.core.password.GeneratedPassword
import com.powermaxv2.ytdownloader.core.password.PasswordMode
import com.powermaxv2.ytdownloader.core.password.PasswordOptions
import com.powermaxv2.ytdownloader.core.password.Passwords
import com.powermaxv2.ytdownloader.core.password.Separator
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.watermark.SettingSlider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

const val PASSWORD_TITLE = "Mots de passe"
const val PASSWORD_COMMAND = "pwgen"
const val PASSWORD_SUBTITLE = "Des mots de passe solides ou faciles à retenir, faits sur le téléphone, avec leur jauge de force."

/** How long a copied password stays in the clipboard before Gulp empties it. */
private const val CLIPBOARD_CLEAR_MS = 60_000L

/** How many passwords "Derniers créés" keeps (in memory only, gone when Gulp closes). */
private const val HISTORY_SIZE = 5

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PasswordActions(
    val onMode: (PasswordMode) -> Unit = {},
    val onOptions: (PasswordOptions) -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onTyped: (String) -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class PasswordUiState(
    val mode: PasswordMode = PasswordMode.RANDOM,
    val options: PasswordOptions = PasswordOptions(),
    val current: GeneratedPassword? = null,
    /** Goes up at each new draw: the slots spin and the die turns again. */
    val generation: Int = 0,
    val copied: Boolean = false,
    val history: List<String> = emptyList(),
    /** The password typed in "Tester le mien". */
    val typed: String = "",
)

/**
 * The Générateur de mots de passe (ToolRegistry.PASSWORDS): random passwords, passphrases of French words and PIN
 * codes drawn from SecureRandom, with a strength gauge, and a gauge for a password the user types in. Nothing is
 * saved nor sent; a copied password is marked sensitive and taken out of the clipboard after a minute.
 */
@Composable
fun PasswordTool(vm: MainViewModel) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(PasswordMode.RANDOM) }
    var options by remember { mutableStateOf(PasswordOptions()) }
    var current by remember { mutableStateOf(Passwords.generate(PasswordMode.RANDOM, PasswordOptions())) }
    var generation by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(emptyList<String>()) }
    var typed by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1_600)
            copied = false
        }
    }

    fun draw(newMode: PasswordMode = mode, newOptions: PasswordOptions = options) {
        if (newMode == PasswordMode.CHECK) return
        current = Passwords.generate(newMode, newOptions)
        generation++
    }

    val actions = PasswordActions(
        onMode = { m ->
            mode = m
            if (m != PasswordMode.CHECK && current.mode != m) draw(m)
        },
        onOptions = { o ->
            options = o.normalized()
            draw(newOptions = options)
        },
        onGenerate = { draw() },
        onCopy = { text ->
            copySecret(context, text)
            history = (listOf(text) + history.filterNot { it == text }).take(HISTORY_SIZE)
            if (text == current.text) {
                copied = true
                copies++
            }
            // Android 13+ shows its own bubble (and hides the text, since the clip is marked sensitive).
            if (Build.VERSION.SDK_INT < 33) vm.toast("Copié · effacé du presse-papiers dans 1 min")
        },
        onTyped = { typed = it },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = PASSWORD_TITLE, command = PASSWORD_COMMAND, subtitle = PASSWORD_SUBTITLE, scrollState = scroll) {
        PasswordContent(PasswordUiState(mode, options, current, generation, copied, history, typed), actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.PasswordContent(state: PasswordUiState, actions: PasswordActions) {
    OptionChips(
        options = PasswordMode.entries,
        selected = state.mode,
        label = { it.label },
        onSelect = actions.onMode,
        modifier = Modifier.staggeredEntrance(0, baseDelay = 40),
    )

    AnimatedContent(
        targetState = state.mode == PasswordMode.CHECK,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 * dir } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(200)) { -it / 3 * dir } + fadeOut(tween(160)))
        },
        label = "passwordMode",
    ) { checking ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (checking) CheckBlock(state, actions) else GeneratorBlock(state, actions)
        }
    }

    Hint(
        "Tiré au hasard sur le téléphone (SecureRandom), hors ligne. Rien n'est enregistré ni envoyé ; " +
            "un mot de passe copié est retiré du presse-papiers au bout d'une minute.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun GeneratorBlock(state: PasswordUiState, actions: PasswordActions) {
    val current = state.current
    val title = when (state.mode) {
        PasswordMode.PASSPHRASE -> "Ta phrase de passe"
        PasswordMode.PIN -> "Ton code PIN"
        else -> "Ton mot de passe"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .drawPulse(state.generation)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlockLabel(title)
        ScrambledPassword(current?.text.orEmpty(), state.generation, Modifier.padding(vertical = 6.dp))
        StrengthBlock(current?.bits)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MainButton(
                if (state.copied) "Copié" else "Copier",
                modifier = Modifier.weight(1f),
                icon = if (state.copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                enabled = current != null,
            ) { current?.let { actions.onCopy(it.text) } }
            DiceButton(state.generation, onClick = actions.onGenerate)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 40)
            .toolCard()
            .padding(18.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Réglages")
        val o = state.options
        when (state.mode) {
            PasswordMode.PASSPHRASE -> {
                SettingSlider(
                    "Nombre de mots",
                    "${o.words}",
                    o.words.toFloat(),
                    Passwords.MIN_WORDS.toFloat()..Passwords.MAX_WORDS.toFloat(),
                    onChange = { v -> v.roundToInt().let { if (it != o.words) actions.onOptions(o.copy(words = it)) } },
                )
                Text(skinText("Entre les mots"), color = Palette.TextSecondary, fontSize = 13.5.sp)
                OptionChips(
                    options = Separator.entries,
                    selected = o.separator,
                    label = { it.label },
                    onSelect = { actions.onOptions(o.copy(separator = it)) },
                )
                SwitchRow("Majuscules", "Chaque mot commence par une majuscule.", o.capitalize) { actions.onOptions(o.copy(capitalize = it)) }
                SwitchRow("Un nombre", "Un nombre collé à un mot, pour les sites qui veulent un chiffre.", o.addNumber) { actions.onOptions(o.copy(addNumber = it)) }
            }
            PasswordMode.PIN -> {
                SettingSlider(
                    "Chiffres",
                    "${o.pinLength}",
                    o.pinLength.toFloat(),
                    Passwords.MIN_PIN.toFloat()..Passwords.MAX_PIN.toFloat(),
                    onChange = { v -> v.roundToInt().let { if (it != o.pinLength) actions.onOptions(o.copy(pinLength = it)) } },
                )
                Hint("Jamais 0000 ni 1234 : les suites et les répétitions sont écartées.")
            }
            else -> {
                SettingSlider(
                    "Longueur",
                    "${o.length}",
                    o.length.toFloat(),
                    Passwords.MIN_LENGTH.toFloat()..Passwords.MAX_LENGTH.toFloat(),
                    onChange = { v -> v.roundToInt().let { if (it != o.length) actions.onOptions(o.copy(length = it)) } },
                )
                SwitchRow("Minuscules", "abc", o.lower) { actions.onOptions(o.copy(lower = it)) }
                SwitchRow("Majuscules", "ABC", o.upper) { actions.onOptions(o.copy(upper = it)) }
                SwitchRow("Chiffres", "123", o.digits) { actions.onOptions(o.copy(digits = it)) }
                SwitchRow("Symboles", Passwords.SYMBOLS.take(9), o.symbols) { actions.onOptions(o.copy(symbols = it)) }
                SwitchRow("Sans caractères ambigus", "Ni I, l, 1, O, 0 : rien à confondre en le recopiant.", o.avoidAmbiguous) {
                    actions.onOptions(o.copy(avoidAmbiguous = it))
                }
            }
        }
    }

    if (state.history.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(3, baseDelay = 40)
                .toolCard()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .animateContentSize(),
        ) {
            BlockLabel("Derniers copiés")
            Hint("Gardés jusqu'à la fermeture de Gulp, jamais enregistrés.", modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            state.history.forEachIndexed { i, text -> HistoryRow(text, i) { actions.onCopy(text) } }
        }
    }
}

@Composable
private fun CheckBlock(state: PasswordUiState, actions: PasswordActions) {
    val checked: Checked = Passwords.check(state.typed)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(18.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlockLabel("Tester le mien")
        QrField(
            label = "Ton mot de passe",
            value = state.typed,
            onChange = actions.onTyped,
            secret = true,
            placeholder = "Tape-le ici",
            maxLength = 128,
        )
        StrengthBlock(if (state.typed.isEmpty()) null else checked.bits)
        checked.tips.forEach { tip -> Hint(tip, warning = checked.common) }
    }
    Hint(
        "Ce que tu tapes reste ici : pas enregistré, pas envoyé, effacé en quittant l'écran. L'estimation est prudente " +
            "mais approximative ; un mot de passe tiré par Gulp est plus sûr qu'un mot de passe inventé.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

// MARK: - Clipboard

/**
 * Copies [text] marked as sensitive (Android 13+ hides it from the copy preview and the keyboard's suggestions), then
 * empties the clipboard after a minute if it still holds the same text.
 */
private fun copySecret(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Mot de passe", text)
    clip.description.extras = PersistableBundle().apply {
        // ClipDescription.EXTRA_IS_SENSITIVE, by its value so it also compiles against older SDK stubs.
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
    val app = context.applicationContext
    Handler(Looper.getMainLooper()).postDelayed({
        val board = app.getSystemService(ClipboardManager::class.java) ?: return@postDelayed
        // Android 10+ refuses to read the clipboard from the background: then it is left alone.
        val now = runCatching { board.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull() ?: return@postDelayed
        if (now != text) return@postDelayed
        if (Build.VERSION.SDK_INT >= 28) board.clearPrimaryClip() else board.setPrimaryClip(ClipData.newPlainText("", ""))
    }, CLIPBOARD_CLEAR_MS)
}
