package com.powermaxv2.ytdownloader.core

import java.util.Locale

object Errors {
    private val extractorPrefix = Regex("""^\[[^\]]+] [^:]+: """)

    /** Turns yt-dlp output into a short, readable French message. */
    fun friendly(lines: List<String>): String {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val raw = cleaned.lastOrNull { it.startsWith("ERROR:") } ?: cleaned.lastOrNull() ?: return "Erreur inconnue."
        val message = raw.removePrefix("ERROR:").trim().replace(extractorPrefix, "")
        val lower = message.lowercase(Locale.ROOT)
        val fromInstagram = "instagram" in raw.lowercase(Locale.ROOT)
        return when {
            "unsupported url" in lower || "is not a valid url" in lower -> "Ce lien n'est pas pris en charge."
            fromInstagram && ("login" in lower || "rate-limit" in lower || "not available" in lower || "401" in lower) ->
                "Instagram demande une connexion : Réglages → Connexion Instagram."
            "there is no video in this post" in lower || "no video formats found" in lower ->
                "Cette publication ne contient pas de vidéo. Connectez-vous à Instagram (Réglages) pour télécharger les photos."
            "private video" in lower -> "Cette vidéo est privée."
            "confirm your age" in lower -> "Vidéo soumise à une limite d'âge : elle ne peut pas être téléchargée sans compte."
            "not a bot" in lower -> "YouTube demande une vérification anti-robot : mettez à jour yt-dlp dans les réglages, ou réessayez plus tard."
            "video unavailable" in lower -> "Vidéo indisponible."
            "members-only" in lower || "join this channel" in lower -> "Vidéo réservée aux membres de la chaîne."
            "http error 403" in lower -> "Accès refusé (403). Mettez à jour yt-dlp dans les réglages."
            "http error 429" in lower -> "Trop de requêtes : le site limite temporairement l'accès. Réessayez plus tard."
            ("unable to download" in lower && "urlopen" in lower) || "failed to resolve" in lower ||
                "network is unreachable" in lower -> "Connexion impossible. Vérifiez votre réseau."
            "no space left" in lower -> "Espace de stockage insuffisant sur le téléphone."
            else -> message
        }
    }
}
