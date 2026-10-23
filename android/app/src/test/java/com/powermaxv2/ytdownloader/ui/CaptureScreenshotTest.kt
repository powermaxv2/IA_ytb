package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import com.powermaxv2.ytdownloader.core.capture.CaptureFormat
import com.powermaxv2.ytdownloader.core.capture.CaptureMode
import com.powermaxv2.ytdownloader.core.capture.CapturePhase
import com.powermaxv2.ytdownloader.core.capture.CaptureSource
import com.powermaxv2.ytdownloader.core.capture.CaptureUiState
import com.powermaxv2.ytdownloader.core.capture.HuntResult
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.capture.CAPTURE_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.capture.CAPTURE_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.capture.CAPTURE_TITLE
import com.powermaxv2.ytdownloader.ui.tools.capture.CaptureActions
import com.powermaxv2.ytdownloader.ui.tools.capture.CaptureContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Capture d'image in the three skins, with sample data (no controller, no decoder). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CaptureScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_capture_idle") { CaptureUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_capture_idle") { CaptureUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_capture_idle") { CaptureUiState() }

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_capture_edit") { edit() }
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_capture_edit") { edit() }
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_capture_edit") { edit() }

    @Test fun classicHunt() = shot(Skin.CLASSIC, "classic_capture_hunt") { edit().copy(hunting = true) }
    @Test fun paperHunt() = shot(Skin.PAPER, "paper_capture_hunt") { edit().copy(hunting = true) }
    @Test fun terminalHunt() = shot(Skin.TERMINAL, "terminal_capture_hunt") { edit().copy(hunting = true) }

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_capture_progress") { edit().copy(phase = CapturePhase.Working(0.4f, "Image 3 sur 5…")) }
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_capture_progress") { edit().copy(phase = CapturePhase.Working(0.4f, "Image 3 sur 5…")) }
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_capture_progress") { edit().copy(phase = CapturePhase.Working(0.4f, "Image 3 sur 5…")) }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_capture_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_capture_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_capture_done") { done() }

    private fun shot(skin: Skin, name: String, state: () -> CaptureUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = CAPTURE_TITLE,
                        command = CAPTURE_COMMAND,
                        subtitle = CAPTURE_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        CaptureContent(s, CaptureActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A sunset: sky gradient, a sun and a dark skyline, drawn so the screenshots have a real picture. */
    private fun scene(w: Int, h: Int, shift: Float = 0f): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until h) {
            val t = y.toFloat() / h
            val r = (255 * (1 - t * 0.3f)).toInt()
            val g = (120 + 80 * t).toInt().coerceAtMost(255)
            val bl = (90 + 120 * (1 - t)).toInt()
            p.color = android.graphics.Color.rgb(r, g, bl)
            c.drawRect(0f, y.toFloat(), w.toFloat(), y + 1f, p)
        }
        p.color = android.graphics.Color.rgb(255, 230, 150)
        c.drawCircle(w * (0.35f + shift), h * 0.55f, h * 0.16f, p)
        p.color = android.graphics.Color.rgb(30, 18, 40)
        var x = 0f
        var i = 0
        while (x < w) {
            val bw = w / 9f
            val bh = h * (0.18f + (i * 37 % 5) * 0.06f)
            c.drawRect(x, h - bh, x + bw - 4, h.toFloat(), p)
            x += bw
            i++
        }
        return b
    }

    private val source = CaptureSource(LocalFile(Uri.parse("content://sample/1"), "anniversaire-lea.mp4", 148_000_000L, "video/mp4"), 94_000L, 1920, 1080, 30f)

    private fun edit() = CaptureUiState(
        phase = CapturePhase.Ready,
        source = source,
        positionMs = 37_517L,
        strip = List(10) { scene(160, 90, it * 0.04f) },
        preview = scene(960, 540, 0.1f),
        sharpness = 0.78f,
        hunt = HuntResult(37_450L, 37_517L, 42),
        format = CaptureFormat.JPG,
        mode = CaptureMode.SINGLE,
    )

    private fun done() = edit().copy(
        mode = CaptureMode.BURST,
        phase = CapturePhase.Done(
            List(5) { i ->
                SavedFile(Uri.parse("content://media/$i"), "image/jpeg", "anniversaire-lea (0m37s${417 + i * 100}).jpg", 640_000L, "Pictures/Gulp")
            },
            List(5) { scene(320, 180, it * 0.05f) },
            CaptureFormat.JPG,
        ),
    )
}
