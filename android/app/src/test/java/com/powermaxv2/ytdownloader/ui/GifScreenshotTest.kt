package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.powermaxv2.ytdownloader.core.gif.GifEffect
import com.powermaxv2.ytdownloader.core.gif.GifPhase
import com.powermaxv2.ytdownloader.core.gif.GifPhoto
import com.powermaxv2.ytdownloader.core.gif.GifRange
import com.powermaxv2.ytdownloader.core.gif.GifSettings
import com.powermaxv2.ytdownloader.core.gif.GifShape
import com.powermaxv2.ytdownloader.core.gif.GifSource
import com.powermaxv2.ytdownloader.core.gif.GifSpeed
import com.powermaxv2.ytdownloader.core.gif.GifUiState
import com.powermaxv2.ytdownloader.core.gif.GifVideo
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.gif.GIF_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.gif.GIF_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.gif.GIF_TITLE
import com.powermaxv2.ytdownloader.ui.tools.gif.GifActions
import com.powermaxv2.ytdownloader.ui.tools.gif.GifContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Créateur de GIF in the three skins, with drawn sample frames (no App, controller or FFmpeg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class GifScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_gif_idle", GifUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_gif_idle", GifUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_gif_idle", GifUiState())

    @Test fun classicVideo() = shot(Skin.CLASSIC, "classic_gif_video", video())
    @Test fun paperVideo() = shot(Skin.PAPER, "paper_gif_video", video())
    @Test fun terminalVideo() = shot(Skin.TERMINAL, "terminal_gif_video", video())

    @Test fun classicPhotos() = shot(Skin.CLASSIC, "classic_gif_photos", photos())
    @Test fun paperPhotos() = shot(Skin.PAPER, "paper_gif_photos", photos())
    @Test fun terminalPhotos() = shot(Skin.TERMINAL, "terminal_gif_photos", photos())

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_gif_progress", video().copy(phase = GifPhase.Working(0.62f, "Création du GIF…")))
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_gif_progress", video().copy(phase = GifPhase.Working(0.62f, "Création du GIF…")))
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_gif_progress", video().copy(phase = GifPhase.Working(0.62f, "Création du GIF…")))

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_gif_done", done())
    @Test fun paperDone() = shot(Skin.PAPER, "paper_gif_done", done())
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_gif_done", done())

    private fun shot(skin: Skin, name: String, state: GifUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = GIF_TITLE,
                        command = GIF_COMMAND,
                        subtitle = GIF_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        GifContent(state, GifActions(), still = true)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A sunset-ish frame with a ball at [t] (0..1) along its jump. */
    private fun frame(w: Int, h: Int, t: Float, hue: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sky = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(255, 120 + hue, 80), Color.rgb(60, 30, 90 + hue), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sky)
        val ground = Paint().apply { color = Color.rgb(40, 24, 50) }
        canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), ground)
        val y = h * 0.8f - h * 0.55f * 4 * t * (1 - t) - h * 0.08f
        canvas.drawCircle(w * (0.2f + 0.6f * t), y, h * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bitmap
    }

    private fun video(): GifUiState {
        val strip = List(8) { frame(170, 96, it / 7f, 20) }
        val preview = List(10) { frame(568, 320, it / 9f, 40) }
        return GifUiState(
            phase = GifPhase.Ready,
            source = GifSource.VIDEO,
            video = GifVideo(LocalFile(Uri.parse("content://sample/1"), "chat-qui-saute.mp4", 18_400_000L, "video/mp4"), 48_000L, 1920, 1080),
            range = GifRange(12_000, 15_200),
            settings = GifSettings(effect = GifEffect.BOOMERANG, speed = GifSpeed.HALF, shape = GifShape.SQUARE, caption = "Quand le café fait effet"),
            strip = strip,
            preview = preview,
        )
    }

    private fun photos(): GifUiState = GifUiState(
        phase = GifPhase.Ready,
        source = GifSource.PHOTOS,
        photos = List(5) { i -> GifPhoto(i + 1L, Uri.parse("content://sample/p$i"), "IMG_00$i.jpg", frame(240, 320, i / 4f, i * 25)) },
        settings = GifSettings(caption = "Joyeux anniversaire !"),
    )

    private fun done(): GifUiState = video().copy(
        phase = GifPhase.Done(
            SavedFile(Uri.parse("content://media/1"), "image/gif", "chat-qui-saute (GIF).gif", 2_350_000L, "Pictures/Gulp"),
            480,
            480,
            154,
            12_800L,
        ),
    )
}
