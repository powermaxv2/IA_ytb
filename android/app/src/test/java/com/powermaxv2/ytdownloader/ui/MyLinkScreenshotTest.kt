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
import com.powermaxv2.ytdownloader.core.mylink.MyImageFormat
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.mylink.MYLINK_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.mylink.MYLINK_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.mylink.MYLINK_TITLE
import com.powermaxv2.ytdownloader.ui.tools.mylink.MyLinkActions
import com.powermaxv2.ytdownloader.ui.tools.mylink.MyLinkContent
import com.powermaxv2.ytdownloader.ui.tools.mylink.MyLinkUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mon lien WhatsApp in the three skins: first time, number kept with a message, editing a wrong number. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class MyLinkScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_mylink_idle", MyLinkUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_mylink_idle", MyLinkUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_mylink_idle", MyLinkUiState())

    @Test fun classicReady() = shot(Skin.CLASSIC, "classic_mylink_ready", ready)
    @Test fun paperReady() = shot(Skin.PAPER, "paper_mylink_ready", ready)
    @Test fun terminalReady() = shot(Skin.TERMINAL, "terminal_mylink_ready", ready)

    @Test fun classicEdit() = shot(Skin.CLASSIC, "classic_mylink_edit", edit)
    @Test fun paperEdit() = shot(Skin.PAPER, "paper_mylink_edit", edit)
    @Test fun terminalEdit() = shot(Skin.TERMINAL, "terminal_mylink_edit", edit)

    private fun shot(skin: Skin, name: String, state: MyLinkUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = MYLINK_TITLE,
                        command = MYLINK_COMMAND,
                        subtitle = MYLINK_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        MyLinkContent(state, MyLinkActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val ready = MyLinkUiState(
            number = "+33612345678",
            message = "Bonjour, je voudrais prendre rendez-vous.",
            keptDigits = "33612345678",
        )
        val edit = MyLinkUiState(
            number = "0470 12 34",
            country = Countries.byIso("BE")!!,
            keptDigits = "33612345678",
            editing = true,
            format = MyImageFormat.QR,
        )
    }
}
