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
import com.powermaxv2.ytdownloader.core.textcount.CaseKind
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.textcount.TEXT_COUNT_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.textcount.TEXT_COUNT_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.textcount.TEXT_COUNT_TITLE
import com.powermaxv2.ytdownloader.ui.tools.textcount.TextCountActions
import com.powermaxv2.ytdownloader.ui.tools.textcount.TextCountContent
import com.powermaxv2.ytdownloader.ui.tools.textcount.TextCountUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Compteur de texte in the three skins: empty (sample), a typed text, the case just changed, and a text too long. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TextCountScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_textcount_idle", TextCountUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_textcount_idle", TextCountUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_textcount_idle", TextCountUiState())

    @Test fun classicTyped() = shot(Skin.CLASSIC, "classic_textcount_typed", typed)
    @Test fun paperTyped() = shot(Skin.PAPER, "paper_textcount_typed", typed)
    @Test fun terminalTyped() = shot(Skin.TERMINAL, "terminal_textcount_typed", typed)

    @Test fun classicCase() = shot(Skin.CLASSIC, "classic_textcount_case", upper)
    @Test fun paperCase() = shot(Skin.PAPER, "paper_textcount_case", upper)
    @Test fun terminalCase() = shot(Skin.TERMINAL, "terminal_textcount_case", upper)

    @Test fun classicLong() = shot(Skin.CLASSIC, "classic_textcount_long", long)
    @Test fun paperLong() = shot(Skin.PAPER, "paper_textcount_long", long)
    @Test fun terminalLong() = shot(Skin.TERMINAL, "terminal_textcount_long", long)

    private fun shot(skin: Skin, name: String, state: TextCountUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = TEXT_COUNT_TITLE,
                        command = TEXT_COUNT_COMMAND,
                        subtitle = TEXT_COUNT_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        TextCountContent(state, TextCountActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val TEXT = "Bonjour à tous ! La fête du quartier aura lieu samedi sur la place. Venez avec vos voisins, la fête commence à 18 h 🎉.\n\nApportez un plat à partager : la fête sera encore plus belle."
        val typed = TextCountUiState(text = TEXT)
        val upper = TextCountUiState(text = TEXT.uppercase(), lastCase = CaseKind.UPPER, caseTaps = 1, canUndo = true)
        val long = TextCountUiState(text = List(8) { TEXT }.joinToString("\n\n"))
    }
}
