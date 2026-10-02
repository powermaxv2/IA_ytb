package com.powermaxv2.ytdownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val gate = rememberDownloadGate()
    BackHandler(enabled = vm.tab == Tab.SETTINGS) { vm.closeSettings() }

    AppTheme(vm.accentPlatform) {
        CompositionLocalProvider(LocalHaptics provides settings.haptics, LocalDownloadGate provides gate) {
            Box(Modifier.fillMaxSize()) {
                ScreenBackground()
                val insets = WindowInsets.systemBars.asPaddingValues()
                val contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 16.dp,
                    bottom = insets.calculateBottomPadding() + 104.dp,
                )
                AnimatedContent(
                    targetState = vm.tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.92f, stiffness = 380f)) { it * direction / 6 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it * direction / 6 } + fadeOut(tween(160)))
                    },
                    label = "tabs",
                ) { tab ->
                    when (tab) {
                        Tab.YOUTUBE -> LinkScreen(vm, vm.youtube, contentPadding)
                        Tab.INSTAGRAM -> LinkScreen(vm, vm.insta, contentPadding)
                        Tab.OTHER -> LinkScreen(vm, vm.other, contentPadding)
                        Tab.DOWNLOADS -> DownloadsScreen(vm, contentPadding)
                        Tab.SETTINGS -> SettingsScreen(vm, contentPadding)
                    }
                }
                ToastHost(
                    vm.toasts,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = insets.calculateBottomPadding() + 98.dp, start = 24.dp, end = 24.dp),
                )
                BottomBar(
                    tab = vm.tab,
                    activeCount = jobs.count { it.isActive },
                    pulse = vm.queuedPulse,
                    onSelect = vm::selectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = insets.calculateBottomPadding() + 12.dp),
                )
            }
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

// MARK: - Bottom navigation

/** A tab shows either a platform logo ([platform]) or an [icon]. */
private data class NavItem(
    val tab: Tab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val platform: Platform? = null,
)

private val navItems = listOf(
    NavItem(Tab.YOUTUBE, "YouTube", platform = Platform.YOUTUBE),
    NavItem(Tab.INSTAGRAM, "Instagram", platform = Platform.INSTAGRAM),
    NavItem(Tab.OTHER, "Autres", Icons.Rounded.Public),
    NavItem(Tab.DOWNLOADS, "Fichiers", Icons.Rounded.FolderOpen),
)

/**
 * Floating bar: the selected tab gets a tinted pill behind its icon, the Files icon jumps when something is queued.
 * Settings has no tab here: while it is open no tab is highlighted.
 */
@Composable
private fun BottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(74.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Palette.NavBar)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navItems.forEach { item ->
            val selected = item.tab == tab
            val indicator by animateFloatAsState(
                if (selected) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "navIndicator",
            )
            val color by animateColorAsState(if (selected) Color.White else Palette.TextTertiary, label = "navColor")
            val bump = remember { Animatable(0f) }
            if (item.tab == Tab.DOWNLOADS) {
                LaunchedEffect(pulse) {
                    if (pulse > 0) {
                        bump.snapTo(1f)
                        bump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        view.tick(haptics)
                        onSelect(item.tab)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(32.dp)
                            .graphicsLayer {
                                scaleX = 0.6f + 0.4f * indicator
                                alpha = indicator
                            }
                            .clip(CircleShape)
                            .background(accent.tint(0.22f)),
                    )
                    Box {
                        val iconModifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                val b = bump.value
                                scaleX = 1f + b * 0.4f
                                scaleY = 1f + b * 0.4f
                                translationY = -b * 7.dp.toPx()
                                alpha = if (item.platform != null && !selected) 0.55f else 1f
                            }
                        if (item.platform != null) {
                            PlatformGlyph(item.platform, iconModifier)
                        } else if (item.icon != null) {
                            Icon(item.icon, contentDescription = item.label, tint = color, modifier = iconModifier)
                        }
                        if (item.tab == Tab.DOWNLOADS) {
                            // Fully qualified: the outer Column/Row scopes must not be used implicitly here.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = activeCount > 0,
                                enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-6).dp),
                            ) {
                                Box(
                                    Modifier
                                        .sizeIn(minWidth = 17.dp, minHeight = 17.dp)
                                        .clip(CircleShape)
                                        .background(accent.horizontal())
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RollingNumber(activeCount)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    item.label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
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
        AnimatedContent(targetState = toast, label = "toastContent") { shown ->
            Row(
                Modifier
                    .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(Palette.Surface2)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
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
                Text(shown.message, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
