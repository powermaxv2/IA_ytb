package com.powermaxv2.ytdownloader.core.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSplitTest {
    private fun people(vararg names: String) = names.map { Diner(it) }

    @Test fun equalSplitAddsUpToTheCent() {
        val split = BillSplitter.split(Bill(10_000, people("Léa", "Tom", "Inès")))
        assertTrue(split.ok)
        assertEquals(listOf(3334L, 3333L, 3333L), split.shares.map { it.cents })
        assertEquals(10_000L, split.shares.sumOf { it.cents })
        assertEquals(0L, split.tipCents)
    }

    @Test fun percentTipIsSharedLikeTheBill() {
        val split = BillSplitter.split(Bill(8_600, people("A", "B"), tip = Tip.Percent(10)))
        assertEquals(860L, split.tipCents)
        assertEquals(9_460L, split.grandCents)
        assertEquals(listOf(4730L, 4730L), split.shares.map { it.cents })
        assertEquals(listOf(430L, 430L), split.shares.map { it.tipCents })
    }

    @Test fun amountTip() {
        val split = BillSplitter.split(Bill(5_000, people("A", "B", "C"), tip = Tip.Amount(500)))
        assertEquals(5_500L, split.grandCents)
        assertEquals(5_500L, split.shares.sumOf { it.cents })
        assertEquals(listOf(1834L, 1833L, 1833L), split.shares.map { it.cents })
    }

    @Test fun differentParts() {
        // Double part for the one who ate for two, half part for the child: 4 + 2 + 1 = 7 halves.
        val diners = listOf(Diner("Ogre", halves = 4), Diner("Moi"), Diner("Petit", halves = 1))
        val split = BillSplitter.split(Bill(7_000, diners))
        assertEquals(listOf(4000L, 2000L, 1000L), split.shares.map { it.cents })
    }

    @Test fun ownOrdersArePaidByTheirOwnerOnly() {
        // A 30 € bottle for Tom alone, the 60 € left shared by three.
        val diners = listOf(Diner("Léa"), Diner("Tom", ownCents = 3_000), Diner("Inès"))
        val split = BillSplitter.split(Bill(9_000, diners, tip = Tip.Percent(10)))
        assertEquals(listOf(2000L, 5000L, 2000L), split.shares.map { it.baseCents })
        assertEquals(listOf(2200L, 5500L, 2200L), split.shares.map { it.cents })
    }

    @Test fun someoneWhoOnlyHadTheirOwnOrder() {
        val diners = listOf(Diner("Café", halves = 0, ownCents = 250), Diner("A"), Diner("B"))
        val split = BillSplitter.split(Bill(4_250, diners))
        assertEquals(listOf(250L, 2000L, 2000L), split.shares.map { it.cents })
    }

    @Test fun roundingUpPutsTheExtraInTheTip() {
        val split = BillSplitter.split(Bill(10_000, people("A", "B", "C"), roundUp = true))
        assertEquals(listOf(3400L, 3400L, 3400L), split.shares.map { it.cents })
        assertEquals(200L, split.tipCents)
        assertEquals(10_200L, split.grandCents)
    }

    @Test fun problems() {
        assertEquals(BillProblem.NO_TOTAL, BillSplitter.split(Bill(0, people("A"))).problem)
        assertEquals(BillProblem.NO_DINERS, BillSplitter.split(Bill(1_000, emptyList())).problem)
        assertEquals(BillProblem.OWN_TOO_BIG, BillSplitter.split(Bill(1_000, listOf(Diner("A", ownCents = 1_500)))).problem)
        assertEquals(BillProblem.NOBODY_SHARES, BillSplitter.split(Bill(1_000, listOf(Diner("A", halves = 0)))).problem)
        // Everything ordered alone: nothing left to share, no part needed.
        assertNull(BillSplitter.split(Bill(1_000, listOf(Diner("A", halves = 0, ownCents = 1_000)))).problem)
    }

    @Test fun paybacksToThePayer() {
        val bill = Bill(9_000, people("Léa", "Tom", ""), payer = 0)
        val split = BillSplitter.split(bill)
        val back = BillSplitter.paybacks(bill, split)
        assertEquals(listOf(Payback("Tom", "Léa", 3000), Payback("Personne 3", "Léa", 3000)), back)
        assertTrue(BillSplitter.paybacks(bill.copy(payer = null), split).isEmpty())
    }

    @Test fun parseAmounts() {
        assertEquals(8_600L, BillSplitter.parseAmount("86"))
        assertEquals(8_650L, BillSplitter.parseAmount("86,5"))
        assertEquals(8_650L, BillSplitter.parseAmount("86.50"))
        assertEquals(123_490L, BillSplitter.parseAmount("1 234,90 €"))
        assertEquals(1_000L, BillSplitter.parseAmount("10,"))
        assertNull(BillSplitter.parseAmount(""))
        assertNull(BillSplitter.parseAmount("12,345"))
        assertNull(BillSplitter.parseAmount("abc"))
        assertNull(BillSplitter.parseAmount("9999999"))
    }

    @Test fun formats() {
        assertEquals("23,40 €", BillSplitter.format(2_340))
        assertEquals("0,05 €", BillSplitter.format(5))
        assertEquals("1 234,00 €", BillSplitter.format(123_400))
        assertEquals("$23,40", BillSplitter.format(2_340, BillCurrency.DOLLAR))
        assertEquals("12,00 CHF", BillSplitter.format(1_200, BillCurrency.FRANC))
        assertEquals(listOf("½", "1", "1½", "2"), listOf(1, 2, 3, 4).map(BillSplitter::partsText))
    }

    @Test fun recapForTheGroup() {
        val bill = Bill(9_000, listOf(Diner("Léa"), Diner("Tom", ownCents = 3_000), Diner("Inès", halves = 1)), tip = Tip.Percent(10), payer = 0)
        val text = BillSplitter.recap(bill, BillSplitter.split(bill))
        assertTrue(text, text.startsWith("🧾 Addition : 90,00 € + pourboire 10 % 9,00 € = 99,00 €"))
        assertTrue(text, "• Tom : " in text && "dont 30,00 € perso" in text)
        assertTrue(text, "• Inès : " in text && "(½ part)" in text)
        assertTrue(text, "Tom → Léa : " in text)
    }

    @Test fun manyPeopleAndOddAmountsNeverLoseACent() {
        for (n in 1..BillSplitter.MAX_DINERS) {
            for (total in listOf(1L, 99L, 10_001L, 123_457L)) {
                val diners = (0 until n).map { Diner("P$it", halves = 1 + it % 4, ownCents = if (it == 0) total / 5 else 0) }
                listOf(Tip.None, Tip.Percent(15), Tip.Amount(333)).forEach { tip ->
                    val split = BillSplitter.split(Bill(total, diners, tip = tip))
                    assertEquals(split.grandCents, split.shares.sumOf { it.cents })
                    assertEquals(total, split.shares.sumOf { it.baseCents })
                    // Two separate roundings: a tip share can only be off by the one cent left over.
                    assertTrue(split.shares.all { it.tipCents >= -1 })
                }
            }
        }
    }
}
