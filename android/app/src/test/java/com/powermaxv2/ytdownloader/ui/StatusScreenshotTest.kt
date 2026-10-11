package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import com.powermaxv2.ytdownloader.core.status.StatusLength
import com.powermaxv2.ytdownloader.core.status.StatusPhase
import com.powermaxv2.ytdownloader.core.status.StatusPlan
import com.powermaxv2.ytdownloader.core.status.StatusUiState
import com.powermaxv2.ytdownloader.core.trim.TrimSource
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

/** The Découpe statut in the three skins, with sample data (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StatusScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_status_idle") { StatusUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_status_idle") { StatusUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_status_idle") { StatusUiState() }

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_status_edit") { edit() }
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_status_edit") { edit() }
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_status_edit") { edit() }

    @Test fun classicHalfMinute() = shot(Skin.CLASSIC, "classic_status_30s") { edit().copy(length = StatusLength.HALF_MINUTE, equal = false) }
    @Test fun paperHalfMinute() = shot(Skin.PAPER, "paper_status_30s") { edit().copy(length = StatusLength.HALF_MINUTE, equal = false) }
    @Test fun terminalHalfMinute() = shot(Skin.TERMINAL, "terminal_status_30s") { edit().copy(length = StatusLength.HALF_MINUTE, equal = false) }

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_status_progress") { progress() }
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_status_progress") { progress() }
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_status_progress") { progress() }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_status_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_status_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_status_done") { done() }

    @Test fun classicTooShort() = shot(Skin.CLASSIC, "classic_status_short") { short() }

    private fun shot(skin: Skin, name: String, state: () -> StatusUiState) {
        val sample = state()
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
                        StatusContent(sample, StatusActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val video = TrimSource(LocalFile(Uri.parse("content://sample/1"), "anniversaire-lea.mp4", 186_000_000L, "video/mp4"), 247_000L, true, 1080, 1920)

        /** Twelve made-up frames, from sunset orange to night blue (a party filmed into the evening). */
        fun frames(): List<Bitmap> = (0 until 12).map { i ->
            val bitmap = Bitmap.createBitmap(135, 240, Bitmap.Config.ARGB_8888)
            val t = i / 11f
            val top = android.graphics.Color.HSVToColor(floatArrayOf(30f + 200f * t, 0.75f, 0.95f - 0.4f * t))
            val bottom = android.graphics.Color.HSVToColor(floatArrayOf(330f - 60f * t, 0.8f, 0.55f - 0.2f * t))
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 240f, top, bottom, Shader.TileMode.CLAMP) }
            Canvas(bitmap).apply {
                drawRect(0f, 0f, 135f, 240f, paint)
                drawCircle(40f + 50f * t, 150f, 34f, Paint().apply { color = android.graphics.Color.argb(150, 255, 240, 200) })
            }
            bitmap
        }

        fun edit() = StatusUiState(StatusPhase.Ready, video, StatusLength.MINUTE, equal = true, frames = frames())

        fun progress() = edit().copy(phase = StatusPhase.Working(2, 5, 0.5f, "Statut 3 sur 5…"))

        fun done(): StatusUiState {
            val saved = (0 until 5).map { i ->
                val name = StatusPlan.outputName(video.file.name, i, 5, "mp4")
                SavedFile(Uri.parse("content://media/$i"), "video/mp4", name, 37_000_000L, "Movies/Gulp")
            }
            return edit().copy(phase = StatusPhase.Done(saved, copied = true))
        }

        fun short() = StatusUiState(
            StatusPhase.Ready,
            TrimSource(LocalFile(Uri.parse("content://sample/2"), "chat-qui-saute.mp4", 9_400_000L, "video/mp4"), 48_000L, true, 720, 1280),
            StatusLength.MINUTE,
        )
    }
}
