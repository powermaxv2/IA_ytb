package com.powermaxv2.ytdownloader.core.mylink

import com.powermaxv2.ytdownloader.core.direct.Countries
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyLinkTest {
    private val fr = Countries.france
    private fun ready(number: String, message: String = "", country: com.powermaxv2.ytdownloader.core.direct.Country = fr) =
        MyLinks.build(number, country, message) as MyLink.Ready

    @Test fun aNumberGivesItsWaMeLink() {
        val r = ready("06 12 34 56 78")
        assertEquals("33612345678", r.digits)
        assertEquals("+33 6 12 34 56 78", r.pretty)
        assertEquals("https://wa.me/33612345678", r.link)
        assertEquals("France", r.country?.name)
        assertTrue(r.qrFits)
        assertTrue(r.withLogo)
    }

    @Test fun theMessageIsInTheLink() {
        val r = ready("+32 470 12 34 56", "  Bonjour, je voudrais prendre rendez-vous.  ")
        assertEquals("https://wa.me/32470123456?text=Bonjour%2C%20je%20voudrais%20prendre%20rendez-vous.", r.link)
        assertNotNull(QrCodec.encode(r.link))
    }

    @Test fun emptyAndBadNumbers() {
        assertEquals(MyLink.Empty, MyLinks.build("  ", fr, "Bonjour"))
        val bad = MyLinks.build("06 12", fr, "")
        assertTrue(bad is MyLink.Bad)
        assertTrue((bad as MyLink.Bad).reason, "10 chiffres" in bad.reason)
    }

    @Test fun messagesAreCutAndAlwaysFitTheCode() {
        assertEquals(MyLinks.MAX_MESSAGE, MyLinks.cleanMessage("a".repeat(1_000)).length)
        // An emoji is never cut in half.
        val odd = MyLinks.cleanMessage("a" + "😀".repeat(200))
        assertFalse(odd.last().isHighSurrogate())
        assertEquals(MyLinks.MAX_MESSAGE - 1, odd.length)
        // Emoji only (12 encoded characters each): the link still works but is too long for a code, and says so.
        val heavy = ready("0612345678", "😀".repeat(400))
        assertFalse(heavy.withLogo)
        assertFalse(heavy.qrFits)
        // Every quick message keeps the logo.
        MyLinks.QUICK.forEach { assertTrue(it, ready("0612345678", it).withLogo) }
    }

    @Test fun longPlainMessagesStillMakeACode() {
        val r = ready("0612345678", "Bonjour ".repeat(40))
        assertTrue(r.qrFits)
        assertNotNull(QrCodec.encode(r.link))
    }

    @Test fun savedNumberComesBackWhateverTheCountry() {
        val saved = ready("06 12 34 56 78").digits
        val again = MyLinks.restore(saved)
        assertEquals("+33612345678", again)
        // Reading it with Belgium chosen still gives the French number.
        assertEquals(saved, ready(again, country = Countries.byIso("BE")!!).digits)
        assertEquals("", MyLinks.restore(null))
        assertEquals("", MyLinks.restore("abc"))
        assertEquals("", MyLinks.restore("123"))
    }

    @Test fun shareTextAndNames() {
        assertEquals("Écris-moi sur WhatsApp :\nhttps://wa.me/33612345678", MyLinks.shareText("https://wa.me/33612345678"))
        assertEquals("wa.me/33612345678", MyLinks.shortLink("33612345678"))
        assertEquals("gulp-mon-whatsapp-5678.png", MyLinks.fileName("33612345678", MyImageFormat.CARD))
        assertEquals("gulp-qr-whatsapp-5678.png", MyLinks.fileName("33612345678", MyImageFormat.QR))
    }

    @Test fun landlinesAreFlagged() {
        assertTrue(ready("01 23 45 67 89").maybeLandline)
        assertFalse(ready("07 12 34 56 78").maybeLandline)
    }
}
