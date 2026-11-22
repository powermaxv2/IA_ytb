package com.powermaxv2.ytdownloader.core.files

import java.io.File

/**
 * Measures and clears Gulp's temporary files inside `cacheDir` (tool work copies, image thumbnails, old updates).
 * Never touches `filesDir`, so the Vault, voice memos, sticker packs, timers and installed models stay intact.
 * Pure JVM file logic: unit tested in CacheCleanerTest.
 */
object CacheCleaner {
    private const val JOBS_DIR = "jobs"

    /** Total size in bytes of temporary files inside [cacheDir]; skips active [JOBS_DIR] when [skipJobs] is true. */
    fun measureBytes(cacheDir: File, skipJobs: Boolean = false): Long {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return 0L
        return cacheDir.listFiles().orEmpty().sumOf { child ->
            if (skipJobs && child.name == JOBS_DIR) 0L else sizeOf(child)
        }
    }

    /**
     * Deletes temporary entries inside [cacheDir] without removing [cacheDir] itself; skips [JOBS_DIR] when
     * [skipJobs] is true (downloads running). Returns the approximate number of bytes freed.
     */
    fun clear(cacheDir: File, skipJobs: Boolean = false): Long {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return 0L
        val before = measureBytes(cacheDir, skipJobs)
        cacheDir.listFiles().orEmpty().forEach { child ->
            if (skipJobs && child.name == JOBS_DIR) return@forEach
            child.deleteRecursively()
        }
        val after = measureBytes(cacheDir, skipJobs)
        return (before - after).coerceAtLeast(0L)
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(file)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            dir.listFiles().orEmpty().forEach { entry ->
                if (entry.isDirectory) stack.add(entry) else total += entry.length()
            }
        }
        return total
    }
}
