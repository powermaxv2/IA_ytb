package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.powermaxv2.ytdownloader.core.scan.Pt
import com.powermaxv2.ytdownloader.core.scan.Quad
import com.powermaxv2.ytdownloader.core.scan.ScanFilter
import com.powermaxv2.ytdownloader.core.scan.ScanPage
import com.powermaxv2.ytdownloader.core.scan.ScanPhase
import com.powermaxv2.ytdownloader.core.scan.ScanUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.scan.SCANNER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.scan.SCANNER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.scan.SCANNER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.scan.ScannerActions
import com.powermaxv2.ytdownloader.ui.tools.scan.ScannerContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Scanner in the three skins, with drawn sample pages (no App, controller or camera). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScannerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_scan_idle") { ScanUiState() }
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_scan_idle") { ScanUiState() }
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_scan_idle") { ScanUiState() }

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_scan_edit") { editing() }
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_scan_edit") { editing() }
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_scan_edit") { editing() }

    @Test fun classicPages() = shot(Skin.CLASSIC, "classic_scan_pages") { pages() }
    @Test fun paperPages() = shot(Skin.PAPER, "paper_scan_pages") { pages() }
    @Test fun terminalPages() = shot(Skin.TERMINAL, "terminal_scan_pages") { pages() }

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_scan_done") { done() }
    @Test fun paperDone() = shot(Skin.PAPER, "paper_scan_done") { done() }
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_scan_done") { done() }

    private fun shot(skin: Skin, name: String, state: () -> ScanUiState) {
        val sample = state()
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = SCANNER_TITLE,
                        command = SCANNER_COMMAND,
                        subtitle = SCANNER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ScannerContent(sample, ScannerActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val quad = Quad(Pt(0.16f, 0.13f), Pt(0.84f, 0.09f), Pt(0.89f, 0.9f), Pt(0.11f, 0.93f))

        fun page(id: Long, filter: ScanFilter = ScanFilter.SHARP) = ScanPage(id, File("photo-$id.jpg"), 0.75f, quad, quad, 0, filter)

        /** A sheet with a title and lines of "text" on a wooden table, as a phone would photograph it. */
        fun photo(): Bitmap {
            val w = 900
            val h = 1200
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            val wood = Paint()
            for (x in 0 until w step 30) {
                wood.color = if ((x / 30) % 2 == 0) Color.rgb(92, 62, 41) else Color.rgb(77, 51, 34)
                c.drawRect(x.toFloat(), 0f, x + 30f, h.toFloat(), wood)
            }
            val sheet = Path().apply {
                moveTo(quad.topLeft.x * w, quad.topLeft.y * h)
                lineTo(quad.topRight.x * w, quad.topRight.y * h)
                lineTo(quad.bottomRight.x * w, quad.bottomRight.y * h)
                lineTo(quad.bottomLeft.x * w, quad.bottomLeft.y * h)
                close()
            }
            c.drawPath(sheet, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 232, 222) })
            c.save()
            c.rotate(-2.5f, w / 2f, h / 2f)
            text(c, 230f, 230f, 440f, 15, Color.rgb(120, 116, 108))
            c.restore()
            return bitmap
        }

        /** A finished page (white, straight) for the small pictures; [dark] = ink colour. */
        fun sheet(lines: Int, dark: Int = Color.rgb(90, 90, 90), paper: Int = Color.WHITE): Bitmap {
            val bitmap = Bitmap.createBitmap(340, 480, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(paper)
            c.drawRect(34f, 40f, 200f, 62f, Paint().apply { color = Color.rgb(30, 30, 30) })
            text(c, 34f, 96f, 272f, lines, dark)
            return bitmap
        }

        fun text(c: Canvas, x: Float, y: Float, width: Float, lines: Int, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            for (i in 0 until lines) {
                val len = width * (0.62f + ((i * 37) % 30) / 80f)
                c.drawRoundRect(x, y + i * 30f, x + len.coerceAtMost(width), y + i * 30f + 11f, 4f, 4f, p)
            }
        }

        fun editing(): ScanUiState {
            val pages = listOf(page(1), page(2), page(3))
            return ScanUiState(
                phase = ScanPhase.Editing(2),
                pages = pages,
                editorPhoto = photo(),
                filterPreviews = mapOf(
                    ScanFilter.ORIGINAL to sheet(10, Color.rgb(130, 126, 118), Color.rgb(226, 220, 206)),
                    ScanFilter.SHARP to sheet(10, Color.rgb(60, 60, 60)),
                    ScanFilter.GRAY to sheet(10, Color.rgb(110, 110, 110), Color.rgb(238, 238, 238)),
                    ScanFilter.BW to sheet(10, Color.BLACK),
                ),
            )
        }

        fun pages(): ScanUiState {
            val pages = listOf(page(1), page(2, ScanFilter.BW), page(3))
            return ScanUiState(
                phase = ScanPhase.Pages,
                pages = pages,
                thumbs = mapOf(1L to sheet(11), 2L to sheet(8, Color.BLACK), 3L to sheet(12)),
                defaultName = "Scan 10-10-2026 14h32",
            )
        }

        fun done(): ScanUiState = pages().copy(
            phase = ScanPhase.Done(
                SavedFile(Uri.parse("content://media/9"), "application/pdf", "Scan 10-10-2026 14h32.pdf", 1_150_000L, "Download/Gulp"),
                3,
            ),
        )
    }
}
