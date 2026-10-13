package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Quickreply
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.tools.chat.ChatStatsTool
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CleanerTool
import com.powermaxv2.ytdownloader.ui.tools.compress.CompressorTool
import com.powermaxv2.ytdownloader.ui.tools.convert.ConverterTool
import com.powermaxv2.ytdownloader.ui.tools.direct.DirectTool
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PdfJoinTool
import com.powermaxv2.ytdownloader.ui.tools.photodup.PhotoDupTool
import com.powermaxv2.ytdownloader.ui.tools.preview.PreviewTool
import com.powermaxv2.ytdownloader.ui.tools.profilefit.ProfileFitTool
import com.powermaxv2.ytdownloader.ui.tools.qr.QrTool
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QuickReplyTool
import com.powermaxv2.ytdownloader.ui.tools.reader.ReaderTool
import com.powermaxv2.ytdownloader.ui.tools.rename.RenameIcon
import com.powermaxv2.ytdownloader.ui.tools.rename.RenameTool
import com.powermaxv2.ytdownloader.ui.tools.resize.ResizeIcon
import com.powermaxv2.ytdownloader.ui.tools.resize.ResizeTool
import com.powermaxv2.ytdownloader.ui.tools.scan.ScannerTool
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SignPdfTool
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SlideshowTool
import com.powermaxv2.ytdownloader.ui.tools.sticker.StickerIcon
import com.powermaxv2.ytdownloader.ui.tools.sticker.StickerTool
import com.powermaxv2.ytdownloader.ui.tools.split.SplitTool
import com.powermaxv2.ytdownloader.ui.tools.status.StatusIcon
import com.powermaxv2.ytdownloader.ui.tools.status.StatusTool
import com.powermaxv2.ytdownloader.ui.tools.styled.StyledTextTool
import com.powermaxv2.ytdownloader.ui.tools.trim.TrimTool
import com.powermaxv2.ytdownloader.ui.tools.voice.VoiceTool
import com.powermaxv2.ytdownloader.ui.tools.watermark.WatermarkTool
import com.powermaxv2.ytdownloader.ui.tools.wipe.EraserIcon
import com.powermaxv2.ytdownloader.ui.tools.wipe.WipeTool

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

    /** Découpeur: keeps a passage of a video or a sound (video, sound only, ringtone or GIF). */
    const val TRIMMER = "trimmer"

    /** Gomme: erases what photos tell (place, phone, author, date) into clean copies. */
    const val WIPER = "wiper"

    /** Assembleur PDF: several PDF and photos in one, pages moved, turned or removed, or a PDF cut in several. */
    const val PDF_JOIN = "pdf-join"

    /** Vocaux en texte: writes down a voice note (WhatsApp, memo), offline. Also "Partager → Transcrire avec Gulp". */
    const val VOICE_TEXT = "voice-text"

    /** Nettoyeur WhatsApp: finds WhatsApp's duplicates and big media in its folder, and deletes what the user checks. */
    const val CLEANER = "whatsapp-cleaner"

    /** Stickers: photos, a video or a GIF into a WhatsApp sticker pack, added to WhatsApp directly. */
    const val STICKERS = "stickers"

    /** Statuts: keeps the photos and videos of WhatsApp statuses before they vanish (24 h). */
    const val STATUSES = "statuses"

    /** Texte stylé: a text in WhatsApp's styles (*gras*, _italique_, ~barré~…) and in Unicode fonts, ready to paste. */
    const val STYLED_TEXT = "styled-text"

    /** Découpe statut: a long video cut into parts of 30 s or 1 min, to post one after the other as WhatsApp statuses. */
    const val STATUS_SPLIT = "status-split"

    /** Message direct: opens a WhatsApp chat with a number that is not in the contacts (WhatsApp's wa.me link). */
    const val DIRECT_CHAT = "whatsapp-direct"

    /** Lecteur de discussion: a WhatsApp export read back in bubbles, searched, and printed to PDF. */
    const val CHAT_READER = "chat-reader"

    /** Réponses rapides: ready-made messages with {variables}, copied or sent to WhatsApp in one touch. */
    const val QUICK_REPLIES = "quick-replies"

    /** Photo de profil entière: a whole photo shrunk into WhatsApp's round profile picture, on a blurred or plain backdrop. */
    const val PROFILE_FIT = "profile-fit"

    /** Signature PDF: a signature drawn once with the finger, placed on a page of a PDF, saved as a signed copy. */
    const val SIGN_PDF = "sign-pdf"

    /** Filigrane: a see-through text ("Copie pour location – date") across a photo or every page of a PDF, saved as a copy. */
    const val WATERMARK = "watermark"

    /** Redimensionneur: several photos made smaller (side, percentage or weight), cut to a shape, as JPG / PNG / WebP copies. */
    const val IMAGE_RESIZER = "image-resizer"

    /** Doublons photos: the photos of the gallery that are the same or look the same, the best one kept, the others deleted. */
    const val PHOTO_DUPLICATES = "photo-duplicates"

    /** Diaporama: photos into a short MP4 (durations, transitions, Ken Burns zoom, music), made on the phone. */
    const val SLIDESHOW = "slideshow"

    /** Renommeur: renames many files at once from pieces (text, number, date, old name, replace), with a live preview. */
    const val RENAMER = "file-renamer"

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
        GulpTool(
            id = TRIMMER,
            name = "Découpeur",
            tagline = "Garde juste le bon moment d'une vidéo ou d'un son, ou tires-en le son, une sonnerie ou un GIF.",
            tags = listOf("Vidéo · Son", "Sonnerie · GIF", "Sans réencodage"),
            icon = Icons.Rounded.ContentCut,
            screen = { vm -> TrimTool(vm) },
        ),
        GulpTool(
            id = WIPER,
            name = "Gomme",
            tagline = "Efface ce que tes photos disent de toi (lieu, téléphone, nom) avant de les envoyer.",
            tags = listOf("Lieu · Téléphone · Nom", "Hors ligne", "Image intacte"),
            icon = EraserIcon,
            screen = { vm -> WipeTool(vm) },
        ),
        GulpTool(
            id = PDF_JOIN,
            name = "Assembleur PDF",
            tagline = "Colle plusieurs PDF et photos en un seul, change l'ordre, tourne ou retire des pages, ou sépare un PDF.",
            tags = listOf("PDF · Photos", "Ordre · Rotation", "Séparer"),
            icon = Icons.Rounded.PictureAsPdf,
            screen = { vm -> PdfJoinTool(vm) },
        ),
        GulpTool(
            id = VOICE_TEXT,
            name = "Vocaux en texte",
            tagline = "Lis un vocal WhatsApp au lieu de l'écouter : Gulp l'écrit mot à mot, sur le téléphone, sans rien envoyer.",
            tags = listOf("WhatsApp", "Hors ligne", "Partager → Gulp"),
            icon = Icons.Rounded.RecordVoiceOver,
            screen = { vm -> VoiceTool(vm) },
        ),
        GulpTool(
            id = CLEANER,
            name = "Nettoyeur WhatsApp",
            tagline = "Trouve les doublons et les gros médias de WhatsApp, et libère de la place en quelques touchers.",
            tags = listOf("WhatsApp", "Doublons", "Hors ligne"),
            icon = Icons.Rounded.CleaningServices,
            screen = { vm -> CleanerTool(vm) },
        ),
        GulpTool(
            id = STICKERS,
            name = "Stickers",
            tagline = "Transforme une photo, une vidéo ou un GIF en stickers WhatsApp et ajoute le pack direct dans WhatsApp.",
            tags = listOf("WhatsApp", "Photo · Vidéo · GIF", "Fond effacé"),
            icon = StickerIcon,
            screen = { vm -> StickerTool(vm) },
        ),
        GulpTool(
            id = STYLED_TEXT,
            name = "Texte stylé",
            tagline = "Écris une fois, colle-le en gras, barré, gothique ou en bulles dans WhatsApp.",
            tags = listOf("WhatsApp", "24 styles", "Copier · Envoyer"),
            icon = Icons.Rounded.TextFormat,
            screen = { vm -> StyledTextTool(vm) },
        ),
        GulpTool(
            id = STATUSES,
            name = "Statuts",
            tagline = "Garde les photos et vidéos des statuts WhatsApp de tes contacts avant qu'ils disparaissent.",
            tags = listOf("WhatsApp", "Photo · Vidéo", "Hors ligne"),
            icon = StatusIcon,
            screen = { vm -> StatusTool(vm) },
        ),
        GulpTool(
            id = STATUS_SPLIT,
            name = "Découpe statut",
            tagline = "Coupe une longue vidéo en morceaux d'une minute, prêts à poster à la suite en statut WhatsApp.",
            tags = listOf("WhatsApp", "30 s · 1 min", "Sans réencodage"),
            icon = Icons.Rounded.DonutLarge,
            screen = { vm -> SplitTool(vm) },
        ),
        GulpTool(
            id = DIRECT_CHAT,
            name = "Écrire sans enregistrer",
            tagline = "Écris sur WhatsApp à un numéro sans l'ajouter à tes contacts : colle-le, la discussion s'ouvre.",
            tags = listOf("WhatsApp", "Message prêt", "Sans contact"),
            icon = Icons.Rounded.Dialpad,
            screen = { vm -> DirectTool(vm) },
        ),
        GulpTool(
            id = CHAT_READER,
            name = "Lecteur de discussion",
            tagline = "Relis une discussion WhatsApp exportée en bulles, comme dans l'appli, cherche dedans et garde-la en PDF.",
            tags = listOf("WhatsApp", "Recherche", "Export PDF"),
            icon = Icons.Rounded.Forum,
            screen = { vm -> ReaderTool(vm) },
        ),
        GulpTool(
            id = QUICK_REPLIES,
            name = "Réponses rapides",
            tagline = "Tes messages tout faits, avec l'heure ou un prénom glissés dedans, copiés ou envoyés dans WhatsApp en un geste.",
            tags = listOf("WhatsApp", "{heure} · {prénom}", "Copier · Envoyer"),
            icon = Icons.Rounded.Quickreply,
            screen = { vm -> QuickReplyTool(vm) },
        ),
        GulpTool(
            id = PROFILE_FIT,
            name = "Photo de profil entière",
            tagline = "Mets toute ta photo dans le rond WhatsApp, sans tête ni ami coupé, sur un fond flou ou de sa couleur.",
            tags = listOf("WhatsApp", "Fond flou", "Hors ligne"),
            icon = Icons.Rounded.AccountCircle,
            screen = { vm -> ProfileFitTool(vm) },
        ),
        GulpTool(
            id = SIGN_PDF,
            name = "Signature PDF",
            tagline = "Dessine ta signature une fois avec le doigt, pose-la sur n'importe quel PDF et enregistre-le signé.",
            tags = listOf("PDF", "Au doigt", "Date · Initiales"),
            icon = Icons.Rounded.Draw,
            screen = { vm -> SignPdfTool(vm) },
        ),
        GulpTool(
            id = WATERMARK,
            name = "Filigrane",
            tagline = "Écris « Copie pour location » en travers d'une image ou d'un PDF avant de l'envoyer, pour qu'il ne serve qu'à ça.",
            tags = listOf("Image · PDF", "Texte + date", "Hors ligne"),
            icon = Icons.Rounded.BrandingWatermark,
            screen = { vm -> WatermarkTool(vm) },
        ),
        GulpTool(
            id = IMAGE_RESIZER,
            name = "Redimensionneur",
            tagline = "Réduis ou recadre plusieurs photos d'un coup : taille en pixels, poids maximum, carré ou 16:9, JPG ou WebP.",
            tags = listOf("Lot de photos", "Poids cible", "1:1 · 4:5 · 16:9"),
            icon = ResizeIcon,
            screen = { vm -> ResizeTool(vm) },
        ),
        GulpTool(
            id = PHOTO_DUPLICATES,
            name = "Doublons photos",
            tagline = "Repère les photos en double ou presque pareilles dans la galerie, garde la meilleure et libère de la place.",
            tags = listOf("Galerie", "Identiques · Ressemblantes", "Hors ligne"),
            icon = Icons.Rounded.PhotoLibrary,
            screen = { vm -> PhotoDupTool(vm) },
        ),
        GulpTool(
            id = SLIDESHOW,
            name = "Diaporama",
            tagline = "Transforme quelques photos en petite vidéo, avec transitions, effet Ken Burns et ta musique.",
            tags = listOf("Photos → vidéo", "Fondu · Zoom", "Musique"),
            icon = Icons.Rounded.Slideshow,
            screen = { vm -> SlideshowTool(vm) },
        ),
        GulpTool(
            id = RENAMER,
            name = "Renommeur",
            tagline = "Renomme plein de fichiers d'un coup avec un compteur, une date ou un texte remplacé, et vois le résultat avant.",
            tags = listOf("Plusieurs fichiers", "Date · Compteur", "Annulable"),
            icon = RenameIcon,
            screen = { vm -> RenameTool(vm) },
        ),
        comingSoon(27),
    )

    val available: List<GulpTool> get() = tools.filter { it.available }
    val upcoming: List<GulpTool> get() = tools.filterNot { it.available }

    /** The openable tool with this [id], or null (unknown id, or a tool that is not out yet). */
    fun find(id: String?): GulpTool? = tools.firstOrNull { it.id == id && it.available }
}
