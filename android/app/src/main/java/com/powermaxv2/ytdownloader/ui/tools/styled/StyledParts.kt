package com.powermaxv2.ytdownloader.ui.tools.styled

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.styled.ShownText
import com.powermaxv2.ytdownloader.core.styled.StyledText
import com.powermaxv2.ytdownloader.core.styled.StyledVariant
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// Pieces of the Texte stylé: the small "Coller / Effacer" pills, the preview that cycles through the fonts, and the
// card of each variant with its WhatsApp bubble, its "Copier" and its "WhatsApp" buttons.

/** Text in the skin's case (Terminal writes in capitals). */
@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** A small pill under the field. */
@Composable
internal fun MiniAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Row(
        Modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(16.dp))
        Text(skinText(text), color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The text sliding through every font, one every 1.2 s: shows at a glance what the tool does. */
@Composable
internal fun StyleCycler(source: String) {
    val kinds = StyledText.fontKinds
    val transition = rememberInfiniteTransition(label = "styleCycle")
    val step by transition.animateFloat(
        0f,
        kinds.size.toFloat(),
        infiniteRepeatable(tween(kinds.size * 1_200, easing = LinearEasing)),
        label = "styleStep",
    )
    val kind = kinds[step.toInt().coerceIn(0, kinds.size - 1)]
    val line = source.lineSequence().first().let { if (it.length > 28) it.take(27) + "…" else it }
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.start.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)))
            },
            label = "styleCycleText",
        ) { current ->
            Column {
                Text(
                    skinText(current.label),
                    color = accent.start,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    StyledText.apply(line, current),
                    color = Palette.TextPrimary,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One variant: its name, how it looks, and the two buttons. The whole card copies on tap. */
@Composable
internal fun VariantCard(variant: StyledVariant, copied: Boolean, index: Int, onCopy: () -> Unit, onShare: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    val ring by animateColorAsState(if (copied) accent.start else Color.Transparent, tween(220), label = "copiedRing")
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 80)
            .bounceClick(pressedScale = 0.98f, onClick = onCopy)
            .toolCard()
            .border(2.dp, ring, shape)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skinText(variant.kind.label),
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
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
        }
        if (variant.kind.whatsApp) {
            WhatsAppBubble(variant.shown)
            Text(
                variant.text,
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                variant.text,
                color = Palette.TextPrimary,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(if (copied) "Copié" else "Copier", if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, filled = false, Modifier.weight(1f), onCopy)
            CardButton("WhatsApp", Icons.AutoMirrored.Rounded.Send, filled = true, Modifier.weight(1f), onShare)
        }
    }
}

/** The text as WhatsApp draws it once sent, in a chat bubble. */
@Composable
private fun WhatsAppBubble(shown: ShownText) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    val bubble = when (skin) {
        Skin.TERMINAL -> Modifier.border(1.dp, accent.start.copy(alpha = 0.5f), shape)
        // WhatsApp's own green, softened so the text stays readable on every skin.
        else -> Modifier.background(Color(0xFF25D366).copy(alpha = 0.16f), shape)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .then(bubble)
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (shown.quote) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.start),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                shown.text,
                color = Palette.TextPrimary.copy(alpha = if (shown.quote) 0.75f else 1f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = if (shown.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (shown.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (shown.strike) TextDecoration.LineThrough else null,
                fontFamily = if (shown.mono) FontFamily.Monospace else null,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Copier" (outlined) or "WhatsApp" (filled with the accent) at the bottom of a card. */
@Composable
private fun CardButton(text: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
