package com.powermaxv2.ytdownloader.ui.tools.direct

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.direct.Countries
import com.powermaxv2.ytdownloader.core.direct.Country
import com.powermaxv2.ytdownloader.core.direct.DirectNumber
import com.powermaxv2.ytdownloader.core.direct.DirectNumbers
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// Pieces of Message direct: the country button and its chips, the line that says what Gulp read in the number,
// the ready-made openings, the little WhatsApp chat that previews what will open, and the recent numbers.

/** WhatsApp's own green, used for the chat preview (softened on every skin). */
private val WhatsAppGreen = Color(0xFF25D366)

@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** Flag and "+33": opens the list of countries; its arrow turns when it is open. */
@Composable
internal fun CountryButton(country: Country, open: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "countryArrow")
    Row(
        Modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnimatedContent(
            targetState = country,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 420f)) { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            },
            label = "countryCode",
        ) { c ->
            Text("${c.flag} +${c.code}", color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = "Pays",
            tint = LocalAccent.current.start,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

/** Every country of the list as a chip; the chosen one is filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountryChips(selected: Country, onPick: (Country) -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Countries.all.forEachIndexed { i, country ->
            val chosen = country.iso == selected.iso
            Text(
                skinText("${country.flag} ${country.name} +${country.code}"),
                color = if (chosen) accent.start else Palette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .staggeredEntrance(i, baseDelay = 0)
                    .bounceClick(pressedScale = 0.9f) { onPick(country) }
                    .clip(shape)
                    .background(if (chosen) accent.tint(0.18f) else Color.Transparent)
                    .border(1.dp, if (chosen) accent.start else Palette.Stroke, shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** What Gulp reads in the number: the clean international form with a tick, or why it does not work. */
@Composable
internal fun NumberVerdict(parsed: DirectNumber) {
    AnimatedContent(
        targetState = parsed,
        contentKey = { it::class },
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.85f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.9f) + fadeOut())
        },
        label = "numberVerdict",
    ) { p ->
        when (p) {
            DirectNumber.Empty -> VerdictLine(
                Icons.Rounded.Info,
                Palette.TextTertiary,
                "Écris ou colle le numéro, avec ou sans +33 : espaces et points ne gênent pas.",
            )
            is DirectNumber.Bad -> VerdictLine(Icons.Rounded.ErrorOutline, Palette.Danger, p.reason)
            is DirectNumber.Ok -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VerdictLine(
                    Icons.Rounded.CheckCircle,
                    Palette.Success,
                    listOfNotNull(p.pretty, p.country?.let { "${it.flag} ${it.name}" }).joinToString("  ·  "),
                    bold = true,
                )
                if (p.maybeLandline) {
                    VerdictLine(Icons.Rounded.Info, Palette.TextTertiary, "On dirait un fixe : il n'a peut-être pas WhatsApp.")
                }
            }
        }
    }
}

@Composable
private fun VerdictLine(icon: ImageVector, tint: Color, text: String, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = if (bold) Palette.TextPrimary else tint,
            fontSize = 13.5.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Ready-made openings in a sideways row; a touch puts one in the message. */
@Composable
internal fun QuickMessages(onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DIRECT_QUICK.forEach { text ->
            Text(
                text,
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .bounceClick(pressedScale = 0.9f) { onPick(text) }
                    .clip(shape)
                    .border(1.dp, Palette.Stroke, shape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * A tiny WhatsApp chat: who it opens on (the number, or "?" while there is none), then the message as a sent
 * bubble, or three dots that keep typing while there is no message.
 */
@Composable
internal fun ChatPreview(ready: DirectNumber.Ok?, message: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "directPreview")
    val pulse by transition.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "avatarPulse")
    val dot by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1_200)), label = "typingDots")
    val ring by animateColorAsState(if (ready != null) WhatsAppGreen else Palette.Stroke, tween(300), label = "avatarRing")
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            val s = if (ready != null) pulse else 1f
                            scaleX = s
                            scaleY = s
                            alpha = if (ready != null) 2f - pulse * 1.4f else 0f
                        }
                        .border(2.dp, WhatsAppGreen, CircleShape),
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (skin == Skin.TERMINAL) Color.Transparent else accent.tint(0.18f))
                        .border(2.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = accent.start, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = ready?.pretty ?: "Numéro inconnu",
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "previewName",
                ) { name ->
                    Text(name, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(
                    skinText(if (ready != null) "Pas dans tes contacts" else "En attente d'un numéro"),
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        AnimatedContent(
            targetState = message.isBlank(),
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.8f) + fadeOut())
            },
            label = "previewBubble",
        ) { empty ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                val bubble = if (skin == Skin.TERMINAL) {
                    Modifier.border(1.dp, accent.start.copy(alpha = 0.5f), shape)
                } else {
                    Modifier.background(WhatsAppGreen.copy(alpha = 0.16f), shape)
                }
                if (empty) {
                    Row(
                        Modifier
                            .clip(shape)
                            .then(bubble)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(3) { i ->
                            val lift = ((dot - i).let { if (it < 0f || it > 1f) 0f else 1f - kotlin.math.abs(it * 2f - 1f) })
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .graphicsLayer {
                                        translationY = -lift * 6.dp.toPx()
                                        alpha = 0.4f + lift * 0.6f
                                    }
                                    .clip(CircleShape)
                                    .background(Palette.TextSecondary),
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .widthIn(max = 290.dp)
                            .clip(shape)
                            .then(bubble)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            message.trim().take(DirectNumbers.MAX_MESSAGE),
                            color = Palette.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("✓✓", color = Color(0xFF53BDEB), fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
    }
}

/** One recent number: a touch puts it back in the field, the cross forgets it. */
@Composable
internal fun RecentRow(digits: String, index: Int, onPick: () -> Unit, onForget: () -> Unit) {
    val country = Countries.ofInternational(digits)
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 60)
            .bounceClick(pressedScale = 0.98f, onClick = onPick)
            .toolCard()
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(country?.flag ?: "🌐", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(DirectNumbers.pretty(digits), color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(skinText(country?.name ?: "Autre pays"), color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
        }
        Box(
            Modifier
                .size(44.dp)
                .bounceClick(pressedScale = 0.85f, onClick = onForget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Oublier ce numéro", tint = Palette.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
