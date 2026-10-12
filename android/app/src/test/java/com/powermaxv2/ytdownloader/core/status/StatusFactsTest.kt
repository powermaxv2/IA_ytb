package com.powermaxv2.ytdownloader.core.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class StatusFactsTest {
    private val hour = 60L * 60 * 1000

    @Test fun foldersOfBothApps() {
        assertEquals("primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses", initialDocumentId(StatusApp.WHATSAPP))
        assertEquals("primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses", initialDocumentId(StatusApp.BUSINESS))
        assertEquals("WhatsApp/Media/.Statuses", StatusApp.WHATSAPP.legacyPath)
        assertEquals(StatusApp.BUSINESS, StatusApp.of("primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"))
        assertEquals(StatusApp.BUSINESS, StatusApp.of("primary:WhatsApp Business/Media/.Statuses"))
        assertEquals(StatusApp.WHATSAPP, StatusApp.of("primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
    }

    @Test fun thePickedFolderIsTheStatusesFolder() {
        val id = "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
        assertEquals(id, statusesFolderOf(id))
        assertEquals("primary:.Statuses", statusesFolderOf("primary:.Statuses"))
        assertNull(statusesFolderOf("primary:Android/media/com.whatsapp/WhatsApp/Media"))
        assertNull(statusesFolderOf("primary:DCIM/Statuses"))
    }

    @Test fun aFolderAboveIsSearchedOnlyIfItIsWhatsApps() {
        assertEquals(1, statusesSearchDepth("primary:Android/media/com.whatsapp/WhatsApp/Media"))
        assertEquals(2, statusesSearchDepth("primary:Android/media/com.whatsapp/WhatsApp"))
        assertEquals(3, statusesSearchDepth("primary:Android/media/com.whatsapp"))
        assertEquals(3, statusesSearchDepth("primary:Android/media/com.whatsapp.w4b/"))
        assertEquals(2, statusesSearchDepth("primary:WhatsApp Business"))
        assertEquals(0, statusesSearchDepth("primary:Download"))
        assertEquals(0, statusesSearchDepth("primary:DCIM/Media"))
        assertEquals(0, statusesSearchDepth("primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
    }

    @Test fun onlyPhotosAndVideosAreStatuses() {
        assertEquals(StatusKind.PHOTO, statusKind("a1b2c3d4e5.jpg", "image/jpeg"))
        assertEquals(StatusKind.PHOTO, statusKind("x.webp", null))
        assertEquals(StatusKind.VIDEO, statusKind("f00d.mp4", "video/mp4"))
        assertEquals(StatusKind.VIDEO, statusKind("f00d.bin", "video/3gpp"))
        assertNull(statusKind(".nomedia", null))
        assertNull(statusKind("voice.opus", "audio/ogg"))
        assertNull(statusKind("notes.txt", "text/plain"))
    }

    @Test fun hoursLeftCountDownFromADay() {
        val now = 100L * 24 * hour
        assertEquals(24, hoursLeft(now, now))
        assertEquals(24, hoursLeft(now - 30 * 60 * 1000, now))
        assertEquals(23, hoursLeft(now - hour, now))
        assertEquals(1, hoursLeft(now - 23 * hour - 1, now))
        assertEquals(0, hoursLeft(now - 24 * hour, now))
        assertEquals(0, hoursLeft(now - 30 * hour, now))
        // A file dated in the future (clock change) or without a date counts as just received.
        assertEquals(24, hoursLeft(now + hour, now))
        assertEquals(24, hoursLeft(0L, now))
        assertEquals(0.5f, lifeLeft(now - 12 * hour, now), 0.0001f)
        assertEquals(0f, lifeLeft(now - 48 * hour, now), 0.0001f)
        assertEquals("3h", leftLabel(3))
        assertEquals("0h", leftLabel(-1))
    }

    @Test fun keptCopiesAreNamedByDate() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2026-10-11 14:32 UTC
        val at = 1_791_729_120_000L
        assertEquals("Statut 2026-10-11 14h32 a1b2c3.jpg", keptName("A1B2C3D4E5F6A7B8.jpg", at, utc))
        assertEquals("Statut 2026-10-11 14h32 f00dca.mp4", keptName("f00d-cafe.MP4", at, utc))
        assertEquals("Statut abc.jpg", keptName("abc", 0L, utc))
    }

    @Test fun filters() {
        assertTrue(StatusFilter.ALL.accepts(StatusKind.VIDEO))
        assertTrue(StatusFilter.PHOTOS.accepts(StatusKind.PHOTO))
        assertTrue(!StatusFilter.PHOTOS.accepts(StatusKind.VIDEO))
        assertTrue(!StatusFilter.VIDEOS.accepts(StatusKind.PHOTO))
    }

    @Test fun texts() {
        assertEquals("Aucun statut", statusCount(0))
        assertEquals("1 statut", statusCount(1))
        assertEquals("9 statuts", statusCount(9))
        assertEquals("6 photos · 3 vidéos", kindsLine(6, 3))
        assertEquals("1 photo", kindsLine(1, 0))
        assertEquals("1 vidéo", kindsLine(0, 1))
        assertEquals("rien pour l'instant", kindsLine(0, 0))
        assertEquals("Statut gardé", keptTitle(1))
        assertEquals("7 statuts gardés", keptTitle(7))
        assertEquals("5 photos dans Pictures/Gulp · 2 vidéos dans Movies/Gulp", keptWhere(5, 2))
        assertEquals("1 vidéo dans Movies/Gulp", keptWhere(0, 1))
        assertEquals("0:28", durationLabel(28_000))
        assertEquals("1:05", durationLabel(64_600))
        assertEquals("0:01", durationLabel(200))
        assertNull(durationLabel(null))
        assertNull(durationLabel(0))
    }
}
