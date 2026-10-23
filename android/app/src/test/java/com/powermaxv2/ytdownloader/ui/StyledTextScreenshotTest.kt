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
import com.powermaxv2.ytdownloader.core.styled.TextStyleKind
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.styled.STYLED_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.styled.STYLED_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.styled.STYLED_TITLE
import com.powermaxv2.ytdownloader.ui.tools.styled.StyleFilter
import com.powermaxv2.ytdownloader.ui.tools.styled.StyledActions
import com.powermaxv2.ytdownloader.ui.tools.styled.StyledContent
import com.powermaxv2.ytdownloader.ui.tools.styled.StyledUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Texte stylé in the three skins: empty (sample), typed, just copied, and the fonts alone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StyledTextScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_styled_idle", StyledUiState())
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_styled_idle", StyledUiState())
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_styled_idle", StyledUiState())

    @Test fun classicTyped() = shot(Skin.CLASSIC, "classic_styled_typed", typed)
    @Test fun paperTyped() = shot(Skin.PAPER, "paper_styled_typed", typed)
    @Test fun terminalTyped() = shot(Skin.TERMINAL, "terminal_styled_typed", typed)

    @Test fun classicCopied() = shot(Skin.CLASSIC, "classic_styled_copied", copied)
    @Test fun paperCopied() = shot(Skin.PAPER, "paper_styled_copied", copied)
    @Test fun terminalCopied() = shot(Skin.TERMINAL, "terminal_styled_copied", copied)

    @Test fun classicFonts() = shot(Skin.CLASSIC, "classic_styled_fonts", fonts)
    @Test fun paperFonts() = shot(Skin.PAPER, "paper_styled_fonts", fonts)
    @Test fun terminalFonts() = shot(Skin.TERMINAL, "terminal_styled_fonts", fonts)

    private fun shot(skin: Skin, name: String, state: StyledUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = STYLED_TITLE,
                        command = STYLED_COMMAND,
                        subtitle = STYLED_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        StyledContent(state, StyledActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val typed = StyledUiState(text = "Soirée chez Léa samedi 🎉")
        val copied = typed.copy(copied = TextStyleKind.BOLD)
        val fonts = typed.copy(filter = StyleFilter.FONTS, copied = TextStyleKind.GOTHIC)
    }
}
