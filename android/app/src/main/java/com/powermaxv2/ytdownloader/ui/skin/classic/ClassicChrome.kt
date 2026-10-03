package com.powermaxv2.ytdownloader.ui.skin.classic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.IconCircleButton
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PlatformGlyph
import com.powermaxv2.ytdownloader.ui.RollingNumber
import com.powermaxv2.ytdownloader.ui.ScreenTitle
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.gradientTint
import com.powermaxv2.ytdownloader.ui.panel
import com.powermaxv2.ytdownloader.ui.tick

// MARK: - Background

/** Plain dark background with a soft glow of the accent colour behind the screen title. */
@Composable
fun ClassicBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val background = Palette.Background
    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        val center = Offset(size.width / 2, -size.width * 0.25f)
        val radius = size.width * 1.15f
        drawRect(Brush.radialGradient(listOf(accent.start.copy(alpha = 0.26f), Color.Transparent), center, radius))
        drawRect(
            Brush.radialGradient(
                listOf(accent.end.copy(alpha = 0.10f), Color.Transparent),
                Offset(size.width, size.height * 0.08f),
                size.width * 0.7f,
            ),
        )
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

@Composable
fun ClassicHeader(platform: Platform, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .panel(RoundedCornerShape(17.dp), Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (platform == Platform.OTHER) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .gradientTint(LocalAccent.current.brush),
                )
            } else {
                PlatformGlyph(platform, Modifier.size(29.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        ScreenTitle(platformTitle(platform), platformSubtitle(platform), Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = onSettings)
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
 * Floating bar: the selected tab gets a tinted pill behind its icon, the Files icon jumps when something is queued.
 * Settings has no tab here: while it is open no tab is highlighted.
 */
@Composable
fun ClassicBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(28.dp)
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
            val bump = remember { Animatable(0f) }
            if (item.tab == Tab.DOWNLOADS) {
                LaunchedEffect(pulse) {
                    if (pulse > 0) {
                        bump.snapTo(1f)
                        bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
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
                            .background(accent.tint(0.22f)),
                    )
                    Box {
                        val iconModifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                val b = bump.value
                                scaleX = 1f + b * 0.4f
                                scaleY = 1f + b * 0.4f
                                translationY = -b * 7.dp.toPx()
                                alpha = if (item.platform != null && !selected) 0.55f else 1f
                            }
                        if (item.platform != null) {
                            PlatformGlyph(item.platform, iconModifier)
                        } else if (item.icon != null) {
                            Icon(item.icon, contentDescription = item.label, tint = color, modifier = iconModifier)
                        }
                        if (item.tab == Tab.DOWNLOADS) {
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
