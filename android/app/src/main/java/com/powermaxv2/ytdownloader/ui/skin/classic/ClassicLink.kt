package com.powermaxv2.ytdownloader.ui.skin.classic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Links
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.AnimatedPlatformGlyph
import com.powermaxv2.ytdownloader.ui.FeatureChip
import com.powermaxv2.ytdownloader.ui.IconCircleButton
import com.powermaxv2.ytdownloader.ui.LinkPane
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.Phase
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.gradientTint
import com.powermaxv2.ytdownloader.ui.panel
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance

// MARK: - Link field

/** Placeholder of the link field of a tab. */
internal fun linkPlaceholder(platform: Platform) = when (platform) {
    Platform.INSTAGRAM -> "Collez un lien Instagram…"
    Platform.OTHER -> "Collez un lien vidéo…"
    else -> "Collez un lien YouTube…"
}

/**
 * Link field and its main action: "Coller le lien" while the field is empty (paste + analysis in one tap),
 * "Analyser" once something is typed, with a paste button next to it.
 */
@Composable
fun ClassicLinkCard(
    pane: LinkPane,
    enabled: Boolean,
    onPaste: (String?) -> Unit,
    onAnalyze: () -> Unit,
    onReset: () -> Unit,
) {
    val accent = LocalAccent.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val detected = remember(pane.urlText) { Links.platformOfText(pane.urlText) }
    val platform = if (pane.urlText.isBlank()) pane.platform else detected
    val focusGlow by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "fieldFocus")
    val loading = pane.phase is Phase.Loading
    val fieldShape = RoundedCornerShape(18.dp)
    val paste = {
        focus.clearFocus()
        onPaste(clipboard.getText()?.text)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 60)
            .panel()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(fieldShape)
                .background(Palette.Surface2)
                .border(1.dp, Palette.Stroke, fieldShape)
                // Accent gradient outline fading in with the focus.
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(accent.start.copy(alpha = 0.85f * focusGlow), accent.end.copy(alpha = 0.6f * focusGlow)),
                    ),
                    fieldShape,
                )
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedPlatformGlyph(platform, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = pane.urlText,
                onValueChange = { pane.urlText = it },
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp, fontFamily = LocalTokens.current.body),
                cursorBrush = SolidColor(accent.start),
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
                            Text(
                                linkPlaceholder(pane.platform),
                                color = Palette.TextTertiary,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
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
                IconCircleButton(Icons.Rounded.Close, Modifier.padding(start = 6.dp), size = 32.dp, contentDescription = "Effacer") {
                    onReset()
                }
            }
        }
        AnimatedContent(
            targetState = pane.urlText.isBlank(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "linkAction",
        ) { empty ->
            if (empty) {
                PrimaryButton("Coller le lien", Modifier.fillMaxWidth().sheen(enabled), icon = Icons.Rounded.ContentPaste, enabled = enabled, onClick = paste)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconCircleButton(
                        Icons.Rounded.ContentPaste,
                        size = 56.dp,
                        shape = RoundedCornerShape(18.dp),
                        contentDescription = "Coller",
                        onClick = paste,
                    )
                    PrimaryButton(
                        "Analyser",
                        Modifier
                            .weight(1f)
                            .sheen(enabled && !loading),
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

private fun bold(vararg parts: Pair<String, Boolean>): AnnotatedString = buildAnnotatedString {
    parts.forEach { (text, strong) ->
        if (strong) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } else append(text)
    }
}

/** The three steps of the how-to of a tab (bold = words of the buttons to touch). */
internal fun guideSteps(platform: Platform): List<AnnotatedString> {
    val first = when (platform) {
        Platform.INSTAGRAM -> bold("Sur le reel ou la publication, touchez " to false, "Partager" to true, " puis " to false, "Copier le lien" to true, "." to false)
        Platform.OTHER -> bold("Copiez le lien de la vidéo : X, TikTok, Facebook, Snapchat ou un autre site." to false)
        else -> bold("Dans YouTube, touchez " to false, "Partager" to true, " puis " to false, "Copier le lien" to true, "." to false)
    }
    val last = if (platform == Platform.INSTAGRAM) {
        bold("Choisissez ce que vous gardez, puis " to false, "Télécharger" to true, "." to false)
    } else {
        bold("Choisissez la qualité, puis " to false, "Télécharger" to true, "." to false)
    }
    return listOf(first, bold("Revenez ici et touchez " to false, "Coller le lien" to true, "." to false), last)
}

/** Tip shown under the steps. */
internal const val SHARE_TIP = "Plus rapide : dans l'app, Partager → Gulp ouvre le lien ici directement."

/** Note on what a tab cannot download, or null. */
internal fun guideFootnote(platform: Platform): String? = when (platform) {
    Platform.INSTAGRAM ->
        "Publications publiques uniquement : l'app ne se connecte à aucun compte Instagram, pour protéger le vôtre. " +
            "Pas de stories ni de comptes privés."
    Platform.OTHER ->
        "Vidéos publiques uniquement : pas de vidéos d'amis ou de groupes privés sur Facebook, pas de photos " +
            "sur X ni de diaporamas TikTok. Snapchat : liens Spotlight. BeReal : aucun lien public, rien à télécharger."
    else -> null
}

internal data class Feature(
    val text: String,
    val icon: ImageVector? = null,
    val platform: Platform? = null,
    val dimmed: Boolean = false,
)

internal fun guideFeatures(platform: Platform): List<Feature> = when (platform) {
    Platform.INSTAGRAM -> listOf(
        Feature("Reels", Icons.Rounded.PlayArrow),
        Feature("Carrousels", Icons.Rounded.ViewCarousel),
        Feature("Photos", Icons.Rounded.PhotoLibrary),
        Feature("Sans compte", Icons.Rounded.Shield),
    )
    Platform.OTHER -> listOf(
        Feature("X", platform = Platform.TWITTER),
        Feature("TikTok", platform = Platform.TIKTOK),
        Feature("Facebook", platform = Platform.FACEBOOK),
        Feature("Snapchat", platform = Platform.SNAPCHAT),
        Feature("BeReal", platform = Platform.BEREAL, dimmed = true),
        Feature("Tout lien vidéo", Icons.Rounded.Public),
    )
    else -> listOf(
        Feature("Jusqu'en 8K", Icons.Rounded.HighQuality),
        Feature("MP3 · M4A · FLAC", Icons.Rounded.MusicNote),
        Feature("Playlists", Icons.Rounded.VideoLibrary),
        Feature("Sous-titres", Icons.Rounded.Subtitles),
    )
}

/** Numbered step with its number on an accent gradient disc. */
@Composable
private fun ClassicStepRow(number: Int, text: AnnotatedString, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(LocalAccent.current.brush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = Palette.OnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
            )
        }
        Text(text, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicGuide(platform: Platform) {
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(0, key = platform, baseDelay = 120)
                .panel()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Comment faire",
                color = Palette.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                letterSpacing = (-0.3).sp,
            )
            guideSteps(platform).forEachIndexed { index, step ->
                ClassicStepRow(index + 1, step, Modifier.staggeredEntrance(index, key = platform, baseDelay = 200))
            }
            // Hairline fading out from the accent colour.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(accent.start.copy(alpha = 0.4f), Palette.Stroke, Color.Transparent))),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .gradientTint(accent.brush),
                )
                Text(
                    SHARE_TIP,
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            guideFeatures(platform).forEachIndexed { index, chip ->
                FeatureChip(
                    chip.text,
                    Modifier.staggeredEntrance(index, key = platform, baseDelay = 320),
                    icon = chip.icon,
                    platform = chip.platform,
                    dimmed = chip.dimmed,
                )
            }
        }
        guideFootnote(platform)?.let {
            Text(it, color = Palette.TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
