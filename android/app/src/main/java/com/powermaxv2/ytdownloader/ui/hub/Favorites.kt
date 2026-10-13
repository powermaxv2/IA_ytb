package com.powermaxv2.ytdownloader.ui.hub

// Tools starred on the home screen: a short list of ids, kept in the order they were starred, stored as one line.

object Favorites {
    /** More stars than this and the first one starred makes room (the home screen stays a home screen). */
    const val MAX = 12

    private val ID = Regex("[a-z0-9-]{1,40}")

    /** "converter,qr-code" → ["converter", "qr-code"]; anything that is not a tool id is dropped. */
    fun decode(text: String?): List<String> =
        text.orEmpty().split(',').map { it.trim() }.filter { ID.matches(it) }.distinct().takeLast(MAX)

    fun encode(ids: List<String>): String = ids.joinToString(",")

    /** Unstars [id] when it is starred, stars it at the end otherwise. */
    fun toggle(ids: List<String>, id: String): List<String> {
        if (!ID.matches(id)) return ids
        return if (id in ids) ids - id else (ids + id).takeLast(MAX)
    }

    /** The starred tools that are out in this version, in the order they were starred. */
    fun of(ids: List<String>, ready: List<GulpTool>): List<GulpTool> = ids.mapNotNull { id -> ready.firstOrNull { it.id == id } }
}
