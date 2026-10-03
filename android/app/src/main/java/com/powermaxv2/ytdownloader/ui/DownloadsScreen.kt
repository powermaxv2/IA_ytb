package com.powermaxv2.ytdownloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.DownloadJob
import com.powermaxv2.ytdownloader.core.Fmt
import com.powermaxv2.ytdownloader.core.JobState
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens

@Composable
fun DownloadsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val active = jobs.count { it.isActive }
    val done = jobs.count { it.state == JobState.DONE }
    val finishedText = if (done > 1) "$done terminés" else "$done terminé"
    val summary = when {
        active > 0 && done > 0 -> "$active en cours · $finishedText"
        active > 0 -> "$active en cours"
        done > 0 -> finishedText
        else -> "Rien pour l'instant"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            ScreenTitle("Téléchargements", summary, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = vm::openSettings)
        }
        UpdateBanner(vm, Modifier.padding(top = 14.dp))
        AnimatedVisibility(
            visible = jobs.any { it.isFinished },
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Films, Musique et Images › ${MediaExporter.FOLDER}",
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                TonalButton("Effacer", Icons.Rounded.DeleteOutline, compact = true) { vm.downloads.clearFinished() }
            }
        }
        AnimatedContent(
            targetState = jobs.isEmpty(),
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "downloadsContent",
            modifier = Modifier.weight(1f),
        ) { empty ->
            if (empty) {
                EmptyDownloads(vm::openLinkTab)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(jobs, key = { it.id }) { job ->
                        DownloadRow(job, vm, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloads(onStart: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(accent.tint(0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Inbox,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .gradientTint(accent.brush),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Aucun téléchargement", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Les vidéos arrivent dans Films › YT Downloader, la musique dans Musique et les photos dans Images.",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Coller un lien", icon = Icons.Rounded.ContentPaste, onClick = onStart)
    }
}

/** "Movies/YT Downloader" → "Films › YT Downloader": the names the phone's apps show. */
private fun SavedFile.place(): String {
    val top = location.substringBefore('/')
    val name = when (top) {
        "Movies" -> "Films"
        "Music" -> "Musique"
        "Pictures" -> "Images"
        "Download" -> "Téléchargements"
        else -> top
    }
    return "$name › ${MediaExporter.FOLDER}"
}

private fun DownloadJob.savedText(): String {
    val first = outputs.firstOrNull() ?: return status
    val size = Fmt.bytes(outputs.sumOf { it.size }.toDouble())
    val count = if (outputs.size > 1) " · ${outputs.size} fichiers" else ""
    return "Enregistré dans ${first.place()} · $size$count"
}

@Composable
private fun DownloadRow(job: DownloadJob, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var celebrate by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(job.state) }
    LaunchedEffect(job.state) {
        if (previous != JobState.DONE && job.state == JobState.DONE) celebrate = true
        previous = job.state
    }

    CompositionLocalProvider(LocalAccent provides LocalTokens.current.accentFor(job.platform)) {
        Column(
            modifier
                .fillMaxWidth()
                .panel(RoundedCornerShape(22.dp))
                .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 320f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row {
                Box(Modifier.size(72.dp)) {
                    RemoteImage(
                        job.thumbnail,
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.Surface2),
                    )
                    PlatformGlyph(
                        job.platform,
                        Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .offset(5.dp, 5.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        job.title,
                        color = Palette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(job.summary, color = Palette.TextTertiary, fontSize = 12.5.sp, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                StateBadge(job.state, celebrate)
            }
            AnimatedVisibility(
                visible = job.isActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ProgressBar(if (job.state == JobState.QUEUED) 0f else job.progress, Modifier.padding(top = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (job.state == JobState.DONE) job.savedText() else job.status,
                    color = when (job.state) {
                        JobState.FAILED -> Palette.Danger
                        JobState.DONE -> Palette.Success
                        else -> Palette.TextSecondary
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (job.isActive) {
                    Spacer(Modifier.width(10.dp))
                    TonalButton("Annuler", Icons.Rounded.Close, compact = true) { vm.downloads.cancel(job.id) }
                }
            }
            if (job.isFinished) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (job.state == JobState.DONE) {
                        job.outputs.firstOrNull()?.let { first ->
                            TonalButton("Ouvrir", Icons.Rounded.PlayArrow, compact = true) { openFile(context, first, vm) }
                            TonalButton("Partager", Icons.Rounded.Share, compact = true) { shareFiles(context, job.outputs, vm) }
                        }
                    } else {
                        TonalButton("Réessayer", Icons.Rounded.Refresh, compact = true) { vm.downloads.retry(job.id) }
                    }
                    Spacer(Modifier.weight(1f))
                    IconCircleButton(
                        Icons.Rounded.DeleteOutline,
                        size = 36.dp,
                        shape = RoundedCornerShape(12.dp),
                        tint = Palette.TextSecondary,
                        contentDescription = "Retirer de la liste",
                    ) { vm.downloads.remove(job.id) }
                }
            }
        }
    }
}

/** Finished (green tick), failed (red !) or cancelled (grey ✕); nothing while the download runs. */
@Composable
private fun StateBadge(state: JobState, celebrate: Boolean) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (state == JobState.DONE) SuccessBurst(celebrate, Palette.Success, Modifier.size(30.dp))
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.3f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.3f) + fadeOut(tween(120)))
            },
            label = "stateBadge",
        ) { current ->
            when (current) {
                JobState.DONE -> AnimatedCheck(Modifier.size(28.dp), animate = celebrate)
                JobState.FAILED -> Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Palette.Danger.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(18.dp))
                }
                JobState.CANCELED -> Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Palette.TextPrimary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(16.dp))
                }
                else -> Spacer(Modifier.size(28.dp))
            }
        }
    }
}

private fun openFile(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce fichier", success = false) }
}

private fun shareFiles(context: Context, files: List<SavedFile>, vm: MainViewModel) {
    val intent = if (files.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(files.first().mime)
            .putExtra(Intent.EXTRA_STREAM, files.first().uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(files.map { it.uri }))
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
