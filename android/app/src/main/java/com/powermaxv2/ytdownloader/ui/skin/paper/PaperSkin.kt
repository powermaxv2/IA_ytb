package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
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
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.classic.SHARE_TIP
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFeatures
import com.powermaxv2.ytdownloader.ui.skin.classic.guideFootnote
import com.powermaxv2.ytdownloader.ui.skin.classic.guideSteps
import com.powermaxv2.ytdownloader.ui.skin.classic.linkPlaceholder
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick

// "Papier" skin: white cards on light grey, black ink, one flat accent per tab, Bricolage Grotesque.

/** Text colour on the black ink (selected segment, badges): the white of the cards. */
private val PaperInkText = Color.White

// MARK: - Surfaces

/** White card with a very soft shadow and no outline. */
@Composable
internal fun Modifier.paperCard(shape: Shape = RoundedCornerShape(LocalTokens.current.cardRadius)): Modifier =
    this
        .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
        .clip(shape)
        .background(Palette.Surface)

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
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = accent, spotColor = accent)
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

@Composable
fun PaperBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background),
    )
}

// MARK: - Header

/** Short tab names: the title is set very large. */
private fun paperTitle(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Instagram"
    Platform.OTHER -> "Autres"
    else -> "YouTube"
}

/** Small grey app name, huge title underlined in the accent (the bar grows on each tab), settings square on the right. */
@Composable
fun PaperHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit) {
    val accent = LocalAccent.current.start
    val grow = remember(platform) { Animatable(0f) }
    LaunchedEffect(platform) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("YT Downloader", color = Palette.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                        alpha = 0.3f + 0.7f * grow.value.coerceIn(0f, 1f)
                        translationY = (1f - grow.value) * 8.dp.toPx()
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .graphicsLayer {
                            scaleX = grow.value
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
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

/** Black bar of labels; a white pill slides under the selected one (hidden while Réglages is open). */
@Composable
fun PaperBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.start
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val bar = Palette.NavBar
    val shape = RoundedCornerShape(26.dp)
    val index = paperTabs.indexOfFirst { it.first == tab }
    val bump = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            bump.snapTo(1f)
            bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
        }
    }
    BoxWithConstraints(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(bar)
            .padding(6.dp),
    ) {
        val segment = maxWidth / paperTabs.size
        val offset by animateDpAsState(segment * index.coerceAtLeast(0), spring(dampingRatio = 0.72f, stiffness = 380f), label = "paperPill")
        val pillAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, label = "paperPillAlpha")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { alpha = pillAlpha }
                .clip(RoundedCornerShape(20.dp))
                .background(PaperInkText),
        )
        Row(Modifier.fillMaxSize()) {
            paperTabs.forEach { (item, label) ->
                val selected = item == tab
                val color by animateColorAsState(if (selected) bar else PaperInkText.copy(alpha = 0.62f), label = "paperNavText")
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
                                scaleY = 1f + b * 0.3f
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

/** White card: grey field with the detected platform logo, then "Coller le lien" or paste + "Analyser". */
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
                    Box(
                        Modifier
                            .bounceClick(pressedScale = 0.9f, onClick = paste)
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Coller", tint = Palette.TextPrimary, modifier = Modifier.size(24.dp))
                    }
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
                .fillMaxWidth()
                .paperCard()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Comment faire",
                color = Palette.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                letterSpacing = (-0.3).sp,
            )
            guideSteps(platform).forEachIndexed { index, step ->
                Row(Modifier.staggeredEntrance(index, key = platform, baseDelay = 80), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Palette.TextPrimary),
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
