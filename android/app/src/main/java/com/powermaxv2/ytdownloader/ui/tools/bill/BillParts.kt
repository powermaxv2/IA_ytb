package com.powermaxv2.ytdownloader.ui.tools.bill

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingBag
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.bill.BillCurrency
import com.powermaxv2.ytdownloader.core.bill.BillSplitter
import com.powermaxv2.ytdownloader.core.bill.Payback
import com.powermaxv2.ytdownloader.core.bill.Share
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.chat.StatBar
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlinx.coroutines.delay
import kotlin.math.min

// Pieces of Partage d'addition: the big amount, a person at the table (avatar, parts stepper, own order, "a payé"),
// the receipt that prints itself (ring of shares, rolling amounts, bars) and the arrows of who pays back whom.

/** One color per person, the same on the avatar, the ring and the bar. */
private val PersonColors = listOf(
    Color(0xFFFF6B4A), Color(0xFF3DB7F5), Color(0xFFF5B83D), Color(0xFF8E6CF0),
    Color(0xFF34C77B), Color(0xFFF0549A), Color(0xFF22C3B5), Color(0xFFB98A4E),
)

internal fun personColor(index: Int): Color = PersonColors[index % PersonColors.size]

@Composable
internal fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** An amount that rolls: the new value slides in from below when it grows, from above when it shrinks. */
@Composable
internal fun RollingAmount(
    cents: Long,
    currency: BillCurrency,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    display: Boolean = false,
) {
    AnimatedContent(
        targetState = cents,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { if (up) it else -it } + fadeIn()) togetherWith
                (slideOutVertically { if (up) -it else it } + fadeOut())
        },
        label = "rollingAmount",
        modifier = modifier,
    ) { value ->
        Text(
            BillSplitter.format(value, currency),
            color = color,
            fontSize = fontSize,
            fontWeight = weight,
            fontFamily = if (display) LocalTokens.current.display else null,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The bill's amount, big, with its currency; a line in red when what is typed is not an amount. */
@Composable
internal fun BigAmountField(value: String, currency: BillCurrency, onChange: (String) -> Unit) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val bad = value.isNotBlank() && BillSplitter.parseAmount(value) == null
    val line by animateColorAsState(if (bad) Palette.Danger else accent.start, tween(220), label = "amountLine")
    val shape = RoundedCornerShape(tokens.fieldRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(if (bad) 2.dp else 1.dp, if (bad) line else Palette.Stroke, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            if (skin == Skin.TERMINAL) "> MONTANT DE LA NOTE" else "Montant de la note",
            color = Palette.TextTertiary,
            fontSize = 11.5.sp,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!currency.suffix) {
                Text(currency.symbol, color = line, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = tokens.display)
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("0,00", color = Palette.TextTertiary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFamily = tokens.display, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onChange(it.take(14)) },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFamily = tokens.display),
                    cursorBrush = SolidColor(accent.start),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (currency.suffix) {
                Spacer(Modifier.width(6.dp))
                Text(currency.symbol, color = line, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = tokens.display)
            }
        }
        AnimatedVisibility(bad, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(skinText("Écris un montant comme 86,50"), color = Palette.Danger, fontSize = 12.5.sp)
        }
    }
}

/** Round badge with the first letter of the name, in the person's color; a coin spins on it for the payer. */
@Composable
internal fun Avatar(name: String, index: Int, payer: Boolean, size: Int = 38) {
    val skin = LocalSkin.current
    val color = personColor(index)
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "${index + 1}"
    Box(
        Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(if (skin == Skin.TERMINAL) Color.Transparent else color.copy(alpha = 0.22f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = letter,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "avatarLetter",
            ) { l ->
                Text(l, color = if (skin == Skin.PAPER) Palette.TextPrimary else color, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnimatedVisibility(
            payer,
            enter = scaleIn(spring(dampingRatio = 0.4f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = 5.dp.toPx()
                    translationY = 4.dp.toPx()
                },
        ) {
            val spin = rememberInfiniteTransition(label = "payerCoin")
            val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "coinTurn")
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationY = turn }
                    .clip(CircleShape)
                    .background(Color(0xFFF0B940)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Payments, contentDescription = "A payé", tint = Color(0xFF5A3D00), modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** "− 1½ +": the person's part of what is shared, by halves; the number slides the way it moves. */
@Composable
internal fun PartsStepper(halves: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, Palette.Stroke, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(Icons.Rounded.Remove, "Moins", enabled = halves > 0) { onChange(halves - 1) }
        AnimatedContent(
            targetState = halves,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 500f)) { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "parts",
        ) { h ->
            Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    BillSplitter.partsText(h),
                    color = if (h == 0) Palette.TextTertiary else Palette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(skinText(if (h <= 2) "part" else "parts"), color = Palette.TextTertiary, fontSize = 9.5.sp, maxLines = 1)
            }
        }
        StepButton(Icons.Rounded.Add, "Plus", enabled = halves < BillSplitter.MAX_HALVES) { onChange(halves + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .bounceClick(enabled = enabled, pressedScale = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) LocalAccent.current.start else Palette.Stroke, modifier = Modifier.size(18.dp))
    }
}

/** A small on/off pill under a person ("Commande perso", "A payé"). */
@Composable
internal fun TogglePill(text: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val fill by animateColorAsState(
        when {
            !on -> Color.Transparent
            skin == Skin.PAPER -> Palette.TextPrimary
            else -> accent.tint(0.2f)
        },
        tween(200),
        label = "pillFill",
    )
    val ink = when {
        on && skin == Skin.PAPER -> Palette.Background
        on -> accent.start
        else -> Palette.TextSecondary
    }
    Row(
        Modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (on) accent.start.copy(alpha = 0.7f) else Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
        Text(skinText(text), color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** One person at the table: avatar, name, parts, cross; under it the own-order and payer pills, and the own amount. */
@Composable
internal fun DinerRow(
    diner: DinerInput,
    index: Int,
    payer: Boolean,
    currency: BillCurrency,
    canRemove: Boolean,
    actions: BillActions,
) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, key = diner.id, baseDelay = 30),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(diner.name, index, payer)
            Box(Modifier.weight(1f)) {
                if (diner.name.isEmpty()) {
                    Text(skinText("Personne ${index + 1}"), color = Palette.TextTertiary, fontSize = 16.sp, maxLines = 1)
                }
                BasicTextField(
                    value = diner.name,
                    onValueChange = { actions.onName(diner.id, it.take(24)) },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = tokens.body),
                    cursorBrush = SolidColor(accent.start),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PartsStepper(diner.halves) { actions.onHalves(diner.id, it) }
            Box(
                Modifier
                    .size(32.dp)
                    .bounceClick(enabled = canRemove, pressedScale = 0.8f) { actions.onRemove(diner.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Retirer",
                    tint = if (canRemove) Palette.TextTertiary else Color.Transparent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TogglePill("Commande perso", Icons.Rounded.ShoppingBag, diner.ownOpen) { actions.onOwnOpen(diner.id) }
            TogglePill("A payé", Icons.Rounded.Payments, payer) { actions.onPayer(diner.id) }
        }
        AnimatedVisibility(
            diner.ownOpen,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val shape = RoundedCornerShape(tokens.fieldRadius)
            val bad = diner.own.isNotBlank() && BillSplitter.parseAmount(diner.own) == null
            Row(
                Modifier
                    .padding(start = 48.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
                    .border(1.dp, if (bad) Palette.Danger else Palette.Stroke, shape)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(skinText("Rien que pour ${BillSplitter.displayName(diner.name, index)}"), color = Palette.TextTertiary, fontSize = 11.sp, maxLines = 1)
                    Box {
                        if (diner.own.isEmpty()) Text("0,00", color = Palette.TextTertiary, fontSize = 16.sp)
                        BasicTextField(
                            value = diner.own,
                            onValueChange = { actions.onOwn(diner.id, it.take(12)) },
                            singleLine = true,
                            textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = tokens.body),
                            cursorBrush = SolidColor(accent.start),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(currency.symbol, color = Palette.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// MARK: - Receipt

/** Ring of the shares: each person's arc grows from the top, one after the other, in their color. */
@Composable
internal fun ShareRing(fractions: List<Float>, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        reveal.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
    }
    val track = Palette.Stroke
    // Each arc eases to its new size when a part changes.
    val animated = fractions.mapIndexed { i, f ->
        animateFloatAsState(f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "ring$i").value
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = min(size.width, size.height) * 0.12f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val total = animated.sum().coerceAtLeast(0.0001f)
            val gap = if (animated.count { it > 0f } > 1) 2.5f else 0f
            animated.forEachIndexed { i, f ->
                val sweep = 360f * f / total * reveal.value
                if (sweep > gap) {
                    drawArc(
                        personColor(i),
                        start + gap / 2f,
                        sweep - gap,
                        false,
                        Offset(inset, inset),
                        arcSize,
                        style = Stroke(stroke, cap = if (skin == Skin.CLASSIC) StrokeCap.Round else StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** One line of the receipt: dot, name, bar of their share, rolling amount. */
@Composable
internal fun ShareLine(share: Share, label: String, note: String?, index: Int, fraction: Float, currency: BillCurrency) {
    val color = personColor(index)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 160),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                note?.let { Text(skinText(it), color = Palette.TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            RollingAmount(share.cents, currency, 17.sp, Palette.TextPrimary, weight = FontWeight.ExtraBold)
        }
        StatBar(fraction, color, Modifier.padding(start = 20.dp), delayMs = 200 + index * 70, height = 6.dp)
    }
}

/** "Tom → Léa  23,40 €": the arrow keeps sliding toward the payer. */
@Composable
internal fun PaybackLine(payback: Payback, fromIndex: Int, toIndex: Int, index: Int, currency: BillCurrency) {
    val slide = rememberInfiniteTransition(label = "payback")
    val dx by slide.animateFloat(-3f, 3f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "arrowSlide")
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(payback.from, fromIndex, payer = false, size = 26)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "doit à",
            tint = LocalAccent.current.start,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { translationX = dx.dp.toPx() },
        )
        Avatar(payback.to, toIndex, payer = true, size = 26)
        Text(
            "${payback.from} → ${payback.to}",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RollingAmount(payback.cents, currency, 15.sp, Palette.TextPrimary)
    }
}

/** The receipt's card with a torn, zigzag bottom edge, printing itself downward on its first appearance. */
@Composable
internal fun Modifier.receiptCard(): Modifier {
    val skin = LocalSkin.current
    val edge = Palette.Background
    val print = remember { Animatable(0f) }
    LaunchedEffect(Unit) { print.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    return this
        .graphicsLayer {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            scaleY = 0.6f + 0.4f * print.value
            alpha = print.value
        }
        .toolCard()
        .drawBehind {
            if (skin == Skin.TERMINAL) return@drawBehind
            // Teeth cut out of the bottom edge, like a till roll torn off.
            val tooth = 14.dp.toPx()
            val depth = 6.dp.toPx()
            val path = Path()
            var x = 0f
            path.moveTo(0f, size.height)
            while (x < size.width) {
                path.lineTo(x + tooth / 2f, size.height - depth)
                path.lineTo(min(x + tooth, size.width), size.height)
                x += tooth
            }
            path.close()
            drawPath(path, edge)
        }
}
