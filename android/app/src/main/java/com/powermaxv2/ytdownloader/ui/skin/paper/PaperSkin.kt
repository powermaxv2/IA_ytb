package com.powermaxv2.ytdownloader.ui.skin.paper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.LinkPane
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicBackground
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicBottomBar
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicGuide
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicHeader
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicLinkCard
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicVideoCard

// "Papier" skin. Temporary: every part delegates to Classique until its own design lands.

@Composable
fun PaperBackground(modifier: Modifier = Modifier) {
    ClassicBackground(modifier)
}

@Composable
fun PaperBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    ClassicBottomBar(tab, activeCount, pulse, onSelect, modifier)
}

@Composable
fun PaperHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit) {
    ClassicHeader(platform, onSettings)
}

@Composable
fun PaperLinkCard(vm: MainViewModel, pane: LinkPane, enabled: Boolean) {
    ClassicLinkCard(vm, pane, enabled)
}

@Composable
fun PaperGuide(platform: Platform) {
    ClassicGuide(platform)
}

@Composable
fun PaperVideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    ClassicVideoCard(info, sourceUrl, vm)
}

@Composable
fun PaperRecentDownload(vm: MainViewModel) {
}
