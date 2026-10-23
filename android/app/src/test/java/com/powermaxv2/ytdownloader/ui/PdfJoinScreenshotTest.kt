package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.LocalFile
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinLine
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPage
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinPhase
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinSource
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinTool
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinUiState
import com.powermaxv2.ytdownloader.core.pdfjoin.SplitMode
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PDF_JOIN_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PDF_JOIN_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PDF_JOIN_TITLE
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PdfJoinActions
import com.powermaxv2.ytdownloader.ui.tools.pdfjoin.PdfJoinContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Assembleur PDF in the three skins, with sample pages drawn here (no App, controller or PDF). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PdfJoinScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_pdfjoin_idle", JoinUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_pdfjoin_idle", JoinUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_pdfjoin_idle", JoinUiState())

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_pdfjoin_edit", edit)
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_pdfjoin_edit", edit)
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_pdfjoin_edit", edit)

    @Test fun classicSplit() = shot(Skin.CLASSIC, "classic_pdfjoin_split", split)
    @Test fun paperSplit() = shot(Skin.PAPER, "paper_pdfjoin_split", split)
    @Test fun terminalSplit() = shot(Skin.TERMINAL, "terminal_pdfjoin_split", split)

    @Test fun classicRemove() = shot(Skin.CLASSIC, "classic_pdfjoin_remove", remove)
    @Test fun paperRemove() = shot(Skin.PAPER, "paper_pdfjoin_remove", remove)
    @Test fun terminalRemove() = shot(Skin.TERMINAL, "terminal_pdfjoin_remove", remove)

    @Test fun classicProgress() = shot(Skin.CLASSIC, "classic_pdfjoin_progress", progress)
    @Test fun paperProgress() = shot(Skin.PAPER, "paper_pdfjoin_progress", progress)
    @Test fun terminalProgress() = shot(Skin.TERMINAL, "terminal_pdfjoin_progress", progress)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_pdfjoin_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_pdfjoin_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_pdfjoin_done", done)

    private fun shot(skin: Skin, name: String, state: JoinUiState) {
        val thumbs = thumbs()
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PDF_JOIN_TITLE,
                        command = PDF_JOIN_COMMAND,
                        subtitle = PDF_JOIN_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        PdfJoinContent(state, thumbs, PdfJoinActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        fun file(n: Int, name: String, mime: String) = LocalFile(Uri.parse("content://sample/$n"), name, 1_000_000L, mime)

        val sources = listOf(
            JoinSource(file(1, "devis.pdf", "application/pdf"), pdf = true, work = File("devis.pdf"), pages = 3),
            JoinSource(file(2, "facture-mars.pdf", "application/pdf"), pdf = true, work = File("facture-mars.pdf"), pages = 2),
            JoinSource(file(3, "IMG_2041.jpg", "image/jpeg"), pdf = false, work = File("IMG_2041.jpg"), pages = 1),
        )
        val pages = listOf(
            JoinPage(1, 0, 0, 595f, 842f),
            JoinPage(2, 0, 1, 595f, 842f),
            JoinPage(3, 0, 2, 595f, 842f),
            JoinPage(4, 1, 0, 595f, 842f),
            JoinPage(5, 1, 1, 595f, 842f),
            JoinPage(6, 2, 0, 595f, 842f),
        )
        val edit = JoinUiState(JoinPhase.Ready, sources = sources, pages = pages, selected = 3)
        val split = JoinUiState(
            JoinPhase.Ready,
            sources = sources.take(1),
            pages = (1L..6L).map { JoinPage(it, 0, it.toInt() - 1, 595f, 842f) },
            tool = JoinTool.SPLIT,
            split = SplitMode.CUTS,
            cuts = setOf(3L, 5L),
        )
        val remove = JoinUiState(
            JoinPhase.Ready,
            sources = sources,
            pages = pages.mapIndexed { i, p -> if (i == 5) p.copy(rotation = 90) else p },
            tool = JoinTool.REMOVE,
            marked = setOf(2L, 5L),
        )
        val progress = edit.copy(phase = JoinPhase.Working(4, 6), selected = null)
        val done = edit.copy(
            phase = JoinPhase.Done(
                tool = JoinTool.JOIN,
                saved = listOf(SavedFile(Uri.parse("content://media/9"), "application/pdf", "devis + 2 autres.pdf", 1_420_000L, "Download/Gulp")),
                pages = 6,
                lines = listOf(
                    JoinLine("devis.pdf", true, "pages 1 à 3"),
                    JoinLine("facture-mars.pdf", true, "pages 4 et 5"),
                    JoinLine("IMG_2041.jpg", false, "page 6, mise au format A4"),
                ),
                cover = listOf(1L, 4L, 6L),
            ),
            selected = null,
        )

        /** Pages like the mockups: a red title bar (devis), a blue one (facture), and a sky-to-sand photo. */
        fun thumbs(): Map<Long, ImageBitmap> = (1L..6L).associateWith { id ->
            val w = 300
            val h = 424
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (id == 6L) {
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF8EC5DB.toInt(), 0xFFA9D6E5.toInt(), 0xFFE9D9A6.toInt(), 0xFFD2A574.toInt()), floatArrayOf(0f, 0.45f, 0.6f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            } else {
                canvas.drawColor(0xFFFFFFFF.toInt())
                paint.color = if (id <= 3L) 0xFFEE3B2B.toInt() else 0xFF2A6FDB.toInt()
                canvas.drawRoundRect(RectF(28f, 34f, 150f, 52f), 8f, 8f, paint)
                paint.color = 0xFFD9D2CC.toInt()
                listOf(0.7f, 0.92f, 0.6f, 0.86f, 0.5f).forEachIndexed { i, f ->
                    val y = 82f + i * 30f
                    canvas.drawRoundRect(RectF(28f, y, 28f + (w - 56) * f, y + 10f), 5f, 5f, paint)
                }
                if (id == 2L || id == 5L) {
                    paint.color = 0xFFEDE8E3.toInt()
                    canvas.drawRoundRect(RectF(28f, 250f, w - 28f, 360f), 10f, 10f, paint)
                }
            }
            bitmap.asImageBitmap()
        }
    }
}
