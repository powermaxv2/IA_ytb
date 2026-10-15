package com.powermaxv2.ytdownloader.ui.tools.units

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.units.MeasureUnit
import com.powermaxv2.ytdownloader.core.units.Quantity
import com.powermaxv2.ytdownloader.core.units.Units
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

// Pieces of the Convertisseur d'unités: the quantity chips, the unit pickers and the swap between them, the result
// rolling digit by digit, the picture of the quantity (thermometer, speedometer, or two bars side by side) and the
// table of every unit.

/** The tool's home-card symbol. */
val UnitsIcon: ImageVector get() = Icons.Rounded.Straighten

/** Text in the skin's case (Terminal writes in capitals). */
@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

private fun Quantity.icon(): ImageVector = when (this) {
    Quantity.LENGTH -> Icons.Rounded.Straighten
    Quantity.MASS -> Icons.Rounded.Scale
    Quantity.TEMPERATURE -> Icons.Rounded.Thermostat
    Quantity.SPEED -> Icons.Rounded.Speed
    Quantity.COOKING -> Icons.Rounded.Restaurant
    Quantity.AREA -> Icons.Rounded.SquareFoot
    Quantity.VOLUME -> Icons.Rounded.LocalDrink
    Quantity.DATA -> Icons.Rounded.Storage
    Quantity.TIME -> Icons.Rounded.Schedule
}

/** "Longueur · Poids · Température…": a row of icon chips that scrolls sideways, the open one filled and popping. */
@Composable
internal fun QuantityChips(selected: Quantity, onSelect: (Quantity) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Quantity.entries.forEachIndexed { index, q ->
            QuantityChip(q, q == selected, Modifier.staggeredEntrance(index, baseDelay = 20)) { onSelect(q) }
        }
    }
}

@Composable
private fun QuantityChip(quantity: Quantity, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "chipPop")
    val ink by animateColorAsState(
        when {
            !selected -> Palette.TextPrimary
            skin == Skin.TERMINAL -> Palette.Background
            else -> Palette.OnAccent
        },
        tween(200),
        label = "chipInk",
    )
    val base = modifier
        .bounceClick(pressedScale = 0.9f, onClick = onClick)
        .clip(shape)
    val surface = when {
        selected && skin == Skin.CLASSIC -> base.background(accent.brush)
        selected -> base.background(accent.start)
        skin == Skin.TERMINAL -> base.border(1.dp, Palette.Stroke, shape)
        else -> base.background(Palette.Surface2).border(1.dp, Palette.Stroke, shape)
    }
    Row(
        surface
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            quantity.icon(),
            contentDescription = null,
            tint = if (selected) ink else accent.start,
            modifier = Modifier
                .size(17.dp)
                .rotate(if (selected) (scale - 1f) * 120f else 0f),
        )
        Text(skinText(quantity.label), color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** "De : Kilomètre (km)": tapping it unfolds the units of the quantity as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitPicker(label: String, unit: MeasureUnit, open: Boolean, onOpen: () -> Unit, onPick: (MeasureUnit) -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    val arrow by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "pickerArrow")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick(pressedScale = 0.98f, onClick = onOpen)
                .clip(shape)
                .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
                .border(1.dp, if (open) accent.start else Palette.Stroke, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (skin == Skin.TERMINAL) "> ${label.uppercase()}" else label,
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.width(44.dp),
            )
            AnimatedContent(
                targetState = unit,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "pickerUnit",
                modifier = Modifier.weight(1f),
            ) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shown.symbol,
                        color = accent.start,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = LocalTokens.current.display,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        skinText(shown.name),
                        color = Palette.TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.rotate(arrow))
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.85f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Units.of(unit.quantity).forEachIndexed { index, option ->
                    UnitChip(option, option == unit, Modifier.staggeredEntrance(index, key = unit.quantity)) { onPick(option) }
                }
            }
        }
    }
}

@Composable
private fun UnitChip(unit: MeasureUnit, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Text(
        skinText("${unit.symbol} · ${unit.name}"),
        color = if (selected) accent.start else Palette.TextPrimary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(if (selected) accent.tint() else if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, if (selected) accent.start else Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A round button that turns half a turn, with a spring, each time the units are swapped. */
@Composable
internal fun SwapButton(swaps: Int, onSwap: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val turn by animateFloatAsState(swaps * 180f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "swapTurn")
    val shape = if (skin == Skin.TERMINAL) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape
    val ink = if (skin == Skin.TERMINAL) Palette.Background else Palette.OnAccent
    Box(
        Modifier
            .size(46.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onSwap)
            .clip(shape)
            .then(if (skin == Skin.CLASSIC) Modifier.background(accent.brush) else Modifier.background(accent.start)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.SwapVert, contentDescription = "Inverser les unités", tint = ink, modifier = Modifier.size(24.dp).rotate(turn))
    }
}

/** The result in big, each digit rolling in when it changes, the full line under it and its "Copier" button. */
@Composable
internal fun ResultBlock(state: UnitsUiState, onCopy: (String) -> Unit) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val value = state.value
    val result = state.result
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.start.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(skinText("Résultat"), color = accent.start, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(
                if (result == null) "—" else Units.format(result),
                Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                state.to.symbol,
                color = Palette.TextSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (value != null && result != null) {
            val line = Units.sentence(value, state.from, result, state.to) +
                if (Units.needsIngredient(state.from, state.to)) " (${state.ingredient.label.lowercase()})" else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (skin == Skin.TERMINAL) "# $line" else line,
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CopyPill(state.copied) { onCopy(line) }
            }
        }
    }
}

/** [text] written one character per slot; a slot whose character changes rolls the new one in from below. */
@Composable
internal fun RollingNumber(text: String, modifier: Modifier = Modifier) {
    val display = LocalTokens.current.display
    val size = when {
        text.length > 16 -> 26.sp
        text.length > 11 -> 32.sp
        else -> 40.sp
    }
    Row(modifier) {
        text.forEachIndexed { index, char ->
            // Keyed from the end: the units digit keeps its slot when the number grows.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 380f)) { it } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(tween(180)) { -it } + fadeOut(tween(120)))
                    },
                    label = "digit",
                ) { c ->
                    Text(
                        c.toString(),
                        color = Palette.TextPrimary,
                        fontSize = size,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = display,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyPill(copied: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Row(
        Modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, if (copied) Palette.Success else Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = { scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "copyIcon",
        ) { done ->
            Icon(
                if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = if (done) Palette.Success else accent.start,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(skinText(if (copied) "Copié" else "Copier"), color = if (copied) Palette.Success else Palette.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Pictures

/** A picture of the conversion: a thermometer, a speedometer, or 1 unit of each side as two bars. */
@Composable
internal fun QuantityVisual(state: UnitsUiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state.quantity) {
            Quantity.TEMPERATURE -> Thermometer(state)
            Quantity.SPEED -> Speedometer(state)
            else -> SizeBars(state)
        }
    }
}

/** Mercury from −30 °C to 50 °C, blue when cold and red when hot, with a few landmarks. */
@Composable
private fun Thermometer(state: UnitsUiState) {
    val accent = LocalAccent.current
    val celsius = state.value?.takeUnless { state.impossible }?.let { Units.convert(it, state.from, Units.unit("c")!!) } ?: 0.0
    val target = ((celsius + 30.0) / 80.0).coerceIn(0.0, 1.0).toFloat()
    val level by animateFloatAsState(target, spring(dampingRatio = 0.5f, stiffness = 120f), label = "mercury")
    val cold = Color(0xFF3B82F6)
    val hot = Color(0xFFEF4444)
    val mercury = lerp(cold, hot, level)
    val track = Palette.Stroke
    val landmark = when {
        celsius <= -18 -> "Congélateur"
        celsius <= 0 -> "L'eau gèle"
        celsius < 15 -> "Frais"
        celsius < 26 -> "Une pièce"
        celsius < 35 -> "Été"
        celsius < 42 -> "Corps / fièvre"
        celsius < 100 -> "Très chaud"
        else -> "Four ou ébullition"
    }
    Text(skinText("Thermomètre"), color = Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .weight(1f)
                .height(44.dp),
        ) {
            val bulb = size.height / 2
            val tubeH = size.height * 0.36f
            val left = bulb * 2
            val top = (size.height - tubeH) / 2
            drawRoundRect(track, Offset(left - bulb / 2, top), Size(size.width - left + bulb / 2, tubeH), CornerRadius(tubeH / 2))
            drawRoundRect(
                mercury,
                Offset(left - bulb / 2, top),
                Size((size.width - left + bulb / 2) * level.coerceAtLeast(0.04f), tubeH),
                CornerRadius(tubeH / 2),
            )
            drawCircle(mercury, radius = bulb * 0.9f, center = Offset(bulb, size.height / 2))
            // Ticks every 10 °C.
            for (i in 0..8) {
                val x = left - bulb / 2 + (size.width - left + bulb / 2) * i / 8f
                drawLine(track, Offset(x, top - 6f), Offset(x, top - 14f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("−30 °C", color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        AnimatedContent(targetState = landmark, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "landmark") {
            Text(skinText(it), color = accent.start, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        Text("50 °C", color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** A dial up to 200 km/h whose needle swings to the speed with a spring. */
@Composable
private fun Speedometer(state: UnitsUiState) {
    val accent = LocalAccent.current
    val kmh = state.value?.takeUnless { state.impossible }?.let { Units.convert(it, state.from, Units.unit("kmh")!!) } ?: 0.0
    val target = (kmh / 200.0).coerceIn(0.0, 1.0).toFloat()
    val needle by animateFloatAsState(target, spring(dampingRatio = 0.35f, stiffness = 90f), label = "needle")
    val track = Palette.Stroke
    val ink = Palette.TextPrimary
    Text(skinText("Compteur"), color = Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            val stroke = 14f
            val radius = minOf(size.width / 2, size.height) - stroke
            val center = Offset(size.width / 2, size.height - 4f)
            val box = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(track, 180f, 180f, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(accent.start, 180f, 180f * needle, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
            for (i in 0..10) {
                val a = PI + PI * i / 10
                val outer = radius - stroke
                val inner = outer - if (i % 5 == 0) 18f else 10f
                drawLine(
                    track,
                    Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                    Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            val a = PI + PI * needle
            val tip = radius - stroke * 2.4f
            drawLine(ink, center, Offset(center.x + (cos(a) * tip).toFloat(), center.y + (sin(a) * tip).toFloat()), strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(ink, 9f, center)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("0", color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            Units.formatWith(kmh, Units.unit("kmh")!!),
            color = accent.start,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("200 km/h", color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/**
 * How big one unit of each side is: "1 km" against "1 mi", the bigger one full width and the other in proportion
 * (log scale beyond ×20, so a tonne and a gram still both show).
 */
@Composable
private fun SizeBars(state: UnitsUiState) {
    val oneFromInTo = Units.convert(1.0, state.from, state.to, state.ingredient)
    val ratio = if (oneFromInTo.isFinite() && oneFromInTo > 0) oneFromInTo else 1.0
    // One "from" is ratio "to": the bigger of the two units takes the whole width.
    val (fromShare, toShare) = if (ratio >= 1) 1.0 to share(ratio) else share(1 / ratio) to 1.0
    Text(skinText("Qui est le plus grand ?"), color = Palette.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    SizeBar("1 ${state.from.symbol}", fromShare.toFloat(), primary = true)
    SizeBar("1 ${state.to.symbol}", toShare.toFloat(), primary = false)
    val bigger = if (ratio >= 1) state.from to state.to else state.to to state.from
    val times = if (ratio >= 1) ratio else 1 / ratio
    Text(
        if (abs(times - 1) < 1e-9) skinText("Les deux unités sont égales.")
        else skinText("1 ${bigger.first.symbol} = ${Units.format(times)} ${bigger.second.symbol}"),
        color = Palette.TextTertiary,
        fontSize = 12.sp,
    )
}

/** The width of the smaller bar when the bigger is [times] bigger. */
private fun share(times: Double): Double = when {
    times <= 1 -> 1.0
    times <= 20 -> 1 / times
    else -> (0.05 * (1 - ln(times / 20) / ln(1e9))).coerceAtLeast(0.015)
}

@Composable
private fun SizeBar(label: String, share: Float, primary: Boolean) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val width by animateFloatAsState(share, spring(dampingRatio = 0.6f, stiffness = 140f), label = "barWidth")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Palette.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (skin == Skin.TERMINAL) FontFamily.Monospace else null,
            maxLines = 1,
            modifier = Modifier.width(78.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).height(18.dp)) {
            val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 2.dp else 9.dp)
            Box(
                Modifier
                    .width(maxWidth * width.coerceIn(0.015f, 1f))
                    .height(18.dp)
                    .clip(shape)
                    .then(
                        when {
                            !primary -> Modifier.background(accent.start.copy(alpha = 0.35f))
                            skin == Skin.CLASSIC -> Modifier.background(accent.horizontal())
                            else -> Modifier.background(accent.start)
                        },
                    ),
            )
        }
    }
}

// MARK: - Table

/** The value in every unit of its kind; the unit chosen as "Vers" is lit, tapping a line makes it the "Vers". */
@Composable
internal fun AllUnits(state: UnitsUiState, value: Double, onTo: (MeasureUnit) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(vertical = 6.dp),
    ) {
        Units.table(value, state.from, state.ingredient)
            .filter { it.unit != state.from }
            .forEachIndexed { index, line ->
                val selected = line.unit == state.to
                val bg by animateColorAsState(if (selected) accent.tint(0.12f) else Color.Transparent, tween(220), label = "rowBg")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(index, key = state.quantity, baseDelay = 60)
                        .bounceClick(pressedScale = 0.98f) { onTo(line.unit) }
                        .background(bg)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        skinText(line.unit.name),
                        color = if (selected) accent.start else Palette.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Units.formatWith(line.value, line.unit),
                        color = Palette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
    }
}
