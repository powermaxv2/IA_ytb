package com.powermaxv2.ytdownloader.ui.skin

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.AudioFormat
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.MediaMode
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.QualityOption
import com.powermaxv2.ytdownloader.core.Settings
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.VideoContainer
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.LinkPane
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.hub.GulpTool
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicComingSoon
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicToolCard
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperComingSoon
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperToolCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalComingSoon
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalToolCard
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

/**
 * Bottom navigation; [activeCount] = running downloads (badge), [pulse] grows each time a download is queued.
 * [bottomInset] is the navigation bar height: Classique and Papier float 12dp above it, Terminal sits flush and fills it.
 */
@Composable
fun SkinBottomBar(tab: Tab, activeCount: Int, pulse: Int, onSelect: (Tab) -> Unit, bottomInset: Dp, modifier: Modifier = Modifier) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicBottomBar(tab, activeCount, pulse, onSelect, modifier.padding(bottom = bottomInset + 12.dp))
        Skin.PAPER -> PaperBottomBar(tab, activeCount, pulse, onSelect, modifier.padding(bottom = bottomInset + 12.dp))
        Skin.TERMINAL -> TerminalBottomBar(tab, activeCount, pulse, onSelect, bottomInset, modifier)
    }
}

/** Title block of a link tab with the settings button; [onHome]: the logo also leads back to the home screen. */
@Composable
fun SkinHeader(platform: Platform, engineState: EngineState, onSettings: () -> Unit, onHome: (() -> Unit)? = null) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicHeader(platform, onSettings, onHome)
        Skin.PAPER -> PaperHeader(platform, engineState, onSettings, onHome)
        Skin.TERMINAL -> TerminalHeader(platform, engineState, onSettings, onHome)
    }
}

/** Link field with its paste / analyse actions. */
@Composable
fun SkinLinkCard(vm: MainViewModel, pane: LinkPane, enabled: Boolean) {
    SkinLinkCard(
        pane = pane,
        enabled = enabled,
        onPaste = { text -> vm.paste(pane, text) },
        onAnalyze = { vm.analyze(pane) },
        onReset = { vm.reset(pane) },
    )
}

/** Stateless form of [SkinLinkCard] (also used by the screenshot tests). */
@Composable
fun SkinLinkCard(
    pane: LinkPane,
    enabled: Boolean,
    onPaste: (String?) -> Unit,
    onAnalyze: () -> Unit,
    onReset: () -> Unit,
) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicLinkCard(pane, enabled, onPaste, onAnalyze, onReset)
        Skin.PAPER -> PaperLinkCard(pane, enabled, onPaste, onAnalyze, onReset)
        Skin.TERMINAL -> TerminalLinkCard(pane, enabled, onPaste, onAnalyze, onReset)
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
    val settings by vm.prefs.settings.collectAsStateWithLifecycle()
    SkinVideoCard(info, sourceUrl, settings) { mode, quality, container, audioFormat ->
        vm.downloadVideo(info, sourceUrl, mode, quality, container, audioFormat)
    }
}

/** Stateless form of [SkinVideoCard]: [settings] gives the default format choices (also used by the screenshot tests). */
@Composable
fun SkinVideoCard(
    info: VideoInfo,
    sourceUrl: String,
    settings: Settings,
    onDownload: (MediaMode, QualityOption?, VideoContainer, AudioFormat) -> Unit,
) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicVideoCard(info, sourceUrl, settings, onDownload)
        Skin.PAPER -> PaperVideoCard(info, sourceUrl, settings, onDownload)
        Skin.TERMINAL -> TerminalVideoCard(info, sourceUrl, settings, onDownload)
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

// MARK: - Home screen of the hub

/** Top of the home screen: logo, title and how many tools are out / coming. */
@Composable
fun SkinHomeHeader(ready: Int, upcoming: Int, onSettings: () -> Unit) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicHomeHeader(ready, upcoming, onSettings)
        Skin.PAPER -> PaperHomeHeader(ready, upcoming, onSettings)
        Skin.TERMINAL -> TerminalHomeHeader(ready, upcoming, onSettings)
    }
}

/** Card of a tool that is out ([index] = its place among them); a tap opens it. */
@Composable
fun SkinToolCard(tool: GulpTool, index: Int, onOpen: () -> Unit) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicToolCard(tool, index, onOpen)
        Skin.PAPER -> PaperToolCard(tool, index, onOpen)
        Skin.TERMINAL -> TerminalToolCard(tool, index, onOpen)
    }
}

/** The "À venir" tools, numbered from [firstNumber]. */
@Composable
fun SkinComingSoon(tools: List<GulpTool>, firstNumber: Int) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicComingSoon(tools, firstNumber)
        Skin.PAPER -> PaperComingSoon(tools, firstNumber)
        Skin.TERMINAL -> TerminalComingSoon(tools, firstNumber)
    }
}
