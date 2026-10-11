package com.powermaxv2.ytdownloader.core.qr

import java.util.Locale

// What a scanned code means (a link, a Wi-Fi network, a contact…) and the text written into a code Gulp creates.
// Pure Kotlin: no Android class, so the unit tests run on the JVM.

/** Wi-Fi security, as the WIFI: text of a QR code names it. */
enum class WifiSecurity(val code: String, val label: String) {
    WPA("WPA", "WPA / WPA2 / WPA3"),
    WEP("WEP", "WEP (ancien)"),
    OPEN("nopass", "Sans mot de passe"),
}

/** The meaning of a scanned code. [raw] is always the exact text read. */
sealed class QrPayload {
    abstract val raw: String

    /** A web address (http or https only: other schemes are shown as text, never opened). */
    data class Link(override val raw: String, val url: String) : QrPayload()

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String,
        val security: WifiSecurity,
        val hidden: Boolean = false,
    ) : QrPayload()

    data class Contact(
        override val raw: String,
        val name: String,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val organization: String = "",
        val url: String = "",
    ) : QrPayload()

    data class Email(override val raw: String, val address: String, val subject: String = "", val body: String = "") : QrPayload()

    data class Phone(override val raw: String, val number: String) : QrPayload()

    data class Sms(override val raw: String, val number: String, val message: String = "") : QrPayload()

    /** A place: latitude, longitude. */
    data class Geo(override val raw: String, val latitude: Double, val longitude: Double) : QrPayload()

    /** A product barcode (EAN-13, UPC…): [format] is the barcode's name. */
    data class Product(override val raw: String, val code: String, val format: String) : QrPayload()

    data class Text(override val raw: String) : QrPayload()

    /** Short name of the kind of code, shown on the result badge. */
    val kindLabel: String
        get() = when (this) {
            is Link -> "Lien"
            is Wifi -> "Réseau Wi-Fi"
            is Contact -> "Contact"
            is Email -> "E-mail"
            is Phone -> "Téléphone"
            is Sms -> "SMS"
            is Geo -> "Lieu"
            is Product -> "Code-barres"
            is Text -> "Texte"
        }

    /** One line that sums the code up (history, share text). */
    val title: String
        get() = when (this) {
            is Link -> url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            is Wifi -> ssid.ifEmpty { "Réseau sans nom" }
            is Contact -> name.ifEmpty { phones.firstOrNull() ?: emails.firstOrNull() ?: "Contact" }
            is Email -> address
            is Phone -> number
            is Sms -> number
            is Geo -> String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)
            is Product -> code
            is Text -> raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { "Texte vide" }
        }

    companion object {
        /** Formats read by the scanner that are product barcodes, not 2D codes carrying text. */
        val productFormats = setOf("EAN_13", "EAN_8", "UPC_A", "UPC_E")

        /** What [raw] means; [format] is the barcode format name of the scanner ("QR_CODE", "EAN_13"…), if known. */
        fun parse(raw: String, format: String? = null): QrPayload {
            val text = raw.trim()
            if (format in productFormats && text.all { it.isDigit() }) return Product(raw, text, prettyFormat(format!!))
            val upper = text.uppercase(Locale.ROOT)
            return when {
                upper.startsWith("WIFI:") -> parseWifi(raw, text)
                upper.startsWith("BEGIN:VCARD") -> parseVCard(raw, text)
                upper.startsWith("MECARD:") -> parseMeCard(raw, text)
                upper.startsWith("MAILTO:") -> parseMailto(raw, text)
                upper.startsWith("MATMSG:") -> parseMatmsg(raw, text)
                upper.startsWith("TEL:") -> text.substring(4).trim().takeIf { it.isNotEmpty() }?.let { Phone(raw, it) } ?: Text(raw)
                upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> parseSms(raw, text)
                upper.startsWith("GEO:") -> parseGeo(raw, text)
                else -> linkOrNull(raw, text) ?: Text(raw)
            }
        }

        private fun prettyFormat(format: String): String = when (format) {
            "EAN_13" -> "EAN-13"
            "EAN_8" -> "EAN-8"
            "UPC_A" -> "UPC-A"
            "UPC_E" -> "UPC-E"
            else -> format.replace('_', ' ')
        }

        private val webAddress = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,}(:\d{1,5})?([/?#]\S*)?$""", RegexOption.IGNORE_CASE)

        /** A web link: http(s) with a host, or a bare "www.site.fr" (opened as https). No spaces, no other scheme. */
        private fun linkOrNull(raw: String, text: String): Link? {
            if (text.any { it.isWhitespace() }) return null
            val lower = text.lowercase(Locale.ROOT)
            return when {
                lower.startsWith("https://") || lower.startsWith("http://") ->
                    if (webAddress.matches(text) || text.substringAfter("://").substringBefore('/').contains('.')) Link(raw, text) else null
                lower.startsWith("www.") && webAddress.matches(text) -> Link(raw, "https://$text")
                else -> null
            }
        }

        /**
         * Splits "K:v;K2:v2;;" fields where `\` escapes `;` `,` `:` `\` and `"` (WIFI: and MECARD: texts). Returns
         * (key upper-cased, value) pairs in order.
         */
        internal fun fields(body: String): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val current = StringBuilder()
            var key: String? = null
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' && i + 1 < body.length -> {
                        current.append(body[i + 1])
                        i++
                    }
                    c == ':' && key == null -> {
                        key = current.toString().trim().uppercase(Locale.ROOT)
                        current.clear()
                    }
                    c == ';' -> {
                        if (key != null) out += key to current.toString()
                        key = null
                        current.clear()
                    }
                    else -> current.append(c)
                }
                i++
            }
            if (key != null) out += key to current.toString()
            return out
        }

        private fun parseWifi(raw: String, text: String): QrPayload {
            val f = fields(text.substring(5))
            fun get(k: String) = f.firstOrNull { it.first == k }?.second.orEmpty()
            val ssid = get("S")
            if (ssid.isEmpty() && get("P").isEmpty()) return Text(raw)
            val type = get("T").uppercase(Locale.ROOT)
            val password = get("P")
            val security = when {
                type == "WEP" -> WifiSecurity.WEP
                type == "NOPASS" || (type.isEmpty() && password.isEmpty()) -> WifiSecurity.OPEN
                else -> WifiSecurity.WPA
            }
            return Wifi(raw, ssid, if (security == WifiSecurity.OPEN) "" else password, security, get("H").equals("true", ignoreCase = true))
        }

        private fun parseMeCard(raw: String, text: String): QrPayload {
            val f = fields(text.substring(7))
            val name = f.firstOrNull { it.first == "N" }?.second.orEmpty().let { n ->
                // MECARD names are "Last,First".
                if (',' in n) n.split(',', limit = 2).let { "${it[1].trim()} ${it[0].trim()}".trim() } else n.trim()
            }
            return Contact(
                raw,
                name = name,
                phones = f.filter { it.first == "TEL" }.map { it.second.trim() }.filter { it.isNotEmpty() },
                emails = f.filter { it.first == "EMAIL" }.map { it.second.trim() }.filter { it.isNotEmpty() },
                organization = f.firstOrNull { it.first == "ORG" }?.second?.trim().orEmpty(),
                url = f.firstOrNull { it.first == "URL" }?.second?.trim().orEmpty(),
            )
        }

        private fun parseVCard(raw: String, text: String): QrPayload {
            // Unfold continuation lines (a line starting with a space continues the previous one).
            val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").lines()
            var fn = ""
            var n = ""
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            var org = ""
            var url = ""
            for (line in lines) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase(Locale.ROOT)
                val value = vcardUnescape(line.substring(colon + 1)).trim()
                if (value.isEmpty()) continue
                when (name) {
                    "FN" -> fn = value
                    "N" -> n = value.split(';').let { parts ->
                        listOfNotNull(parts.getOrNull(1), parts.getOrNull(0)).joinToString(" ") { it.trim() }.trim()
                    }
                    "TEL" -> phones += value.removePrefix("tel:")
                    "EMAIL" -> emails += value
                    "ORG" -> org = value.replace(';', ' ').trim()
                    "URL" -> url = value
                }
            }
            return Contact(raw, fn.ifEmpty { n }, phones, emails, org, url)
        }

        private fun vcardUnescape(value: String): String =
            value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

        private fun parseMailto(raw: String, text: String): QrPayload {
            val rest = text.substring(7)
            val address = percentDecode(rest.substringBefore('?')).trim()
            val query = rest.substringAfter('?', "")
            val params = query.split('&').filter { '=' in it }.associate {
                it.substringBefore('=').lowercase(Locale.ROOT) to percentDecode(it.substringAfter('='))
            }
            if (address.isEmpty() && params.isEmpty()) return Text(raw)
            return Email(raw, address, params["subject"].orEmpty(), params["body"].orEmpty())
        }

        private fun parseMatmsg(raw: String, text: String): QrPayload {
            val f = fields(text.substring(7))
            fun get(k: String) = f.firstOrNull { it.first == k }?.second.orEmpty()
            return if (get("TO").isEmpty()) Text(raw) else Email(raw, get("TO").trim(), get("SUB"), get("BODY"))
        }

        private fun parseSms(raw: String, text: String): QrPayload {
            val rest = text.substringAfter(':')
            val number = rest.substringBefore(':').substringBefore('?').trim()
            val message = when {
                ':' in rest -> rest.substringAfter(':')
                "body=" in rest -> percentDecode(rest.substringAfter("body="))
                else -> ""
            }
            return if (number.isEmpty()) Text(raw) else Sms(raw, number, message)
        }

        private fun parseGeo(raw: String, text: String): QrPayload {
            val coords = text.substring(4).substringBefore('?').substringBefore(';').split(',')
            val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) return Text(raw)
            return Geo(raw, lat, lon)
        }

        /** %XX decoding (UTF-8), with "+" kept as is (mailto bodies use %20). Invalid sequences are left untouched. */
        internal fun percentDecode(s: String): String {
            if ('%' !in s) return s
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val hi = if (c == '%' && i + 2 < s.length) s[i + 1].digitToIntOrNull(16) else null
                val lo = if (hi != null) s[i + 2].digitToIntOrNull(16) else null
                val hex = if (hi != null && lo != null) hi * 16 + lo else null
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                } else {
                    bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                    i++
                }
            }
            return bytes.toString("UTF-8")
        }
    }
}

// MARK: - Creating a code

/** What the user can put into a code Gulp creates. */
enum class QrKind(val label: String) {
    LINK("Lien"),
    TEXT("Texte"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
}

/** The form of the "Créer" tab; [text] tells what goes into the code, or null while it is still empty. */
data class QrDraft(
    val kind: QrKind = QrKind.LINK,
    val link: String = "",
    val text: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
) {
    /** The text to encode, or null when the form is not filled enough to make a useful code. */
    fun encoded(): String? = when (kind) {
        QrKind.LINK -> link.trim().takeIf { it.isNotEmpty() }?.let { url ->
            if (url.contains("://") || url.startsWith("mailto:", ignoreCase = true)) url else "https://$url"
        }
        QrKind.TEXT -> text.takeIf { it.isNotBlank() }
        QrKind.WIFI -> ssid.takeIf { it.isNotEmpty() }?.let {
            val sec = if (password.isEmpty()) WifiSecurity.OPEN else security
            buildString {
                append("WIFI:T:").append(sec.code)
                append(";S:").append(escape(ssid))
                if (sec != WifiSecurity.OPEN) append(";P:").append(escape(password))
                append(";;")
            }
        }
        QrKind.CONTACT -> if (name.isBlank() && phone.isBlank() && email.isBlank()) null else buildString {
            // vCard 3.0: read by the cameras of Android and iPhone alike.
            append("BEGIN:VCARD\nVERSION:3.0\n")
            val n = name.trim()
            val last = if (' ' in n) n.substringAfterLast(' ') else n
            val first = if (' ' in n) n.substringBeforeLast(' ') else ""
            append("N:").append(vcardEscape(last)).append(';').append(vcardEscape(first)).append(";;;\n")
            append("FN:").append(vcardEscape(n)).append('\n')
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:").append(vcardEscape(phone.trim())).append('\n')
            if (email.isNotBlank()) append("EMAIL:").append(vcardEscape(email.trim())).append('\n')
            append("END:VCARD")
        }
    }

    /** Hint under the code: what someone who scans it gets. */
    val effect: String
        get() = when (kind) {
            QrKind.LINK -> "Un scan ouvre le site, sans rien taper."
            QrKind.TEXT -> "Un scan affiche ce texte, prêt à copier."
            QrKind.WIFI -> "Tes invités le scannent pour se connecter, sans taper le mot de passe."
            QrKind.CONTACT -> "Un scan propose d'ajouter le contact au carnet d'adresses."
        }

    /** A name for the saved image: "QR Wi-Fi Maison-5G.png". */
    val fileName: String
        get() {
            val what = when (kind) {
                QrKind.LINK -> link.trim().substringAfter("://").substringBefore('/').ifEmpty { "lien" }
                QrKind.TEXT -> text.trim().take(24).ifEmpty { "texte" }
                QrKind.WIFI -> "Wi-Fi ${ssid.trim()}"
                QrKind.CONTACT -> name.trim().ifEmpty { "contact" }
            }
            val safe = what.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().take(40)
            return "QR $safe.png"
        }

    companion object {
        private val wifiSpecial = setOf('\\', ';', ',', ':', '"')

        internal fun escape(s: String): String = buildString { s.forEach { if (it in wifiSpecial) append('\\'); append(it) } }

        internal fun vcardEscape(s: String): String =
            s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
    }
}
