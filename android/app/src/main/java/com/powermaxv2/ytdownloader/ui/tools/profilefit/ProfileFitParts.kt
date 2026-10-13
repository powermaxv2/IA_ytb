package com.powermaxv2.ytdownloader.ui.tools.profilefit

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.profilefit.Backdrop
import com.powermaxv2.ytdownloader.core.profilefit.FitPhoto
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Pieces of "Photo de profil entière": the round stage (WhatsApp's crop screen, the photo inside the circle,
// a dashed ring turning around it), the WhatsApp-alone / with-Gulp comparison, the backdrop swatches, and the
// looping demo of the empty screen.

@Composable
internal fun rememberImage(bitmap: Bitmap?): ImageBitmap? = remember(bitmap) { bitmap?.asImageBitmap() }

/**
 * The result as WhatsApp will show it: the whole square dimmed, the round lit, and a dashed ring that turns.
 * Each new [preview] pops in with a spring; [busy] makes the ring run faster and the photo breathe.
 */
@Composable
internal fun ProfileStage(preview: Bitmap?, modifier: Modifier = Modifier, busy: Boolean = false, maxSide: Dp = 300.dp) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val image = rememberImage(preview)
    val pop = remember { Animatable(0.86f) }
    LaunchedEffect(preview) {
        pop.snapTo(if (pop.value > 0.95f) 0.95f else pop.value)
        pop.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 380f))
    }
    val transition = rememberInfiniteTransition(label = "profileStage")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 1400 else 9000, easing = LinearEasing)), label = "ringSpin")
    val breathe by transition.animateFloat(
        1f,
        if (busy) 0.96f else 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxSide)
        val corner = if (terminal) LocalTokens.current.cardRadius else 28.dp
        Box(
            Modifier
                .size(side)
                .clip(RoundedCornerShape(corner))
                .background(Color(0xFF0B0F12)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(image, animationSpec = tween(260), label = "profilePreview") { img ->
                if (img != null) {
                    Box(Modifier.fillMaxSize()) {
                        // Outside the round: what the file holds but WhatsApp never shows.
                        Image(img, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.32f }, contentScale = ContentScale.Crop)
                        Image(
                            img,
                            contentDescription = "Aperçu de la photo de profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .graphicsLayer {
                                    scaleX = pop.value * breathe
                                    scaleY = pop.value * breathe
                                }
                                .clip(CircleShape),
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val w = 2.dp.toPx()
                rotate(spin) {
                    drawCircle(
                        brush = if (terminal) Brush.linearGradient(listOf(accent.start, accent.start)) else Brush.sweepGradient(listOf(accent.start, accent.end, accent.start)),
                        radius = size.minDimension / 2 - w,
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                    )
                }
            }
        }
    }
}

/** "WhatsApp seul" (its centred crop, with what it hides) next to "Avec Gulp" (the whole photo). */
@Composable
internal fun FitCompare(photo: FitPhoto, preview: Bitmap?, hidden: Float, shown: Float, modifier: Modifier = Modifier) {
    val source = rememberImage(photo.bitmap)
    val after = rememberImage(preview)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        CompareCircle("WhatsApp seul", source, -hidden, warn = true, index = 0)
        CompareCircle("Avec Gulp", after, shown, warn = false, index = 1)
    }
}

@Composable
private fun CompareCircle(label: String, image: ImageBitmap?, share: Float, warn: Boolean, index: Int) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val target = (abs(share) * 100f).roundToInt()
    val count by animateIntAsState(target, tween(900, delayMillis = 200 + index * 150, easing = FastOutSlowInEasing), label = "compareCount")
    val color = if (warn) Palette.Danger else accent.start
    Column(Modifier.staggeredEntrance(index, baseDelay = 120), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Palette.TextPrimary.copy(alpha = 0.08f))
                    .border(2.dp, color.copy(alpha = 0.8f), CircleShape),
            ) {
                image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Text(
                if (warn) "−$count %" else "$count %",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
                    .background(color)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (terminal) label.uppercase() else label,
            color = Palette.TextSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Four round swatches (the blurred photo, its colour, white, black); the chosen one wears a springy ring. */
@Composable
internal fun BackdropSwatches(photo: FitPhoto?, selected: Backdrop, onSelect: (Backdrop) -> Unit, modifier: Modifier = Modifier) {
    val blurred = rememberImage(photo?.backdrops?.blurred)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Backdrop.entries.forEachIndexed { index, backdrop ->
            Swatch(backdrop, backdrop == selected, index, blurred, photo?.backdrops?.color?.let { Color(it) }) { onSelect(backdrop) }
        }
    }
}

@Composable
private fun Swatch(backdrop: Backdrop, chosen: Boolean, index: Int, blurred: ImageBitmap?, color: Color?, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val ring by animateFloatAsState(if (chosen) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "swatchRing")
    Column(
        Modifier
            .staggeredEntrance(index, baseDelay = 80)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = chosen
                contentDescription = "Fond ${backdrop.label}"
            }
            .bounceClick(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = 0.8f + 0.2f * ring
                        scaleY = 0.8f + 0.2f * ring
                        alpha = ring
                    }
                    .border(2.5.dp, accent.start, CircleShape),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (backdrop) {
                            Backdrop.BLUR -> Color(0xFF6B7A88)
                            Backdrop.COLOR -> color ?: Color(0xFF4E6A5A)
                            Backdrop.WHITE -> Color.White
                            Backdrop.BLACK -> Color.Black
                        },
                    )
                    .border(1.dp, Palette.Stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (backdrop == Backdrop.BLUR && blurred != null) {
                    Image(blurred, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (chosen) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (backdrop == Backdrop.WHITE) Color.Black else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = ring
                                scaleY = ring
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (terminal) backdrop.label.uppercase() else backdrop.label,
            color = if (chosen) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The empty screen's loop: a group photo in WhatsApp's round. First the round crops it (the friends on the sides
 * fade, a red "coupé"), then the photo shrinks into the round on a blurred backdrop and everyone is back.
 */
@Composable
internal fun FitDemo(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val danger = Palette.Danger
    val transition = rememberInfiniteTransition(label = "fitDemo")
    // 0 = WhatsApp's crop, 1 = fitted by Gulp; holds on each, springs between.
    val t by transition.animateFloat(
        0f,
        0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0
                0f at 1300
                1f at 2000 using FastOutSlowInEasing
                1f at 3500
                0f at 4200 using FastOutSlowInEasing
            },
        ),
        label = "fitT",
    )
    val people = listOf(Color(0xFFFFB74D), Color(0xFF4FC3F7), Color(0xFFE57373), Color(0xFF81C784), Color(0xFFBA68C8))
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f),
        ) {
            val d = size.height * 0.92f
            val c = Offset(size.width / 2, size.height / 2)
            val r = d / 2
            // Photo 16:10, from "cover the round" (crop) to "diagonal = diameter" (whole).
            val pw = 16f
            val ph = 10f
            val crop = d / min(pw, ph)
            val fit = d * 0.94f / sqrt(pw * pw + ph * ph)
            val s = crop + (fit - crop) * t
            val photo = Rect(Offset(c.x - pw * s / 2, c.y - ph * s / 2), Size(pw * s, ph * s))
            val round = Path().apply { addOval(Rect(c, r)) }

            // Outside the round: the photo, faded.
            drawGroup(photo, people, alpha = 0.22f)
            clipPath(round) {
                // Gulp's blurred backdrop fades in behind the shrinking photo.
                drawRect(Brush.radialGradient(listOf(Color(0xFF8FA6B8), Color(0xFF3A4A58)), c, r), alpha = t)
                drawGroup(photo, people, alpha = 1f)
            }
            drawCircle(if (t < 0.5f) danger else accent.start, r, c, style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoTag("WhatsApp coupe", danger, active = t < 0.5f)
            DemoTag("Gulp garde tout", accent.start, active = t >= 0.5f)
        }
    }
}

/** Five friends in a row, the outer ones near the photo's sides (they are the ones WhatsApp drops). */
private fun DrawScope.drawGroup(photo: Rect, colors: List<Color>, alpha: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9AD0F5), Color(0xFFF6D7A7)), photo.top, photo.bottom), photo.topLeft, photo.size, alpha = alpha)
    drawRect(Color(0xFF7CB342), Offset(photo.left, photo.top + photo.height * 0.78f), Size(photo.width, photo.height * 0.22f), alpha = alpha)
    val n = colors.size
    colors.forEachIndexed { i, color ->
        val x = photo.left + photo.width * (0.1f + 0.8f * i / (n - 1))
        val head = photo.height * 0.11f
        val y = photo.top + photo.height * (if (i % 2 == 0) 0.42f else 0.46f)
        drawCircle(color, head * 1.7f, Offset(x, y + head * 2.6f), alpha = alpha)
        drawCircle(Color(0xFFFFE0C2), head, Offset(x, y), alpha = alpha)
    }
}

@Composable
private fun DemoTag(text: String, color: Color, active: Boolean) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val on by animateFloatAsState(if (active) 1f else 0f, tween(260), label = "demoTag")
    Text(
        if (terminal) text.uppercase() else text,
        color = lerpColor(Palette.TextTertiary, Color.White, on),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .graphicsLayer {
                scaleX = 0.94f + 0.06f * on
                scaleY = 0.94f + 0.06f * on
            }
            .clip(RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .background(color.copy(alpha = on))
            .border(1.dp, color.copy(alpha = 0.5f + 0.5f * on), RoundedCornerShape(if (terminal) 2.dp else 50.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
