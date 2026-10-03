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
    val display: FontFamily,
    val body: FontFamily,
    val mono: Boolean,
    val uppercaseLabels: Boolean,
    val youtubeAccent: Accent,
    val instagramAccent: Accent,
    val webAccent: Accent,
) {
    fun accentFor(platform: Platform): Accent = when (platform) {
        Platform.YOUTUBE -> youtubeAccent
        Platform.INSTAGRAM -> instagramAccent
        else -> webAccent
    }
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

/** Today's dark look, unchanged. */
val ClassicTokens = SkinTokens(
    background = Color(0xFF0B0B10),
    surface = Color(0xFF15151D),
    surface2 = Color(0xFF1E1E28),
    navBar = Color(0xF516161F),
    stroke = Color.White.copy(alpha = 0.07f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.62f),
    textTertiary = Color.White.copy(alpha = 0.40f),
    success = Color(0xFF34D399),
    danger = Color(0xFFFF6B6B),
    onAccent = Color.White,
    isLight = false,
    cardRadius = 24.dp,
    fieldRadius = 18.dp,
    buttonRadius = 18.dp,
    chipRadius = 50.dp,
    // Exactly what the Classique texts resolved to before skins: Material's titles (SansSerif), plain text fields (Default).
    display = FontFamily.SansSerif,
    body = FontFamily.Default,
    mono = false,
    uppercaseLabels = false,
    youtubeAccent = Accent.YouTube,
    instagramAccent = Accent.Instagram,
    webAccent = Accent.Web,
)

/** Light, magazine-like: white cards on light grey, flat accents, Bricolage Grotesque. */
val PaperTokens = SkinTokens(
    background = Color(0xFFF1F1F3),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F2F4),
    navBar = Color(0xFF111216),
    stroke = Color(0x14111216),
    textPrimary = Color(0xFF111216),
    textSecondary = Color(0xFF6B6E76),
    textTertiary = Color(0xFF9A9DA5),
    success = Color(0xFF1E9E5A),
    danger = Color(0xFFD93025),
    onAccent = Color.White,
    isLight = true,
    cardRadius = 22.dp,
    fieldRadius = 18.dp,
    buttonRadius = 22.dp,
    chipRadius = 50.dp,
    display = BricolageFamily,
    body = BricolageFamily,
    mono = false,
    uppercaseLabels = false,
    youtubeAccent = Accent(Color(0xFFC8202B), Color(0xFFC8202B)),
    instagramAccent = Accent(Color(0xFFC1358A), Color(0xFFC1358A)),
    webAccent = Accent(Color(0xFF1F6FEB), Color(0xFF1F6FEB)),
)

/** Console: near-black, neon accent, JetBrains Mono everywhere and Archivo Black for the big titles. */
val TerminalTokens = SkinTokens(
    background = Color(0xFF0A0B0A),
    surface = Color(0xFF0F110F),
    surface2 = Color(0xFF151815),
    navBar = Color(0xFF0F110F),
    stroke = Color(0xFF262A24),
    textPrimary = Color(0xFFE6EDE0),
    textSecondary = Color(0xFF8A9384),
    textTertiary = Color(0xFF5C6458),
    success = Color(0xFFC6FF3D),
    danger = Color(0xFFFF5C5C),
    onAccent = Color(0xFF0A0B0A),
    isLight = false,
    cardRadius = 4.dp,
    fieldRadius = 4.dp,
    buttonRadius = 4.dp,
    chipRadius = 4.dp,
    display = ArchivoBlackFamily,
    body = JetBrainsMonoFamily,
    mono = true,
    uppercaseLabels = true,
    youtubeAccent = Accent(Color(0xFFC6FF3D), Color(0xFFC6FF3D)),
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
