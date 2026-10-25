package com.powermaxv2.ytdownloader.core.billing

import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCatalogTest {
    @Test fun premiumToolsExistInTheRegistry() {
        PlusCatalog.premiumIds.forEach { assertTrue("$it is not a tool", ToolRegistry.find(it) != null) }
    }

    @Test fun freeToolsStayFree() {
        assertFalse(PlusCatalog.isPremium(ToolRegistry.CONVERTER))
        assertFalse(PlusCatalog.isPremium(ToolRegistry.QR_CODE))
        assertFalse(PlusCatalog.isPremium(null))
    }

    @Test fun lockedOnlyWithoutPlus() {
        assertTrue(PlusCatalog.locked(ToolRegistry.VAULT, plus = false))
        assertFalse(PlusCatalog.locked(ToolRegistry.VAULT, plus = true))
        assertFalse(PlusCatalog.locked(ToolRegistry.CONVERTER, plus = false))
    }

    @Test fun moreFreeThanPremium() {
        assertTrue(PlusCatalog.premiumIds.size < ToolRegistry.tools.count { it.available } / 2)
    }

    @Test fun priceLabels() {
        assertEquals("2 €", PlusProduct.label(PlusProduct.PRICE_CENTS))
        assertEquals("1,50 €", PlusProduct.label(150))
    }
}
