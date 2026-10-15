package com.powermaxv2.ytdownloader.ui.tools.level

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.level.Calibration
import com.powermaxv2.ytdownloader.core.level.Gravity
import com.powermaxv2.ytdownloader.core.level.LevelMath
import com.powermaxv2.ytdownloader.core.level.LevelMode
import com.powermaxv2.ytdownloader.core.level.LevelReading
import com.powermaxv2.ytdownloader.core.level.LevelSensor
import com.powermaxv2.ytdownloader.ui.LocalHaptics
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import kotlinx.coroutines.delay

const val LEVEL_TITLE = "Niveau à bulle"
const val LEVEL_COMMAND = "level"
const val LEVEL_SUBTITLE = "Pose le téléphone sur l'étagère, ou appuie sa tranche contre le cadre : la bulle dit si c'est droit."

/** Where the sensor stands. */
enum class SensorStatus {
    /** Registered, no value yet. */
    STARTING,
    READY,

    /** The phone has no accelerometer nor gravity sensor. */
    MISSING,

    /** A sensor exists but sent nothing (broken, or blocked by the system). */
    SILENT,
}

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class LevelActions(
    val onMode: (LevelMode) -> Unit = {},
    val onFreeze: () -> Unit = {},
    val onCalibrate: () -> Unit = {},
    val onResetCalibration: () -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class LevelUiState(
    val status: SensorStatus = SensorStatus.STARTING,
    val mode: LevelMode = LevelMode.AUTO,
    /** The measure shown (the held one while [frozen]). */
    val reading: LevelReading? = null,
    val frozen: Boolean = false,
    val calibrated: Boolean = false,
    /** "capteur de gravité" or "accéléromètre". */
    val sensorKind: String? = null,
) {
    val working: Boolean get() = status == SensorStatus.STARTING || status == SensorStatus.READY
}

/**
 * Niveau à bulle (ToolRegistry.LEVEL): the gravity sensor (or the accelerometer) turned into a spirit level. Lying
 * flat, a round vial; standing on an edge, a tube (Auto picks which). The screen stays on, the phone ticks once when
 * it becomes straight, the measure can be held to read it afterwards, and "Calibrer" sets the zero (a camera bump
 * keeps a phone from lying flat). Nothing is saved but the mode and the zero.
 */
@Composable
fun LevelTool(vm: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val sensor = remember { LevelSensor(context.applicationContext) }
    var mode by remember { mutableStateOf(LevelMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: LevelMode.AUTO) }
    var calibration by remember { mutableStateOf(Calibration.decode(prefs.getString(KEY_CALIBRATION, null))) }
    var status by remember { mutableStateOf(if (sensor.available) SensorStatus.STARTING else SensorStatus.MISSING) }
    var gravity by remember { mutableStateOf<Gravity?>(null) }
    var resolved by remember { mutableStateOf<LevelMode?>(null) }
    var held by remember { mutableStateOf<LevelReading?>(null) }

    // The display turns with the phone (auto-rotate), the sensor does not: bring the values into screen axes.
    val configuration = LocalConfiguration.current
    val rotation = remember(configuration) {
        (view.display?.rotation ?: 0) * 90
    }
    val currentRotation by rememberUpdatedState(rotation)
    val currentMode by rememberUpdatedState(mode)

    // Listen only while Gulp is on screen (the sensor costs battery).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, sensor) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val started = sensor.start { raw ->
                        val g = LevelMath.toScreen(raw, currentRotation)
                        resolved = LevelMath.resolve(currentMode, g, resolved)
                        gravity = g
                        status = SensorStatus.READY
                    }
                    if (!started && status != SensorStatus.MISSING) status = SensorStatus.SILENT
                }
                Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            sensor.stop()
        }
    }
    LaunchedEffect(status) {
        if (status == SensorStatus.STARTING) {
            delay(2_500)
            if (status == SensorStatus.STARTING) status = SensorStatus.SILENT
        }
    }
    // A level is read from afar: keep the screen on while the tool is open.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val g = gravity
    val active = resolved ?: if (mode == LevelMode.AUTO) LevelMode.FLAT else mode
    val live = g?.let { LevelMath.read(it, active, calibration) }
    val shown = held ?: live

    // One tick the moment it becomes straight.
    val straight = live?.level == true && held == null
    LaunchedEffect(straight) {
        if (straight && haptics) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    val actions = LevelActions(
        onMode = {
            mode = it
            resolved = if (it == LevelMode.AUTO) null else it
            held = null
            prefs.edit().putString(KEY_MODE, it.name).apply()
        },
        onFreeze = { held = if (held == null) live else null },
        onCalibrate = {
            val next = g?.let { LevelMath.calibrate(it, active, calibration) }
            if (next == null) {
                vm.toast("Trop penché pour servir de zéro : pose le téléphone sur une surface bien droite.", success = false)
            } else {
                calibration = next
                held = null
                prefs.edit().putString(KEY_CALIBRATION, next.encode()).apply()
                vm.toast("Zéro réglé")
            }
        },
        onResetCalibration = {
            calibration = Calibration()
            prefs.edit().remove(KEY_CALIBRATION).apply()
            vm.toast("Zéro d'usine remis")
        },
    )

    val state = LevelUiState(
        status = status,
        mode = mode,
        reading = shown,
        frozen = held != null,
        calibrated = calibration.isSet,
        sensorKind = if (sensor.available) sensor.kind else null,
    )
    ToolScaffold(vm, title = LEVEL_TITLE, command = LEVEL_COMMAND, subtitle = LEVEL_SUBTITLE) {
        LevelContent(state, actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.LevelContent(state: LevelUiState, actions: LevelActions) {
    if (!state.working) {
        NoSensorCard(state.status, Modifier.staggeredEntrance(0, baseDelay = 40))
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Comment tu le poses")
        OptionChips(LevelMode.entries, state.mode, ::modeLabel, actions.onMode)
    }

    // The vial, the hero of the screen: round when flat, a tube on an edge.
    val edge = state.reading is LevelReading.Edge || (state.reading == null && state.mode == LevelMode.EDGE)
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            (if (edge) "Sur la tranche" else "À plat").uppercase(),
            color = Palette.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = edge,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.62f, stiffness = 300f), initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(140)) + scaleOut(targetScale = 0.9f))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
            label = "vial",
        ) { onEdge ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (onEdge) EdgeVial(state.reading as? LevelReading.Edge) else FlatVial(state.reading as? LevelReading.Flat)
            }
        }
        Spacer(Modifier.height(18.dp))
        LevelReadout(state.reading, state.frozen)
    }

    MainButton(
        if (state.frozen) "Reprendre la mesure" else "Figer la mesure",
        icon = if (state.frozen) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
        subtitle = if (state.frozen) "La bulle bouge de nouveau" else "Pour lire l'angle une fois le téléphone repris",
        enabled = state.reading != null,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 80),
        onClick = actions.onFreeze,
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 80), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Calibrer ici", Icons.Rounded.CenterFocusStrong, Modifier.weight(1f)) { actions.onCalibrate() }
        AnimatedVisibility(state.calibrated, Modifier.weight(1f), enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f)), exit = fadeOut() + scaleOut()) {
            SideButton("Zéro d'usine", Icons.Rounded.RestartAlt, Modifier.fillMaxWidth()) { actions.onResetCalibration() }
        }
    }
    AnimatedVisibility(state.calibrated, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Hint("Zéro réglé sur ce téléphone : les mesures en tiennent compte.", modifier = Modifier.padding(horizontal = 8.dp))
    }
    Hint(
        "Si l'appareil photo dépasse au dos, le téléphone ne pose pas à plat : mets-le sur une surface que tu sais droite et touche « Calibrer ici ». " +
            "L'écran reste allumé tant que le niveau est ouvert." +
            (state.sensorKind?.let { " Mesure faite par le $it du téléphone." } ?: ""),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/** The phone cannot measure a tilt: say so plainly, with the rocking phone. */
@Composable
private fun NoSensorCard(status: SensorStatus, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoSensorArt()
        Spacer(Modifier.height(14.dp))
        val title = if (status == SensorStatus.SILENT) "Le capteur ne répond pas" else "Pas de capteur d'inclinaison"
        Text(
            if (terminal) title.uppercase() else title,
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        val text = if (status == SensorStatus.SILENT) {
            "Ce téléphone a un capteur de mouvement mais il n'envoie aucune mesure. Ferme Gulp puis rouvre-le ; si rien ne change, le capteur est désactivé ou en panne."
        } else {
            "Ce téléphone n'a ni accéléromètre ni capteur de gravité : il ne peut pas sentir s'il penche, le niveau à bulle ne peut donc pas marcher ici."
        }
        Text(if (terminal) "# $text" else text, color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

private fun modeLabel(mode: LevelMode): String = when (mode) {
    LevelMode.AUTO -> "Auto"
    LevelMode.FLAT -> "À plat"
    LevelMode.EDGE -> "Sur la tranche"
}

private const val PREFS = "level"
private const val KEY_MODE = "mode"
private const val KEY_CALIBRATION = "calibration"
