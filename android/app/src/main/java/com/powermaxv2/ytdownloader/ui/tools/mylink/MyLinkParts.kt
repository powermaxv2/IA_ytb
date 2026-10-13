package com.powermaxv2.ytdownloader.ui.tools.mylink

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.mylink.MyLink
import com.powermaxv2.ytdownloader.core.mylink.MyLinks
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction

// Pieces of Mon lien WhatsApp: the compact card of the kept number, the ready-made messages, the halo that sweeps
// around the code, the link under it, and the placeholder while there is no code yet.

/** WhatsApp's own green (softened by each skin's card). */
private val WhatsAppGreen = Color(0xFF25D366)

@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** The kept number: a pulsing green avatar, the number and its country, "Enregistré sur ce téléphone", "Modifier". */
@Composable
internal fun IdentityCard(ready: MyLink.Ready, onEdit: () -> Unit) {
    val skin = LocalSkin.current
    val transition = rememberInfiniteTransition(label = "identity")
    val pulse by transition.animateFloat(1f, 1.22f, infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "identityPulse")
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(50.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            alpha = 2f - pulse * 1.5f
                        }
                        .border(2.dp, WhatsAppGreen, CircleShape),
                )
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (skin == Skin.TERMINAL) Color.Transparent else WhatsAppGreen.copy(alpha = 0.18f))
                        .border(2.dp, WhatsAppGreen, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ready.country?.flag ?: "🌐", fontSize = 22.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(ready.pretty, color = Palette.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = Palette.Success, modifier = Modifier.size(14.dp))
                    Text(skinText("Enregistré sur ce téléphone"), color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
                }
            }
            MiniAction("Modifier", Icons.Rounded.Edit, onClick = onEdit)
        }
        if (ready.maybeLandline) {
            Text("On dirait un fixe : vérifie qu'il a bien WhatsApp.", color = Palette.TextTertiary, fontSize = 12.5.sp)
        }
    }
}

/** Ready-made messages in a sideways row; a touch puts one in the message. */
@Composable
internal fun QuickChips(messages: List<String>, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.forEach { text ->
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
 * Around the code: a green light that keeps turning along the frame, and a scan line that sweeps down it, as if a
 * phone were reading it. Static in the terminal skin's spirit: only the scan line there.
 */
@Composable
internal fun ScanHalo(content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "scanHalo")
    val turn by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "haloTurn")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scanSweep")
    val radius = if (skin == Skin.TERMINAL) 6.dp else 28.dp
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.padding(8.dp)) { content() }
        Canvas(Modifier.matchParentSize()) {
            val r = radius.toPx()
            val w = 3.dp.toPx()
            if (skin != Skin.TERMINAL) {
                // A bright tail running around the rounded frame: the sweep gradient's stops shift with [turn].
                val stops = (0..48).map { i ->
                    val f = i / 48f
                    val behind = ((turn - f) % 1f + 1f) % 1f
                    f to WhatsAppGreen.copy(alpha = if (behind < 0.3f) 1f - behind / 0.3f else 0f)
                }
                drawRoundRect(
                    Brush.sweepGradient(*stops.toTypedArray(), center = center),
                    Offset(w / 2, w / 2),
                    Size(size.width - w, size.height - w),
                    CornerRadius(r),
                    style = Stroke(w),
                )
            } else {
                drawRoundRect(accent.start, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(r), style = Stroke(1.dp.toPx()))
            }
            // Scan line across the code.
            val inset = 22.dp.toPx()
            val y = inset + (size.height - 2 * inset) * sweep
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, WhatsAppGreen.copy(alpha = 0.28f)), startY = y - 26.dp.toPx(), endY = y),
                topLeft = Offset(inset, y - 26.dp.toPx()),
                size = Size(size.width - 2 * inset, 26.dp.toPx()),
            )
            drawRect(WhatsAppGreen.copy(alpha = 0.8f), topLeft = Offset(inset, y - 1.dp.toPx()), size = Size(size.width - 2 * inset, 2.dp.toPx()))
        }
    }
}

/** "wa.me/33612345678" under the code, rolling to the new link when it changes; a hint while there is none. */
@Composable
internal fun LinkLine(ready: MyLink.Ready?) {
    AnimatedContent(
        targetState = ready?.let { MyLinks.shortLink(it.digits) to it.link.contains("?text=") },
        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
        label = "myLinkLine",
    ) { line ->
        if (line == null) {
            Text(skinText("Ton lien s'affichera ici"), color = Palette.TextTertiary, fontSize = 13.sp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    line.first,
                    color = WhatsAppGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.second) {
                    Spacer(Modifier.height(2.dp))
                    Text(skinText("+ ton message prêt"), color = Palette.TextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Where the code will be: a faint QR icon that breathes, and why there is no code yet. */
@Composable
internal fun CodePlaceholder(text: String) {
    val accent = LocalAccent.current.start
    val transition = rememberInfiniteTransition(label = "codePlaceholder")
    val breath by transition.animateFloat(0.55f, 0.95f, infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    Column(
        Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) 4.dp else 22.dp))
            .background(Palette.TextPrimary.copy(alpha = 0.05f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.QrCode2,
            contentDescription = null,
            tint = accent.copy(alpha = breath),
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = 0.9f + breath * 0.12f
                    scaleY = 0.9f + breath * 0.12f
                },
        )
        Spacer(Modifier.height(10.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}
