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
import com.powermaxv2.ytdownloader.core.compress.CompressItem
import com.powermaxv2.ytdownloader.core.compress.CompressPhase
import com.powermaxv2.ytdownloader.core.compress.CompressResult
import com.powermaxv2.ytdownloader.core.compress.CompressorUiState
import com.powermaxv2.ytdownloader.core.compress.Media
import com.powermaxv2.ytdownloader.core.compress.MediaInfo
import com.powermaxv2.ytdownloader.core.compress.Mode
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.COMPRESSOR_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.compress.COMPRESSOR_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.compress.COMPRESSOR_TITLE
import com.powermaxv2.ytdownloader.ui.tools.compress.CompressorActions
import com.powermaxv2.ytdownloader.ui.tools.compress.CompressorContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Compresseur in the three skins, with sample data (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CompressorScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_compress_idle", CompressorUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_compress_idle", CompressorUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_compress_idle", CompressorUiState())

    @Test fun classicOptions() = shot(Skin.CLASSIC, "classic_compress_options", options)
    @Test fun paperOptions() = shot(Skin.PAPER, "paper_compress_options", options)
    @Test fun terminalOptions() = shot(Skin.TERMINAL, "terminal_compress_options", options)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_compress_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_compress_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_compress_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_compress_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_compress_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_compress_done", done)

    @Test fun classicZip() = shot(Skin.CLASSIC, "classic_compress_zip", zip)
    @Test fun paperZip() = shot(Skin.PAPER, "paper_compress_zip", zip)
    @Test fun terminalZip() = shot(Skin.TERMINAL, "terminal_compress_zip", zip)

    private fun shot(skin: Skin, name: String, state: CompressorUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = COMPRESSOR_TITLE,
                        command = COMPRESSOR_COMMAND,
                        subtitle = COMPRESSOR_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        CompressorContent(state, CompressorActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        fun file(n: Int, name: String, size: Long, mime: String) = LocalFile(Uri.parse("content://sample/$n"), name, size, mime)

        val video = CompressItem(file(1, "Vacances à Nice.mp4", 1_200_000_000L, "video/mp4"), Media.VIDEO, MediaInfo(192.0, 3840, 2160))
        val options = CompressorUiState(CompressPhase.Ready, listOf(video), Mode.EACH)
        val progress = options.copy(phase = CompressPhase.Working(0.62f, "Compression de la vidéo…", 186_000_000L))
        val done = options.copy(
            phase = CompressPhase.Done(
                listOf(
                    CompressResult(
                        "Vacances à Nice (compressé).mp4",
                        1_200_000_000L,
                        312_000_000L,
                        SavedFile(Uri.parse("content://media/1"), "video/mp4", "Vacances à Nice (compressé).mp4", 312_000_000L, "Movies/Gulp"),
                    ),
                ),
            ),
        )
        val zip = CompressorUiState(
            CompressPhase.Ready,
            listOf(
                CompressItem(file(2, "Devis cuisine.pdf", 8_400_000L, "application/pdf"), Media.PDF, MediaInfo(pages = 6)),
                CompressItem(file(3, "IMG_2041.jpg", 6_100_000L, "image/jpeg"), Media.IMAGE, MediaInfo(width = 4032, height = 3024)),
                CompressItem(file(4, "IMG_2042.jpg", 5_800_000L, "image/jpeg"), Media.IMAGE, MediaInfo(width = 4032, height = 3024)),
                CompressItem(file(5, "Contrat.docx", 1_200_000L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), Media.OTHER),
                CompressItem(file(6, "Visite.mp4", 26_900_000L, "video/mp4"), Media.VIDEO, MediaInfo(48.0, 1920, 1080)),
            ),
            Mode.ZIP,
        )
    }
}
