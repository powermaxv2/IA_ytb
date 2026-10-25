package com.powermaxv2.ytdownloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.billing.PlusCatalog
import com.powermaxv2.ytdownloader.ui.hub.HomeContent
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import com.powermaxv2.ytdownloader.ui.plus.DonateBody
import com.powermaxv2.ytdownloader.ui.plus.PaywallBody
import com.powermaxv2.ytdownloader.ui.plus.PlusBanner
import com.powermaxv2.ytdownloader.ui.plus.PlusSheetPreview
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of Gulp Plus (paywall, donation, locked home). Record with `./gradlew recordRoborazziDebug`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PlusScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicHomePlus() = home(Skin.CLASSIC, "classic", plus = false)
    @Test fun paperHomePlus() = home(Skin.PAPER, "paper", plus = false)
    @Test fun terminalHomePlus() = home(Skin.TERMINAL, "terminal", plus = false)
    @Test fun classicHomeUnlocked() = home(Skin.CLASSIC, "classic", plus = true)

    @Test fun classicPaywall() = sheet(Skin.CLASSIC, "classic_paywall") { PaywallBody("Coffre-fort", PayPhase.OFFER, true, {}, {}, {}) }
    @Test fun paperPaywall() = sheet(Skin.PAPER, "paper_paywall") { PaywallBody("Coffre-fort", PayPhase.OFFER, true, {}, {}, {}) }
    @Test fun terminalPaywall() = sheet(Skin.TERMINAL, "terminal_paywall") { PaywallBody("Coffre-fort", PayPhase.OFFER, true, {}, {}, {}) }
    @Test fun classicPaywallWorking() = sheet(Skin.CLASSIC, "classic_paywall_working") { PaywallBody("Coffre-fort", PayPhase.WORKING, true, {}, {}, {}) }
    @Test fun classicPaywallDone() = sheet(Skin.CLASSIC, "classic_paywall_done") { PaywallBody("Coffre-fort", PayPhase.DONE, true, {}, {}, {}) }
    @Test fun classicDonate() = sheet(Skin.CLASSIC, "classic_donate") { DonateBody(PayPhase.OFFER, true, {}, {}) }
    @Test fun paperDonate() = sheet(Skin.PAPER, "paper_donate") { DonateBody(PayPhase.OFFER, true, {}, {}) }
    @Test fun terminalDonate() = sheet(Skin.TERMINAL, "terminal_donate") { DonateBody(PayPhase.OFFER, true, {}, {}) }
    @Test fun classicDonateDone() = sheet(Skin.CLASSIC, "classic_donate_done") { DonateBody(PayPhase.DONE, true, {}, {}) }

    private fun home(skin: Skin, name: String, plus: Boolean) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        lastUsedId = ToolRegistry.VAULT,
                        onOpen = {},
                        onStar = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                        lockedIds = if (plus) emptySet() else PlusCatalog.premiumIds,
                        banner = { PlusBanner(plus, PlusCatalog.premiumIds.size, {}, {}) },
                    )
                }
            }
        }
        capture(if (plus) "${name}_home_unlocked" else "${name}_home_plus")
    }

    private fun sheet(skin: Skin, fileName: String, body: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.62f)))
                    Box(Modifier.align(Alignment.BottomCenter)) { PlusSheetPreview(body) }
                }
            }
        }
        capture(fileName)
    }

    private fun capture(fileName: String) {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$fileName.png")
    }
}
