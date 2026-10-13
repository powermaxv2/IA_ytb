package com.powermaxv2.ytdownloader.ui

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
import com.powermaxv2.ytdownloader.core.photodup.Album
import com.powermaxv2.ytdownloader.core.photodup.DupKind
import com.powermaxv2.ytdownloader.core.photodup.DupPhase
import com.powermaxv2.ytdownloader.core.photodup.DupReport
import com.powermaxv2.ytdownloader.core.photodup.DupUiState
import com.powermaxv2.ytdownloader.core.photodup.GalleryAccess
import com.powermaxv2.ytdownloader.core.photodup.Photo
import com.powermaxv2.ytdownloader.core.photodup.PhotoDupFacts
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.photodup.PHOTO_DUP_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.photodup.PHOTO_DUP_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.photodup.PHOTO_DUP_TITLE
import com.powermaxv2.ytdownloader.ui.tools.photodup.PhotoDupActions
import com.powermaxv2.ytdownloader.ui.tools.photodup.PhotoDupContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Doublons photos in the three skins, with a sample gallery (no App, controller or real photos). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PhotoDupScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicAccess() = shot(Skin.CLASSIC, "classic_photodup_access", DupUiState())
    @Test fun paperAccess() = shot(Skin.PAPER, "paper_photodup_access", DupUiState())
    @Test fun terminalAccess() = shot(Skin.TERMINAL, "terminal_photodup_access", DupUiState())

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_photodup_idle", idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_photodup_idle", idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_photodup_idle", idle)

    @Test fun classicScan() = shot(Skin.CLASSIC, "classic_photodup_scan", scanning, settle = 900)
    @Test fun paperScan() = shot(Skin.PAPER, "paper_photodup_scan", scanning, settle = 900)
    @Test fun terminalScan() = shot(Skin.TERMINAL, "terminal_photodup_scan", scanning, settle = 900)

    @Test fun classicExact() = shot(Skin.CLASSIC, "classic_photodup_exact", ready)
    @Test fun paperExact() = shot(Skin.PAPER, "paper_photodup_exact", ready)
    @Test fun terminalExact() = shot(Skin.TERMINAL, "terminal_photodup_exact", ready)

    @Test fun classicSimilar() = shot(Skin.CLASSIC, "classic_photodup_similar", ready.copy(kind = DupKind.SIMILAR))
    @Test fun paperSimilar() = shot(Skin.PAPER, "paper_photodup_similar", ready.copy(kind = DupKind.SIMILAR))
    @Test fun terminalSimilar() = shot(Skin.TERMINAL, "terminal_photodup_similar", ready.copy(kind = DupKind.SIMILAR))

    @Test fun classicConfirm() = shot(Skin.CLASSIC, "classic_photodup_confirm", ready.copy(confirming = true, toTrash = true))
    @Test fun paperConfirm() = shot(Skin.PAPER, "paper_photodup_confirm", ready.copy(confirming = true, toTrash = true))
    @Test fun terminalConfirm() = shot(Skin.TERMINAL, "terminal_photodup_confirm", ready.copy(confirming = true, toTrash = true))

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_photodup_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_photodup_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_photodup_done", done)

    private fun shot(skin: Skin, name: String, state: DupUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PHOTO_DUP_TITLE,
                        command = PHOTO_DUP_COMMAND,
                        subtitle = PHOTO_DUP_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        PhotoDupContent(state, PhotoDupActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(settle)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_791_700_000_000L

        fun p(id: Long, size: Long, w: Int, h: Int, album: String, daysAgo: Int) = Photo(id, "IMG_$id.jpg", size, NOW - daysAgo * DAY, w, h, album)

        val photos = listOf(
            p(1, 3_400_000, 4032, 3024, "Camera", 40), p(2, 3_400_000, 4032, 3024, "Download", 12), p(3, 3_400_000, 4032, 3024, "WhatsApp Images", 3),
            p(4, 2_100_000, 3024, 4032, "Camera", 60), p(5, 2_100_000, 3024, 4032, "Camera", 60),
            p(6, 4_800_000, 4000, 3000, "Camera", 20), p(7, 310_000, 1600, 1200, "WhatsApp Images", 19),
            p(8, 2_900_000, 4000, 3000, "Camera", 8), p(9, 2_750_000, 4000, 3000, "Camera", 8), p(10, 2_800_000, 4000, 3000, "Camera", 8),
        ) + (11L..400L).map { p(it, 1_500_000 + it * 3_000, 4000, 3000, "Camera", (it % 300).toInt()) }

        val contents = mapOf(1L to "A", 2L to "A", 3L to "A", 4L to "B", 5L to "B")
        val prints = mapOf(6L to 0x0F0F_3C3C_0F0F_3C3CL, 7L to 0x0F0F_3C3C_0F0F_3C3DL, 8L to 0x7E7E_1818_7E7E_1818L, 9L to 0x7E7E_1818_7E7E_1819L, 10L to 0x7E7E_1818_7E7E_181BL)
        val report = DupReport(photos, PhotoDupFacts.groups(photos, contents, prints))
        val albums = listOf(Album("Camera", 1_842), Album("WhatsApp Images", 731), Album("Screenshots", 312), Album("Download", 54))
        val idle = DupUiState(access = GalleryAccess.FULL, albums = albums)
        val ready = idle.copy(phase = DupPhase.Ready, report = report, selection = PhotoDupFacts.defaultSelection(report.groups))
        val scanning = idle.copy(phase = DupPhase.Scanning(1, 1_204, 2_939, 2_939))
        val done = idle.copy(phase = DupPhase.Done(photos.filter { it.id in setOf(2L, 3L, 5L, 7L) }, failed = 0, toTrash = false, before = report.totalBytes))
    }
}
