package com.powermaxv2.ytdownloader.core.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class QrHistoryTest {
    @Test fun roundTripAndDeduplication() {
        val a = QrHistoryEntry("https://gulp.app", "QR_CODE", 1_000L)
        val b = QrHistoryEntry("WIFI:T:WPA;S:Maison;P:x;;", "QR_CODE", 2_000L)
        var list = QrHistory.add(emptyList(), a)
        list = QrHistory.add(list, b)
        list = QrHistory.add(list, a.copy(time = 3_000L))
        assertEquals(listOf("https://gulp.app", "WIFI:T:WPA;S:Maison;P:x;;"), list.map { it.text })
        assertEquals(list, QrHistory.decode(QrHistory.encode(list)))
        // Texts with line breaks survive; the separators themselves are replaced.
        val multi = listOf(QrHistoryEntry("ligne 1\nligne 2\u001Ex", "QR_CODE", 5L))
        assertEquals("ligne 1\nligne 2 x", QrHistory.decode(QrHistory.encode(multi)).single().text)
    }

    @Test fun keepsTheLastEight() {
        var list = emptyList<QrHistoryEntry>()
        repeat(12) { list = QrHistory.add(list, QrHistoryEntry("code $it", "QR_CODE", it.toLong())) }
        assertEquals(QrHistory.MAX, list.size)
        assertEquals("code 11", list.first().text)
    }

    @Test fun brokenStorageIsIgnored() {
        assertEquals(emptyList<QrHistoryEntry>(), QrHistory.decode(null))
        assertEquals(emptyList<QrHistoryEntry>(), QrHistory.decode("n'importe quoi"))
    }

    @Test fun ago() {
        val now = 10L * 24 * 3_600_000
        assertEquals("à l'instant", QrHistory.ago(now - 10_000, now))
        assertEquals("il y a 5 min", QrHistory.ago(now - 5 * 60_000, now))
        assertEquals("il y a 3 h", QrHistory.ago(now - 3 * 3_600_000, now))
        assertEquals("hier", QrHistory.ago(now - 30 * 3_600_000L, now))
        assertEquals("il y a 4 j", QrHistory.ago(now - 4 * 24 * 3_600_000L, now))
    }
}
