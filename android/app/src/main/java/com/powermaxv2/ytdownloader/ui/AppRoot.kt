package com.powermaxv2.ytdownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.ui.brand.GulpIntro
import com.powermaxv2.ytdownloader.ui.brand.LocalGulpPulse
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.tokensFor
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.hub.HomeScreen
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val gate = rememberDownloadGate()
    // Back: settings tab → previous tab, a tool → home screen, home settings → home screen; home → leaves the app.
    BackHandler(enabled = vm.place != Place.HOME) {
        if (vm.place == Place.TOOL && vm.tab != Tab.SETTINGS) vm.goHome() else vm.closeSettings()
    }

    val insets = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        // Switching skin crossfades the whole app; each side draws with its own skin's theme.
        Crossfade(targetState = settings.skin, animationSpec = tween(350), label = "skin") { skin ->
            // The home screen wears the Gulp colours; a tool wears its own (the downloader: the current tab's).
            AppTheme(if (vm.place == Place.TOOL) vm.accentPlatform else Platform.YOUTUBE, skin) {
                CompositionLocalProvider(
                    LocalHaptics provides settings.haptics,
                    LocalDownloadGate provides gate,
                    LocalGulpPulse provides vm.queuedPulse,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        SkinBackground()
                        AnimatedContent(
                            targetState = vm.place,
                            transitionSpec = {
                                // Into a tool: the home screen grows past the viewer while the tool comes up from
                                // slightly smaller, like walking through a door; out of it: the reverse.
                                val forward = targetState.ordinal > initialState.ordinal
                                val enter = scaleIn(spring(dampingRatio = 0.86f, stiffness = 320f), initialScale = if (forward) 0.9f else 1.08f) +
                                    fadeIn(tween(260, delayMillis = 60))
                                val exit = scaleOut(tween(260), targetScale = if (forward) 1.08f else 0.92f) + fadeOut(tween(200))
                                enter togetherWith exit
                            },
                            label = "hub",
                        ) { place ->
                            when (place) {
                                Place.HOME -> HomeScreen(vm, insets)
                                Place.SETTINGS -> SettingsScreen(
                                    vm,
                                    PaddingValues(top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 32.dp),
                                )
                                Place.TOOL -> {
                                    val tool = ToolRegistry.find(vm.toolId)
                                    if (tool?.screen != null) tool.screen.invoke(vm) else HomeScreen(vm, insets)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Outside the crossfade: a single collector of the one-shot toasts, so none shows twice during a skin switch.
        val toastModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = insets.calculateBottomPadding() + 98.dp, start = 24.dp, end = 24.dp)
        AppTheme(vm.accentPlatform, settings.skin) {
            ToastHost(vm.toasts, toastModifier)
        }
        // Gulp launch animation, once per launch (not again on rotation).
        var introDone by rememberSaveable { mutableStateOf(false) }
        if (!introDone) {
            val tokens = tokensFor(settings.skin)
            GulpIntro(tokens.background, tokens.textPrimary, onFinished = { introDone = true })
        }
    }
}

/** Asks for the notification permission (Android 13+) or storage permission (Android 9-) before a download. */
@Composable
private fun rememberDownloadGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.invoke()
        pending = null
    }
    return remember(launcher) {
        { action: () -> Unit ->
            val needed = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) {
                action()
            } else {
                pending = action
                launcher.launch(needed.toTypedArray())
            }
        }
    }
}

// MARK: - Toasts

@Composable
private fun ToastHost(toasts: SharedFlow<Toast>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<Toast?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toasts) {
        toasts.collect {
            current = it
            visible = true
        }
    }
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(2400)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 380f)) { it * 2 } + fadeIn() + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val toast = current ?: return@AnimatedVisibility
        val tokens = LocalTokens.current
        AnimatedContent(targetState = toast, label = "toastContent") { shown ->
            Row(
                Modifier
                    .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(tokens.toastBackground)
                    .border(1.dp, tokens.toastText.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (shown.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (shown.success) Palette.Success else Palette.Danger,
                    modifier = Modifier.size(18.dp),
                )
                Text(shown.message, color = tokens.toastText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
