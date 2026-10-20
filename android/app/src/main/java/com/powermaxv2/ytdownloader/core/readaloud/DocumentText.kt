package com.powermaxv2.ytdownloader.core.readaloud

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** The document cannot be read as text; the message is French and shown as is. */
class DocumentTextError(message: String) : IOException(message)

// The words of Word (.docx), OpenDocument (.odt) and EPUB books: all three are ZIP files of XML, read here
// without any library. Each entry is capped so a "ZIP bomb" cannot fill the memory. Pure JVM: tested.
object DocumentText {
    /** Largest XML entry read (uncompressed). */
    const val ENTRY_MAX = 24L * 1024 * 1024

    /** Largest sum of entries read for one book. */
    const val TOTAL_MAX = 96L * 1024 * 1024

    /** Paragraphs of a Word document, in order (field codes, deleted text and comments left out). */
    fun docx(file: File): String = ZipFile(file).use { zip ->
        val xml = entry(zip, "word/document.xml") ?: throw DocumentTextError("Ce fichier Word est vide ou abîmé.")
        val text = unindent(xml)
            .replace(Regex("""(?s)<w:instrText\b[^>]*>.*?</w:instrText>"""), "")
            .replace(Regex("""(?s)<w:delText\b[^>]*>.*?</w:delText>"""), "")
            .replace(Regex("""<w:tab\s*/>"""), " ")
            .replace(Regex("""<w:(br|cr)\b[^>]*/>"""), "\n")
            .replace(Regex("""</w:p>"""), "\n")
            .replace(Regex("""<[^>]*>"""), "")
        SpeechText.entities(text)
    }

    /** Paragraphs and titles of an OpenDocument text (LibreOffice, Google Docs export). */
    fun odt(file: File): String = ZipFile(file).use { zip ->
        val xml = entry(zip, "content.xml") ?: throw DocumentTextError("Ce document est vide ou abîmé.")
        val text = unindent(xml)
            .replace(Regex("""(?s)<office:annotation\b.*?</office:annotation>"""), "")
            .replace(Regex("""<text:s\b[^>]*/>"""), " ")
            .replace(Regex("""<text:tab\b[^>]*/>"""), " ")
            .replace(Regex("""<text:line-break\b[^>]*/>"""), "\n")
            .replace(Regex("""</text:(p|h)>"""), "\n")
            .replace(Regex("""<[^>]*>"""), "")
        SpeechText.entities(text)
    }

    /**
     * Chapters of an EPUB book in reading order (the "spine" of its OPF file), each as a web page's words, one empty
     * line apart. A book without a readable spine is read page by page in name order.
     */
    fun epub(file: File): String = ZipFile(file).use { zip ->
        var total = 0L
        fun read(name: String): String? = entry(zip, name)?.also {
            total += it.length
            if (total > TOTAL_MAX) throw DocumentTextError("Ce livre est trop gros pour être lu.")
        }
        val pages = spine(zip, ::read).ifEmpty {
            zip.entries().toList().map { it.name }
                .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                .sorted()
        }
        if (pages.isEmpty()) throw DocumentTextError("Ce livre ne contient aucune page lisible.")
        pages.mapNotNull { read(it)?.let(SpeechText::fromHtml) }.joinToString("\n\n")
    }

    /** Paths (inside the ZIP) of the book's pages, in reading order; empty when the OPF cannot be followed. */
    private fun spine(zip: ZipFile, read: (String) -> String?): List<String> {
        val container = read("META-INF/container.xml") ?: return emptyList()
        val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""").find(container)?.groupValues?.get(1) ?: return emptyList()
        val opf = read(opfPath) ?: return emptyList()
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = HashMap<String, String>()
        Regex("""<(?:\w+:)?item\b[^>]*>""").findAll(opf).forEach { m ->
            val tag = m.value
            val id = attr(tag, "id") ?: return@forEach
            val href = attr(tag, "href") ?: return@forEach
            val type = attr(tag, "media-type").orEmpty()
            if (type.contains("html")) manifest[id] = href
        }
        return Regex("""<(?:\w+:)?itemref\b[^>]*>""").findAll(opf).mapNotNull { m ->
            val idref = attr(m.value, "idref") ?: return@mapNotNull null
            if (attr(m.value, "linear") == "no") return@mapNotNull null
            manifest[idref]?.let { resolve(base, it) }
        }.distinct().toList()
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""(?:^|\s)$name\s*=\s*"([^"]*)"""").find(tag)?.groupValues?.get(1)
            ?: Regex("""(?:^|\s)$name\s*=\s*'([^']*)'""").find(tag)?.groupValues?.get(1)

    /** [href] (URL-encoded, maybe with "../" or "#part") from the OPF folder [base], as a ZIP path. */
    internal fun resolve(base: String, href: String): String {
        val clean = java.net.URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
        val parts = ArrayList<String>()
        for (p in (base + clean).split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    /** Text of the ZIP entry [name] (UTF-8), or null when it is missing; throws when it is bigger than [ENTRY_MAX]. */
    /** Line ends and indents between two tags only lay the XML out: they are not words. */
    private fun unindent(xml: String): String = xml.replace(Regex(""">[ \t]*[\r\n][\s]*<"""), "><")

    private fun entry(zip: ZipFile, name: String): String? {
        val e = zip.getEntry(name) ?: return null
        if (e.isDirectory) return null
        if (e.size > ENTRY_MAX) throw DocumentTextError("Ce document est trop gros pour être lu.")
        zip.getInputStream(e).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                // The size in the ZIP's directory can lie: count what really comes out.
                if (total > ENTRY_MAX) throw DocumentTextError("Ce document est trop gros pour être lu.")
                out.write(buffer, 0, n)
            }
            return SpeechText.decode(out.toByteArray())
        }
    }
}
