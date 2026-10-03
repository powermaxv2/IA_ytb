package com.powermaxv2.ytdownloader.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.MainActivity
import com.powermaxv2.ytdownloader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission") // Every notify() is guarded by canPost() / canUpdate().
object Notifications {
    const val CHANNEL_PROGRESS = "progress"
    const val CHANNEL_DONE = "done"
    const val CHANNEL_UPDATES = "updates"
    const val PROGRESS_ID = 1
    private const val UPDATE_ID = 2

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** A new version is on GitHub: tapping the notification opens the app, which downloads and installs it. */
    fun updateAvailable(context: Context, release: AppRelease) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            UPDATE_ID,
            Intent().setClassName(context, MainActivity.UPDATE_ENTRY)
                .setAction(MainActivity.ACTION_APP_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notes = UpdateNotes.preview(release.notes, maxLines = 6)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Mise à jour disponible : ${release.version}")
            .setContentText("Touchez pour mettre à jour Gulp")
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf("Touchez pour mettre à jour Gulp.", notes).filter { it.isNotBlank() }.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    /** Android's install confirmation, when it could not be shown on screen right away. */
    fun installReady(context: Context, confirm: Intent, version: String?) {
        if (!canPost(context)) return
        val pending = PendingIntent.getActivity(
            context,
            UPDATE_ID + 1,
            confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Mise à jour prête" + (version?.let { " : $it" } ?: ""))
            .setContentText("Touchez pour installer la nouvelle version")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    fun updated(context: Context, version: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Gulp est à jour")
            .setContentText("Version $version installée")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    fun cancelUpdate(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_ID) }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun progress(context: Context, jobs: List<DownloadJob>): Notification {
        val active = jobs.filter { it.isActive }
        val current = active.firstOrNull { it.state != JobState.QUEUED } ?: active.firstOrNull()
        val title = when {
            active.size > 1 -> "${active.size} téléchargements en cours"
            current != null -> current.title
            else -> "Téléchargements terminés"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(current?.status ?: "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(context))
        if (current != null) {
            if (current.progress < 0 || current.state == JobState.QUEUED) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(1000, (current.progress * 1000).toInt(), false)
            }
        }
        return builder.build()
    }

    fun finished(context: Context, job: DownloadJob) {
        if (!canPost(context)) return
        val output = job.outputs.firstOrNull()
        val contentIntent = if (output != null && job.outputs.size == 1) {
            PendingIntent.getActivity(
                context,
                job.id.hashCode(),
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(output.uri, output.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            openAppIntent(context)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Téléchargement terminé")
            .setContentText(job.title)
            .setSubText(output?.location)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(job.id.hashCode(), notification) }
    }
}

/** Keeps downloads running while the app is in the background, with a progress notification. */
@SuppressLint("MissingPermission")
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing = false
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloads = App.instance.downloads
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifications.PROGRESS_ID, Notifications.progress(this, downloads.jobs.value), type)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            scope.launch {
                downloads.jobs.collectLatest { jobs ->
                    if (jobs.none { it.isActive }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (canUpdate() && SystemClock.elapsedRealtime() - lastNotify > 500) {
                        lastNotify = SystemClock.elapsedRealtime()
                        runCatching {
                            NotificationManagerCompat.from(this@DownloadService)
                                .notify(Notifications.PROGRESS_ID, Notifications.progress(this@DownloadService, jobs))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun canUpdate(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Android 15+ allows "dataSync" services 6 h a day: one still running when told to stop makes the app crash. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            }
        }
    }
}
