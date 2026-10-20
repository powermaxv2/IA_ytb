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
import com.powermaxv2.ytdownloader.core.collage.CollageBackground
import com.powermaxv2.ytdownloader.core.collage.CollageFormat
import com.powermaxv2.ytdownloader.core.collage.CollageLayout
import com.powermaxv2.ytdownloader.core.collage.CollageOptions
import com.powermaxv2.ytdownloader.core.collage.CollagePhase
import com.powermaxv2.ytdownloader.core.collage.CollagePhoto
import com.powermaxv2.ytdownloader.core.collage.CollageRender
import com.powermaxv2.ytdownloader.core.collage.CollageUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.collage.COLLAGE_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.collage.COLLAGE_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.collage.COLLAGE_TITLE
import com.powermaxv2.ytdownloader.ui.tools.collage.CollageActions
import com.powermaxv2.ytdownloader.ui.tools.collage.CollageContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Collage photos" in the three skins, with photos painted here (sunsets, sea, a field): no controller, no files. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CollageScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_collage_idle") { CollageUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_collage_idle") { CollageUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_collage_idle") { CollageUiState() }

    @Test fun classicGrid() = shot(Skin.CLASSIC, "classic_collage_grid") { ready(5, CollageOptions()) }
    @Test fun paperHero() = shot(Skin.PAPER, "paper_collage_hero") {
        ready(4, CollageOptions(layout = CollageLayout.HERO, format = CollageFormat.PORTRAIT, background = CollageBackground.CREAM, rounded = true))
    }
    @Test fun terminalStrip() = shot(Skin.TERMINAL, "terminal_collage_strip") {
        ready(3, CollageOptions(layout = CollageLayout.STRIP, format = CollageFormat.STORY, background = CollageBackground.BLACK))
    }

    @Test fun classicScatterSelected() = shot(Skin.CLASSIC, "classic_collage_scatter") {
        ready(6, CollageOptions(layout = CollageLayout.SCATTER, background = CollageBackground.COLOR)).copy(selected = 2)
    }
    @Test fun paperScatter() = shot(Skin.PAPER, "paper_collage_scatter") { ready(6, CollageOptions(layout = CollageLayout.SCATTER, background = CollageBackground.CREAM)) }
    @Test fun terminalGrid() = shot(Skin.TERMINAL, "terminal_collage_grid") { ready(9, CollageOptions(background = CollageBackground.BLACK)) }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_collage_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_collage_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_collage_done") { done() }

    private fun shot(skin: Skin, name: String, settle: Long = 2_500, state: () -> CollageUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = COLLAGE_TITLE,
                        command = COLLAGE_COMMAND,
                        subtitle = COLLAGE_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        CollageContent(s, CollageActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A landscape-ish photo: a sky gradient, a sun and a hill, in the colours of [i]. */
    private fun photo(i: Int): Bitmap {
        val skies = listOf(
            0xFFFF8A65 to 0xFFFFD54F, 0xFF4FC3F7 to 0xFF81C784, 0xFFBA68C8 to 0xFFF06292,
            0xFF26A69A to 0xFF9CCC65, 0xFF5C6BC0 to 0xFFFFB74D, 0xFFEF5350 to 0xFFFFCC80,
        )
        val (top, bottom) = skies[i % skies.size]
        val w = if (i % 3 == 1) 600 else 900
        val h = if (i % 3 == 1) 900 else 600
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(w * 0.3f, h * 0.32f, minOf(w, h) * 0.12f, p)
        p.color = 0x55000000
        c.drawOval(-w * 0.2f, h * 0.68f, w * 1.2f, h * 1.4f, p)
        return b
    }

    private fun ready(count: Int, options: CollageOptions, phase: CollagePhase = CollagePhase.Ready): CollageUiState {
        val photos = List(count) { i ->
            val bitmap = photo(i)
            CollagePhoto(i + 1L, "photo-$i.jpg", bitmap, CollageRender.mainColor(bitmap))
        }
        return CollageUiState(phase, photos, options)
    }

    private fun done(): CollageUiState {
        val saved = SavedFile(Uri.parse("content://media/external/images/media/42"), "image/jpeg", "collage-4-photos.jpg", 612_000, "Pictures/Gulp")
        return ready(4, CollageOptions(layout = CollageLayout.HERO, rounded = true), CollagePhase.Done(saved))
    }
}
