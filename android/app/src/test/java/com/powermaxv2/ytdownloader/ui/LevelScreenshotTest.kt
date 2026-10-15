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
import com.powermaxv2.ytdownloader.core.level.LevelMode
import com.powermaxv2.ytdownloader.core.level.LevelReading
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.level.LEVEL_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.level.LEVEL_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.level.LEVEL_TITLE
import com.powermaxv2.ytdownloader.ui.tools.level.LevelActions
import com.powermaxv2.ytdownloader.ui.tools.level.LevelContent
import com.powermaxv2.ytdownloader.ui.tools.level.LevelUiState
import com.powermaxv2.ytdownloader.ui.tools.level.SensorStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Niveau à bulle in the three skins: flat and tilted, flat and straight (held), on an edge, and no sensor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class LevelScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicFlat() = shot(Skin.CLASSIC, "classic_level_flat", flat)
    @Test fun paperFlat() = shot(Skin.PAPER, "paper_level_flat", flat)
    @Test fun terminalFlat() = shot(Skin.TERMINAL, "terminal_level_flat", flat)

    @Test fun classicStraight() = shot(Skin.CLASSIC, "classic_level_straight", straight)
    @Test fun paperStraight() = shot(Skin.PAPER, "paper_level_straight", straight)
    @Test fun terminalStraight() = shot(Skin.TERMINAL, "terminal_level_straight", straight)

    @Test fun classicEdge() = shot(Skin.CLASSIC, "classic_level_edge", edge)
    @Test fun paperEdge() = shot(Skin.PAPER, "paper_level_edge", edge)
    @Test fun terminalEdge() = shot(Skin.TERMINAL, "terminal_level_edge", edge)

    @Test fun classicNoSensor() = shot(Skin.CLASSIC, "classic_level_nosensor", none)
    @Test fun paperNoSensor() = shot(Skin.PAPER, "paper_level_nosensor", none)
    @Test fun terminalNoSensor() = shot(Skin.TERMINAL, "terminal_level_nosensor", none)

    private fun shot(skin: Skin, name: String, state: LevelUiState) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = LEVEL_TITLE,
                        command = LEVEL_COMMAND,
                        subtitle = LEVEL_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        LevelContent(state, LevelActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        val flat = LevelUiState(
            status = SensorStatus.READY,
            reading = LevelReading.Flat(tiltX = 3.4f, tiltY = -1.2f, level = false),
            sensorKind = "capteur de gravité",
        )
        val straight = LevelUiState(
            status = SensorStatus.READY,
            reading = LevelReading.Flat(tiltX = 0.1f, tiltY = 0.2f, level = true),
            frozen = true,
            calibrated = true,
            sensorKind = "accéléromètre",
        )
        val edge = LevelUiState(
            status = SensorStatus.READY,
            mode = LevelMode.EDGE,
            reading = LevelReading.Edge(angle = -1.8f, snap = 0, level = false),
        )
        val none = LevelUiState(status = SensorStatus.MISSING)
    }
}
