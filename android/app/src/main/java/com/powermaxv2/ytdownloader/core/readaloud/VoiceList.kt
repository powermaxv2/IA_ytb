package com.powermaxv2.ytdownloader.core.readaloud

import java.util.Locale

/** A voice of the phone, as the tool needs it (from android.speech.tts.Voice). */
data class VoiceInfo(
    val name: String,
    /** BCP 47 tag, "fr-FR". */
    val tag: String,
    val offline: Boolean,
    /** Voice.QUALITY_*: 100 very low … 500 very high. */
    val quality: Int = 300,
)

/** A language the user can pick: [code] "fr", [label] "Français". */
data class LanguageChoice(val code: String, val label: String)

/** A voice the user can pick: [label] "France · voix 2", [offline] false = needs the Internet. */
data class VoiceChoice(val name: String, val label: String, val offline: Boolean)

// The voices of the phone's speech engine, sorted for people: languages in a useful order (French first), voices
// of a language by region then quality, named "France · voix 1" instead of "fr-fr-x-frd-local". Pure: tested.
object VoiceList {
    /** Languages most Gulp users read, in this order, before all the others (alphabetical). */
    private val PREFERRED = listOf("fr", "en", "es", "de", "it", "pt", "ar", "nl")

    const val MAX_LANGUAGES = 14
    const val MAX_VOICES = 10

    private val FRENCH = Locale.FRENCH

    private fun languageOf(tag: String): String = Locale.forLanguageTag(tag).language.lowercase(Locale.ROOT)

    /** "Français", "Anglais"… with a capital letter. */
    fun languageLabel(code: String): String =
        Locale(code).getDisplayLanguage(FRENCH).replaceFirstChar { it.titlecase(FRENCH) }.ifBlank { code }

    /**
     * Languages of [voices]: [device] (the phone's language) and French first, then [PREFERRED], then the rest in
     * alphabetical order of their French names; at most [MAX_LANGUAGES].
     */
    fun languages(voices: List<VoiceInfo>, device: String = "fr"): List<LanguageChoice> {
        val codes = voices.map { languageOf(it.tag) }.filter { it.isNotBlank() }.distinct()
        val order = (listOf(device.lowercase(Locale.ROOT), "fr") + PREFERRED).distinct()
        val first = order.filter { it in codes }
        val rest = (codes - first.toSet()).sortedBy { languageLabel(it) }
        return (first + rest).take(MAX_LANGUAGES).map { LanguageChoice(it, languageLabel(it)) }
    }

    /**
     * Voices of [language]: offline ones first, the region of the [device] locale and then the language's home region
     * (France for French) first, then better quality; labelled "<Région> · voix N".
     */
    fun voices(voices: List<VoiceInfo>, language: String, deviceRegion: String = "FR"): List<VoiceChoice> {
        val mine = voices.filter { languageOf(it.tag) == language }
        val home = homeRegion(language)
        val sorted = mine.sortedWith(
            compareBy<VoiceInfo>(
                { !it.offline },
                { regionRank(Locale.forLanguageTag(it.tag).country, deviceRegion, home) },
                { Locale.forLanguageTag(it.tag).country },
                { -it.quality },
                { it.name },
            ),
        ).take(MAX_VOICES)
        val counts = HashMap<String, Int>()
        return sorted.map { v ->
            val region = regionLabel(Locale.forLanguageTag(v.tag))
            val n = (counts[region] ?: 0) + 1
            counts[region] = n
            VoiceChoice(v.name, "$region · voix $n", v.offline)
        }
    }

    private fun regionRank(country: String, device: String, home: String): Int = when {
        country.equals(device, true) -> 0
        country.equals(home, true) -> 1
        else -> 2
    }

    private fun homeRegion(language: String): String = when (language) {
        "fr" -> "FR"
        "en" -> "US"
        "es" -> "ES"
        "pt" -> "BR"
        "de" -> "DE"
        "it" -> "IT"
        "nl" -> "NL"
        "ar" -> "SA"
        else -> ""
    }

    private fun regionLabel(locale: Locale): String =
        locale.getDisplayCountry(FRENCH).ifBlank { languageLabel(locale.language) }

    /** The language to start with: the phone's when a voice speaks it, else French, else the first one. */
    fun defaultLanguage(languages: List<LanguageChoice>, device: String): String? =
        languages.firstOrNull { it.code == device.lowercase(Locale.ROOT) }?.code
            ?: languages.firstOrNull { it.code == "fr" }?.code
            ?: languages.firstOrNull()?.code
}
