package com.powermaxv2.ytdownloader.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
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
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperBackground
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperBottomBar
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperGuide
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperHeader
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperLinkCard
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperRecentDownload
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperVideoCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalBackground
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalBottomBar
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalGuide
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalHeader
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalLinkCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalRecentDownload
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalVideoCard

// Skin-specific parts of the shared screens, dispatched on LocalSkin.

/** Full-screen background, drawn behind every tab. */
@Composable
fun SkinBackground(modifier: Modifier = Modifier) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicBackground(modifier)
        Skin.PAPER -> PaperBackground(modifier)
        Skin.TERMINAL -> TerminalBackground(modifier)
    }
}

/** Bottom navigation; [activeCount] = running downloads (badge), [pulse] grows each time a download is queued. */
@Composable
fun SkinBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicBottomBar(tab, activeCount, pulse, onSelect, modifier)
        Skin.PAPER -> PaperBottomBar(tab, activeCount, pulse, onSelect, modifier)
        Skin.TERMINAL -> TerminalBottomBar(tab, activeCount, pulse, onSelect, modifier)
    }
}

/** Title block of a link tab with the settings button. */
@Composable
fun SkinHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicHeader(platform, onSettings)
        Skin.PAPER -> PaperHeader(platform, engineState, onSettings)
        Skin.TERMINAL -> TerminalHeader(platform, engineState, onSettings)
    }
}

/** Link field with its paste / analyse actions. */
@Composable
fun SkinLinkCard(vm: MainViewModel, pane: LinkPane, enabled: Boolean) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicLinkCard(vm, pane, enabled)
        Skin.PAPER -> PaperLinkCard(vm, pane, enabled)
        Skin.TERMINAL -> TerminalLinkCard(vm, pane, enabled)
    }
}

/** Idle state of a link tab: how-to and features. */
@Composable
fun SkinGuide(platform: Platform) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicGuide(platform)
        Skin.PAPER -> PaperGuide(platform)
        Skin.TERMINAL -> TerminalGuide(platform)
    }
}

/** Single video result: thumbnail, mode, quality / format choice and the download button. */
@Composable
fun SkinVideoCard(info: VideoInfo, sourceUrl: String, vm: MainViewModel) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicVideoCard(info, sourceUrl, vm)
        Skin.PAPER -> PaperVideoCard(info, sourceUrl, vm)
        Skin.TERMINAL -> TerminalVideoCard(info, sourceUrl, vm)
    }
}

/** Compact strip of the latest download under the result (Papier, Terminal); Classique shows nothing. */
@Composable
fun SkinRecentDownload(vm: MainViewModel) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> Unit
        Skin.PAPER -> PaperRecentDownload(vm)
        Skin.TERMINAL -> TerminalRecentDownload(vm)
    }
}
