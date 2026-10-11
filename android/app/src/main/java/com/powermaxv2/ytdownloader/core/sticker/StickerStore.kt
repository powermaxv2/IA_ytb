package com.powermaxv2.ytdownloader.core.sticker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.random.Random

/** A pack made by Gulp, as WhatsApp reads it through [StickerProvider]. */
data class StickerPack(
    val id: String,
    val name: String,
    val animated: Boolean,
    /** File names in the pack folder, in order ("01.webp"…). */
    val stickers: List<String>,
    /** The emoji of each sticker (WhatsApp files the stickers under it). */
    val emoji: String,
    val createdAt: Long,
    /** Total size of the stickers, in bytes. */
    val bytes: Long = 0,
)

/**
 * The packs, in Gulp's private files (`files/stickers/<id>/`): WhatsApp copies nothing, it reads them from Gulp
 * when it shows them, so uninstalling Gulp removes them from WhatsApp too. A pack never changes once written
 * (WhatsApp caches by version); "modifying" one means making a new one.
 */
object StickerStore {
    private const val DIR = "stickers"
    private const val META = "pack.json"

    fun root(context: Context): File = File(context.filesDir, DIR)

    /** The packs, newest first; a folder that cannot be read is skipped. */
    fun list(context: Context): List<StickerPack> =
        root(context).listFiles()?.filter { it.isDirectory && StickerRules.isPackId(it.name) }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.createdAt }
            .orEmpty()

    fun find(context: Context, id: String?): StickerPack? {
        if (!StickerRules.isPackId(id)) return null
        return read(File(root(context), id!!))
    }

    private fun read(dir: File): StickerPack? = runCatching {
        val json = JSONObject(File(dir, META).readText())
        val files = json.getJSONArray("stickers").let { a -> (0 until a.length()).map { a.getString(it) } }
        if (files.isEmpty() || files.any { !StickerRules.isAssetName(it) || !File(dir, it).isFile }) return null
        StickerPack(
            id = json.getString("id").takeIf { it == dir.name } ?: return null,
            name = json.getString("name"),
            animated = json.getBoolean("animated"),
            stickers = files,
            emoji = json.optString("emoji", StickerRules.DEFAULT_EMOJI),
            createdAt = json.optLong("createdAt"),
            bytes = files.sumOf { File(dir, it).length() },
        )
    }.getOrNull()

    /** Writes a new pack (stickers already encoded, [tray] PNG) and returns it. */
    fun save(context: Context, name: String, animated: Boolean, stickers: List<ByteArray>, tray: ByteArray, emoji: String): StickerPack {
        require(stickers.size in StickerRules.MIN_STICKERS..StickerRules.MAX_STICKERS) { "Un pack WhatsApp a de 3 à 30 stickers." }
        val now = System.currentTimeMillis()
        val id = StickerRules.packId(now, Random.nextInt())
        val root = root(context).apply { mkdirs() }
        val temp = File(root, ".tmp-$id")
        temp.deleteRecursively()
        if (!temp.mkdirs()) throw IOException("Impossible de créer le pack.")
        try {
            val files = stickers.mapIndexed { i, bytes ->
                StickerRules.stickerFile(i).also { File(temp, it).writeBytes(bytes) }
            }
            File(temp, StickerRules.TRAY_FILE).writeBytes(tray)
            val json = JSONObject()
                .put("id", id)
                .put("name", StickerRules.packName(name))
                .put("animated", animated)
                .put("emoji", emoji.takeIf { it in StickerRules.EMOJIS } ?: StickerRules.DEFAULT_EMOJI)
                .put("createdAt", now)
                .put("stickers", JSONArray(files))
            File(temp, META).writeText(json.toString())
            val dir = File(root, id)
            if (!temp.renameTo(dir)) throw IOException("Impossible d'enregistrer le pack.")
            return read(dir) ?: throw IOException("Le pack enregistré est illisible.")
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }
    }

    fun delete(context: Context, id: String) {
        if (StickerRules.isPackId(id)) File(root(context), id).deleteRecursively()
    }

    /** A file of a pack, for the provider: only names this app writes, only inside the pack folder. */
    fun asset(context: Context, id: String?, name: String?): File? {
        if (!StickerRules.isPackId(id) || !StickerRules.isAssetName(name)) return null
        val file = File(File(root(context), id!!), name!!)
        return file.takeIf { it.isFile }
    }
}
