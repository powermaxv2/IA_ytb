package com.powermaxv2.ytdownloader.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class WorkDirTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun root(): File = File(temp.root, "gulp-work").apply { mkdirs() }

    @Test fun filesInsideTheRoot() {
        val root = root()
        val job = File(root, "abc").apply { mkdirs() }
        assertTrue(WorkDir.isInside(root, job))
        assertTrue(WorkDir.isInside(root, File(job, "photo.jpg")))
        assertTrue(WorkDir.isInside(root, File(root, "abc/../def/x.png")))
    }

    @Test fun rootItselfAndTraversalAreOutside() {
        val root = root()
        assertFalse(WorkDir.isInside(root, root))
        assertFalse(WorkDir.isInside(root, File(root, "abc/../..")))
        assertFalse(WorkDir.isInside(root, File(root, "../secret.txt")))
        assertFalse(WorkDir.isInside(root, File(root, "abc/../../shared_prefs/prefs.xml")))
        assertFalse(WorkDir.isInside(root, temp.root))
    }

    @Test fun siblingWithTheSamePrefixIsOutside() {
        val root = root()
        val sibling = File(temp.root, "gulp-work2").apply { mkdirs() }
        assertFalse(WorkDir.isInside(root, File(sibling, "x.txt")))
        assertFalse(WorkDir.isInside(root, sibling))
    }

    @Test fun symlinkLeavingTheRootIsOutside() {
        val root = root()
        val outside = temp.newFolder("outside")
        val link = File(root, "link")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        if (!created) return // file system without symlinks: nothing to check
        assertFalse(WorkDir.isInside(root, File(link, "file.txt")))
        assertFalse(WorkDir.isInside(root, link))
    }

    @Test fun uniqueFileNeverReusesAName() {
        val dir = temp.newFolder("job")
        val existing = File(dir, "photo.jpg").apply { writeText("first") }
        val second = FileIntake.uniqueFile(dir, "photo.jpg")
        assertNotEquals(existing, second)
        assertEquals("photo (2).jpg", second.name)
        assertEquals("first", existing.readText())
        assertEquals("photo (3).jpg", FileIntake.uniqueFile(dir, "photo.jpg").name)
        assertEquals("README", FileIntake.uniqueFile(dir, "README").name)
        assertEquals("README (2)", FileIntake.uniqueFile(dir, "README").name)
        val long = "z".repeat(116) + ".jpg"
        File(dir, long).createNewFile()
        val longCopy = FileIntake.uniqueFile(dir, long)
        assertTrue(longCopy.name.length <= SafeNames.MAX_LENGTH)
        assertTrue(longCopy.name.endsWith(" (2).jpg"))
    }
}
