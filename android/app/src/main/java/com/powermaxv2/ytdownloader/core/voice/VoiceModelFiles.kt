package com.powermaxv2.ytdownloader.core.voice

import java.util.Locale

/**
 * The French speech model Gulp downloads once (Vosk "small fr" 0.22 by Alpha Cephei, Apache-2.0): where it comes
 * from, what it must weigh and hash, and which files of its ZIP are kept. Pure, unit tested.
 */
object VoiceModelFiles {
    const val ID = "vosk-model-small-fr-0.22"
    const val URL = "https://alphacephei.com/vosk/models/$ID.zip"

    /** Size and SHA-256 of the ZIP as published (checked 2026-10-11): anything else is refused, never unpacked. */
    const val ZIP_BYTES = 42_233_323L
    const val ZIP_SHA256 = "cabf6180e177eb9b3a9a9d43a437bd5e549f3a7d09525e5d69a3fed787be12ad"

    /** What the unpacked model takes on the phone (68.5 Mo), plus room for the ZIP while it is unpacked. */
    const val UNPACKED_BYTES = 68_483_807L
    const val NEEDED_FREE_BYTES = ZIP_BYTES + UNPACKED_BYTES + 20_000_000L

    /** Files Vosk needs to load the model; their absence means a broken install. */
    val REQUIRED = listOf("am/final.mdl", "graph/HCLr.fst", "graph/Gr.fst", "conf/mfcc.conf", "conf/model.conf", "ivector/final.ie")

    /** Largest single entry accepted (the biggest, graph/Gr.fst, is 24 Mo). */
    const val MAX_ENTRY_BYTES = 40_000_000L

    /**
     * Where an entry of the ZIP goes inside the model folder: its path without the top "vosk-model-small-fr-0.22/"
     * folder, or null to skip it (folders, the top folder itself, or anything that could leave the model folder).
     */
    fun target(entryName: String): String? {
        val name = entryName.replace('\\', '/')
        if (name.endsWith("/")) return null
        val inside = name.removePrefix("$ID/")
        if (inside == name) return null
        val parts = inside.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains('\u0000') }) return null
        if (parts.size > 4) return null
        return inside
    }

    /** True when every file of [REQUIRED] is among [present] (paths relative to the model folder). */
    fun complete(present: Collection<String>): Boolean = REQUIRED.all { it in present }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }

    /** "41 Mo" for the download size shown before asking. */
    val downloadLabel: String get() = "${(ZIP_BYTES + 500_000) / 1_000_000} Mo"
}
