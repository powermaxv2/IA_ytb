package com.powermaxv2.ytdownloader.core.convert

import com.powermaxv2.ytdownloader.core.files.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    @Test fun categoriesFromKinds() {
        assertEquals(Category.VIDEO, Category.of(FileKind.VIDEO))
        assertEquals(Category.AUDIO, Category.of(FileKind.AUDIO))
        assertEquals(Category.IMAGE, Category.of(FileKind.IMAGE))
        assertEquals(Category.PDF, Category.of(FileKind.PDF))
        assertEquals(Category.MARKDOWN, Category.of(FileKind.MARKDOWN))
        assertEquals(Category.CSV, Category.of(FileKind.CSV))
        assertEquals(Category.JSON, Category.of(FileKind.JSON))
        assertNull(Category.of(FileKind.TEXT))
        assertNull(Category.of(FileKind.CODE))
        assertNull(Category.of(FileKind.ZIP))
        assertNull(Category.of(FileKind.OTHER))
    }

    @Test fun targetsPerCategory() {
        assertEquals(
            listOf(Target.MP4, Target.MKV, Target.WEBM, Target.MOV, Target.AVI, Target.GIF, Target.MP3, Target.M4A, Target.OPUS, Target.OGG, Target.FLAC, Target.WAV),
            Formats.targets(Category.VIDEO),
        )
        assertEquals(listOf(Target.MP3, Target.M4A, Target.OPUS, Target.OGG, Target.FLAC, Target.WAV), Formats.targets(Category.AUDIO))
        assertEquals(listOf(Target.JPG, Target.PNG, Target.WEBP, Target.BMP, Target.TIFF, Target.PDF), Formats.targets(Category.IMAGE))
        assertEquals(listOf(Target.PDF, Target.JPG, Target.PNG, Target.WEBP, Target.BMP, Target.TIFF), Formats.targets(Category.IMAGE, multiple = true))
        assertEquals(listOf(Target.JPG, Target.PNG), Formats.targets(Category.PDF))
        assertEquals(listOf(Target.HTML), Formats.targets(Category.MARKDOWN))
        assertEquals(listOf(Target.JSON), Formats.targets(Category.CSV))
        assertEquals(listOf(Target.CSV), Formats.targets(Category.JSON))
    }

    @Test fun everyTargetHasAMethod() {
        Category.entries.forEach { c ->
            listOf(false, true).forEach { multiple ->
                Formats.targets(c, multiple).forEach { t -> assertNotNull(Formats.method(c, t)) }
            }
        }
        assertEquals(Method.FFMPEG, Formats.method(Category.VIDEO, Target.MP3))
        assertEquals(Method.BITMAP, Formats.method(Category.IMAGE, Target.WEBP))
        assertEquals(Method.BITMAP_FFMPEG, Formats.method(Category.IMAGE, Target.TIFF))
        assertEquals(Method.IMAGES_TO_PDF, Formats.method(Category.IMAGE, Target.PDF))
        assertEquals(Method.PDF_TO_IMAGES, Formats.method(Category.PDF, Target.PNG))
        assertEquals(Method.MARKDOWN_TO_HTML, Formats.method(Category.MARKDOWN, Target.HTML))
        assertEquals(Method.CSV_TO_JSON, Formats.method(Category.CSV, Target.JSON))
        assertEquals(Method.JSON_TO_CSV, Formats.method(Category.JSON, Target.CSV))
    }

    @Test fun defaultTargetChangesTheFormat() {
        assertEquals(Target.MKV, Formats.defaultTarget(Category.VIDEO, "clip.mp4"))
        assertEquals(Target.MP4, Formats.defaultTarget(Category.VIDEO, "clip.mkv"))
        assertEquals(Target.M4A, Formats.defaultTarget(Category.AUDIO, "song.mp3"))
        assertEquals(Target.PNG, Formats.defaultTarget(Category.IMAGE, "IMG.JPEG"))
        assertEquals(Target.JPG, Formats.defaultTarget(Category.IMAGE, "IMG_001.heic"))
        assertEquals(Target.PDF, Formats.defaultTarget(Category.IMAGE, "3 photos", multiple = true))
        assertEquals(Target.HTML, Formats.defaultTarget(Category.MARKDOWN, "notes.md"))
    }

    @Test fun optionsOnlyWhereTheyApply() {
        val o = ConvertOptions()
        assertTrue(Formats.usesResolution(Target.MP4, o))
        assertTrue(Formats.usesQuality(Category.VIDEO, Target.WEBM, o))
        assertTrue(Formats.usesAudioBitrate(Target.AVI, o))
        assertFalse(Formats.usesResolution(Target.GIF, o))
        assertTrue(Formats.usesQuality(Category.VIDEO, Target.GIF, o))
        assertFalse(Formats.usesAudioBitrate(Target.GIF, o))
        assertTrue(Formats.usesAudioBitrate(Target.MP3, o))
        assertFalse(Formats.usesAudioBitrate(Target.FLAC, o))
        assertFalse(Formats.usesAudioBitrate(Target.WAV, o))
        assertTrue(Formats.usesQuality(Category.IMAGE, Target.JPG, o))
        assertTrue(Formats.usesQuality(Category.PDF, Target.JPG, o))
        assertFalse(Formats.usesQuality(Category.IMAGE, Target.PNG, o))
        assertFalse(Formats.usesQuality(Category.IMAGE, Target.PDF, o))
        // MKV copy: nothing is re-encoded.
        val copy = o.copy(copyStreams = true)
        assertTrue(Formats.allowsCopy(Target.MKV))
        assertFalse(Formats.allowsCopy(Target.MP4))
        assertFalse(Formats.usesResolution(Target.MKV, copy))
        assertFalse(Formats.usesQuality(Category.VIDEO, Target.MKV, copy))
        assertFalse(Formats.usesAudioBitrate(Target.MKV, copy))
        assertTrue(Formats.usesResolution(Target.MP4, copy))
    }

    @Test fun summaries() {
        assertEquals(
            "1080p · Qualité moyenne · 192 kbps",
            Formats.summary(Category.VIDEO, Target.MP4, ConvertOptions(resolution = Resolution.P1080)),
        )
        assertEquals("Copie rapide", Formats.summary(Category.VIDEO, Target.MKV, ConvertOptions(copyStreams = true)))
        assertEquals("320 kbps", Formats.summary(Category.AUDIO, Target.MP3, ConvertOptions(audioBitrate = AudioBitrate.K320)))
        assertNull(Formats.summary(Category.AUDIO, Target.FLAC, ConvertOptions()))
        assertNull(Formats.summary(Category.CSV, Target.JSON, ConvertOptions()))
    }

    @Test fun details() {
        assertEquals("Une image par page", Formats.detail(Category.PDF, Target.JPG))
        assertEquals("24 photos au plus", Formats.detail(Category.IMAGE, Target.PDF, multiple = true))
        assertEquals("H.264 · AAC", Formats.detail(Category.VIDEO, Target.MP4))
    }

    @Test fun outputNames() {
        assertEquals("Vacances.mp4", Formats.outputName("Vacances.mkv", Target.MP4))
        assertEquals("IMG_001.jpg", Formats.outputName("IMG_001.HEIC", Target.JPG))
        assertEquals("notes.html", Formats.outputName("notes.md", Target.HTML))
        assertEquals("sans extension.csv", Formats.outputName("sans extension", Target.CSV))
    }

    @Test fun refusals() {
        assertNull(Formats.refusal(FileKind.VIDEO, "a.mp4"))
        assertNull(Formats.refusal(FileKind.CSV, "a.csv"))
        // Office files are ZIP inside, but the message talks about Word / Excel / PowerPoint.
        assertTrue(Formats.refusal(FileKind.ZIP, "rapport.docx")!!.contains("Word"))
        assertTrue(Formats.refusal(FileKind.OTHER, "budget.xls")!!.contains("Excel"))
        assertTrue(Formats.refusal(FileKind.ZIP, "cours.pptx")!!.contains("PowerPoint"))
        // RAW photos can look like TIFF images: still refused.
        assertTrue(Formats.refusal(FileKind.IMAGE, "IMG_1.DNG")!!.contains("RAW"))
        assertTrue(Formats.refusal(FileKind.OTHER, "photo.cr2")!!.contains("RAW"))
        assertTrue(Formats.refusal(FileKind.ZIP, "archive.zip")!!.contains("Aperçu"))
        assertTrue(Formats.refusal(FileKind.TEXT, "lisez-moi.txt")!!.contains("Aperçu"))
        assertNotNull(Formats.refusal(FileKind.OTHER, "programme.exe"))
    }

    @Test fun qualityScalesGoFromBestToLightest() {
        val q = Quality.entries
        assertTrue(q.zipWithNext().all { (a, b) -> a.x264Crf < b.x264Crf && a.x265Crf < b.x265Crf && a.vp9Crf < b.vp9Crf })
        assertTrue(q.zipWithNext().all { (a, b) -> a.mpeg4Q < b.mpeg4Q && a.imageQuality > b.imageQuality && a.gifSide > b.gifSide })
        assertEquals(listOf(320, 192, 128), AudioBitrate.entries.map { it.kbps })
        assertEquals(listOf(null, 2160, 1080, 720, 480), Resolution.entries.map { it.height })
    }

    @Test fun pdfPageNamesStayWithinTheCap() {
        assertEquals("Cours - page 007.jpg", Formats.pageName("Cours", "007", "jpg"))
        val long = "x".repeat(119)
        val a = Formats.pageName(long, "001", "png")
        val b = Formats.pageName(long, "002", "png")
        assertTrue(a.length <= 120)
        assertTrue(a.endsWith(" - page 001.png"))
        assertTrue(a != b)
        assertEquals("page - page 001.jpg", Formats.pageName("", "001", "jpg"))
    }

    @Test fun legacySampleSizeKeepsTheLongestSideWithinTheBound() {
        assertEquals(1, ImageMath.sampleSize(4096, 3000, 4096))
        assertEquals(2, ImageMath.sampleSize(4097, 3000, 4096))
        assertEquals(2, ImageMath.sampleSize(8000, 6000, 4096))
        assertEquals(2, ImageMath.sampleSize(6000, 8192, 4096))
        assertEquals(4, ImageMath.sampleSize(8194, 100, 4096))
        assertEquals(8, ImageMath.sampleSize(30000, 20000, 4096))
        listOf(1, 4095, 4097, 8191, 8193, 12000, 16385, 65000).forEach { side ->
            val s = ImageMath.sampleSize(side, side / 2, 4096)
            assertTrue("$side / $s", side / s <= 4096)
        }
    }
}
