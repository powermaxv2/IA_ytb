package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedVisibility
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
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.VideoContainer

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val version by vm.engine.version.collectAsStateWithLifecycle()
    val updating by vm.engine.updating.collectAsStateWithLifecycle()
    val checking by vm.updater.checking.collectAsStateWithLifecycle()

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
        UpdateBanner(vm)

        GroupTitle("Formats par défaut", 0)
        ListCard(0) {
            ChoiceRow(Icons.Rounded.Movie, "Vidéo", settings.container.subtitle) {
                ChoiceChips(VideoContainer.entries, settings.container, { it.title }, { c -> vm.prefs.update { it.copy(container = c) } })
            }
            RowDivider()
            ChoiceRow(Icons.Rounded.MusicNote, "Audio", settings.audioFormat.subtitle) {
                ChoiceChips(AudioFormat.entries, settings.audioFormat, { it.title }, { f -> vm.prefs.update { it.copy(audioFormat = f) } })
            }
        }

        GroupTitle("Fichiers téléchargés", 1)
        ListCard(1) {
            SwitchRow("Miniature dans le fichier", "Pochette visible dans la Galerie et les lecteurs", settings.embedThumbnail) { v ->
                vm.prefs.update { it.copy(embedThumbnail = v) }
            }
            RowDivider()
            SwitchRow("Titre, artiste et date", "Lus par les apps de musique et de vidéo", settings.embedMetadata) { v ->
                vm.prefs.update { it.copy(embedMetadata = v) }
            }
            RowDivider()
            SwitchRow("Chapitres YouTube", null, settings.embedChapters) { v -> vm.prefs.update { it.copy(embedChapters = v) } }
            RowDivider()
            SwitchRow("Sous-titres intégrés", "Quand la vidéo en propose", settings.subtitles) { v -> vm.prefs.update { it.copy(subtitles = v) } }
            AnimatedVisibility(
                visible = settings.subtitles,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .panel(RoundedCornerShape(14.dp), Palette.Surface2)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Langues", color = Palette.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = settings.subtitleLangs,
                        onValueChange = { v -> vm.prefs.update { it.copy(subtitleLangs = v.take(40)) } },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(LocalAccent.current.start),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        GroupTitle("Application", 2)
        ListCard(2) {
            SwitchRow("Notification à la fin", null, settings.notify) { v -> vm.prefs.update { it.copy(notify = v) } }
            RowDivider()
            SwitchRow("Vibrations", "Petit retour quand vous touchez un bouton", settings.haptics) { v -> vm.prefs.update { it.copy(haptics = v) } }
        }

        GroupTitle("Moteur yt-dlp", 3)
        ListCard(3) {
            ValueRow("Version installée", version ?: "…")
            RowDivider()
            SwitchRow("Mise à jour automatique", "Toutes les 12 h, fichier vérifié avant l'installation", settings.autoUpdateYtDlp) { v ->
                vm.prefs.update { it.copy(autoUpdateYtDlp = v) }
            }
            RowDivider()
            ActionArea(
                button = { TonalButton("Mettre à jour yt-dlp", Icons.Rounded.SystemUpdate, Modifier.fillMaxWidth(), loading = updating) { vm.updateYtDlp() } },
                message = vm.updateMessage,
                hint = "En cas d'erreur soudaine (403, « not a bot »…), mettez yt-dlp à jour : les sites changent souvent.",
            )
        }

        GroupTitle("Mises à jour de l'app", 4)
        ListCard(4) {
            ValueRow("Version installée", BuildConfig.VERSION_NAME)
            RowDivider()
            SwitchRow("Chercher les nouvelles versions", "Une notification quand une version sort", settings.autoCheckAppUpdates, vm::setAutoCheckAppUpdates)
            RowDivider()
            ActionArea(
                button = { TonalButton("Rechercher une mise à jour", Icons.Rounded.Refresh, Modifier.fillMaxWidth(), loading = checking) { vm.checkAppUpdate() } },
                message = vm.appUpdateMessage,
                hint = "Les versions viennent du projet GitHub de l'app ; chaque fichier est vérifié (empreinte et signature) avant l'installation.",
            )
        }

        GroupTitle("Où sont les fichiers", 5)
        ListCard(5) {
            ValueRow("Vidéos", "Films › ${MediaExporter.FOLDER}", Icons.Rounded.Movie)
            RowDivider()
            ValueRow("Musique", "Musique › ${MediaExporter.FOLDER}", Icons.Rounded.MusicNote)
            RowDivider()
            ValueRow("Photos", "Images › ${MediaExporter.FOLDER}", Icons.Rounded.Image)
        }

        GroupTitle("Sécurité", 6)
        ListCard(6) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Aucune connexion à un compte (Instagram ou autre) : l'app ne lit que les contenus publics.",
                    "yt-dlp vient de sa page GitHub officielle et doit correspondre à l'empreinte SHA-256 publiée.",
                    "Contrôle à chaque lancement : un yt-dlp modifié est supprimé et remplacé par l'original.",
                    "yt-dlp isolé : aucun fichier de configuration ni plugin externe.",
                    "Seuls les liens https:// sont acceptés ; rien n'est envoyé ailleurs qu'au site du lien.",
                    "Une mise à jour de l'app est refusée si son empreinte ou sa signature ne correspond pas.",
                ).forEach { Bullet(it) }
            }
        }

        GroupTitle("À propos", 7)
        ListCard(7) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("YT Downloader ${BuildConfig.VERSION_NAME} pour Android", color = Color.White, fontSize = 15.sp)
                Text(
                    "Téléchargez uniquement des contenus dont vous détenez les droits ou dont le téléchargement est autorisé, " +
                        "et respectez les conditions d'utilisation de YouTube, d'Instagram et des autres sites.",
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
            Text(title, color = Color.White, fontSize = 15.sp)
            subtitle?.let { Text(it, color = Palette.TextTertiary, fontSize = 12.5.sp, lineHeight = 16.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.start,
                checkedBorderColor = accent.start,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.06f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
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
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
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
                Text(title, color = Color.White, fontSize = 15.sp)
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
            Text(message ?: "", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
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
