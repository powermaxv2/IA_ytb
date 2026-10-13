package com.powermaxv2.ytdownloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatSample
import com.powermaxv2.ytdownloader.core.chat.WhatsAppChat
import com.powermaxv2.ytdownloader.core.reader.ChatReader
import com.powermaxv2.ytdownloader.core.reader.ReaderChat
import com.powermaxv2.ytdownloader.core.reader.ReaderPhase
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.reader.READER_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.reader.READER_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.reader.READER_TITLE
import com.powermaxv2.ytdownloader.ui.tools.reader.ReaderActions
import com.powermaxv2.ytdownloader.ui.tools.reader.ReaderScreen
import com.powermaxv2.ytdownloader.ui.tools.reader.ReaderStartContent
import com.powermaxv2.ytdownloader.ui.tools.reader.ReaderUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Lecteur de discussion in the three skins: start screen, the open discussion, and a search. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ReaderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = start(Skin.CLASSIC, "classic_reader_idle")
    @Test fun paperIdle() = start(Skin.PAPER, "paper_reader_idle")
    @Test fun terminalIdle() = start(Skin.TERMINAL, "terminal_reader_idle")

    @Test fun classicChat() = open(Skin.CLASSIC, "classic_reader_chat", chatState)
    @Test fun paperChat() = open(Skin.PAPER, "paper_reader_chat", chatState)
    @Test fun terminalChat() = open(Skin.TERMINAL, "terminal_reader_chat", chatState)

    @Test fun classicSearch() = open(Skin.CLASSIC, "classic_reader_search", searchState)
    @Test fun paperSearch() = open(Skin.PAPER, "paper_reader_search", searchState)
    @Test fun terminalSearch() = open(Skin.TERMINAL, "terminal_reader_search", searchState)

    private fun start(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = READER_TITLE,
                        command = READER_COMMAND,
                        subtitle = READER_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        ReaderStartContent(ReaderPhase.Idle, ReaderActions())
                    }
                }
            }
        }
        capture(name)
    }

    private fun open(skin: Skin, name: String, state: ReaderUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ReaderScreen(
                        state = state,
                        actions = ReaderActions(),
                        listState = rememberLazyListState(),
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
        capture(name)
    }

    private fun capture(name: String) {
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val messages = WhatsAppChat.parse(ChatSample.text()).orEmpty()
        val chat = ReaderChat(ChatSample.NAME, messages, sample = true)
        val chatState = ReaderUiState(chat, me = ChatReader.guessMe(messages))
        val searchState = chatState.copy(query = "courses")
    }
}
