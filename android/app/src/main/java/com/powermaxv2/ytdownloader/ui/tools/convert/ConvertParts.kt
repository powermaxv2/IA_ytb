package com.powermaxv2.ytdownloader.ui.tools.convert

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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.TonalButton
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.hub.nudgeRight
import com.powermaxv2.ytdownloader.ui.panel
import com.powermaxv2.ytdownloader.ui.pulsing
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperButton
import com.powermaxv2.ytdownloader.ui.skin.paper.paperCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.TermButton
import com.powermaxv2.ytdownloader.ui.skin.terminal.TermLabel
import com.powermaxv2.ytdownloader.ui.skin.terminal.blinking
import com.powermaxv2.ytdownloader.ui.skin.terminal.termPanel
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.roundToInt

// Building blocks of the Convertisseur, drawn natively in each skin: cards, labels, buttons, option chips,
// format tiles, the progress ring and the "MKV → MP4" badges.

// MARK: - Surfaces, labels, buttons

/** A card of the tool in the skin's style (outlined panel, white paper card, terminal box). */
@Composable
internal fun Modifier.toolCard(): Modifier = when (LocalSkin.current) {
    Skin.CLASSIC -> panel()
    Skin.PAPER -> paperCard()
    Skin.TERMINAL -> termPanel()
}

/** Small title above a block ("Convertir en", "Qualité"…). */
@Composable
internal fun BlockLabel(text: String, modifier: Modifier = Modifier) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> Text(
            text,
            modifier = modifier,
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Skin.PAPER -> Text(
            text.uppercase(),
            modifier = modifier,
            color = LocalAccent.current.start,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            maxLines = 1,
        )
        Skin.TERMINAL -> TermLabel("> ${text.uppercase()}", modifier)
    }
}

/** The main action of a step, full width. */
@Composable
internal fun MainButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val m = modifier.fillMaxWidth()
    when (LocalSkin.current) {
        Skin.CLASSIC -> PrimaryButton(text, m.height(64.dp), icon = icon, subtitle = subtitle, enabled = enabled, onClick = onClick)
        Skin.PAPER -> PaperButton(text, m, icon = icon, subtitle = subtitle, enabled = enabled, onClick = onClick)
        Skin.TERMINAL -> TermButton(text.uppercase(), m, enabled = enabled, big = true, subtitle = subtitle?.uppercase(), onClick = onClick)
    }
}

/** A secondary action. */
@Composable
internal fun SideButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    when (LocalSkin.current) {
        Skin.TERMINAL -> TermButton(text.uppercase(), modifier, solid = false, onClick = onClick)
        else -> TonalButton(text, icon, modifier, onClick = onClick)
    }
}

/** Icon of a kind of file. */
internal fun kindIcon(kind: FileKind?): ImageVector = when (kind) {
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileKind.MARKDOWN, FileKind.TEXT -> Icons.Rounded.Description
    FileKind.CSV -> Icons.Rounded.TableChart
    FileKind.JSON, FileKind.CODE -> Icons.Rounded.Code
    else -> Icons.Rounded.InsertDriveFile
}

/** Square holding the file's icon, in the skin's style; it bobs gently. */
@Composable
internal fun KindTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (LocalSkin.current == Skin.TERMINAL) tokens.cardRadius else size * 0.3f)
    val base = modifier
        .size(size)
        .clip(shape)
    val tile = when (LocalSkin.current) {
        Skin.CLASSIC -> base.background(accent.brush)
        Skin.PAPER -> base.background(accent.start)
        Skin.TERMINAL -> base.background(accent.start.copy(alpha = 0.12f)).border(1.dp, accent.start.copy(alpha = 0.6f), shape)
    }
    val tint = if (LocalSkin.current == Skin.TERMINAL) accent.start else Palette.OnAccent
    Box(tile, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// MARK: - Option chips

/** A row of choices; the chosen one is filled with the accent and pops with a spring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            OptionChip(label(option), option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(tokens.chipRadius)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.88f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        }
    }
    val fill by animateColorAsState(
        when {
            !selected -> Color.Transparent
            skin == Skin.CLASSIC -> accent.tint(0.22f)
            skin == Skin.PAPER -> Palette.TextPrimary
            else -> accent.start
        },
        tween(200),
        label = "chipFill",
    )
    val outline by animateColorAsState(
        when {
            selected && skin == Skin.PAPER -> Palette.TextPrimary
            selected -> accent.start.copy(alpha = 0.8f)
            else -> Palette.Stroke
        },
        tween(200),
        label = "chipOutline",
    )
    val ink = when {
        !selected -> Palette.TextSecondary
        skin == Skin.PAPER -> Color.White
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.TextPrimary
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .bounceClick(onClick = onClick)
            .height(if (skin == Skin.TERMINAL) 34.dp else 38.dp)
            .clip(shape)
            .background(if (skin == Skin.PAPER && !selected) Palette.Surface2 else Color.Transparent)
            .background(fill)
            .border(1.dp, outline, shape)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (skin == Skin.TERMINAL) text.uppercase() else text,
            color = ink,
            fontSize = if (skin == Skin.TERMINAL) 12.5.sp else 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// MARK: - Format tiles

/** A target format: big label, short detail; the chosen one fills / outlines with the accent and springs. */
@Composable
internal fun FormatTile(label: String, detail: String, selected: Boolean, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 480f))
        }
    }
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "tileCheck")
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.cardRadius else 18.dp)
    val fill by animateColorAsState(
        when {
            skin == Skin.PAPER && selected -> accent.start
            skin == Skin.PAPER -> Palette.Surface
            selected -> accent.start.copy(alpha = if (skin == Skin.TERMINAL) 0.10f else 0.16f)
            else -> Palette.Surface2
        },
        tween(220),
        label = "tileFill",
    )
    val outline by animateColorAsState(
        when {
            selected && skin == Skin.PAPER -> accent.start
            selected -> accent.start
            skin == Skin.PAPER -> Palette.Stroke
            else -> Palette.Stroke
        },
        tween(220),
        label = "tileOutline",
    )
    val titleColor = if (skin == Skin.PAPER && selected) Color.White else Palette.TextPrimary
    val detailColor = when {
        skin == Skin.PAPER && selected -> Color.White.copy(alpha = 0.85f)
        skin == Skin.TERMINAL -> Palette.TextTertiary
        else -> Palette.TextSecondary
    }
    Box(
        modifier
            .staggeredEntrance(index, key = label, baseDelay = 60)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .bounceClick(pressedScale = 0.93f, onClick = onClick)
            .clip(shape)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .defaultMinSize(minHeight = 74.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when (skin) {
                Skin.TERMINAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selected) "▶ " else "  ",
                        color = accent.start,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(label.uppercase(), color = if (selected) accent.start else titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                else -> Text(
                    label,
                    color = titleColor,
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = tokens.display,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                if (skin == Skin.TERMINAL) "# ${detail.lowercase()}" else detail,
                color = detailColor,
                fontSize = if (skin == Skin.TERMINAL) 10.5.sp else 11.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (skin != Skin.TERMINAL && check > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                    }
                    .clip(CircleShape)
                    .background(if (skin == Skin.PAPER) Color.White else accent.start),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (skin == Skin.PAPER) accent.start else Palette.OnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// MARK: - Progress

/** Percentage whose digits roll up as they change. */
@Composable
internal fun RollingPercent(value: Int, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, pad: Int = 1) {
    val text = value.coerceIn(0, 100).toString().padStart(pad, '0')
    val family = LocalTokens.current.display
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { index, char ->
            // Keyed by position from the right, so units stay units when 9 → 10.
            val position = text.length - index
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)))
                },
                label = "digit$position",
            ) { digit ->
                Text(digit.toString(), color = color, fontSize = fontSize, fontFamily = family, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
        Text(
            "%",
            color = color.copy(alpha = 0.7f),
            fontSize = fontSize * 0.5f,
            fontFamily = family,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
    }
}

/**
 * Progress ring ([progress] 0..1, negative = unknown: a spinning arc) with the rolling percentage inside.
 * Classic: gradient arc with a glowing head; Papier: thick ink track, flat accent arc.
 */
@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 196.dp) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "ring")
    val transition = rememberInfiniteTransition(label = "ringSpin")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ringSpinAngle")
    val breathe by transition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ringGlow")
    val track = Palette.TextPrimary.copy(alpha = if (skin == Skin.PAPER) 0.10f else 0.08f)
    val ink = Palette.TextPrimary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = (if (skin == Skin.PAPER) 18.dp else 14.dp).toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val brush = if (skin == Skin.PAPER) {
                Brush.linearGradient(listOf(accent.start, accent.start))
            } else {
                Brush.sweepGradient(listOf(accent.start, accent.end, accent.start), center)
            }
            val cap = if (skin == Skin.PAPER) StrokeCap.Butt else StrokeCap.Round
            if (indeterminate) {
                rotate(spin) { drawArc(brush, -90f, 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = cap)) }
            } else {
                val sweep = 360f * animated
                rotate(-90f) { drawArc(brush, 0f, sweep.coerceAtLeast(1f), false, topLeft, arcSize, style = Stroke(stroke, cap = cap)) }
                if (skin == Skin.CLASSIC && animated > 0.01f) {
                    // Glowing head at the end of the arc.
                    val angle = Math.toRadians((sweep - 90f).toDouble())
                    val r = arcSize.width / 2
                    val head = Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
                    drawCircle(accent.end.copy(alpha = breathe * 0.5f), radius = stroke * 1.1f, center = head)
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = stroke * 0.28f, center = head)
                }
                if (skin == Skin.PAPER) {
                    // Tick marks every 10 %, like a gauge printed on paper.
                    val r = arcSize.width / 2 + stroke / 2 + 4.dp.toPx()
                    for (i in 0 until 20) {
                        val a = Math.toRadians((i * 18f - 90f).toDouble())
                        val len = if (i % 5 == 0) 6.dp.toPx() else 3.dp.toPx()
                        val start = Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat())
                        val end = Offset(center.x + ((r + len) * kotlin.math.cos(a)).toFloat(), center.y + ((r + len) * kotlin.math.sin(a)).toFloat())
                        drawLine(ink.copy(alpha = 0.35f), start, end, strokeWidth = 1.5.dp.toPx())
                    }
                }
            }
        }
        if (indeterminate) {
            Text("…", color = Palette.TextSecondary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.pulsing())
        } else {
            RollingPercent((progress * 100).roundToInt(), Palette.TextPrimary, 50.sp)
        }
    }
}

/** Terminal progress: a block bar of [cells] characters, the percentage typed big above it. */
@Composable
internal fun TerminalProgress(progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val indeterminate = progress < 0f
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "termBar")
    val transition = rememberInfiniteTransition(label = "termSpin")
    val tick by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "termSpinTick")
    val spinner = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spinner[(tick * spinner.length).toInt().coerceIn(0, spinner.length - 1)].toString(),
                color = accent,
                fontSize = 30.sp,
                modifier = Modifier.padding(end = 10.dp),
            )
            if (indeterminate) {
                Text("···", color = Palette.TextPrimary, fontSize = 54.sp, fontFamily = LocalTokens.current.display)
            } else {
                RollingPercent((progress * 100).roundToInt(), Palette.TextPrimary, 58.sp, pad = 3)
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // As many cells as fit the width at 13sp mono (≈ 8dp each), between the brackets.
            val cells = ((maxWidth.value - 24f) / 8.2f).toInt().coerceIn(10, 60)
            val filled = if (indeterminate) 0 else (animated * cells).roundToInt()
            val sweep = (tick * cells).toInt()
            val bar = buildString {
                for (i in 0 until cells) {
                    append(
                        when {
                            indeterminate -> if (i in sweep..sweep + 3) '█' else '░'
                            i < filled -> '█'
                            else -> '░'
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("[", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(bar, color = accent, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Text("]", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "MKV → MP4": the source badge, an arrow nudging right, the target badge breathing. */
@Composable
internal fun FormatBadges(from: String, to: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormatBadge(from, target = false)
        when (skin) {
            Skin.TERMINAL -> Text("──▶", color = accent.start, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.nudgeRight(5.dp, 1400))
            else -> Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "vers",
                tint = if (skin == Skin.PAPER) Palette.TextPrimary else accent.end,
                modifier = Modifier
                    .size(24.dp)
                    .nudgeRight(5.dp, 1400),
            )
        }
        FormatBadge(to, target = true, modifier = Modifier.pulsing(0.95f, 1.06f, 1100))
    }
}

@Composable
private fun FormatBadge(text: String, target: Boolean, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.chipRadius else 12.dp)
    when (skin) {
        Skin.TERMINAL -> Text(
            "[ ${text.uppercase()} ]",
            color = if (target) Palette.Background else accent.start,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = modifier
                .clip(shape)
                .background(if (target) accent.start else Color.Transparent)
                .border(1.dp, accent.start, shape)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
        else -> {
            val background = when {
                target && skin == Skin.CLASSIC -> Modifier.background(accent.horizontal())
                target -> Modifier.background(accent.start)
                skin == Skin.PAPER -> Modifier.background(Palette.TextPrimary)
                else -> Modifier.background(Palette.Surface2).border(1.dp, Palette.Stroke, shape)
            }
            Text(
                text.uppercase(),
                color = if (target || skin == Skin.PAPER) Color.White else Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = tokens.display,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                modifier = modifier
                    .clip(shape)
                    .then(background)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

// MARK: - What Gulp does not convert

private data class Limit(val title: String, val why: String)

private val limits = listOf(
    Limit("Word, Excel, PowerPoint ⇄ PDF", "Il faudrait embarquer une suite bureautique entière, des centaines de Mo."),
    Limit("Fichiers protégés (DRM)", "Films et musiques loués ou achetés restent verrouillés : Gulp ne contourne pas les protections."),
    Limit("Photos RAW", "Leur développement demande un logiciel photo spécialisé, bien trop lourd pour Gulp."),
)

/** Collapsible, honest card: what the converter does not do, and why. */
@Composable
internal fun LimitsCard(modifier: Modifier = Modifier, initiallyOpen: Boolean = false, index: Int = 4) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val rotation by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "limitsChevron")
    Column(
        modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 120)
            .toolCard()
            .bounceClick(pressedScale = 0.98f) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = accent.start, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (skin == Skin.TERMINAL) "CE QUE GULP NE CONVERTIT PAS" else "Ce que Gulp ne convertit pas",
                color = Palette.TextPrimary,
                fontSize = if (skin == Skin.TERMINAL) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Replier" else "Déplier",
                tint = Palette.TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                limits.forEach { limit ->
                    Row {
                        Text(
                            if (skin == Skin.TERMINAL) "✗" else "×",
                            color = Palette.Danger,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp),
                        )
                        Column {
                            Text(limit.title, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(limit.why, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp)
                        }
                    }
                }
                Text(
                    "Gulp reste léger : il convertit ce que FFmpeg et Android, déjà présents dans l'app, savent faire.",
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

/** What goes in and what comes out, on the empty screen. */
@Composable
internal fun SupportedCard(modifier: Modifier = Modifier) {
    val rows = listOf(
        Triple(FileKind.VIDEO, "Vidéo", "MP4 · MKV · WebM · MOV · AVI · GIF · le son seul"),
        Triple(FileKind.AUDIO, "Son", "MP3 · M4A · Opus · OGG · FLAC · WAV"),
        Triple(FileKind.IMAGE, "Photo", "JPG · PNG · WebP · BMP · TIFF · PDF"),
        Triple(FileKind.PDF, "PDF", "Une image JPG ou PNG par page"),
        Triple(FileKind.MARKDOWN, "Markdown", "Page HTML"),
        Triple(FileKind.CSV, "CSV ⇄ JSON", "Tableaux et listes d'objets"),
    )
    val skin = LocalSkin.current
    Column(
        modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Ce que Gulp convertit")
        rows.forEachIndexed { index, (kind, title, targets) ->
            Row(
                Modifier.staggeredEntrance(index, baseDelay = 260),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KindTile(kindIcon(kind), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (skin == Skin.TERMINAL) title.uppercase() else title,
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(targets, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

/** Terminal accent caret used after a few labels. */
@Composable
internal fun Caret() {
    Text("▌", color = LocalAccent.current.start, fontSize = 13.sp, modifier = Modifier.blinking())
}

/** A tool card holding a column of blocks, 14dp apart. */
@Composable
internal fun CardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
