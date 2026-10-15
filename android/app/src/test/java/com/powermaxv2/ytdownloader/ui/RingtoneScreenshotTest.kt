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
import com.powermaxv2.ytdownloader.core.ringtone.RingPhase
import com.powermaxv2.ytdownloader.core.ringtone.RingRange
import com.powermaxv2.ytdownloader.core.ringtone.RingSource
import com.powermaxv2.ytdownloader.core.ringtone.RingUiState
import com.powermaxv2.ytdownloader.core.ringtone.RingtoneUse
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.ringtone.RING_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.ringtone.RING_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.ringtone.RING_TITLE
import com.powermaxv2.ytdownloader.ui.tools.ringtone.RingtoneActions
import com.powermaxv2.ytdownloader.ui.tools.ringtone.RingtoneContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sin

/** Sonneries in the three skins, with sample data (no controller, player or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class RingtoneScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_ringtone_idle", RingUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_ringtone_idle", RingUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_ringtone_idle", RingUiState())

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_ringtone_edit", edit)
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_ringtone_edit", edit)
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_ringtone_edit", edit)

    @Test fun classicPlaying() = shot(Skin.CLASSIC, "classic_ringtone_playing", playing)
    @Test fun paperPlaying() = shot(Skin.PAPER, "paper_ringtone_playing", playing)
    @Test fun terminalPlaying() = shot(Skin.TERMINAL, "terminal_ringtone_playing", playing)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_ringtone_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_ringtone_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_ringtone_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_ringtone_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_ringtone_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_ringtone_done", done)

    @Test fun classicApplied() = shot(Skin.CLASSIC, "classic_ringtone_applied", applied)

    private fun shot(skin: Skin, name: String, state: RingUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = RING_TITLE,
                        command = RING_COMMAND,
                        subtitle = RING_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        RingtoneContent(state, RingtoneActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val wave = FloatArray(160) { i ->
            val chorus = if (i in 70..92) 0.35f else 0f
            (0.2f + chorus + 0.45f * abs(sin(i * 0.37f) * sin(i * 0.11f + 1f))).coerceAtMost(1f)
        }
        val source = RingSource(LocalFile(Uri.parse("content://sample/1"), "Soleil d'été.mp3", 8_400_000L, "audio/mpeg"), 214_000L, false)
        val edit = RingUiState(RingPhase.Ready, source, RingRange(93_600, 123_600), RingtoneUse.RINGTONE, wave = wave, bestMs = 93_600)
        val playing = edit.copy(use = RingtoneUse.ALARM, range = RingRange(93_600, 133_600), louder = true, playheadMs = 104_000)
        val progress = edit.copy(phase = RingPhase.Working(0.62f, "Création de la sonnerie…"))
        val saved = SavedFile(Uri.parse("content://media/1"), "audio/mpeg", "Soleil d'été (sonnerie).mp3", 482_000L, "Ringtones/Gulp")
        val done = edit.copy(phase = RingPhase.Done(saved, RingtoneUse.RINGTONE, 30_000L))
        val applied = edit.copy(phase = RingPhase.Done(saved, RingtoneUse.RINGTONE, 30_000L, applied = true))
    }
}
