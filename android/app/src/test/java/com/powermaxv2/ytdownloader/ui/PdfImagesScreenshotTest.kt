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
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesPhase
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesSource
import com.powermaxv2.ytdownloader.core.pdfimages.PdfImagesUiState
import com.powermaxv2.ytdownloader.core.pdfimages.PdfPageInfo
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.pdfimages.PDF_IMAGES_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.pdfimages.PDF_IMAGES_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.pdfimages.PDF_IMAGES_TITLE
import com.powermaxv2.ytdownloader.ui.tools.pdfimages.PdfImagesActions
import com.powermaxv2.ytdownloader.ui.tools.pdfimages.PdfImagesContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PDF en images in the three skins, with drawn placeholder pages (no App, controller or real PDF). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PdfImagesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_pdfimages_idle", idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_pdfimages_idle", idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_pdfimages_idle", idle)

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_pdfimages_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_pdfimages_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_pdfimages_ready", ready)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_pdfimages_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_pdfimages_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_pdfimages_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_pdfimages_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_pdfimages_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_pdfimages_done", done)

    private fun shot(skin: Skin, name: String, state: PdfImagesUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PDF_IMAGES_TITLE,
                        command = PDF_IMAGES_COMMAND,
                        subtitle = PDF_IMAGES_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        PdfImagesContent(state, emptyMap(), PdfImagesActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val idle = PdfImagesUiState()
        val source = PdfImagesSource(
            LocalFile(Uri.parse("content://sample/1"), "Billet concert.pdf", 1_240_000L, "application/pdf"),
            (0 until 5).map { PdfPageInfo(it, 595f, 842f) } + PdfPageInfo(5, 842f, 595f),
        )
        val ready = idle.copy(phase = PdfImagesPhase.Ready, source = source, selected = setOf(1, 2, 3, 6), pagesText = "1-3, 6")
        val working = ready.copy(phase = PdfImagesPhase.Working(2, 4, 3))
        val done = ready.copy(
            phase = PdfImagesPhase.Done(
                listOf(SavedFile(Uri.parse("content://media/1"), "image/jpeg", "Billet concert - page 01.jpg", 318_000L, "Pictures/Gulp")),
                pictures = 4,
                zip = false,
            ),
        )
    }
}
