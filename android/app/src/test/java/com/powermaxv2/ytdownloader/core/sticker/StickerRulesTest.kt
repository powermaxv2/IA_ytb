package com.powermaxv2.ytdownloader.core.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class StickerRulesTest {
    @Test fun aPackAlwaysHasAtLeastThreeStickers() {
        assertEquals(
            listOf(0 to StickerVariant.NORMAL, 0 to StickerVariant.MIRROR, 0 to StickerVariant.TILT),
            StickerRules.plan(1, hasText = false, animated = false),
        )
        assertEquals(StickerVariant.NO_TEXT, StickerRules.plan(1, hasText = true, animated = false)[2].second)
        assertEquals(StickerVariant.REVERSE, StickerRules.plan(1, hasText = false, animated = true)[2].second)
        assertEquals(
            listOf(0 to StickerVariant.NORMAL, 1 to StickerVariant.NORMAL, 0 to StickerVariant.MIRROR),
            StickerRules.plan(2, hasText = false, animated = false),
        )
        assertEquals((0 until 7).map { it to StickerVariant.NORMAL }, StickerRules.plan(7, hasText = true, animated = false))
        assertEquals(30, StickerRules.plan(30, hasText = false, animated = false).size)
        assertTrue(runCatching { StickerRules.plan(31, false, false) }.isFailure)
        assertTrue(runCatching { StickerRules.plan(0, false, false) }.isFailure)
    }

    @Test fun framesAreThinnedEvenly() {
        assertEquals((0 until 36).toList(), StickerRules.subsample(36, 12, 12))
        assertEquals(listOf(0, 2, 4, 6, 8, 10), StickerRules.subsample(12, 12, 6))
        assertEquals(24, StickerRules.subsample(36, 12, 8).size)
        assertEquals(listOf(0), StickerRules.subsample(1, 12, 4))
        assertEquals(emptyList<Int>(), StickerRules.subsample(0, 12, 4))
        assertTrue(StickerRules.subsample(36, 12, 5).all { it in 0 until 36 })
    }

    @Test fun boomerangAndReverse() {
        assertEquals(listOf(0, 1, 2, 3, 2, 1), StickerRules.order(listOf(0, 1, 2, 3), boomerang = true, reverse = false))
        assertEquals(listOf(3, 2, 1, 0), StickerRules.order(listOf(0, 1, 2, 3), boomerang = false, reverse = true))
        assertEquals(listOf(0, 1), StickerRules.order(listOf(0, 1), boomerang = true, reverse = false))
    }

    @Test fun animationsStayInsideWhatsAppLimits() {
        assertEquals(83, StickerRules.frameMs(12))
        assertEquals(250, StickerRules.frameMs(4))
        assertTrue(StickerRules.frameMs(1000) >= StickerRules.MIN_FRAME_MS)
        // The longest clip, forth and back, at the highest rate, still lasts under 10 s.
        val frames = StickerRules.order(StickerRules.subsample(37, 12, 12), boomerang = true, reverse = false).size
        assertTrue(frames * StickerRules.frameMs(12) <= StickerRules.MAX_ANIMATED_MS)
        assertEquals(12 to 70, StickerRules.ANIMATED_LADDER.first())
        assertTrue(StickerRules.fits(100 * 1024, animated = false))
        assertFalse(StickerRules.fits(100 * 1024 + 1, animated = false))
        assertTrue(StickerRules.fits(500 * 1024, animated = true))
    }

    @Test fun clipsStayInsideTheFile() {
        assertEquals(StickerClip(0, 3_000), StickerRules.clamp(StickerClip(0, 3_000), 60_000))
        assertEquals(StickerClip(0, 1_500), StickerRules.clamp(StickerClip(0, 3_000), 1_500))
        assertEquals(StickerClip(57_000, 3_000), StickerRules.clamp(StickerClip(59_000, 3_000), 60_000))
        assertEquals(StickerClip(0, 3_000), StickerRules.clamp(StickerClip(-5, 9_000), 60_000))
        assertEquals(200, StickerRules.clamp(StickerClip(0, 10), 60_000).lengthMs)
    }

    @Test fun namesAndIds() {
        assertEquals(StickerRules.DEFAULT_NAME, StickerRules.packName("   "))
        assertEquals("Les potes du lycée", StickerRules.packName("  Les potes\n du   lycée "))
        assertEquals(128, StickerRules.packName("a".repeat(300)).length)
        val id = StickerRules.packId(1_760_000_000_000L, -42)
        assertTrue(id, StickerRules.isPackId(id))
        assertFalse(StickerRules.isPackId("../gulp_x"))
        assertFalse(StickerRules.isPackId(".tmp-gulp_abc"))
        assertFalse(StickerRules.isPackId("gulp_é1"))
        assertFalse(StickerRules.isPackId(null))
        assertEquals("01.webp", StickerRules.stickerFile(0))
        assertEquals("30.webp", StickerRules.stickerFile(29))
        assertTrue(StickerRules.isAssetName("07.webp"))
        assertTrue(StickerRules.isAssetName("tray.png"))
        assertFalse(StickerRules.isAssetName("../pack.json"))
        assertFalse(StickerRules.isAssetName("pack.json"))
        assertEquals("3 stickers", StickerRules.countText(3, animated = false))
        assertEquals("1 sticker animé", StickerRules.countText(1, animated = true))
        assertEquals("45 Ko", StickerRules.kiloText(45 * 1024))
    }

    @Test fun gifLength() {
        // 4 × 4, 3 frames of 50 ms, 10 ms (shown as 100 ms, like browsers) and 200 ms; made with Pillow.
        val gif = Base64.getDecoder().decode(
            "R0lGODlhBAAEAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQABQAAACwAAAAABAAEAAAICQABCBxIsCCAgAAh+QQBAQABACwAAAAABAAEAIEA/wAAAAAAAAAAAAAICQABCBxIsCCAgAAh+QQBFAABACwAAAAABAAEAIEAAP8AAAAAAAAAAAAICQABCBxIsCCAgAA7",
        )
        assertEquals(GifInfo.Info(4, 4, 3, 350), GifInfo.read(gif))
        assertNull(GifInfo.read("GIF89a".toByteArray()))
        assertNull(GifInfo.read(ByteArray(40)))
    }

    @Test fun magicWandTakesThePlainBackgroundOnly() {
        // 6 × 6: white background, a red 2 × 2 subject in the middle, and a near-white pixel (still background).
        val w = 6
        val white = 0xFFFFFFFF.toInt()
        val red = 0xFFE01010.toInt()
        val pixels = IntArray(36) { white }
        listOf(14, 15, 20, 21).forEach { pixels[it] = red }
        pixels[0] = 0xFFF4F4F4.toInt()
        val removed = Cutout.mask(pixels, w, w)
        assertEquals(32, removed.count { it })
        listOf(14, 15, 20, 21).forEach { assertFalse(removed[it]) }

        Cutout.apply(pixels, w, w, removed)
        assertEquals(0, pixels[0])
        // The subject's edge is softened (half alpha), its colour kept.
        assertEquals(0x7F, pixels[14] ushr 24)
        assertEquals(red and 0xFFFFFF, pixels[14] and 0xFFFFFF)
    }

    @Test fun magicWandFromATouchOnly() {
        // Left half blue, right half green: a touch on the right removes only the right.
        val w = 4
        val pixels = IntArray(16) { i -> if (i % w < 2) 0xFF0000FF.toInt() else 0xFF00FF00.toInt() }
        val removed = Cutout.mask(pixels, w, w, fromEdges = false, seeds = listOf(3 to 0))
        assertEquals(8, removed.count { it })
        assertTrue(removed[3])
        assertFalse(removed[0])
        assertEquals(0.5f, Cutout.share(removed))
        // Out-of-picture touches are ignored.
        assertEquals(0, Cutout.mask(pixels, w, w, fromEdges = false, seeds = listOf(9 to 9)).count { it })
    }

    @Test fun colourCloseness() {
        assertTrue(Cutout.close(0xFF808080.toInt(), 0xFF8A7A80.toInt(), 10))
        assertFalse(Cutout.close(0xFF808080.toInt(), 0xFF809580.toInt(), 10))
        assertTrue(Cutout.close(0x00FF0000, 0x00000000, 0))
        assertFalse(Cutout.close(0x00000000, 0xFF000000.toInt(), 40))
    }

    @Test fun touchesLandOnTheSourcePicture() {
        val plain = StickerStyle(shape = StickerShape.SQUARE, outline = StickerOutline.NONE)
        assertEquals(0, StickerRules.margin(plain))
        assertEquals(0.25f to 0.5f, StickerRules.toSource(plain, StickerVariant.NORMAL, 0.25f, 0.5f))
        // Mirrored: the left of the sticker is the right of the photo.
        assertEquals(0.75f to 0.5f, StickerRules.toSource(plain, StickerVariant.MIRROR, 0.25f, 0.5f))
        assertEquals(0.25f to 0.5f, StickerRules.toSource(plain.copy(mirror = true), StickerVariant.MIRROR, 0.25f, 0.5f))
        // With an outline, the very edge is the white border, not the photo.
        val outlined = StickerStyle(outline = StickerOutline.THICK)
        assertEquals(17, StickerRules.margin(outlined))
        assertNull(StickerRules.toSource(outlined, StickerVariant.NORMAL, 0.01f, 0.5f))
        assertNull(StickerRules.toSource(outlined, StickerVariant.TILT, 0.5f, 0.5f))
        val (u, v) = StickerRules.toSource(outlined, StickerVariant.NORMAL, 0.5f, 0.5f)!!
        assertEquals(0.5f, u, 0.001f)
        assertEquals(0.5f, v, 0.001f)
    }
}
