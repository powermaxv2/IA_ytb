package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheCleanerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test fun measuresAndClearsNestedCacheWhileKeepingRoot() {
        val cache = temp.newFolder("cache")
        val work = File(cache, "gulp-work/sub").apply { mkdirs() }
        File(work, "a.tmp").writeBytes(ByteArray(1200))
        File(cache, "image_cache").apply { mkdirs() }.let { File(it, "thumb.jpg").writeBytes(ByteArray(800)) }

        assertEquals(2000L, CacheCleaner.measureBytes(cache))
        val freed = CacheCleaner.clear(cache)
        assertEquals(2000L, freed)
        assertEquals(0L, CacheCleaner.measureBytes(cache))
        assertTrue(cache.exists())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun skipsActiveJobsDirectoryWhenRequested() {
        val cache = temp.newFolder("cache-jobs")
        val jobFile = File(cache, "jobs/job-1/part.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(5000))
        }
        val scratch = File(cache, "gulp-work/temp.pdf").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(1500))
        }

        assertEquals(1500L, CacheCleaner.measureBytes(cache, skipJobs = true))
        val freed = CacheCleaner.clear(cache, skipJobs = true)
        assertEquals(1500L, freed)
        assertFalse(scratch.exists())
        assertTrue(jobFile.exists())
        assertEquals(5000L, CacheCleaner.measureBytes(cache, skipJobs = false))
    }

    @Test fun emptyOrMissingDirReturnsZero() {
        val missing = File(temp.root, "does-not-exist")
        assertEquals(0L, CacheCleaner.measureBytes(missing))
        assertEquals(0L, CacheCleaner.clear(missing))
    }
}
