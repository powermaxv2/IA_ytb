package com.powermaxv2.ytdownloader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun extractStripsSurroundingAndTrailingPunctuation() {
        assertEquals("https://youtu.be/abc123", Links.extract("Regarde (https://youtu.be/abc123):"))
        assertEquals("https://www.instagram.com/p/xyz", Links.extract("Lien : {https://www.instagram.com/p/xyz}!"))
        assertEquals("https://x.com/user/status/42", Links.extract("« https://x.com/user/status/42 »"))
    }

    @Test fun bareLinkRecognizesShortAndExtraVideoHosts() {
        assertTrue(Links.isBareLink("instagr.am/p/abc123"))
        assertTrue(Links.isBareLink("fb.com/watch/?v=123"))
        assertTrue(Links.isBareLink("vimeo.com/123456"))
        assertTrue(Links.isBareLink("dailymotion.com/video/x123"))
        assertTrue(Links.isBareLink("twitch.tv/videos/98765"))
        assertTrue(Links.isBareLink("old.reddit.com/r/videos/comments/abc"))
        assertFalse(Links.isBareLink("bonjour tout le monde"))
        assertFalse(Links.isBareLink("youtube.com"))
    }
}
