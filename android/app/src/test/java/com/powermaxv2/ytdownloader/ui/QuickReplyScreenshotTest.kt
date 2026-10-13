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
import com.powermaxv2.ytdownloader.core.quickreply.QuickReplies
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QUICK_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QUICK_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QUICK_TITLE
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QuickActions
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QuickContent
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QuickDraft
import com.powermaxv2.ytdownloader.ui.tools.quickreply.QuickUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** Réponses rapides in the three skins: the starters, a first name filled in, a message being written, arranging. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class QuickReplyScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicList() = shot(Skin.CLASSIC, "classic_quick_list", list)
    @Test fun paperList() = shot(Skin.PAPER, "paper_quick_list", list)
    @Test fun terminalList() = shot(Skin.TERMINAL, "terminal_quick_list", list)

    @Test fun classicFilled() = shot(Skin.CLASSIC, "classic_quick_filled", filled)
    @Test fun paperFilled() = shot(Skin.PAPER, "paper_quick_filled", filled)
    @Test fun terminalFilled() = shot(Skin.TERMINAL, "terminal_quick_filled", filled)

    @Test fun classicDraft() = shot(Skin.CLASSIC, "classic_quick_draft", draft)
    @Test fun paperDraft() = shot(Skin.PAPER, "paper_quick_draft", draft)
    @Test fun terminalDraft() = shot(Skin.TERMINAL, "terminal_quick_draft", draft)

    @Test fun classicArrange() = shot(Skin.CLASSIC, "classic_quick_arrange", arrange)
    @Test fun paperArrange() = shot(Skin.PAPER, "paper_quick_arrange", arrange)
    @Test fun terminalArrange() = shot(Skin.TERMINAL, "terminal_quick_arrange", arrange)

    @Test fun classicEmpty() = shot(Skin.CLASSIC, "classic_quick_empty", QuickUiState(replies = emptyList(), now = now))

    private fun shot(skin: Skin, name: String, state: QuickUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = QUICK_TITLE,
                        command = QUICK_COMMAND,
                        subtitle = QUICK_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        QuickContent(state, QuickActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val now: LocalDateTime = LocalDateTime.of(2026, 10, 13, 18, 40)
        val list = QuickUiState(now = now, copied = 2)
        val filled = QuickUiState(
            replies = QuickReplies.starters.drop(3),
            values = mapOf("prenom" to "Léa", "lieu" to "la gare"),
            open = 4,
            now = now,
        )
        val draft = QuickUiState(
            replies = QuickReplies.starters.take(2),
            draft = QuickDraft(title = "Retard", text = "{bonjour} {prénom}, j'ai un peu de retard, j'arrive vers {heure+15} 🙏"),
            now = now,
        )
        val arrange = QuickUiState(arranging = true, now = now)
    }
}
