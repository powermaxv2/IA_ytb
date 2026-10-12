package com.powermaxv2.ytdownloader.ui.tools.styled

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.styled.StyledText
import com.powermaxv2.ytdownloader.core.styled.StyledVariant
import com.powermaxv2.ytdownloader.core.styled.TextStyleKind
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import kotlinx.coroutines.delay

const val STYLED_TITLE = "Texte stylé"
const val STYLED_COMMAND = "style"
const val STYLED_SUBTITLE = "Écris une fois, et colle-le en gras, barré, gothique ou en bulles dans WhatsApp."

/** Which variants the list shows. */
enum class StyleFilter(val label: String) {
    ALL("Tout"),
    WHATSAPP("WhatsApp"),
    FONTS("Polices"),
}

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class StyledActions(
    val onText: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onFilter: (StyleFilter) -> Unit = {},
    val onCopy: (StyledVariant) -> Unit = {},
    val onShare: (StyledVariant) -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class StyledUiState(
    val text: String = "",
    val filter: StyleFilter = StyleFilter.ALL,
    /** The variant just copied: its card shows "Copié" for a moment. */
    val copied: TextStyleKind? = null,
)

/**
 * The Texte stylé (ToolRegistry.STYLED_TEXT): one text, written in WhatsApp's own styles and in Unicode fonts, each
 * ready to copy or to send to WhatsApp. Nothing leaves the phone except what the user shares.
 */
@Composable
fun StyledTextTool(vm: MainViewModel) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StyleFilter.ALL) }
    var copied by remember { mutableStateOf<TextStyleKind?>(null) }
    var copies by remember { mutableStateOf(0) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1_600)
            copied = null
        }
    }

    val actions = StyledActions(
        onText = { text = it.take(StyledText.MAX_LENGTH) },
        onPaste = {
            val pasted = paste(context)
            if (pasted.isNullOrEmpty()) vm.toast("Rien à coller", success = false) else text = pasted.take(StyledText.MAX_LENGTH)
        },
        onFilter = { filter = it },
        onCopy = { variant ->
            copy(context, variant.text)
            copied = variant.kind
            copies++
            // Android 13+ shows its own "Copié" bubble.
            if (Build.VERSION.SDK_INT < 33) vm.toast("${variant.kind.label} copié")
        },
        onShare = { sendToWhatsApp(context, it.text, vm) },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = STYLED_TITLE, command = STYLED_COMMAND, subtitle = STYLED_SUBTITLE, scrollState = scroll) {
        StyledContent(StyledUiState(text, filter, copied), actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.StyledContent(state: StyledUiState, actions: StyledActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val source = state.text.ifBlank { StyledText.SAMPLE }
    val sample = state.text.isBlank()

    // Input, its two little actions and the cycling preview.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrField(
            label = "Ton texte",
            value = state.text,
            onChange = actions.onText,
            singleLine = false,
            placeholder = "Écris ici… ex : ${StyledText.SAMPLE}",
            maxLength = StyledText.MAX_LENGTH,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("Coller", Icons.Rounded.ContentPaste, onClick = actions.onPaste)
            AnimatedVisibility(!sample, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)), exit = fadeOut() + scaleOut()) {
                MiniAction("Effacer", Icons.Rounded.Close) { actions.onText("") }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${state.text.length}/${StyledText.MAX_LENGTH}",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }
        StyleCycler(source)
    }

    OptionChips(
        options = StyleFilter.entries,
        selected = state.filter,
        label = {
            val count = when (it) {
                StyleFilter.ALL -> TextStyleKind.entries.size
                StyleFilter.WHATSAPP -> StyledText.whatsAppKinds.size
                StyleFilter.FONTS -> StyledText.fontKinds.size
            }
            val text = "${it.label} · $count"
            if (terminal) text.uppercase() else text
        },
        onSelect = actions.onFilter,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 40),
    )

    if (sample) {
        Hint(
            "Exemple avec « ${StyledText.SAMPLE} » : écris ton texte au-dessus, tout change en direct.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .staggeredEntrance(2, baseDelay = 40),
        )
    }

    var index = 2
    if (state.filter != StyleFilter.FONTS) {
        BlockLabel("Mise en forme WhatsApp", Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(index++, baseDelay = 60))
        Hint(
            "WhatsApp met en forme à l'envoi : colle le texte tel quel, avec ses signes.",
            modifier = Modifier.padding(horizontal = 4.dp).staggeredEntrance(index++, baseDelay = 60),
        )
        StyledText.variants(source, StyledText.whatsAppKinds).forEach { variant ->
            VariantCard(
                variant,
                copied = state.copied == variant.kind,
                index = index++,
                onCopy = { actions.onCopy(variant) },
                onShare = { actions.onShare(variant) },
            )
        }
    }
    if (state.filter != StyleFilter.WHATSAPP) {
        BlockLabel("Polices fantaisie", Modifier.padding(start = 4.dp, top = 8.dp).staggeredEntrance(index++, baseDelay = 60))
        Hint(
            "Ce sont d'autres lettres : elles restent stylées partout (WhatsApp, Insta, TikTok, un pseudo).",
            modifier = Modifier.padding(horizontal = 4.dp).staggeredEntrance(index++, baseDelay = 60),
        )
        StyledText.variants(source, StyledText.fontKinds).forEach { variant ->
            VariantCard(
                variant,
                copied = state.copied == variant.kind,
                index = index++,
                onCopy = { actions.onCopy(variant) },
                onShare = { actions.onShare(variant) },
            )
        }
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Les polices fantaisie sont mal lues par les lecteurs d'écran et la recherche : garde-les pour un titre ou un pseudo.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .animateContentSize(),
    )
}

// MARK: - Intents

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Texte stylé", text))
}

private fun paste(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/** Opens WhatsApp (or WhatsApp Business) on "send to…" with [text]; any other app through the chooser otherwise. */
private fun sendToWhatsApp(context: Context, text: String, vm: MainViewModel) {
    val base = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    for (app in WHATSAPP_PACKAGES) {
        try {
            context.startActivity(Intent(base).setPackage(app))
            return
        } catch (_: ActivityNotFoundException) {
            // Not installed: next one.
        }
    }
    runCatching { context.startActivity(Intent.createChooser(base, "Envoyer avec")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
