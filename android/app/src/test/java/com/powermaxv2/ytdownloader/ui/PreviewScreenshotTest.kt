package com.powermaxv2.ytdownloader.ui

import android.net.Uri
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
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.preview.DecodedText
import com.powermaxv2.ytdownloader.core.preview.ExtensionCheck
import com.powermaxv2.ytdownloader.core.preview.PreviewBody
import com.powermaxv2.ytdownloader.core.preview.PreviewDoc
import com.powermaxv2.ytdownloader.core.preview.PreviewFile
import com.powermaxv2.ytdownloader.core.preview.PreviewLoader
import com.powermaxv2.ytdownloader.core.preview.PreviewPhase
import com.powermaxv2.ytdownloader.core.preview.ZipEntryInfo
import com.powermaxv2.ytdownloader.core.preview.ZipNames
import com.powermaxv2.ytdownloader.ui.skin.SkinBackground
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.preview.HashState
import com.powermaxv2.ytdownloader.ui.tools.preview.PREVIEW_COMMAND
import com.powermaxv2.ytdownloader.ui.tools.preview.PREVIEW_SUBTITLE
import com.powermaxv2.ytdownloader.ui.tools.preview.PREVIEW_TITLE
import com.powermaxv2.ytdownloader.ui.tools.preview.PreviewActions
import com.powermaxv2.ytdownloader.ui.tools.preview.PreviewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Aperçu in the three skins, with sample data (no file access, no media). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PreviewScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun classicIdle() = shot(Skin.CLASSIC, "classic_preview_idle", PreviewPhase.Idle)
    @Test fun paperIdle() = shot(Skin.PAPER, "paper_preview_idle", PreviewPhase.Idle)
    @Test fun terminalIdle() = shot(Skin.TERMINAL, "terminal_preview_idle", PreviewPhase.Idle)

    @Test fun classicCode() = shot(Skin.CLASSIC, "classic_preview_code", code)
    @Test fun paperCode() = shot(Skin.PAPER, "paper_preview_code", code)
    @Test fun terminalCode() = shot(Skin.TERMINAL, "terminal_preview_code", code)

    @Test fun classicZip() = shot(Skin.CLASSIC, "classic_preview_zip", zip)
    @Test fun paperZip() = shot(Skin.PAPER, "paper_preview_zip", zip)
    @Test fun terminalZip() = shot(Skin.TERMINAL, "terminal_preview_zip", zip)

    /** Android 14+ refused the archive (a ".." name): the names come from our own read of the central directory. */
    @Test fun classicZipRefused() = shot(Skin.CLASSIC, "classic_preview_zip_refused", zipRefused)
    @Test fun paperZipRefused() = shot(Skin.PAPER, "paper_preview_zip_refused", zipRefused)
    @Test fun terminalZipRefused() = shot(Skin.TERMINAL, "terminal_preview_zip_refused", zipRefused)

    @Test fun classicNotice() = shot(Skin.CLASSIC, "classic_preview_notice", notice)
    @Test fun paperNotice() = shot(Skin.PAPER, "paper_preview_notice", notice)
    @Test fun terminalNotice() = shot(Skin.TERMINAL, "terminal_preview_notice", notice)

    @Test fun classicInfo() = shot(Skin.CLASSIC, "classic_preview_info", renamed, infoOpen = true)
    @Test fun paperInfo() = shot(Skin.PAPER, "paper_preview_info", renamed, infoOpen = true)
    @Test fun terminalInfo() = shot(Skin.TERMINAL, "terminal_preview_info", renamed, infoOpen = true)

    private fun shot(skin: Skin, name: String, phase: PreviewPhase, infoOpen: Boolean = false) {
        composeRule.setContent {
            AppTheme(Platform.YOUTUBE, skin) {
                Box(Modifier.fillMaxSize()) {
                    SkinBackground()
                    ToolScaffold(
                        title = PREVIEW_TITLE,
                        command = PREVIEW_COMMAND,
                        subtitle = PREVIEW_SUBTITLE,
                        onHome = {},
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    ) {
                        PreviewContent(
                            phase,
                            PreviewActions(),
                            hash = if (infoOpen) HashState(progress = 1f, value = SHA) else HashState(),
                            infoInitiallyOpen = infoOpen,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

        val kotlinSample = """
            package com.exemple.gulp

            import android.os.Bundle

            /** Écran d'accueil : affiche le nombre de fichiers. */
            @Composable
            fun Accueil(fichiers: List<String>, limite: Int = 42) {
                val total = fichiers.size + 0x1F
                if (total > limite) {
                    Log.d("Gulp", "Trop de fichiers : ${'$'}total")
                }
                /* Les noms vides sont ignorés. */
                fichiers.filter { it.isNotBlank() }
                    .forEach { nom -> println(nom) }
                return
            }
        """.trimIndent() + "\n"

        val code: PreviewPhase = run {
            val file = PreviewFile(
                uri = Uri.parse("content://sample/code"),
                name = "Accueil.kt",
                size = kotlinSample.length.toLong(),
                mime = "text/x-kotlin",
                kind = FileKind.CODE,
                head = kotlinSample.toByteArray().copyOf(256),
                expectedExtension = null,
            )
            val body = PreviewLoader.textBody(FileKind.CODE, file.name, DecodedText(kotlinSample, "UTF-8"), cut = false)
            PreviewPhase.Ready(PreviewDoc(file, body))
        }

        val zipHead = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0, 0x08, 0) + "[Content_Types].xml".toByteArray()

        val listing = ZipNames.tree(
            listOf(
                ZipEntryInfo("[Content_Types].xml", 1_312, 402, false),
                ZipEntryInfo("_rels/.rels", 590, 243, false),
                ZipEntryInfo("word/document.xml", 48_210, 9_877, false),
                ZipEntryInfo("word/styles.xml", 29_004, 3_120, false),
                ZipEntryInfo("word/media/image1.png", 284_113, 280_950, false),
                ZipEntryInfo("word/media/image2.jpeg", 512_340, 509_002, false),
                ZipEntryInfo("docProps/core.xml", 742, 371, false),
                ZipEntryInfo("../../Android/data/piege.sh", 220, 180, false),
            ),
        )

        val zip: PreviewPhase = PreviewPhase.Ready(
            PreviewDoc(
                PreviewFile(Uri.parse("content://sample/zip"), "Rapport annuel.docx", 412_000, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileKind.ZIP, zipHead, null),
                PreviewBody.Zip(listing),
            ),
        )

        val zipFile = PreviewFile(Uri.parse("content://sample/zip-refused"), "photos-vacances.zip", 1_204_000, "application/zip", FileKind.ZIP, zipHead, null)

        val zipRefused: PreviewPhase = PreviewPhase.Ready(PreviewDoc(zipFile, PreviewBody.Zip(listing.copy(refusedByAndroid = true))))

        val notice: PreviewPhase = PreviewPhase.Ready(PreviewDoc(zipFile, PreviewBody.Notice(PreviewLoader.DANGEROUS_ZIP)))

        /** A Word file renamed ".pdf": the bytes say ZIP. */
        val renamed: PreviewPhase = PreviewPhase.Ready(
            PreviewDoc(
                PreviewFile(
                    Uri.parse("content://sample/renamed"),
                    "Rapport.pdf",
                    412_000,
                    "application/pdf",
                    FileKind.ZIP,
                    zipHead,
                    ExtensionCheck.mismatch("Rapport.pdf", zipHead),
                ),
                PreviewBody.Zip(listing),
            ),
        )
    }
}
