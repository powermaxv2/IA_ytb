package com.powermaxv2.ytdownloader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesTest {
    @Test fun emptyOrMissingIsNoFavorite() {
        assertEquals(emptyList<String>(), Favorites.decode(null))
        assertEquals(emptyList<String>(), Favorites.decode(""))
        assertEquals(emptyList<String>(), Favorites.decode(" , ,"))
    }

    @Test fun roundTripKeepsTheStarringOrder() {
        val ids = listOf("qr-code", "converter", "pdf-join")
        assertEquals(ids, Favorites.decode(Favorites.encode(ids)))
    }

    @Test fun decodeDropsJunkAndDuplicates() {
        assertEquals(listOf("qr-code", "converter"), Favorites.decode("qr-code,QR CODE,converter,qr-code,<b>"))
    }

    @Test fun toggleStarsAtTheEndAndUnstars() {
        val one = Favorites.toggle(emptyList(), "scanner")
        assertEquals(listOf("scanner"), one)
        val two = Favorites.toggle(one, "wiper")
        assertEquals(listOf("scanner", "wiper"), two)
        assertEquals(listOf("wiper"), Favorites.toggle(two, "scanner"))
    }

    @Test fun toggleIgnoresAnIdThatIsNotOne() {
        assertEquals(listOf("scanner"), Favorites.toggle(listOf("scanner"), "a,b"))
    }

    @Test fun theOldestStarMakesRoomPastTheMax() {
        var ids = emptyList<String>()
        repeat(Favorites.MAX + 2) { ids = Favorites.toggle(ids, "tool-$it") }
        assertEquals(Favorites.MAX, ids.size)
        assertEquals("tool-2", ids.first())
        assertEquals("tool-${Favorites.MAX + 1}", ids.last())
    }
}
