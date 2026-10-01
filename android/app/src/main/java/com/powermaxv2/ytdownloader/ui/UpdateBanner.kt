package com.powermaxv2.ytdownloader.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.AppUpdateState
import com.powermaxv2.ytdownloader.core.UpdateNotes

/** "Mise à jour disponible" card at the top of the screens, from the announcement to the installation. */
@Composable
fun UpdateBanner(vm: MainViewModel, modifier: Modifier = Modifier) {
    val state by vm.updater.state.collectAsStateWithLifecycle()
    val visible = when (val s = state) {
        AppUpdateState.None -> false
        is AppUpdateState.Available -> vm.dismissedUpdate != s.release.version
        else -> true
    }
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val current = state
        if (current !is AppUpdateState.None) UpdateCard(current, vm)
    }
}

@Composable
private fun UpdateCard(state: AppUpdateState, vm: MainViewModel) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val (title, subtitle) = when (state) {
        is AppUpdateState.Available -> "Mise à jour ${state.release.version} disponible" to "Vous avez la version ${vm.updater.currentVersion}"
        is AppUpdateState.Downloading -> "Téléchargement de la ${state.release.version}…" to "${(state.progress * 100).toInt()} %"
        is AppUpdateState.NeedsPermission -> "Une autorisation, une seule fois" to "Pour installer la ${state.release.version}"
        is AppUpdateState.Installing -> "Installation de la ${state.release.version}…" to "Confirmez si Android le demande"
        is AppUpdateState.Failed -> "Mise à jour ${state.release.version} interrompue" to "Vous pouvez réessayer"
        AppUpdateState.None -> return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), accent.start.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (state is AppUpdateState.Available) Modifier.floating(2.dp, 1800) else Modifier),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Palette.TextSecondary, fontSize = 12.sp)
            }
            if (state is AppUpdateState.Available) {
                IconCircleButton(Icons.Rounded.Close, size = 28.dp, contentDescription = "Plus tard") {
                    vm.dismissAppUpdate(state.release.version)
                }
            }
        }
        when (state) {
            is AppUpdateState.Available -> {
                val notes = UpdateNotes.preview(state.release.notes, maxLines = 5)
                if (notes.isNotBlank()) {
                    Text(notes, color = Palette.TextSecondary, fontSize = 12.5.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                GradientButton("Mettre à jour", Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdate) { vm.startAppUpdate() }
            }
            is AppUpdateState.Downloading -> GradientProgressBar(state.progress)
            is AppUpdateState.NeedsPermission -> {
                Text(
                    "Android doit autoriser YT Downloader à installer ses propres mises à jour. Activez " +
                        "« Autoriser cette source », puis revenez : l'installation reprend toute seule.",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                )
                GradientButton("Autoriser", Modifier.fillMaxWidth(), icon = Icons.Rounded.Security) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
            is AppUpdateState.Installing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = accent.start, strokeWidth = 2.dp)
                Text("L'app va redémarrer dans sa nouvelle version.", color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
            is AppUpdateState.Failed -> {
                Text(state.message, color = Palette.Danger, fontSize = 12.5.sp)
                SecondaryButton("Réessayer", Icons.Rounded.Refresh, compact = true) { vm.startAppUpdate() }
            }
            AppUpdateState.None -> Unit
        }
    }
}
