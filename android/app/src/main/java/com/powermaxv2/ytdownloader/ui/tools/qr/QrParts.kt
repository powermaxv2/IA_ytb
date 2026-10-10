package com.powermaxv2.ytdownloader.ui.tools.qr

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.qr.QrHistory
import com.powermaxv2.ytdownloader.core.qr.QrHistoryEntry
import com.powermaxv2.ytdownloader.core.qr.QrKind
import com.powermaxv2.ytdownloader.core.qr.QrMatrix
import com.powermaxv2.ytdownloader.core.qr.QrPayload
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlin.math.hypot

// Pieces of the QR code tool drawn in the three skins: the Scanner | Créer switch, the camera viewfinder with its
// sweeping laser, the code itself (modules rippling in from the centre), form fields and the result card.

internal fun QrPayload.icon(): ImageVector = when (this) {
    is QrPayload.Link -> Icons.Rounded.Link
    is QrPayload.Wifi -> Icons.Rounded.Wifi
    is QrPayload.Contact -> Icons.Rounded.Person
    is QrPayload.Email -> Icons.Rounded.Email
    is QrPayload.Phone -> Icons.Rounded.Phone
    is QrPayload.Sms -> Icons.Rounded.Sms
    is QrPayload.Geo -> Icons.Rounded.Place
    is QrPayload.Product -> Icons.Rounded.ViewWeek
    is QrPayload.Text -> Icons.Rounded.TextFields
}

internal fun QrKind.icon(): ImageVector = when (this) {
    QrKind.LINK -> Icons.Rounded.Link
    QrKind.TEXT -> Icons.Rounded.TextFields
    QrKind.WIFI -> Icons.Rounded.Wifi
    QrKind.CONTACT -> Icons.Rounded.Person
}

@Composable
internal fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

// MARK: - Scanner | Créer

/** Two-way switch "Scanner | Créer": the thumb slides under the chosen side with a spring. */
@Composable
internal fun TabSwitch(create: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val slide by animateFloatAsState(if (create) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f), label = "qrTab")
    val radius: Dp = if (skin == Skin.TERMINAL) tokens.chipRadius else 50.dp
    val shape = RoundedCornerShape(radius)
    val paperThumb = Palette.Surface
    Box(
        modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 120)
            .height(50.dp)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.width / 2
            val brush = when (skin) {
                Skin.CLASSIC -> Brush.horizontalGradient(listOf(accent.start, accent.end), startX = slide * half, endX = slide * half + half)
                Skin.PAPER -> SolidColor(paperThumb)
                Skin.TERMINAL -> SolidColor(accent.start)
            }
            drawRoundRect(brush, topLeft = Offset(slide * half, 0f), size = Size(half, size.height), cornerRadius = CornerRadius(radius.toPx()))
        }
        Row(Modifier.fillMaxSize()) {
            listOf(false to "Scanner", true to "Créer").forEach { (value, label) ->
                val on = value == create
                val ink by animateColorAsState(
                    when {
                        !on -> Palette.TextSecondary
                        skin == Skin.CLASSIC -> Palette.OnAccent
                        skin == Skin.TERMINAL -> Palette.Background
                        else -> Palette.TextPrimary
                    },
                    tween(200),
                    label = "qrTabInk",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .bounceClick(pressedScale = 0.95f) { onChange(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        skinText(label),
                        color = ink,
                        fontSize = if (skin == Skin.TERMINAL) 12.5.sp else 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// MARK: - Viewfinder

/**
 * A dark window holding [scene] (the live camera, or a still picture) with the four viewfinder corners breathing,
 * a laser sweeping up and down with its glow, an optional torch button and a caption at the bottom.
 */
@Composable
internal fun Viewfinder(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
    torch: Boolean? = null,
    onTorch: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    scene: @Composable BoxScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "laser")
    val breathe by transition.animateFloat(1f, 0.93f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "corners")
    val radius = if (skin == Skin.TERMINAL) tokens.cardRadius else tokens.cardRadius + 2.dp
    val shape = RoundedCornerShape(radius)
    val frame = 0.56f
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.copy(alpha = 0.6f), shape) else Modifier)
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF4A3B31), Color(0xFF16110E))))
            .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.97f, onClick = onClick) else Modifier),
    ) {
        scene()
        // Darken outside the frame a little, so the eye goes to the centre.
        Canvas(Modifier.fillMaxSize()) {
            val side = size.minDimension * frame * breathe
            val left = (size.width - side) / 2
            val top = (size.height - side) / 2 - size.height * 0.06f
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            val arm = side * 0.22f
            val w = (if (skin == Skin.TERMINAL) 3.dp else 5.dp).toPx()
            val r = if (skin == Skin.TERMINAL) 0f else arm * 0.55f
            corners(left, top, side, arm, w, r, accent)
            // Laser line with a soft glow, sweeping inside the frame.
            val y = top + side * (0.08f + 0.84f * sweep)
            val glow = 22.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent), startY = y - glow, endY = y + glow),
                topLeft = Offset(left + w, y - glow),
                size = Size(side - 2 * w, glow * 2),
            )
            drawLine(accent, Offset(left + w * 2, y), Offset(left + side - w * 2, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
        torch?.let { on ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Color.Black.copy(alpha = 0.45f))
                    .bounceClick(pressedScale = 0.85f, onClick = onTorch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = if (on) "Éteindre la lampe" else "Allumer la lampe",
                    tint = if (on) Palette.OnAccent else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                skinText(title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = tokens.display.takeIf { skin != Skin.TERMINAL },
                textAlign = TextAlign.Center,
                letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.sp,
            )
            hint?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 12.5.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun DrawScope.corners(left: Float, top: Float, side: Float, arm: Float, w: Float, r: Float, color: Color) {
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val right = left + side
    val bottom = top + side
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(x, y + dy * arm)
            if (r > 0f) {
                lineTo(x, y + dy * r)
                quadraticBezierTo(x, y, x + dx * r, y)
            } else {
                lineTo(x, y)
            }
            lineTo(x + dx * arm, y)
        }
        drawPath(path, color, style = stroke)
    }
    corner(left, top, 1f, 1f)
    corner(right, top, -1f, 1f)
    corner(left, bottom, 1f, -1f)
    corner(right, bottom, -1f, -1f)
}

/** Still scene for the viewfinder (start screen, screenshots): a tilted white card holding a code, slowly swaying. */
@Composable
internal fun BoxScope.SampleScene(matrix: QrMatrix?, caption: String, compact: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "sampleScene")
    val sway by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(bottom = if (compact) 76.dp else 40.dp)
            .graphicsLayer {
                rotationZ = -6f + sway * 1.5f
                rotationX = 10f
                translationY = sway * 4.dp.toPx()
            }
            .width(if (compact) 124.dp else 168.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEFE9E0))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        matrix?.let { QrPicture(it, Modifier.fillMaxWidth(), framed = false, animate = false, ink = Color(0xFF222222), paper = Color(0xFFEFE9E0)) }
        Spacer(Modifier.height(6.dp))
        Text(caption, color = Color(0xFF444444), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - The code

/**
 * [matrix] drawn as on the saved PNG (rounded eyes, Gulp logo in the middle when the code is strong enough), on a
 * white card when [framed]. With [animate] the modules ripple in from the centre and the logo pops with a spring
 * each time the code changes.
 */
@Composable
internal fun QrPicture(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    animate: Boolean = true,
    ink: Color = Color(0xFF111111),
    paper: Color = Color.White,
) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(matrix) {
        if (animate) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
    }
    val logo = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(matrix) {
        if (animate) {
            logo.snapTo(0f)
            logo.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f))
        }
    }
    val frameShape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 22.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .then(
                if (framed) {
                    Modifier
                        .then(if (skin == Skin.TERMINAL) Modifier.border(1.dp, accent.start, frameShape) else Modifier)
                        .clip(frameShape)
                        .background(paper)
                        .padding(14.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = matrix.size
            val cell = size.minDimension / n
            val hole = matrix.logoSpan()
            val center = n / 2f
            val maxDist = hypot(center, center)
            val p = reveal.value
            for (y in 0 until n) for (x in 0 until n) {
                if (!matrix[x, y] || matrix.isFinder(x, y)) continue
                if (hole != null && x in hole && y in hole) continue
                // Ripple: each module grows once the wave (from the centre) reaches it.
                val d = hypot(x + 0.5f - center, y + 0.5f - center) / maxDist
                val local = ((p * 1.4f - d) / 0.4f).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val s = cell * (0.35f + 0.65f * local)
                val inset = (cell - s) / 2
                drawRect(ink.copy(alpha = local), topLeft = Offset(x * cell + inset, y * cell + inset), size = Size(s + 0.6f, s + 0.6f))
            }
            for ((fx, fy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
                val eye = (p * 2.2f).coerceIn(0f, 1f)
                val l = fx * cell
                val t = fy * cell
                val c = Offset(l + 3.5f * cell, t + 3.5f * cell)
                rotate((1f - eye) * -90f, c) {
                    drawRoundRect(ink, Offset(l + cell / 2, t + cell / 2), Size(6 * cell, 6 * cell), CornerRadius(cell * 1.7f), style = Stroke(cell))
                    val pupil = 3 * cell * eye
                    drawRoundRect(ink, Offset(c.x - pupil / 2, c.y - pupil / 2), Size(pupil, pupil), CornerRadius(cell))
                }
            }
            hole?.let { span ->
                val from = span.first * cell
                val to = (span.last + 1) * cell
                val pad = (to - from) * 0.08f
                val s = (to - from - 2 * pad) * logo.value
                if (s > 1f) {
                    val c = (from + to) / 2
                    val l = c - s / 2
                    drawRoundRect(
                        Brush.linearGradient(listOf(Palette.Accent, Palette.Accent2), Offset(l, l), Offset(l + s, l + s)),
                        Offset(l, l),
                        Size(s, s),
                        CornerRadius(s * 0.26f),
                    )
                    val stroke = Stroke(s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(Path().apply {
                        moveTo(c - s * 0.2f, c - s * 0.18f)
                        lineTo(c, c + s * 0.04f)
                        lineTo(c + s * 0.2f, c - s * 0.18f)
                    }, Color.White, style = stroke)
                    drawPath(Path().apply {
                        moveTo(c - s * 0.22f, c + s * 0.15f)
                        quadraticBezierTo(c, c + s * 0.34f, c + s * 0.22f, c + s * 0.15f)
                    }, Color.White, style = Stroke(s * 0.09f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

// MARK: - Form

/** A labelled text field in the skin's style; [secret] hides the text behind dots with an eye to show it. */
@Composable
internal fun QrField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String = "",
    maxLength: Int = 300,
) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current.start
    var shown by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tokens.fieldRadius)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, if (skin == Skin.PAPER) Palette.TextPrimary.copy(alpha = 0.1f) else Palette.Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (skin == Skin.TERMINAL) "> ${label.uppercase()}" else label,
                color = Palette.TextTertiary,
                fontSize = 11.5.sp,
                letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = Palette.TextTertiary, fontSize = 16.sp, maxLines = if (singleLine) 1 else 4)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onChange(it.take(maxLength)) },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 6,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 16.sp, fontFamily = tokens.body),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret && !shown) KeyboardType.Password else keyboard),
                    visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (secret) {
            Box(
                Modifier
                    .size(36.dp)
                    .bounceClick(pressedScale = 0.85f) { shown = !shown },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (shown) "Masquer" else "Afficher",
                    tint = Palette.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** "Lien · Texte · Wi-Fi · Contact": icon chips, the chosen one filled with the accent and popping. */
@Composable
internal fun KindChips(selected: QrKind, onSelect: (QrKind) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QrKind.entries.forEach { kind -> KindChip(kind, kind == selected, Modifier.weight(1f)) { onSelect(kind) } }
    }
}

@Composable
private fun KindChip(kind: QrKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.chipRadius else 18.dp)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    val ink = when {
        !selected -> Palette.TextSecondary
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.OnAccent
    }
    val base = modifier
        .graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        }
        .bounceClick(onClick = onClick)
        .height(64.dp)
        .clip(shape)
    val filled = when {
        !selected -> base.background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2).border(1.dp, Palette.Stroke, shape)
        skin == Skin.CLASSIC -> base.background(accent.brush)
        else -> base.background(accent.start)
    }
    Column(filled, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(kind.icon(), contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            skinText(kind.label),
            color = ink,
            fontSize = if (skin == Skin.TERMINAL) 11.sp else 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// MARK: - Result

/** A line of the result: small label, value, and a copy button when [onCopy] is given. */
@Composable
internal fun ValueRow(label: String, value: String, index: Int, onCopy: (() -> Unit)?) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.fieldRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 220)
            .clip(shape)
            .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .then(if (onCopy != null) Modifier.bounceClick(pressedScale = 0.97f, onClick = onCopy) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
            Text(value, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (onCopy != null) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier $label", tint = LocalAccent.current.start, modifier = Modifier.size(20.dp))
        }
    }
}

/** The kind badge: "Réseau Wi-Fi", popping in with a spring. */
@Composable
internal fun KindBadge(payload: QrPayload) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(payload.raw) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
    val color = if (skin == Skin.CLASSIC) Color(0xFFFF8A5F) else accent.start
    Row(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 30.dp))
            .background(accent.start.copy(alpha = if (skin == Skin.PAPER) 0.1f else 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(payload.icon(), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            skinText(payload.kindLabel),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (skin == Skin.TERMINAL) 1.sp else 0.sp,
        )
    }
}

/** "Derniers scans": the last codes, newest first; a tap shows one again. */
@Composable
internal fun HistoryCard(entries: List<QrHistoryEntry>, now: Long, onOpen: (QrHistoryEntry) -> Unit, onClear: () -> Unit, index: Int) {
    if (entries.isEmpty()) return
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockLabel("Derniers scans", Modifier.weight(1f))
            Text(
                skinText("Effacer"),
                color = LocalAccent.current.start,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bounceClick(pressedScale = 0.9f, onClick = onClear),
            )
        }
        Spacer(Modifier.height(6.dp))
        entries.take(5).forEachIndexed { i, entry ->
            val payload = entry.payload
            Row(
                Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(i, key = entry.text, baseDelay = 220)
                    .bounceClick(pressedScale = 0.97f) { onOpen(entry) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KindTile(payload.icon(), size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (terminal) payload.title.lowercase() else payload.title,
                    color = Palette.TextPrimary,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(QrHistory.ago(entry.time, now), color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** A line of the "Gulp sait lire" card. */
@Composable
internal fun ReadsRow(icon: ImageVector, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(icon, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(if (terminal) detail.lowercase() else detail, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 1, textAlign = TextAlign.End)
    }
}

/** Hint under a block. */
@Composable
internal fun QrHint(text: String, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) "# $text" else text,
        color = Palette.TextTertiary,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = modifier,
    )
}
