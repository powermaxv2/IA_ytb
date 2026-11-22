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
        // Tools of 1.14.0.
        ToolRegistry.STICKERS to ToolCategory.WHATSAPP,
        ToolRegistry.STATUSES to ToolCategory.WHATSAPP,
        ToolRegistry.STATUS_SPLIT to ToolCategory.WHATSAPP,
        ToolRegistry.VOICE_TEXT to ToolCategory.WHATSAPP,
        ToolRegistry.CLEANER to ToolCategory.WHATSAPP,
        ToolRegistry.DIRECT_CHAT to ToolCategory.WHATSAPP,
        ToolRegistry.CHAT_READER to ToolCategory.WHATSAPP,
        ToolRegistry.STYLED_TEXT to ToolCategory.HANDY,
        // Tools of 1.16.0.
        ToolRegistry.QUICK_REPLIES to ToolCategory.WHATSAPP,
        ToolRegistry.PROFILE_FIT to ToolCategory.WHATSAPP,
        ToolRegistry.SIGN_PDF to ToolCategory.FILES,
        ToolRegistry.WATERMARK to ToolCategory.FILES,
        ToolRegistry.IMAGE_RESIZER to ToolCategory.MEDIA,
        ToolRegistry.PHOTO_DUPLICATES to ToolCategory.FILES,
        ToolRegistry.SLIDESHOW to ToolCategory.MEDIA,
        ToolRegistry.MY_LINK to ToolCategory.WHATSAPP,
        ToolRegistry.RENAMER to ToolCategory.FILES,
        ToolRegistry.VAULT to ToolCategory.HANDY,
        // Tools of 1.17.0.
        ToolRegistry.VOICE_RECORDER to ToolCategory.MEDIA,
        ToolRegistry.BILL_SPLIT to ToolCategory.HANDY,
        ToolRegistry.UNIT_CONVERTER to ToolCategory.HANDY,
        ToolRegistry.LEVEL to ToolCategory.HANDY,
        ToolRegistry.TEXT_COUNTER to ToolCategory.HANDY,
        ToolRegistry.GIF_MAKER to ToolCategory.MEDIA,
        ToolRegistry.COLLAGE to ToolCategory.MEDIA,
        ToolRegistry.PHOTO_BLUR to ToolCategory.MEDIA,
        ToolRegistry.RINGTONES to ToolCategory.MEDIA,
        ToolRegistry.FRAME_GRAB to ToolCategory.MEDIA,
        ToolRegistry.VIDEO_SPEED to ToolCategory.MEDIA,
        ToolRegistry.PDF_IMAGES to ToolCategory.FILES,
        ToolRegistry.READ_ALOUD to ToolCategory.HANDY,
        ToolRegistry.PASSWORDS to ToolCategory.HANDY,
        ToolRegistry.COLOR_PICKER to ToolCategory.HANDY,
        ToolRegistry.TIMER to ToolCategory.HANDY,
    )

    /** Extra words people may type to find a tool (synonyms, formats, sites), on top of its name, tagline and tags. */
    internal val keywords: Map<String, String> = mapOf(
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
        ToolRegistry.VOICE_TEXT to "vocal vocaux transcrire transcription audio parole texte opus whatsapp dictee",
        ToolRegistry.CLEANER to "nettoyer nettoyeur menage espace stockage supprimer gros fichiers doublons whatsapp memoire",
        ToolRegistry.STICKERS to "sticker stickers autocollant autocollants pack detourage fond transparent webp whatsapp",
        ToolRegistry.STYLED_TEXT to "texte style police font gras italique barre gothique unicode bio pseudo whatsapp",
        ToolRegistry.STATUSES to "statut statuts story stories sauvegarder enregistrer garder 24h whatsapp",
        ToolRegistry.STATUS_SPLIT to "decouper couper statut statuts 30s 60s 1min video tronconner morceaux whatsapp",
        ToolRegistry.DIRECT_CHAT to "numero telephone contact inconnu annonce leboncoin message direct ecrire lien wa me",
        ToolRegistry.CHAT_READER to "lire relire lecteur export discussion conversation historique recherche imprimer pdf archive",
        ToolRegistry.QUICK_REPLIES to "reponse rapide message tout fait modele copier envoyer raccourci texte",
        ToolRegistry.PROFILE_FIT to "photo profil avatar rond cercle entiere recadrer fond flou",
        ToolRegistry.SIGN_PDF to "signer signature contrat document pdf paraphe doigt",
        ToolRegistry.WATERMARK to "filigrane copie identite carte location dossier tampon texte photo pdf",
        ToolRegistry.IMAGE_RESIZER to "redimensionner reduire agrandir taille dimension pixels photo image lot",
        ToolRegistry.PHOTO_DUPLICATES to "doublons double identique ressemblante galerie trier supprimer place photo",
        ToolRegistry.SLIDESHOW to "diaporama montage video photos musique transition souvenir",
        ToolRegistry.MY_LINK to "lien qr code whatsapp partager carte visite wa me",
        ToolRegistry.RENAMER to "renommer nom fichiers lot date compteur",
        ToolRegistry.VAULT to "coffre fort cache secret code verrou prive chiffre",
        ToolRegistry.VOICE_RECORDER to "enregistrer enregistreur dictaphone memo vocal audio micro note voix",
        ToolRegistry.BILL_SPLIT to "addition note resto restaurant partager diviser pourboire amis payer rembourser tricount",
        ToolRegistry.UNIT_CONVERTER to "unite mesure longueur poids temperature vitesse cuisine recette miles celsius fahrenheit metre kilo litre surface volume octet duree",
        ToolRegistry.LEVEL to "niveau bulle equerre horizontal vertical etagere cadre tableau inclinaison pente degre bricolage",
        ToolRegistry.TEXT_COUNTER to "compter mots caracteres lettres longueur majuscules minuscules casse lecture tweet sms",
        ToolRegistry.GIF_MAKER to "gif anime animation boucle video photos meme",
        ToolRegistry.COLLAGE to "collage montage mosaique grille plusieurs photos cadre assembler",
        ToolRegistry.PHOTO_BLUR to "flouter flou cacher visage plaque masquer pixeliser anonyme photo",
        ToolRegistry.RINGTONES to "sonnerie couper audio musique mp3 extrait alarme notification",
        ToolRegistry.FRAME_GRAB to "capture image video photo extraire arret sur image instant",
        ToolRegistry.VIDEO_SPEED to "vitesse ralenti accelere video slow motion timelapse",
        ToolRegistry.PDF_IMAGES to "pdf images pages jpg png extraire convertir",
        ToolRegistry.READ_ALOUD to "lire voix haute lecture synthese vocale ecouter texte parler",
        ToolRegistry.PASSWORDS to "mot de passe generateur securite aleatoire phrase secrete",
        ToolRegistry.COLOR_PICKER to "couleur pipette hex rgb palette teinte peinture",
        ToolRegistry.TIMER to "minuteur chrono chronometre compte rebours temps cuisson sport tours",
    )

    internal fun hasCategory(id: String): Boolean = id in byId

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
