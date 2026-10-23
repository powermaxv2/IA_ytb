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
import com.powermaxv2.ytdownloader.core.profilefit.Backdrop
import com.powermaxv2.ytdownloader.core.profilefit.FitOptions
import com.powermaxv2.ytdownloader.core.profilefit.FitPhase
import com.powermaxv2.ytdownloader.core.profilefit.FitPhoto
import com.powermaxv2.ytdownloader.core.profilefit.FitUiState
import com.powermaxv2.ytdownloader.core.profilefit.ProfileFit
import com.powermaxv2.ytdownloader.core.profilefit.ProfileRender
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.profilefit.FitActions
import com.powermaxv2.ytdownloader.ui.tools.profilefit.PROFILE_FIT_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.profilefit.PROFILE_FIT_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.profilefit.PROFILE_FIT_TITLE
import com.powermaxv2.ytdownloader.ui.tools.profilefit.ProfileFitContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Photo de profil entière" in the three skins. The preview is really drawn by [ProfileRender] from a group
 * photo painted here: no controller, no real files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ProfileFitScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_profilefit_idle") { FitUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_profilefit_idle") { FitUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_profilefit_idle") { FitUiState() }

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_profilefit_ready") { ready(FitOptions()) }
    @Test fun paperReady() = shot(Skin.PAPER, "paper_profilefit_ready") { ready(FitOptions(backdrop = Backdrop.COLOR)) }
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_profilefit_ready") { ready(FitOptions(backdrop = Backdrop.BLACK)) }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_profilefit_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_profilefit_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_profilefit_done") { done() }

    private fun shot(skin: Skin, name: String, settle: Long = 2_500, state: () -> FitUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PROFILE_FIT_TITLE,
                        command = PROFILE_FIT_COMMAND,
                        subtitle = PROFILE_FIT_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ProfileFitContent(s, FitActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A landscape group photo: sky, grass and five friends from edge to edge (WhatsApp would drop two). */
    private fun group(): Bitmap {
        val b = Bitmap.createBitmap(960, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 600f, 0xFF7EC8F2.toInt(), 0xFFF8D49A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 960f, 600f, p)
        p.shader = null
        p.color = 0xFF6FAE3E.toInt()
        c.drawRect(0f, 470f, 960f, 600f, p)
        val shirts = intArrayOf(0xFFFFB74D.toInt(), 0xFF4FC3F7.toInt(), 0xFFE57373.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt())
        shirts.forEachIndexed { i, shirt ->
            val x = 96f + i * 192f
            val y = if (i % 2 == 0) 250f else 276f
            p.color = shirt
            c.drawCircle(x, y + 170f, 110f, p)
            p.color = 0xFFFFDFC2.toInt()
            c.drawCircle(x, y, 64f, p)
        }
        return b
    }

    private fun ready(options: FitOptions, phase: FitPhase = FitPhase.Ready): FitUiState {
        val bitmap = group()
        val photo = FitPhoto("amis.jpg", bitmap, ProfileRender.backdrops(bitmap))
        return FitUiState(phase, photo, options, ProfileRender.render(bitmap, photo.backdrops, ProfileFit.PREVIEW_SIDE, options))
    }

    private fun done(): FitUiState {
        val saved = SavedFile(Uri.parse("content://media/external/images/media/42"), "image/jpeg", "amis-profil.jpg", 182_000, "Pictures/Gulp")
        return ready(FitOptions(), FitPhase.Done(saved))
    }
}
