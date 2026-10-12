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
import com.powermaxv2.ytdownloader.core.direct.Countries
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.direct.DIRECT_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.direct.DIRECT_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.direct.DIRECT_TITLE
import com.powermaxv2.ytdownloader.ui.tools.direct.DirectActions
import com.powermaxv2.ytdownloader.ui.tools.direct.DirectContent
import com.powermaxv2.ytdownloader.ui.tools.direct.DirectUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Écrire sans enregistrer in the three skins: empty, a number with a message, a wrong number, the countries open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class DirectScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_direct_idle", DirectUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_direct_idle", DirectUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_direct_idle", DirectUiState())

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_direct_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_direct_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_direct_ready", ready)

    @Test fun classicBad() = shot(Skin.CLASSIC, "classic_direct_bad", bad)
    @Test fun paperBad() = shot(Skin.PAPER, "paper_direct_bad", bad)
    @Test fun terminalBad() = shot(Skin.TERMINAL, "terminal_direct_bad", bad)

    @Test fun classicCountries() = shot(Skin.CLASSIC, "classic_direct_countries", countries)
    @Test fun paperCountries() = shot(Skin.PAPER, "paper_direct_countries", countries)
    @Test fun terminalCountries() = shot(Skin.TERMINAL, "terminal_direct_countries", countries)

    private fun shot(skin: Skin, name: String, state: DirectUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = DIRECT_TITLE,
                        command = DIRECT_COMMAND,
                        subtitle = DIRECT_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        DirectContent(state, DirectActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val ready = DirectUiState(
            number = "06 12 34 56 78",
            message = "Bonjour, je vous contacte au sujet de votre annonce.",
            recents = listOf("32470123456", "15145550199"),
            business = true,
        )
        val bad = DirectUiState(number = "06 12 34 5", recents = listOf("33612345678"))
        val countries = DirectUiState(number = "0470 12 34 56", country = Countries.byIso("BE")!!, countriesOpen = true)
    }
}
