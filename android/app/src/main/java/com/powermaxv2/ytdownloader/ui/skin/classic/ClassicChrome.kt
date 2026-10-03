package com.powermaxv2.ytdownloader.ui.skin.classic

import com.powermaxv2.ytdownloader.ui.hub.HomeLink
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.IconCircleButton
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.RollingNumber
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.gradientTint
import com.powermaxv2.ytdownloader.ui.panel
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tick

// MARK: - Background

/** Warm dark background: two accent glows and an ember near the bottom, drifting slowly. */
@Composable
fun ClassicBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val background = Palette.Background
    val transition = rememberInfiniteTransition(label = "glow")
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowDrift",
    )
    val sway by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowSway",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        val w = size.width
        val h = size.height
        // Main glow behind the title, drifting from left to right.
        drawRect(
            Brush.radialGradient(
                listOf(accent.start.copy(alpha = 0.26f), Color.Transparent),
                Offset(w * (0.3f + 0.4f * drift), -w * 0.22f),
                w * (1.05f + 0.1f * sway),
            ),
        )
        // Second blob on the right, in the end colour of the accent.
        drawRect(
            Brush.radialGradient(
                listOf(accent.end.copy(alpha = 0.12f), Color.Transparent),
                Offset(w * (1f - 0.25f * sway), h * (0.06f + 0.1f * drift)),
                w * 0.75f,
            ),
        )
        // Faint ember behind the bottom bar.
        drawRect(
            Brush.radialGradient(
                listOf(GulpColors.Red.copy(alpha = 0.07f), Color.Transparent),
                Offset(w * (0.15f + 0.3f * sway), h * (0.92f - 0.06f * drift)),
                w * 0.8f,
            ),
        )
    }
}

// MARK: - Motion

/** Soft highlight sweeping across a button every few seconds; [radius] matches the button corners. */
@Composable
internal fun Modifier.sheen(enabled: Boolean = true, radius: Dp = LocalTokens.current.buttonRadius): Modifier {
    val transition = rememberInfiniteTransition(label = "sheen")
    val x by transition.animateFloat(
        -0.6f, 1.6f, infiniteRepeatable(tween(1600, delayMillis = 2200, easing = FastOutSlowInEasing)), label = "sheenX",
    )
    val highlight = Color.White.copy(alpha = 0.24f)
    return drawWithContent {
        drawContent()
        if (enabled) {
            val w = size.width
            val band = w * 0.28f
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(w * x - band, 0f),
                    end = Offset(w * x + band * 0.4f, size.height),
                ),
                cornerRadius = CornerRadius(radius.toPx()),
            )
        }
    }
}

// MARK: - Header

/** Screen title of a link tab. */
internal fun platformTitle(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Instagram"
    Platform.OTHER -> "Autres sites"
    else -> "YouTube"
}

/** One-line description of what a link tab handles. */
internal fun platformSubtitle(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Reels, photos et carrousels publics"
    Platform.OTHER -> "X, TikTok, Facebook, Snapchat et liens vidéo"
    else -> "Vidéos jusqu'en 8K, musique, playlists"
}

/** The Gulp lockup (its mark swallows on its own and on each queued download) with settings, then the tab title. */
@Composable
fun ClassicHeader(platform: Platform, onSettings: () -> Unit, onHome: (() -> Unit)? = null) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeLink(onHome, Palette.TextSecondary) { GulpLockup(markSize = 30.dp, textColor = Palette.TextPrimary) }
            Spacer(Modifier.weight(1f))
            IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = onSettings)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.staggeredEntrance(0, key = platform, baseDelay = 60),
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .panel(RoundedCornerShape(16.dp), Palette.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (platform == Platform.OTHER) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(27.dp)
                            .gradientTint(LocalAccent.current.brush),
                    )
                } else {
                    PlatformGlyph(platform, Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    platformTitle(platform),
                    color = Palette.TextPrimary,
                    fontSize = 30.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = LocalTokens.current.display,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    platformSubtitle(platform),
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - Bottom navigation

/** A tab shows either a platform logo ([platform]) or an [icon]. */
private data class NavItem(
    val tab: Tab,
    val label: String,
    val icon: ImageVector? = null,
    val platform: Platform? = null,
)

private val navItems = listOf(
    NavItem(Tab.YOUTUBE, "YouTube", platform = Platform.YOUTUBE),
    NavItem(Tab.INSTAGRAM, "Instagram", platform = Platform.INSTAGRAM),
    NavItem(Tab.OTHER, "Autres", Icons.Rounded.Public),
    NavItem(Tab.DOWNLOADS, "Fichiers", Icons.Rounded.FolderOpen),
)

/**
 * Floating bar: the selected tab gets a pill in the accent gradient. When something is queued a little play
 * triangle drops into the Files tab, which squashes and springs back with its badge.
 * Settings has no tab here: while it is open no tab is highlighted.
 */
@Composable
fun ClassicBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(28.dp)
    val pillFill = Brush.horizontalGradient(listOf(accent.start.copy(alpha = 0.34f), accent.end.copy(alpha = 0.22f)))
    val pillOutline = Brush.horizontalGradient(listOf(accent.start.copy(alpha = 0.75f), accent.end.copy(alpha = 0.45f)))
    val dropBrush = accent.brush
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(74.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Palette.NavBar)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navItems.forEach { item ->
            val selected = item.tab == tab
            val indicator by animateFloatAsState(
                if (selected) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "navIndicator",
            )
            val color by animateColorAsState(if (selected) Palette.TextPrimary else Palette.TextTertiary, label = "navColor")
            // drop: 0 → 1 while the triangle falls (1 = at rest, hidden); bump: squash of the icon once it lands.
            val drop = remember { Animatable(1f) }
            val bump = remember { Animatable(0f) }
            if (item.tab == Tab.DOWNLOADS) {
                LaunchedEffect(pulse) {
                    if (pulse > 0) {
                        drop.snapTo(0f)
                        drop.animateTo(1f, tween(420, easing = FastOutLinearInEasing))
                        bump.snapTo(1f)
                        bump.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 280f))
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        view.tick(haptics)
                        onSelect(item.tab)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(32.dp)
                            .graphicsLayer {
                                scaleX = 0.6f + 0.4f * indicator
                                alpha = indicator
                            }
                            .clip(CircleShape)
                            .background(pillFill)
                            .border(1.dp, pillOutline, CircleShape),
                    )
                    Box {
                        val iconModifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                val b = bump.value
                                scaleX = 1f + b * 0.28f
                                scaleY = 1f - b * 0.2f
                                translationY = b * 3.dp.toPx()
                                alpha = if (item.platform != null && !selected) 0.55f else 1f
                            }
                        if (item.platform != null) {
                            PlatformGlyph(item.platform, iconModifier)
                        } else if (item.icon != null) {
                            Icon(item.icon, contentDescription = item.label, tint = color, modifier = iconModifier)
                        }
                        if (item.tab == Tab.DOWNLOADS) {
                            // The falling play triangle: shrinks and fades as it is swallowed by the icon.
                            Canvas(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(11.dp)
                                    .graphicsLayer {
                                        val d = drop.value
                                        translationY = (-20f * (1f - d)).dp.toPx()
                                        val s = 1f - 0.5f * d
                                        scaleX = s
                                        scaleY = s
                                        alpha = when {
                                            d >= 1f -> 0f
                                            d < 0.75f -> 1f
                                            else -> (1f - d) / 0.25f
                                        }
                                    },
                            ) {
                                val triangle = Path().apply {
                                    moveTo(size.width * 0.08f, size.height * 0.12f)
                                    lineTo(size.width * 0.92f, size.height * 0.12f)
                                    lineTo(size.width * 0.5f, size.height * 0.88f)
                                    close()
                                }
                                drawPath(triangle, dropBrush)
                                drawPath(triangle, dropBrush, style = Stroke(size.width * 0.18f, join = StrokeJoin.Round))
                            }
                            // Fully qualified: the outer Column/Row scopes must not be used implicitly here.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = activeCount > 0,
                                enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-6).dp),
                            ) {
                                Box(
                                    Modifier
                                        .graphicsLayer {
                                            val s = 1f + bump.value * 0.45f
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .sizeIn(minWidth = 17.dp, minHeight = 17.dp)
                                        .clip(CircleShape)
                                        .background(accent.horizontal())
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RollingNumber(activeCount)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    item.label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
