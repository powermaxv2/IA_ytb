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
import com.powermaxv2.ytdownloader.core.cleaner.CleanerFacts
import com.powermaxv2.ytdownloader.core.cleaner.CleanerPhase
import com.powermaxv2.ytdownloader.core.cleaner.CleanerReport
import com.powermaxv2.ytdownloader.core.cleaner.CleanerTab
import com.powermaxv2.ytdownloader.core.cleaner.CleanerUiState
import com.powermaxv2.ytdownloader.core.cleaner.MediaFile
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CLEANER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CLEANER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CLEANER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CleanerActions
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CleanerContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Nettoyeur WhatsApp in the three skins, with a sample WhatsApp folder (no App, controller or real files). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CleanerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_cleaner_idle", CleanerUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_cleaner_idle", CleanerUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_cleaner_idle", CleanerUiState())

    @Test fun classicScan() = shot(Skin.CLASSIC, "classic_cleaner_scan", scanning, settle = 900)
    @Test fun paperScan() = shot(Skin.PAPER, "paper_cleaner_scan", scanning, settle = 900)
    @Test fun terminalScan() = shot(Skin.TERMINAL, "terminal_cleaner_scan", scanning, settle = 900)

    @Test fun classicDuplicates() = shot(Skin.CLASSIC, "classic_cleaner_duplicates", ready)
    @Test fun paperDuplicates() = shot(Skin.PAPER, "paper_cleaner_duplicates", ready)
    @Test fun terminalDuplicates() = shot(Skin.TERMINAL, "terminal_cleaner_duplicates", ready)

    @Test fun classicBig() = shot(Skin.CLASSIC, "classic_cleaner_big", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2")))
    @Test fun paperBig() = shot(Skin.PAPER, "paper_cleaner_big", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2")))
    @Test fun terminalBig() = shot(Skin.TERMINAL, "terminal_cleaner_big", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2")))

    @Test fun classicTypes() = shot(Skin.CLASSIC, "classic_cleaner_types", ready.copy(tab = CleanerTab.TYPES))
    @Test fun paperTypes() = shot(Skin.PAPER, "paper_cleaner_types", ready.copy(tab = CleanerTab.TYPES))
    @Test fun terminalTypes() = shot(Skin.TERMINAL, "terminal_cleaner_types", ready.copy(tab = CleanerTab.TYPES))

    @Test fun classicConfirm() = shot(Skin.CLASSIC, "classic_cleaner_confirm", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2") + defaults, confirming = true))
    @Test fun paperConfirm() = shot(Skin.PAPER, "paper_cleaner_confirm", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2") + defaults, confirming = true))
    @Test fun terminalConfirm() = shot(Skin.TERMINAL, "terminal_cleaner_confirm", ready.copy(tab = CleanerTab.BIG, selection = setOf("v1", "v2") + defaults, confirming = true))

    @Test fun classicDone() = shot(Skin.CLASSIC, "classic_cleaner_done", done)
    @Test fun paperDone() = shot(Skin.PAPER, "paper_cleaner_done", done)
    @Test fun terminalDone() = shot(Skin.TERMINAL, "terminal_cleaner_done", done)

    private fun shot(skin: Skin, name: String, state: CleanerUiState, settle: Long = 2_500) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = CLEANER_TITLE,
                        command = CLEANER_COMMAND,
                        subtitle = CLEANER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        CleanerContent(state, CleanerActions(now = NOW))
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
        const val NOW = 1_791_700_000_000L // 2026-10-11

        fun f(id: String, name: String, size: Long, folder: String, daysAgo: Int) = MediaFile(id, name, size, NOW - daysAgo * DAY, folder)

        val files = listOf(
            f("m1", "IMG-20260912-WA0031.jpg", 2_400_000, "WhatsApp Images", 29),
            f("m2", "IMG-20260913-WA0007.jpg", 2_400_000, "WhatsApp Images", 28),
            f("m3", "IMG-20260915-WA0112.jpg", 2_400_000, "WhatsApp Images/Sent", 26),
            f("g1", "VID-20260801-WA0004.mp4", 18_600_000, "WhatsApp Video", 71),
            f("g2", "VID-20260803-WA0019.mp4", 18_600_000, "WhatsApp Video", 69),
            f("v1", "VID-20260514-WA0002.mp4", 412_000_000, "WhatsApp Video", 150),
            f("v2", "VID-20260702-WA0011.mp4", 268_000_000, "WhatsApp Video", 101),
            f("v3", "VID-20260921-WA0040.mp4", 96_500_000, "WhatsApp Video", 20),
            f("d1", "Contrat de bail.pdf", 14_200_000, "WhatsApp Documents", 210),
            f("a1", "PTT-20261002-WA0003.opus", 640_000, "WhatsApp Voice Notes/202640", 9),
            f("s1", "STK-20261001-WA0001.webp", 38_000, "WhatsApp Stickers", 10),
        ) + (1..60).map { f("p$it", "IMG-2026${it}-WA00$it.jpg", 1_900_000L + it * 37_000, "WhatsApp Images", it * 4) } +
            (1..40).map { f("n$it", "PTT-2026-WA0$it.opus", 300_000L + it * 9_000, "WhatsApp Voice Notes/2026${it % 9}", it * 3) } +
            (1..25).map { f("t$it", "STK-$it.webp", 42_000L, "WhatsApp Stickers", it) } +
            (1..12).map { f("st$it", "status$it.mp4", 4_800_000L, ".Statuses", 1) }

        val prints = mapOf("m1" to "A", "m2" to "A", "m3" to "A", "g1" to "B", "g2" to "B")
        val report = CleanerReport(files, CleanerFacts.duplicates(files, prints))
        val defaults = CleanerFacts.defaultSelection(report.duplicates)
        val ready = CleanerUiState(CleanerPhase.Ready, folder = "WhatsApp › Media", report = report, selection = defaults)
        val scanning = CleanerUiState(CleanerPhase.Scanning(1_284, 3_420_000_000, hashed = 38, toHash = 64), folder = "WhatsApp › Media")
        val done = CleanerUiState(
            CleanerPhase.Done(files.filter { it.id in setOf("v1", "v2", "m2", "m3", "g2") }, failed = 0, before = report.totalBytes),
            folder = "WhatsApp › Media",
        )
    }
}
