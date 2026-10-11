package com.powermaxv2.ytdownloader.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// Pieces shared by the Découpeur, the Gomme and the Assembleur PDF: the error card, the "done" header with its
// burst, and the intents that open or share what a tool saved.

/** A failure: the message, and the FFmpeg / exception log behind "Détails" when there is one. */
@Composable
internal fun ToolErrorCard(message: String, debug: String? = null, title: String? = null) {
    var details by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                title?.let {
                    Text(if (LocalSkin.current == Skin.TERMINAL) it.uppercase() else it, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                }
                Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        debug?.takeIf { it.isNotBlank() }?.let { log ->
            Spacer(Modifier.height(10.dp))
            Text(
                if (details) "Masquer les détails" else "Détails",
                color = LocalAccent.current.start,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bounceClick { details = !details },
            )
            AnimatedVisibility(details, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(
                    log.lines().takeLast(14).joinToString("\n"),
                    color = Palette.TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Background.copy(alpha = 0.6f))
                        .padding(10.dp),
                )
            }
        }
    }
}

/** Top of a "done" card: the burst, the check drawing itself, the [title] and a [subtitle]. */
@Composable
internal fun DoneHeader(title: String, subtitle: String?) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(104.dp))
            AnimatedCheck(Modifier.size(60.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (terminal) title.uppercase() else title,
            color = Palette.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(if (terminal) "# $it" else it, color = Palette.TextSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Opens [file] in the app the user picks. */
internal fun openSavedFile(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce fichier", success = false) }
}

/** Shares [files] (one or several). */
internal fun shareSavedFiles(context: Context, files: List<SavedFile>, vm: MainViewModel) {
    if (files.isEmpty()) return
    val intent = if (files.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(files.first().mime)
            .putExtra(Intent.EXTRA_STREAM, files.first().uri)
    } else {
        val mimes = files.map { it.mime }.distinct()
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(mimes.singleOrNull() ?: if (mimes.all { it.startsWith("image/") }) "image/*" else "*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(files.map { it.uri }))
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
