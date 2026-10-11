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
import com.powermaxv2.ytdownloader.core.wipe.PhotoFacts
import com.powermaxv2.ytdownloader.core.wipe.WipePhase
import com.powermaxv2.ytdownloader.core.wipe.WipePhoto
import com.powermaxv2.ytdownloader.core.wipe.WipeResult
import com.powermaxv2.ytdownloader.core.wipe.WipeUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.wipe.WIPE_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.wipe.WIPE_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.wipe.WIPE_TITLE
import com.powermaxv2.ytdownloader.ui.tools.wipe.WipeActions
import com.powermaxv2.ytdownloader.ui.tools.wipe.WipeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Gomme in the three skins, with sample photos (no App, controller or real files). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class WipeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_gomme_idle", WipeUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_gomme_idle", WipeUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_gomme_idle", WipeUiState())

    @Test fun classicScan() = shot(Skin.CLASSIC, "classic_gomme_scan", ready)
    @Test fun paperScan() = shot(Skin.PAPER, "paper_gomme_scan", ready)
    @Test fun terminalScan() = shot(Skin.TERMINAL, "terminal_gomme_scan", ready)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_gomme_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_gomme_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_gomme_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_gomme_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_gomme_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_gomme_done", done)

    private fun shot(skin: Skin, name: String, state: WipeUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = WIPE_TITLE,
                        command = WIPE_COMMAND,
                        subtitle = WIPE_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        WipeContent(state, WipeActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        fun photo(n: Int, name: String, facts: PhotoFacts) =
            WipePhoto(LocalFile(Uri.parse("content://sample/$n"), name, 3_400_000L, "image/jpeg"), facts)

        val pixel = PhotoFacts(
            latitude = 48.8650,
            longitude = 2.3780,
            make = "Google",
            model = "Pixel 8 Pro",
            deviceExtra = true,
            serial = true,
            software = "HDR+ 1.0",
            artist = "Maxime",
            date = "2026:10:10 18:42:07",
        )
        val photos = listOf(
            photo(1, "PXL_20261010_184207.jpg", pixel),
            photo(2, "PXL_20261010_190112.jpg", pixel.copy(date = "2026:10:10 19:01:12")),
            photo(3, "PXL_20261010_201530.jpg", pixel.copy(date = "2026:10:10 20:15:30")),
        )
        val ready = WipeUiState(WipePhase.Ready, photos)
        val working = ready.copy(phase = WipePhase.Working(1, 3))
        val done = ready.copy(
            phase = WipePhase.Done(
                photos.mapIndexed { i, p ->
                    val saved = SavedFile(Uri.parse("content://media/${i + 1}"), "image/jpeg", p.file.name, 3_300_000L, "Pictures/Gulp")
                    WipeResult(p.file.name, saved, reencoded = false, after = PhotoFacts(date = p.facts.date))
                },
            ),
        )
    }
}
