package com.powermaxv2.ytdownloader.core.units

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// The Convertisseur d'unités: every unit it knows, how to go from one to another, how to read what the user types
// ("1,5", "1 1/2", "350 °F en °C") and how to write a result the French way ("1 609,34"). Pure, unit tested in UnitsTest.

/** What is being measured: the chips at the top of the tool. */
enum class Quantity(val label: String) {
    LENGTH("Longueur"),
    MASS("Poids"),
    TEMPERATURE("Température"),
    SPEED("Vitesse"),
    COOKING("Cuisine"),
    AREA("Surface"),
    VOLUME("Volume"),
    DATA("Données"),
    TIME("Durée"),
}

/**
 * One unit. A value in this unit is `value × factor + offset` in its quantity's base unit (m, kg, °C, m/s, ml or g,
 * m², L, octet, s). In [Quantity.COOKING] a [mass] unit counts in grams, the others in millilitres: going from one to
 * the other goes through the ingredient's density.
 */
class MeasureUnit(
    /** Stable id (saved state): never rename it. */
    val id: String,
    val quantity: Quantity,
    val name: String,
    val symbol: String,
    val factor: Double,
    val offset: Double = 0.0,
    /** Words the user may type for it, already folded (see [Units.fold]); [symbol] and [name] are added. */
    aliases: List<String> = emptyList(),
    val mass: Boolean = false,
) {
    val aliases: Set<String> = (aliases + symbol + name).map(Units::key).toSet()

    fun toBase(value: Double): Double = value * factor + offset
    fun fromBase(base: Double): Double = (base - offset) / factor

    override fun toString(): String = id
}

/** What a cooking cup weighs depends on what is in it: grams per millilitre. */
enum class Ingredient(val label: String, val density: Double) {
    WATER("Eau", 1.0),
    MILK("Lait", 1.03),
    FLOUR("Farine", 0.53),
    SUGAR("Sucre", 0.85),
    ICING_SUGAR("Sucre glace", 0.51),
    BUTTER("Beurre", 0.96),
    OIL("Huile", 0.92),
    RICE("Riz", 0.85),
    HONEY("Miel", 1.42),
    OATS("Flocons d'avoine", 0.38),
    COCOA("Cacao", 0.42),
}

/** "5 km en mi" read back: the value, the unit it is in, and the unit asked for when there is one. */
data class Query(val value: Double, val from: MeasureUnit?, val to: MeasureUnit?)

/** One line of the table: [value] in [unit]. */
data class Converted(val unit: MeasureUnit, val value: Double)

object Units {
    /** Longest text the field takes. */
    const val MAX_INPUT = 40

    private fun u(
        id: String,
        q: Quantity,
        name: String,
        symbol: String,
        factor: Double,
        vararg aliases: String,
        offset: Double = 0.0,
        mass: Boolean = false,
    ) = MeasureUnit(id, q, name, symbol, factor, offset, aliases.toList(), mass)

    private val L = Quantity.LENGTH
    private val M = Quantity.MASS
    private val T = Quantity.TEMPERATURE
    private val S = Quantity.SPEED
    private val C = Quantity.COOKING
    private val A = Quantity.AREA
    private val V = Quantity.VOLUME
    private val D = Quantity.DATA
    private val H = Quantity.TIME

    val all: List<MeasureUnit> = listOf(
        // Length, in metres.
        u("mm", L, "Millimètre", "mm", 0.001, "millimetres"),
        u("cm", L, "Centimètre", "cm", 0.01, "centimetres"),
        u("m", L, "Mètre", "m", 1.0, "metres", "meter", "meters"),
        u("km", L, "Kilomètre", "km", 1000.0, "kilometres", "kilometer", "kilometers", "bornes"),
        u("in", L, "Pouce", "in", 0.0254, "pouces", "inch", "inches", "\""),
        u("ft", L, "Pied", "ft", 0.3048, "pieds", "foot", "feet", "'"),
        u("yd", L, "Yard", "yd", 0.9144, "yards"),
        u("mi", L, "Mile", "mi", 1609.344, "miles"),
        u("nmi", L, "Mille marin", "M", 1852.0, "milles marins", "nmi", "nautical mile"),
        // Mass, in kilograms.
        u("mg", M, "Milligramme", "mg", 1e-6, "milligrammes"),
        u("g", M, "Gramme", "g", 0.001, "grammes", "gr", "gram", "grams"),
        u("kg", M, "Kilogramme", "kg", 1.0, "kilos", "kilo", "kilogrammes"),
        u("t", M, "Tonne", "t", 1000.0, "tonnes"),
        u("oz", M, "Once", "oz", 0.028349523125, "onces", "ounce", "ounces"),
        u("lb", M, "Livre (lb)", "lb", 0.45359237, "lbs", "livre", "livres", "pound", "pounds"),
        u("st", M, "Stone", "st", 6.35029318, "stones"),
        // Temperature, in degrees Celsius.
        u("c", T, "Celsius", "°C", 1.0, "c", "degres", "degre", "degres celsius", "deg c"),
        u("f", T, "Fahrenheit", "°F", 5.0 / 9.0, "f", "degres fahrenheit", "deg f", offset = -160.0 / 9.0),
        u("k", T, "Kelvin", "K", 1.0, "kelvins", offset = -273.15),
        u("th", T, "Thermostat (four)", "Th", 30.0, "thermostat"),
        // Speed, in metres per second.
        u("kmh", S, "Kilomètre/heure", "km/h", 1 / 3.6, "kmh", "km h", "kilometres heure", "kph"),
        u("ms", S, "Mètre/seconde", "m/s", 1.0, "m s", "metres seconde"),
        u("mph", S, "Mile/heure", "mph", 0.44704, "mi/h", "miles heure"),
        u("kn", S, "Nœud", "nd", 1852.0 / 3600.0, "noeud", "noeuds", "kn", "knot", "knots"),
        u("fts", S, "Pied/seconde", "ft/s", 0.3048, "fps"),
        u("mach", S, "Mach (au sol)", "Mach", 340.29),
        // Cooking: millilitres, or grams for the mass units.
        u("cook-ml", C, "Millilitre", "ml", 1.0, "millilitres"),
        u("cook-cl", C, "Centilitre", "cl", 10.0, "centilitres"),
        u("cook-dl", C, "Décilitre", "dl", 100.0, "decilitres"),
        u("cook-l", C, "Litre", "L", 1000.0, "litres", "liter", "liters"),
        u("tsp", C, "Cuillère à café", "c. à c.", 5.0, "cac", "c a c", "cc", "cuilleres a cafe", "tsp", "teaspoon", "teaspoons"),
        u("tbsp", C, "Cuillère à soupe", "c. à s.", 15.0, "cas", "c a s", "cs", "cuilleres a soupe", "tbsp", "tablespoon", "tablespoons"),
        u("cup", C, "Tasse (cup US)", "cup", 236.5882365, "cups", "tasse", "tasses"),
        u("glass", C, "Verre (20 cl)", "verre", 200.0, "verres"),
        u("cook-floz", C, "Once liquide US", "fl oz", 29.5735295625, "floz"),
        u("cook-g", C, "Gramme", "g", 1.0, "grammes", "gr", "gram", "grams", mass = true),
        u("cook-kg", C, "Kilogramme", "kg", 1000.0, "kilos", "kilo", mass = true),
        u("cook-oz", C, "Once", "oz", 28.349523125, "onces", "ounce", "ounces", mass = true),
        u("cook-lb", C, "Livre (lb)", "lb", 453.59237, "lbs", "pound", "pounds", mass = true),
        // Area, in square metres.
        u("cm2", A, "Centimètre carré", "cm²", 1e-4, "cm2"),
        u("m2", A, "Mètre carré", "m²", 1.0, "m2", "metres carres"),
        u("are", A, "Are", "a", 100.0, "ares"),
        u("ha", A, "Hectare", "ha", 10_000.0, "hectares"),
        u("km2", A, "Kilomètre carré", "km²", 1e6, "km2"),
        u("in2", A, "Pouce carré", "in²", 0.00064516, "in2", "sq in"),
        u("ft2", A, "Pied carré", "ft²", 0.09290304, "ft2", "sq ft", "sqft"),
        u("acre", A, "Acre", "ac", 4046.8564224, "acres"),
        u("mi2", A, "Mile carré", "mi²", 2_589_988.110336, "mi2", "sq mi"),
        // Volume, in litres.
        u("ml", V, "Millilitre", "ml", 0.001, "millilitres"),
        u("cl", V, "Centilitre", "cl", 0.01, "centilitres"),
        u("l", V, "Litre", "L", 1.0, "litres", "liter", "liters"),
        u("m3", V, "Mètre cube", "m³", 1000.0, "m3", "metres cubes"),
        u("cm3", V, "Centimètre cube", "cm³", 0.001, "cm3", "cc"),
        u("galus", V, "Gallon US", "gal", 3.785411784, "gallon", "gallons", "gal us"),
        u("galuk", V, "Gallon UK", "gal UK", 4.54609, "gallon uk", "gallons uk"),
        u("pt", V, "Pinte US", "pt", 0.473176473, "pinte", "pintes", "pint", "pints"),
        u("floz", V, "Once liquide US", "fl oz", 0.0295735295625, "floz"),
        u("ft3", V, "Pied cube", "ft³", 28.316846592, "ft3", "cu ft"),
        // Data, in bytes (octets).
        u("bit", D, "Bit", "bit", 0.125, "bits", "b"),
        u("o", D, "Octet", "o", 1.0, "octets", "byte", "bytes"),
        u("ko", D, "Kilo-octet", "Ko", 1e3, "kb", "kilo octets"),
        u("mo", D, "Mégaoctet", "Mo", 1e6, "mb", "mega", "megaoctets"),
        u("go", D, "Gigaoctet", "Go", 1e9, "gb", "giga", "gigaoctets"),
        u("to", D, "Téraoctet", "To", 1e12, "tb", "tera", "teraoctets"),
        u("kio", D, "Kibioctet", "Kio", 1024.0, "kib"),
        u("mio", D, "Mébioctet", "Mio", 1024.0 * 1024, "mib"),
        u("gio", D, "Gibioctet", "Gio", 1024.0 * 1024 * 1024, "gib"),
        // Time, in seconds.
        u("msec", H, "Milliseconde", "ms", 0.001, "millisecondes"),
        u("s", H, "Seconde", "s", 1.0, "sec", "secondes"),
        u("min", H, "Minute", "min", 60.0, "minutes", "mn"),
        u("h", H, "Heure", "h", 3600.0, "heures", "hr", "hour", "hours"),
        u("day", H, "Jour", "j", 86_400.0, "jours", "day", "days"),
        u("week", H, "Semaine", "sem.", 604_800.0, "semaines", "sem", "week", "weeks"),
        u("month", H, "Mois", "mois", 2_629_746.0, "month", "months"),
        u("year", H, "Année", "an", 31_556_952.0, "ans", "annee", "annees", "year", "years"),
    )

    private val byId: Map<String, MeasureUnit> = all.associateBy { it.id }

    fun unit(id: String?): MeasureUnit? = byId[id]

    fun of(quantity: Quantity): List<MeasureUnit> = all.filter { it.quantity == quantity }

    /** The pair the tool opens on for each quantity: the conversion people look up the most. */
    fun defaults(quantity: Quantity): Pair<MeasureUnit, MeasureUnit> = when (quantity) {
        Quantity.LENGTH -> byId.getValue("km") to byId.getValue("mi")
        Quantity.MASS -> byId.getValue("kg") to byId.getValue("lb")
        Quantity.TEMPERATURE -> byId.getValue("c") to byId.getValue("f")
        Quantity.SPEED -> byId.getValue("kmh") to byId.getValue("mph")
        Quantity.COOKING -> byId.getValue("cup") to byId.getValue("cook-g")
        Quantity.AREA -> byId.getValue("m2") to byId.getValue("ft2")
        Quantity.VOLUME -> byId.getValue("l") to byId.getValue("galus")
        Quantity.DATA -> byId.getValue("go") to byId.getValue("mo")
        Quantity.TIME -> byId.getValue("h") to byId.getValue("min")
    }

    /** The value the field shows before the user types (a room's 20 °C, otherwise 1). */
    fun sampleValue(quantity: Quantity): Double = if (quantity == Quantity.TEMPERATURE) 20.0 else 1.0

    /** True when going from [from] to [to] needs to know what is weighed (a cup of flour into grams). */
    fun needsIngredient(from: MeasureUnit, to: MeasureUnit): Boolean = from.quantity == Quantity.COOKING && from.mass != to.mass

    /** [value] in [from] written in [to]; NaN when they do not measure the same thing. */
    fun convert(value: Double, from: MeasureUnit, to: MeasureUnit, ingredient: Ingredient = Ingredient.WATER): Double {
        if (from.quantity != to.quantity) return Double.NaN
        var base = from.toBase(value)
        if (needsIngredient(from, to)) base = if (from.mass) base / ingredient.density else base * ingredient.density
        return to.fromBase(base)
    }

    /** [value] in [from] written in every unit of its quantity. */
    fun table(value: Double, from: MeasureUnit, ingredient: Ingredient = Ingredient.WATER): List<Converted> =
        of(from.quantity).map { Converted(it, convert(value, from, it, ingredient)) }

    /** Below absolute zero, or a negative length: a value that cannot exist (the tool says so instead of a result). */
    fun impossible(value: Double, unit: MeasureUnit): Boolean = when (unit.quantity) {
        Quantity.TEMPERATURE -> unit.toBase(value) < -273.15 - 1e-9
        else -> value < 0
    }

    // MARK: - Reading what the user types

    /** "Mètre carré" → "metre carre": no accents, lower case, single spaces (°, ², ³, / and quotes kept). */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private val vulgar = mapOf('½' to " 1/2", '¼' to " 1/4", '¾' to " 3/4", '⅓' to " 1/3", '⅔' to " 2/3")

    /** "1,5", "1.5", "1 500", "1/2", "1 1/2", "1½", "-4" → the number; null when it is not one. */
    fun parseNumber(text: String): Double? {
        var t = text
        vulgar.forEach { (c, s) -> t = t.replace(c.toString(), s) }
        t = t.replace(' ', ' ').replace(' ', ' ').replace('−', '-').trim()
        if (t.isEmpty()) return null
        Regex("^(-?)(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)$").find(t)?.let { m ->
            val (sign, whole, num, den) = m.destructured
            val d = den.toDouble()
            if (d == 0.0) return null
            val v = whole.toDouble() + num.toDouble() / d
            return if (sign == "-") -v else v
        }
        Regex("^(-?[\\d.,]+)\\s*/\\s*([\\d.,]+)$").find(t)?.let { m ->
            val n = decimal(m.groupValues[1]) ?: return null
            val d = decimal(m.groupValues[2]) ?: return null
            return if (d == 0.0) null else n / d
        }
        return decimal(t)
    }

    /** "1 234,5" / "1,234.5" / "1.5" → 1234.5 / 1234.5 / 1.5. */
    private fun decimal(text: String): Double? {
        var t = text.replace(" ", "")
        if (!Regex("^-?[\\d.,]*\\d[\\d.,]*$").matches(t)) return null
        val dot = t.lastIndexOf('.')
        val comma = t.lastIndexOf(',')
        t = when {
            dot >= 0 && comma >= 0 -> if (dot > comma) t.replace(",", "") else t.replace(".", "").replace(',', '.')
            comma >= 0 -> if (t.count { it == ',' } > 1) t.replace(",", "") else t.replace(',', '.')
            dot >= 0 && t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private val numberStart = Regex("^\\s*(-?(?:\\d+\\s+\\d+\\s*/\\s*\\d+|[\\d.,]+\\s*/\\s*[\\d.,]+|\\d[\\d .,  ]*))")
    private val separator = Regex("\\s+(?:en|in|to|vers|dans)\\s+|\\s*(?:->|→|=)\\s*")

    /**
     * "5", "5 km", "350 °F en °C", "1 1/2 cup en g": the value and the units named, found among the units of
     * [preferred] first (so "1 l" stays in Cuisine when the user is in Cuisine). Null when there is no number.
     */
    fun parseQuery(text: String, preferred: Quantity? = null): Query? {
        var t = text
        vulgar.forEach { (c, s) -> t = t.replace(c.toString(), s) }
        val match = numberStart.find(t) ?: return null
        val value = parseNumber(match.groupValues[1]) ?: return null
        val rest = fold(t.substring(match.range.last + 1))
        if (rest.isEmpty()) return Query(value, null, null)
        val parts = rest.split(separator, limit = 2)
        val fromWord = parts[0].trim()
        val toWord = parts.getOrNull(1)?.trim().orEmpty()
        val fromCandidates = candidates(fromWord)
        val toCandidates = if (toWord.isEmpty()) emptyList() else candidates(toWord)
        // Cuisine last: "1 l" or "200 g" alone are a Volume and a Poids unless the user is cooking.
        val order = listOfNotNull(preferred) + Quantity.entries.filter { it != preferred }.sortedBy { it == Quantity.COOKING }
        if (fromCandidates.isEmpty()) return Query(value, null, null)
        for (q in order) {
            val from = fromCandidates.firstOrNull { it.quantity == q } ?: continue
            if (toCandidates.isEmpty()) return Query(value, from, null)
            val to = toCandidates.firstOrNull { it.quantity == q } ?: continue
            return Query(value, from, to)
        }
        // The two units do not measure the same thing: keep the first one.
        val from = order.firstNotNullOf { q -> fromCandidates.firstOrNull { it.quantity == q } }
        return Query(value, from, null)
    }

    /** How an alias is compared: folded, without dots ("c. à s." = "c a s"). */
    internal fun key(text: String): String = fold(text.replace(".", " "))

    private fun candidates(word: String): List<MeasureUnit> {
        val w = key(word)
        if (w.isEmpty()) return emptyList()
        // "°C" or "° c" or "degres c": the degree sign is optional.
        val bare = w.replace("°", "").trim()
        return all.filter { w in it.aliases || bare in it.aliases || ("°$bare") in it.aliases }
    }

    // MARK: - Writing a result

    private val superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    /**
     * A result written the French way with 6 significant digits at most: "1 609,34", "0,000123", "-40". Huge or tiny
     * numbers in powers of ten: "1,5 × 10¹⁵". The thousands are split by a narrow no-break space.
     */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        if (value == 0.0) return "0"
        val a = abs(value)
        if (a >= 1e15 || a < 1e-6) {
            val exp = floor(log10(a)).toInt()
            val mantissa = value / 10.0.pow(exp)
            val m = plain(BigDecimal(mantissa).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros())
            val e = exp.toString().map { c -> if (c == '-') '⁻' else superscripts[c - '0'] }.joinToString("")
            return "$m × 10$e"
        }
        val decimals = (5 - floor(log10(a)).toInt()).coerceIn(0, 12)
        val rounded = BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.signum() == 0) "0" else plain(rounded)
    }

    private fun plain(number: BigDecimal): String {
        val text = number.toPlainString()
        val negative = text.startsWith("-")
        val digits = text.removePrefix("-")
        val whole = digits.substringBefore('.')
        val fraction = digits.substringAfter('.', "")
        val grouped = whole.reversed().chunked(3).joinToString(" ").reversed()
        return (if (negative) "-" else "") + grouped + (if (fraction.isEmpty()) "" else ",$fraction")
    }

    /** "1 609,34 mi" with the unit's symbol (no space before the "°" of °C, as written in France: "20 °C"). */
    fun formatWith(value: Double, unit: MeasureUnit): String = "${format(value)} ${unit.symbol}"

    /** The line a copy puts on the clipboard: "5 km = 3,10686 mi". */
    fun sentence(value: Double, from: MeasureUnit, result: Double, to: MeasureUnit): String =
        "${formatWith(value, from)} = ${formatWith(result, to)}"
}
