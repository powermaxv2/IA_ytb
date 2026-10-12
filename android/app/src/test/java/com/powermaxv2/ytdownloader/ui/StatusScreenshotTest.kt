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
import com.powermaxv2.ytdownloader.core.status.StatusItem
import com.powermaxv2.ytdownloader.core.status.StatusKept
import com.powermaxv2.ytdownloader.core.status.StatusKind
import com.powermaxv2.ytdownloader.core.status.StatusPhase
import com.powermaxv2.ytdownloader.core.status.StatusUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.status.STATUS_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.status.STATUS_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.status.STATUS_TITLE
import com.powermaxv2.ytdownloader.ui.tools.status.StatusActions
import com.powermaxv2.ytdownloader.ui.tools.status.StatusContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Statuts tool in the three skins, with sample statuses (no App, controller or WhatsApp folder). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StatusScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicSetup() = shot(Skin.CLASSIC, "classic_statuts_setup", StatusUiState())
    @Test fun paperSetup() = shot(Skin.PAPER, "paper_statuts_setup", StatusUiState())
    @Test fun terminalSetup() = shot(Skin.TERMINAL, "terminal_statuts_setup", StatusUiState())

    @Test fun classicGrid() = shot(Skin.CLASSIC, "classic_statuts_grid", grid)
    @Test fun paperGrid() = shot(Skin.PAPER, "paper_statuts_grid", grid)
    @Test fun terminalGrid() = shot(Skin.TERMINAL, "terminal_statuts_grid", grid)

    @Test fun classicEmpty() = shot(Skin.CLASSIC, "classic_statuts_empty", empty)
    @Test fun paperEmpty() = shot(Skin.PAPER, "paper_statuts_empty", empty)
    @Test fun terminalEmpty() = shot(Skin.TERMINAL, "terminal_statuts_empty", empty)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_statuts_progress", working, settle = 900)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_statuts_progress", working, settle = 900)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_statuts_progress", working, settle = 900)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_statuts_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_statuts_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_statuts_done", done)

    private fun shot(skin: Skin, name: String, state: StatusUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = STATUS_TITLE,
                        command = STATUS_COMMAND,
                        subtitle = STATUS_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        StatusContent(state, StatusActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val NOW = 1_791_729_120_000L

        fun item(n: Int, hoursAgo: Double, video: Long? = null) = StatusItem(
            uri = Uri.parse("content://sample/status/$n"),
            name = "status$n.${if (video != null) "mp4" else "jpg"}",
            kind = if (video != null) StatusKind.VIDEO else StatusKind.PHOTO,
            size = 900_000L,
            modifiedAt = NOW - (hoursAgo * HOUR).toLong(),
            durationMs = video,
        )

        val items = listOf(
            item(1, 1.5, 28_000), item(2, 2.5), item(3, 6.2, 12_000),
            item(4, 9.4), item(5, 14.8), item(6, 17.1, 30_000),
            item(7, 20.6), item(8, 21.5, 7_000), item(9, 22.9),
        )
        val grid = StatusUiState(
            phase = StatusPhase.Ready,
            items = items,
            saved = setOf("status2.jpg", "status6.mp4"),
            saving = setOf("status4.jpg"),
            now = NOW,
        )
        val empty = StatusUiState(phase = StatusPhase.Ready, now = NOW)
        val working = grid.copy(phase = StatusPhase.Working(2, 6), saving = emptySet())
        val done = grid.copy(
            phase = StatusPhase.Done(
                listOf(items[0], items[2], items[3], items[4], items[6], items[7], items[8]).mapIndexed { i, it ->
                    val mime = if (it.kind == StatusKind.VIDEO) "video/mp4" else "image/jpeg"
                    StatusKept(it, SavedFile(Uri.parse("content://media/${i + 1}"), mime, it.name, it.size, "Pictures/Gulp"))
                },
            ),
        )
    }
}
