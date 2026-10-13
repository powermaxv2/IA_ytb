package com.powermaxv2.ytdownloader.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VaultStoreTest {
    private lateinit var root: File
    private lateinit var store: VaultStore

    // Few rounds: the tests check the format, not the slowness.
    private val fast = 1_000

    @Before fun setUp() {
        root = Files.createTempDirectory("vault-test").toFile()
        store = VaultStore(File(root, "vault"))
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun bytes(n: Int, seed: Int = 7) = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

    @Test fun pbkdf2WrittenOutMatchesTheBuiltInOne() {
        val salt = "salt".toByteArray()
        val builtIn = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("passwd".toCharArray(), salt, 1, 64 * 8)).encoded
        assertArrayEquals(builtIn, VaultCrypto.pbkdf2("passwd".toByteArray(), salt, 1, 64))
        // RFC 7914 §11, first bytes.
        assertEquals("55ac046e56e3089f", VaultCrypto.pbkdf2("passwd".toByteArray(), salt, 1, 64).take(8).joinToString("") { "%02x".format(it) })
        val many = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("482916".toCharArray(), salt, 2_000, 256)).encoded
        assertArrayEquals(many, VaultCrypto.pbkdf2("482916".toByteArray(), salt, 2_000, 32))
        assertArrayEquals(many, VaultCrypto.deriveKey("482916", salt, 2_000))
    }

    @Test fun streamsGoRoundTripAtEverySize() {
        val key = VaultCrypto.randomBytes(32)
        listOf(0, 1, 15, 16, 100, 1024, 1025, 4096, 10_000).forEach { size ->
            val plain = bytes(size)
            val sealed = ByteArrayOutputStream()
            assertEquals(size.toLong(), VaultCrypto.encryptStream(key, "abc", ByteArrayInputStream(plain), sealed, chunk = 1024))
            assertFalse(size > 32 && sealed.toByteArray().toList().windowed(32).contains(plain.take(32)))
            val back = VaultCrypto.decryptAll(key, "abc", ByteArrayInputStream(sealed.toByteArray()))
            assertArrayEquals("size $size", plain, back)
        }
    }

    @Test fun aFileCutShortOrMovedDoesNotOpen() {
        val key = VaultCrypto.randomBytes(32)
        val sealed = ByteArrayOutputStream().also { VaultCrypto.encryptStream(key, "abc", ByteArrayInputStream(bytes(4096)), it, chunk = 1024) }.toByteArray()
        // Exactly one chunk less: every remaining chunk is intact, but the last one is not flagged last.
        val oneChunk = 1024 + VaultCrypto.TAG_BYTES
        val cut = sealed.copyOf(sealed.size - oneChunk)
        expectCorrupt { VaultCrypto.decryptAll(key, "abc", ByteArrayInputStream(cut)) }
        // Same bytes under another file's id.
        expectCorrupt { VaultCrypto.decryptAll(key, "abd", ByteArrayInputStream(sealed)) }
        // One bit flipped.
        val flipped = sealed.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        expectCorrupt { VaultCrypto.decryptAll(key, "abc", ByteArrayInputStream(flipped)) }
        // Another key.
        expectCorrupt { VaultCrypto.decryptAll(VaultCrypto.randomBytes(32), "abc", ByteArrayInputStream(sealed)) }
    }

    @Test fun createUnlockAndWrongCode() {
        assertFalse(store.exists)
        store.create("482916", fast)
        assertTrue(store.exists)
        assertTrue(store.items(store.unlock("482916")).isEmpty())
        try {
            store.unlock("482917")
            fail("wrong code opened the vault")
        } catch (_: WrongCodeException) {
        }
        try {
            store.create("111111", fast)
            fail("second vault over the first")
        } catch (_: IllegalStateException) {
        }
    }

    @Test fun filesGoInAndComeBackOut() {
        val key = store.create("482916", fast)
        val photo = bytes(200_000)
        val item = store.add(key, "DCIM/IMG_0042.jpg", "image/jpeg", ByteArrayInputStream(photo), thumb = bytes(500, 3), now = 1_000L)
        val note = store.add(key, "note.txt", "text/plain", ByteArrayInputStream("secret".toByteArray()), now = 2_000L)
        assertEquals("IMG_0042.jpg", item.name)
        assertEquals(200_000L, item.size)
        assertEquals(VaultKind.PHOTO, item.kind)
        assertEquals(VaultKind.TEXT, note.kind)

        // Locked again, opened with the code: same list, same bytes.
        key.wipe()
        val again = store.unlock("482916")
        val items = store.items(again)
        assertEquals(listOf(item, note), items)
        assertArrayEquals(photo, store.open(again, items[0]).use { it.readBytes() })
        assertArrayEquals(bytes(500, 3), store.thumb(again, items[0]))
        assertNull(store.thumb(again, items[1]))
        assertEquals("secret", String(store.readAll(again, items[1], 1_000)))

        // Nothing readable on disk: no name, no content.
        val disk = store.dir.walkTopDown().filter { it.isFile }.map { it.readBytes().toString(Charsets.ISO_8859_1) }.joinToString()
        assertFalse(disk.contains("IMG_0042"))
        assertFalse(disk.contains("secret"))
        assertFalse(disk.contains("image/jpeg"))
    }

    @Test fun removeAndOrphans() {
        val key = store.create("482916", fast)
        val a = store.add(key, "a.jpg", "image/jpeg", ByteArrayInputStream(bytes(10)), thumb = bytes(5))
        val b = store.add(key, "b.pdf", "application/pdf", ByteArrayInputStream(bytes(10)))
        store.remove(key, setOf(a.id))
        assertEquals(listOf(b), store.items(key))
        assertEquals(1, File(store.dir, "items").listFiles()!!.size)
        File(store.dir, "items/0123456789abcdef0123456789abcdef.bin").writeBytes(bytes(3))
        assertEquals(1, store.cleanOrphans(key))
        assertEquals(listOf(b), store.items(key))
    }

    @Test fun newCodeKeepsTheFiles() {
        val key = store.create("482916", fast)
        store.add(key, "a.txt", "text/plain", ByteArrayInputStream("hello".toByteArray()))
        store.changeCode(key, "73195004", fast)
        expectWrong { store.unlock("482916") }
        val again = store.unlock("73195004")
        assertEquals("hello", String(store.readAll(again, store.items(again).single(), 100)))
    }

    @Test fun rawKeyUnlocksOnlyItsVault() {
        val key = store.create("482916", fast)
        assertEquals(0, store.items(store.unlockRaw(key.export())).size)
        expectWrong { store.unlockRaw(VaultCrypto.randomBytes(32)) }
    }

    @Test fun destroyLeavesNothing() {
        val key = store.create("482916", fast)
        store.add(key, "a.txt", "text/plain", ByteArrayInputStream("hello".toByteArray()))
        store.destroy()
        assertFalse(store.exists)
        assertFalse(store.dir.exists())
    }

    @Test fun codeRules() {
        assertTrue(VaultCode.isValid("482916"))
        assertTrue(VaultCode.isValid("482916482916"))
        assertFalse(VaultCode.isValid("48291"))
        assertFalse(VaultCode.isValid("4829164829164"))
        assertFalse(VaultCode.isValid("48a916"))
        assertTrue(VaultCode.isWeak("000000"))
        assertTrue(VaultCode.isWeak("123456"))
        assertTrue(VaultCode.isWeak("987654"))
        assertFalse(VaultCode.isWeak("482916"))
        assertFalse(VaultCode.isWeak("121212"))
    }

    @Test fun waitGrowsAfterFourWrongCodes() {
        assertEquals(0L, VaultThrottle.waitMillis(0))
        assertEquals(0L, VaultThrottle.waitMillis(4))
        assertEquals(30_000L, VaultThrottle.waitMillis(5))
        assertEquals(60_000L, VaultThrottle.waitMillis(6))
        assertEquals(120_000L, VaultThrottle.waitMillis(7))
        assertEquals(15 * 60_000L, VaultThrottle.waitMillis(40))
    }

    @Test fun namesStayShortAndFlat() {
        assertEquals("photo.jpg", VaultStore.cleanName("/storage/emulated/0/DCIM/photo.jpg"))
        assertEquals("fichier", VaultStore.cleanName("  "))
        assertEquals(120, VaultStore.cleanName("a".repeat(300) + ".jpg").length)
        assertTrue(VaultStore.cleanName("a".repeat(300) + ".jpg").endsWith(".jpg"))
    }

    @Test fun kinds() {
        assertEquals(VaultKind.VIDEO, VaultKind.of("video/mp4"))
        assertEquals(VaultKind.AUDIO, VaultKind.of("audio/ogg"))
        assertEquals(VaultKind.PDF, VaultKind.of("application/pdf"))
        assertEquals(VaultKind.OTHER, VaultKind.of("application/zip"))
    }

    private fun expectCorrupt(block: () -> Unit) {
        try {
            block()
            fail("opened")
        } catch (_: VaultCorruptException) {
        }
    }

    private fun expectWrong(block: () -> Unit) {
        try {
            block()
            fail("opened")
        } catch (_: WrongCodeException) {
        }
    }
}
