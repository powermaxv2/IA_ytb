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
import com.powermaxv2.ytdownloader.core.password.GeneratedPassword
import com.powermaxv2.ytdownloader.core.password.PasswordMode
import com.powermaxv2.ytdownloader.core.password.PasswordOptions
import com.powermaxv2.ytdownloader.core.password.Passwords
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.password.PASSWORD_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.password.PASSWORD_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.password.PASSWORD_TITLE
import com.powermaxv2.ytdownloader.ui.tools.password.PasswordActions
import com.powermaxv2.ytdownloader.ui.tools.password.PasswordContent
import com.powermaxv2.ytdownloader.ui.tools.password.PasswordUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random

/** The Générateur de mots de passe in the three skins: random, passphrase, PIN, and a typed password tested. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PasswordScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicRandom() = shot(Skin.CLASSIC, "classic_password_random", random)
    @Test fun paperRandom() = shot(Skin.PAPER, "paper_password_random", random)
    @Test fun terminalRandom() = shot(Skin.TERMINAL, "terminal_password_random", random)

    @Test fun classicPhrase() = shot(Skin.CLASSIC, "classic_password_phrase", phrase)
    @Test fun paperPhrase() = shot(Skin.PAPER, "paper_password_phrase", phrase)
    @Test fun terminalPhrase() = shot(Skin.TERMINAL, "terminal_password_phrase", phrase)

    @Test fun classicPin() = shot(Skin.CLASSIC, "classic_password_pin", pin)
    @Test fun paperPin() = shot(Skin.PAPER, "paper_password_pin", pin)
    @Test fun terminalPin() = shot(Skin.TERMINAL, "terminal_password_pin", pin)

    @Test fun classicCheck() = shot(Skin.CLASSIC, "classic_password_check", check)
    @Test fun paperCheck() = shot(Skin.PAPER, "paper_password_check", check)
    @Test fun terminalCheck() = shot(Skin.TERMINAL, "terminal_password_check", check)

    private fun shot(skin: Skin, name: String, state: PasswordUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PASSWORD_TITLE,
                        command = PASSWORD_COMMAND,
                        subtitle = PASSWORD_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        PasswordContent(state, PasswordActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val options = PasswordOptions()
        fun made(mode: PasswordMode): GeneratedPassword = Passwords.generate(mode, options, Random(7))
        val random = PasswordUiState(current = made(PasswordMode.RANDOM), copied = true, history = listOf("Lp7#qe2!Vx9@mZtR4&kc"))
        val phrase = PasswordUiState(mode = PasswordMode.PASSPHRASE, current = made(PasswordMode.PASSPHRASE))
        val pin = PasswordUiState(mode = PasswordMode.PIN, current = made(PasswordMode.PIN))
        val check = PasswordUiState(mode = PasswordMode.CHECK, typed = "Soleil2024!")
    }
}
