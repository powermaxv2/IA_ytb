package com.powermaxv2.ytdownloader.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Selectable look of the whole app (Réglages → Interface). */
enum class Skin(val title: String, val subtitle: String) {
    CLASSIC("Gulp", "Sombre, dégradé rouge orangé"),
    PAPER("Papier", "Clair, crème et encre"),
    TERMINAL("Terminal", "Console orange phosphore"),
}

data class Settings(
    val haptics: Boolean = true,
    val skin: Skin = Skin.CLASSIC,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        sp.edit()
            .putBoolean("haptics", next.haptics)
            .putString("skin", next.skin.name)
            .apply()
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            haptics = sp.getBoolean("haptics", d.haptics),
            skin = enumOr(sp.getString("skin", null), d.skin),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    /** Id of the last tool opened (shown big on the home screen); null until a tool has been used. */
    /** Last codes read by the QR code tool (QrHistory.encode), on this phone only. */
    var qrHistory: String?
        get() = sp.getString("qrHistory", null)
        set(value) = sp.edit().putString("qrHistory", value).apply()

    var lastToolId: String?
        get() = sp.getString("lastToolId", null)
        set(value) = sp.edit().putString("lastToolId", value).apply()

    /** Ids of the tools starred on the home screen, in the order they were starred (Favorites.encode). */
    var favoriteTools: String?
        get() = sp.getString("favoriteTools", null)
        set(value) = sp.edit().putString("favoriteTools", value).apply()

    /** Gulp Plus unlocked (set by the billing backend after a purchase or a restore). */
    var plusUnlocked: Boolean
        get() = sp.getBoolean("plusUnlocked", false)
        set(value) = sp.edit().putBoolean("plusUnlocked", value).apply()

    /** Total given through the donation button, in cents, on this phone. */
    var donatedCents: Int
        get() = sp.getInt("donatedCents", 0)
        set(value) = sp.edit().putInt("donatedCents", value).apply()
}
