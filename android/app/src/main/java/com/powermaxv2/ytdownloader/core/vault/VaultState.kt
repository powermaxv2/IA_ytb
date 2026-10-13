package com.powermaxv2.ytdownloader.core.vault

/** Steps of choosing a code (a new vault, or a new code for the open one). */
enum class CodeStep { WARNING, CODE, CONFIRM }

/** Where the Coffre-fort is at. */
sealed interface VaultPhase {
    /** Choosing a code: the warning (new vault only), the code, the code again. */
    data class Setup(val step: CodeStep, val mismatch: Boolean = false, val changing: Boolean = false) : VaultPhase

    /** Closed: the keypad. [waitSeconds] > 0 after too many wrong codes. [shake] changes at each wrong code. */
    data class Locked(val error: String? = null, val waitSeconds: Int = 0, val shake: Int = 0, val forgot: Boolean = false) : VaultPhase

    /** Deriving the key from the code (about a second), or writing the vault. */
    data class Busy(val label: String) : VaultPhase

    /** Open: the files. */
    data object Open : VaultPhase
}

/** Files being sealed into the vault. */
data class VaultImport(val done: Int, val total: Int, val name: String, val fraction: Float)

/** What the last import did, and whether the originals can still be deleted from the phone. */
data class VaultImportDone(val added: Int, val failed: Int, val originals: Int, val deleted: Int? = null)

/** Everything the screen draws (also drawn by the screenshot tests). */
data class VaultUiState(
    val phase: VaultPhase = VaultPhase.Setup(CodeStep.WARNING),
    val items: List<VaultItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val filter: VaultKind? = null,
    val importing: VaultImport? = null,
    val lastImport: VaultImportDone? = null,
    /** Files being written back out to the phone: done / total. */
    val exporting: Pair<Int, Int>? = null,
    val viewing: VaultItem? = null,
    /** The viewer could not show it (too big, damaged…). */
    val viewError: String? = null,
    val settingsOpen: Boolean = false,
    val confirmDelete: Boolean = false,
    val confirmDestroy: Boolean = false,
    val fingerprintAvailable: Boolean = false,
    val fingerprintOn: Boolean = false,
    val usedBytes: Long = 0L,
) {
    /** Newest first, then the [filter]. */
    val shown: List<VaultItem> get() = items.sortedByDescending { it.addedAt }.filter { filter == null || it.kind == filter }

    /** The kinds present, for the filter chips (only when there are at least two). */
    val kinds: List<VaultKind> get() = VaultKind.entries.filter { k -> items.any { it.kind == k } }

    val selecting: Boolean get() = selected.isNotEmpty()

    val busy: Boolean get() = importing != null || exporting != null
}

object VaultWords {
    fun files(n: Int) = if (n <= 1) "$n fichier" else "$n fichiers"

    /** "1,4 Mo", "820 Ko". */
    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f Go".format(java.util.Locale.FRANCE, bytes / 1e9)
        bytes >= 1_000_000L -> "%.1f Mo".format(java.util.Locale.FRANCE, bytes / 1e6)
        bytes >= 1_000L -> "${bytes / 1_000} Ko"
        else -> "$bytes o"
    }

    fun wait(seconds: Int): String = if (seconds >= 60) "${(seconds + 59) / 60} min" else "$seconds s"
}
