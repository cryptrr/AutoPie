package com.autopi

import com.autopi.autopieapp.data.services.isRepositoryFileStale
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.autopi.autopieapp.domain.model.matchesSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoryDiscoveryTest {

    @Test
    fun `missing empty and old repository files are stale`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-repolist-${System.nanoTime()}.json")
        assertTrue(isRepositoryFileStale(missing, maxAgeMillis = 1_000L, nowMillis = 2_000L))

        val repository = File.createTempFile("repolist", ".json")
        try {
            assertTrue(isRepositoryFileStale(repository, maxAgeMillis = 1_000L, nowMillis = 2_000L))

            repository.writeText("{}")
            repository.setLastModified(500L)
            assertTrue(isRepositoryFileStale(repository, maxAgeMillis = 1_000L, nowMillis = 2_000L))

            repository.setLastModified(1_500L)
            assertFalse(isRepositoryFileStale(repository, maxAgeMillis = 1_000L, nowMillis = 2_000L))
        } finally {
            repository.delete()
        }
    }

    @Test
    fun `catalog search includes metadata that installed command search does not expose`() {
        val command = CloudCommandModel(
            id = "media.convert.video",
            name = "Convert video",
            namespace = "media",
            summary = "Transcode a video file",
            tags = listOf("ffmpeg", "encoding"),
            version = "2.0"
        )

        assertTrue(command.matchesSearch("ffmpeg"))
        assertTrue(command.matchesSearch("transcode"))
        assertTrue(command.matchesSearch("media.convert"))
        assertFalse(command.matchesSearch("archive"))
    }
}
