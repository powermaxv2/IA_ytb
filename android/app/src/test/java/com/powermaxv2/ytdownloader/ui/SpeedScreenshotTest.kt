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
import com.powermaxv2.ytdownloader.core.speed.SpeedPhase
import com.powermaxv2.ytdownloader.core.speed.SpeedSound
import com.powermaxv2.ytdownloader.core.speed.SpeedSource
import com.powermaxv2.ytdownloader.core.speed.SpeedUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.speed.SPEED_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.speed.SPEED_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.speed.SPEED_TITLE
import com.powermaxv2.ytdownloader.ui.tools.speed.SpeedActions
import com.powermaxv2.ytdownloader.ui.tools.speed.SpeedContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Vitesse vidéo in the three skins, with sample data (no controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SpeedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_speed_idle", SpeedUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_speed_idle", SpeedUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_speed_idle", SpeedUiState())

    @Test fun classicSlow() = shot(Skin.CLASSIC, "classic_speed_slow", slow)
    @Test fun paperSlow() = shot(Skin.PAPER, "paper_speed_slow", slow)
    @Test fun terminalSlow() = shot(Skin.TERMINAL, "terminal_speed_slow", slow)

    @Test fun classicFast() = shot(Skin.CLASSIC, "classic_speed_fast", fast)
    @Test fun paperFast() = shot(Skin.PAPER, "paper_speed_fast", fast)
    @Test fun terminalFast() = shot(Skin.TERMINAL, "terminal_speed_fast", fast)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_speed_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_speed_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_speed_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_speed_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_speed_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_speed_done", done)

    private fun shot(skin: Skin, name: String, state: SpeedUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = SPEED_TITLE,
                        command = SPEED_COMMAND,
                        subtitle = SPEED_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        SpeedContent(state, SpeedActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val video = SpeedSource(LocalFile(Uri.parse("content://sample/1"), "saut-plongeoir.mp4", 48_000_000L, "video/mp4"), 14_000L, true, 1080, 1920, 60.0, 48_000)
        val slow = SpeedUiState(SpeedPhase.Ready, video, speed = 0.25, sound = SpeedSound.NATURAL)
        val fast = slow.copy(speed = 4.0, sound = SpeedSound.TAPE)
        val progress = slow.copy(phase = SpeedPhase.Working(0.42f, "Ralenti ×0,25…"))
        val done = slow.copy(
            phase = SpeedPhase.Done(
                SavedFile(Uri.parse("content://media/1"), "video/mp4", "saut-plongeoir (ralenti ×0,25).mp4", 61_000_000L, "Movies/Gulp"),
                0.25,
                SpeedSound.NATURAL,
                56_000L,
            ),
        )
    }
}
