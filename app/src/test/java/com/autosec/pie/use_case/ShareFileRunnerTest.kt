package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.InputParsedData
import com.autopi.autopieapp.data.ProcessResult
import com.autopi.autopieapp.data.services.ProcessManagerService
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class ShareFileRunnerTest {
    @Test fun perFileRunsExposeNewlineSeparatedPathsForBashArrays() = runTest {
        val service = mockk<ProcessManagerService>(relaxed = true)
        every { service.getAutoPiePackagePath(any()) } returns "/nonexistent/autopie/package"
        every { service.getCommandWorkingDirectory(any()) } returns "/tmp"
        val environments = mutableListOf<List<InputParsedData>>()
        coEvery {
            service.runCommandForShareWithEnv2(
                any(), any(), any(), any(), capture(environments), any(), any(), any(), any(), any(), any()
            )
        } returns ProcessResult("test", 12, true, "")
        val paths = listOf("/tmp/a b.txt", "/tmp/c'd.txt")
        val results = RunCommandForFiles(service)(
            CommandModel(command = "echo ok"), null, paths, processId = 12
        ).toList()
        assertEquals(2, results.size)
        assertEquals(2, environments.size)
        for (env in environments) {
            assertEquals(paths, env.single { it.name == "INPUT_FILES" }.value.split("\n"))
        }
    }

    @Test(expected = IOException::class)
    fun unavailableDirectoryHasExplicitFailure() = runTest {
        RunCommandForDirectory(mockk())(
            CommandModel(command = "echo ok"),
            File("/nonexistent/autopie/input-directory"),
            processId = 12
        ).toList()
        Unit
    }

    @Test fun directoryInputFilesCommandRunsOnlyOnce() = runTest {
        val service = mockk<ProcessManagerService>(relaxed = true)
        every { service.getAutoPiePackagePath(any()) } returns "/nonexistent/autopie/package"
        every { service.getCommandWorkingDirectory(any()) } returns "/tmp"
        val environments = mutableListOf<List<InputParsedData>>()
        coEvery {
            service.runCommandForShareWithEnv2(
                any(), any(), any(), any(), capture(environments), any(), any(), any(), any(), any(), any()
            )
        } returns ProcessResult("test", 12, true, "")

        val directory = createTempDirectory("autopie-directory-runner").toFile()
        try {
            val files = listOf(File(directory, "a.txt"), File(directory, "b.txt"))
            files.forEach { it.createNewFile() }

            val results = RunCommandForDirectory(service)(
                CommandModel(command = "printf '%s\\n' \"\$INPUT_FILES\""),
                directory,
                processId = 12
            ).toList()

            assertEquals(1, results.size)
            assertEquals(1, environments.size)
            assertEquals(
                files.map(File::getAbsolutePath).toSet(),
                environments.single()
                    .single { it.name == "INPUT_FILES" }
                    .value
                    .split("\n")
                    .toSet()
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
