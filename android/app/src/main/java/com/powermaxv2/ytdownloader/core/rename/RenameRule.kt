package com.powermaxv2.ytdownloader.core.rename

import com.powermaxv2.ytdownloader.core.files.SafeNames
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// The Renommeur's rule and its before / after preview. Pure (no Android), unit tested: the controller only feeds it
// the picked files and applies the rows it returns.

/** How a date block is written. */
enum class NameDateFormat(val label: String) {
    DAY("2026-10-13"),
    COMPACT("20261013"),
    STAMP("20261013_184207"),
    FRENCH("13-10-2026"),
    MONTH("2026-10"),
}

/** Letter case applied to the whole new name (its extension stays as it was). */
enum class NameCase(val label: String) {
    KEEP("Tel quel"),
    LOWER("minuscules"),
    UPPER("MAJUSCULES"),
    TITLE("Première Lettre"),
}

/** Order of the files, which decides their numbers. */
enum class RenameOrder(val label: String) {
    PICKED("Choix"),
    NAME("Nom"),
    DATE("Date"),
}

/** One piece of the new name; the pieces are written one after the other. */
sealed interface NameBlock {
    /** Fixed text ("Vacances_"). */
    data class Text(val text: String) : NameBlock

    /** 1, 2, 3… or 001, 002, 003… */
    data class Counter(val start: Int = 1, val step: Int = 1, val digits: Int = 3) : NameBlock

    /** The day the photo was taken (EXIF), else the day the file was last changed. */
    data class Date(val format: NameDateFormat = NameDateFormat.DAY) : NameBlock

    /** The current name, without its extension. */
    data object Original : NameBlock
}

/**
 * The whole rule: [blocks] give the name, then every [find] becomes [replace] (case ignored when [ignoreCase]),
 * then [case] applies. The extension of each file is always kept.
 */
data class RenameRule(
    val blocks: List<NameBlock> = listOf(NameBlock.Original),
    val find: String = "",
    val replace: String = "",
    val ignoreCase: Boolean = true,
    val case: NameCase = NameCase.KEEP,
    val order: RenameOrder = RenameOrder.PICKED,
) {
    companion object {
        /** Ready-made rules offered as chips. */
        val presets: List<Pair<String, RenameRule>> = listOf(
            "Date + n°" to RenameRule(listOf(NameBlock.Date(), NameBlock.Text("_"), NameBlock.Counter())),
            "Texte + n°" to RenameRule(listOf(NameBlock.Text("Vacances_"), NameBlock.Counter())),
            "Remplacer" to RenameRule(listOf(NameBlock.Original), find = "IMG_", replace = "Photo_"),
            "Minuscules" to RenameRule(listOf(NameBlock.Original), case = NameCase.LOWER),
        )
    }
}

/** A picked file as the rule sees it. [takenAt] / [modifiedAt] are epoch milliseconds when known. */
data class RenameSource(
    val key: String,
    val name: String,
    val takenAt: Long? = null,
    val modifiedAt: Long? = null,
    /** False when the folder holding the file does not let Gulp rename it: a renamed copy is saved instead. */
    val renamable: Boolean = true,
) {
    val date: Long? get() = takenAt ?: modifiedAt
}

enum class RowStatus {
    /** Gets the new name. */
    READY,

    /** The rule gives the name it already has: left alone. */
    SAME,

    /** Two files would get the same name (or a name another untouched file of the folder has): left alone. */
    CLASH,

    /** The rule gives an empty name: left alone. */
    EMPTY,
}

data class RenameRow(val source: RenameSource, val newName: String, val status: RowStatus)

data class RenamePreview(val rows: List<RenameRow>) {
    val ready: List<RenameRow> get() = rows.filter { it.status == RowStatus.READY }
    val clashes: Int get() = rows.count { it.status == RowStatus.CLASH }
    val unchanged: Int get() = rows.count { it.status == RowStatus.SAME }
    val empty: Int get() = rows.count { it.status == RowStatus.EMPTY }
    val copies: Int get() = ready.count { !it.source.renamable }
}

object RenamePlan {
    /** Longest counter padding offered (000001). */
    const val MAX_DIGITS = 6

    /**
     * The new name of every file, in the order of [rule].order. [taken] = names already used in the folder by files
     * that are not renamed (folder mode); a new name equal to one of them, or to another new name, is a [RowStatus.CLASH].
     * Names are compared case-insensitively, as on the phone's shared storage.
     */
    fun preview(
        files: List<RenameSource>,
        rule: RenameRule,
        taken: Set<String> = emptySet(),
        zone: TimeZone = TimeZone.getDefault(),
    ): RenamePreview {
        val sorted = sort(files, rule.order)
        val names = sorted.mapIndexed { index, file -> file to newName(file, index, rule, zone) }
        // A file may take the old name of another file of the batch (they swap): only untouched names block.
        val keys = names.groupingBy { (_, name) -> name?.let(::key) }.eachCount()
        val blocked = taken.map(::key).toSet()
        val rows = names.map { (file, name) ->
            val status = when {
                name == null -> RowStatus.EMPTY
                name == file.name -> RowStatus.SAME
                (keys[key(name)] ?: 0) > 1 -> RowStatus.CLASH
                key(name) in blocked -> RowStatus.CLASH
                else -> RowStatus.READY
            }
            RenameRow(file, name ?: file.name, status)
        }
        // A file that keeps its name still holds it: whoever else wants it clashes.
        val kept = rows.filter { it.status != RowStatus.READY }.map { key(it.source.name) }.toSet()
        return RenamePreview(rows.map { if (it.status == RowStatus.READY && key(it.newName) in kept) it.copy(status = RowStatus.CLASH) else it })
    }

    /**
     * Renaming in place can hit a name a file of the batch still has (A → B while B → C, or "photo" → "Photo" on a
     * storage that ignores case). Those rows go through a temporary name first; the result lists their keys.
     */
    fun needsDetour(rows: List<RenameRow>): Set<String> {
        val current = rows.map { key(it.source.name) }.toSet()
        return rows.filter { it.status == RowStatus.READY && key(it.newName) in current }.map { it.source.key }.toSet()
    }

    fun sort(files: List<RenameSource>, order: RenameOrder): List<RenameSource> = when (order) {
        RenameOrder.PICKED -> files
        RenameOrder.NAME -> files.sortedWith(compareBy(NaturalOrder) { it.name })
        RenameOrder.DATE -> files.sortedWith(compareBy<RenameSource, Long?>(nullsLast()) { it.date }.thenBy(NaturalOrder) { it.name })
    }

    /** The new full name of [file] (index = its place in the sorted list), or null when the rule gives nothing. */
    fun newName(file: RenameSource, index: Int, rule: RenameRule, zone: TimeZone = TimeZone.getDefault()): String? {
        val original = SafeNames.baseOf(file.name)
        val ext = extension(file.name)
        val built = rule.blocks.joinToString("") { block ->
            when (block) {
                is NameBlock.Text -> block.text
                NameBlock.Original -> original
                is NameBlock.Counter -> counter(block, index)
                is NameBlock.Date -> file.date?.let { formatDate(it, block.format, zone) } ?: "sans-date"
            }
        }
        val replaced = if (rule.find.isEmpty()) built else built.replace(rule.find, rule.replace, ignoreCase = rule.ignoreCase)
        val cased = applyCase(replaced, rule.case)
        val clean = clean(cased) ?: return null
        val full = if (ext.isEmpty()) clean else "$clean.$ext"
        return SafeNames.sanitize(full)
    }

    fun counter(block: NameBlock.Counter, index: Int): String {
        val value = block.start.toLong() + block.step.toLong() * index
        val digits = block.digits.coerceIn(1, MAX_DIGITS)
        val text = kotlin.math.abs(value).toString().padStart(digits, '0')
        return if (value < 0) "-$text" else text
    }

    fun formatDate(millis: Long, format: NameDateFormat, zone: TimeZone): String {
        val c = Calendar.getInstance(zone, Locale.ROOT).apply { timeInMillis = millis }
        fun two(field: Int, plus: Int = 0) = (c.get(field) + plus).toString().padStart(2, '0')
        val y = c.get(Calendar.YEAR).toString().padStart(4, '0')
        val m = two(Calendar.MONTH, 1)
        val d = two(Calendar.DAY_OF_MONTH)
        return when (format) {
            NameDateFormat.DAY -> "$y-$m-$d"
            NameDateFormat.COMPACT -> "$y$m$d"
            NameDateFormat.STAMP -> "$y$m${d}_${two(Calendar.HOUR_OF_DAY)}${two(Calendar.MINUTE)}${two(Calendar.SECOND)}"
            NameDateFormat.FRENCH -> "$d-$m-$y"
            NameDateFormat.MONTH -> "$y-$m"
        }
    }

    /** EXIF "2026:10:13 18:42:07" (local time of the camera) → epoch milliseconds; null when it is not a date. */
    fun parseExifDate(text: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
        val m = Regex("""^\s*(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""").find(text ?: return null) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val year = y.toInt()
        if (year < 1900 || mo.toInt() !in 1..12 || d.toInt() !in 1..31 || h.toInt() > 23 || mi.toInt() > 59 || s.toInt() > 60) return null
        return Calendar.getInstance(zone, Locale.ROOT).apply {
            clear()
            set(year, mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt().coerceAtMost(59))
        }.timeInMillis
    }

    fun applyCase(text: String, case: NameCase): String = when (case) {
        NameCase.KEEP -> text
        NameCase.LOWER -> text.lowercase(Locale.FRENCH)
        NameCase.UPPER -> text.uppercase(Locale.FRENCH)
        NameCase.TITLE -> {
            val out = StringBuilder(text.length)
            var start = true
            for (ch in text) {
                out.append(if (start) ch.titlecaseChar() else ch.lowercaseChar())
                start = !ch.isLetterOrDigit()
            }
            out.toString()
        }
    }

    /** "photo.JPG" → "JPG": the extension as it is written (kept as is), "" when there is none. */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1)
    }

    /** Characters the phone refuses become "_", blanks and dots around are trimmed; null when nothing is left. */
    private fun clean(base: String): String? {
        val out = base
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F\u007F‎‏‪-‮⁦-⁩]"""), "_")
            .trim()
            .trimStart('.')
            .trimEnd('.', ' ')
            .trim()
        return out.ifEmpty { null }
    }

    private fun key(name: String) = name.lowercase(Locale.ROOT)
}

/** "IMG_2" before "IMG_10": digits compared as numbers, the rest ignoring case. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a).map { it.value }.toList()
        val y = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val diff = if (p[0].isDigit() && q[0].isDigit()) {
                val n = p.trimStart('0').ifEmpty { "0" }
                val m = q.trimStart('0').ifEmpty { "0" }
                if (n.length != m.length) n.length - m.length else n.compareTo(m)
            } else {
                p.compareTo(q, ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return x.size - y.size
    }
}
