package com.powermaxv2.ytdownloader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.signpdf.SavedSignature
import com.powermaxv2.ytdownloader.core.signpdf.SignBox
import com.powermaxv2.ytdownloader.core.signpdf.SignDoc
import com.powermaxv2.ytdownloader.core.signpdf.SignPhase
import com.powermaxv2.ytdownloader.core.signpdf.SignStamp
import com.powermaxv2.ytdownloader.core.signpdf.SignUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SIGN_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SIGN_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SIGN_TITLE
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SignActions
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SignImages
import com.powermaxv2.ytdownloader.ui.tools.signpdf.SignPdfContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Signature PDF in the three skins, with a drawn page and signature (no App, controller or real files). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SignPdfScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_sign_idle", SignUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_sign_idle", SignUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_sign_idle", SignUiState())

    @Test fun classicPad() = shot(Skin.CLASSIC, "classic_sign_pad", SignUiState(drawing = true))
    @Test fun paperPad() = shot(Skin.PAPER, "paper_sign_pad", SignUiState(drawing = true))
    @Test fun terminalPad() = shot(Skin.TERMINAL, "terminal_sign_pad", SignUiState(drawing = true))

    @Test fun classicSaved() = shot(Skin.CLASSIC, "classic_sign_saved", SignUiState(signature = saved))
    @Test fun paperSaved() = shot(Skin.PAPER, "paper_sign_saved", SignUiState(signature = saved))
    @Test fun terminalSaved() = shot(Skin.TERMINAL, "terminal_sign_saved", SignUiState(signature = saved))

    @Test fun classicPlace() = shot(Skin.CLASSIC, "classic_sign_place", ready)
    @Test fun paperPlace() = shot(Skin.PAPER, "paper_sign_place", ready)
    @Test fun terminalPlace() = shot(Skin.TERMINAL, "terminal_sign_place", ready)

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_sign_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_sign_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_sign_done", done)

    private val saved = SavedSignature(File("signature.png"), 600, 200, 1L)
    private val doc = SignDoc("Bail appartement.pdf", File("bail.pdf"), 4)
    private val ready = SignUiState(
        phase = SignPhase.Ready,
        signature = saved,
        doc = doc,
        page = 3,
        pageAspect = 595f / 842f,
        box = SignBox(0.52f, 0.7f, 0.36f),
        stamp = SignStamp(date = true, initials = "MG", initialsEverywhere = true),
    )
    private val done = ready.copy(
        phase = SignPhase.Done(
            SavedFile(Uri.parse("content://media/1"), "application/pdf", "Bail appartement signé.pdf", 182_000, "Download/Gulp"),
            page = 3,
            pages = 4,
            paraphs = 3,
        ),
    )

    private fun page(): Bitmap {
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply { color = Color.rgb(170, 174, 182) }
        canvas.drawRect(60f, 70f, 330f, 92f, Paint().apply { color = Color.rgb(40, 44, 52) })
        var y = 140f
        repeat(22) { i ->
            canvas.drawRect(60f, y, if (i % 5 == 4) 360f else 535f, y + 7f, ink)
            y += 22f
        }
        return bitmap
    }

    private fun signature(): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 24, 29)
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(30f, 140f)
            cubicTo(80f, 20f, 140f, 20f, 150f, 110f)
            cubicTo(170f, 170f, 230f, 40f, 280f, 120f)
            cubicTo(320f, 170f, 360f, 50f, 420f, 100f)
            cubicTo(470f, 140f, 520f, 80f, 570f, 70f)
            moveTo(60f, 175f)
            cubicTo(200f, 155f, 400f, 165f, 560f, 150f)
        }
        Canvas(bitmap).drawPath(path, paint)
        return bitmap
    }

    private fun shot(skin: Skin, name: String, state: SignUiState, settle: Long = 2_500) {
        val images = SignImages(page = page().asImageBitmap(), signature = signature().asImageBitmap())
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = SIGN_TITLE,
                        command = SIGN_COMMAND,
                        subtitle = SIGN_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        SignPdfContent(state, images, SignActions(), today = LocalDate.of(2026, 10, 13))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}
