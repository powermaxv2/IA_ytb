package com.powermaxv2.ytdownloader.ui.tools.textcount

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
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
import com.powermaxv2.ytdownloader.core.textcount.CaseChanger
import com.powermaxv2.ytdownloader.core.textcount.CaseKind
import com.powermaxv2.ytdownloader.core.textcount.TextCounter
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import kotlinx.coroutines.delay

const val TEXT_COUNT_TITLE = "Compteur de texte"
const val TEXT_COUNT_COMMAND = "wc"
const val TEXT_COUNT_SUBTITLE = "Mots, caractères, temps de lecture, et la casse changée d'un toucher."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class TextCountActions(
    val onText: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onCase: (CaseKind) -> Unit = {},
    val onUndo: () -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class TextCountUiState(
    val text: String = "",
    /** The case just applied: its chip pops and the field shines once. */
    val lastCase: CaseKind? = null,
    /** How many times a case was applied: replays the shine even on the same chip. */
    val caseTaps: Int = 0,
    val canUndo: Boolean = false,
    val copied: Boolean = false,
)

/**
 * The Compteur de texte (ToolRegistry.TEXT_COUNTER): words, characters, sentences, reading and speaking times, how
 * the text fits an SMS, a post X or an Instagram bio, the words that come back, and its case changed in one touch.
 * Everything is counted on the phone as the user types.
 */
@Composable
fun TextCountTool(vm: MainViewModel) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCase by remember { mutableStateOf<CaseKind?>(null) }
    var caseTaps by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1_600)
            copied = false
        }
    }

    val actions = TextCountActions(
        onText = {
            text = it.take(TextCounter.MAX_LENGTH)
            before = null
            lastCase = null
        },
        onPaste = {
            val pasted = paste(context)
            if (pasted.isNullOrEmpty()) {
                vm.toast("Rien à coller", success = false)
            } else {
                text = pasted.take(TextCounter.MAX_LENGTH)
                before = null
                lastCase = null
            }
        },
        onCopy = {
            if (text.isEmpty()) {
                vm.toast("Rien à copier", success = false)
            } else {
                copy(context, text)
                copied = true
                copies++
                if (Build.VERSION.SDK_INT < 33) vm.toast("Texte copié")
            }
        },
        onCase = { kind ->
            if (text.isBlank()) {
                vm.toast("Écris ou colle d'abord un texte", success = false)
            } else {
                val changed = CaseChanger.apply(text, kind)
                if (changed != text) before = text
                text = changed
                lastCase = kind
                caseTaps++
            }
        },
        onUndo = {
            before?.let { text = it }
            before = null
            lastCase = null
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = TEXT_COUNT_TITLE, command = TEXT_COUNT_COMMAND, subtitle = TEXT_COUNT_SUBTITLE, scrollState = scroll) {
        TextCountContent(TextCountUiState(text, lastCase, caseTaps, before != null, copied), actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.TextCountContent(state: TextCountUiState, actions: TextCountActions) {
    val sample = state.text.isBlank()
    val source = if (sample) TextCounter.SAMPLE else state.text
    val stats = remember(source) { TextCounter.stats(source) }
    val fits = remember(source) { TextCounter.fits(source) }
    val sms = remember(source) { TextCounter.sms(source) }

    // The field, its little actions, and the shine that sweeps it when the case changes.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .caseShine(state.caseTaps)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrField(
            label = "Ton texte",
            value = state.text,
            onChange = actions.onText,
            singleLine = false,
            placeholder = "Écris ou colle un texte, tout se compte en direct.",
            maxLength = TextCounter.MAX_LENGTH,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("Coller", Icons.Rounded.ContentPaste, onClick = actions.onPaste)
            AnimatedVisibility(!sample, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                MiniAction(if (state.copied) "Copié" else "Copier", if (state.copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, onClick = actions.onCopy)
            }
            AnimatedVisibility(!sample, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                MiniAction("Effacer", Icons.Rounded.Close) { actions.onText("") }
            }
            Spacer(Modifier.weight(1f))
        }
    }

    if (sample) {
        Hint(
            "Exemple avec une petite invitation : écris ou colle ton texte au-dessus, les chiffres bougent en direct.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .staggeredEntrance(1, baseDelay = 40),
        )
    }

    HeroCount(stats, Modifier.staggeredEntrance(1, baseDelay = 60))
    StatGrid(stats, Modifier.staggeredEntrance(2, baseDelay = 60))

    BlockLabel("Changer la casse", Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(3, baseDelay = 60))
    CaseChips(
        text = state.text,
        last = state.lastCase,
        enabled = !sample,
        onCase = actions.onCase,
        modifier = Modifier.staggeredEntrance(4, baseDelay = 60),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .staggeredEntrance(5, baseDelay = 60),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Hint(
            if (sample) "Écris d'abord ton texte : un toucher sur une casse le change en entier." else "Un toucher change tout le texte. Les accents et les emoji restent.",
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(state.canUndo, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.45f)), exit = fadeOut() + scaleOut()) {
            MiniAction("Annuler", Icons.AutoMirrored.Rounded.Undo, onClick = actions.onUndo)
        }
    }

    BlockLabel("Ça rentre ?", Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(6, baseDelay = 60))
    LimitsCard(fits, sms, Modifier.staggeredEntrance(7, baseDelay = 60))

    if (stats.topWords.isNotEmpty() || stats.longestWord.isNotEmpty()) {
        BlockLabel("Les mots", Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(8, baseDelay = 60))
        WordsCard(stats, Modifier.staggeredEntrance(9, baseDelay = 60))
    }

    Hint(
        "Tout se compte sur le téléphone, hors ligne : rien n'est envoyé. Un emoji compte pour un caractère ; le temps de lecture suppose 230 mots par minute, à voix haute 150.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

// MARK: - Clipboard

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(TEXT_COUNT_TITLE, text))
}

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
