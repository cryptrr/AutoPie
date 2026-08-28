package com.autopi

import android.app.Application
import android.os.Environment
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.nextStepOrNull
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DefaultDispatchers
import com.autopi.utils.Shell
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.test.KoinTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit


class ProcessManagerTests : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createProcessManagerService(
        testName: String
    ): Pair<ProcessManagerService, AutoPieConfigPathProvider> {
        val mockApplication = mockk<Application>(relaxed = true)
        val testRoot = Files.createTempDirectory("autopie-$testName").toFile()
        val testCacheDir = File(testRoot, "cache")
        val testFilesDir = File(testRoot, "files")
        testCacheDir.mkdirs()
        File(testFilesDir, "usr/bin").mkdirs()
        Files.createSymbolicLink(
            File(testFilesDir, "usr/bin/bash").toPath(),
            File("/bin/bash").toPath()
        )

        every { mockApplication.getString(any()) } returns "Mocked String"
        every { mockApplication.cacheDir } returns testCacheDir
        every { mockApplication.filesDir } returns testFilesDir
        every { mockApplication.packageName } returns "com.autopi.test"

        val mockedPreferences = mockk<AppPreferences>(relaxed = true)
        every { mockedPreferences.getStringSync(any()) } returns "Preferences"
        every { mockedPreferences.getString(any()) } returns flowOf("Preferences")

        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns testRoot

        val autoPieConfigPathProvider = AutoPieConfigPathProvider(mockApplication, mockedPreferences)
        val mainViewModel = MainViewModel(
            mockApplication,
            mockedPreferences,
            autoPieConfigPathProvider,
            DefaultDispatchers()
        )
        return ProcessManagerService(
            mainViewModel,
            DefaultDispatchers(),
            mockApplication,
            autoPieConfigPathProvider,
            Shell.Timeout(5, TimeUnit.SECONDS)
        ) to autoPieConfigPathProvider
    }

    @Test
    fun `multistage command keeps shell alive and reuses env in next step`() = runTest {
        val (processManagerService, _) = createProcessManagerService("multistage-shell-env")
        val processId = 61545
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Keep env",
            path = "",
            command = "export AUTOPIE_MULTI_STAGE_VALUE=from-first-step",
            exec = "",
            extras = emptyList(),
            multiStage = true,
            steps = listOf(
                com.autopi.autopieapp.data.CommandStep(
                    command = "export AUTOPIE_MULTI_STAGE_VALUE=from-first-step"
                ),
                com.autopi.autopieapp.data.CommandStep(
                    command = "printf '%s\\n' \"\$AUTOPIE_MULTI_STAGE_VALUE\""
                )
            )
        )
        val firstStep = command
        val secondStep = firstStep.nextStepOrNull()!!

        try {
            val firstResult = processManagerService.runCommandForShareWithEnv2(
                firstStep,
                firstStep.exec,
                firstStep.command,
                firstStep.path,
                commandExtraInputs = emptyList(),
                rawInput = "",
                processId = processId,
                jobType = JobType.STANDALONE,
                usePython = false
            )

            assertTrue(firstResult.success)
            assertTrue(firstResult.partial)
            assertEquals(
                "from-first-step",
                processManagerService.getShellEnvironmentVariable(
                    processId,
                    "AUTOPIE_MULTI_STAGE_VALUE"
                )
            )

            val secondResult = processManagerService.runCommandForShareWithEnv2(
                secondStep,
                secondStep.exec,
                secondStep.command,
                secondStep.path,
                commandExtraInputs = emptyList(),
                rawInput = "",
                processId = processId,
                jobType = JobType.STANDALONE,
                usePython = false
            )

            assertTrue(secondResult.success)
            assertTrue(secondResult.output.contains("from-first-step"))
        } finally {
            processManagerService.stopShell(processId)
        }
    }

    @Test
    fun `multistage command that exits shared shell reports failure`() = runTest {
        val (processManagerService, _) = createProcessManagerService("multistage-shell-exit")
        val processId = 61546
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Fail workflow",
            path = "",
            command = "exit 7",
            exec = "",
            extras = emptyList(),
            multiStage = true,
            steps = listOf(
                com.autopi.autopieapp.data.CommandStep(command = "exit 7"),
                com.autopi.autopieapp.data.CommandStep(command = "echo should-not-run")
            )
        )

        try {
            val result = processManagerService.runCommandForShareWithEnv2(
                command,
                command.exec,
                command.command,
                command.path,
                commandExtraInputs = emptyList(),
                rawInput = "",
                processId = processId,
                jobType = JobType.STANDALONE,
                usePython = false
            )

            assertFalse(result.success)
            assertFalse(result.partial)
        } finally {
            processManagerService.stopShell(processId)
        }
    }

//    @Test
//    fun `runCommandForShareWithEnv does not throw for safe command`() = runTest {
//
//        val mockApplication = mockk<Application>(relaxed = true)
//
//        every { mockApplication.getString(any()) } returns "Mocked String"
//
//        val mockedPreferences = mockk<AppPreferences>(relaxed = true)
//
//        every { mockedPreferences.getStringSync(any()) } returns "Preferences"
//        every { mockedPreferences.getString(any()) } returns flowOf("Preferences")
//
//        val processManagerService = ProcessManagerService(MainViewModel(mockApplication, mockedPreferences, DefaultDispatchers()), DefaultDispatchers(), mockApplication)
//
//        val newCommand = CommandModel(
//            type = CommandType.SHARE,
//            name = "Delete everything",
//            path = "",
//            command = "-o \"/storage/emulated/0/dd.mp4\" out.mp3",
//            deleteSourceFile = true,
//            exec = "ffmpeg",
//            extras = emptyList(),
//        )
//
//
//        try {
//            processManagerService.runCommandForShareWithEnv(newCommand, newCommand.exec, newCommand.command, newCommand.path, commandExtraInputs = emptyList(), processId = 51545)
//        }catch (e: Exception){
//            fail("Should not have thrown an exception: ${e.message}")
//        }
//    }

}
