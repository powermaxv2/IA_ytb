package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.brand.GulpMarkStatic
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.tokensFor
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.BuildConfig

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            ScreenTitle("Réglages", null, Modifier.weight(1f))
            IconCircleButton(Icons.Rounded.Close, contentDescription = "Fermer", onClick = vm::closeSettings)
        }

        SettingsInterfaceSection(settings.skin) { s -> vm.prefs.update { it.copy(skin = s) } }

        GroupTitle("Application", 1)
        ListCard(1) {
            SwitchRow("Vibrations", "Petit retour quand vous touchez un bouton", settings.haptics) { v -> vm.prefs.update { it.copy(haptics = v) } }
        }

        GroupTitle("Confidentialité", 2)
        ListCard(2) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Vos fichiers restent sur le téléphone : les outils travaillent hors ligne, sans compte.",
                    "Gulp ne contient ni publicité ni outil de suivi.",
                    "Le seul téléchargement est le modèle de reconnaissance vocale de « Vocaux en texte », à votre demande.",
                ).forEach { Bullet(it) }
            }
        }

        GroupTitle("À propos", 3)
        ListCard(3) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gulp ${BuildConfig.VERSION_NAME} pour Android", color = Palette.TextPrimary, fontSize = 15.sp)
                Text(
                    "Une boîte à outils pour vos photos, vidéos, sons, PDF et messages. " +
                        "Logiciel libre sous licence GPL-3.0.",
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GroupTitle(text: String, index: Int) {
    Text(
        text,
        color = Palette.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(start = 6.dp, top = 8.dp)
            .staggeredEntrance(index),
    )
}

@Composable
private fun ListCard(index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index)
            .panel(RoundedCornerShape(22.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Stroke),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.TextPrimary, fontSize = 15.sp)
            subtitle?.let { Text(it, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 16.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = accent.start,
                checkedBorderColor = accent.start,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.TextPrimary.copy(alpha = 0.06f),
                uncheckedBorderColor = Palette.TextPrimary.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (icon != null) 11.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            IconBadge(it, size = 34.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = Palette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Palette.TextPrimary, fontSize = 15.sp)
                Text(subtitle, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        content()
    }
}

/** Button of a list card, with the result of the action under it and a short explanation. */
@Composable
private fun ActionArea(button: @Composable () -> Unit, message: String?, hint: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        button()
        AnimatedVisibility(visible = message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(message ?: "", color = Palette.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text(hint, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(LocalAccent.current.start),
        )
        Text(text, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

// MARK: - Interface (skins)

/** "Interface" group of the settings: title and skin picker (stateless, also used by the screenshot tests). */
@Composable
internal fun SettingsInterfaceSection(current: Skin, onSelect: (Skin) -> Unit) {
    GroupTitle("Interface", 0)
    SkinPicker(current, onSelect)
}

/** The three skins side by side, each drawn as a miniature of its own look. */
@Composable
private fun SkinPicker(current: Skin, onSelect: (Skin) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Skin.entries.forEach { skin ->
            SkinOption(skin, skin == current, Modifier.weight(1f)) { onSelect(skin) }
        }
    }
}

@Composable
private fun SkinOption(skin: Skin, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val scale by animateFloatAsState(if (selected) 1f else 0.95f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "skinScale")
    val outline by animateColorAsState(if (selected) accent.start else Palette.Stroke, tween(250), label = "skinOutline")
    val outlineWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(250), label = "skinOutlineWidth")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "skinCheck")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(shape)
                .clickable {
                    view.tick(haptics)
                    onClick()
                }
                .border(outlineWidth, outline, shape),
        ) {
            SkinPreview(skin, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                        alpha = check.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(accent.start),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            skin.title,
            color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            skin.subtitle,
            color = Palette.TextTertiary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Miniature of a skin with its own colours and fonts: the Gulp mark as that skin draws it, a title, a card and a
 * button in its YouTube accent.
 */
@Composable
private fun SkinPreview(skin: Skin, modifier: Modifier = Modifier) {
    val tokens = tokensFor(skin)
    val accent = tokens.youtubeAccent
    val cardShape = RoundedCornerShape(minOf(tokens.cardRadius, 10.dp))
    // Brand tile on Gulp and Papier, orange outline on Terminal.
    val markStyle = if (skin == Skin.TERMINAL) GulpStyle.outline(accent.start) else GulpStyle.Brand
    val glow = accent.start.copy(alpha = 0.3f)
    Column(
        modifier
            .background(tokens.background)
            .drawBehind {
                // Gulp: the warm glow of its background.
                if (skin == Skin.CLASSIC) {
                    drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(size.width * 0.3f, 0f), size.width))
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GulpMarkStatic(17.dp, style = markStyle)
            Text(
                if (skin == Skin.TERMINAL) "YT_" else "Aa",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontFamily = tokens.display,
                // Archivo Black has a single weight: asking for a bolder one would fake-bold it.
                fontWeight = if (skin == Skin.TERMINAL) FontWeight.Normal else FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(cardShape)
                .background(tokens.surface)
                .border(1.dp, tokens.stroke, cardShape)
                .padding(horizontal = 7.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(minOf(tokens.chipRadius, 3.dp)))
                    .background(tokens.textPrimary.copy(alpha = 0.75f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(minOf(tokens.chipRadius, 3.dp)))
                    .background(tokens.textTertiary),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(minOf(tokens.buttonRadius, 8.dp)))
                .background(accent.horizontal()),
        )
    }
}
