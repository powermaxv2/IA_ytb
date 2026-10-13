package com.powermaxv2.ytdownloader.core.resize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizePlanTest {
    private val photo = Dims(4032, 3024)

    @Test fun cropKeepsTheCentre() {
        assertEquals(Box(504, 0, 3024, 3024), ResizePlan.cropBox(4032, 3024, CropRatio.SQUARE))
        assertEquals(Box(0, 0, 4032, 3024), ResizePlan.cropBox(4032, 3024, CropRatio.NONE))
        // A landscape photo into 16:9: top and bottom go.
        assertEquals(Box(0, 378, 4032, 2268), ResizePlan.cropBox(4032, 3024, CropRatio.WIDE))
        // A portrait photo into 4:5: top and bottom go.
        assertEquals(Box(0, 126, 3024, 3780), ResizePlan.cropBox(3024, 4032, CropRatio.PORTRAIT))
        // Story out of a landscape: a thin band from the middle.
        val story = ResizePlan.cropBox(4032, 3024, CropRatio.STORY)
        assertEquals(3024, story.height)
        assertEquals(1701, story.width)
        assertEquals((4032 - 1701) / 2, story.left)
    }

    @Test fun sideModeBringsTheLongestSideDown() {
        assertEquals(Dims(1920, 1440), ResizePlan.target(photo, ResizeSettings(side = SidePreset.FULL_HD)))
        assertEquals(Dims(1440, 1920), ResizePlan.target(Dims(3024, 4032), ResizeSettings(side = SidePreset.FULL_HD)))
        assertEquals(Dims(1080, 1080), ResizePlan.target(photo, ResizeSettings(side = SidePreset.POST, crop = CropRatio.SQUARE)))
        assertEquals(Dims(1536, 1920), ResizePlan.target(Dims(3024, 4032), ResizeSettings(side = SidePreset.FULL_HD, crop = CropRatio.PORTRAIT)))
    }

    @Test fun neverMadeBigger() {
        val small = Dims(800, 600)
        assertEquals(small, ResizePlan.target(small, ResizeSettings(side = SidePreset.UHD)))
        assertTrue(ResizePlan.unchanged(small, ResizeSettings(side = SidePreset.FULL_HD)))
        assertFalse(ResizePlan.unchanged(small, ResizeSettings(side = SidePreset.SMALL)))
        // A crop alone is a change even when the size stays under the preset.
        assertFalse(ResizePlan.unchanged(small, ResizeSettings(side = SidePreset.FULL_HD, crop = CropRatio.SQUARE)))
    }

    @Test fun percentMode() {
        assertEquals(Dims(2016, 1512), ResizePlan.target(photo, ResizeSettings(mode = ResizeMode.PERCENT, percent = PercentPreset.P50)))
        assertEquals(Dims(1008, 756), ResizePlan.target(photo, ResizeSettings(mode = ResizeMode.PERCENT, percent = PercentPreset.P25)))
    }

    @Test fun capsAtTheMaximumSide() {
        val huge = Dims(12000, 9000)
        assertEquals(Dims(4096, 3072), ResizePlan.target(huge, ResizeSettings(mode = ResizeMode.PERCENT, percent = PercentPreset.P75)))
        assertEquals(Dims(4096, 3072), ResizePlan.target(huge, ResizeSettings(mode = ResizeMode.WEIGHT)))
    }

    @Test fun weightModeStartsFromTheWholeCrop() {
        assertEquals(photo, ResizePlan.target(photo, ResizeSettings(mode = ResizeMode.WEIGHT)))
        assertEquals(Dims(3024, 3024), ResizePlan.target(photo, ResizeSettings(mode = ResizeMode.WEIGHT, crop = CropRatio.SQUARE)))
        assertFalse(ResizePlan.unchanged(photo, ResizeSettings(mode = ResizeMode.WEIGHT)))
    }

    @Test fun decodeJustBigEnough() {
        // Half the size: decode at half (plus a little), not the whole 4032 px.
        assertEquals(2018, ResizePlan.decodeSide(photo, 0.5))
        assertEquals(4032, ResizePlan.decodeSide(photo, 1.0))
        // A 50 MP photo kept whole is decoded under the pixel budget.
        val big = Dims(8160, 6120)
        val side = ResizePlan.decodeSide(big, 1.0)
        val h = side * 6120.0 / 8160
        assertTrue(side * h <= ResizePlan.MAX_DECODE_PIXELS)
        assertTrue(side > 5000)
    }

    @Test fun formats() {
        assertEquals(OutFormat.JPEG, ResizePlan.format("IMG.JPG", FormatChoice.SAME))
        assertEquals(OutFormat.PNG, ResizePlan.format("capture.png", FormatChoice.SAME))
        assertEquals(OutFormat.WEBP, ResizePlan.format("a.webp", FormatChoice.SAME))
        assertEquals(OutFormat.JPEG, ResizePlan.format("Vacances.HEIC", FormatChoice.SAME))
        assertEquals(OutFormat.WEBP, ResizePlan.format("logo.bmp", FormatChoice.SAME, hasAlpha = true))
        assertEquals(OutFormat.PNG, ResizePlan.format("IMG.JPG", FormatChoice.PNG))
        assertFalse(ResizePlan.lossy(OutFormat.PNG))
        assertTrue(ResizePlan.lossy(OutFormat.WEBP))
    }

    @Test fun names() {
        assertEquals("Vacances_1920x1440.jpg", ResizePlan.outputName("Vacances.HEIC", Dims(1920, 1440), OutFormat.JPEG))
        assertEquals("photo_10x10.png", ResizePlan.outputName("", Dims(10, 10), OutFormat.PNG))
        assertEquals("_etc_passwd_800x600.webp", ResizePlan.outputName("../etc/passwd", Dims(800, 600), OutFormat.WEBP))
        assertTrue(ResizePlan.outputName("a".repeat(300) + ".jpg", Dims(1, 1), OutFormat.JPEG).length <= 120)
    }

    /** A fake encoder: weight grows with pixels and quality, like a real one. */
    private fun weight(attempt: ResizePlan.Attempt): Long =
        (attempt.dims.width.toLong() * attempt.dims.height * (0.02 + attempt.quality / 100.0 * 0.3)).toLong()

    @Test fun weightSearchTakesTheBestQualityFirst() {
        val limit = 2_500_000L
        val (attempt, size) = ResizePlan.fit(Dims(4032, 3024), lossy = true, limit = limit, encode = ::weight)
        assertTrue(size <= limit)
        // Full size fits at some quality: no shrink, the highest quality that fits.
        assertEquals(Dims(4032, 3024), attempt.dims)
        assertTrue(weight(attempt.copy(quality = attempt.quality + 1)) > limit)
    }

    @Test fun weightSearchShrinksWhenNoQualityFits() {
        val limit = 100_000L
        val (attempt, size) = ResizePlan.fit(Dims(4032, 3024), lossy = true, limit = limit, encode = ::weight)
        assertTrue(size <= limit)
        assertTrue(attempt.dims.width < 4032)
        // Proportions kept.
        assertEquals(4032.0 / 3024, attempt.dims.width.toDouble() / attempt.dims.height, 0.01)
    }

    @Test fun pngOnlyShrinks() {
        val tries = ArrayList<ResizePlan.Attempt>()
        val (attempt, size) = ResizePlan.fit(Dims(2000, 1000), lossy = false, limit = 200_000L) { tries += it; it.dims.width.toLong() * it.dims.height / 2 }
        assertTrue(size <= 200_000L)
        assertTrue(tries.all { it.quality == 100 })
        assertTrue(attempt.dims.width < 2000)
    }

    @Test fun impossibleGoalEndsOnTheSmallestTry() {
        val (attempt, size) = ResizePlan.fit(Dims(400, 300), lossy = true, limit = 10L) { 1_000L }
        assertTrue(size > 10L)
        assertTrue(attempt.dims.longest <= ResizePlan.MIN_SIDE)
    }

    @Test fun gain() {
        assertEquals(75, ResizePlan.gainPercent(4_000_000, 1_000_000))
        assertEquals(0, ResizePlan.gainPercent(1_000, 2_000))
        assertEquals(0, ResizePlan.gainPercent(0, 0))
        assertEquals(1_000_000L * 97 / 100 - 300, ResizePlan.weightLimit(WeightGoal.MB1, 300))
    }
}
