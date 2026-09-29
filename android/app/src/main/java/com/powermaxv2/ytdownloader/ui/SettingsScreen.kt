package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
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
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.VideoContainer

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val loggedIn by vm.instagram.loggedIn.collectAsStateWithLifecycle()
    val version by vm.engine.version.collectAsStateWithLifecycle()
    val updating by vm.engine.updating.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Réglages", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Section("Instagram", null, index = 0, glyph = Platform.INSTAGRAM) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(loggedIn)
                Text(
                    if (loggedIn) "Compte connecté" else "Non connecté",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "La connexion permet de télécharger les photos, les carrousels, les stories et les reels réservés aux " +
                    "abonnés. Elle se fait sur instagram.com dans l'app : votre mot de passe n'est jamais lu, seuls les " +
                    "cookies de session sont gardés, dans l'espace privé de l'app.",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
            )
            if (loggedIn) {
                SecondaryButton("Se déconnecter", Icons.Rounded.PowerSettingsNew, Modifier.fillMaxWidth()) { vm.logoutInstagram() }
            } else {
                GradientButton("Se connecter à Instagram", Modifier.fillMaxWidth(), icon = Icons.Rounded.Login) {
                    vm.showInstagramLogin = true
                }
            }
        }

        Section("Formats par défaut", Icons.Rounded.Tune, index = 1) {
            SectionLabel("Vidéo")
            ChipRow(VideoContainer.entries, settings.container, { it.title }, { c -> vm.prefs.update { it.copy(container = c) } })
            SectionLabel("Audio")
            ChipRow(AudioFormat.entries, settings.audioFormat, { it.title }, { f -> vm.prefs.update { it.copy(audioFormat = f) } })
        }

        Section("Options", Icons.Rounded.CheckCircle, index = 2) {
            ToggleRow("Miniature intégrée au fichier", settings.embedThumbnail) { v -> vm.prefs.update { it.copy(embedThumbnail = v) } }
            ToggleRow("Titre, artiste et date (métadonnées)", settings.embedMetadata) { v -> vm.prefs.update { it.copy(embedMetadata = v) } }
            ToggleRow("Chapitres YouTube", settings.embedChapters) { v -> vm.prefs.update { it.copy(embedChapters = v) } }
            ToggleRow("Sous-titres intégrés", settings.subtitles) { v -> vm.prefs.update { it.copy(subtitles = v) } }
            AnimatedVisibility(
                visible = settings.subtitles,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Langues", color = Palette.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = settings.subtitleLangs,
                        onValueChange = { v -> vm.prefs.update { it.copy(subtitleLangs = v.take(40)) } },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent.start),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ToggleRow("Notification à la fin", settings.notify) { v -> vm.prefs.update { it.copy(notify = v) } }
            ToggleRow("Vibrations", settings.haptics) { v -> vm.prefs.update { it.copy(haptics = v) } }
        }

        Section("Moteur yt-dlp", Icons.Rounded.Bolt, index = 3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version installée", color = Palette.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(version ?: "…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            ToggleRow("Mise à jour automatique (vérifiée)", settings.autoUpdateYtDlp) { v -> vm.prefs.update { it.copy(autoUpdateYtDlp = v) } }
            GradientButton(
                "Mettre à jour yt-dlp",
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.SystemUpdate,
                loading = updating,
            ) { vm.updateYtDlp() }
            AnimatedVisibility(visible = vm.updateMessage != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(vm.updateMessage ?: "", color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
            Text(
                "En cas d'erreur soudaine (403, « not a bot »…), mettez à jour yt-dlp : les sites changent souvent.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }

        Section("Sécurité", Icons.Rounded.Shield, index = 4) {
            listOf(
                "Mises à jour de yt-dlp téléchargées depuis sa page GitHub officielle et comparées à l'empreinte SHA-256 publiée : un fichier non conforme est refusé.",
                "Contrôle d'intégrité à chaque lancement : un yt-dlp modifié est supprimé et remplacé par la version d'origine.",
                "yt-dlp isolé : aucun fichier de configuration ni plugin externe (--ignore-config, --no-plugin-dirs).",
                "Seuls les liens https:// sont acceptés ; aucune donnée n'est envoyée ailleurs qu'au site du lien.",
                "Cookies Instagram gardés dans l'espace privé de l'app, jamais sauvegardés ni partagés.",
            ).forEach { Bullet(it) }
        }

        Section("Emplacement des fichiers", Icons.Rounded.FolderOpen, index = 5) {
            Bullet("Vidéos : Films › YT Downloader")
            Bullet("Musique : Musique › YT Downloader")
            Bullet("Photos : Images › YT Downloader")
            Text(
                "Ils apparaissent dans la Galerie, Google Photos et les apps de musique.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }

        Section("À propos", Icons.Rounded.Info, index = 6) {
            Text("YT Downloader ${BuildConfig.VERSION_NAME} pour Android", color = Color.White, fontSize = 13.sp)
            Text(
                "Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé, " +
                    "et respectez les conditions d'utilisation de YouTube et d'Instagram.",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector?,
    index: Int,
    glyph: Platform? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index)
            .glass()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.start.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (glyph != null) {
                    PlatformGlyph(glyph, Modifier.size(18.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).gradientTint(accent.brush))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.start,
                checkedBorderColor = accent.start,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                uncheckedBorderColor = Palette.Stroke,
            ),
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(LocalAccent.current.start),
        )
        Text(text, color = Palette.TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
    }
}

/** Green dot with a ripple ring when connected, grey when not. */
@Composable
private fun StatusDot(active: Boolean) {
    val color by animateColorAsState(if (active) Palette.Success else Palette.TextTertiary, label = "dot")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(18.dp)
                    .pulsing(0.5f, 1.2f, 1100)
                    .clip(CircleShape)
                    .border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
