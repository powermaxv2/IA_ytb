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
import com.powermaxv2.ytdownloader.core.units.Ingredient
import com.powermaxv2.ytdownloader.core.units.Quantity
import com.powermaxv2.ytdownloader.core.units.Units
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.units.Side
import com.powermaxv2.ytdownloader.ui.tools.units.UNITS_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.units.UNITS_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.units.UNITS_TITLE
import com.powermaxv2.ytdownloader.ui.tools.units.UnitsActions
import com.powermaxv2.ytdownloader.ui.tools.units.UnitsContent
import com.powermaxv2.ytdownloader.ui.tools.units.UnitsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Convertisseur d'unités in the three skins: lengths, the oven thermometer, a cup of flour, and a unit list open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class UnitsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicLength() = shot(Skin.CLASSIC, "classic_units_length", length)
    @Test fun paperLength() = shot(Skin.PAPER, "paper_units_length", length)
    @Test fun terminalLength() = shot(Skin.TERMINAL, "terminal_units_length", length)

    @Test fun classicTemperature() = shot(Skin.CLASSIC, "classic_units_temperature", oven)
    @Test fun paperTemperature() = shot(Skin.PAPER, "paper_units_temperature", oven)
    @Test fun terminalTemperature() = shot(Skin.TERMINAL, "terminal_units_temperature", oven)

    @Test fun classicCooking() = shot(Skin.CLASSIC, "classic_units_cooking", cooking)
    @Test fun paperCooking() = shot(Skin.PAPER, "paper_units_cooking", cooking)
    @Test fun terminalCooking() = shot(Skin.TERMINAL, "terminal_units_cooking", cooking)

    @Test fun classicPicker() = shot(Skin.CLASSIC, "classic_units_picker", picker)
    @Test fun paperPicker() = shot(Skin.PAPER, "paper_units_picker", picker)
    @Test fun terminalPicker() = shot(Skin.TERMINAL, "terminal_units_picker", picker)

    private fun shot(skin: Skin, name: String, state: UnitsUiState) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = UNITS_TITLE,
                        command = UNITS_COMMAND,
                        subtitle = UNITS_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        UnitsContent(state, UnitsActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val length = UnitsUiState(input = "42,195")
        val oven = UnitsUiState(
            quantity = Quantity.TEMPERATURE,
            input = "350",
            from = Units.unit("f")!!,
            to = Units.unit("c")!!,
        )
        val cooking = UnitsUiState(
            quantity = Quantity.COOKING,
            input = "1 1/2",
            from = Units.unit("cup")!!,
            to = Units.unit("cook-g")!!,
            ingredient = Ingredient.FLOUR,
            copied = true,
        )
        val picker = UnitsUiState(
            quantity = Quantity.SPEED,
            input = "130",
            from = Units.unit("kmh")!!,
            to = Units.unit("mph")!!,
            picking = Side.TO,
        )
    }
}
