package com.powermaxv2.ytdownloader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test fun downloaderIsTheFirstToolAndOpens() {
        val first = ToolRegistry.tools.first()
        assertEquals(ToolRegistry.DOWNLOADER, first.id)
        assertTrue(first.available)
        assertNotNull(ToolRegistry.find(ToolRegistry.DOWNLOADER))
    }

    @Test fun fileToolsFollowTheDownloader() {
        val ids = ToolRegistry.tools.map { it.id }
        assertEquals(
            listOf(ToolRegistry.DOWNLOADER, ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS, ToolRegistry.COMPRESSOR, ToolRegistry.QR_CODE, ToolRegistry.SCANNER, ToolRegistry.TRIMMER, ToolRegistry.WIPER, ToolRegistry.PDF_JOIN, ToolRegistry.VOICE_TEXT, ToolRegistry.CLEANER, ToolRegistry.STICKERS, ToolRegistry.STYLED_TEXT, ToolRegistry.STATUSES, ToolRegistry.STATUS_SPLIT, ToolRegistry.DIRECT_CHAT, ToolRegistry.CHAT_READER, ToolRegistry.QUICK_REPLIES, ToolRegistry.PROFILE_FIT, ToolRegistry.SIGN_PDF, ToolRegistry.WATERMARK, ToolRegistry.IMAGE_RESIZER, ToolRegistry.PHOTO_DUPLICATES, ToolRegistry.SLIDESHOW, ToolRegistry.MY_LINK, ToolRegistry.RENAMER, ToolRegistry.VAULT, ToolRegistry.VOICE_RECORDER, ToolRegistry.BILL_SPLIT, ToolRegistry.UNIT_CONVERTER, ToolRegistry.LEVEL, ToolRegistry.TEXT_COUNTER, ToolRegistry.PASSWORDS, ToolRegistry.VIDEO_SPEED),
            ids.take(35),
        )
        // Stable ids: intents (Partager → Gulp) and saved state use them.
        assertEquals("converter", ToolRegistry.CONVERTER)
        assertEquals("preview", ToolRegistry.PREVIEW)
        assertEquals("chat-stats", ToolRegistry.CHAT_STATS)
        assertEquals("compressor", ToolRegistry.COMPRESSOR)
        assertEquals("qr-code", ToolRegistry.QR_CODE)
        assertEquals("scanner", ToolRegistry.SCANNER)
        assertEquals("trimmer", ToolRegistry.TRIMMER)
        assertEquals("wiper", ToolRegistry.WIPER)
        assertEquals("pdf-join", ToolRegistry.PDF_JOIN)
        assertEquals("voice-text", ToolRegistry.VOICE_TEXT)
        assertEquals("whatsapp-cleaner", ToolRegistry.CLEANER)
        assertEquals("stickers", ToolRegistry.STICKERS)
        assertEquals("styled-text", ToolRegistry.STYLED_TEXT)
        assertEquals("statuses", ToolRegistry.STATUSES)
        assertEquals("status-split", ToolRegistry.STATUS_SPLIT)
        assertEquals("whatsapp-direct", ToolRegistry.DIRECT_CHAT)
        assertEquals("chat-reader", ToolRegistry.CHAT_READER)
        assertEquals("file-renamer", ToolRegistry.RENAMER)
        assertEquals("quick-replies", ToolRegistry.QUICK_REPLIES)
        assertEquals("profile-fit", ToolRegistry.PROFILE_FIT)
        assertEquals("sign-pdf", ToolRegistry.SIGN_PDF)
        assertEquals("watermark", ToolRegistry.WATERMARK)
        assertEquals("photo-duplicates", ToolRegistry.PHOTO_DUPLICATES)
        assertEquals("image-resizer", ToolRegistry.IMAGE_RESIZER)
        assertEquals("slideshow", ToolRegistry.SLIDESHOW)
        assertEquals("whatsapp-link", ToolRegistry.MY_LINK)
        assertEquals("vault", ToolRegistry.VAULT)
        assertEquals("password-generator", ToolRegistry.PASSWORDS)
        assertEquals("text-counter", ToolRegistry.TEXT_COUNTER)
        assertEquals("voice-recorder", ToolRegistry.VOICE_RECORDER)
        assertEquals("bill-split", ToolRegistry.BILL_SPLIT)
        assertEquals("unit-converter", ToolRegistry.UNIT_CONVERTER)
        assertEquals("bubble-level", ToolRegistry.LEVEL)
        assertEquals("video-speed", ToolRegistry.VIDEO_SPEED)
        listOf(ToolRegistry.CONVERTER, ToolRegistry.PREVIEW, ToolRegistry.CHAT_STATS, ToolRegistry.COMPRESSOR, ToolRegistry.QR_CODE, ToolRegistry.SCANNER, ToolRegistry.TRIMMER, ToolRegistry.WIPER, ToolRegistry.PDF_JOIN, ToolRegistry.VOICE_TEXT, ToolRegistry.CLEANER, ToolRegistry.STICKERS, ToolRegistry.STYLED_TEXT, ToolRegistry.STATUSES, ToolRegistry.STATUS_SPLIT, ToolRegistry.DIRECT_CHAT, ToolRegistry.CHAT_READER, ToolRegistry.QUICK_REPLIES, ToolRegistry.PROFILE_FIT, ToolRegistry.SIGN_PDF, ToolRegistry.WATERMARK, ToolRegistry.IMAGE_RESIZER, ToolRegistry.PHOTO_DUPLICATES, ToolRegistry.SLIDESHOW, ToolRegistry.MY_LINK, ToolRegistry.RENAMER, ToolRegistry.VAULT, ToolRegistry.VOICE_RECORDER, ToolRegistry.BILL_SPLIT, ToolRegistry.UNIT_CONVERTER, ToolRegistry.LEVEL, ToolRegistry.TEXT_COUNTER, ToolRegistry.PASSWORDS, ToolRegistry.VIDEO_SPEED).forEach { id ->
            val tool = ToolRegistry.find(id)
            assertNotNull(id, tool)
            tool!!
            assertTrue(tool.available)
            assertTrue(tool.platforms.isEmpty())
            assertNotNull("$id needs an icon for its home card", tool.icon)
            assertEquals(3, tool.tags.size)
        }
        assertEquals("Convertisseur", ToolRegistry.find(ToolRegistry.CONVERTER)?.name)
        assertEquals("Aperçu", ToolRegistry.find(ToolRegistry.PREVIEW)?.name)
        assertEquals("Vu, répondu", ToolRegistry.find(ToolRegistry.CHAT_STATS)?.name)
        assertEquals("Compresseur", ToolRegistry.find(ToolRegistry.COMPRESSOR)?.name)
        assertEquals("QR code", ToolRegistry.find(ToolRegistry.QR_CODE)?.name)
        assertEquals("Scanner", ToolRegistry.find(ToolRegistry.SCANNER)?.name)
        assertEquals("Découpeur", ToolRegistry.find(ToolRegistry.TRIMMER)?.name)
        assertEquals("Gomme", ToolRegistry.find(ToolRegistry.WIPER)?.name)
        assertEquals("Assembleur PDF", ToolRegistry.find(ToolRegistry.PDF_JOIN)?.name)
        assertEquals("Vocaux en texte", ToolRegistry.find(ToolRegistry.VOICE_TEXT)?.name)
        assertEquals("Nettoyeur WhatsApp", ToolRegistry.find(ToolRegistry.CLEANER)?.name)
        assertEquals("Stickers", ToolRegistry.find(ToolRegistry.STICKERS)?.name)
        assertEquals("Texte stylé", ToolRegistry.find(ToolRegistry.STYLED_TEXT)?.name)
        assertEquals("Statuts", ToolRegistry.find(ToolRegistry.STATUSES)?.name)
        assertEquals("Découpe statut", ToolRegistry.find(ToolRegistry.STATUS_SPLIT)?.name)
        assertEquals("Écrire sans enregistrer", ToolRegistry.find(ToolRegistry.DIRECT_CHAT)?.name)
        assertEquals("Lecteur de discussion", ToolRegistry.find(ToolRegistry.CHAT_READER)?.name)
        assertEquals("Renommeur", ToolRegistry.find(ToolRegistry.RENAMER)?.name)
        assertEquals("Réponses rapides", ToolRegistry.find(ToolRegistry.QUICK_REPLIES)?.name)
        assertEquals("Photo de profil entière", ToolRegistry.find(ToolRegistry.PROFILE_FIT)?.name)
        assertEquals("Signature PDF", ToolRegistry.find(ToolRegistry.SIGN_PDF)?.name)
        assertEquals("Filigrane", ToolRegistry.find(ToolRegistry.WATERMARK)?.name)
        assertEquals("Doublons photos", ToolRegistry.find(ToolRegistry.PHOTO_DUPLICATES)?.name)
        assertEquals("Redimensionneur", ToolRegistry.find(ToolRegistry.IMAGE_RESIZER)?.name)
        assertEquals("Diaporama", ToolRegistry.find(ToolRegistry.SLIDESHOW)?.name)
        assertEquals("Mon lien WhatsApp", ToolRegistry.find(ToolRegistry.MY_LINK)?.name)
        assertEquals("Coffre-fort", ToolRegistry.find(ToolRegistry.VAULT)?.name)
        assertEquals("Mots de passe", ToolRegistry.find(ToolRegistry.PASSWORDS)?.name)
        assertEquals("Compteur de texte", ToolRegistry.find(ToolRegistry.TEXT_COUNTER)?.name)
        assertEquals("Enregistreur vocal", ToolRegistry.find(ToolRegistry.VOICE_RECORDER)?.name)
        assertEquals("Partage d'addition", ToolRegistry.find(ToolRegistry.BILL_SPLIT)?.name)
        assertEquals("Convertisseur d'unités", ToolRegistry.find(ToolRegistry.UNIT_CONVERTER)?.name)
        assertEquals("Niveau à bulle", ToolRegistry.find(ToolRegistry.LEVEL)?.name)
        assertEquals("Vitesse vidéo", ToolRegistry.find(ToolRegistry.VIDEO_SPEED)?.name)
    }

    @Test fun oneSlotStaysComingSoon() {
        assertEquals(listOf("soon-36"), ToolRegistry.upcoming.map { it.id })
        assertEquals(35, ToolRegistry.available.size)
    }

    @Test fun honestWording() {
        ToolRegistry.tools.forEach { tool ->
            val text = (listOf(tool.name, tool.tagline) + tool.tags).joinToString(" ").lowercase()
            assertTrue(tool.id, "tous les formats" !in text)
        }
    }

    @Test fun comingSoonToolsCannotBeOpened() {
        assertTrue(ToolRegistry.upcoming.isNotEmpty())
        ToolRegistry.upcoming.forEach { assertNull(it.id, ToolRegistry.find(it.id)) }
    }

    @Test fun unknownIdsAreIgnored() {
        assertNull(ToolRegistry.find(null))
        assertNull(ToolRegistry.find("../downloader"))
    }

    @Test fun idsAreUnique() {
        val ids = ToolRegistry.tools.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun counts() {
        assertEquals("1 outil prêt", readyText(1))
        assertEquals("2 outils prêts", readyText(2))
        assertEquals("03", toolNumber(3))
    }

    @Test fun downloaderIsFeaturedUntilAToolIsUsed() {
        val ready = ToolRegistry.available
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, null).id)
        // A tool removed in a later version (or an "À venir" id) falls back to the downloader too.
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, "gone").id)
        assertEquals(ToolRegistry.DOWNLOADER, featuredTool(ready, "soon-36").id)
    }

    @Test fun lastUsedToolIsFeatured() {
        val ready = ToolRegistry.available
        assertEquals(ToolRegistry.CHAT_STATS, featuredTool(ready, ToolRegistry.CHAT_STATS).id)
        assertEquals(ToolRegistry.PREVIEW, featuredTool(ready, ToolRegistry.PREVIEW).id)
    }

    @Test fun everyToolHasACategory() {
        ToolRegistry.available.forEach { assertTrue(it.id, ToolCategories.of(it) != ToolCategory.OTHER) }
        assertEquals(ToolCategory.MEDIA, ToolCategories.of(ToolRegistry.find(ToolRegistry.DOWNLOADER)!!))
        assertEquals(ToolCategory.FILES, ToolCategories.of(ToolRegistry.find(ToolRegistry.PDF_JOIN)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.CHAT_STATS)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.DIRECT_CHAT)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.CHAT_READER)!!))
        assertEquals(ToolCategory.FILES, ToolCategories.of(ToolRegistry.find(ToolRegistry.RENAMER)!!))
        assertEquals(ToolCategory.MEDIA, ToolCategories.of(ToolRegistry.find(ToolRegistry.SLIDESHOW)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.QUICK_REPLIES)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.PROFILE_FIT)!!))
        assertEquals(ToolCategory.FILES, ToolCategories.of(ToolRegistry.find(ToolRegistry.SIGN_PDF)!!))
        assertEquals(ToolCategory.FILES, ToolCategories.of(ToolRegistry.find(ToolRegistry.WATERMARK)!!))
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(ToolRegistry.find(ToolRegistry.MY_LINK)!!))
        assertEquals(ToolCategory.HANDY, ToolCategories.of(ToolRegistry.find(ToolRegistry.PASSWORDS)!!))
        assertEquals(ToolCategory.HANDY, ToolCategories.of(ToolRegistry.find(ToolRegistry.TEXT_COUNTER)!!))
        assertEquals(ToolCategory.HANDY, ToolCategories.of(ToolRegistry.find(ToolRegistry.BILL_SPLIT)!!))
    }

    @Test fun newToolsAreFiledByTheirWordsElseInAutres() {
        val whatsapp = GulpTool(id = "new-thing", name = "Machin", tagline = "Range tes discussions WhatsApp.")
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(whatsapp))
        val stickers = GulpTool(id = "made-up", name = "Stickers", tagline = "Fais des autocollants.")
        assertEquals(ToolCategory.WHATSAPP, ToolCategories.of(stickers))
        val unknown = GulpTool(id = "mystery", name = "Boussole", tagline = "Indique le nord.")
        assertEquals(ToolCategory.OTHER, ToolCategories.of(unknown))
        assertEquals(listOf(ToolCategory.OTHER), ToolCategories.present(listOf(unknown)))
    }

    @Test fun searchIgnoresAccentsAndCaseAndMatchesEveryWord() {
        fun found(q: String) = ToolRegistry.available.filter { ToolCategories.matches(it, q) }.map { it.id }
        assertTrue(ToolRegistry.DOWNLOADER in found("TELECHARGEUR"))
        assertTrue(ToolRegistry.DOWNLOADER in found("tiktok"))
        assertTrue(ToolRegistry.PDF_JOIN in found("pdf"))
        assertTrue(ToolRegistry.SCANNER in found("pdf"))
        assertEquals(listOf(ToolRegistry.CHAT_STATS, ToolRegistry.QUICK_REPLIES), found("whatsapp réponse"))
        assertEquals(listOf(ToolRegistry.QUICK_REPLIES), found("message tout fait"))
        assertEquals(listOf(ToolRegistry.DIRECT_CHAT), found("numéro"))
        assertTrue(ToolRegistry.CHAT_READER in found("relire pdf"))
        assertTrue(ToolRegistry.PROFILE_FIT in found("photo de profil"))
        assertTrue(ToolRegistry.PROFILE_FIT in found("fond flou"))
        assertTrue(ToolRegistry.WATERMARK in found("filigrane"))
        assertTrue(ToolRegistry.MY_LINK in found("qr whatsapp"))
        assertTrue(found("karaoké").isEmpty())
        assertTrue(ToolRegistry.SLIDESHOW in found("diaporama musique"))
        assertTrue(ToolRegistry.PASSWORDS in found("mot de passe"))
        assertEquals(listOf(ToolRegistry.BILL_SPLIT), found("resto pourboire"))
        assertEquals(ToolRegistry.available.size, found("  ").size)
    }

    @Test fun resultsWording() {
        assertEquals("Aucun outil trouvé", resultsText(0))
        assertEquals("1 outil trouvé", resultsText(1))
        assertEquals("4 outils trouvés", resultsText(4))
    }
}
