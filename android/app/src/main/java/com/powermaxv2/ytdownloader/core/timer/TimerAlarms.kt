package com.powermaxv2.ytdownloader.core.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.MainActivity
import com.powermaxv2.ytdownloader.R

// Minuteur et chrono, the Android part: the timers live in their own preferences (read by the screen and by the
// receiver alike), each running timer has an alarm in AlarmManager that wakes the phone at its end, and the
// notifications show what runs (a live countdown drawn by Android itself) and ring when a timer reaches zero.
// No service: Android keeps the alarms and the notifications while Gulp is closed and the screen is off.

/** The timers and the stopwatch, saved in the "timers" preferences. */
object TimerStore {
    private const val PREFS = "timers"
    private const val KEY_LIST = "list"
    private const val KEY_WATCH = "watch"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun timers(context: Context): List<TimerItem> = Timers.decode(prefs(context).getString(KEY_LIST, null))

    fun watch(context: Context): Stopwatch = Timers.decodeWatch(prefs(context).getString(KEY_WATCH, null))

    /** Saves [list], then puts the alarms and the notifications in line with it. */
    fun saveTimers(context: Context, list: List<TimerItem>) {
        prefs(context).edit().putString(KEY_LIST, Timers.encode(list)).apply()
        TimerAlarms.sync(context, list)
    }

    fun saveWatch(context: Context, watch: Stopwatch) {
        prefs(context).edit().putString(KEY_WATCH, Timers.encodeWatch(watch)).apply()
        TimerAlarms.showWatch(context, watch)
    }

    fun isTimersKey(key: String?) = key == KEY_LIST || key == null
    fun isWatchKey(key: String?) = key == KEY_WATCH || key == null
}

@SuppressLint("MissingPermission") // Every notify() is guarded by canPost().
object TimerAlarms {
    const val ACTION_RING = "com.powermaxv2.ytdownloader.timer.RING"
    const val ACTION_STOP = "com.powermaxv2.ytdownloader.timer.STOP"
    const val ACTION_PLUS = "com.powermaxv2.ytdownloader.timer.PLUS"
    const val ACTION_PAUSE_WATCH = "com.powermaxv2.ytdownloader.timer.PAUSE_WATCH"
    const val EXTRA_ID = "timerId"

    private const val CHANNEL_RUNNING = "timers_running"
    private const val CHANNEL_RING = "timers_ring"

    // Notification ids: 2 is the update one; timers use 3000 + id, the stopwatch 2999.
    private const val WATCH_NOTIFICATION = 2_999
    private fun notificationId(id: Long) = 3_000 + id.toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Minuteurs en cours", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Minuteur terminé", NotificationManager.IMPORTANCE_HIGH).apply {
                val alarm = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(alarmSound(), alarm)
                enableVibration(true)
                vibrationPattern = VIBRATION
                setBypassDnd(true)
            },
        )
    }

    private val VIBRATION = longArrayOf(0, 600, 400, 600, 400, 600)

    private fun alarmSound() =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** Notifications are allowed (Android 13+ asks for them). */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Play Store version: no exact-alarm permission is declared, and none is needed: [AlarmManager.setAlarmClock]
     * (what [schedule] uses) rings to the second on every Android version.
     */
    @Suppress("UNUSED_PARAMETER")
    fun exactAllowed(context: Context): Boolean = true

    /** One alarm per running timer, one notification per running or ringing timer; the rest is cleared. */
    fun sync(context: Context, list: List<TimerItem>, now: Long = System.currentTimeMillis()) {
        createChannels(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = NotificationManagerCompat.from(context)
        val shown = runCatching { activeIds(context) }.getOrDefault(emptySet())
        val live = list.map { notificationId(it.id) }.toSet()
        // Timers deleted since: their notifications go.
        shown.filter { it >= 3_000 && it !in live }.forEach { runCatching { notifications.cancel(it) } }
        list.forEach { timer ->
            val alarm = ringIntent(context, timer.id)
            when {
                timer.ringing(now) -> {
                    alarms?.cancel(alarm)
                    // The receiver posts the ringing one; after a reboot or a missed alarm, post it here.
                    if (!ringingShown(context, timer.id)) ring(context, timer)
                }
                timer.running -> {
                    schedule(context, alarms, timer.endsAt!!, alarm)
                    showRunning(context, timer)
                }
                else -> {
                    alarms?.cancel(alarm)
                    runCatching { notifications.cancel(notificationId(timer.id)) }
                }
            }
        }
    }

    private fun activeIds(context: Context): Set<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptySet()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return emptySet()
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun ringingShown(context: Context, id: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.activeNotifications.any { it.id == notificationId(id) && it.notification.extras.getBoolean(EXTRA_RINGING) }
    }

    private const val EXTRA_RINGING = "gulpTimerRinging"

    private fun schedule(context: Context, alarms: AlarmManager?, at: Long, alarm: PendingIntent) {
        alarms ?: return
        runCatching {
            if (exactAllowed(context)) {
                // An alarm clock: wakes the phone from Doze to the second, and Android shows the alarm icon.
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openIntent(context, 0)), alarm)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, at, alarm)
            }
        }.onFailure {
            // SecurityException when exact alarms were withdrawn meanwhile: fall back to an inexact one.
            runCatching { alarms.set(AlarmManager.RTC_WAKEUP, at, alarm) }
        }
    }

    private fun ringIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, TimerReceiver::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(context: Context, action: String, id: Long, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, TimerReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context,
        10_000 + code,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TIMERS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A quiet notification whose countdown Android updates itself, every second, app closed. */
    private fun showRunning(context: Context, timer: TimerItem) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(timer.name)
            .setContentText("Minuteur de ${Timers.words(timer.durationMs)}")
            .setWhen(timer.endsAt!!)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openIntent(context, timer.id.toInt()))
            .addAction(0, "+1 min", actionIntent(context, ACTION_PLUS, timer.id, 20_000 + timer.id.toInt()))
            .addAction(0, "Arrêter", actionIntent(context, ACTION_STOP, timer.id, 30_000 + timer.id.toInt()))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(timer.id), n) }
    }

    /** The timer is over: an alarm-sound notification that keeps ringing until "Arrêter" or "+1 min". */
    fun ring(context: Context, timer: TimerItem) {
        if (!canPost(context)) return
        createChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("${timer.name} : c'est l'heure !")
            .setContentText("Minuteur de ${Timers.words(timer.durationMs)} terminé")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(alarmSound(), android.media.AudioManager.STREAM_ALARM)
            .setVibrate(VIBRATION)
            .setContentIntent(openIntent(context, timer.id.toInt()))
            .setDeleteIntent(actionIntent(context, ACTION_STOP, timer.id, 40_000 + timer.id.toInt()))
            .addAction(0, "Arrêter", actionIntent(context, ACTION_STOP, timer.id, 30_000 + timer.id.toInt()))
            .addAction(0, "+1 min", actionIntent(context, ACTION_PLUS, timer.id, 20_000 + timer.id.toInt()))
            .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_RINGING, true) })
        val n = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(timer.id), n) }
    }

    /** The stopwatch keeps counting in its notification while it runs. */
    fun showWatch(context: Context, watch: Stopwatch, now: Long = System.currentTimeMillis()) {
        val notifications = NotificationManagerCompat.from(context)
        if (!watch.running || !canPost(context)) {
            runCatching { notifications.cancel(WATCH_NOTIFICATION) }
            return
        }
        createChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Chronomètre")
            .setContentText(if (watch.laps.isEmpty()) "En marche" else "Tour ${watch.laps.size + 1}")
            .setWhen(now - watch.elapsed(now))
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openIntent(context, 0))
            .addAction(0, "Pause", actionIntent(context, ACTION_PAUSE_WATCH, 0, 50_000))
            .build()
        runCatching { notifications.notify(WATCH_NOTIFICATION, n) }
    }
}

/** Rings a timer at its end, handles "Arrêter" / "+1 min" / "Pause" from the notifications, and puts the alarms back after a reboot. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val id = intent.getLongExtra(TimerAlarms.EXTRA_ID, -1)
        val list = TimerStore.timers(context)
        when (intent.action) {
            TimerAlarms.ACTION_RING -> list.firstOrNull { it.id == id && it.ringing(now) }?.let { TimerAlarms.ring(context, it) }
            TimerAlarms.ACTION_STOP -> TimerStore.saveTimers(context, Timers.reset(list, id))
            TimerAlarms.ACTION_PLUS -> TimerStore.saveTimers(context, Timers.plusMinute(list, id, now))
            TimerAlarms.ACTION_PAUSE_WATCH -> TimerStore.saveWatch(context, Timers.pauseWatch(TimerStore.watch(context), now))
            // Reboot or app update: Android forgot the alarms; a timer that ended meanwhile rings now.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                if (list.isNotEmpty()) TimerAlarms.sync(context, list, now)
                TimerAlarms.showWatch(context, TimerStore.watch(context), now)
            }
        }
    }
}
