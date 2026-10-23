package com.powermaxv2.ytdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.powermaxv2.ytdownloader.R
import com.powermaxv2.ytdownloader.ui.brand.GulpColors
import com.powermaxv2.ytdownloader.ui.brand.GulpIntro
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.brand.GulpMarkStatic
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.skin.ClassicTokens
import com.powermaxv2.ytdownloader.ui.skin.TerminalTokens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.powermaxv2.ytdownloader.core.EngineState
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Settings
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.hub.HomeContent
import com.powermaxv2.ytdownloader.ui.hub.ToolCategory
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.foundation.layout.PaddingValues
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the three interfaces, rendered on the JVM with sample data (no App, engine or ViewModel).
 * Record with `./gradlew recordRoborazziDebug`; the PNGs land in app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real App starts the download engine in onCreate.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SkinScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()



    @Test fun classicHome() = home(Skin.CLASSIC, "classic")
    @Test fun paperHome() = home(Skin.PAPER, "paper")
    @Test fun terminalHome() = home(Skin.TERMINAL, "terminal")

    @Test fun classicHomeFavorites() = homeFavorites(Skin.CLASSIC, "classic")
    @Test fun paperHomeFavorites() = homeFavorites(Skin.PAPER, "paper")
    @Test fun terminalHomeFavorites() = homeFavorites(Skin.TERMINAL, "terminal")

    // Taller screen: the whole home screen, "À venir" included, in one picture.
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    @Test fun classicHomeLastUsed() = homeLastUsed(Skin.CLASSIC, "classic")
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    @Test fun paperHomeLastUsed() = homeLastUsed(Skin.PAPER, "paper")
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    @Test fun terminalHomeLastUsed() = homeLastUsed(Skin.TERMINAL, "terminal")

    // Search and categories of the home screen: typed "pdf", the "Fichiers" chip, and a search with no result.
    @Test fun classicHomeSearch() = homeFiltered(Skin.CLASSIC, "classic_home_search", query = "pdf")
    @Test fun paperHomeSearch() = homeFiltered(Skin.PAPER, "paper_home_search", query = "pdf")
    @Test fun terminalHomeSearch() = homeFiltered(Skin.TERMINAL, "terminal_home_search", query = "pdf")
    @Test fun classicHomeCategory() = homeFiltered(Skin.CLASSIC, "classic_home_category", category = ToolCategory.FILES)
    @Test fun paperHomeCategory() = homeFiltered(Skin.PAPER, "paper_home_category", category = ToolCategory.FILES)
    @Test fun terminalHomeCategory() = homeFiltered(Skin.TERMINAL, "terminal_home_category", category = ToolCategory.FILES)
    @Test fun classicHomeEmpty() = homeFiltered(Skin.CLASSIC, "classic_home_empty", query = "karaoké")
    @Test fun paperHomeEmpty() = homeFiltered(Skin.PAPER, "paper_home_empty", query = "karaoké")
    @Test fun terminalHomeEmpty() = homeFiltered(Skin.TERMINAL, "terminal_home_empty", query = "karaoké")

    @Test fun classicToolScaffold() = toolScaffold(Skin.CLASSIC, "classic")
    @Test fun paperToolScaffold() = toolScaffold(Skin.PAPER, "paper")
    @Test fun terminalToolScaffold() = toolScaffold(Skin.TERMINAL, "terminal")

    @Test fun classicSettingsInterface() = settings(Skin.CLASSIC, "classic")
    @Test fun paperSettingsInterface() = settings(Skin.PAPER, "paper")
    @Test fun terminalSettingsInterface() = settings(Skin.TERMINAL, "terminal")

    /** Launch animation mid-way (mark swallowing, letters rising) and the brand marks. */
    @Test fun gulpIntro() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AppTheme(Platform.GULP, Skin.CLASSIC) {
                GulpIntro(ClassicTokens.background, ClassicTokens.textPrimary, onFinished = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(820)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/gulp_intro.png")
    }

    @Test fun gulpBrand() {
        composeRule.setContent {
            AppTheme(Platform.GULP, Skin.CLASSIC) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(GulpColors.Cream)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Image(painterResource(R.drawable.ic_splash), contentDescription = null, modifier = Modifier.size(160.dp))
                    GulpLockup(64.dp, GulpColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GulpMarkStatic(72.dp)
                        Box(Modifier.size(72.dp).background(TerminalTokens.background)) {
                            GulpMarkStatic(72.dp, style = GulpStyle.outline(GulpColors.Orange))
                        }
                        Box(Modifier.size(72.dp).background(GulpColors.brush)) {
                            GulpMarkStatic(72.dp, style = GulpStyle.bare(androidx.compose.ui.graphics.Color.White, GulpColors.Ink))
                        }
                    }
                }
            }
        }
        capture("gulp_brand")
    }

    /** Home screen at the first launch: nothing used yet, so the downloader is the big card. */
    private fun home(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        lastUsedId = null,
                        onOpen = {},
                        onStar = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    )
                }
            }
        }
        capture("${name}_home")
    }

    /** Home screen with three starred tools: "Favoris" first, then the last tool used, then the categories. */
    private fun homeFavorites(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        lastUsedId = ToolRegistry.CHAT_STATS,
                        onOpen = {},
                        favoriteIds = listOf(ToolRegistry.QR_CODE, ToolRegistry.PDF_JOIN, ToolRegistry.STICKERS),
                        onStar = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    )
                }
            }
        }
        capture("${name}_home_favorites")
    }

    /** Home screen once "Vu, répondu" was the last tool used: it is the big card, the downloader a bubble. */
    private fun homeLastUsed(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        lastUsedId = ToolRegistry.CHAT_STATS,
                        onOpen = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    )
                }
            }
        }
        capture("${name}_home_last_used")
    }

    /** Home screen with a search typed in and/or a category picked. */
    private fun homeFiltered(skin: Skin, fileName: String, query: String = "", category: ToolCategory? = null) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        lastUsedId = null,
                        onOpen = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                        initialQuery = query,
                        initialCategory = category,
                    )
                }
            }
        }
        capture(fileName)
    }

    /** Shared frame of the file tools: top bar and the "Choisir un fichier" drop zone. */
    private fun toolScaffold(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = "Convertisseur",
                        command = "convert",
                        subtitle = "Change le format d'une photo, d'un son, d'une vidéo ou d'un document.",
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        FileDropZone(
                            title = "Choisir un fichier",
                            hint = "Photo, son, vidéo ou document",
                            icon = Icons.Rounded.UploadFile,
                            onClick = {},
                        )
                    }
                }
            }
        }
        capture("${name}_tool_scaffold")
    }

    private fun settings(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.GULP, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsInterfaceSection(skin) {}
                    }
                }
            }
        }
        capture("${name}_settings_interface")
    }

    private fun capture(fileName: String) {
        // Let the entrance animations (springs, staggered delays) settle.
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$fileName.png")
    }

}
