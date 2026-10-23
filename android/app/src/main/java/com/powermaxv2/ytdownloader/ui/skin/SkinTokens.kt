package com.powermaxv2.ytdownloader.ui.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.R
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.Accent

/** Colours, shapes and fonts of a skin; shared screens read them through [LocalTokens] (or `Palette`). */
@Immutable
data class SkinTokens(
    val background: Color,
    /** Cards. */
    val surface: Color,
    /** Fields, tiles and buttons inside the cards. */
    val surface2: Color,
    val navBar: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val danger: Color,
    /** Text and icons drawn on an accent fill. */
    val onAccent: Color,
    val isLight: Boolean,
    val cardRadius: Dp,
    val fieldRadius: Dp,
    val buttonRadius: Dp,
    val chipRadius: Dp,
    /** Shared cards: hairline outline (dark skins) and/or soft shadow ([cardShadow] > 0, Papier). */
    val cardBorder: Boolean,
    val cardShadow: Dp,
    val cardShadowColor: Color,
    /** Toast pill fill and text. */
    val toastBackground: Color,
    val toastText: Color,
    val display: FontFamily,
    val body: FontFamily,
    val mono: Boolean,
    val uppercaseLabels: Boolean,
    val youtubeAccent: Accent,
    val instagramAccent: Accent,
    val webAccent: Accent,
) {
    @Suppress("UNUSED_PARAMETER")
    fun accentFor(platform: Platform): Accent = youtubeAccent
}

val BricolageFamily = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val ArchivoBlackFamily = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

/** Gulp (signature skin): warm near-black, the brand red → orange gradient, Bricolage Grotesque titles. */
val ClassicTokens = SkinTokens(
    background = Color(0xFF120A08),
    surface = Color(0xFF1D1310),
    surface2 = Color(0xFF281A16),
    navBar = Color(0xF51F1512),
    stroke = Color(0xFFFFE3D6).copy(alpha = 0.08f),
    textPrimary = Color(0xFFFFF4EA),
    textSecondary = Color(0xFFFFE3D6).copy(alpha = 0.64f),
    textTertiary = Color(0xFFFFE3D6).copy(alpha = 0.40f),
    success = Color(0xFF34D399),
    danger = Color(0xFFFF6B6B),
    onAccent = Color.White,
    isLight = false,
    cardRadius = 24.dp,
    fieldRadius = 18.dp,
    buttonRadius = 18.dp,
    chipRadius = 50.dp,
    cardBorder = true,
    cardShadow = 0.dp,
    cardShadowColor = Color.Black,
    toastBackground = Color(0xFF281A16),
    toastText = Color(0xFFFFF4EA),
    display = BricolageFamily,
    body = FontFamily.Default,
    mono = false,
    uppercaseLabels = false,
    youtubeAccent = Accent.YouTube,
    instagramAccent = Accent.Instagram,
    webAccent = Accent.Web,
)

/** Light, magazine-like: white cards on Gulp cream, ink of the logo's mouth, flat accents, Bricolage Grotesque. */
val PaperTokens = SkinTokens(
    background = Color(0xFFFBF1E7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF8EDE3),
    navBar = Color(0xFF2A0F0A),
    stroke = Color(0x162A0F0A),
    textPrimary = Color(0xFF2A0F0A),
    textSecondary = Color(0xFF7A6058),
    textTertiary = Color(0xFFA88F86),
    success = Color(0xFF1E9E5A),
    danger = Color(0xFFD93025),
    onAccent = Color.White,
    isLight = true,
    cardRadius = 22.dp,
    fieldRadius = 18.dp,
    buttonRadius = 22.dp,
    chipRadius = 50.dp,
    cardBorder = false,
    cardShadow = 6.dp,
    cardShadowColor = Color(0xFF7A2A10).copy(alpha = 0.12f),
    // Ink pill like the bottom bar: readable on the cream background.
    toastBackground = Color(0xFF2A0F0A),
    toastText = Color.White,
    display = BricolageFamily,
    body = BricolageFamily,
    mono = false,
    uppercaseLabels = false,
    youtubeAccent = Accent(Color(0xFFF0361F), Color(0xFFF0361F)),
    instagramAccent = Accent(Color(0xFFC1358A), Color(0xFFC1358A)),
    webAccent = Accent(Color(0xFF1F6FEB), Color(0xFF1F6FEB)),
)

/** Console: near-black, Gulp orange phosphor, JetBrains Mono everywhere and Archivo Black for the big titles. */
val TerminalTokens = SkinTokens(
    background = Color(0xFF0B0908),
    surface = Color(0xFF110E0C),
    surface2 = Color(0xFF181310),
    navBar = Color(0xFF110E0C),
    stroke = Color(0xFF2E2621),
    textPrimary = Color(0xFFF2E6DC),
    textSecondary = Color(0xFF9A8B80),
    textTertiary = Color(0xFF65584F),
    success = Color(0xFFFFA43D),
    danger = Color(0xFFFF5C5C),
    onAccent = Color(0xFF0B0908),
    isLight = false,
    cardRadius = 4.dp,
    fieldRadius = 4.dp,
    buttonRadius = 4.dp,
    chipRadius = 4.dp,
    cardBorder = true,
    cardShadow = 0.dp,
    cardShadowColor = Color.Black,
    toastBackground = Color(0xFF181310),
    toastText = Color(0xFFF2E6DC),
    display = ArchivoBlackFamily,
    body = JetBrainsMonoFamily,
    mono = true,
    uppercaseLabels = true,
    youtubeAccent = Accent(Color(0xFFFF8A1F), Color(0xFFFF8A1F)),
    instagramAccent = Accent(Color(0xFFFF5FD2), Color(0xFFFF5FD2)),
    webAccent = Accent(Color(0xFF3DE0FF), Color(0xFF3DE0FF)),
)

fun tokensFor(skin: Skin): SkinTokens = when (skin) {
    Skin.CLASSIC -> ClassicTokens
    Skin.PAPER -> PaperTokens
    Skin.TERMINAL -> TerminalTokens
}

val LocalSkin = staticCompositionLocalOf { Skin.CLASSIC }
val LocalTokens = staticCompositionLocalOf { ClassicTokens }
