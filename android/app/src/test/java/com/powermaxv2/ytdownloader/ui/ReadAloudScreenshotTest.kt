package com.powermaxv2.ytdownloader.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.readaloud.LanguageChoice
import com.powermaxv2.ytdownloader.core.readaloud.ReadAloudUiState
import com.powermaxv2.ytdownloader.core.readaloud.SpeechEngine
import com.powermaxv2.ytdownloader.core.readaloud.SpeechExport
import com.powermaxv2.ytdownloader.core.readaloud.SpeechSource
import com.powermaxv2.ytdownloader.core.readaloud.SpeechText
import com.powermaxv2.ytdownloader.core.readaloud.VoiceChoice
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.readaloud.READ_ALOUD_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.readaloud.READ_ALOUD_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.readaloud.READ_ALOUD_TITLE
import com.powermaxv2.ytdownloader.ui.tools.readaloud.ReadAloudActions
import com.powermaxv2.ytdownloader.ui.tools.readaloud.ReadAloudContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Lecture à voix haute in the three skins: empty, reading a PDF word by word, saving, saved. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ReadAloudScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_readaloud_idle", idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_readaloud_idle", idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_readaloud_idle", idle)

    @Test fun classicPlaying() = shot(Skin.CLASSIC, "classic_readaloud_playing", playing)
    @Test fun paperPlaying() = shot(Skin.PAPER, "paper_readaloud_playing", playing)
    @Test fun terminalPlaying() = shot(Skin.TERMINAL, "terminal_readaloud_playing", playing)

    @Test fun classicSaving() = shot(Skin.CLASSIC, "classic_readaloud_saving", saving)
    @Test fun paperSaving() = shot(Skin.PAPER, "paper_readaloud_saving", saving)
    @Test fun terminalSaving() = shot(Skin.TERMINAL, "terminal_readaloud_saving", saving)

    @Test fun classicSaved() = shot(Skin.CLASSIC, "classic_readaloud_saved", saved)
    @Test fun paperSaved() = shot(Skin.PAPER, "paper_readaloud_saved", saved)
    @Test fun terminalSaved() = shot(Skin.TERMINAL, "terminal_readaloud_saved", saved)

    private fun shot(skin: Skin, name: String, state: ReadAloudUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = READ_ALOUD_TITLE,
                        command = READ_ALOUD_COMMAND,
                        subtitle = READ_ALOUD_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ReadAloudContent(state, ReadAloudActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val voices = listOf(
            VoiceChoice("fr-fr-x-vlf-local", "France · voix 1", true),
            VoiceChoice("fr-fr-x-frd-local", "France · voix 2", true),
            VoiceChoice("fr-ca-x-caa-network", "Canada · voix 1", false),
        )
        val ready = ReadAloudUiState(
            engine = SpeechEngine.Ready,
            languages = listOf(LanguageChoice("fr", "Français"), LanguageChoice("en", "Anglais"), LanguageChoice("es", "Espagnol")),
            language = "fr",
            voices = voices,
            voice = voices.first().name,
        )
        val idle = ready

        private const val TEXT = "La photosynthèse se déroule dans les chloroplastes. La lumière fournit l'énergie nécessaire. " +
            "Le dioxyde de carbone et l'eau deviennent du glucose et de l'oxygène. Ce glucose nourrit toute la plante."
        val playing = ready.copy(
            text = TEXT,
            chunks = SpeechText.chunks(TEXT),
            sourceName = "Cours de SVT.pdf",
            sourceKind = SpeechSource.PDF,
            playing = true,
            current = 1,
            word = 3 until 10,
        )
        val saving = playing.copy(playing = false, word = null, export = SpeechExport.Working(3, 5))
        val saved = playing.copy(
            playing = false,
            word = null,
            export = SpeechExport.Done(SavedFile(Uri.EMPTY, "audio/mpeg", "Cours de SVT (lu).mp3", 412_000, "Music/Gulp"), 42),
        )
    }
}
