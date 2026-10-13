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
import com.powermaxv2.ytdownloader.core.watermark.WatermarkInk
import com.powermaxv2.ytdownloader.core.watermark.WatermarkPhase
import com.powermaxv2.ytdownloader.core.watermark.WatermarkSource
import com.powermaxv2.ytdownloader.core.watermark.WatermarkUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.watermark.WATERMARK_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.watermark.WATERMARK_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.watermark.WATERMARK_TITLE
import com.powermaxv2.ytdownloader.ui.tools.watermark.WatermarkActions
import com.powermaxv2.ytdownloader.ui.tools.watermark.WatermarkContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Filigrane in the three skins, on the drawn sample card (no App, controller or real file). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class WatermarkScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_watermark_idle", idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_watermark_idle", idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_watermark_idle", idle)

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_watermark_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_watermark_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_watermark_ready", ready)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_watermark_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_watermark_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_watermark_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_watermark_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_watermark_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_watermark_done", done)

    private fun shot(skin: Skin, name: String, state: WatermarkUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = WATERMARK_TITLE,
                        command = WATERMARK_COMMAND,
                        subtitle = WATERMARK_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        WatermarkContent(state, WatermarkActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val idle = WatermarkUiState(date = "13/10/2026")
        val pdf = WatermarkSource(
            LocalFile(Uri.parse("content://sample/1"), "Bulletin de paie septembre.pdf", 412_000L, "application/pdf"),
            pdf = true,
            pages = 3,
            width = 595,
            height = 842,
        )
        val ready = idle.copy(phase = WatermarkPhase.Ready, source = pdf)
        val working = ready.copy(phase = WatermarkPhase.Working(1, 3), style = ready.style.copy(ink = WatermarkInk.RED))
        val done = working.copy(
            phase = WatermarkPhase.Done(
                SavedFile(Uri.parse("content://media/1"), "application/pdf", "Bulletin de paie septembre - filigrane.pdf", 431_000L, "Download/Gulp"),
            ),
        )
    }
}
