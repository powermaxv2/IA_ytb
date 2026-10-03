package com.powermaxv2.ytdownloader.core.files

import java.util.Locale

/**
 * What a file is, as the tools need to know it (which viewer, which conversions). Pure, unit tested.
 *
 * Detection order in [detect]: the first bytes ([sniff], magic numbers) win over the name, the name's extension
 * over the MIME type given by the sending app, and a last look at the bytes ([looksLikeText]) tells text from
 * binary. Read [HEAD_SIZE] bytes for the head ([FileIntake.readHead] does).
 */
enum class FileKind {
    IMAGE, VIDEO, AUDIO, PDF, TEXT, CODE, JSON, MARKDOWN, CSV, ZIP, OTHER;

    /** Text-like kinds the preview shows as text (and whose size is capped when read). */
    val isText: Boolean get() = this == TEXT || this == CODE || this == JSON || this == MARKDOWN || this == CSV

    /** Audio / video / image: the kinds FFmpeg or the image decoder handle. */
    val isMedia: Boolean get() = this == IMAGE || this == VIDEO || this == AUDIO

    companion object {
        /** Bytes to read from the start of a file for [detect] (magic numbers only need ~40, text detection more). */
        const val HEAD_SIZE = 4096

        private val imageExt = setOf("png", "jpg", "jpeg", "jpe", "gif", "webp", "bmp", "dib", "tif", "tiff", "heic", "heif", "avif", "ico")
        private val videoExt = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "3g2", "wmv", "flv", "mpg", "mpeg", "m2ts", "mts", "ogv", "vob")
        private val audioExt = setOf("mp3", "m4a", "m4b", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "aif", "aiff", "amr", "mka", "weba", "mid", "midi")
        private val zipExt = setOf("zip", "apk", "jar", "aar", "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "xapk", "apks")
        private val textExt = setOf("txt", "text", "log", "ini", "cfg", "conf", "srt", "vtt", "ass", "nfo", "env", "rtf")
        private val codeExt = setOf(
            "kt", "kts", "java", "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "swift", "py", "rb", "php", "js", "mjs",
            "cjs", "jsx", "tsx", "html", "htm", "xhtml", "css", "scss", "less", "xml", "svg", "yaml", "yml", "toml", "sh",
            "bash", "zsh", "fish", "sql", "gradle", "properties", "lua", "dart", "r", "pl", "ps1", "bat", "cmd", "vue",
            "m", "mm", "scala", "groovy", "dockerfile", "makefile", "tex",
        )

        /** Kind of a file from its first bytes, its [name] and the [mime] type the sender gave (may be null). */
        fun detect(name: String, mime: String?, head: ByteArray): FileKind {
            val ext = SafeNames.extensionOf(name)
            sniff(head)?.let { sniffed ->
                // Same container, other use: Matroska / Ogg audio-only files, Ogg video.
                if (sniffed.kind == VIDEO && ext in audioExt) return AUDIO
                if (sniffed.kind == AUDIO && sniffed.ext == "ogg" && ext == "ogv") return VIDEO
                return sniffed.kind
            }
            // ".ts" is TypeScript or an MPEG transport stream: the bytes decide.
            if (ext == "ts") return if (looksLikeText(head)) CODE else VIDEO
            fromExtension(ext)?.let { return it }
            fromMime(mime)?.let { return it }
            return if (looksLikeText(head)) TEXT else OTHER
        }

        /** Kind of an extension ("jpg", no dot, any case), or null when it says nothing. */
        fun fromExtension(ext: String): FileKind? = when (ext.lowercase(Locale.ROOT)) {
            in imageExt -> IMAGE
            in videoExt -> VIDEO
            in audioExt -> AUDIO
            "pdf" -> PDF
            "json", "geojson", "jsonl", "ndjson" -> JSON
            "md", "markdown", "mdown", "mkd" -> MARKDOWN
            "csv", "tsv" -> CSV
            in zipExt -> ZIP
            in textExt -> TEXT
            in codeExt -> CODE
            else -> null
        }

        /** Kind of a MIME type, or null for a missing / generic one. */
        fun fromMime(mime: String?): FileKind? {
            val m = mime?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim() ?: return null
            return when {
                m.isEmpty() || m == MimeTypes.OCTET_STREAM || m == "*/*" -> null
                m.startsWith("image/svg") -> CODE
                m.startsWith("image/") -> IMAGE
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                m == "application/pdf" -> PDF
                m == "application/json" || m.endsWith("+json") -> JSON
                m == "text/markdown" || m == "text/x-markdown" -> MARKDOWN
                m == "text/csv" || m == "text/comma-separated-values" || m == "text/tab-separated-values" -> CSV
                m == "application/zip" || m == "application/x-zip-compressed" || m == "application/vnd.android.package-archive" ||
                    m == "application/java-archive" || m == "application/epub+zip" || m.startsWith("application/vnd.openxmlformats") ||
                    m.startsWith("application/vnd.oasis.opendocument") -> ZIP
                m == "text/html" || m == "text/css" || m == "text/xml" || m == "application/xml" || m.endsWith("+xml") ||
                    m.startsWith("text/x-") || m == "application/javascript" || m == "text/javascript" ||
                    m == "application/x-sh" || m == "application/x-yaml" || m == "application/yaml" -> CODE
                m.startsWith("text/") -> TEXT
                else -> null
            }
        }

        /**
         * Kind and usual extension from magic numbers at the start of a file, or null when no known signature
         * matches. Recognised: PNG, JPEG, GIF, WebP, BMP, TIFF, HEIC / AVIF and MP4 / MOV / M4A / 3GP (ISO BMFF
         * `ftyp` brands), Matroska / WebM (EBML), AVI and WAV (RIFF), MP3 (ID3 or MPEG frame sync), AAC (ADTS),
         * FLAC, Ogg (Opus), PDF, ZIP (`PK\x03\x04`, also docx / xlsx / apk / epub, which are ZIP files).
         */
        fun sniff(head: ByteArray): Sniffed? {
            val h = head
            fun at(offset: Int, vararg bytes: Int): Boolean =
                h.size >= offset + bytes.size && bytes.indices.all { (h[offset + it].toInt() and 0xFF) == bytes[it] }
            fun ascii(offset: Int, text: String): Boolean =
                h.size >= offset + text.length && text.indices.all { (h[offset + it].toInt() and 0xFF) == text[it].code }

            return when {
                at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Sniffed(IMAGE, "png")
                at(0, 0xFF, 0xD8, 0xFF) -> Sniffed(IMAGE, "jpg")
                ascii(0, "GIF87a") || ascii(0, "GIF89a") -> Sniffed(IMAGE, "gif")
                ascii(0, "RIFF") && ascii(8, "WEBP") -> Sniffed(IMAGE, "webp")
                ascii(0, "RIFF") && ascii(8, "AVI ") -> Sniffed(VIDEO, "avi")
                ascii(0, "RIFF") && ascii(8, "WAVE") -> Sniffed(AUDIO, "wav")
                // "BM", then the file size, then 4 reserved bytes that are always zero.
                ascii(0, "BM") && at(6, 0, 0, 0, 0) -> Sniffed(IMAGE, "bmp")
                at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) -> Sniffed(IMAGE, "tif")
                ascii(4, "ftyp") -> ftyp(h)
                at(0, 0x1A, 0x45, 0xDF, 0xA3) -> Sniffed(VIDEO, if (contains(h, "webm", limit = 64)) "webm" else "mkv")
                ascii(0, "%PDF") -> Sniffed(PDF, "pdf")
                at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) ->
                    Sniffed(ZIP, if (ascii(30, "mimetypeapplication/epub+zip")) "epub" else "zip")
                ascii(0, "fLaC") -> Sniffed(AUDIO, "flac")
                ascii(0, "OggS") -> Sniffed(AUDIO, if (contains(h, "OpusHead", limit = 128)) "opus" else "ogg")
                ascii(0, "ID3") -> Sniffed(AUDIO, "mp3")
                // ADTS AAC: 12-bit sync, layer bits 00.
                h.size >= 2 && at(0, 0xFF) && (h[1].toInt() and 0xF6) == 0xF0 -> Sniffed(AUDIO, "aac")
                // MPEG audio frame: 11-bit sync, layer bits not 00.
                h.size >= 2 && at(0, 0xFF) && (h[1].toInt() and 0xE0) == 0xE0 && (h[1].toInt() and 0x06) != 0 -> Sniffed(AUDIO, "mp3")
                else -> null
            }
        }

        /** ISO base media file: the major brand (bytes 8–11) and the compatible brands tell image, audio and video apart. */
        private fun ftyp(h: ByteArray): Sniffed {
            val boxSize = if (h.size >= 4) {
                ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            } else {
                0
            }
            val end = minOf(maxOf(boxSize, 12), h.size, 256)
            val brands = buildList {
                if (h.size >= 12) add(String(h, 8, 4, Charsets.ISO_8859_1))
                var i = 16
                while (i + 4 <= end) {
                    add(String(h, i, 4, Charsets.ISO_8859_1))
                    i += 4
                }
            }
            val major = brands.firstOrNull().orEmpty()
            return when {
                major == "avif" || major == "avis" -> Sniffed(IMAGE, "avif")
                major in heicBrands -> Sniffed(IMAGE, if ("avif" in brands) "avif" else "heic")
                major == "qt  " -> Sniffed(VIDEO, "mov")
                major == "M4A " || major == "M4B " || major == "M4P " || major == "F4A " -> Sniffed(AUDIO, "m4a")
                major.startsWith("3g") -> Sniffed(VIDEO, "3gp")
                else -> Sniffed(VIDEO, "mp4")
            }
        }

        private val heicBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")

        private fun contains(h: ByteArray, text: String, limit: Int): Boolean {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            val last = minOf(h.size, limit) - bytes.size
            for (i in 0..last) {
                if (bytes.indices.all { h[i + it] == bytes[it] }) return true
            }
            return false
        }

        /**
         * Whether [head] reads as text: no NUL byte (unless it starts with a UTF-16 byte order mark), and at most
         * 5 % of control characters other than tab, line breaks, form feed and escape. Bytes ≥ 0x80 are accepted
         * (UTF-8 or Latin-1 accents). An empty head counts as text (an empty file).
         */
        fun looksLikeText(head: ByteArray): Boolean {
            if (head.isEmpty()) return true
            if (head.size >= 2) {
                val b0 = head[0].toInt() and 0xFF
                val b1 = head[1].toInt() and 0xFF
                if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return true
            }
            var suspicious = 0
            for (b in head) {
                val c = b.toInt() and 0xFF
                when {
                    c == 0 -> return false
                    c == 0x09 || c == 0x0A || c == 0x0D || c == 0x0C || c == 0x1B || c == 0x08 -> Unit
                    c < 0x20 || c == 0x7F -> suspicious++
                }
            }
            return suspicious * 20 <= head.size
        }
    }
}

/** Result of [FileKind.sniff]: the kind and the extension that matches the bytes ("jpg", "heic", "mkv"…). */
data class Sniffed(val kind: FileKind, val ext: String)
