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
import com.powermaxv2.ytdownloader.core.convert.Category
import com.powermaxv2.ytdownloader.core.convert.ConvertInput
import com.powermaxv2.ytdownloader.core.convert.ConvertOptions
import com.powermaxv2.ytdownloader.core.convert.ConvertPhase
import com.powermaxv2.ytdownloader.core.convert.ConverterUiState
import com.powermaxv2.ytdownloader.core.convert.Resolution
import com.powermaxv2.ytdownloader.core.convert.Target
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.CONVERTER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.convert.CONVERTER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.convert.CONVERTER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.convert.ConverterActions
import com.powermaxv2.ytdownloader.ui.tools.convert.ConverterContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Convertisseur in the three skins, with sample data (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ConverterScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_convert_idle", ConverterUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_convert_idle", ConverterUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_convert_idle", ConverterUiState())

    @Test fun classicOptions() = shot(Skin.CLASSIC, "classic_convert_options", options)
    @Test fun paperOptions() = shot(Skin.PAPER, "paper_convert_options", options)
    @Test fun terminalOptions() = shot(Skin.TERMINAL, "terminal_convert_options", options)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_convert_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_convert_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_convert_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_convert_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_convert_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_convert_done", done)

    private fun shot(skin: Skin, name: String, state: ConverterUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = CONVERTER_TITLE,
                        command = CONVERTER_COMMAND,
                        subtitle = CONVERTER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ConverterContent(state, ConverterActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val video = ConvertInput(
            listOf(LocalFile(Uri.parse("content://sample/1"), "Vacances à Nice.mkv", 1_240_000_000L, "video/x-matroska")),
            FileKind.VIDEO,
            Category.VIDEO,
        )
        val options = ConverterUiState(ConvertPhase.Ready, video, Target.MP4, ConvertOptions(resolution = Resolution.P1080))
        val progress = options.copy(phase = ConvertPhase.Converting(0.62f, "Conversion…"))
        val done = options.copy(
            phase = ConvertPhase.Done(
                listOf(SavedFile(Uri.parse("content://media/1"), "video/mp4", "Vacances à Nice.mp4", 412_000_000L, "Movies/Gulp")),
            ),
        )
    }
}
