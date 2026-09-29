package com.powermaxv2.ytdownloader.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val container: VideoContainer = VideoContainer.MP4,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val subtitles: Boolean = false,
    val subtitleLangs: String = "fr,en",
    val notify: Boolean = true,
    val autoUpdateYtDlp: Boolean = true,
    val haptics: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        sp.edit()
            .putString("container", next.container.name)
            .putString("audioFormat", next.audioFormat.name)
            .putBoolean("embedThumbnail", next.embedThumbnail)
            .putBoolean("embedMetadata", next.embedMetadata)
            .putBoolean("embedChapters", next.embedChapters)
            .putBoolean("subtitles", next.subtitles)
            .putString("subtitleLangs", next.subtitleLangs)
            .putBoolean("notify", next.notify)
            .putBoolean("autoUpdateYtDlp", next.autoUpdateYtDlp)
            .putBoolean("haptics", next.haptics)
            .apply()
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            container = enumOr(sp.getString("container", null), d.container),
            audioFormat = enumOr(sp.getString("audioFormat", null), d.audioFormat),
            embedThumbnail = sp.getBoolean("embedThumbnail", d.embedThumbnail),
            embedMetadata = sp.getBoolean("embedMetadata", d.embedMetadata),
            embedChapters = sp.getBoolean("embedChapters", d.embedChapters),
            subtitles = sp.getBoolean("subtitles", d.subtitles),
            subtitleLangs = sp.getString("subtitleLangs", null) ?: d.subtitleLangs,
            notify = sp.getBoolean("notify", d.notify),
            autoUpdateYtDlp = sp.getBoolean("autoUpdateYtDlp", d.autoUpdateYtDlp),
            haptics = sp.getBoolean("haptics", d.haptics),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    /** SHA-256 of the yt-dlp file installed by a verified update (integrity check at each launch). */
    var ytdlpHash: String?
        get() = sp.getString("ytdlpHash", null)
        set(value) = sp.edit().putString("ytdlpHash", value).apply()

    var lastYtdlpCheck: Long
        get() = sp.getLong("lastYtdlpCheck", 0)
        set(value) = sp.edit().putLong("lastYtdlpCheck", value).apply()
}
