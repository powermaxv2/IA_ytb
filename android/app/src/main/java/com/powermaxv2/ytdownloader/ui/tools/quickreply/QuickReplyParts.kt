package com.powermaxv2.ytdownloader.ui.tools.quickreply

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.quickreply.QuickReply
import com.powermaxv2.ytdownloader.core.quickreply.QuickVars
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import java.time.LocalDateTime

@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** One ready-made message: its title, the text as it will be sent, the fields of its variables, and the buttons. */
@Composable
internal fun ReplyCard(reply: QuickReply, state: QuickUiState, index: Int, first: Boolean, last: Boolean, actions: QuickActions) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    val copied = state.copied == reply.id
    val ring by animateColorAsState(if (copied) accent.start else Color.Transparent, tween(220), label = "copiedRing")
    val asked = QuickVars.asked(reply.text)
    val showFields = !state.arranging && asked.isNotEmpty() && (state.open == reply.id || asked.any { !state.values[it.key].isNullOrBlank() })
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 80)
            .bounceClick(enabled = !state.arranging, pressedScale = 0.98f) { actions.onCopy(reply) }
            .toolCard()
            .border(2.dp, ring, shape)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skinText(reply.title),
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = copied,
                transitionSpec = { scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn() togetherWith scaleOut() + fadeOut() },
                label = "copiedBadge",
            ) { done ->
                if (done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.Success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(skinText("Copié"), color = Palette.Success, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
            AnimatedContent(targetState = state.arranging, label = "cardTools") { arranging ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (arranging) {
                        RoundIcon(Icons.Rounded.KeyboardArrowUp, "Monter", enabled = !first) { actions.onMove(reply.id, -1) }
                        RoundIcon(Icons.Rounded.KeyboardArrowDown, "Descendre", enabled = !last) { actions.onMove(reply.id, 1) }
                        RoundIcon(Icons.Rounded.DeleteOutline, "Supprimer", danger = true) { actions.onDelete(reply.id) }
                    } else {
                        RoundIcon(Icons.Rounded.Edit, "Modifier") { actions.onEdit(reply) }
                    }
                }
            }
        }

        MessageBubble(highlighted(reply.text, state.values, state.now), maxLines = if (state.arranging) 2 else 6)

        AnimatedVisibility(
            showFields,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                asked.forEach { v ->
                    QrField(
                        label = v.name.replaceFirstChar { it.uppercase() },
                        value = state.values[v.key].orEmpty(),
                        onChange = { actions.onValue(v.key, it) },
                        placeholder = placeholderFor(v.key),
                        maxLength = 60,
                    )
                }
            }
        }

        AnimatedVisibility(!state.arranging, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickButton(if (copied) "Copié" else "Copier", if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, filled = false, Modifier.weight(1f)) { actions.onCopy(reply) }
                QuickButton("WhatsApp", Icons.AutoMirrored.Rounded.Send, filled = true, Modifier.weight(1f)) { actions.onShare(reply) }
            }
        }
    }
}

/** Writing a new message or changing one: title, text, variables to insert, and what it will look like. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DraftCard(draft: QuickDraft, values: Map<String, String>, now: LocalDateTime, actions: QuickActions) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .toolCard()
            .border(1.5.dp, accent.start.copy(alpha = 0.6f), RoundedCornerShape(LocalTokens.current.cardRadius))
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel(if (draft.id == null) "Nouveau message" else "Modifier le message")
        QrField(
            label = "Titre (facultatif)",
            value = draft.title,
            onChange = { actions.onDraft(draft.copy(title = it)) },
            placeholder = "En route, Adresse, Merci…",
            maxLength = 40,
        )
        QrField(
            label = "Message",
            value = draft.text,
            onChange = { actions.onDraft(draft.copy(text = it)) },
            singleLine = false,
            placeholder = "J'arrive vers {heure+15} !",
            maxLength = 1_000,
        )
        BlockLabel("Ajouter")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (QuickVars.suggested + QuickVars.autos).forEachIndexed { i, name ->
                VarChip("{$name}", auto = name in QuickVars.autos, index = i) {
                    val sep = if (draft.text.isEmpty() || draft.text.endsWith(' ') || draft.text.endsWith('\n')) "" else " "
                    actions.onDraft(draft.copy(text = draft.text + sep + "{$name}"))
                }
            }
        }
        AnimatedVisibility(draft.text.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            MessageBubble(highlighted(draft.text, values, now), maxLines = 8)
        }
        PrimaryButton(
            text = "Enregistrer",
            icon = Icons.Rounded.Check,
            enabled = draft.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.onSave,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            MiniAction("Annuler", Icons.Rounded.Close, onClick = actions.onCancel)
            if (draft.id != null) MiniAction("Supprimer", Icons.Rounded.DeleteOutline) { actions.onDelete(draft.id) }
        }
    }
}

/** [text] with its variables in the accent: filled ones show their value, empty ones keep their {braces}. */
@Composable
private fun highlighted(text: String, values: Map<String, String>, now: LocalDateTime): AnnotatedString {
    val accent = LocalAccent.current
    val pieces = QuickVars.pieces(text, values, now)
    return buildAnnotatedString {
        pieces.forEach { piece ->
            when {
                piece.variable == null -> append(piece.text)
                piece.filled -> withStyle(SpanStyle(color = accent.start, fontWeight = FontWeight.SemiBold)) { append(piece.text) }
                else -> withStyle(SpanStyle(color = accent.start, background = accent.tint(0.16f), fontWeight = FontWeight.Medium)) { append(piece.text) }
            }
        }
    }
}

/** The message as it lands in a WhatsApp chat. */
@Composable
private fun MessageBubble(text: AnnotatedString, maxLines: Int) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    val bubble = when (skin) {
        Skin.TERMINAL -> Modifier.border(1.dp, accent.start.copy(alpha = 0.5f), shape)
        // WhatsApp's own green, softened so the text stays readable on every skin.
        else -> Modifier.background(Color(0xFF25D366).copy(alpha = 0.16f), shape)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            color = Palette.TextPrimary,
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .then(bubble)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** A variable to insert: filled-in ones (time, day) are tinted, asked ones are outlined. */
@Composable
private fun VarChip(text: String, auto: Boolean, index: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Text(
        text,
        color = if (auto) accent.start else Palette.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .staggeredEntrance(index, baseDelay = 60)
            .bounceClick(pressedScale = 0.88f, onClick = onClick)
            .clip(shape)
            .background(if (auto) accent.tint(0.14f) else Color.Transparent)
            .border(1.dp, if (auto) accent.start.copy(alpha = 0.5f) else Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A small round icon button in the corner of a card. */
@Composable
private fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val tint = when {
        !enabled -> Palette.TextTertiary.copy(alpha = 0.35f)
        danger -> Palette.Danger
        else -> Palette.TextSecondary
    }
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(34.dp)
            .clip(CircleShape)
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick)
            .background(Palette.Surface2.copy(alpha = if (enabled) 1f else 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** "Copier" (outlined) or "WhatsApp" (filled with the accent) at the bottom of a card. */
@Composable
private fun QuickButton(text: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) LocalTokens.current.chipRadius else 14.dp)
    val ink = when {
        !filled -> Palette.TextPrimary
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.OnAccent
    }
    val base = modifier
        .bounceClick(pressedScale = 0.92f, onClick = onClick)
        .height(42.dp)
        .clip(shape)
    val surface = when {
        !filled -> base.background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2).border(1.dp, Palette.Stroke, shape)
        skin == Skin.CLASSIC -> base.background(accent.brush)
        else -> base.background(accent.start)
    }
    Row(surface, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (filled) ink else accent.start, modifier = Modifier.size(17.dp))
        Text(skinText(text), color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A line in place of the list when it is empty. */
@Composable
internal fun EmptyNote(text: String) {
    Text(
        if (LocalSkin.current == Skin.TERMINAL) "# $text" else text,
        color = Palette.TextTertiary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 80)
            .padding(horizontal = 12.dp, vertical = 18.dp),
    )
}

private fun placeholderFor(key: String): String = when (key) {
    "prenom", "nom" -> "Léa"
    "lieu", "adresse" -> "la gare"
    else -> ""
}
