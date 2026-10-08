package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.hub.GulpTool
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.hub.nudgeRight
import com.powermaxv2.ytdownloader.ui.hub.readyText
import com.powermaxv2.ytdownloader.ui.hub.toolNumber
import com.powermaxv2.ytdownloader.ui.hub.upcomingText
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.delay

// Home screen of the hub in the Papier skin: an editorial table of contents. Numbered entries, ink stamps.

/** Lockup, huge "Mes outils" on the accent smile that draws itself, and the count of tools. */
@Composable
fun PaperHomeHeader(ready: Int, upcoming: Int, onSettings: () -> Unit) {
    val accent = LocalAccent.current.start
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, delayMillis = 200, easing = FastOutSlowInEasing)) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            GulpLockup(markSize = 26.dp, textColor = Palette.TextPrimary, fontSize = 21.sp)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.width(IntrinsicSize.Max)) {
                Text(
                    "Mes outils",
                    color = Palette.TextPrimary,
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    letterSpacing = (-1.2).sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.3f + 0.7f * rise.value.coerceIn(0f, 1f)
                        translationY = (1f - rise.value) * 10.dp.toPx()
                    },
                )
                PaperSmileUnderline({ grow.value }, { 0f }, accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${readyText(ready)} · ${upcomingText(upcoming)}",
                color = Palette.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.staggeredEntrance(1, baseDelay = 260),
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .padding(top = 2.dp)
                .bounceClick(pressedScale = 0.9f, onClick = onSettings)
                .size(56.dp)
                .paperCard(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Réglages", tint = Palette.TextPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Ink stamp ("PRÊT", "BIENTÔT"): slammed onto the page [delayMs] after it appears (big and see-through, then
 * bouncing flat), then [wiggle]s a little forever if asked.
 */
@Composable
internal fun PaperStamp(text: String, color: Color, tilt: Float, delayMs: Long, wiggle: Boolean = false, modifier: Modifier = Modifier) {
    val slam = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        slam.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
    }
    val sway = if (wiggle) {
        val transition = rememberInfiniteTransition(label = "stampWiggle")
        transition.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "stampSway").value
    } else {
        0f
    }
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.4.sp,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                val p = slam.value
                val scale = 2.2f - 1.2f * p
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
                rotationZ = tilt + sway
            }
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** A tool that is out: big accent number, "PRÊT" stamp, floating platform logos and an ink "Ouvrir" bar. */
@Composable
fun PaperToolCard(tool: GulpTool, index: Int, onOpen: () -> Unit) {
    val accent = LocalAccent.current.start
    Column(
        Modifier
            .staggeredEntrance(index, baseDelay = 220)
            .bounceClick(pressedScale = 0.97f, onClick = onOpen)
            .fillMaxWidth()
            .paperCard()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                toolNumber(index + 1),
                color = accent,
                fontSize = 44.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                letterSpacing = (-1.5).sp,
            )
            val icon = tool.icon
            if (tool.platforms.isEmpty() && icon != null) {
                Spacer(Modifier.width(12.dp))
                // The tool's symbol next to its number, floating on the cream.
                Box(
                    Modifier
                        .bob(index, amplitude = 2.5.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            PaperStamp("PRÊT", accent, tilt = -7f, delayMs = 650)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tool.name,
            color = Palette.TextPrimary,
            fontSize = 26.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(tool.tagline, color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (tool.platforms.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tool.platforms.forEachIndexed { i, platform ->
                    Box(
                        Modifier
                            .bob(i, amplitude = 2.5.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlatformGlyph(platform, Modifier.size(21.dp))
                    }
                }
            }
        }
        if (tool.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                tool.tags.joinToString("  ·  "),
                color = Palette.TextTertiary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(18.dp))
        val ink = Palette.TextPrimary
        val shape = RoundedCornerShape(LocalTokens.current.buttonRadius)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape, ambientColor = ink, spotColor = ink)
                .clip(shape)
                .background(ink)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ouvrir l'outil", color = GulpColors.Cream, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).nudgeRight(3.dp),
                )
            }
        }
    }
}

/** "À venir": numbered entries of a table of contents, a dashed ink outline and a "BIENTÔT" stamp that sways. */
@Composable
fun PaperComingSoon(tools: List<GulpTool>, firstNumber: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "À venir",
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            modifier = Modifier
                .padding(top = 10.dp, bottom = 2.dp)
                .staggeredEntrance(firstNumber, baseDelay = 260),
        )
        tools.forEachIndexed { i, tool ->
            PaperSoonRow(tool, firstNumber + i)
        }
        Text(
            "D'autres outils arriveront avec les mises à jour.",
            color = Palette.TextTertiary,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 4.dp)
                .staggeredEntrance(firstNumber + tools.size, baseDelay = 260),
        )
    }
}

@Composable
private fun PaperSoonRow(tool: GulpTool, number: Int) {
    val ink = Palette.TextPrimary.copy(alpha = 0.22f)
    val radius = LocalTokens.current.cardRadius
    Row(
        Modifier
            .staggeredEntrance(number, baseDelay = 260)
            .fillMaxWidth()
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    ink,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))),
                )
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            toolNumber(number),
            color = Palette.TextTertiary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.name, color = Palette.TextPrimary.copy(alpha = 0.75f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(tool.tagline, color = Palette.TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        PaperStamp("BIENTÔT", Palette.TextSecondary, tilt = 5f, delayMs = 800L + number * 140L, wiggle = true)
    }
}

/** Group title of the home screen, in the display face. */
@Composable
fun PaperHomeSection(title: String, order: Int) {
    Text(
        title,
        color = Palette.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
        modifier = Modifier
            .padding(top = 6.dp)
            .staggeredEntrance(order, baseDelay = 220),
    )
}

/**
 * Small entry of a tool (half a card): accent number and floating symbol, the name in the display face, and a
 * little ink tile whose arrow pushes forward now and then. The whole entry opens the tool.
 */
@Composable
fun PaperToolBubble(tool: GulpTool, index: Int, order: Int, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val accent = LocalAccent.current.start
    Column(
        modifier
            .staggeredEntrance(order, baseDelay = 260)
            .bounceClick(pressedScale = 0.94f, onClick = onOpen)
            .paperCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                toolNumber(index + 1),
                color = accent,
                fontSize = 28.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .bob(order, amplitude = 2.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Background),
                contentAlignment = Alignment.Center,
            ) {
                val icon = tool.icon
                val platform = tool.platforms.firstOrNull()
                when {
                    tool.platforms.isEmpty() && icon != null -> Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    platform != null -> PlatformGlyph(platform, Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tool.name,
            color = Palette.TextPrimary,
            fontSize = 18.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = (-0.4).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ouvrir", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.TextPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = GulpColors.Cream,
                    modifier = Modifier.size(16.dp).nudgeRight(3.dp, periodMs = 2200 + order * 180),
                )
            }
        }
    }
}
