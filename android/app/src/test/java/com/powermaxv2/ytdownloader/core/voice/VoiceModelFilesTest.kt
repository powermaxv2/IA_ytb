package com.powermaxv2.ytdownloader.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelFilesTest {
    @Test fun keepsOnlyTheModelsOwnFiles() {
        assertEquals("am/final.mdl", VoiceModelFiles.target("vosk-model-small-fr-0.22/am/final.mdl"))
        assertEquals("graph/phones/word_boundary.int", VoiceModelFiles.target("vosk-model-small-fr-0.22/graph/phones/word_boundary.int"))
        assertNull(VoiceModelFiles.target("vosk-model-small-fr-0.22/"))
        assertNull(VoiceModelFiles.target("vosk-model-small-fr-0.22/am/"))
        assertNull(VoiceModelFiles.target("other/am/final.mdl"))
        assertNull(VoiceModelFiles.target("vosk-model-small-fr-0.22/../../shared_prefs/x.xml"))
        assertNull(VoiceModelFiles.target("vosk-model-small-fr-0.22/am/../../x"))
        assertNull(VoiceModelFiles.target("vosk-model-small-fr-0.22//x"))
        assertNull(VoiceModelFiles.target("/vosk-model-small-fr-0.22/x"))
    }

    @Test fun completeNeedsEveryRequiredFile() {
        assertTrue(VoiceModelFiles.complete(VoiceModelFiles.REQUIRED + "README"))
        assertFalse(VoiceModelFiles.complete(VoiceModelFiles.REQUIRED.drop(1)))
    }

    @Test fun pinnedDownload() {
        assertTrue(VoiceModelFiles.URL.startsWith("https://"))
        assertEquals(64, VoiceModelFiles.ZIP_SHA256.length)
        assertEquals("42 Mo", VoiceModelFiles.downloadLabel)
        assertEquals("00ff10", VoiceModelFiles.hex(byteArrayOf(0, -1, 16)))
    }

    @Test fun ffmpegDecodeArgs() {
        val root = "/data/cache/gulp-work"
        val args = VoiceArgs.decode(root, "$root/a/input.opus", "$root/a/voice.pcm")
        assertEquals("$root/a/voice.pcm", args.last())
        assertTrue(args.containsAll(listOf("-ar", "16000", "-ac", "1", "s16le", "-format_whitelist")))
        assertEquals("file,pipe", args[args.indexOf("-protocol_whitelist") + 1])
        assertThrows(IllegalArgumentException::class.java) { VoiceArgs.decode(root, "/sdcard/input.opus", "$root/a/voice.pcm") }
        assertThrows(IllegalArgumentException::class.java) { VoiceArgs.decode(root, "$root/a/x", "$root/a/x") }
    }
}
