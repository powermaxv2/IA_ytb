package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.tokensFor

/** Colours of the current skin (see `ui/skin/SkinTokens.kt`), readable from any composable. */
object Palette {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.background

    /** Cards. */
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.surface

    /** Fields, tiles and buttons inside the cards. */
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.surface2
    val NavBar: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.navBar
    val Stroke: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.stroke
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.textTertiary
    val Success: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.success
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.danger

    /** Text and icons on an accent fill. */
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTokens.current.onAccent

    // Brand colours (Gulp red → orange, Instagram logo): the same in every skin.
    val Accent = Color(0xFFFF3B30)
    val Accent2 = Color(0xFFFF8A1F)
    val InstaPurple = Color(0xFF9B3FD6)
    val InstaPink = Color(0xFFE1306C)
    val InstaOrange = Color(0xFFF77737)
    val WebCyan = Color(0xFF06B6D4)
    val WebIndigo = Color(0xFF6366F1)
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

        /** Classique accents; use `LocalTokens.current.accentFor` for the current skin's. */
        fun of(platform: Platform) = when (platform) {
            Platform.YOUTUBE -> YouTube
            Platform.INSTAGRAM -> Instagram
            else -> Web
        }
    }
}

val LocalAccent = compositionLocalOf { Accent.YouTube }

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun AppTheme(platform: Platform, skin: Skin = Skin.CLASSIC, content: @Composable () -> Unit) {
    val tokens = tokensFor(skin)
    val target = tokens.accentFor(platform)
    val start by animateColorAsState(target.start, tween(600), label = "accentStart")
    val end by animateColorAsState(target.end, tween(600), label = "accentEnd")
    val scheme = if (tokens.isLight) {
        lightColorScheme(
            primary = start,
            secondary = end,
            background = tokens.background,
            surface = tokens.surface,
            onPrimary = tokens.onAccent,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
        )
    } else {
        darkColorScheme(
            primary = start,
            secondary = end,
            background = tokens.background,
            surface = tokens.surface,
            onPrimary = tokens.onAccent,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
        )
    }
    // Classique keeps Material's default typography untouched; the other skins swap the font family.
    val typography = if (skin == Skin.CLASSIC) Typography() else Typography().withFamily(tokens.body)
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalSkin provides skin,
            LocalTokens provides tokens,
            LocalAccent provides Accent(start, end),
        ) {
            content()
        }
    }
}
