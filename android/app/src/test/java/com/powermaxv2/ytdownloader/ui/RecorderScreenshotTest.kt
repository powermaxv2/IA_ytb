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
import com.powermaxv2.ytdownloader.core.recorder.MemoInfo
import com.powermaxv2.ytdownloader.core.recorder.RecPhase
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.recorder.REC_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.recorder.REC_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.recorder.REC_TITLE
import com.powermaxv2.ytdownloader.ui.tools.recorder.RecActions
import com.powermaxv2.ytdownloader.ui.tools.recorder.RecContent
import com.powermaxv2.ytdownloader.ui.tools.recorder.RecUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sin

/** Enregistreur vocal in the three skins: no memo yet, recording, a memo playing, renaming. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class RecorderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicEmpty() = shot(Skin.CLASSIC, "classic_rec_empty", empty)
    @Test fun paperEmpty() = shot(Skin.PAPER, "paper_rec_empty", empty)
    @Test fun terminalEmpty() = shot(Skin.TERMINAL, "terminal_rec_empty", empty)

    @Test fun classicRecording() = shot(Skin.CLASSIC, "classic_rec_recording", recording)
    @Test fun paperRecording() = shot(Skin.PAPER, "paper_rec_recording", recording)
    @Test fun terminalRecording() = shot(Skin.TERMINAL, "terminal_rec_recording", recording)

    @Test fun classicPlaying() = shot(Skin.CLASSIC, "classic_rec_playing", playing)
    @Test fun paperPlaying() = shot(Skin.PAPER, "paper_rec_playing", playing)
    @Test fun terminalPlaying() = shot(Skin.TERMINAL, "terminal_rec_playing", playing)

    @Test fun classicRename() = shot(Skin.CLASSIC, "classic_rec_rename", rename)

    private fun shot(skin: Skin, name: String, state: RecUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = REC_TITLE,
                        command = REC_COMMAND,
                        subtitle = REC_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        RecContent(state, RecActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val now: LocalDateTime = LocalDateTime.of(2026, 10, 14, 20, 30)
        fun at(d: LocalDateTime) = d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun wave(seed: Int, n: Int = 120) = List(n) { i -> (0.15f + 0.8f * abs(sin(i * 0.37f + seed) * sin(i * 0.11f + seed * 2))).coerceIn(0f, 1f) }
        val memos = listOf(
            MemoInfo("Idée cadeau Léa.m4a", 42_000, 512_000, at(now.minusMinutes(20)), wave(1)),
            MemoInfo("Mémo 13 oct. 08h05.m4a", 7_400, 96_000, at(now.minusDays(1).withHour(8).withMinute(5)), wave(2)),
            MemoInfo("Courses du week-end.m4a", 95_000, 1_160_000, at(now.minusDays(4)), wave(3)),
        )
        val empty = RecUiState(now = now)
        val recording = RecUiState(phase = RecPhase.RECORDING, elapsedMs = 12_400, live = wave(4, 64), memos = memos.drop(1), now = now)
        val playing = RecUiState(memos = memos, open = memos[0].fileName, playing = true, positionMs = 15_000, speed = 1.5f, now = now)
        val rename = RecUiState(memos = memos, open = memos[1].fileName, renaming = memos[1].fileName, draft = "Rappel dentiste", now = now)
    }
}
