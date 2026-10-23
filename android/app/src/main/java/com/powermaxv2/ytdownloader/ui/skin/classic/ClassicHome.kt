package com.powermaxv2.ytdownloader.ui.skin.classic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.FeatureChip
import com.powermaxv2.ytdownloader.ui.IconCircleButton
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.hub.FavoriteStar
import com.powermaxv2.ytdownloader.ui.hub.GulpTool
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.hub.nudgeRight
import com.powermaxv2.ytdownloader.ui.hub.readyText
import com.powermaxv2.ytdownloader.ui.hub.upcomingText
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.delay

// Home screen of the hub in the Gulp skin: dark, the red → orange gradient running around the cards.

/** Big lockup, "Tes outils" rising word by word, and the count of tools. */
@Composable
fun ClassicHomeHeader(ready: Int, upcoming: Int, onSettings: () -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GulpLockup(markSize = 34.dp, textColor = Palette.TextPrimary)
            Spacer(Modifier.weight(1f))
            IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = onSettings)
        }
        Spacer(Modifier.height(22.dp))
        Row {
            listOf("Tes", "outils").forEachIndexed { i, word ->
                RisingWord(word, i, if (i == 1) LocalAccent.current.horizontal() else null)
                if (i == 0) Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.staggeredEntrance(2, baseDelay = 200)) {
            LiveDot(Palette.Success)
            Spacer(Modifier.width(8.dp))
            Text(
                "${readyText(ready)} · ${upcomingText(upcoming)}",
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** A word that springs up from below the line, [index] × 90 ms after the screen appears; [brush] paints it. */
@Composable
private fun RisingWord(word: String, index: Int, brush: Brush?) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L + index * 90L)
        rise.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
    }
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
        letterSpacing = (-1).sp,
        brush = brush,
    )
    Text(
        word,
        color = if (brush == null) Palette.TextPrimary else Color.Unspecified,
        style = style,
        maxLines = 1,
        modifier = Modifier.graphicsLayer {
            val p = rise.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 26.dp.toPx()
            rotationX = (1f - p) * 50f
        },
    )
}

/** Small status dot with a ring that keeps expanding and fading ("live"). */
@Composable
internal fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val ring by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)), label = "liveRing")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer {
                    scaleX = 0.4f + ring * 0.8f
                    scaleY = 0.4f + ring * 0.8f
                    alpha = (1f - ring) * 0.6f
                }
                .clip(CircleShape)
                .background(color),
        )
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

/**
 * A tool that is out: the brand gradient sweeps around its outline, its platforms float on a wave,
 * and the "Ouvrir" arrow pushes forward now and then. The whole card opens the tool.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicToolCard(tool: GulpTool, index: Int, starred: Boolean = false, onStar: (() -> Unit)? = null, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    val radius = LocalTokens.current.cardRadius
    val transition = rememberInfiniteTransition(label = "toolBorder")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "toolBorderAngle")
    val glow by transition.animateFloat(0.18f, 0.34f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "toolGlow")
    val outer = RoundedCornerShape(radius + 2.dp)
    Box(
        Modifier
            .staggeredEntrance(index, baseDelay = 260)
            .bounceClick(pressedScale = 0.97f, onClick = onOpen)
            .fillMaxWidth()
            .drawBehind {
                // Soft halo of the accent under the card.
                drawRoundRect(accent.start.copy(alpha = glow), cornerRadius = CornerRadius((radius + 6.dp).toPx()), size = size)
            }
            .clip(outer)
            .drawBehind {
                rotate(angle) {
                    drawCircle(
                        Brush.sweepGradient(listOf(accent.start, accent.end, Color.Transparent, Color.Transparent, accent.start)),
                        radius = size.maxDimension,
                    )
                }
            }
            .padding(1.5.dp)
            .clip(RoundedCornerShape(radius))
            .background(Palette.Surface)
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = tool.icon
                if (icon != null) {
                    // A tool that is not about a website wears its own symbol on a gradient tile, floating.
                    Box(
                        Modifier
                            .bob(index, amplitude = 3.dp)
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.brush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(30.dp))
                    }
                } else {
                    GulpMark(60.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tool.name,
                        color = Palette.TextPrimary,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = LocalTokens.current.display,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(tool.tagline, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (tool.tags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tool.tags.forEach { FeatureChip(it) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(Palette.Success)
                Spacer(Modifier.width(8.dp))
                Text("Prêt", color = Palette.Success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (onStar != null) {
                    FavoriteStar(starred, tool.name, onStar)
                    Spacer(Modifier.width(6.dp))
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(accent.horizontal())
                        .padding(start = 20.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ouvrir", color = Palette.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Palette.OnAccent,
                        modifier = Modifier.size(18.dp).nudgeRight(),
                    )
                }
            }
        }
    }
}

/** "À venir": a grid of dashed placeholders, two per row, each with a "+" that breathes and a shimmering "Bientôt". */
@Composable
fun ClassicComingSoon(tools: List<GulpTool>, firstNumber: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp)
                .staggeredEntrance(firstNumber, baseDelay = 300),
        ) {
            Text("À venir", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = LocalTokens.current.display)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Palette.Stroke))
        }
        tools.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, tool ->
                    ClassicSoonCard(tool, firstNumber + row * 2 + col, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "D'autres outils arriveront avec les mises à jour.",
            color = Palette.TextTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier
                .padding(top = 4.dp)
                .staggeredEntrance(firstNumber + tools.size, baseDelay = 300),
        )
    }
}

@Composable
private fun ClassicSoonCard(tool: GulpTool, number: Int, modifier: Modifier = Modifier) {
    val radius = LocalTokens.current.cardRadius
    val dash = Palette.TextTertiary.copy(alpha = 0.55f)
    val transition = rememberInfiniteTransition(label = "soon")
    val breathe by transition.animateFloat(
        0.88f, 1.08f, infiniteRepeatable(tween(1500 + number * 130, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "soonBreathe",
    )
    val dashPhase by transition.animateFloat(0f, 40f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "soonDash")
    Column(
        modifier
            .staggeredEntrance(number, baseDelay = 300)
            .clip(RoundedCornerShape(radius))
            .background(Palette.Surface.copy(alpha = 0.55f))
            .drawBehind {
                // Marching dashed outline: "under construction".
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    dash,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), dashPhase)),
                )
            }
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                }
                .clip(RoundedCornerShape(13.dp))
                .background(Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(tool.name, color = Palette.TextPrimary.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bientôt",
            color = Palette.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(Palette.Surface2)
                .shimmer()
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Group title of the home screen: bold words then a hairline running to the edge. */
@Composable
fun ClassicHomeSection(title: String, order: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .staggeredEntrance(order, baseDelay = 220),
    ) {
        Text(title, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = LocalTokens.current.display)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Stroke))
    }
}

/**
 * Small bubble of a tool (half a card): its symbol floats on a gradient tile, a thin gradient outline breathes,
 * and the arrow pushes forward now and then. The whole bubble opens the tool.
 */
@Composable
fun ClassicToolBubble(
    tool: GulpTool,
    order: Int,
    modifier: Modifier = Modifier,
    starred: Boolean = false,
    onStar: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val accent = LocalAccent.current
    val radius = LocalTokens.current.cardRadius
    val transition = rememberInfiniteTransition(label = "bubble")
    val rim by transition.animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(1900 + order * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bubbleRim",
    )
    Column(
        modifier
            .staggeredEntrance(order, baseDelay = 300)
            .bounceClick(pressedScale = 0.94f, onClick = onOpen)
            .clip(RoundedCornerShape(radius))
            .background(Palette.Surface)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    accent.horizontal(),
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    alpha = rim,
                    style = Stroke(w),
                )
            }
            .padding(16.dp),
    ) {
        val icon = tool.icon
        if (icon != null) {
            Box(
                Modifier
                    .bob(order, amplitude = 2.5.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(23.dp))
            }
        } else {
            GulpMark(44.dp, modifier = Modifier.bob(order, amplitude = 2.5.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tool.name,
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(Palette.Success)
            Spacer(Modifier.width(6.dp))
            Text("Prêt", color = Palette.Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (onStar != null) FavoriteStar(starred, tool.name, onStar, size = 28.dp)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.horizontal()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Ouvrir",
                    tint = Palette.OnAccent,
                    modifier = Modifier.size(17.dp).nudgeRight(3.dp, periodMs = 2200 + order * 180),
                )
            }
        }
    }
}
