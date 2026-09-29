package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.powermaxv2.ytdownloader.core.Platform

object Palette {
    val Background = Color(0xFF0E0E14)
    val Accent = Color(0xFFFF4561)
    val Accent2 = Color(0xFFFF8F3D)
    val InstaPurple = Color(0xFF9B3FD6)
    val InstaPink = Color(0xFFE1306C)
    val InstaOrange = Color(0xFFF77737)
    val Purple = Color(0xFF8A5CF6)
    val Stroke = Color.White.copy(alpha = 0.09f)
    val Fill = Color.White.copy(alpha = 0.05f)
    val TextSecondary = Color.White.copy(alpha = 0.64f)
    val TextTertiary = Color.White.copy(alpha = 0.42f)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFFF6B6B)
}

/** The app accent follows the detected platform: red → orange for YouTube, purple → pink for Instagram. */
@Immutable
data class Accent(val start: Color, val end: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
    fun horizontal(): Brush = Brush.horizontalGradient(listOf(start, end))
    fun diagonal(size: Float): Brush = Brush.linearGradient(listOf(start, end), Offset.Zero, Offset(size, size))

    companion object {
        val YouTube = Accent(Palette.Accent, Palette.Accent2)
        val Instagram = Accent(Palette.InstaPurple, Palette.InstaPink)

        fun of(platform: Platform) = if (platform == Platform.INSTAGRAM) Instagram else YouTube
    }
}

val LocalAccent = compositionLocalOf { Accent.YouTube }

@Composable
fun AppTheme(platform: Platform, content: @Composable () -> Unit) {
    val target = Accent.of(platform)
    val start by animateColorAsState(target.start, tween(700), label = "accentStart")
    val end by animateColorAsState(target.end, tween(900), label = "accentEnd")
    val scheme = darkColorScheme(
        primary = start,
        secondary = end,
        background = Palette.Background,
        surface = Palette.Background,
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalAccent provides Accent(start, end)) {
            content()
        }
    }
}
