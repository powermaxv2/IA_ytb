package com.powermaxv2.ytdownloader.ui.skin.terminal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.LinkPane
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.Phase
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.classic.SHARE_TIP
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFeatures
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFootnote
import com.powermaxv2.ytdownloader.ui.skin.classic.guideSteps
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick

// "Terminal" skin: near-black console, green grid, one neon accent, JetBrains Mono + Archivo Black titles.

private val Amber = Color(0xFFFFB020)

// MARK: - Shared helpers

/** 0/1 square wave for blinking cursors (period ~1s). */
@Composable
internal fun rememberBlink(periodMs: Int = 1060): State<Float> {
    val transition = rememberInfiniteTransition(label = "termBlink")
    return transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "termBlinkPhase",
    )
}

/** Hides the element during the second half of each blink period. */
@Composable
internal fun Modifier.blinking(periodMs: Int = 1060): Modifier {
    val phase = rememberBlink(periodMs)
    return graphicsLayer { alpha = if (phase.value < 0.5f) 1f else 0f }
}

/** Almost square panel with a 1dp outline. */
@Composable
internal fun Modifier.termPanel(): Modifier {
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    return this
        .clip(shape)
        .background(Palette.Surface)
        .border(1.dp, Palette.Stroke, shape)
}

/** Small uppercase mono label, e.g. "> LIEN". */
@Composable
internal fun TermLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(text, color = color, fontSize = 12.sp, letterSpacing = 1.5.sp, maxLines = 1, modifier = modifier)
}

/**
 * Terminal button: [solid] = accent fill with dark text, else accent outline. Pressing inverts the colours
 * (a short flash), the release springs back.
 */
@Composable
internal fun TermButton(
    text: String,
    modifier: Modifier = Modifier,
    solid: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    big: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current.start
    val dark = Palette.Background
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = solid != pressed
    val fill by animateColorAsState(if (filled) accent else dark, tween(if (pressed) 40 else 220), label = "termButtonFill")
    val ink by animateColorAsState(if (filled) dark else accent, tween(if (pressed) 40 else 220), label = "termButtonInk")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "termButtonAlpha")
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.buttonRadius)
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(fill)
            .border(1.dp, accent, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                view.tick(haptics)
                onClick()
            }
            .defaultMinSize(minHeight = if (big) 60.dp else 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "termButton",
        ) { isLoading ->
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ANALYSE", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("…", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.blinking(600))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text,
                        color = ink,
                        fontSize = if (big) 19.sp else 14.sp,
                        fontWeight = if (big) FontWeight.Normal else FontWeight.Bold,
                        fontFamily = if (big) tokens.display else tokens.body,
                        letterSpacing = if (big) 0.5.sp else 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(it, color = ink.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// MARK: - Background

/** Near-black with a faint accent grid every 28dp and a soft glow at the top. */
@Composable
fun TerminalBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val base = Palette.Background
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.08f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        val step = 28.dp.toPx()
        val line = accent.copy(alpha = 0.05f)
        val stroke = 1.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
            y += step
        }
    }
}

// MARK: - Header

private fun terminalTitle(platform: Platform) = when (platform) {
    Platform.YOUTUBE -> "YOUTUBE"
    Platform.INSTAGRAM -> "INSTAGRAM"
    else -> "AUTRES"
}

/** Engine line with a coloured dot, typewriter title with a blinking accent underscore, menu square. */
@Composable
fun TerminalHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit) {
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val title = terminalTitle(platform)
    val typed = remember(platform) { Animatable(0f) }
    LaunchedEffect(platform) {
        typed.animateTo(title.length.toFloat(), tween(title.length * 55, easing = LinearEasing))
    }
    val count by remember(platform) { derivedStateOf { typed.value.toInt() } }

    val (dotColor, engineText) = when (engineState) {
        EngineState.Ready -> accent to "MOTEUR PRÊT · YT-DLP"
        EngineState.Starting -> Amber to "DÉMARRAGE · YT-DLP"
        is EngineState.Failed -> Palette.Danger to "MOTEUR EN ÉCHEC · YT-DLP"
    }
    val dot by animateColorAsState(dotColor, label = "termEngineDot")
    val dotPhase = rememberBlink(900)
    val starting = engineState == EngineState.Starting

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = if (starting && dotPhase.value >= 0.5f) 0.25f else 1f }
                        .clip(CircleShape)
                        .background(dot),
                )
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = engineText,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "termEngine",
                ) {
                    TermLabel(it, color = Palette.TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title.take(count),
                    color = Palette.TextPrimary,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontFamily = tokens.display,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    "_",
                    color = accent,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontFamily = tokens.display,
                    maxLines = 1,
                    modifier = Modifier.blinking(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .padding(top = 2.dp)
                .bounceClick(pressedScale = 0.9f, onClick = onSettings)
                .size(48.dp)
                .termPanel(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Menu, contentDescription = "Réglages", tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

// MARK: - Bottom navigation

private val terminalTabs = listOf(
    Tab.YOUTUBE to "YT",
    Tab.INSTAGRAM to "INSTA",
    Tab.OTHER to "WEB",
    Tab.DOWNLOADS to "FICHIERS",
)

/** Flat full-width bar; a lighter cell and a 2dp accent top line slide to the selected tab. */
@Composable
fun TerminalBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val stroke = Palette.Stroke
    val index = terminalTabs.indexOfFirst { it.first == tab }
    val bump = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            bump.snapTo(1f)
            bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Palette.NavBar),
    ) {
        val segment = maxWidth / terminalTabs.size
        val offset by animateDpAsState(segment * index.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 420f), label = "termNavLine")
        val markAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, label = "termNavAlpha")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { alpha = markAlpha }
                .background(accent.copy(alpha = 0.07f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(stroke),
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .height(2.dp)
                .graphicsLayer { alpha = markAlpha }
                .background(accent),
        )
        Row(Modifier.fillMaxSize()) {
            terminalTabs.forEach { (item, label) ->
                val selected = item == tab
                val color by animateColorAsState(if (selected) accent else Palette.TextSecondary, label = "termNavText")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                        if (item == Tab.DOWNLOADS) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = activeCount > 0,
                                enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                Text(
                                    " [$activeCount]",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer {
                                        val b = bump.value
                                        scaleX = 1f + b * 0.35f
                                        scaleY = 1f + b * 0.35f
                                        translationY = -b * 4.dp.toPx()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Link field

/** "> LIEN" panel: "$" prompt field with a blinking block cursor, then terminal buttons. */
@Composable
fun TerminalLinkCard(vm: MainViewModel, pane: LinkPane, enabled: Boolean) {
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(if (focused) accent else accent.copy(alpha = 0.55f), label = "termFieldOutline")
    val loading = pane.phase is Phase.Loading
    val empty = pane.urlText.isEmpty()
    val fieldShape = RoundedCornerShape(tokens.fieldRadius)
    val paste = {
        focus.clearFocus()
        vm.paste(pane, clipboard.getText()?.text)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .termPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TermLabel("> LIEN")
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(fieldShape)
                .background(Palette.Background)
                .border(1.dp, outline, fieldShape)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$ ", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = pane.urlText,
                onValueChange = { pane.urlText = it },
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 14.sp, fontFamily = tokens.body),
                // The block cursor stands in for the caret while the field is empty.
                cursorBrush = SolidColor(if (empty) Color.Transparent else accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    vm.analyze(pane)
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (empty) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(width = 9.dp, height = 18.dp)
                                        .blinking()
                                        .background(accent),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("https://…", color = Palette.TextTertiary, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = !empty,
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Text(
                    "[x]",
                    color = Palette.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .bounceClick(pressedScale = 0.85f) { vm.reset(pane) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        AnimatedContent(
            targetState = pane.urlText.isBlank(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "termLinkAction",
        ) { blank ->
            if (blank) {
                TermButton("[ COLLER LE LIEN ]", Modifier.fillMaxWidth(), enabled = enabled, onClick = paste)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TermButton("[ COLLER ]", Modifier.weight(1f), solid = false, onClick = paste)
                    TermButton("ANALYSER ↵", Modifier.weight(1.4f), enabled = enabled, loading = loading) {
                        focus.clearFocus()
                        vm.analyze(pane)
                    }
                }
            }
        }
    }
}

// MARK: - Empty state: how to

/** Bracketed uppercase tag, e.g. "[ JUSQU'EN 8K ]". */
@Composable
internal fun TermTag(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary, fontSize: TextUnit = 12.sp, height: Dp = 32.dp) {
    val accent = LocalAccent.current.start
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(LocalTokens.current.chipRadius))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(LocalTokens.current.chipRadius))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("[ ", color = accent, fontSize = fontSize, fontWeight = FontWeight.Bold)
        Text(text.uppercase(), color = color, fontSize = fontSize, letterSpacing = 0.5.sp, maxLines = 1)
        Text(" ]", color = accent, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalGuide(platform: Platform) {
    val accent = LocalAccent.current.start
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .termPanel()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TermLabel("> COMMENT FAIRE")
            guideSteps(platform).forEachIndexed { index, step ->
                Row(Modifier.staggeredEntrance(index, key = platform, baseDelay = 80)) {
                    Text(
                        (index + 1).toString().padStart(2, '0'),
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(step, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Stroke),
            )
            Row {
                Text("# ", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(SHARE_TIP, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            guideFeatures(platform).forEachIndexed { index, chip ->
                TermTag(
                    chip.text,
                    Modifier
                        .staggeredEntrance(index, key = platform, baseDelay = 200)
                        .graphicsLayer { alpha = if (chip.dimmed) 0.5f else 1f },
                )
            }
        }
        guideFootnote(platform)?.let {
            Text("// $it", color = Palette.TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
