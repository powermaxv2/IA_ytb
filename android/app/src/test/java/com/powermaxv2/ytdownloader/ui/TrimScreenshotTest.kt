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
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.trim.TrimOutput
import com.powermaxv2.ytdownloader.core.trim.TrimPhase
import com.powermaxv2.ytdownloader.core.trim.TrimRange
import com.powermaxv2.ytdownloader.core.trim.TrimSource
import com.powermaxv2.ytdownloader.core.trim.TrimStrip
import com.powermaxv2.ytdownloader.core.trim.TrimUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.trim.TRIM_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.trim.TRIM_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.trim.TRIM_TITLE
import com.powermaxv2.ytdownloader.ui.tools.trim.TrimActions
import com.powermaxv2.ytdownloader.ui.tools.trim.TrimContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sin

/** The Découpeur in the three skins, with sample data (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TrimScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_trim_idle", TrimUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_trim_idle", TrimUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_trim_idle", TrimUiState())

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_trim_edit", edit)
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_trim_edit", edit)
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_trim_edit", edit)

    @Test fun classicSound() = shot(Skin.CLASSIC, "classic_trim_sound", sound)
    @Test fun paperSound() = shot(Skin.PAPER, "paper_trim_sound", sound)
    @Test fun terminalSound() = shot(Skin.TERMINAL, "terminal_trim_sound", sound)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_trim_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_trim_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_trim_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_trim_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_trim_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_trim_done", done)

    private fun shot(skin: Skin, name: String, state: TrimUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = TRIM_TITLE,
                        command = TRIM_COMMAND,
                        subtitle = TRIM_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        TrimContent(state, TrimActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val video = TrimSource(LocalFile(Uri.parse("content://sample/1"), "concert-lyon.mp4", 212_000_000L, "video/mp4"), 227_000L, true, 1920, 1080)
        val edit = TrimUiState(TrimPhase.Ready, video, TrimRange(50_000, 145_500), TrimOutput.VIDEO)
        val wave = FloatArray(48) { i -> (0.25f + 0.75f * abs(sin(i * 0.55f) * sin(i * 0.17f + 1f))) }
        val sound = TrimUiState(
            TrimPhase.Ready,
            TrimSource(LocalFile(Uri.parse("content://sample/2"), "Mémo vocal 12.m4a", 4_800_000L, "audio/mp4"), 312_000L, false),
            TrimRange(12_000, 52_000),
            TrimOutput.RINGTONE,
            strip = TrimStrip(wave = wave),
        )
        val progress = edit.copy(phase = TrimPhase.Working(0.58f, "Découpe sans réencodage…"))
        val done = edit.copy(
            phase = TrimPhase.Done(
                SavedFile(Uri.parse("content://media/1"), "video/mp4", "concert-lyon (0m50s-2m25s).mp4", 89_000_000L, "Movies/Gulp"),
                212_000_000L,
                TrimOutput.VIDEO,
                95_500L,
                copied = true,
            ),
        )
    }
}
