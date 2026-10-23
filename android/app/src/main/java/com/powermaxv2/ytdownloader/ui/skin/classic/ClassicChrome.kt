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
import com.powermaxv2.ytdownloader.ui.RollingNumber
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

// MARK: - Bottom navigation
