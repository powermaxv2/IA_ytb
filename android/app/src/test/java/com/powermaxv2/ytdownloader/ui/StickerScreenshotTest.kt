package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.sticker.SavedPack
import com.powermaxv2.ytdownloader.core.sticker.StickerArt
import com.powermaxv2.ytdownloader.core.sticker.StickerClip
import com.powermaxv2.ytdownloader.core.sticker.StickerMotion
import com.powermaxv2.ytdownloader.core.sticker.StickerOutline
import com.powermaxv2.ytdownloader.core.sticker.StickerPack
import com.powermaxv2.ytdownloader.core.sticker.StickerPhase
import com.powermaxv2.ytdownloader.core.sticker.StickerPhoto
import com.powermaxv2.ytdownloader.core.sticker.StickerShape
import com.powermaxv2.ytdownloader.core.sticker.StickerStyle
import com.powermaxv2.ytdownloader.core.sticker.StickerUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.sticker.STICKER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.sticker.STICKER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.sticker.STICKER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.sticker.StickerActions
import com.powermaxv2.ytdownloader.ui.tools.sticker.StickerContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Stickers tool in the three skins. The stickers are really drawn by [StickerArt] (magic wand, outline, text)
 * from pictures painted here: no App, controller or real files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StickerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_sticker_idle") { idle() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_sticker_idle") { idle() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_sticker_idle") { idle() }

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_sticker_edit") { ready() }
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_sticker_edit") { ready() }
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_sticker_edit") { ready() }

    @Test fun classicVideo() = shot(Skin.CLASSIC, "classic_sticker_video") { video() }
    @Test fun paperVideo() = shot(Skin.PAPER, "paper_sticker_video") { video() }
    @Test fun terminalVideo() = shot(Skin.TERMINAL, "terminal_sticker_video") { video() }

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_sticker_progress", settle = 900) { working() }
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_sticker_progress", settle = 900) { working() }
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_sticker_progress", settle = 900) { working() }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_sticker_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_sticker_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_sticker_done") { done() }

    private fun shot(skin: Skin, name: String, settle: Long = 2_500, state: () -> StickerUiState) {
        val s = state()
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = STICKER_TITLE,
                        command = STICKER_COMMAND,
                        subtitle = STICKER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        StickerContent(s, StickerActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // MARK: - Sample pictures

    /** A smiling face on a plain wall: the magic wand takes the wall away. */
    private fun face(wall: Int, skin: Int, cheeks: Int = 0xFFFF7A6B.toInt()): Bitmap {
        val b = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(wall)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = skin
        c.drawCircle(320f, 250f, 175f, p)
        p.color = 0xFF2B1B10.toInt()
        c.drawCircle(262f, 215f, 22f, p)
        c.drawCircle(378f, 215f, 22f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 16f
        p.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(230f, 220f, 410f, 340f), 20f, 140f, false, p)
        p.style = Paint.Style.FILL
        p.color = cheeks
        c.drawCircle(225f, 285f, 24f, p)
        c.drawCircle(415f, 285f, 24f, p)
        return b
    }

    private fun photo(n: Int, bitmap: Bitmap) =
        StickerPhoto(LocalFile(Uri.parse("content://sample/$n"), "IMG_2026101$n.jpg", 2_400_000L, "image/jpeg"), StickerArt.square(bitmap))

    private val style = StickerStyle(shape = StickerShape.ROUNDED, outline = StickerOutline.THICK, text = "TROP FORT", cutout = true)

    private fun photos() = listOf(
        photo(1, face(0xFFBFE3FF.toInt(), 0xFFFFC93C.toInt())),
        photo(2, face(0xFFD7F5D0.toInt(), 0xFFFFA94D.toInt())),
    )

    private fun drawn(state: StickerUiState): StickerUiState {
        val pictures = state.plan.map { (source, variant) ->
            val square = state.motion?.square ?: state.photos[source].square
            StickerArt.render(square, state.style, variant, state.seeds[source].orEmpty())
        }
        return state.copy(preview = pictures[state.selected], thumbs = pictures.map { StickerArt.scaled(it, 160) })
    }

    private fun packs(): List<SavedPack> {
        val tray = StickerArt.render(photos()[0].square, style.copy(text = ""))
        return listOf(
            SavedPack(StickerPack("gulp_a1", "Les potes du lycée", false, List(6) { "0$it.webp" }, "😂", 2L, 312_000), StickerArt.scaled(tray, 96)),
            SavedPack(StickerPack("gulp_a2", "Chat qui danse", true, List(3) { "0$it.webp" }, "🔥", 1L, 1_180_000), StickerArt.scaled(StickerArt.render(photos()[1].square, StickerStyle(shape = StickerShape.ROUND)), 96)),
        )
    }

    private fun idle() = StickerUiState(packs = packs())

    private fun ready() = drawn(StickerUiState(StickerPhase.Ready, photos = photos(), style = style, name = "Les potes du lycée", emoji = "😂"))

    private fun video(): StickerUiState {
        val frame = face(0xFF1E1E2A.toInt(), 0xFF8EE3EF.toInt(), cheeks = 0xFFFF5C8A.toInt())
        val motion = StickerMotion(LocalFile(Uri.parse("content://sample/v"), "VID_20261011_chat.mp4", 18_000_000L, "video/mp4"), 14_500, gif = false, square = StickerArt.square(frame))
        return drawn(
            StickerUiState(
                StickerPhase.Ready,
                motion = motion,
                style = StickerStyle(shape = StickerShape.ROUND, outline = StickerOutline.THIN, text = "MDR", textOnTop = true),
                clip = StickerClip(4_200, 3_000, boomerang = true),
                emoji = "🔥",
            ),
        )
    }

    private fun working() = ready().copy(phase = StickerPhase.Working(0.62f, "Sticker 2/3…"))

    private fun done(): StickerUiState {
        val s = ready()
        val pack = StickerPack("gulp_a3", "Les potes du lycée", false, List(3) { "0${it + 1}.webp" }, "😂", 3L, 141_000)
        return s.copy(phase = StickerPhase.Done(pack, s.thumbs, listOf(48_000, 47_000, 46_000)))
    }
}
