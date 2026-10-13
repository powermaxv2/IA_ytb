package com.powermaxv2.ytdownloader.ui.tools.quickreply

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.quickreply.QuickReplies
import com.powermaxv2.ytdownloader.core.quickreply.QuickReply
import com.powermaxv2.ytdownloader.core.quickreply.QuickVars
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import kotlinx.coroutines.delay
import java.time.LocalDateTime

const val QUICK_TITLE = "Réponses rapides"
const val QUICK_COMMAND = "reply"
const val QUICK_SUBTITLE = "Tes messages tout faits : un toucher pour copier, un autre pour l'envoyer dans WhatsApp."

/** The message being written or changed; [id] is null for a new one. */
data class QuickDraft(val id: Long? = null, val title: String = "", val text: String = "")

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class QuickActions(
    val onQuery: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (QuickReply) -> Unit = {},
    val onDraft: (QuickDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onArrange: () -> Unit = {},
    val onMove: (id: Long, by: Int) -> Unit = { _, _ -> },
    val onValue: (key: String, value: String) -> Unit = { _, _ -> },
    val onCopy: (QuickReply) -> Unit = {},
    val onShare: (QuickReply) -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class QuickUiState(
    val replies: List<QuickReply> = QuickReplies.starters,
    val query: String = "",
    /** What the user typed for the asked variables, by folded key ("prenom" → "Léa"). */
    val values: Map<String, String> = emptyMap(),
    /** The message whose variables are unfolded to be filled. */
    val open: Long? = null,
    /** Arrows and bins instead of the send buttons. */
    val arranging: Boolean = false,
    val draft: QuickDraft? = null,
    /** The message just copied: its card shows "Copié" for a moment. */
    val copied: Long? = null,
    /** Clock used for {heure}, {jour}…; fixed in the screenshot tests. */
    val now: LocalDateTime = LocalDateTime.now(),
) {
    val shown: List<QuickReply> get() = QuickReplies.search(replies, query)
}

/** Above this many messages, a search field shows up. */
private const val SEARCH_FROM = 6

/**
 * Réponses rapides (ToolRegistry.QUICK_REPLIES): the messages the user sends again and again, kept here, filled with
 * the time or a first name, then copied or sent to WhatsApp in one touch. Stored only in the app's preferences.
 */
@Composable
fun QuickReplyTool(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var replies by remember { mutableStateOf(QuickReplies.decode(prefs.getString(KEY_LIST, null))) }
    var query by rememberSaveable { mutableStateOf("") }
    var values by remember { mutableStateOf(mapOf<String, String>()) }
    var open by remember { mutableStateOf<Long?>(null) }
    var arranging by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<QuickDraft?>(null) }
    var copied by remember { mutableStateOf<Long?>(null) }
    var copies by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1_600)
            copied = null
        }
    }
    // {heure} follows the clock while the screen stays open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }

    fun save(list: List<QuickReply>) {
        replies = list
        prefs.edit().putString(KEY_LIST, QuickReplies.encode(list)).apply()
    }

    /** The text of [reply] ready to send, or null after unfolding its fields when a variable is still empty. */
    fun ready(reply: QuickReply): String? {
        val missing = QuickVars.missing(reply.text, values)
        if (missing.isNotEmpty()) {
            open = reply.id
            vm.toast("Remplis « ${missing.first().name} » d'abord", success = false)
            return null
        }
        return QuickVars.fill(reply.text, values, LocalDateTime.now())
    }

    val actions = QuickActions(
        onQuery = { query = it.take(60) },
        onNew = {
            draft = QuickDraft()
            arranging = false
        },
        onEdit = { draft = QuickDraft(it.id, it.title, it.text) },
        onDraft = { draft = it.copy(title = it.title.take(QuickReplies.MAX_TITLE), text = it.text.take(QuickReplies.MAX_TEXT)) },
        onSave = {
            val d = draft
            when {
                d == null || d.text.isBlank() -> Unit
                d.id == null && replies.size >= QuickReplies.MAX_COUNT ->
                    vm.toast("${QuickReplies.MAX_COUNT} messages au plus : supprimes-en un", success = false)
                d.id == null -> {
                    save(QuickReplies.add(replies, d.title, d.text))
                    query = ""
                    draft = null
                }
                else -> {
                    save(QuickReplies.update(replies, d.id, d.title, d.text))
                    draft = null
                }
            }
        },
        onCancel = { draft = null },
        onDelete = { id ->
            save(QuickReplies.remove(replies, id))
            if (draft?.id == id) draft = null
            if (open == id) open = null
        },
        onArrange = {
            arranging = !arranging
            draft = null
            open = null
        },
        onMove = { id, by -> save(QuickReplies.move(replies, id, by)) },
        onValue = { key, value -> values = values + (key to value.take(60)) },
        onCopy = { reply ->
            ready(reply)?.let { text ->
                copy(context, text)
                copied = reply.id
                copies++
                if (Build.VERSION.SDK_INT < 33) vm.toast("Message copié")
            }
        },
        onShare = { reply -> ready(reply)?.let { sendToWhatsApp(context, it, vm) } },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = QUICK_TITLE, command = QUICK_COMMAND, subtitle = QUICK_SUBTITLE, scrollState = scroll) {
        QuickContent(QuickUiState(replies, query, values, open, arranging, draft, copied, now), actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.QuickContent(state: QuickUiState, actions: QuickActions) {
    // Search, new message, arrange.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.replies.size >= SEARCH_FROM || state.query.isNotEmpty()) {
            QrField(
                label = "Chercher",
                value = state.query,
                onChange = actions.onQuery,
                placeholder = "merci, arrive, adresse…",
                maxLength = 60,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                countText(state.replies.size),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.replies.size > 1) {
                MiniAction(if (state.arranging) "Terminé" else "Ranger", if (state.arranging) Icons.Rounded.Check else Icons.Rounded.SwapVert, onClick = actions.onArrange)
            }
            MiniAction("Nouveau", Icons.Rounded.Add, onClick = actions.onNew)
        }
    }

    AnimatedVisibility(
        state.draft != null,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        state.draft?.let { DraftCard(it, state.values, state.now, actions) }
    }

    val shown = state.shown
    shown.forEachIndexed { i, reply ->
        key(reply.id) {
            ReplyCard(
                reply = reply,
                state = state,
                index = i + 1,
                first = reply.id == state.replies.firstOrNull()?.id,
                last = reply.id == state.replies.lastOrNull()?.id,
                actions = actions,
            )
        }
    }

    when {
        state.replies.isEmpty() -> EmptyNote("Aucun message pour l'instant. Touche « Nouveau » pour écrire le premier.")
        shown.isEmpty() -> EmptyNote("Aucun message ne contient « ${state.query.trim()} ».")
    }

    Hint(
        "{heure}, {heure+15}, {jour}, {date} et {bonjour} se remplissent tout seuls ; un autre mot entre accolades, " +
            "comme {prénom}, te sera demandé avant l'envoi. Les messages restent sur le téléphone.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

internal fun countText(n: Int): String = when (n) {
    0 -> "Aucun message"
    1 -> "1 message prêt"
    else -> "$n messages prêts"
}

// MARK: - Intents

private const val PREFS = "quick_replies"
private const val KEY_LIST = "list"

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Réponse rapide", text))
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
