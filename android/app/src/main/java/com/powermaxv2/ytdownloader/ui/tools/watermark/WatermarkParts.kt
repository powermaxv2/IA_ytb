package com.powermaxv2.ytdownloader.ui.tools.watermark

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.watermark.Watermark
import com.powermaxv2.ytdownloader.core.watermark.WatermarkInk
import com.powermaxv2.ytdownloader.core.watermark.WatermarkStyle
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay

// Pieces of the Filigrane: the live preview where the copies of the text land one after the other from the middle,
// the sample document shown before a file is chosen, the ink swatches and the labelled sliders.

/**
 * The page ([image], or a sample identity card when null) with the watermark drawn on top exactly where the saved
 * file will have it ([Watermark.stamps]). When [label], the mode or the ink change, the copies land again from the
 * centre outwards, each one pressed down like a rubber stamp; with [loop] they keep landing and fading (idle demo).
 * [sweep] draws a light band going down the page (while the file is written).
 */
@Composable
internal fun WatermarkPreview(
    image: ImageBitmap?,
    aspect: Float,
    style: WatermarkStyle,
    label: String,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    sweep: Boolean = false,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(style.mode, style.ink, loop) {
        if (loop) {
            while (true) {
                reveal.snapTo(0f)
                reveal.animateTo(1f, tween(1500, easing = LinearEasing))
                delay(1600)
                reveal.animateTo(0f, tween(450))
                delay(250)
            }
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(950, easing = LinearEasing))
        }
    }
    // A new text lands again once the user stops typing for a moment (not at every letter).
    var landed by remember { mutableStateOf(label) }
    LaunchedEffect(label) {
        if (loop || label == landed) return@LaunchedEffect
        delay(650)
        landed = label
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(950, easing = LinearEasing))
    }
    val transition = rememberInfiniteTransition(label = "watermarkSweep")
    val band by transition.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing)), label = "sweepBand")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val radius = if (terminal) LocalTokens.current.cardRadius else 14.dp
    val ink = Color(style.ink.argb)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.55f, 1.9f))
            .clip(RoundedCornerShape(radius))
            .background(Color.White)
            .semantics { contentDescription = "Aperçu du filigrane : $label" },
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            SampleCard(Modifier.fillMaxSize())
        }
        Canvas(Modifier.fillMaxSize()) {
            val fontPx = Watermark.fontSize(size.width, size.height, style)
            val layout = measurer.measure(
                label,
                TextStyle(
                    color = ink,
                    fontSize = with(density) { fontPx.toSp() },
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    shadow = if (style.ink == WatermarkInk.WHITE) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, fontPx * 0.02f), fontPx * 0.08f) else null,
                ),
                softWrap = false,
                maxLines = 1,
            )
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val stamps = Watermark.stamps(size.width, size.height, w, fontPx, style)
            val n = stamps.size
            // Each copy lands over a quarter of the reveal, the next one a little later: a wave from the centre.
            val spread = 4f
            stamps.forEachIndexed { i, stamp ->
                val t = ((reveal.value * (n + spread) - i) / spread).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val press = 1f + 0.35f * (1f - t) * (1f - t)
                withTransform({
                    translate(stamp.x, stamp.y)
                    rotate(style.angle, pivot = Offset.Zero)
                    scale(press, press, pivot = Offset.Zero)
                }) {
                    drawText(layout, topLeft = Offset(-w / 2, -h / 2), alpha = style.opacity * t)
                }
            }
            if (sweep) sweepBand(band)
        }
    }
}

private fun DrawScope.sweepBand(at: Float) {
    val y = size.height * at
    val tall = size.height * 0.22f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
            startY = y - tall / 2,
            endY = y + tall / 2,
        ),
    )
}

/** A made-up identity card, drawn (nothing real): what the Filigrane is typically used on. */
@Composable
internal fun SampleCard(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFF4F1EA))) {
        val w = size.width
        val h = size.height
        val pad = w * 0.06f
        val line = Color(0xFF2B2B2B).copy(alpha = 0.16f)
        // Card on the page.
        val cardTop = h * 0.16f
        val cardH = h * 0.62f
        drawRoundRect(Color(0xFFE6EEF7), Offset(pad, cardTop), Size(w - pad * 2, cardH), CornerRadius(w * 0.04f))
        drawRoundRect(Color(0xFF9DB7D5), Offset(pad, cardTop), Size(w - pad * 2, cardH * 0.16f), CornerRadius(w * 0.04f))
        drawRect(Color(0xFF9DB7D5), Offset(pad, cardTop + cardH * 0.08f), Size(w - pad * 2, cardH * 0.08f))
        // Portrait.
        val photoW = w * 0.24f
        val photoTop = cardTop + cardH * 0.26f
        drawRoundRect(Color(0xFFC9D3DE), Offset(pad * 2, photoTop), Size(photoW, cardH * 0.6f), CornerRadius(w * 0.015f))
        drawCircle(Color(0xFF8FA1B5), radius = photoW * 0.22f, center = Offset(pad * 2 + photoW / 2, photoTop + cardH * 0.22f))
        drawRoundRect(
            Color(0xFF8FA1B5),
            Offset(pad * 2 + photoW * 0.18f, photoTop + cardH * 0.38f),
            Size(photoW * 0.64f, cardH * 0.22f),
            CornerRadius(photoW * 0.3f),
        )
        // Lines of text.
        val left = pad * 3 + photoW
        val widths = listOf(0.9f, 0.7f, 0.82f, 0.55f, 0.75f)
        widths.forEachIndexed { i, f ->
            val y = photoTop + cardH * 0.06f + i * cardH * 0.11f
            drawRoundRect(line, Offset(left, y), Size((w - pad * 2 - left) * f, cardH * 0.045f), CornerRadius(cardH * 0.03f))
        }
        // Lines under the card, like a scanned page.
        repeat(3) { i ->
            val y = cardTop + cardH + h * (0.06f + i * 0.045f)
            if (y < h - pad) drawRoundRect(line, Offset(pad, y), Size((w - pad * 2) * (0.95f - i * 0.2f), h * 0.018f), CornerRadius(h * 0.01f))
        }
    }
}

/** Five round swatches; the chosen one grows, rings and shows a tick. */
@Composable
internal fun InkSwatches(selected: WatermarkInk, onSelect: (WatermarkInk) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        WatermarkInk.entries.forEach { ink -> Swatch(ink, ink == selected) { onSelect(ink) } }
    }
}

@Composable
private fun Swatch(ink: WatermarkInk, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    val square = LocalSkin.current == Skin.TERMINAL
    val shape = if (square) RoundedCornerShape(LocalTokens.current.chipRadius) else CircleShape
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.7f)
            pop.animateTo(1.12f, spring(dampingRatio = 0.38f, stiffness = 480f))
        } else {
            pop.animateTo(1f, spring(stiffness = 600f))
        }
    }
    val color = Color(ink.argb)
    Box(
        Modifier
            .size(40.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .semantics { contentDescription = "Couleur ${ink.label}" + if (selected) ", choisie" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .size(30.dp)
                .clip(shape)
                .background(color)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else Palette.Stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (ink == WatermarkInk.WHITE) Color(0xFF111111) else Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** A setting with its name, its value on the right ("32 %") and a slider in the accent colour. */
@Composable
internal fun SettingSlider(
    name: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current.start
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (terminal) name.uppercase() else name,
                color = Palette.TextSecondary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                color = Palette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (terminal) LocalTokens.current.body else null,
            )
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Palette.TextPrimary.copy(alpha = 0.12f),
            ),
        )
    }
}

/** A progress bar filling with the accent, with a moving sheen. */
@Composable
internal fun StampProgress(progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    val transition = rememberInfiniteTransition(label = "stampProgress")
    val classic = LocalSkin.current == Skin.CLASSIC
    val shine by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "stampShine")
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(Palette.TextPrimary.copy(alpha = 0.08f)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val filled = size.width * progress.coerceIn(0.04f, 1f)
            val brush = if (classic) Brush.horizontalGradient(listOf(accent.start, accent.end), endX = filled) else Brush.horizontalGradient(listOf(accent.start, accent.start))
            drawRoundRect(brush, size = Size(filled, size.height), cornerRadius = CornerRadius(size.height / 2))
            val x = filled * shine
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), startX = x - 40f, endX = x + 40f),
                topLeft = Offset(0f, 0f),
                size = Size(filled, size.height),
            )
        }
    }
}
