package com.powermaxv2.ytdownloader.ui.skin

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Settings
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.hub.GulpTool
import com.powermaxv2.ytdownloader.ui.hub.ToolCategory
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicCategoryChips
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicSearchBar
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperCategoryChips
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperSearchBar
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalCategoryChips
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalSearchBar
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicComingSoon
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicToolCard
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicToolBubble
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicHomeSection
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperComingSoon
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperToolCard
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperToolBubble
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperHomeSection
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalComingSoon
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalToolCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalToolBubble
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalHomeSection
import com.powermaxv2.ytdownloader.ui.skin.classic.ClassicBackground
import com.powermaxv2.ytdownloader.ui.skin.paper.PaperBackground
import com.powermaxv2.ytdownloader.ui.skin.terminal.TerminalBackground

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

/** Search bar of the home screen; the hint cycles through [suggestions] (tool names). */
@Composable
fun SkinSearchBar(query: String, onQueryChange: (String) -> Unit, suggestions: List<String>) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicSearchBar(query, onQueryChange, suggestions)
        Skin.PAPER -> PaperSearchBar(query, onQueryChange, suggestions)
        Skin.TERMINAL -> TerminalSearchBar(query, onQueryChange, suggestions)
    }
}

/** "Tout" then one chip per category with its count of tools; [selected] null = "Tout". */
@Composable
fun SkinCategoryChips(
    categories: List<ToolCategory>,
    counts: Map<ToolCategory, Int>,
    total: Int,
    selected: ToolCategory?,
    onSelect: (ToolCategory?) -> Unit,
) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicCategoryChips(categories, counts, total, selected, onSelect)
        Skin.PAPER -> PaperCategoryChips(categories, counts, total, selected, onSelect)
        Skin.TERMINAL -> TerminalCategoryChips(categories, counts, total, selected, onSelect)
    }
}

/** Card of a tool that is out ([index] = its place among them); a tap opens it, its star ([onStar]) pins it. */
@Composable
fun SkinToolCard(tool: GulpTool, index: Int, starred: Boolean = false, onStar: (() -> Unit)? = null, onOpen: () -> Unit) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicToolCard(tool, index, starred, onStar, onOpen)
        Skin.PAPER -> PaperToolCard(tool, index, starred, onStar, onOpen)
        Skin.TERMINAL -> TerminalToolCard(tool, index, starred, onStar, onOpen)
    }
}

/** Small card of a tool that is out (half the width of [SkinToolCard]); [order] staggers its entrance. */
@Composable
fun SkinToolBubble(
    tool: GulpTool,
    index: Int,
    order: Int,
    modifier: Modifier = Modifier,
    starred: Boolean = false,
    onStar: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicToolBubble(tool, order, modifier, starred, onStar, onOpen)
        Skin.PAPER -> PaperToolBubble(tool, index, order, modifier, starred, onStar, onOpen)
        Skin.TERMINAL -> TerminalToolBubble(tool, index, order, modifier, starred, onStar, onOpen)
    }
}

/** Title of a group of the home screen ("Dernier utilisé", "Médias", "3 outils trouvés"). */
@Composable
fun SkinHomeSection(title: String, order: Int) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicHomeSection(title, order)
        Skin.PAPER -> PaperHomeSection(title, order)
        Skin.TERMINAL -> TerminalHomeSection(title, order)
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
