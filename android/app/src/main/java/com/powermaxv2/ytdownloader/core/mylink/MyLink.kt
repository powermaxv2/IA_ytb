package com.powermaxv2.ytdownloader.core.mylink

import com.powermaxv2.ytdownloader.core.direct.Country
import com.powermaxv2.ytdownloader.core.direct.DirectNumber
import com.powermaxv2.ytdownloader.core.direct.DirectNumbers
import com.powermaxv2.ytdownloader.core.qr.QrCodec

// "Mon lien WhatsApp": the user's own number, typed once and kept on the phone, turned into WhatsApp's official
// wa.me link (with a message people find already written when they open it) and into a QR code to share or print.
// The number reading and the link come from Écrire sans enregistrer (core/direct); this only adds what a link meant
// for others needs. Pure Kotlin: tested on the JVM.

/** What the typed number and message give. */
sealed interface MyLink {
    /** No number yet. */
    data object Empty : MyLink

    /** Not a usable number; [reason] is shown under the field. */
    data class Bad(val reason: String) : MyLink

    /**
     * The link is ready: [digits] is the international number without "+", [pretty] the way people read it,
     * [link] the wa.me address. [qrFits] is false when the message makes the link too long for a readable code,
     * [withLogo] when the code is short enough to keep the Gulp logo in the middle.
     */
    data class Ready(
        val digits: String,
        val pretty: String,
        val country: Country?,
        val link: String,
        val qrFits: Boolean,
        val withLogo: Boolean,
        val maybeLandline: Boolean,
    ) : MyLink
}

/** The picture saved or shared: a card to print or post (title, number, code), or the code alone. */
enum class MyImageFormat { CARD, QR }

object MyLinks {
    /**
     * Longest message kept: the link carries it encoded (an "é" takes 6 characters, an emoji 12), and past a few
     * hundred characters the code gets too dense to scan from a printed sheet.
     */
    const val MAX_MESSAGE = 250

    /** The code keeps the Gulp logo in the middle up to this many bytes (same rule as the QR code tool). */
    private const val LOGO_BYTES = 400

    /** Ready-made messages for the person who opens the link. */
    val QUICK: List<String> = listOf(
        "Bonjour ! 👋",
        "Bonjour, je vous écris de la part de votre carte.",
        "Bonjour, je voudrais prendre rendez-vous.",
        "Bonjour, je suis intéressé par votre annonce.",
    )

    fun build(number: String, country: Country, message: String): MyLink = when (val parsed = DirectNumbers.parse(number, country)) {
        DirectNumber.Empty -> MyLink.Empty
        is DirectNumber.Bad -> MyLink.Bad(parsed.reason)
        is DirectNumber.Ok -> {
            val link = DirectNumbers.link(parsed.digits, cleanMessage(message))
            MyLink.Ready(
                digits = parsed.digits,
                pretty = parsed.pretty,
                country = parsed.country,
                link = link,
                qrFits = link.length <= QrCodec.MAX_CHARS,
                withLogo = link.toByteArray(Charsets.UTF_8).size <= LOGO_BYTES,
                maybeLandline = parsed.maybeLandline,
            )
        }
    }

    /** The message as it goes in the link: trimmed and cut at [MAX_MESSAGE], never in the middle of an emoji. */
    fun cleanMessage(message: String): String {
        val text = message.trim()
        if (text.length <= MAX_MESSAGE) return text
        val cut = text.take(MAX_MESSAGE)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /** What "Partager le lien" sends: one friendly line, then the link alone so every app makes it clickable. */
    fun shareText(link: String): String = "Écris-moi sur WhatsApp :\n$link"

    /** The link without "https://", as printed under the code ("wa.me/33612345678"). */
    fun shortLink(digits: String): String = "wa.me/$digits"

    /** Name of the saved picture: which kind, and the last 4 digits so two numbers do not get mixed up. */
    fun fileName(digits: String, format: MyImageFormat): String {
        val end = digits.takeLast(4)
        return when (format) {
            MyImageFormat.CARD -> "gulp-mon-whatsapp-$end.png"
            MyImageFormat.QR -> "gulp-qr-whatsapp-$end.png"
        }
    }

    /** The saved number, given back as "+digits": it then reads the same whatever country is chosen. */
    fun restore(savedDigits: String?): String {
        val digits = savedDigits?.trim().orEmpty()
        return if (digits.length in 8..15 && digits.all(Char::isDigit)) "+$digits" else ""
    }
}
