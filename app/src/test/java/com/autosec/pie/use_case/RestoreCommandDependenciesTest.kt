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
    fun `installation opens Termux only for packages still missing`() = runTest {
        val manager = mockk<ProcessManagerService>()
        coEvery { manager.findMissingTermuxDependencies(any(), any()) } returnsMany listOf(
            MissingTermuxDependencies(pip = listOf("yt-dlp")),
            MissingTermuxDependencies()
        )
        coEvery { manager.openRestoreInstallation(any()) } returns mockk()
        val restore = RestoreCommandDependencies(manager)
        val plan = DependencyRestorePlan(emptyList(), emptyList(), emptyList(), listOf("ffmpeg"), listOf("yt-dlp"), listOf("ffmpeg"), listOf("yt-dlp"))
        assertEquals(true, restore.install(plan))
        assertEquals(false, restore.install(plan))
        coVerify(exactly = 1) { manager.openRestoreInstallation(restoreInstallScript(emptyList(), listOf("yt-dlp"))) }
    }

    @Test
    fun `script continues after failure and safely quotes package names`() {
        val script = restoreInstallScript(listOf("broken", "good'\$(echo injected)"), listOf("python-tool"))
        val process = ProcessBuilder("bash", "-c", """
            pkg() { [[ "${'$'}3" != broken ]]; }
            pip() { return 0; }
            $script
        """.trimIndent()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(1, process.waitFor())
        org.junit.Assert.assertTrue(output.contains("Successful: 2. Failed: 1."))
        org.junit.Assert.assertTrue(output.contains("Failed: pkg: broken"))
        org.junit.Assert.assertTrue(output.contains("good'\$(echo injected)"))
    }
}
