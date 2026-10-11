package com.powermaxv2.ytdownloader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatExport
import com.powermaxv2.ytdownloader.core.chat.ChatPhase
import com.powermaxv2.ytdownloader.core.chat.ReplyOptions
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.chat.CHAT_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.chat.CHAT_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.chat.CHAT_TITLE
import com.powermaxv2.ytdownloader.ui.tools.chat.ChatActions
import com.powermaxv2.ytdownloader.ui.tools.chat.ChatContent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/** "Vu, répondu" in the three skins: the empty screen, then the example discussion's results on a tall screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ChatStatsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_chat_idle", ChatPhase.Idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_chat_idle", ChatPhase.Idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_chat_idle", ChatPhase.Idle)

    // Tall screen: every result card in one picture.
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun classicResults() = shot(Skin.CLASSIC, "classic_chat_results", ChatExport.sample())
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun paperResults() = shot(Skin.PAPER, "paper_chat_results", ChatExport.sample())
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun terminalResults() = shot(Skin.TERMINAL, "terminal_chat_results", ChatExport.sample())

    // The rest of the results, scrolled to the end (one picture taller than ~16 000 px loses its animated layers).
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun classicResultsEnd() = shot(Skin.CLASSIC, "classic_chat_results_end", ChatExport.sample(), scrollToEnd = true)
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun paperResultsEnd() = shot(Skin.PAPER, "paper_chat_results_end", ChatExport.sample(), scrollToEnd = true)
    @Config(qualifiers = "w411dp-h10000dp-hdpi")
    @Test fun terminalResultsEnd() = shot(Skin.TERMINAL, "terminal_chat_results_end", ChatExport.sample(), scrollToEnd = true)

    // The precision switches turned on.
    @Test fun classicOptions() = shot(Skin.CLASSIC, "classic_chat_options", ChatExport.sample(), ReplyOptions(nights = true, realWait = true))

    @Test fun classicFailed() = shot(Skin.CLASSIC, "classic_chat_failed", ChatPhase.Failed(ChatExport.NOT_WHATSAPP))
    @Test fun paperFailed() = shot(Skin.PAPER, "paper_chat_failed", ChatPhase.Failed(ChatExport.NOT_WHATSAPP))
    @Test fun terminalFailed() = shot(Skin.TERMINAL, "terminal_chat_failed", ChatPhase.Failed(ChatExport.NOT_WHATSAPP))

    private fun shot(skin: Skin, name: String, phase: ChatPhase, options: ReplyOptions = ReplyOptions(), scrollToEnd: Boolean = false) {
        lateinit var scroll: ScrollState
        composeRule.setContent {
            scroll = rememberScrollState()
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = CHAT_TITLE,
                        command = CHAT_COMMAND,
                        subtitle = CHAT_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                        scrollState = scroll,
                    ) {
                        ChatContent(phase, options, ChatActions())
                    }
                }
            }
        }
        // Long enough for the podium crown and every bar; the demo chat is then mid-conversation.
        composeRule.mainClock.advanceTimeBy(7_000)
        composeRule.waitForIdle()
        if (scrollToEnd) {
            composeRule.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
            composeRule.mainClock.advanceTimeBy(7_000)
            composeRule.waitForIdle()
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}
