package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer

// Search bar and categories of the home screen. The home screen owns them: a tool only has to be in
// ToolRegistry.tools, it is filed here by its id (or guessed from its words), and lands in "Autres" otherwise.

/** A group of tools on the home screen, in the order the chips show them. */
enum class ToolCategory(val label: String, val icon: ImageVector) {
    MEDIA("Médias", Icons.Rounded.PlayCircle),
    FILES("Fichiers", Icons.Rounded.Folder),
    WHATSAPP("WhatsApp", Icons.AutoMirrored.Rounded.Chat),
    HANDY("Pratique", Icons.Rounded.AutoAwesome),
    OTHER("Autres", Icons.Rounded.MoreHoriz),
}

/** Icon of the "Tout" chip (no category picked). */
val AllToolsIcon: ImageVector = Icons.Rounded.Apps

object ToolCategories {
    /** Tool id → category. A tool missing here is guessed by [of] from its words, else "Autres". */
    private val byId: Map<String, ToolCategory> = mapOf(
        ToolRegistry.DOWNLOADER to ToolCategory.MEDIA,
        ToolRegistry.TRIMMER to ToolCategory.MEDIA,
        ToolRegistry.WIPER to ToolCategory.MEDIA,
        ToolRegistry.CONVERTER to ToolCategory.FILES,
        ToolRegistry.PREVIEW to ToolCategory.FILES,
        ToolRegistry.COMPRESSOR to ToolCategory.FILES,
        ToolRegistry.SCANNER to ToolCategory.FILES,
        ToolRegistry.PDF_JOIN to ToolCategory.FILES,
        ToolRegistry.CHAT_STATS to ToolCategory.WHATSAPP,
        ToolRegistry.QR_CODE to ToolCategory.HANDY,
        // Tools of 1.14.0, written in parallel: their likely ids (the word guess below covers the others).
        "stickers" to ToolCategory.WHATSAPP,
        "sticker" to ToolCategory.WHATSAPP,
        "status" to ToolCategory.WHATSAPP,
        "statuses" to ToolCategory.WHATSAPP,
        "status-saver" to ToolCategory.WHATSAPP,
        "status-split" to ToolCategory.WHATSAPP,
        "status-cut" to ToolCategory.WHATSAPP,
        ToolRegistry.VOICE_TEXT to ToolCategory.WHATSAPP,
        "voice-to-text" to ToolCategory.WHATSAPP,
        "voice-notes" to ToolCategory.WHATSAPP,
        "whatsapp-cleaner" to ToolCategory.WHATSAPP,
        "cleaner" to ToolCategory.WHATSAPP,
        ToolRegistry.DIRECT_CHAT to ToolCategory.WHATSAPP,
        ToolRegistry.CHAT_READER to ToolCategory.WHATSAPP,
        "styled-text" to ToolCategory.HANDY,
        "fancy-text" to ToolCategory.HANDY,
        // Tools of 1.16.0.
        ToolRegistry.QUICK_REPLIES to ToolCategory.WHATSAPP,
        ToolRegistry.PROFILE_FIT to ToolCategory.WHATSAPP,
        ToolRegistry.SIGN_PDF to ToolCategory.FILES,
        ToolRegistry.WATERMARK to ToolCategory.FILES,
        ToolRegistry.IMAGE_RESIZER to ToolCategory.MEDIA,
        ToolRegistry.PHOTO_DUPLICATES to ToolCategory.MEDIA,
        ToolRegistry.SLIDESHOW to ToolCategory.MEDIA,
        // Still being written in parallel: their likely ids (the word guess below covers the others).
        "whatsapp-link" to ToolCategory.WHATSAPP,
        "my-whatsapp-link" to ToolCategory.WHATSAPP,
        "vault" to ToolCategory.FILES,
        "safe" to ToolCategory.FILES,
        "renamer" to ToolCategory.FILES,
        "file-renamer" to ToolCategory.FILES,
    )

    /** Extra words people may type to find a tool (synonyms, formats, sites), on top of its name, tagline and tags. */
    private val keywords: Map<String, String> = mapOf(
        ToolRegistry.DOWNLOADER to "telecharger download youtube instagram tiktok twitter musique mp3 mp4 video reel short",
        ToolRegistry.CONVERTER to "convertir format jpg jpeg png webp heic mp3 m4a wav mp4 gif pdf",
        ToolRegistry.PREVIEW to "ouvrir voir lire visionneuse zip pdf texte image",
        ToolRegistry.CHAT_STATS to "whatsapp discussion groupe message reponse statistiques stats",
        ToolRegistry.COMPRESSOR to "compresser reduire alleger poids taille zip",
        ToolRegistry.QR_CODE to "qr code barre barcode wifi scanner lien",
        ToolRegistry.SCANNER to "scanner document papier photo pdf feuille",
        ToolRegistry.TRIMMER to "couper decouper extrait sonnerie gif passage",
        ToolRegistry.WIPER to "metadonnees exif gps localisation vie privee effacer photo",
        ToolRegistry.PDF_JOIN to "fusionner assembler coller pdf pages separer diviser",
        ToolRegistry.DIRECT_CHAT to "numero telephone contact inconnu annonce leboncoin message direct ecrire lien wa me",
        ToolRegistry.CHAT_READER to "lire relire lecteur export discussion conversation historique recherche imprimer pdf archive",
        ToolRegistry.QUICK_REPLIES to "reponse rapide message tout fait modele copier envoyer raccourci texte",
        ToolRegistry.PROFILE_FIT to "photo profil avatar rond cercle entiere recadrer fond flou",
        ToolRegistry.SIGN_PDF to "signer signature contrat document pdf paraphe doigt",
        ToolRegistry.WATERMARK to "filigrane copie identite carte location dossier tampon texte photo pdf",
        ToolRegistry.IMAGE_RESIZER to "redimensionner reduire agrandir taille dimension pixels photo image lot",
        ToolRegistry.PHOTO_DUPLICATES to "doublons double identique ressemblante galerie trier supprimer place photo",
        ToolRegistry.SLIDESHOW to "diaporama montage video photos musique transition souvenir",
        "whatsapp-link" to "lien qr code whatsapp numero partager carte visite",
        "my-whatsapp-link" to "lien qr code whatsapp numero partager carte visite",
        "vault" to "coffre fort cache secret code verrou prive chiffre",
        "renamer" to "renommer nom fichiers lot date numero",
        "file-renamer" to "renommer nom fichiers lot date numero",
    )

    /** Words that file a tool missing from [byId] in a category (checked in this order). */
    private val guesses: List<Pair<ToolCategory, List<String>>> = listOf(
        ToolCategory.WHATSAPP to listOf("whatsapp", "sticker", "statut", "vocal", "vocaux"),
        ToolCategory.MEDIA to listOf("video", "photo", "musique", "son "),
        ToolCategory.FILES to listOf("fichier", "pdf", "document", "zip"),
        ToolCategory.HANDY to listOf("texte", "emoji", "police"),
    )

    fun of(tool: GulpTool): ToolCategory {
        byId[tool.id]?.let { return it }
        val text = fold(listOf(tool.name, tool.tagline) + tool.tags) + " "
        return guesses.firstOrNull { (_, words) -> words.any { it in text } }?.first ?: ToolCategory.OTHER
    }

    /** True when every word of [query] is found in the tool (name, tagline, tags, category, extra words); accents and case ignored. */
    fun matches(tool: GulpTool, query: String): Boolean {
        val words = fold(listOf(query)).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val text = fold(listOf(tool.name, tool.tagline, of(tool).label, keywords[tool.id].orEmpty()) + tool.tags)
        return words.all { it in text }
    }

    /** The categories that hold at least one of [tools], in chip order. */
    fun present(tools: List<GulpTool>): List<ToolCategory> = tools.map(::of).toSet().let { used -> ToolCategory.entries.filter { it in used } }

    /** "Téléchargeur vidéo · MP4" → "telechargeur video mp4". */
    internal fun fold(parts: List<String>): String = Normalizer.normalize(parts.joinToString(" "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
