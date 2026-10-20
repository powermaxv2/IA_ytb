package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import com.powermaxv2.ytdownloader.core.colors.ColorPickerController
import com.powermaxv2.ytdownloader.core.colors.PickPhase
import com.powermaxv2.ytdownloader.core.colors.PickPhoto
import com.powermaxv2.ytdownloader.core.colors.PickUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.colors.COLOR_PICKER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.colors.COLOR_PICKER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.colors.COLOR_PICKER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.colors.ColorPickerContent
import com.powermaxv2.ytdownloader.ui.tools.colors.PickActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Pipette in the three skins. The palette is really computed by [ColorPickerController.palette] from a
 * sunset painted here: no controller, no real files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ColorPickerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_pipette_idle") { PickUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_pipette_idle") { PickUiState(kept = listOf(0xE4572E, 0x1B2A4A, 0xFFB347)) }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_pipette_idle") { PickUiState() }

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_pipette_ready") { ready(0.7f, 0.32f) }
    @Test fun paperReady() = shot(Skin.PAPER, "paper_pipette_ready") { ready(0.3f, 0.75f, kept = listOf(0xE4572E, 0x7EC8F2)) }
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_pipette_ready") { ready(0.5f, 0.5f) }

    private fun shot(skin: Skin, name: String, settle: Long = 2_500, state: () -> PickUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = COLOR_PICKER_TITLE,
                        command = COLOR_PICKER_COMMAND,
                        subtitle = COLOR_PICKER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ColorPickerContent(s, PickActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A sunset: sky gradient, a sun, the sea and a dark hill. */
    private fun sunset(): Bitmap {
        val b = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 380f, 0xFF3B2A6B.toInt(), 0xFFF28C4B.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 900f, 380f, p)
        p.shader = null
        p.color = 0xFFFFD166.toInt()
        c.drawCircle(630f, 330f, 90f, p)
        p.color = 0xFF1B4F72.toInt()
        c.drawRect(0f, 380f, 900f, 600f, p)
        p.color = 0xFF1A1423.toInt()
        c.drawOval(-200f, 330f, 420f, 700f, p)
        return b
    }

    private fun ready(x: Float, y: Float, kept: List<Int> = emptyList()): PickUiState {
        val bitmap = sunset()
        val palette = ColorPickerController.palette(bitmap)
        return PickUiState(
            phase = PickPhase.Ready,
            photo = PickPhoto("coucher-de-soleil.jpg", bitmap),
            palette = palette,
            x = x,
            y = y,
            color = ColorPickerController.sample(bitmap, x, y),
            kept = kept,
        )
    }
}
