package com.powermaxv2.ytdownloader.ui

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
import com.powermaxv2.ytdownloader.core.qr.QrCodec
import com.powermaxv2.ytdownloader.core.qr.QrDraft
import com.powermaxv2.ytdownloader.core.qr.QrHistoryEntry
import com.powermaxv2.ytdownloader.core.qr.QrKind
import com.powermaxv2.ytdownloader.core.qr.QrPayload
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.qr.QR_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.qr.QR_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.qr.QR_TITLE
import com.powermaxv2.ytdownloader.ui.tools.qr.QrActions
import com.powermaxv2.ytdownloader.ui.tools.qr.QrContent
import com.powermaxv2.ytdownloader.ui.tools.qr.QrUiState
import com.powermaxv2.ytdownloader.ui.tools.qr.SampleScene
import com.powermaxv2.ytdownloader.ui.tools.qr.ScanPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The QR code tool in the three skins, with sample data (no camera: a still scene stands in for it). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class QrScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_qr_idle", idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_qr_idle", idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_qr_idle", idle)

    @Test fun classicScan() = shot(Skin.CLASSIC, "classic_qr_scan", scan)
    @Test fun paperScan() = shot(Skin.PAPER, "paper_qr_scan", scan)
    @Test fun terminalScan() = shot(Skin.TERMINAL, "terminal_qr_scan", scan)

    @Test fun classicResult() = shot(Skin.CLASSIC, "classic_qr_result", result)
    @Test fun paperResult() = shot(Skin.PAPER, "paper_qr_result", result)
    @Test fun terminalResult() = shot(Skin.TERMINAL, "terminal_qr_result", result)

    @Test fun classicCreate() = shot(Skin.CLASSIC, "classic_qr_create", create)
    @Test fun paperCreate() = shot(Skin.PAPER, "paper_qr_create", create)
    @Test fun terminalCreate() = shot(Skin.TERMINAL, "terminal_qr_create", create)

    @Test fun classicLink() = shot(Skin.CLASSIC, "classic_qr_link", link)
    @Test fun paperLink() = shot(Skin.PAPER, "paper_qr_link", link)
    @Test fun terminalLink() = shot(Skin.TERMINAL, "terminal_qr_link", link)

    private fun shot(skin: Skin, name: String, state: QrUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = QR_TITLE,
                        command = QR_COMMAND,
                        subtitle = QR_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        QrContent(state, QrActions()) { SampleScene(QrCodec.encode(WIFI), "Wi-Fi invités") }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val WIFI = "WIFI:T:WPA;S:Maison-5G;P:soleil-2024;;"
        const val NOW = 1_760_000_000_000L
        val history = listOf(
            QrHistoryEntry("https://gulp.app/menu", "QR_CODE", NOW - 26 * 3_600_000L),
            QrHistoryEntry("3017620422003", "EAN_13", NOW - 4 * 24 * 3_600_000L),
        )
        val idle = QrUiState(history = history, now = NOW)
        val scan = QrUiState(phase = ScanPhase.Camera, history = history, now = NOW)
        val result = QrUiState(
            phase = ScanPhase.Result(QrPayload.parse(WIFI, "QR_CODE")),
            history = listOf(QrHistoryEntry(WIFI, "QR_CODE", NOW)) + history,
            now = NOW,
        )
        val create = QrUiState(create = true, draft = QrDraft(kind = QrKind.WIFI, ssid = "Maison-5G", password = "soleil-2024"))
        val link = QrUiState(
            create = true,
            draft = QrDraft(kind = QrKind.LINK, link = "gulp.app/menu"),
            saved = SavedFile(Uri.parse("content://media/1"), "image/png", "QR gulp.app.png", 21_000L, "Pictures/Gulp"),
            savedFor = "https://gulp.app/menu",
        )
    }
}
