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
import com.powermaxv2.ytdownloader.core.vault.CodeStep
import com.powermaxv2.ytdownloader.core.vault.VaultImportDone
import com.powermaxv2.ytdownloader.core.vault.VaultItem
import com.powermaxv2.ytdownloader.core.vault.VaultPhase
import com.powermaxv2.ytdownloader.core.vault.VaultUiState
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.vault.VAULT_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.vault.VAULT_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.vault.VAULT_TITLE
import com.powermaxv2.ytdownloader.ui.tools.vault.VaultActions
import com.powermaxv2.ytdownloader.ui.tools.vault.VaultContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Coffre-fort in the three skins: the warning, choosing the code, the keypad after a wrong code, the open vault. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class VaultScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicWarning() = shot(Skin.CLASSIC, "classic_vault_warning", VaultUiState())
    @Test fun paperWarning() = shot(Skin.PAPER, "paper_vault_warning", VaultUiState())
    @Test fun terminalWarning() = shot(Skin.TERMINAL, "terminal_vault_warning", VaultUiState())

    @Test fun classicCode() = shot(Skin.CLASSIC, "classic_vault_code", code, entered = 4)
    @Test fun paperCode() = shot(Skin.PAPER, "paper_vault_code", code, entered = 4)
    @Test fun terminalCode() = shot(Skin.TERMINAL, "terminal_vault_code", code, entered = 4)

    @Test fun classicLocked() = shot(Skin.CLASSIC, "classic_vault_locked", locked, entered = 2)
    @Test fun paperLocked() = shot(Skin.PAPER, "paper_vault_locked", locked, entered = 2)
    @Test fun terminalLocked() = shot(Skin.TERMINAL, "terminal_vault_locked", locked, entered = 2)

    @Test fun classicOpen() = shot(Skin.CLASSIC, "classic_vault_open", open)
    @Test fun paperOpen() = shot(Skin.PAPER, "paper_vault_open", open)
    @Test fun terminalOpen() = shot(Skin.TERMINAL, "terminal_vault_open", open)

    private fun shot(skin: Skin, name: String, state: VaultUiState, entered: Int = 0) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = VAULT_TITLE,
                        command = VAULT_COMMAND,
                        subtitle = VAULT_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        VaultContent(state, entered = entered, actions = VaultActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val code = VaultUiState(phase = VaultPhase.Setup(CodeStep.CODE))
        val locked = VaultUiState(phase = VaultPhase.Locked(error = "Code incorrect", shake = 1), fingerprintOn = true)
        private fun item(i: Int, name: String, mime: String) =
            VaultItem("%032x".format(i), name, mime, 1_200_000L + i * 300_000L, 1_000L + i, hasThumb = false)
        val open = VaultUiState(
            phase = VaultPhase.Open,
            items = listOf(
                item(1, "IMG_2041.jpg", "image/jpeg"),
                item(2, "vacances.mp4", "video/mp4"),
                item(3, "passeport.pdf", "application/pdf"),
                item(4, "codes.txt", "text/plain"),
                item(5, "IMG_2042.jpg", "image/jpeg"),
            ),
            usedBytes = 18_400_000L,
            lastImport = VaultImportDone(added = 2, failed = 0, originals = 2),
        )
    }
}
