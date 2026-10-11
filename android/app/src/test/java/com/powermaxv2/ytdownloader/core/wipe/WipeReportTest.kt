package com.powermaxv2.ytdownloader.core.wipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WipeReportTest {
    private val pixel = PhotoFacts(
        latitude = 48.865,
        longitude = 2.378,
        make = "Google",
        model = "Pixel 8 Pro",
        deviceExtra = true,
        serial = true,
        artist = "Maxime",
        date = "2026:10:10 18:42:07",
    )

    @Test fun detailsSayWhatThePhotosTell() {
        assertEquals("48,8650° N · 2,3780° E", WipeReport.detail(WipeKind.LOCATION, listOf(pixel)))
        assertEquals("Pixel 8 Pro · n° de série", WipeReport.detail(WipeKind.DEVICE, listOf(pixel)))
        assertEquals("« Maxime »", WipeReport.detail(WipeKind.AUTHOR, listOf(pixel)))
        assertEquals("sam. 10 oct. 2026 · 18:42", WipeReport.detail(WipeKind.DATE, listOf(pixel)))
    }

    @Test fun detailsCountWhenOnlySomePhotosTell() {
        val photos = listOf(PhotoFacts(), pixel, PhotoFacts(date = "2026:01:02 03:04:05"))
        assertEquals("48,8650° N · 2,3780° E · 1 photo sur 3", WipeReport.detail(WipeKind.LOCATION, photos))
        assertEquals("sam. 10 oct. 2026 · 18:42 · 2 photos sur 3", WipeReport.detail(WipeKind.DATE, photos))
        assertNull(WipeReport.detail(WipeKind.AUTHOR, listOf(PhotoFacts())))
    }

    @Test fun hiddenLocationStillCounts() {
        val hidden = PhotoFacts(locationHidden = true)
        assertTrue(hidden.hasLocation)
        assertEquals("Présent, masqué par Android", WipeReport.detail(WipeKind.LOCATION, listOf(hidden)))
    }

    @Test fun southWestCoordinates() {
        assertEquals("33,8688° S · 151,2093° E", WipeReport.coordinates(-33.8688, 151.2093))
        assertEquals("40,7128° N · 74,0060° O", WipeReport.coordinates(40.7128, -74.006))
    }

    @Test fun deviceWithoutBrandRepeat() {
        assertEquals("Pixel 8 Pro", PhotoFacts(make = "Google", model = "Pixel 8 Pro").device)
        assertEquals("iPhone 15", PhotoFacts(make = "Apple", model = "iPhone 15").device)
        assertEquals("Samsung SM-S921B", PhotoFacts(make = "samsung", model = "SM-S921B").device)
        assertEquals("NIKON D750", PhotoFacts(make = "NIKON CORPORATION", model = "NIKON D750").device)
        assertEquals("Google", PhotoFacts(make = "Google").device)
    }

    @Test fun doneLinesTellWhatHappened() {
        val photos = listOf(pixel, pixel, pixel)
        val lines = WipeReport.doneLines(photos, WipeChoices(), photos.map { PhotoFacts(date = it.date) }, reencoded = 0)
        assertEquals(
            listOf(
                "Lieu effacé sur 3 photos",
                "Téléphone et n° de série effacés",
                "Nom de l'auteur effacé",
                "Date gardée, comme tu l'as choisi",
            ),
            lines.map { it.text },
        )
        assertEquals(listOf(true, true, true, false), lines.map { it.done })
    }

    @Test fun keptButLostIsSaid() {
        val lines = WipeReport.doneLines(listOf(pixel), WipeChoices(), listOf(PhotoFacts()), reencoded = 1)
        assertEquals("Date effacée aussi (ce format ne peut pas la garder)", lines.first { it.kind == WipeKind.DATE }.text)
        assertEquals("Photo HEIC enregistrée en JPG", lines.last().text)
    }

    @Test fun nothingFoundStillSaysTheHiddenPartsWent() {
        val lines = WipeReport.doneLines(listOf(PhotoFacts()), WipeChoices(), listOf(PhotoFacts()), reencoded = 0)
        assertEquals(1, lines.size)
        assertTrue(lines.single().done)
        assertFalse(lines.single().text.isEmpty())
    }

    @Test fun badDateStaysRaw() {
        assertEquals("hier", WipeReport.readableDate("hier"))
    }
}
