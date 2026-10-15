package com.powermaxv2.ytdownloader.core.bill

import kotlin.math.max

// Partage d'addition: a restaurant bill split between friends. Pure Kotlin (no Android), so it is tested on the JVM.
// Every amount is in cents (Long): the shares always add up to the cent to what is paid, never 0,01 € lost or found.

/** A currency the screen offers; [suffix] true writes "12,50 €", false "$12,50". */
enum class BillCurrency(val symbol: String, val suffix: Boolean) {
    EURO("€", true),
    DOLLAR("$", false),
    POUND("£", false),
    FRANC("CHF", true),
}

/** How the tip is given: none, a percentage of the bill, or an amount. */
sealed interface Tip {
    data object None : Tip
    data class Percent(val percent: Int) : Tip
    data class Amount(val cents: Long) : Tip
}

/**
 * One person at the table. [halves] is their part of what is shared, in halves (2 = one part, 1 = half a part,
 * 4 = double, 0 = shares nothing); [ownCents] is what they ordered alone (a bottle, a dessert), paid by them only.
 */
data class Diner(val name: String, val halves: Int = 2, val ownCents: Long = 0)

/** The whole bill: what the receipt says (tip excluded), the tip, the people, and who paid the waiter. */
data class Bill(
    val totalCents: Long,
    val diners: List<Diner>,
    val tip: Tip = Tip.None,
    /** Each share is rounded up to the whole unit (1 €); the extra goes to the tip. */
    val roundUp: Boolean = false,
    /** Index in [diners] of the one who paid the whole bill; the others pay them back. */
    val payer: Int? = null,
    val currency: BillCurrency = BillCurrency.EURO,
)

/** What one person pays: [baseCents] of the bill, [tipCents] of tip (rounding included), [cents] in all. */
data class Share(val name: String, val baseCents: Long, val tipCents: Long, val cents: Long)

/** Why a bill cannot be split yet. */
enum class BillProblem(val message: String) {
    NO_TOTAL("Écris le montant de l'addition."),
    NO_DINERS("Ajoute au moins une personne."),
    OWN_TOO_BIG("Les commandes perso dépassent l'addition."),
    NOBODY_SHARES("Le reste n'est partagé par personne : donne au moins une part à quelqu'un."),
}

/** The split of a [Bill]: the shares, in the order of the diners, or the [problem] that stops it. */
data class Split(
    val shares: List<Share>,
    val totalCents: Long,
    val tipCents: Long,
    val problem: BillProblem? = null,
) {
    /** What everyone pays together: the bill and the tip (rounding included). */
    val grandCents: Long get() = totalCents + tipCents
    val ok: Boolean get() = problem == null
}

/** "Tom doit 23,40 € à Léa". */
data class Payback(val from: String, val to: String, val cents: Long)

object BillSplitter {
    const val MAX_DINERS = 20
    const val MAX_HALVES = 20

    /** Up to 1 000 000 (a million) in the currency, far beyond any bill. */
    const val MAX_CENTS = 100_000_000L

    /** Tip percentages offered as chips. */
    val tipPercents = listOf(0, 5, 10, 15, 20)

    fun split(bill: Bill): Split {
        val total = bill.totalCents
        val diners = bill.diners
        val problem = when {
            total <= 0 -> BillProblem.NO_TOTAL
            diners.isEmpty() -> BillProblem.NO_DINERS
            diners.sumOf { it.ownCents.coerceAtLeast(0) } > total -> BillProblem.OWN_TOO_BIG
            diners.sumOf { it.halves.coerceAtLeast(0) } == 0 && total > diners.sumOf { it.ownCents.coerceAtLeast(0) } -> BillProblem.NOBODY_SHARES
            else -> null
        }
        if (problem != null) return Split(emptyList(), max(total, 0), 0, problem)

        val own = diners.map { it.ownCents.coerceAtLeast(0) }
        val halves = diners.map { it.halves.coerceAtLeast(0).toLong() }
        val shared = total - own.sum()
        // Halves only matter when something is shared; nobody sharing an empty rest is fine.
        val weight = halves.sum().coerceAtLeast(1)

        // Exact part of the bill of each one: (own × W + shared × halves) / W.
        val baseNumerators = diners.indices.map { own[it] * weight + shared * halves[it] }
        val base = allocate(baseNumerators, weight, total)

        val tip = tipCents(bill.tip, total)
        // With a tip, everyone gives the same percentage of their part: exact part × (total + tip) / total. Shared on
        // its own (not part + tip share), so two people with the same part pay the same, to the cent left over.
        val paid = if (tip == 0L) base else allocate(baseNumerators.map { it * (total + tip) }, weight * total, total + tip)
        val rounded = if (bill.roundUp) paid.map { roundUpToUnit(it) } else paid
        val shares = diners.indices.map { Share(diners[it].name, base[it], rounded[it] - base[it], rounded[it]) }
        return Split(shares, total, rounded.sum() - total)
    }

    /** The tip in cents for a bill of [total] cents. */
    fun tipCents(tip: Tip, total: Long): Long = when (tip) {
        Tip.None -> 0
        is Tip.Percent -> (total * tip.percent.coerceIn(0, 100) + 50) / 100
        is Tip.Amount -> tip.cents.coerceIn(0, MAX_CENTS)
    }

    /** Who gives what to [Bill.payer]; empty without a payer or when the split does not work. */
    fun paybacks(bill: Bill, split: Split): List<Payback> {
        val payer = bill.payer ?: return emptyList()
        if (!split.ok || payer !in split.shares.indices) return emptyList()
        val to = displayName(bill.diners[payer].name, payer)
        return split.shares.mapIndexedNotNull { i, share ->
            if (i == payer || share.cents <= 0) null else Payback(displayName(share.name, i), to, share.cents)
        }
    }

    /**
     * Shares [numerators]/[denominator] so that the parts add up to [sum] exactly: each one gets its floor, then the
     * cents left go to the biggest remainders (the first one wins a tie). [numerators] must add up to sum × denominator.
     */
    internal fun allocate(numerators: List<Long>, denominator: Long, sum: Long): List<Long> {
        val floors = numerators.map { it / denominator }
        var left = sum - floors.sum()
        val result = floors.toMutableList()
        val order = numerators.indices.sortedWith(compareByDescending<Int> { numerators[it] % denominator }.thenBy { it })
        for (i in order) {
            if (left <= 0) break
            result[i] = result[i] + 1
            left--
        }
        return result
    }

    private fun roundUpToUnit(cents: Long): Long = (cents + 99) / 100 * 100

    // MARK: - Text

    /** "Léa", or "Personne 3" for a name left blank. */
    fun displayName(name: String, index: Int): String = name.trim().ifEmpty { "Personne ${index + 1}" }

    /** Reads "86", "86,5", "86.50", "1 234,90 €" → cents; null when it is not an amount. */
    fun parseAmount(text: String): Long? {
        val clean = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it in "€$£" }
            .removeSuffix("CHF").removePrefix("CHF")
        if (clean.isEmpty()) return null
        val match = Regex("^(\\d{1,7})(?:[.,](\\d{0,2}))?$").matchEntire(clean) ?: return null
        val units = match.groupValues[1].toLong()
        val decimals = match.groupValues[2].padEnd(2, '0')
        val cents = units * 100 + decimals.toLong()
        return cents.takeIf { it <= MAX_CENTS }
    }

    /** 2340 → "23,40 €" (or "$23,40"); thousands split by a narrow space: "1 234,00 €". */
    fun format(cents: Long, currency: BillCurrency = BillCurrency.EURO): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val units = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val number = "$units,${(abs % 100).toString().padStart(2, '0')}"
        return if (currency.suffix) "$sign$number ${currency.symbol}" else "$sign${currency.symbol}$number"
    }

    /** "1", "½", "1½", "2": a number of parts written from halves. */
    fun partsText(halves: Int): String {
        val whole = halves / 2
        val half = halves % 2 == 1
        return when {
            half && whole == 0 -> "½"
            half -> "$whole½"
            else -> "$whole"
        }
    }

    /** The recap to paste in the group's chat. */
    fun recap(bill: Bill, split: Split): String {
        if (!split.ok) return ""
        val c = bill.currency
        val lines = mutableListOf<String>()
        val tip = split.tipCents
        lines += if (tip > 0) {
            val label = (bill.tip as? Tip.Percent)?.takeIf { !bill.roundUp }?.let { " ${it.percent} %" }.orEmpty()
            "🧾 Addition : ${format(split.totalCents, c)} + pourboire$label ${format(tip, c)} = ${format(split.grandCents, c)}"
        } else {
            "🧾 Addition : ${format(split.totalCents, c)}"
        }
        lines += ""
        split.shares.forEachIndexed { i, share ->
            val d = bill.diners[i]
            val notes = listOfNotNull(
                partsText(d.halves).takeIf { d.halves != 2 }?.let { if (d.halves <= 2) "$it part" else "$it parts" },
                d.ownCents.takeIf { it > 0 }?.let { "dont ${format(it, c)} perso" },
            )
            val note = if (notes.isEmpty()) "" else " (${notes.joinToString(", ")})"
            lines += "• ${displayName(share.name, i)} : ${format(share.cents, c)}$note"
        }
        val back = paybacks(bill, split)
        if (back.isNotEmpty()) {
            lines += ""
            lines += "💸 ${back.first().to} a tout payé :"
            back.forEach { lines += "${it.from} → ${it.to} : ${format(it.cents, c)}" }
        }
        return lines.joinToString("\n")
    }
}
