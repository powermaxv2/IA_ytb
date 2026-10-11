package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.tools.chat.ChatStatsTool
import com.powermaxv2.ytdownloader.ui.tools.compress.CompressorTool
import com.powermaxv2.ytdownloader.ui.tools.convert.ConverterTool
import com.powermaxv2.ytdownloader.ui.tools.preview.PreviewTool
import com.powermaxv2.ytdownloader.ui.tools.qr.QrTool
import com.powermaxv2.ytdownloader.ui.tools.scan.ScannerTool

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

    /** "Vu, répondu": who answers fastest in a WhatsApp discussion export. Also reached by sharing the export to Gulp. */
    const val CHAT_STATS = "chat-stats"

    /** File compressor (photos, videos, sounds, PDF, or several files in one ZIP). Also "Partager → Compresser avec Gulp". */
    const val COMPRESSOR = "compressor"

    /** QR codes: read one (camera or picture), or create one for a link, a text, a Wi-Fi network or a contact. */
    const val QR_CODE = "qr-code"

    /** Document scanner: photos of pages, cropped, straightened and cleaned, into one PDF. */
    const val SCANNER = "scanner"

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
            icon = Icons.Rounded.SwapHoriz,
            screen = { vm -> ConverterTool(vm) },
        ),
        GulpTool(
            id = PREVIEW,
            name = "Aperçu",
            tagline = "Ouvre une image, un PDF, un texte ou une archive ZIP pour voir ce qu'il y a dedans.",
            tags = listOf("Image · PDF · Texte", "ZIP", "Ouvrir avec Gulp"),
            icon = Icons.Rounded.Preview,
            screen = { vm -> PreviewTool(vm) },
        ),
        GulpTool(
            id = CHAT_STATS,
            name = "Vu, répondu",
            tagline = "Qui répond le plus vite dans un groupe WhatsApp ? Temps de réponse, podium, messages et refrains.",
            tags = listOf("WhatsApp", "Temps de réponse", "Partager → Gulp"),
            icon = Icons.Rounded.DoneAll,
            screen = { vm -> ChatStatsTool(vm) },
        ),
        GulpTool(
            id = COMPRESSOR,
            name = "Compresseur",
            tagline = "Rend une photo, une vidéo ou un PDF plus léger pour l'envoyer, ou range plusieurs fichiers dans un ZIP.",
            tags = listOf("Photo · Vidéo · PDF", "ZIP", "Avant / après"),
            icon = Icons.Rounded.Compress,
            screen = { vm -> CompressorTool(vm) },
        ),
        GulpTool(
            id = QR_CODE,
            name = "QR code",
            tagline = "Scanne un QR code ou un code-barres, ou crée le tien : lien, texte, Wi-Fi ou contact.",
            tags = listOf("Scanner", "Créer", "Wi-Fi · Lien"),
            icon = Icons.Rounded.QrCodeScanner,
            screen = { vm -> QrTool(vm) },
        ),
        GulpTool(
            id = SCANNER,
            name = "Scanner",
            tagline = "Photographie des pages, Gulp les recadre, les redresse et les nettoie, puis en fait un seul PDF.",
            tags = listOf("Photo → PDF", "Recadrage auto", "Hors ligne"),
            icon = Icons.Rounded.DocumentScanner,
            screen = { vm -> ScannerTool(vm) },
        ),
        comingSoon(8),
    )

    val available: List<GulpTool> get() = tools.filter { it.available }
    val upcoming: List<GulpTool> get() = tools.filterNot { it.available }

    /** The openable tool with this [id], or null (unknown id, or a tool that is not out yet). */
    fun find(id: String?): GulpTool? = tools.firstOrNull { it.id == id && it.available }
}
