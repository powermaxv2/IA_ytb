package com.powermaxv2.ytdownloader.core.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    private fun unit(id: String) = Units.unit(id)!!
    private fun conv(v: Double, from: String, to: String, ingredient: Ingredient = Ingredient.WATER) =
        Units.convert(v, unit(from), unit(to), ingredient)

    @Test fun idsAreUniqueAndEveryQuantityHasUnitsAndDefaults() {
        assertEquals(Units.all.size, Units.all.map { it.id }.toSet().size)
        Quantity.entries.forEach { q ->
            assertTrue(q.label, Units.of(q).size >= 4)
            val (from, to) = Units.defaults(q)
            assertEquals(q, from.quantity)
            assertEquals(q, to.quantity)
            assertTrue(from != to)
        }
    }

    @Test fun lengthsMassesAndSpeeds() {
        assertEquals(3.10686, conv(5.0, "km", "mi"), 1e-5)
        assertEquals(2.54, conv(1.0, "in", "cm"), 1e-12)
        assertEquals(1852.0, conv(1.0, "nmi", "m"), 1e-9)
        assertEquals(2.20462, conv(1.0, "kg", "lb"), 1e-5)
        assertEquals(28.3495, conv(1.0, "oz", "g"), 1e-4)
        assertEquals(62.1371, conv(100.0, "kmh", "mph"), 1e-4)
        assertEquals(27.7778, conv(100.0, "kmh", "ms"), 1e-4)
        assertEquals(1.852, conv(1.0, "kn", "kmh"), 1e-9)
    }

    @Test fun temperaturesHaveAnOffset() {
        assertEquals(68.0, conv(20.0, "c", "f"), 1e-9)
        assertEquals(-40.0, conv(-40.0, "f", "c"), 1e-9)
        assertEquals(176.667, conv(350.0, "f", "c"), 1e-3)
        assertEquals(0.0, conv(-273.15, "c", "k"), 1e-9)
        assertEquals(180.0, conv(6.0, "th", "c"), 1e-9)
        assertEquals(6.0, conv(356.0, "f", "th"), 1e-9)
        // Round trip.
        assertEquals(123.4, conv(conv(123.4, "k", "f"), "f", "k"), 1e-9)
    }

    @Test fun cookingGoesThroughTheIngredient() {
        val flour = conv(1.0, "cup", "cook-g", Ingredient.FLOUR)
        assertEquals(125.4, flour, 0.1)
        assertEquals(236.588, conv(1.0, "cup", "cook-g", Ingredient.WATER), 1e-3)
        assertEquals(1.0, conv(flour, "cook-g", "cup", Ingredient.FLOUR), 1e-9)
        assertEquals(3.0, conv(1.0, "tbsp", "tsp"), 1e-9)
        assertEquals(20.0, conv(1.0, "glass", "cook-cl"), 1e-9)
        // Volume to volume and mass to mass ignore the ingredient.
        assertEquals(15.0, conv(1.0, "tbsp", "cook-ml", Ingredient.HONEY), 1e-9)
        assertEquals(453.592, conv(1.0, "cook-lb", "cook-g", Ingredient.COCOA), 1e-3)
        assertTrue(Units.needsIngredient(unit("cup"), unit("cook-g")))
        assertFalse(Units.needsIngredient(unit("cup"), unit("tbsp")))
        assertFalse(Units.needsIngredient(unit("kg"), unit("g")))
    }

    @Test fun areaVolumeDataAndTime() {
        assertEquals(10.7639, conv(1.0, "m2", "ft2"), 1e-4)
        assertEquals(2.47105, conv(1.0, "ha", "acre"), 1e-5)
        assertEquals(0.264172, conv(1.0, "l", "galus"), 1e-6)
        assertEquals(1000.0, conv(1.0, "go", "mo"), 1e-9)
        assertEquals(1.073741824, conv(1.0, "gio", "go"), 1e-12)
        assertEquals(8.0, conv(1.0, "o", "bit"), 1e-12)
        assertEquals(90.0, conv(1.5, "h", "min"), 1e-9)
        assertEquals(365.2425, conv(1.0, "year", "day"), 1e-9)
    }

    @Test fun differentQuantitiesDoNotConvert() {
        assertTrue(conv(1.0, "km", "kg").isNaN())
        // Grams of the Poids chips are not the grams of the Cuisine chips.
        assertTrue(conv(1.0, "g", "cook-g").isNaN())
    }

    @Test fun tableListsEveryUnitOfTheQuantity() {
        val table = Units.table(1.0, unit("km"))
        assertEquals(Units.of(Quantity.LENGTH).map { it.id }, table.map { it.unit.id })
        assertEquals(1000.0, table.first { it.unit.id == "m" }.value, 1e-9)
    }

    @Test fun impossibleValues() {
        assertTrue(Units.impossible(-300.0, unit("c")))
        assertTrue(Units.impossible(-1.0, unit("k")))
        assertFalse(Units.impossible(-40.0, unit("f")))
        assertTrue(Units.impossible(-2.0, unit("km")))
        assertFalse(Units.impossible(0.0, unit("km")))
    }

    @Test fun numbersTheFrenchWayAndFractions() {
        assertEquals(1.5, Units.parseNumber("1,5")!!, 1e-12)
        assertEquals(1.5, Units.parseNumber("1.5")!!, 1e-12)
        assertEquals(1500.0, Units.parseNumber("1 500")!!, 1e-12)
        assertEquals(1500.0, Units.parseNumber("1 500")!!, 1e-12)
        assertEquals(1234.5, Units.parseNumber("1 234,5")!!, 1e-12)
        assertEquals(1234.5, Units.parseNumber("1,234.5")!!, 1e-12)
        assertEquals(1234.5, Units.parseNumber("1.234,5")!!, 1e-12)
        assertEquals(0.5, Units.parseNumber("1/2")!!, 1e-12)
        assertEquals(1.5, Units.parseNumber("1 1/2")!!, 1e-12)
        assertEquals(1.5, Units.parseNumber("1½")!!, 1e-12)
        assertEquals(0.75, Units.parseNumber("¾")!!, 1e-12)
        assertEquals(-4.0, Units.parseNumber("-4")!!, 1e-12)
        assertEquals(-4.0, Units.parseNumber("−4")!!, 1e-12)
        assertNull(Units.parseNumber(""))
        assertNull(Units.parseNumber("abc"))
        assertNull(Units.parseNumber("1/0"))
        assertNull(Units.parseNumber("1e400"))
    }

    @Test fun queriesNameTheirUnits() {
        val plain = Units.parseQuery("12,5")!!
        assertEquals(12.5, plain.value, 1e-12)
        assertNull(plain.from)

        val km = Units.parseQuery("5 km en miles")!!
        assertEquals(5.0, km.value, 1e-12)
        assertEquals("km", km.from?.id)
        assertEquals("mi", km.to?.id)

        val oven = Units.parseQuery("350 °F en °C")!!
        assertEquals("f", oven.from?.id)
        assertEquals("c", oven.to?.id)
        assertEquals("f", Units.parseQuery("350°F")!!.from?.id)
        assertEquals("c", Units.parseQuery("20 degrés")!!.from?.id)

        val cup = Units.parseQuery("1 1/2 cup -> g")!!
        assertEquals(1.5, cup.value, 1e-12)
        assertEquals("cup", cup.from?.id)
        assertEquals("cook-g", cup.to?.id)

        assertEquals("tbsp", Units.parseQuery("2 c. à s.")!!.from?.id)
        assertEquals("kmh", Units.parseQuery("130 km/h")!!.from?.id)
        assertEquals("m2", Units.parseQuery("70 m²")!!.from?.id)
        assertEquals("m2", Units.parseQuery("70 m2 to sq ft")!!.from?.id)
        assertEquals("ft2", Units.parseQuery("70 m2 to sq ft")!!.to?.id)
        assertEquals("go", Units.parseQuery("64 Go")!!.from?.id)
        assertEquals("m", Units.parseQuery("3 M")!!.from?.id)
    }

    @Test fun queriesPreferTheOpenQuantity() {
        assertEquals("l", Units.parseQuery("1 l")!!.from?.id)
        assertEquals("cook-l", Units.parseQuery("1 l", Quantity.COOKING)!!.from?.id)
        assertEquals("g", Units.parseQuery("200 g en lb")!!.from?.id)
        assertEquals("cook-g", Units.parseQuery("200 g en lb", Quantity.COOKING)!!.from?.id)
        // Only Cuisine has both grams and cups.
        val grams = Units.parseQuery("200 g en cups")!!
        assertEquals("cook-g", grams.from?.id)
        assertEquals("cup", grams.to?.id)
        // Units that measure different things: keep the first.
        val mixed = Units.parseQuery("5 km en kg")!!
        assertEquals("km", mixed.from?.id)
        assertNull(mixed.to)
        assertNull(Units.parseQuery("km"))
        assertNull(Units.parseQuery("5 bananes")!!.from)
    }

    @Test fun resultsAreWrittenTheFrenchWay() {
        assertEquals("1 609,34", Units.format(1609.344))
        assertEquals("3,10686", Units.format(3.106855961))
        assertEquals("0,000123", Units.format(0.000123))
        assertEquals("-40", Units.format(-40.0))
        assertEquals("0", Units.format(0.0))
        assertEquals("68", Units.format(68.00000000001))
        assertEquals("1 000 000", Units.format(1e6))
        assertEquals("1,5 × 10¹⁵", Units.format(1.5e15))
        assertEquals("2 × 10⁻⁷", Units.format(2e-7))
        assertEquals("—", Units.format(Double.NaN))
        assertEquals("20 °C = 68 °F", Units.sentence(20.0, unit("c"), 68.0, unit("f")))
    }
}
