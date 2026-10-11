package com.powermaxv2.ytdownloader.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    @Test fun links() {
        assertEquals(QrPayload.Link("https://gulp.app/menu", "https://gulp.app/menu"), QrPayload.parse("https://gulp.app/menu"))
        assertEquals("https://www.exemple.fr", (QrPayload.parse("www.exemple.fr") as QrPayload.Link).url)
        assertEquals("gulp.app/menu", QrPayload.parse("https://gulp.app/menu/").title)
        // Other schemes and sentences are never treated as links to open.
        assertTrue(QrPayload.parse("javascript:alert(1)") is QrPayload.Text)
        assertTrue(QrPayload.parse("intent://scan/#Intent;scheme=zxing;end") is QrPayload.Text)
        assertTrue(QrPayload.parse("https://exemple.fr et la suite") is QrPayload.Text)
        assertTrue(QrPayload.parse("http://localhost") is QrPayload.Text)
    }

    @Test fun wifi() {
        val wifi = QrPayload.parse("WIFI:T:WPA;S:Maison-5G;P:soleil-2024;;") as QrPayload.Wifi
        assertEquals("Maison-5G", wifi.ssid)
        assertEquals("soleil-2024", wifi.password)
        assertEquals(WifiSecurity.WPA, wifi.security)
        // Escaped ; and : inside the values, fields in any order, hidden network.
        val escaped = QrPayload.parse("""WIFI:S:Chez\;moi\:2;H:true;P:a\\b\,c;T:WEP;;""") as QrPayload.Wifi
        assertEquals("Chez;moi:2", escaped.ssid)
        assertEquals("""a\b,c""", escaped.password)
        assertEquals(WifiSecurity.WEP, escaped.security)
        assertTrue(escaped.hidden)
        val open = QrPayload.parse("WIFI:T:nopass;S:Gare;;") as QrPayload.Wifi
        assertEquals(WifiSecurity.OPEN, open.security)
        assertEquals("", open.password)
        assertEquals("Réseau Wi-Fi", open.kindLabel)
    }

    @Test fun contacts() {
        val vcard = QrPayload.parse("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Martin;Camille;;;\r\nFN:Camille Martin\r\nTEL;TYPE=CELL:+33 6 12 34 56 78\r\nEMAIL:camille@exemple.fr\r\nORG:Atelier\\, Paris\r\nEND:VCARD") as QrPayload.Contact
        assertEquals("Camille Martin", vcard.name)
        assertEquals(listOf("+33 6 12 34 56 78"), vcard.phones)
        assertEquals(listOf("camille@exemple.fr"), vcard.emails)
        assertEquals("Atelier, Paris", vcard.organization)
        val noFn = QrPayload.parse("BEGIN:VCARD\nN:Durand;Léa\nEND:VCARD") as QrPayload.Contact
        assertEquals("Léa Durand", noFn.name)
        val mecard = QrPayload.parse("MECARD:N:Durand,Léa;TEL:0612345678;EMAIL:lea@ex.fr;;") as QrPayload.Contact
        assertEquals("Léa Durand", mecard.name)
        assertEquals(listOf("0612345678"), mecard.phones)
    }

    @Test fun messagesPhonesPlaces() {
        val mail = QrPayload.parse("mailto:hello@gulp.app?subject=Bonjour%20toi&body=%C3%A7a%20va") as QrPayload.Email
        assertEquals("hello@gulp.app", mail.address)
        assertEquals("Bonjour toi", mail.subject)
        assertEquals("ça va", mail.body)
        assertEquals("hello@gulp.app", (QrPayload.parse("MATMSG:TO:hello@gulp.app;SUB:Salut;BODY:Coucou;;") as QrPayload.Email).address)
        assertEquals(QrPayload.Phone("tel:+33612345678", "+33612345678"), QrPayload.parse("tel:+33612345678"))
        val sms = QrPayload.parse("SMSTO:0612345678:On arrive") as QrPayload.Sms
        assertEquals("0612345678", sms.number)
        assertEquals("On arrive", sms.message)
        val geo = QrPayload.parse("geo:48.8584,2.2945") as QrPayload.Geo
        assertEquals(48.8584, geo.latitude, 1e-9)
        assertEquals("48.85840, 2.29450", geo.title)
        assertTrue(QrPayload.parse("geo:200,5") is QrPayload.Text)
    }

    @Test fun productsAndText() {
        val ean = QrPayload.parse("3017620422003", "EAN_13") as QrPayload.Product
        assertEquals("EAN-13", ean.format)
        assertTrue(QrPayload.parse("3017620422003", "QR_CODE") is QrPayload.Text)
        val text = QrPayload.parse("\n  Code promo : GULP10\nValable jusqu'à dimanche")
        assertTrue(text is QrPayload.Text)
        assertEquals("Code promo : GULP10", text.title)
    }

    @Test fun percentDecodeKeepsBrokenSequences() {
        assertEquals("100%", QrPayload.percentDecode("100%"))
        assertEquals("%+1", QrPayload.percentDecode("%+1"))
        assertEquals("é", QrPayload.percentDecode("%C3%A9"))
    }

    @Test fun draftsEncodeAndReadBack() {
        assertNull(QrDraft().encoded())
        assertEquals("https://gulp.app", QrDraft(link = "gulp.app").encoded())
        assertEquals("https://gulp.app", QrDraft(link = " https://gulp.app ").encoded())

        val wifi = QrDraft(kind = QrKind.WIFI, ssid = "Chez;moi", password = "p:a\"ss")
        val encoded = wifi.encoded()!!
        assertEquals("""WIFI:T:WPA;S:Chez\;moi;P:p\:a\"ss;;""", encoded)
        val back = QrPayload.parse(encoded) as QrPayload.Wifi
        assertEquals("Chez;moi", back.ssid)
        assertEquals("p:a\"ss", back.password)
        assertEquals("WIFI:T:nopass;S:Gare;;", QrDraft(kind = QrKind.WIFI, ssid = "Gare").encoded())
        assertNull(QrDraft(kind = QrKind.WIFI, password = "x").encoded())

        val contact = QrDraft(kind = QrKind.CONTACT, name = "Camille Martin", phone = "06 12", email = "c@ex.fr").encoded()!!
        val person = QrPayload.parse(contact) as QrPayload.Contact
        assertEquals("Camille Martin", person.name)
        assertEquals(listOf("06 12"), person.phones)
        assertEquals(listOf("c@ex.fr"), person.emails)
        assertTrue(contact.contains("N:Martin;Camille;;;"))

        assertNull(QrDraft(kind = QrKind.TEXT, text = "   ").encoded())
        assertEquals("QR Wi-Fi Chez;moi.png", wifi.fileName)
        assertEquals("QR a_b.png", QrDraft(kind = QrKind.TEXT, text = "a/b").fileName)
    }
}
