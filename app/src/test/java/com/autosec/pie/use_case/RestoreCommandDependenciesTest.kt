package com.autopi.use_case

import com.autopi.autopieapp.data.CommandsRepositoryChannel
import com.autopi.autopieapp.data.services.MissingTermuxDependencies
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreCommandDependenciesTest {
    @Test
    fun `plan preserves config and collects references while tolerating failed recipes`() = runTest {
        val manager = mockk<ProcessManagerService>()
        coEvery { manager.findMissingTermuxDependencies(listOf("ffmpeg"), listOf("yt-dlp")) } returns
            MissingTermuxDependencies(listOf("ffmpeg"), listOf("yt-dlp"))
        val commands = JsonParser.parseString("""{
            "Edited": {"id":"catalog.one", "command":"my custom command"},
            "Workflow": {"id":"local.flow", "steps":[
                {"commandId":"catalog.two"}, {"commandId":"catalog.one"}, {"commandId":"catalog.broken"}
            ]},
            "Legacy": {"command":"echo hello"}
        }""").asJsonObject
        val original = commands.toString()
        val restore = RestoreCommandDependencies(manager) { url ->
            val id = when {
                url.contains("catalog/one/") -> "catalog.one"
                url.contains("catalog/two/") -> "catalog.two"
                else -> error("unavailable")
            }
            """
                id: $id
                name: Example
                runtime:
                  command: echo replaced
                install:
                  dependencies:
                    pkg: [ffmpeg]
                    pip: [yt-dlp]
            """.trimIndent()
        }
        val plan = restore.plan(commands, setOf("catalog.one", "catalog.two", "catalog.broken"), CommandsRepositoryChannel.MAIN)
        assertEquals(listOf("catalog.broken"), plan.failedIds)
        assertEquals(listOf("Legacy", "local.flow"), plan.unresolved)
        assertEquals(listOf("ffmpeg"), plan.missingPkg)
        assertEquals(listOf("yt-dlp"), plan.missingPip)
        assertEquals(original, commands.toString())
    }

    @Test
    fun `installation continues after failure and retries only remaining packages`() = runTest {
        val manager = mockk<ProcessManagerService>()
        coEvery { manager.findMissingTermuxDependencies(any(), any()) } returnsMany listOf(
            MissingTermuxDependencies(listOf("ffmpeg"), listOf("yt-dlp")),
            MissingTermuxDependencies(pip = listOf("yt-dlp")),
            MissingTermuxDependencies(pip = listOf("yt-dlp")),
            MissingTermuxDependencies()
        )
        coEvery { manager.installRestoreDependency("pkg", "ffmpeg") } returns true
        coEvery { manager.installRestoreDependency("pip", "yt-dlp") } throws IllegalStateException("offline")
        val restore = RestoreCommandDependencies(manager)
        val plan = DependencyRestorePlan(emptyList(), emptyList(), emptyList(), listOf("ffmpeg"), listOf("yt-dlp"), listOf("ffmpeg"), listOf("yt-dlp"))
        val result = restore.install(plan) {}
        assertEquals(1, result.missingCount)
        coEvery { manager.installRestoreDependency("pip", "yt-dlp") } returns true
        assertEquals(0, restore.install(result) {}.missingCount)
        coVerify(exactly = 1) { manager.installRestoreDependency("pkg", "ffmpeg") }
        coVerify(exactly = 2) { manager.installRestoreDependency("pip", "yt-dlp") }
    }
}
