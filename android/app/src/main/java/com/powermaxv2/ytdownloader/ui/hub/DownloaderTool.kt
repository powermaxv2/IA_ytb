package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.ui.DownloadsScreen
import com.powermaxv2.ytdownloader.ui.LinkScreen
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.SettingsScreen
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.skin.SkinBottomBar

/** The first tool of the hub: the video downloader (YouTube, Instagram, Autres, Fichiers tabs and its bottom bar). */
@Composable
fun DownloaderTool(vm: MainViewModel) {
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    val insets = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
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
        SkinBottomBar(
            tab = vm.tab,
            activeCount = jobs.count { it.isActive },
            pulse = vm.queuedPulse,
            onSelect = vm::selectTab,
            bottomInset = insets.calculateBottomPadding(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
