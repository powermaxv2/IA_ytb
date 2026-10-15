package com.powermaxv2.ytdownloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.timer.Stopwatch
import com.powermaxv2.ytdownloader.core.timer.Timers
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.timer.TIMER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.timer.TIMER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.timer.TIMER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.timer.TimerActions
import com.powermaxv2.ytdownloader.ui.tools.timer.TimerContent
import com.powermaxv2.ytdownloader.ui.tools.timer.TimerDraft
import com.powermaxv2.ytdownloader.ui.tools.timer.TimerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Minuteur et chrono in the three skins: a new timer, three timers (one ringing), the stopwatch with laps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TimerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicNew() = shot(Skin.CLASSIC, "classic_timer_new", fresh)
    @Test fun paperNew() = shot(Skin.PAPER, "paper_timer_new", fresh)
    @Test fun terminalNew() = shot(Skin.TERMINAL, "terminal_timer_new", fresh)

    @Test fun classicList() = shot(Skin.CLASSIC, "classic_timer_list", list)
    @Test fun paperList() = shot(Skin.PAPER, "paper_timer_list", list)
    @Test fun terminalList() = shot(Skin.TERMINAL, "terminal_timer_list", list)

    @Test fun classicWatch() = shot(Skin.CLASSIC, "classic_timer_watch", watch)
    @Test fun paperWatch() = shot(Skin.PAPER, "paper_timer_watch", watch)
    @Test fun terminalWatch() = shot(Skin.TERMINAL, "terminal_timer_watch", watch)

    private fun shot(skin: Skin, name: String, state: TimerUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = TIMER_TITLE,
                        command = TIMER_COMMAND,
                        subtitle = TIMER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        TimerContent(state, TimerActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val NOW = 1_792_000_000_000L
        val fresh = TimerUiState(now = NOW, draft = TimerDraft(name = "Pâtes", durationMs = 9 * 60_000L))
        val list = TimerUiState(
            now = NOW,
            timers = Timers.add(emptyList(), "Pâtes", 9 * 60_000L, NOW - 5 * 60_000L)
                .let { Timers.add(it, "Lessive", 45 * 60_000L, NOW - 12 * 60_000L) }
                .let { Timers.pause(it, 2, NOW) }
                .let { Timers.add(it, "Thé", 3 * 60_000L, NOW - 3 * 60_000L) }
                .let { Timers.add(it, "Parking", 2 * 3600_000L, NOW, start = false) },
        )
        val watch = TimerUiState(
            watchTab = true,
            now = NOW,
            watch = Stopwatch(startedAt = NOW - 158_420, laps = listOf(41_230, 79_880, 124_060)),
        )
    }
}
