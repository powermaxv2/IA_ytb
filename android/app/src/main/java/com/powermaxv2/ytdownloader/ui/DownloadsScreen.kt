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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.powermaxv2.ytdownloader.core.JobState
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile

@Composable
fun DownloadsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val active = jobs.count { it.isActive }
    val done = jobs.count { it.state == JobState.DONE }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Téléchargements", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                AnimatedContent(targetState = active to done, label = "summary") { (running, finished) ->
                    val finishedText = if (finished > 1) "$finished terminés" else "$finished terminé"
                    Text(
                        when {
                            running > 0 && finished > 0 -> "$running en cours · $finishedText"
                            running > 0 -> "$running en cours"
                            finished > 0 -> "$finishedText · Films, Musique et Images › ${MediaExporter.FOLDER}"
                            else -> "Rien pour l'instant"
                        },
                        color = Palette.TextSecondary,
                        fontSize = 12.5.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            IconCircleButton(Icons.Rounded.Settings, contentDescription = "Réglages", onClick = vm::openSettings)
        }
        UpdateBanner(vm, Modifier.padding(top = 12.dp))
        AnimatedVisibility(
            visible = jobs.any { it.isFinished },
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                SecondaryButton("Effacer", Icons.Rounded.DeleteOutline, compact = true) { vm.downloads.clearFinished() }
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
            Box(
                Modifier
                    .size(96.dp)
                    .pulsing(0.9f, 1.05f, 2000)
                    .clip(CircleShape)
                    .background(LocalAccent.current.start.copy(alpha = 0.12f)),
            )
            Icon(
                Icons.Rounded.Inbox,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(46.dp)
                    .floating(5.dp, 2200)
                    .gradientTint(LocalAccent.current.brush),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Aucun téléchargement", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Vos vidéos arrivent dans Films › YT Downloader, la musique dans Musique et les photos dans Images.",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))
        GradientButton("Coller un lien", icon = Icons.Rounded.PlayArrow, onClick = onStart)
    }
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

    CompositionLocalProvider(LocalAccent provides Accent.of(job.platform)) {
        Column(
            modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 320f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp)) {
                    RemoteImage(
                        job.thumbnail,
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                    )
                    PlatformGlyph(
                        job.platform,
                        Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .offset(5.dp, 5.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(job.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(job.summary, color = Palette.TextTertiary, fontSize = 11.5.sp, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                StateIndicator(job, celebrate)
            }
            AnimatedVisibility(
                visible = job.isActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                GradientProgressBar(if (job.state == JobState.QUEUED) 0f else job.progress, Modifier.padding(top = 2.dp))
            }
            Text(
                job.status,
                color = if (job.state == JobState.FAILED) Palette.Danger else Palette.TextSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (job.state) {
                    JobState.QUEUED, JobState.DOWNLOADING, JobState.PROCESSING ->
                        SecondaryButton("Annuler", Icons.Rounded.Close, compact = true) { vm.downloads.cancel(job.id) }
                    JobState.DONE -> {
                        job.outputs.firstOrNull()?.let { first ->
                            SecondaryButton("Ouvrir", Icons.Rounded.PlayArrow, compact = true) { openFile(context, first, vm) }
                            SecondaryButton("Partager", Icons.Rounded.Share, compact = true) { shareFiles(context, job.outputs, vm) }
                        }
                        SecondaryButton("Retirer", Icons.Rounded.DeleteOutline, compact = true, tint = Palette.TextSecondary) {
                            vm.downloads.remove(job.id)
                        }
                    }
                    JobState.FAILED, JobState.CANCELED -> {
                        SecondaryButton("Réessayer", Icons.Rounded.Refresh, compact = true) { vm.downloads.retry(job.id) }
                        SecondaryButton("Retirer", Icons.Rounded.DeleteOutline, compact = true, tint = Palette.TextSecondary) {
                            vm.downloads.remove(job.id)
                        }
                    }
                }
            }
        }
    }
}

private enum class Indicator { WAITING, ACTIVE, DONE, FAILED, CANCELED }

@Composable
private fun StateIndicator(job: DownloadJob, celebrate: Boolean) {
    val indicator = when (job.state) {
        JobState.QUEUED -> Indicator.WAITING
        JobState.DOWNLOADING, JobState.PROCESSING -> Indicator.ACTIVE
        JobState.DONE -> Indicator.DONE
        JobState.FAILED -> Indicator.FAILED
        JobState.CANCELED -> Indicator.CANCELED
    }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (indicator == Indicator.DONE) SuccessBurst(celebrate, Palette.Success, Modifier.size(44.dp))
        AnimatedContent(
            targetState = indicator,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.3f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.3f) + fadeOut(tween(120)))
            },
            label = "indicator",
        ) { state ->
            when (state) {
                Indicator.WAITING -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(0f, Modifier.size(34.dp))
                    Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(15.dp))
                }
                Indicator.ACTIVE -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(job.progress, Modifier.size(34.dp))
                    if (job.progress >= 0f) {
                        Text("${(job.progress * 100).toInt()}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Indicator.DONE -> AnimatedCheck(Modifier.size(34.dp), animate = celebrate)
                Indicator.FAILED -> Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Palette.Danger.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
                }
                Indicator.CANCELED -> Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
                }
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
