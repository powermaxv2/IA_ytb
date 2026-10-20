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
import com.powermaxv2.ytdownloader.core.blur.BlurController
import com.powermaxv2.ytdownloader.core.blur.BlurEffect
import com.powermaxv2.ytdownloader.core.blur.BlurOptions
import com.powermaxv2.ytdownloader.core.blur.BlurPhase
import com.powermaxv2.ytdownloader.core.blur.BlurPhoto
import com.powermaxv2.ytdownloader.core.blur.BlurRender
import com.powermaxv2.ytdownloader.core.blur.BlurUiState
import com.powermaxv2.ytdownloader.core.blur.BlurZone
import com.powermaxv2.ytdownloader.core.blur.FaceSearch
import com.powermaxv2.ytdownloader.core.blur.ZoneShape
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.blur.BLUR_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.blur.BLUR_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.blur.BLUR_TITLE
import com.powermaxv2.ytdownloader.ui.tools.blur.BlurActions
import com.powermaxv2.ytdownloader.ui.tools.blur.BlurContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Flouter une photo" in the three skins. The preview is really hidden by [BlurRender] on a street photo painted
 * here: no controller, no real files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class BlurScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_blur_idle") { BlurUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_blur_idle") { BlurUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_blur_idle") { BlurUiState() }

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_blur_ready") { ready(BlurOptions()) }
    @Test fun paperReady() = shot(Skin.PAPER, "paper_blur_ready") { ready(BlurOptions(effect = BlurEffect.PIXELS)) }
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_blur_ready") { ready(BlurOptions(effect = BlurEffect.BAR)) }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_blur_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_blur_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_blur_done") { done() }

    private fun shot(skin: Skin, name: String, settle: Long = 2_500, state: () -> BlurUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = BLUR_TITLE,
                        command = BLUR_COMMAND,
                        subtitle = BLUR_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        BlurContent(s, BlurActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A street: sky, a person on the left, a red car with its white plate on the right. */
    private fun street(): Bitmap {
        val b = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 640f, 0xFF7FC6F0.toInt(), 0xFFFCE3B6.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 960f, 640f, p)
        p.shader = null
        p.color = 0xFF8E9AA6.toInt()
        c.drawRect(0f, 470f, 960f, 640f, p)
        p.color = 0xFF5C8DF0.toInt()
        c.drawCircle(210f, 440f, 120f, p)
        p.color = 0xFFF2C29B.toInt()
        c.drawCircle(210f, 240f, 80f, p)
        p.color = 0xFF1D1F24.toInt()
        c.drawCircle(180f, 230f, 9f, p)
        c.drawCircle(240f, 230f, 9f, p)
        p.color = 0xFFE5484D.toInt()
        c.drawRoundRect(430f, 320f, 900f, 520f, 40f, 40f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(570f, 410f, 760f, 470f, p)
        p.color = 0xFF1D1F24.toInt()
        p.textSize = 40f
        c.drawText("AB-123-CD", 582f, 455f, p)
        return b
    }

    private val zones = listOf(
        BlurZone(1, 0.165f, 0.22f, 0.17f, 0.3f, ZoneShape.OVAL),
        BlurZone(2, 0.58f, 0.62f, 0.22f, 0.13f),
    )

    private fun ready(options: BlurOptions, phase: BlurPhase = BlurPhase.Ready): BlurUiState {
        val bitmap = street()
        return BlurUiState(
            phase = phase,
            photo = BlurPhoto("rue.jpg", bitmap),
            zones = zones,
            selected = 2,
            options = options,
            preview = BlurRender.render(bitmap, zones, options, BlurController.PREVIEW_SIDE),
            faces = FaceSearch.Found(1),
            canUndo = true,
        )
    }

    private fun done(): BlurUiState {
        val saved = SavedFile(Uri.parse("content://media/external/images/media/42"), "image/jpeg", "rue-floute.jpg", 214_000, "Pictures/Gulp")
        return ready(BlurOptions(), BlurPhase.Done(saved)).copy(selected = null)
    }
}
