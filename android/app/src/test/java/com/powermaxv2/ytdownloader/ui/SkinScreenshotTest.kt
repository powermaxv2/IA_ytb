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
import com.powermaxv2.ytdownloader.core.MediaInfo
import com.powermaxv2.ytdownloader.core.Platform
import com.powermaxv2.ytdownloader.core.Settings
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.VideoFormat
import com.powermaxv2.ytdownloader.core.VideoInfo
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.hub.HomeContent
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import androidx.compose.foundation.layout.PaddingValues
import com.powermaxv2.ytdownloader.ui.skin.SkinBottomBar
import com.powermaxv2.ytdownloader.ui.skin.SkinGuide
import com.powermaxv2.ytdownloader.ui.skin.SkinHeader
import com.powermaxv2.ytdownloader.ui.skin.SkinLinkCard
import com.powermaxv2.ytdownloader.ui.skin.SkinVideoCard
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

    @Test fun classicYoutubeIdle() = idle(Skin.CLASSIC, "classic")
    @Test fun paperYoutubeIdle() = idle(Skin.PAPER, "paper")
    @Test fun terminalYoutubeIdle() = idle(Skin.TERMINAL, "terminal")

    @Test fun classicYoutubeVideo() = video(Skin.CLASSIC, "classic")
    @Test fun paperYoutubeVideo() = video(Skin.PAPER, "paper")
    @Test fun terminalYoutubeVideo() = video(Skin.TERMINAL, "terminal")

    @Test fun classicHome() = home(Skin.CLASSIC, "classic")
    @Test fun paperHome() = home(Skin.PAPER, "paper")
    @Test fun terminalHome() = home(Skin.TERMINAL, "terminal")

    @Test fun classicSettingsInterface() = settings(Skin.CLASSIC, "classic")
    @Test fun paperSettingsInterface() = settings(Skin.PAPER, "paper")
    @Test fun terminalSettingsInterface() = settings(Skin.TERMINAL, "terminal")

    /** Launch animation mid-way (mark swallowing, letters rising) and the brand marks. */
    @Test fun gulpIntro() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, Skin.CLASSIC) {
                GulpIntro(ClassicTokens.background, ClassicTokens.textPrimary, onFinished = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(820)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/gulp_intro.png")
    }

    @Test fun gulpBrand() {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, Skin.CLASSIC) {
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

    private fun idle(skin: Skin, name: String) {
        val pane = LinkPane(Platform.YOUTUBE, Tab.YOUTUBE, "YouTube")
        composeRule.setContent {
            Screen(skin, pane) { SkinGuide(Platform.YOUTUBE) }
        }
        capture("${name}_youtube_idle")
    }

    private fun video(skin: Skin, name: String) {
        val pane = LinkPane(Platform.YOUTUBE, Tab.YOUTUBE, "YouTube").apply {
            urlText = SAMPLE_URL
            phase = Phase.Ready(MediaInfo.Video(sampleVideo, SAMPLE_URL))
        }
        composeRule.setContent {
            Screen(skin, pane) {
                SkinVideoCard(sampleVideo, SAMPLE_URL, Settings(skin = skin)) { _, _, _, _ -> }
            }
        }
        capture("${name}_youtube_video")
    }

    /** Home screen of the hub with the real list of tools (the downloader + the "À venir" ones). */
    private fun home(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    HomeContent(
                        tools = ToolRegistry.tools,
                        onOpen = {},
                        onSettings = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    )
                }
            }
        }
        capture("${name}_home")
    }

    private fun settings(skin: Skin, name: String) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
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

    /** Same layout as the YouTube tab (LinkScreen) without the ViewModel-driven banners. */
    @Composable
    private fun Screen(skin: Skin, pane: LinkPane, content: @Composable ColumnScope.() -> Unit) {
        AppTheme(Platform.YOUTUBE, skin) {
            Box(Modifier.fillMaxSize()) {
                SkinBackground()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 104.dp)
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // As inside the hub: the logo leads back to the home screen.
                    SkinHeader(Platform.YOUTUBE, EngineState.Ready, onSettings = {}, onHome = {})
                    SkinLinkCard(pane, enabled = true, onPaste = {}, onAnalyze = {}, onReset = {})
                    content()
                }
                SkinBottomBar(
                    tab = Tab.YOUTUBE,
                    activeCount = 1,
                    pulse = 0,
                    onSelect = {},
                    bottomInset = 0.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    private companion object {
        const val SAMPLE_URL = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"

        fun format(id: String, height: Int?, width: Int?, fps: Double?, tbr: Double, vcodec: String, acodec: String, hdr: Boolean = false) =
            VideoFormat(
                formatId = id,
                ext = if (vcodec == "none") "m4a" else "webm",
                vcodec = vcodec,
                acodec = acodec,
                width = width,
                height = height,
                fps = fps,
                tbr = tbr,
                filesize = null,
                dynamicRange = if (hdr) "HDR10" else "SDR",
            )

        val sampleVideo = VideoInfo(
            id = "aqz-KE-bpKQ",
            title = "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film",
            uploader = "Blender",
            duration = 635.0,
            thumbnail = null,
            viewCount = 15_400_000.0,
            webpageUrl = SAMPLE_URL,
            formats = listOf(
                format("140", null, null, null, 129.0, "none", "mp4a.40.2"),
                format("401", 2160, 3840, 60.0, 18_000.0, "av01.0.12M.08", "none", hdr = true),
                format("400", 1440, 2560, 60.0, 9_000.0, "av01.0.12M.08", "none"),
                format("299", 1080, 1920, 60.0, 4_500.0, "avc1.64002a", "none"),
                format("298", 720, 1280, 60.0, 2_500.0, "avc1.4d4020", "none"),
                format("135", 480, 854, 30.0, 1_100.0, "avc1.4d401f", "none"),
                format("134", 360, 640, 30.0, 600.0, "avc1.4d401e", "none"),
            ),
        )
    }
}
