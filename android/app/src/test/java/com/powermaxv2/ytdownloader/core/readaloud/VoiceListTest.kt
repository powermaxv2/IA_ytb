package com.powermaxv2.ytdownloader.core.readaloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceListTest {
    private val all = listOf(
        VoiceInfo("en-us-x-sfg-local", "en-US", offline = true),
        VoiceInfo("fr-ca-x-caa-network", "fr-CA", offline = false, quality = 400),
        VoiceInfo("fr-fr-x-frd-local", "fr-FR", offline = true, quality = 300),
        VoiceInfo("fr-fr-x-vlf-local", "fr-FR", offline = true, quality = 400),
        VoiceInfo("fr-ca-x-cab-local", "fr-CA", offline = true),
        VoiceInfo("ja-jp-x-htm-local", "ja-JP", offline = true),
        VoiceInfo("de-de-x-deb-local", "de-DE", offline = true),
    )

    @Test fun languagesInAUsefulOrder() {
        assertEquals(listOf("fr", "en", "de", "ja"), VoiceList.languages(all, device = "fr").map { it.code })
        assertEquals(listOf("en", "fr", "de", "ja"), VoiceList.languages(all, device = "en").map { it.code })
        assertEquals("Français", VoiceList.languages(all).first().label)
        assertEquals("Japonais", VoiceList.languageLabel("ja"))
    }

    @Test fun voicesOfALanguageReadableNames() {
        val fr = VoiceList.voices(all, "fr", deviceRegion = "FR")
        assertEquals(
            listOf("fr-fr-x-vlf-local", "fr-fr-x-frd-local", "fr-ca-x-cab-local", "fr-ca-x-caa-network"),
            fr.map { it.name },
        )
        assertEquals(listOf("France · voix 1", "France · voix 2", "Canada · voix 1", "Canada · voix 2"), fr.map { it.label })
        assertEquals(false, fr.last().offline)
        // A Canadian phone puts Canada first.
        assertEquals("fr-ca-x-cab-local", VoiceList.voices(all, "fr", deviceRegion = "CA").first().name)
    }

    @Test fun defaultLanguage() {
        val langs = VoiceList.languages(all)
        assertEquals("de", VoiceList.defaultLanguage(langs, "DE"))
        assertEquals("fr", VoiceList.defaultLanguage(langs, "it"))
        assertNull(VoiceList.defaultLanguage(emptyList(), "fr"))
    }
}
