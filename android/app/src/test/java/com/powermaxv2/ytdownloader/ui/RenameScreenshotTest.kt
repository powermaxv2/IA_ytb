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
import com.powermaxv2.ytdownloader.core.rename.NameBlock
import com.powermaxv2.ytdownloader.core.rename.RenameFile
import com.powermaxv2.ytdownloader.core.rename.RenameOutcome
import com.powermaxv2.ytdownloader.core.rename.RenamePhase
import com.powermaxv2.ytdownloader.core.rename.RenameRule
import com.powermaxv2.ytdownloader.core.rename.RenameSource
import com.powermaxv2.ytdownloader.core.rename.RenameUiState
import com.powermaxv2.ytdownloader.core.rename.UndoStep
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.rename.RENAME_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.rename.RENAME_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.rename.RENAME_TITLE
import com.powermaxv2.ytdownloader.ui.tools.rename.RenameActions
import com.powermaxv2.ytdownloader.ui.tools.rename.RenameContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Renommeur in the three skins, with sample files (no App, controller or real files). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class RenameScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_rename_idle", RenameUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_rename_idle", RenameUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_rename_idle", RenameUiState())

    @Test fun classicRule() = shot(Skin.CLASSIC, "classic_rename_rule", ready)
    @Test fun paperRule() = shot(Skin.PAPER, "paper_rename_rule", ready)
    @Test fun terminalRule() = shot(Skin.TERMINAL, "terminal_rename_rule", ready)

    @Test fun classicClash() = shot(Skin.CLASSIC, "classic_rename_clash", clash)
    @Test fun paperClash() = shot(Skin.PAPER, "paper_rename_clash", clash)
    @Test fun terminalClash() = shot(Skin.TERMINAL, "terminal_rename_clash", clash)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_rename_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_rename_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_rename_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_rename_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_rename_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_rename_done", done)

    private fun shot(skin: Skin, name: String, state: RenameUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = RENAME_TITLE,
                        command = RENAME_COMMAND,
                        subtitle = RENAME_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        RenameContent(state, RenameActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        // 2026-10-13 18:42:07 in Paris.
        const val OCT13 = 1_791_909_727_000L

        fun file(n: Int, name: String, minutes: Int, renamable: Boolean = true): RenameFile {
            val uri = Uri.parse("content://sample/$n")
            return RenameFile(uri, RenameSource(uri.toString(), name, takenAt = OCT13 + minutes * 60_000L, renamable = renamable), "image/jpeg")
        }

        val files = listOf(
            file(1, "IMG_20261013_184207.jpg", 0),
            file(2, "IMG_20261013_190112.jpg", 19),
            file(3, "IMG_20261013_201530.jpg", 93),
            file(4, "PXL_20261014_093004.jpg", 900, renamable = false),
            file(5, "VID_20261014_101500.mp4", 940),
        )
        val ready = RenameUiState(
            RenamePhase.Ready,
            files,
            rule = RenameRule(listOf(NameBlock.Text("Rome_"), NameBlock.Date(), NameBlock.Text("_"), NameBlock.Counter())),
        )
        val clash = ready.copy(rule = RenameRule(listOf(NameBlock.Text("Rome_"), NameBlock.Date())))
        val working = ready.copy(phase = RenamePhase.Working(3, 5))
        val done = ready.copy(
            phase = RenamePhase.Done(
                RenameOutcome(
                    renamed = (1..4).map { UndoStep("content://sample/$it", Uri.parse("content://sample/$it"), "Rome_00$it.jpg", "old") },
                    copies = listOf(SavedFile(Uri.parse("content://media/9"), "image/jpeg", "Rome_005.jpg", 3_300_000L, "Pictures/Gulp")),
                ),
            ),
        )
    }
}
