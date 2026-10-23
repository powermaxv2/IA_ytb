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
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.voice.VoiceModelStatus
import com.powermaxv2.ytdownloader.core.voice.VoicePhase
import com.powermaxv2.ytdownloader.core.voice.VoiceSentence
import com.powermaxv2.ytdownloader.core.voice.VoiceSource
import com.powermaxv2.ytdownloader.core.voice.VoiceStep
import com.powermaxv2.ytdownloader.core.voice.VoiceUiState
import com.powermaxv2.ytdownloader.core.voice.VoiceWord
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.voice.VOICE_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.voice.VOICE_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.voice.VOICE_TITLE
import com.powermaxv2.ytdownloader.ui.tools.voice.VoiceActions
import com.powermaxv2.ytdownloader.ui.tools.voice.VoiceContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sin

/** Vocaux en texte in the three skins, with a sample voice note (no App, controller, model or real file). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class VoiceScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_voice_idle", VoiceUiState(), settle = 2_400)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_voice_idle", VoiceUiState(), settle = 2_400)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_voice_idle", VoiceUiState(model = VoiceModelStatus.READY), settle = 2_400)

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_voice_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_voice_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_voice_ready", ready)

    @Test fun classicDownload() = shot(Skin.CLASSIC, "classic_voice_download", download, settle = 900)
    @Test fun paperDownload() = shot(Skin.PAPER, "paper_voice_download", download, settle = 900)
    @Test fun terminalDownload() = shot(Skin.TERMINAL, "terminal_voice_download", download, settle = 900)

    @Test fun classicListening() = shot(Skin.CLASSIC, "classic_voice_listening", listening, settle = 900)
    @Test fun paperListening() = shot(Skin.PAPER, "paper_voice_listening", listening, settle = 900)
    @Test fun terminalListening() = shot(Skin.TERMINAL, "terminal_voice_listening", listening, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_voice_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_voice_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_voice_done", done)

    private fun shot(skin: Skin, name: String, state: VoiceUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = VOICE_TITLE,
                        command = VOICE_COMMAND,
                        subtitle = VOICE_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        VoiceContent(state, VoiceActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val source = VoiceSource(LocalFile(Uri.parse("content://sample/1"), "PTT-20261011-WA0007.opus", 61_440L, "audio/ogg"), durationMs = 23_000)
        val wave = List(48) { i -> (0.2 + abs(sin(i * 0.47) * 0.55 + sin(i * 1.3) * 0.25)).toFloat().coerceIn(0.06f, 1f) }

        fun sentence(start: Long, vararg words: Pair<String, Float>): VoiceSentence {
            var t = start
            return VoiceSentence(words.map { (w, c) -> VoiceWord(w, t, t + 300, c).also { t += 320 } })
        }

        val sentences = listOf(
            sentence(0, "salut" to 1f, "c'est" to 1f, "moi" to 1f),
            sentence(1_500, "je" to 1f, "t'envoie" to 0.98f, "un" to 0.9f, "vocal" to 0.92f, "pour" to 0.95f, "te" to 1f, "dire" to 1f, "que" to 0.9f, "je" to 1f, "serai" to 0.85f, "en" to 1f, "retard" to 0.97f, "ce" to 1f, "soir" to 1f),
            sentence(8_000, "on" to 1f, "se" to 1f, "retrouve" to 0.96f, "devant" to 0.9f, "le" to 1f, "cinéma" to 0.95f, "vers" to 0.8f, "vingt" to 0.7f, "heures" to 0.75f),
            sentence(13_500, "et" to 1f, "garde-moi" to 0.4f, "une" to 1f, "place" to 0.9f, "s'il" to 1f, "te" to 1f, "plaît" to 1f),
        )
        val ready = VoiceUiState(VoicePhase.Ready, source, VoiceModelStatus.MISSING)
        val download = ready.copy(phase = VoicePhase.Preparing(VoiceStep.DOWNLOAD, 0.62f, "26 / 42 Mo"))
        val listening = VoiceUiState(
            VoicePhase.Listening(sentences.take(2), "on se retrouve devant", 9_400, 23_000),
            source,
            VoiceModelStatus.READY,
            wave,
        )
        val done = VoiceUiState(VoicePhase.Done(sentences, 23_000, 6_800), source, VoiceModelStatus.READY, wave)
    }
}
