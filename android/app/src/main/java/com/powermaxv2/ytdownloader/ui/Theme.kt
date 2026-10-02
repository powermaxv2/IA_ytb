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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.powermaxv2.ytdownloader.core.Platform

object Palette {
    val Background = Color(0xFF0B0B10)

    /** Cards. */
    val Surface = Color(0xFF15151D)

    /** Fields, tiles and buttons inside the cards. */
    val Surface2 = Color(0xFF1E1E28)
    val NavBar = Color(0xF516161F)
    val Accent = Color(0xFFFF3B4E)
    val Accent2 = Color(0xFFFF7A45)
    val InstaPurple = Color(0xFF9B3FD6)
    val InstaPink = Color(0xFFE1306C)
    val InstaOrange = Color(0xFFF77737)
    val WebCyan = Color(0xFF06B6D4)
    val WebIndigo = Color(0xFF6366F1)
    val Stroke = Color.White.copy(alpha = 0.07f)
    val TextSecondary = Color.White.copy(alpha = 0.62f)
    val TextTertiary = Color.White.copy(alpha = 0.40f)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFFF6B6B)
}

/** The app accent follows the tab: red → orange for YouTube, purple → pink for Instagram, cyan → indigo for the other sites. */
@Immutable
data class Accent(val start: Color, val end: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
    fun horizontal(): Brush = Brush.horizontalGradient(listOf(start, end))

    /** Accent-tinted fill for selected tiles, chips and icon backgrounds. */
    fun tint(alpha: Float = 0.16f): Color = start.copy(alpha = alpha)

    companion object {
        val YouTube = Accent(Palette.Accent, Palette.Accent2)
        val Instagram = Accent(Palette.InstaPurple, Palette.InstaPink)
        val Web = Accent(Palette.WebCyan, Palette.WebIndigo)

        fun of(platform: Platform) = when (platform) {
            Platform.YOUTUBE -> YouTube
            Platform.INSTAGRAM -> Instagram
            else -> Web
        }
    }
}

val LocalAccent = compositionLocalOf { Accent.YouTube }

@Composable
fun AppTheme(platform: Platform, content: @Composable () -> Unit) {
    val target = Accent.of(platform)
    val start by animateColorAsState(target.start, tween(600), label = "accentStart")
    val end by animateColorAsState(target.end, tween(600), label = "accentEnd")
    val scheme = darkColorScheme(
        primary = start,
        secondary = end,
        background = Palette.Background,
        surface = Palette.Surface,
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
