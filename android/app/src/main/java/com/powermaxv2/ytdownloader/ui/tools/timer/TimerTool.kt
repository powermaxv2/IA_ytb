package com.powermaxv2.ytdownloader.ui.tools.timer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.timer.Lap
import com.powermaxv2.ytdownloader.core.timer.Stopwatch
import com.powermaxv2.ytdownloader.core.timer.TimerAlarms
import com.powermaxv2.ytdownloader.core.timer.TimerItem
import com.powermaxv2.ytdownloader.core.timer.TimerStore
import com.powermaxv2.ytdownloader.core.timer.Timers
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TIMER_TITLE = "Minuteur et chrono"
const val TIMER_COMMAND = "timer"
const val TIMER_SUBTITLE = "Plusieurs minuteurs avec un nom, et un chrono avec des tours. Ils continuent écran éteint, appli fermée."

/** The timer being set up. */
data class TimerDraft(val name: String = "", val durationMs: Long = 5 * 60_000L)

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class TimerActions(
    val onTab: (watch: Boolean) -> Unit = {},
    val onNew: () -> Unit = {},
    val onDraft: (TimerDraft) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onStart: (Long) -> Unit = {},
    val onPause: (Long) -> Unit = {},
    val onReset: (Long) -> Unit = {},
    val onPlus: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onWatchStart: () -> Unit = {},
    val onWatchPause: () -> Unit = {},
    val onLap: () -> Unit = {},
    val onWatchReset: () -> Unit = {},
    val onCopyLaps: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onAllowExact: () -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class TimerUiState(
    val watchTab: Boolean = false,
    val timers: List<TimerItem> = emptyList(),
    val watch: Stopwatch = Stopwatch(),
    val now: Long = System.currentTimeMillis(),
    val draft: TimerDraft? = null,
    val notificationsOff: Boolean = false,
    val exactOff: Boolean = false,
) {
    /** The set-up card shows when asked, or when there is no timer yet. */
    val showDraft: Boolean get() = draft != null || timers.isEmpty()
    val activeTimers: Int get() = timers.count { it.running }
}

/**
 * Minuteur et chrono (ToolRegistry.TIMER): named countdowns side by side and a stopwatch with laps. Times are kept
 * as clock times in TimerStore, Android's alarms ring the timers (TimerAlarms) and the notifications count on their
 * own, so nothing stops when the screen goes off or Gulp is closed.
 */
@Composable
fun TimerTool(vm: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext
    var timers by remember { mutableStateOf(TimerStore.timers(app)) }
    var watch by remember { mutableStateOf(TimerStore.watch(app)) }
    var watchTab by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<TimerDraft?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notificationsOff by remember { mutableStateOf(!TimerAlarms.canPost(app)) }
    var exactOff by remember { mutableStateOf(!TimerAlarms.exactAllowed(app)) }

    // "Arrêter" / "+1 min" from a notification, or a timer ringing: the receiver writes the preferences.
    DisposableEffect(Unit) {
        val prefs = TimerStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (TimerStore.isTimersKey(key)) timers = TimerStore.timers(app)
            if (TimerStore.isWatchKey(key)) watch = TimerStore.watch(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Opening the tool also repairs the alarms (an alarm lost after a force-stop rings now if it is late).
        TimerAlarms.sync(app, timers)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // The clock of the screen: 30 frames a second for the stopwatch's hundredths, 4 for the timers, none when idle.
    val busy = watch.running || timers.any { it.running }
    LaunchedEffect(busy, watchTab) {
        while (busy) {
            now = System.currentTimeMillis()
            delay(if (watchTab && watch.running) 33 else 250)
        }
        now = System.currentTimeMillis()
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOff = !granted
        if (granted) TimerAlarms.sync(app, timers) else vm.toast("Sans notifications, le minuteur ne sonnera qu'appli ouverte", success = false)
    }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun saveTimers(list: List<TimerItem>) {
        timers = list
        now = System.currentTimeMillis()
        TimerStore.saveTimers(app, list)
    }

    fun saveWatch(w: Stopwatch) {
        watch = w
        now = System.currentTimeMillis()
        TimerStore.saveWatch(app, w)
    }

    val actions = TimerActions(
        onTab = { watchTab = it },
        onNew = { draft = TimerDraft() },
        onDraft = { draft = it.copy(name = it.name.take(Timers.MAX_NAME), durationMs = it.durationMs.coerceIn(0, Timers.MAX_DURATION_MS)) },
        onCreate = {
            val d = draft ?: TimerDraft()
            when {
                d.durationMs < 1000 -> vm.toast("Choisis une durée", success = false)
                timers.size >= Timers.MAX_COUNT -> vm.toast("${Timers.MAX_COUNT} minuteurs au plus : supprimes-en un", success = false)
                else -> {
                    saveTimers(Timers.add(timers, d.name, d.durationMs, System.currentTimeMillis()))
                    draft = null
                    askNotifications()
                }
            }
        },
        onCancel = { draft = null },
        onStart = {
            saveTimers(Timers.start(timers, it, System.currentTimeMillis()))
            askNotifications()
        },
        onPause = { saveTimers(Timers.pause(timers, it, System.currentTimeMillis())) },
        onReset = { saveTimers(Timers.reset(timers, it)) },
        onPlus = { saveTimers(Timers.plusMinute(timers, it, System.currentTimeMillis())) },
        onDelete = { saveTimers(Timers.remove(timers, it)) },
        onWatchStart = {
            saveWatch(Timers.startWatch(watch, System.currentTimeMillis()))
            askNotifications()
        },
        onWatchPause = { saveWatch(Timers.pauseWatch(watch, System.currentTimeMillis())) },
        onLap = { saveWatch(Timers.lap(watch, System.currentTimeMillis())) },
        onWatchReset = { saveWatch(Timers.resetWatch()) },
        onCopyLaps = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Tours", Timers.lapsText(watch)))
            if (Build.VERSION.SDK_INT < 33) vm.toast("Tours copiés")
        },
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !TimerAlarms.canPost(app)) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
        },
        onAllowExact = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) openSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            exactOff = !TimerAlarms.exactAllowed(app)
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = TIMER_TITLE, command = TIMER_COMMAND, subtitle = TIMER_SUBTITLE, scrollState = scroll) {
        TimerContent(TimerUiState(watchTab, timers, watch, now, draft, notificationsOff, exactOff), actions)
    }
}

private fun openSettings(context: Context, action: String) {
    val intent = Intent(action)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .setData(Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.TimerContent(state: TimerUiState, actions: TimerActions) {
    TimerTabs(
        watch = state.watchTab,
        timersBadge = state.activeTimers,
        watchRunning = state.watch.running,
        onChange = actions.onTab,
        modifier = Modifier.staggeredEntrance(0, baseDelay = 40),
    )
    AnimatedContent(
        targetState = state.watchTab,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it * dir / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(160)))
        },
        label = "timerTabContent",
    ) { watchTab ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (watchTab) WatchPage(state, actions) else TimersPage(state, actions)
        }
    }
}

// MARK: - Timers

@Composable
private fun TimersPage(state: TimerUiState, actions: TimerActions) {
    if (state.notificationsOff && state.timers.isNotEmpty()) {
        WarningCard(
            "Notifications coupées",
            "Autorise-les pour que les minuteurs sonnent écran éteint ou appli fermée.",
            "Autoriser",
            actions.onAllowNotifications,
        )
    } else if (state.exactOff && state.timers.any { it.running }) {
        WarningCard(
            "Alarmes à l'heure exacte refusées",
            "Le minuteur peut sonner avec un peu de retard. Autorise « Alarmes et rappels » pour qu'il sonne à la seconde.",
            "Autoriser",
            actions.onAllowExact,
        )
    }

    AnimatedVisibility(
        state.showDraft,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        DraftCard(state.draft ?: TimerDraft(), canCancel = state.timers.isNotEmpty(), actions)
    }

    // Ringing first, then running by end time, then the others in their order.
    val ordered = state.timers.sortedWith(
        compareBy<TimerItem>({ !it.ringing(state.now) }, { !it.running }, { it.endsAt ?: Long.MAX_VALUE }),
    )
    ordered.forEachIndexed { i, timer ->
        key(timer.id) { TimerCard(timer, state.now, i, actions) }
    }

    if (!state.showDraft) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                countText(state.timers.size, state.activeTimers),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (state.timers.size < Timers.MAX_COUNT) MiniAction("Nouveau minuteur", Icons.Rounded.Add, onClick = actions.onNew)
        }
    }
    Hint(
        "Chaque minuteur sonne comme un réveil, même téléphone en veille. Touche « Arrêter » ou « +1 min » dans la notification.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(draft: TimerDraft, canCancel: Boolean, actions: TimerActions) {
    val (h, m, s) = Timers.parts(draft.durationMs)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlockLabel("Nouveau minuteur")
        QrField(
            label = "Nom",
            value = draft.name,
            onChange = { actions.onDraft(draft.copy(name = it)) },
            placeholder = "Pâtes, lessive, four…",
            maxLength = Timers.MAX_NAME,
        )
        if (draft.name.isEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Timers.nameIdeas.forEachIndexed { i, idea -> PickChip(idea, false, i) { actions.onDraft(draft.copy(name = idea)) } }
            }
        }
        BlockLabel("Durée")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Timers.presets.forEachIndexed { i, sec ->
                val ms = sec * 1000L
                PickChip(Timers.words(ms), draft.durationMs == ms, i) { actions.onDraft(draft.copy(durationMs = ms)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DurationColumn(h, "heures", { actions.onDraft(draft.copy(durationMs = Timers.ofParts(it, m, s))) }, max = 23)
            Colon()
            DurationColumn(m, "minutes", { actions.onDraft(draft.copy(durationMs = Timers.ofParts(h, it, s))) }, max = 59)
            Colon()
            DurationColumn(s, "secondes", { actions.onDraft(draft.copy(durationMs = Timers.ofParts(h, m, it))) }, max = 59)
        }
        MainButton(
            "Lancer",
            subtitle = if (draft.durationMs >= 1000) "${draft.name.trim().ifEmpty { "Minuteur" }} · ${Timers.words(draft.durationMs)}" else "Choisis une durée",
            icon = Icons.Rounded.PlayArrow,
            enabled = draft.durationMs >= 1000,
            onClick = actions.onCreate,
        )
        if (canCancel) SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
    }
}

@Composable
private fun Colon() {
    Text(":", color = Palette.TextTertiary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
}

@Composable
private fun TimerCard(timer: TimerItem, now: Long, index: Int, actions: TimerActions) {
    val ringing = timer.ringing(now)
    val left = timer.left(now)
    val pulse = rememberInfiniteTransition(label = "timerCard")
    val beat by pulse.animateFloat(1f, 1.015f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "timerBeat")
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index + 1, key = timer.id, baseDelay = 60)
            .scale(if (ringing) beat else 1f)
            .toolCard()
            .then(if (ringing) Modifier.background(Palette.Danger.copy(alpha = 0.10f)) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CountdownRing(timer.progress(now), timer.running, ringing, Modifier.size(112.dp)) {
            if (ringing) {
                Bell(Modifier.size(40.dp), color = Palette.Danger)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val text = Timers.clock(left)
                    RollingDigits(text, if (text.length > 5) 19.sp else 24.sp, Palette.TextPrimary)
                    Text(
                        skinText(Timers.words(timer.durationMs)),
                        color = Palette.TextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(
                    skinText(timer.name),
                    color = Palette.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    skinText(stateText(timer, now)),
                    color = if (ringing) Palette.Danger else if (timer.running) LocalAccent.current.start else Palette.TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    ringing -> {
                        RoundAction(Icons.Rounded.Stop, "Arrêter", strong = true, danger = true) { actions.onReset(timer.id) }
                        PlusOne { actions.onPlus(timer.id) }
                    }
                    timer.running -> {
                        RoundAction(Icons.Rounded.Pause, "Pause", strong = true) { actions.onPause(timer.id) }
                        PlusOne { actions.onPlus(timer.id) }
                        RoundAction(Icons.Rounded.Refresh, "Remettre à zéro") { actions.onReset(timer.id) }
                    }
                    else -> {
                        RoundAction(Icons.Rounded.PlayArrow, "Lancer", strong = true) { actions.onStart(timer.id) }
                        if (timer.touched(now)) RoundAction(Icons.Rounded.Refresh, "Remettre à zéro") { actions.onReset(timer.id) }
                        RoundAction(Icons.Rounded.DeleteOutline, "Supprimer", danger = true) { actions.onDelete(timer.id) }
                    }
                }
            }
        }
    }
}

/** "+1" in a round button. */
@Composable
private fun PlusOne(onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        RoundAction(Icons.Rounded.Add, "+1 min", onClick = onClick)
        Text("1", color = Palette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 26.dp, top = 22.dp))
    }
}

internal fun stateText(timer: TimerItem, now: Long): String = when {
    timer.ringing(now) -> "Ça sonne !"
    timer.running -> "Sonne à ${hour(timer.endsAt!!)}"
    timer.leftMs != timer.durationMs -> "En pause"
    else -> "Prêt"
}

private fun hour(at: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(at))

internal fun countText(total: Int, running: Int): String = when {
    total == 0 -> "Aucun minuteur"
    running == 0 -> if (total == 1) "1 minuteur" else "$total minuteurs"
    else -> "$total minuteur${if (total > 1) "s" else ""}, $running en cours"
}

@Composable
private fun WarningCard(title: String, text: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.07f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Bell(Modifier.size(28.dp), swing = false, color = Palette.Danger)
        Column(Modifier.weight(1f)) {
            Text(skinText(title), color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        MiniAction(button, Icons.Rounded.NotificationsActive, onClick = onClick)
    }
}

// MARK: - Stopwatch

@Composable
private fun WatchPage(state: TimerUiState, actions: TimerActions) {
    val w = state.watch
    val elapsed = w.elapsed(state.now)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StopwatchDial(elapsed, w.laps, w.running, Modifier.fillMaxWidth(0.86f).aspectRatio(1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val text = Timers.watch(elapsed)
                val main = text.substringBeforeLast('.')
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingDigits(main, if (main.length > 5) 34.sp else 44.sp, Palette.TextPrimary)
                    Text(
                        "." + text.substringAfterLast('.'),
                        color = LocalAccent.current.start,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                val current = w.laps.size + 1
                Text(
                    skinText(
                        when {
                            w.idle -> "Prêt"
                            w.laps.isEmpty() -> if (w.running) "En marche" else "En pause"
                            else -> "Tour $current · ${Timers.watch(elapsed - w.laps.last())}"
                        },
                    ),
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                w.running -> SideButton("Tour", Icons.Rounded.Flag, Modifier.weight(1f), onClick = actions.onLap)
                !w.idle -> SideButton("Remettre à zéro", Icons.Rounded.Refresh, Modifier.weight(1f), onClick = actions.onWatchReset)
            }
            MainButton(
                when {
                    w.running -> "Pause"
                    w.idle -> "Démarrer"
                    else -> "Reprendre"
                },
                modifier = Modifier.weight(1f),
                icon = if (w.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = if (w.running) actions.onWatchPause else actions.onWatchStart,
            )
        }
    }

    val laps = Timers.laps(w)
    if (laps.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(2, baseDelay = 60)
                .toolCard()
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 420f))
                .padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BlockLabel(if (laps.size == 1) "1 tour" else "${laps.size} tours", Modifier.weight(1f))
                MiniAction("Copier", Icons.Rounded.ContentCopy, onClick = actions.onCopyLaps)
            }
            laps.forEach { lap -> key(lap.number) { LapRow(lap, newest = lap.number == laps.first().number) } }
        }
    }
    Hint(
        "Le chrono continue écran éteint ou appli fermée : sa notification compte avec lui.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun LapRow(lap: Lap, newest: Boolean) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val color = when {
        lap.best -> Palette.Success
        lap.worst -> Palette.Danger
        else -> Palette.TextPrimary
    }
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(!newest) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = shown,
        enter = if (newest) expandVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) + scaleIn(initialScale = 0.9f) + fadeIn() else fadeIn(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text("%02d".format(lap.number), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                Timers.watch(lap.splitMs),
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val tag = when {
                lap.best -> "plus rapide"
                lap.worst -> "plus lent"
                else -> null
            }
            tag?.let {
                Text(if (terminal) it.uppercase() else it, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
            }
            Text(Timers.watch(lap.totalMs), color = Palette.TextTertiary, fontSize = 12.5.sp)
        }
    }
}
