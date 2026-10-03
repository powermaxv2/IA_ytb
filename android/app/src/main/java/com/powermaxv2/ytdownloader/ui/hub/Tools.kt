package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.tools.convert.ConverterTool
import com.powermaxv2.ytdownloader.ui.tools.preview.PreviewTool

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
    /** Symbol of the tool, drawn on its home card when it has no [platforms]. */
    val icon: ImageVector? = null,
    /** The tool's screen, drawn full screen over the skin background; null = "À venir" card, not openable. */
    val screen: (@Composable (MainViewModel) -> Unit)? = null,
) {
    val available: Boolean get() = screen != null
}

object ToolRegistry {
    const val DOWNLOADER = "downloader"

    /** File converter (images, audio, video, documents). Also the target of "Partager → Convertir avec Gulp". */
    const val CONVERTER = "converter"

    /** File preview (images, PDF, text, ZIP contents, media info). Also "Partager → Aperçu avec Gulp" / "Ouvrir avec". */
    const val PREVIEW = "preview"

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
        GulpTool(
            id = CONVERTER,
            name = "Convertisseur",
            tagline = "Change le format d'une photo, d'un son, d'une vidéo ou d'un document, sur le téléphone.",
            tags = listOf("Photo · Son · Vidéo", "Hors ligne", "Partager → Gulp"),
            icon = Icons.Rounded.Transform,
            screen = { vm -> ConverterTool(vm) },
        ),
        GulpTool(
            id = PREVIEW,
            name = "Aperçu",
            tagline = "Ouvre une image, un PDF, un texte ou une archive ZIP pour voir ce qu'il y a dedans.",
            tags = listOf("Image · PDF · Texte", "Contenu des ZIP", "Ouvrir avec Gulp"),
            icon = Icons.Rounded.Preview,
            screen = { vm -> PreviewTool(vm) },
        ),
        comingSoon(4),
    )

    val available: List<GulpTool> get() = tools.filter { it.available }
    val upcoming: List<GulpTool> get() = tools.filterNot { it.available }

    /** The openable tool with this [id], or null (unknown id, or a tool that is not out yet). */
    fun find(id: String?): GulpTool? = tools.firstOrNull { it.id == id && it.available }
}
