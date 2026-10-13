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
import com.powermaxv2.ytdownloader.core.slideshow.SlideFit
import com.powermaxv2.ytdownloader.core.slideshow.SlideFormat
import com.powermaxv2.ytdownloader.core.slideshow.SlideMusic
import com.powermaxv2.ytdownloader.core.slideshow.SlidePhase
import com.powermaxv2.ytdownloader.core.slideshow.SlidePhoto
import com.powermaxv2.ytdownloader.core.slideshow.SlideTransition
import com.powermaxv2.ytdownloader.core.slideshow.SlideUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SLIDESHOW_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SLIDESHOW_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SLIDESHOW_TITLE
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SlideActions
import com.powermaxv2.ytdownloader.ui.tools.slideshow.SlideshowContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Diaporama in the three skins, with sample data (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SlideshowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_slideshow_idle") { SlideUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_slideshow_idle") { SlideUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_slideshow_idle") { SlideUiState() }

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_slideshow_edit") { edit() }
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_slideshow_edit") { edit() }
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_slideshow_edit") { edit() }

    @Test fun classicSquareFit() = shot(Skin.CLASSIC, "classic_slideshow_square") {
        edit().copy(format = SlideFormat.SQUARE, fit = SlideFit.FIT, transition = SlideTransition.SLIDE, music = null)
    }

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_slideshow_progress") { progress() }
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_slideshow_progress") { progress() }
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_slideshow_progress") { progress() }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_slideshow_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_slideshow_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_slideshow_done") { done() }

    private fun shot(skin: Skin, name: String, state: () -> SlideUiState) {
        val sample = state()
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = SLIDESHOW_TITLE,
                        command = SLIDESHOW_COMMAND,
                        subtitle = SLIDESHOW_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        SlideshowContent(sample, SlideActions(), still = true)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        /** Made-up holiday photos: a sky gradient and a sun, from morning to sunset. */
        fun photo(i: Int, landscape: Boolean): Bitmap {
            val w = if (landscape) 320 else 240
            val h = if (landscape) 240 else 320
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val t = i / 5f
            val top = android.graphics.Color.HSVToColor(floatArrayOf(200f - 170f * t, 0.6f, 0.95f))
            val bottom = android.graphics.Color.HSVToColor(floatArrayOf(40f - 30f * t, 0.7f, 0.9f - 0.3f * t))
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
            Canvas(bitmap).apply {
                drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                drawCircle(w * (0.2f + 0.6f * t), h * (0.25f + 0.4f * t), w * 0.12f, Paint().apply { color = android.graphics.Color.argb(220, 255, 236, 180) })
                drawRect(0f, h * 0.78f, w.toFloat(), h.toFloat(), Paint().apply { color = android.graphics.Color.argb(200, 30, 60, 90) })
            }
            return bitmap
        }

        val names = listOf("plage-matin.jpg", "IMG_2041.HEIC", "pique-nique.jpg", "IMG_2057.jpg", "coucher-de-soleil.jpg", "nous-quatre.png")

        fun edit() = SlideUiState(
            phase = SlidePhase.Editing,
            photos = names.mapIndexed { i, n -> SlidePhoto(i.toLong(), Uri.parse("content://sample/$i"), n, if (i == 4) 5_000L else 3_000L, photo(i, i % 3 == 1)) },
            music = SlideMusic(Uri.parse("content://sample/music"), "vacances-ete.mp3", 184_000L),
        )

        fun progress() = edit().copy(phase = SlidePhase.Working(0.62f, "Montage de la vidéo…"))

        fun done() = edit().copy(
            phase = SlidePhase.Done(
                SavedFile(Uri.parse("content://media/1"), "video/mp4", "Diaporama 13-10-2026 14h05.mp4", 9_800_000L, "Movies/Gulp"),
                edit().totalMs,
            ),
        )
    }
}
