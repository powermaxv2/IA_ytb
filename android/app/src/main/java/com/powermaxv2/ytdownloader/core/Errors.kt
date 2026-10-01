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
            "unsupported url" in lower ->
                "Aucune vidéo détectée à ce lien : le site n'est pas pris en charge, ou la page ne contient pas de vidéo."
            "cannot parse data" in lower -> "Facebook n'a pas pu lire cette vidéo : elle est en direct, supprimée ou privée."
            "is not a valid url" in lower -> "Ce lien n'est pas valide."
            fromInstagram && ("private" in lower || "follow" in lower) ->
                "Publication d'un compte privé : connectez un compte Instagram qui suit ce compte."
            fromInstagram && (
                "login" in lower || "logged" in lower || "cookies" in lower || "authentication" in lower ||
                    "rate-limit" in lower || "not available" in lower || "401" in lower
                ) ->
                "Instagram demande une connexion : connectez votre compte en haut de l'onglet Instagram."
            "there is no video in this post" in lower || "no video formats found" in lower ->
                "Cette publication ne contient pas de vidéo. Connectez votre compte Instagram pour télécharger les photos."
            "no video could be found in this tweet" in lower ->
                "Ce post X ne contient pas de vidéo (les photos ne sont pas prises en charge)."
            "protected tweet" in lower || "nsfw tweet" in lower ->
                "Ce post X est protégé ou réservé aux adultes : il faudrait un compte X, non pris en charge."
            "twitter api says" in lower || "error(s) while querying api" in lower ->
                "X ne donne pas ce post : il est supprimé, privé ou indisponible."
            "guest token" in lower -> "X refuse l'accès pour le moment. Réessayez plus tard, ou mettez yt-dlp à jour dans les réglages."
            "unable to extract webpage video data" in lower || "unable to extract aweme detail" in lower ->
                "TikTok n'a pas donné cette vidéo : elle est privée ou supprimée, ou c'est un diaporama de photos " +
                    "(non pris en charge). Réessayez, ou mettez yt-dlp à jour dans les réglages."
            "ip address is blocked" in lower -> "Ce site bloque l'accès à cette vidéo depuis votre connexion."
            "do not have permission to view this post" in lower ->
                "Cette vidéo TikTok est privée : il faudrait un compte, non pris en charge."
            "drm" in lower && "protected" in lower -> "Cette vidéo est protégée (DRM) : elle ne peut pas être téléchargée."
            "geo restrict" in lower || "not available in your country" in lower -> "Vidéo indisponible dans votre pays."
            "private video" in lower -> "Cette vidéo est privée."
            "confirm your age" in lower -> "Vidéo soumise à une limite d'âge : elle ne peut pas être téléchargée sans compte."
            "not a bot" in lower -> "YouTube demande une vérification anti-robot : mettez à jour yt-dlp dans les réglages, ou réessayez plus tard."
            "video unavailable" in lower -> "Vidéo indisponible."
            "members-only" in lower || "join this channel" in lower -> "Vidéo réservée aux membres de la chaîne."
            "http error 404" in lower -> "Lien introuvable (404) : la page ou le fichier n'existe plus."
            "http error 403" in lower -> "Accès refusé (403). Mettez à jour yt-dlp dans les réglages."
            "http error 429" in lower -> "Trop de requêtes : le site limite temporairement l'accès. Réessayez plus tard."
            ("unable to download" in lower && "urlopen" in lower) || "failed to resolve" in lower ||
                "network is unreachable" in lower -> "Connexion impossible. Vérifiez votre réseau."
            "no space left" in lower -> "Espace de stockage insuffisant sur le téléphone."
            else -> message
        }
    }
}
