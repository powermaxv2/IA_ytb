package com.powermaxv2.ytdownloader.core

import android.net.Uri

enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TWITTER("X"),
    TIKTOK("TikTok"),
    SNAPCHAT("Snapchat"),
    FACEBOOK("Facebook"),
    BEREAL("BeReal"),
    OTHER("Lien"),
}

object Links {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val bareLinkRegex = Regex(
        """^(www\.|m\.|mobile\.|music\.|vm\.|vt\.)?""" +
            """(youtube\.com|youtu\.be|instagram\.com|x\.com|twitter\.com|tiktok\.com|snapchat\.com|facebook\.com|bereal\.com|bere\.al)/\S+$""",
        RegexOption.IGNORE_CASE,
    )

    /** First http(s) link found in a shared or pasted text ("Regarde ça : https://youtu.be/…"). */
    fun extract(text: String?): String? {
        val match = text?.let { urlRegex.find(it) } ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '!', '?', ';', '»')
    }

    /** Returns a clean https:// link, or null if the text contains no usable web link. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        val raw = extract(trimmed) ?: trimmed.takeIf { bareLinkRegex.matches(it) }?.let { "https://$it" } ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return if (scheme == "http") "https" + raw.substring(4) else raw
    }

    fun platform(url: String?): Platform {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase() ?: return Platform.OTHER
        fun isHost(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            host == "youtu.be" || isHost("youtube.com") -> Platform.YOUTUBE
            host == "instagr.am" || isHost("instagram.com") -> Platform.INSTAGRAM
            isHost("x.com") || isHost("twitter.com") -> Platform.TWITTER
            isHost("tiktok.com") -> Platform.TIKTOK
            isHost("snapchat.com") -> Platform.SNAPCHAT
            isHost("facebook.com") -> Platform.FACEBOOK
            host == "bere.al" || isHost("bereal.com") -> Platform.BEREAL
            else -> Platform.OTHER
        }
    }

    fun platformOfText(text: String): Platform = platform(normalize(text))
}
