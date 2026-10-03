package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.AnimatedPlatformGlyph
import com.powermaxv2.ytdownloader.ui.LinkPane
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.Phase
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.RollingNumber
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.brand.LocalGulpPulse
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.classic.SHARE_TIP
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFeatures
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFootnote
import com.powermaxv2.ytdownloader.ui.skin.classic.guideSteps
import com.powermaxv2.ytdownloader.ui.skin.classic.linkPlaceholder
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick

// "Papier" skin: Gulp's editorial look. White cards on cream paper, ink of the logo's mouth, one flat accent
// per tab, Bricolage Grotesque.

/** Text colour on the ink (selected segment, badges): the white of the cards. */
private val PaperInkText = Color.White

/** Paper-coloured fill on the ink bar (selected tab). */
private val PaperCream = GulpColors.Cream

// MARK: - Surfaces

/** White card with a very soft, warm (brown) shadow and no outline. */
@Composable
internal fun Modifier.paperCard(shape: Shape = RoundedCornerShape(LocalTokens.current.cardRadius)): Modifier {
    val warm = LocalTokens.current.cardShadowColor
    return this
        .shadow(6.dp, shape, ambientColor = warm, spotColor = warm)
        .clip(shape)
        .background(Palette.Surface)
}

/** Small ink square button (secondary actions next to the accent button), cream icon. */
@Composable
internal fun PaperInkButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = Palette.TextPrimary
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .bounceClick(pressedScale = 0.9f, onClick = onClick)
            .size(60.dp)
            .shadow(8.dp, shape, ambientColor = ink, spotColor = ink)
            .clip(shape)
            .background(ink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = PaperCream, modifier = Modifier.size(24.dp))
    }
}

/** Big flat accent button: bold label, coloured shadow, press scale; [subtitle] repeats the chosen options. */
@Composable
internal fun PaperButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current.start
    val onAccent = Palette.OnAccent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "paperPress")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "paperAlpha")
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(LocalTokens.current.buttonRadius)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = accent.copy(alpha = 0.7f), spotColor = accent.copy(alpha = 0.7f))
            .clip(shape)
            .background(accent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
            label = "paperButton",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = onAccent, strokeWidth = 2.5.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        icon?.let { Icon(it, contentDescription = null, tint = onAccent, modifier = Modifier.size(22.dp)) }
                        Text(text, color = onAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    subtitle?.let {
                        Text(it, color = onAccent.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// MARK: - Background

/**
 * Cream paper with a faint Gulp watermark (a big drop above a big smile, the logo's two strokes) that drifts
 * slowly in the lower right corner. Barely visible: the content stays on top.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier) {
    val ink = Palette.TextPrimary.copy(alpha = 0.035f)
    val transition = rememberInfiniteTransition(label = "paperWatermark")
    val drift by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "paperDrift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * 0.9f
            val u = side / 512f
            val left = size.width - side * 0.72f
            val top = size.height - side * 1.05f
            // The drop floats a little more than the smile, as if about to be swallowed.
            translate(left, top + drift * 14.dp.toPx()) {
                val drop = Path().apply {
                    moveTo(184f * u, 158f * u)
                    lineTo(328f * u, 158f * u)
                    lineTo(256f * u, 284f * u)
                    close()
                }
                drawPath(drop, ink)
                drawPath(drop, ink, style = Stroke(40f * u, join = StrokeJoin.Round))
            }
            translate(left, top - drift * 4.dp.toPx()) {
                val smile = Path().apply {
                    moveTo(158f * u, 346f * u)
                    quadraticTo(256f * u, 418f * u, 354f * u, 346f * u)
                }
                drawPath(smile, ink, style = Stroke(40f * u, cap = StrokeCap.Round))
            }
        }
    }
}

// MARK: - Header

/** Short tab names: the title is set very large. */
private fun paperTitle(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Instagram"
    Platform.OTHER -> "Autres"
    else -> "YouTube"
}

/**
 * The logo's smile in the accent, under the title: it draws itself from left to right on each tab ([grow])
 * and deepens for a moment when a download is queued ([swallow], like the mark's mouth).
 */
@Composable
private fun PaperSmileUnderline(grow: () -> Float, swallow: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val stroke = 4.dp.toPx()
        val half = stroke / 2
        val depth = (size.height - stroke) * (1f + 0.6f * swallow())
        val smile = Path().apply {
            moveTo(half, half)
            // A quadratic curve reaches half of its control point's depth.
            quadraticTo(size.width / 2, half + depth * 2f, size.width - half, half)
        }
        clipRect(right = size.width * grow().coerceIn(0f, 1f)) {
            drawPath(smile, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Gulp lockup (animated mark + "gulp"), huge title on the accent smile (draws itself on each tab), settings square on the right. */
@Composable
fun PaperHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit) {
    val accent = LocalAccent.current.start
    val grow = remember(platform) { Animatable(0f) }
    LaunchedEffect(platform) { grow.animateTo(1f, tween(620, delayMillis = 120, easing = FastOutSlowInEasing)) }
    val rise = remember(platform) { Animatable(0f) }
    LaunchedEffect(platform) { rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f)) }
    // The smile deepens with each queued download, in time with the mark's gulp.
    val pulse = LocalGulpPulse.current
    val swallow = remember { Animatable(0f) }
    var firstPulse by remember { mutableStateOf(true) }
    LaunchedEffect(pulse) {
        if (firstPulse) {
            firstPulse = false
        } else {
            swallow.snapTo(0f)
            swallow.animateTo(1f, tween(320, delayMillis = 260, easing = FastOutSlowInEasing))
            swallow.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            GulpLockup(markSize = 26.dp, textColor = Palette.TextPrimary, fontSize = 21.sp)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.width(IntrinsicSize.Max)) {
                Text(
                    paperTitle(platform),
                    color = Palette.TextPrimary,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.3f + 0.7f * rise.value.coerceIn(0f, 1f)
                        translationY = (1f - rise.value) * 8.dp.toPx()
                    },
                )
                PaperSmileUnderline({ grow.value }, { swallow.value }, accent)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .padding(top = 8.dp)
                .bounceClick(pressedScale = 0.9f, onClick = onSettings)
                .size(56.dp)
                .paperCard(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Réglages", tint = Palette.TextPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

// MARK: - Bottom navigation

private val paperTabs = listOf(
    Tab.YOUTUBE to "YouTube",
    Tab.INSTAGRAM to "Instagram",
    Tab.OTHER to "Autres",
    Tab.DOWNLOADS to "Fichiers",
)

/** Gulp's drop (a small rounded play triangle pointing down), centred on [center], [side] wide. */
private fun DrawScope.drawPaperDrop(center: Offset, side: Float, color: Color) {
    val h = side * 0.875f
    val drop = Path().apply {
        moveTo(center.x - side / 2, center.y - h / 2)
        lineTo(center.x + side / 2, center.y - h / 2)
        lineTo(center.x, center.y + h / 2)
        close()
    }
    drawPath(drop, color)
    drawPath(drop, color, style = Stroke(side * 0.28f, join = StrokeJoin.Round))
}

/**
 * Ink pill of labels; a cream pill slides under the selected one (hidden while Réglages is open). Each queued
 * download is "swallowed" by Fichiers: a drop falls into the label, then the label and the badge bounce.
 */
@Composable
fun PaperBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val bar = Palette.NavBar
    val shape = RoundedCornerShape(28.dp)
    val index = paperTabs.indexOfFirst { it.first == tab }
    val bump = remember { Animatable(0f) }
    val fall = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            fall.snapTo(0f)
            fall.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            bump.snapTo(1f)
            bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
        }
    }
    BoxWithConstraints(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(18.dp, shape, ambientColor = bar, spotColor = bar)
            .clip(shape)
            .background(bar)
            .padding(6.dp),
    ) {
        val segment = maxWidth / paperTabs.size
        val offset by animateDpAsState(segment * index.coerceAtLeast(0), spring(dampingRatio = 0.72f, stiffness = 380f), label = "paperPill")
        val pillAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, label = "paperPillAlpha")
        // The pill stretches a little while it travels, then settles.
        val stretch = remember { Animatable(0f) }
        LaunchedEffect(index) {
            stretch.snapTo(0f)
            stretch.animateTo(1f, tween(140))
            stretch.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = pillAlpha
                    scaleX = 1f + 0.08f * stretch.value
                    scaleY = 1f - 0.06f * stretch.value
                }
                .clip(RoundedCornerShape(22.dp))
                .background(PaperCream),
        )
        Row(Modifier.fillMaxSize()) {
            paperTabs.forEach { (item, label) ->
                val selected = item == tab
                val color by animateColorAsState(if (selected) bar else PaperCream.copy(alpha = 0.62f), label = "paperNavText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            view.tick(haptics)
                            onSelect(item)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item == Tab.DOWNLOADS) {
                        // The drop falls from the top of the bar into the label and is swallowed (shrinks, fades).
                        val dropColor = if (selected) accent else PaperCream
                        Canvas(Modifier.matchParentSize()) {
                            val t = fall.value
                            if (t < 1f) {
                                val side = 9.dp.toPx()
                                val y = side * 0.6f + (size.height / 2 - side * 0.6f) * t
                                val shrink = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
                                drawPaperDrop(Offset(size.width / 2, y), side * shrink, dropColor)
                            }
                        }
                    }
                    Text(
                        label,
                        color = color,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = if (item == Tab.DOWNLOADS) {
                            Modifier.graphicsLayer {
                                val b = bump.value
                                scaleX = 1f + b * 0.3f
                                scaleY = 1f + b * 0.22f
                                translationY = -b * 5.dp.toPx()
                            }
                        } else {
                            Modifier
                        },
                    )
                    if (item == Tab.DOWNLOADS) {
                        // Fully qualified: the outer Row/Box scopes must not be used implicitly here.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = activeCount > 0,
                            enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 2.dp),
                        ) {
                            Box(
                                Modifier
                                    .graphicsLayer {
                                        val b = bump.value
                                        scaleX = 1f + b * 0.45f
                                        scaleY = 1f + b * 0.45f
                                        translationY = -b * 4.dp.toPx()
                                    }
                                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                RollingNumber(activeCount)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Link field

/** White card (rises in): cream field with the detected platform logo, then "Coller le lien" or ink paste + "Analyser". */
@Composable
fun PaperLinkCard(
    pane: LinkPane,
    enabled: Boolean,
    onPaste: (String?) -> Unit,
    onAnalyze: () -> Unit,
    onReset: () -> Unit,
) {
    val accent = LocalAccent.current.start
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val detected = remember(pane.urlText) { Links.platformOfText(pane.urlText) }
    val platform = if (pane.urlText.isBlank()) pane.platform else detected
    val outline by animateColorAsState(if (focused) accent else Color.Transparent, label = "paperFieldOutline")
    val loading = pane.phase is Phase.Loading
    val fieldShape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    val paste = {
        focus.clearFocus()
        onPaste(clipboard.getText()?.text)
    }

    Column(
        Modifier
            .staggeredEntrance(0, baseDelay = 60)
            .fillMaxWidth()
            .paperCard()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(fieldShape)
                .background(Palette.Surface2)
                .border(1.5.dp, outline, fieldShape)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedPlatformGlyph(platform, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = pane.urlText,
                onValueChange = { pane.urlText = it },
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp, fontFamily = LocalTokens.current.body),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    onAnalyze()
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (pane.urlText.isEmpty()) {
                            Text(linkPlaceholder(pane.platform), color = Palette.TextTertiary, fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = pane.urlText.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .bounceClick(pressedScale = 0.9f) { onReset() }
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Palette.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = Palette.TextPrimary, modifier = Modifier.size(17.dp))
                }
            }
        }
        AnimatedContent(
            targetState = pane.urlText.isBlank(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "paperLinkAction",
        ) { empty ->
            if (empty) {
                PaperButton("Coller le lien", Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentPaste, enabled = enabled, onClick = paste)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperInkButton(Icons.Rounded.ContentPaste, "Coller", onClick = paste)
                    PaperButton(
                        "Analyser",
                        Modifier.weight(1f),
                        icon = Icons.Rounded.Search,
                        enabled = enabled,
                        loading = loading,
                    ) {
                        focus.clearFocus()
                        onAnalyze()
                    }
                }
            }
        }
    }
}

// MARK: - Empty state: how to

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperGuide(platform: Platform) {
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier
                .staggeredEntrance(0, key = platform, baseDelay = 40)
                .fillMaxWidth()
                .paperCard()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Comment faire",
                    color = Palette.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.weight(1f),
                )
                // The mouth, waiting for a link.
                GulpMark(26.dp, style = GulpStyle.bare(drop = accent, mouth = Palette.TextPrimary))
            }
            guideSteps(platform).forEachIndexed { index, step ->
                Row(Modifier.staggeredEntrance(index, key = platform, baseDelay = 80), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) accent else Palette.TextPrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((index + 1).toString(), color = PaperInkText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(step, color = Palette.TextPrimary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Surface2),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(SHARE_TIP, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            guideFeatures(platform).forEachIndexed { index, chip ->
                Row(
                    Modifier
                        .staggeredEntrance(index, key = platform, baseDelay = 200)
                        .graphicsLayer { alpha = if (chip.dimmed) 0.5f else 1f }
                        .height(38.dp)
                        .paperCard(CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (chip.platform != null) {
                        PlatformGlyph(chip.platform, Modifier.size(17.dp))
                    } else if (chip.icon != null) {
                        Icon(chip.icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                    }
                    Text(chip.text, color = Palette.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        guideFootnote(platform)?.let {
            Text(it, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
