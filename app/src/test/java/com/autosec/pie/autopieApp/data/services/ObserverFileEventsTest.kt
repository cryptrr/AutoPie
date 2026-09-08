package com.autopi.autopieapp.data.services

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ObserverFileEventsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun newFileRunsOnlyAfterCloseAndOnlyOnce() {
        val events = ObserverFileEvents(emptyList())
        assertNull(events.onEvent(0x100, "video.mp4"))
        assertNull(events.onEvent(0x2, "video.mp4"))
        assertEquals("video.mp4", events.onEvent(0x8, "video.mp4"))
        assertNull(events.onEvent(0x8, "video.mp4"))
        assertNull(events.onEvent(0x8, "existing.mp4"))
    }

    @Test fun pendingRenameUsesFinalNameAndSelectors() {
        val events = ObserverFileEvents(listOf(".*\\.mp4"))
        assertNull(events.onEvent(0x100, ".pending"))
        assertNull(events.onEvent(0x8, ".pending"))
        assertEquals("my-video.mp4", events.onEvent(0x80, "my-video.mp4"))
        assertNull(events.onEvent(0x8, "my-video.mp4"))
        assertNull(events.onEvent(0x80, "my-video.txt"))
        assertNull(events.onEvent(0x80, "my-video.conv.mp4"))
    }

    @Test fun deletedAndMovedAwayFilesAreForgotten() {
        for (event in listOf(0x200, 0x40)) {
            val events = ObserverFileEvents(emptyList())
            events.onEvent(0x100, "file")
            assertNull(events.onEvent(event, "file"))
            assertNull(events.onEvent(0x8, "file"))
            events.onEvent(0x100, "file")
            assertEquals("file", events.onEvent(0x8, "file"))
        }
    }

    @Test fun rejectsDirectoriesInvalidPathsAndLateCallbacks() {
        val events = ObserverFileEvents(emptyList())
        assertNull(events.onEvent(0x40000080, "directory"))
        for (name in listOf(null, "", "..", "../outside", "/absolute", "sub/file")) {
            assertNull(events.onEvent(0x80, name))
        }
        events.onEvent(0x100, "file")
        events.close()
        assertNull(events.onEvent(0x8, "file"))
        assertNull(events.onEvent(0x80, "another"))
    }

    @Test(expected = java.util.regex.PatternSyntaxException::class)
    fun invalidSelectorRejectsObserverAtSetup() {
        ObserverFileEvents(listOf("["))
    }

    @Test fun resolvesActualFilesWithinWatchedDirectory() {
        val directory = temp.newFolder("watched").canonicalFile
        val file = File(directory, "spaces and-dashes.txt").apply { writeText("complete") }
        assertEquals(file, resolveObservedFile(directory, file.name))
        assertNull(resolveObservedFile(directory, "missing"))
        assertNull(resolveObservedFile(directory, "../outside"))
        File(directory, "subdirectory").mkdir()
        assertNull(resolveObservedFile(directory, "subdirectory"))
        val outside = temp.newFile("outside")
        Files.createSymbolicLink(File(directory, "link").toPath(), outside.toPath())
        assertNull(resolveObservedFile(directory, "link"))
    }

    @Test fun resolvesRelativeWatchDirectoriesFromExternalStorageAndPreservesAbsolutePaths() {
        val externalStorage = temp.newFolder("external-storage").canonicalFile
        val absolute = temp.newFolder("elsewhere").canonicalFile
        assertEquals(
            File(externalStorage, "Pictures/Screenshots"),
            resolveObserverDirectory(externalStorage, "Pictures/Screenshots")
        )
        assertEquals(absolute, resolveObserverDirectory(externalStorage, absolute.path))
    }
}
