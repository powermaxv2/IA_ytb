package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.MainViewModel

// Gulp is a hub: the home screen lists the tools, each tool is a full screen of its own.
// Adding a tool = one entry in ToolRegistry.tools and one composable for its screen.

/** One tool of the hub, as the home screen shows it. */
@Immutable
class GulpTool(
    /** Stable identifier (saved state, intents): never rename it once released. */
    val id: String,
    val name: String,
    val tagline: String,
    /** Platforms whose logos float on the card; empty for a tool that is not about a website. */
    val platforms: List<Platform> = emptyList(),
    /** Short words shown as chips on the card. */
    val tags: List<String> = emptyList(),
    /** The tool's screen, drawn full screen over the skin background; null = "À venir" card, not openable. */
    val screen: (@Composable (MainViewModel) -> Unit)? = null,
) {
    val available: Boolean get() = screen != null
}

object ToolRegistry {
    const val DOWNLOADER = "downloader"

    private fun comingSoon(slot: Int) = GulpTool(
        id = "soon-$slot",
        name = "Outil n°$slot",
        tagline = "Un nouvel outil arrive ici.",
    )

    val tools: List<GulpTool> = listOf(
        GulpTool(
            id = DOWNLOADER,
            name = "Téléchargeur vidéo",
            tagline = "YouTube, Instagram, TikTok, X… en vidéo ou en audio, enregistré dans la Galerie.",
            platforms = listOf(Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK, Platform.TWITTER),
            tags = listOf("Jusqu'à 4K", "MP4 · MP3", "Partager → Gulp"),
            screen = { vm -> DownloaderTool(vm) },
        ),
        comingSoon(2),
        comingSoon(3),
        comingSoon(4),
    )

    val available: List<GulpTool> get() = tools.filter { it.available }
    val upcoming: List<GulpTool> get() = tools.filterNot { it.available }

    /** The openable tool with this [id], or null (unknown id, or a tool that is not out yet). */
    fun find(id: String?): GulpTool? = tools.firstOrNull { it.id == id && it.available }
}
