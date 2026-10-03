package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileKindTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)
    private fun pad(head: ByteArray, size: Int = 64) = head.copyOf(maxOf(size, head.size))

    /** An ISO BMFF head: box size 24, "ftyp", [major] brand, minor version, one compatible brand. */
    private fun ftyp(major: String, compatible: String = "isom") =
        bytes(0, 0, 0, 24) + ascii("ftyp") + ascii(major) + bytes(0, 0, 0, 0) + ascii(compatible) + ByteArray(16)

    private fun riff(form: String) = ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii(form) + ByteArray(20)

    @Test fun images() {
        assertEquals(Sniffed(FileKind.IMAGE, "png"), FileKind.sniff(pad(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))))
        assertEquals(Sniffed(FileKind.IMAGE, "jpg"), FileKind.sniff(pad(bytes(0xFF, 0xD8, 0xFF, 0xE0))))
        assertEquals(Sniffed(FileKind.IMAGE, "gif"), FileKind.sniff(pad(ascii("GIF89a"))))
        assertEquals(Sniffed(FileKind.IMAGE, "webp"), FileKind.sniff(riff("WEBP")))
        assertEquals(Sniffed(FileKind.IMAGE, "bmp"), FileKind.sniff(pad(ascii("BM") + bytes(0x36, 0x10, 0, 0, 0, 0, 0, 0, 0x36, 0, 0, 0))))
        assertEquals(Sniffed(FileKind.IMAGE, "tif"), FileKind.sniff(pad(bytes(0x49, 0x49, 0x2A, 0x00))))
        assertEquals(Sniffed(FileKind.IMAGE, "tif"), FileKind.sniff(pad(bytes(0x4D, 0x4D, 0x00, 0x2A))))
        assertEquals(Sniffed(FileKind.IMAGE, "heic"), FileKind.sniff(ftyp("heic", "mif1")))
        assertEquals(Sniffed(FileKind.IMAGE, "heic"), FileKind.sniff(ftyp("mif1", "heic")))
        assertEquals(Sniffed(FileKind.IMAGE, "avif"), FileKind.sniff(ftyp("avif", "mif1")))
        assertEquals(Sniffed(FileKind.IMAGE, "avif"), FileKind.sniff(ftyp("mif1", "avif")))
    }

    @Test fun videoAndAudioContainers() {
        assertEquals(Sniffed(FileKind.VIDEO, "mp4"), FileKind.sniff(ftyp("isom")))
        assertEquals(Sniffed(FileKind.VIDEO, "mp4"), FileKind.sniff(ftyp("mp42")))
        assertEquals(Sniffed(FileKind.VIDEO, "mov"), FileKind.sniff(ftyp("qt  ")))
        assertEquals(Sniffed(FileKind.VIDEO, "3gp"), FileKind.sniff(ftyp("3gp5")))
        assertEquals(Sniffed(FileKind.AUDIO, "m4a"), FileKind.sniff(ftyp("M4A ")))
        assertEquals(Sniffed(FileKind.VIDEO, "mkv"), FileKind.sniff(pad(bytes(0x1A, 0x45, 0xDF, 0xA3) + ascii("\u0093B\u0082\u0088matroska"))))
        assertEquals(Sniffed(FileKind.VIDEO, "webm"), FileKind.sniff(pad(bytes(0x1A, 0x45, 0xDF, 0xA3) + ascii("\u0093B\u0082\u0084webm"))))
        assertEquals(Sniffed(FileKind.VIDEO, "avi"), FileKind.sniff(riff("AVI ")))
        assertEquals(Sniffed(FileKind.AUDIO, "wav"), FileKind.sniff(riff("WAVE")))
        assertEquals(Sniffed(FileKind.AUDIO, "mp3"), FileKind.sniff(pad(ascii("ID3") + bytes(4, 0, 0))))
        assertEquals(Sniffed(FileKind.AUDIO, "mp3"), FileKind.sniff(pad(bytes(0xFF, 0xFB, 0x90, 0x64))))
        assertEquals(Sniffed(FileKind.AUDIO, "aac"), FileKind.sniff(pad(bytes(0xFF, 0xF1, 0x50, 0x80))))
        assertEquals(Sniffed(FileKind.AUDIO, "flac"), FileKind.sniff(pad(ascii("fLaC"))))
        assertEquals(Sniffed(FileKind.AUDIO, "ogg"), FileKind.sniff(pad(ascii("OggS") + ByteArray(24) + ascii("\u0001vorbis"))))
        assertEquals(Sniffed(FileKind.AUDIO, "opus"), FileKind.sniff(pad(ascii("OggS") + ByteArray(24) + ascii("OpusHead"))))
    }

    @Test fun documentsAndArchives() {
        assertEquals(Sniffed(FileKind.PDF, "pdf"), FileKind.sniff(pad(ascii("%PDF-1.7\n"))))
        assertEquals(Sniffed(FileKind.ZIP, "zip"), FileKind.sniff(pad(bytes(0x50, 0x4B, 0x03, 0x04))))
        assertEquals(Sniffed(FileKind.ZIP, "zip"), FileKind.sniff(pad(bytes(0x50, 0x4B, 0x05, 0x06))))
        val epub = bytes(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + ascii("mimetypeapplication/epub+zip")
        assertEquals(Sniffed(FileKind.ZIP, "epub"), FileKind.sniff(epub))
        // Office files and APKs are ZIP files: the bytes say ZIP whatever the name.
        val zipHead = pad(bytes(0x50, 0x4B, 0x03, 0x04))
        assertEquals(FileKind.ZIP, FileKind.detect("rapport.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zipHead))
        assertEquals(FileKind.ZIP, FileKind.detect("budget.xlsx", null, zipHead))
        assertEquals(FileKind.ZIP, FileKind.detect("app.apk", "application/vnd.android.package-archive", zipHead))
    }

    @Test fun shortOrUnknownHeadsDoNotMatch() {
        assertNull(FileKind.sniff(ByteArray(0)))
        assertNull(FileKind.sniff(bytes(0xFF)))
        assertNull(FileKind.sniff(ascii("hello world")))
        // "ftyp" with a truncated head must not crash.
        assertEquals(FileKind.VIDEO, FileKind.sniff(bytes(0, 0, 0, 24) + ascii("ftyp"))?.kind)
        assertEquals(FileKind.VIDEO, FileKind.sniff(bytes(0, 0, 0, 24) + ascii("ftypis"))?.kind)
    }

    @Test fun magicBeatsNameAndMime() {
        val png = pad(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        assertEquals(FileKind.IMAGE, FileKind.detect("photo.txt", "text/plain", png))
        assertEquals(FileKind.PDF, FileKind.detect("download", null, pad(ascii("%PDF-1.4"))))
        // Audio-only Matroska keeps its audio extension.
        assertEquals(FileKind.AUDIO, FileKind.detect("song.mka", null, pad(bytes(0x1A, 0x45, 0xDF, 0xA3))))
        assertEquals(FileKind.VIDEO, FileKind.detect("clip.ogv", null, pad(ascii("OggS"))))
    }

    @Test fun extensionThenMimeThenBytes() {
        val text = ascii("{\"a\": 1}\n")
        assertEquals(FileKind.JSON, FileKind.detect("data.json", null, text))
        assertEquals(FileKind.MARKDOWN, FileKind.detect("README.md", null, ascii("# Titre\n")))
        assertEquals(FileKind.CSV, FileKind.detect("t.csv", null, ascii("a,b\n1,2\n")))
        assertEquals(FileKind.CODE, FileKind.detect("Main.kt", null, ascii("fun main() {}\n")))
        assertEquals(FileKind.CODE, FileKind.detect("index.html", null, ascii("<!doctype html>")))
        assertEquals(FileKind.TEXT, FileKind.detect("notes.txt", null, ascii("bonjour")))
        // ".ts": TypeScript when it reads as text, a transport stream otherwise.
        assertEquals(FileKind.CODE, FileKind.detect("app.ts", null, ascii("const a: number = 1\n")))
        assertEquals(FileKind.VIDEO, FileKind.detect("film.ts", null, bytes(0x47, 0x40, 0x00, 0x10, 0x00, 0x00, 0xB0, 0x0D)))
        // No extension: the sender's MIME type.
        assertEquals(FileKind.JSON, FileKind.detect("data", "application/json", text))
        assertEquals(FileKind.CSV, FileKind.detect("export", "text/comma-separated-values", ascii("a;b")))
        assertEquals(FileKind.MARKDOWN, FileKind.detect("x", "text/markdown; charset=utf-8", ascii("# a")))
        // Nothing known: the bytes.
        assertEquals(FileKind.TEXT, FileKind.detect("LICENSE", null, ascii("MIT License\n")))
        assertEquals(FileKind.TEXT, FileKind.detect("vide", "application/octet-stream", ByteArray(0)))
        assertEquals(FileKind.OTHER, FileKind.detect("blob", "application/octet-stream", bytes(0x00, 0x01, 0x02, 0x03, 0x10)))
    }

    @Test fun textDetection() {
        assertTrue(FileKind.looksLikeText(ascii("Bonjour\tà tous\r\n")))
        assertTrue(FileKind.looksLikeText("Été déjà là — ok".toByteArray(Charsets.UTF_8)))
        assertTrue(FileKind.looksLikeText("Latin-1 é".toByteArray(Charsets.ISO_8859_1)))
        assertTrue(FileKind.looksLikeText(bytes(0xFF, 0xFE, 0x41, 0x00)))
        assertTrue(FileKind.looksLikeText(ByteArray(0)))
        assertFalse(FileKind.looksLikeText(bytes(0x41, 0x00, 0x42)))
        assertFalse(FileKind.looksLikeText(ByteArray(40) { (it % 8 + 1).toByte() }))
    }

    @Test fun kindFlags() {
        assertTrue(FileKind.JSON.isText)
        assertTrue(FileKind.CODE.isText)
        assertFalse(FileKind.PDF.isText)
        assertTrue(FileKind.VIDEO.isMedia)
        assertFalse(FileKind.ZIP.isMedia)
    }
}
