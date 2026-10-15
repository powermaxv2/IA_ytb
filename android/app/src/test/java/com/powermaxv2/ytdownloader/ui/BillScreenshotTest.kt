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
import com.powermaxv2.ytdownloader.core.bill.BillCurrency
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.bill.BILL_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.bill.BILL_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.bill.BILL_TITLE
import com.powermaxv2.ytdownloader.ui.tools.bill.BillActions
import com.powermaxv2.ytdownloader.ui.tools.bill.BillContent
import com.powermaxv2.ytdownloader.ui.tools.bill.BillUiState
import com.powermaxv2.ytdownloader.ui.tools.bill.DinerInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Partage d'addition in the three skins: empty, an even split with a tip, parts + own order + payer, a problem. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class BillScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_bill_idle", BillUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_bill_idle", BillUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_bill_idle", BillUiState())

    @Test fun classicEven() = shot(Skin.CLASSIC, "classic_bill_even", even)
    @Test fun paperEven() = shot(Skin.PAPER, "paper_bill_even", even)
    @Test fun terminalEven() = shot(Skin.TERMINAL, "terminal_bill_even", even)

    @Test fun classicParts() = shot(Skin.CLASSIC, "classic_bill_parts", parts)
    @Test fun paperParts() = shot(Skin.PAPER, "paper_bill_parts", parts)
    @Test fun terminalParts() = shot(Skin.TERMINAL, "terminal_bill_parts", parts)

    @Test fun classicProblem() = shot(Skin.CLASSIC, "classic_bill_problem", problem)
    @Test fun paperProblem() = shot(Skin.PAPER, "paper_bill_problem", problem)
    @Test fun terminalProblem() = shot(Skin.TERMINAL, "terminal_bill_problem", problem)

    private fun shot(skin: Skin, name: String, state: BillUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = BILL_TITLE,
                        command = BILL_COMMAND,
                        subtitle = BILL_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        BillContent(state, BillActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val even = BillUiState(
            amount = "86,00",
            tipPercent = 10,
            diners = listOf(DinerInput(0, "Léa"), DinerInput(1, "Tom"), DinerInput(2, "Inès")),
        )
        val parts = BillUiState(
            amount = "142,50",
            tipPercent = 15,
            diners = listOf(
                DinerInput(0, "Léa"),
                DinerInput(1, "Tom", halves = 4),
                DinerInput(2, "Inès", own = "28", ownOpen = true),
                DinerInput(3, "Petit Max", halves = 1),
            ),
            roundUp = true,
            payer = 0,
        )
        val problem = BillUiState(
            amount = "20",
            currency = BillCurrency.DOLLAR,
            diners = listOf(DinerInput(0, "Sam", own = "35", ownOpen = true), DinerInput(1, "Alex")),
        )
    }
}
