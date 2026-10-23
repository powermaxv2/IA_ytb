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
import com.powermaxv2.ytdownloader.core.resize.CropRatio
import com.powermaxv2.ytdownloader.core.resize.Dims
import com.powermaxv2.ytdownloader.core.resize.ResizeMode
import com.powermaxv2.ytdownloader.core.resize.ResizePhase
import com.powermaxv2.ytdownloader.core.resize.ResizePhoto
import com.powermaxv2.ytdownloader.core.resize.ResizeResult
import com.powermaxv2.ytdownloader.core.resize.ResizeSettings
import com.powermaxv2.ytdownloader.core.resize.ResizeUiState
import com.powermaxv2.ytdownloader.core.resize.SidePreset
import com.powermaxv2.ytdownloader.core.resize.WeightGoal
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.resize.RESIZE_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.resize.RESIZE_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.resize.RESIZE_TITLE
import com.powermaxv2.ytdownloader.ui.tools.resize.ResizeActions
import com.powermaxv2.ytdownloader.ui.tools.resize.ResizeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Redimensionneur in the three skins, with sample photos (no App, controller or real files). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ResizeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_resize_idle", ResizeUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_resize_idle", ResizeUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_resize_idle", ResizeUiState())

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_resize_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_resize_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_resize_ready", ready)

    @Test fun classicWeight() = shot(Skin.CLASSIC, "classic_resize_weight", weight)
    @Test fun paperWeight() = shot(Skin.PAPER, "paper_resize_weight", weight)
    @Test fun terminalWeight() = shot(Skin.TERMINAL, "terminal_resize_weight", weight)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_resize_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_resize_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_resize_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_resize_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_resize_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_resize_done", done)

    private fun shot(skin: Skin, name: String, state: ResizeUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = RESIZE_TITLE,
                        command = RESIZE_COMMAND,
                        subtitle = RESIZE_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ResizeContent(state, ResizeActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        fun photo(n: Int, name: String, size: Long, dims: Dims) =
            ResizePhoto(LocalFile(Uri.parse("content://sample/$n"), name, size, "image/jpeg"), dims)

        val photos = listOf(
            photo(1, "PXL_20261012_101530.jpg", 4_200_000L, Dims(4032, 3024)),
            photo(2, "PXL_20261012_101602.jpg", 3_900_000L, Dims(3024, 4032)),
            photo(3, "Screenshot_20261012.png", 1_600_000L, Dims(1080, 2400)),
        )
        val ready = ResizeUiState(ResizePhase.Ready, photos, ResizeSettings(side = SidePreset.POST, crop = CropRatio.PORTRAIT))
        val weight = ResizeUiState(ResizePhase.Ready, photos, ResizeSettings(mode = ResizeMode.WEIGHT, weight = WeightGoal.KB500))
        val working = ready.copy(phase = ResizePhase.Working(1, 3))
        val done = ready.copy(
            phase = ResizePhase.Done(
                photos.mapIndexed { i, p ->
                    val dims = Dims(864, 1080)
                    val name = p.file.name.substringBeforeLast('.') + "_864x1080.jpg"
                    val after = listOf(212_000L, 198_000L, 143_000L)[i]
                    val saved = SavedFile(Uri.parse("content://media/${i + 1}"), "image/jpeg", name, after, "Pictures/Gulp")
                    ResizeResult(name, saved, p.file.size ?: 0L, after, p.dims!!, dims)
                },
            ),
        )
    }
}
