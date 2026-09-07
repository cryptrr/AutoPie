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
import com.autopi.autopieapp.data.services.AutoPieStructuredEvent
import com.autopi.autopieapp.data.services.parseAutoPieStructuredEvent
import com.autopi.autopieapp.data.services.shouldReplaceWidgetOutput
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DefaultDispatchers
import com.autopi.utils.Shell
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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


@OptIn(ExperimentalCoroutinesApi::class)
class ProcessManagerTests : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class ProcessManagerFixture(
        val service: ProcessManagerService,
        val configPathProvider: AutoPieConfigPathProvider,
        val mainViewModel: MainViewModel
    )

    private fun createProcessManagerService(
        testName: String
    ): ProcessManagerFixture {
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
        return ProcessManagerFixture(
            service = ProcessManagerService(
                mainViewModel,
                DefaultDispatchers(),
                mockApplication,
                autoPieConfigPathProvider,
                Shell.Timeout(5, TimeUnit.SECONDS)
            ),
            configPathProvider = autoPieConfigPathProvider,
            mainViewModel = mainViewModel
        )
    }

    @Test
    fun `blank cron output preserves last widget value`() {
        assertFalse(shouldReplaceWidgetOutput(JobType.CRON, null))
        assertFalse(shouldReplaceWidgetOutput(JobType.CRON, ""))
        assertFalse(shouldReplaceWidgetOutput(JobType.CRON, "   "))
        assertTrue(shouldReplaceWidgetOutput(JobType.CRON, "42"))
        assertTrue(shouldReplaceWidgetOutput(JobType.STANDALONE, ""))
    }

    @Test
    fun `AutoPie output directive parses its value for the widget`() {
        assertEquals(
            AutoPieStructuredEvent.Output("\"3 new posts\""),
            parseAutoPieStructuredEvent(
                "#@AUTOPIE {\"type\":\"output\",\"value\":\"3 new posts\"}"
            )
        )
    }

    @Test
    fun `AutoPie output directive preserves structured JSON values`() {
        assertEquals(
            AutoPieStructuredEvent.Output("{\"count\":3,\"fresh\":true}"),
            parseAutoPieStructuredEvent(
                "#@AUTOPIE {\"type\":\"output\",\"value\":{\"count\":3,\"fresh\":true}}"
            )
        )
    }

    @Test
    fun `AutoPie output directive unwraps a JSON encoded string`() {
        assertEquals(
            AutoPieStructuredEvent.Output("{\"count\":3,\"fresh\":true}"),
            parseAutoPieStructuredEvent(
                "#@AUTOPIE {\"type\":\"output\",\"value\":\"{\\\"count\\\":3,\\\"fresh\\\":true}\"}"
            )
        )
    }

    @Test
    fun `malformed AutoPie directive is ignored`() {
        assertEquals(null, parseAutoPieStructuredEvent("#@AUTOPIE not-json"))
        assertEquals(null, parseAutoPieStructuredEvent("normal command output"))
    }

    @Test
    fun `structured stdout output is retained as command widget output`() = runTest {
        val fixture = createProcessManagerService("structured-stdout-output")
        val command = CommandModel(
            id = "structured-output-command",
            type = CommandType.CRON,
            name = "Structured output",
            path = "",
            command = "printf '%s\\n' '#@AUTOPIE {\"type\":\"output\",\"value\":\"3 new posts\"}'",
            exec = "",
            extras = emptyList()
        )

        val result = fixture.service.runCommandForShareWithEnv2(
            command,
            command.exec,
            command.command,
            command.path,
            commandExtraInputs = emptyList(),
            rawInput = "",
            processId = 61549,
            jobType = JobType.CRON,
            usePython = false
        )

        assertTrue(result.success)
        assertEquals("\"3 new posts\"", result.exportedOutput)
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
        val fixture = createProcessManagerService("multistage-shell-exit")
        val processManagerService = fixture.service
        val events = mutableListOf<ViewModelEvent>()
        mainDispatcherRule.scheduler.advanceUntilIdle()
        val eventCollector = backgroundScope.launch(
            context = UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fixture.mainViewModel.eventFlow.take(2).toList(events)
        }
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
            mainDispatcherRule.scheduler.advanceUntilIdle()
            assertEquals(2, events.size)
            assertTrue(events[0] is ViewModelEvent.CommandStarted)
            val failed = events[1] as ViewModelEvent.CommandFailed
            assertEquals(JobType.STANDALONE, failed.jobType)
        } finally {
            eventCollector.cancel()
            processManagerService.stopShell(processId)
        }
    }

    @Test
    fun `command result captures exported output and clears stale sidecar`() = runTest {
        val (processManagerService, _) = createProcessManagerService("exported-output")
        val processId = 61547
        val command = CommandModel(
            id = "widget-output-command",
            type = CommandType.SHARE,
            name = "Widget output",
            path = "",
            command = "export OUTPUT='[\"one\",\"two\"]'",
            exec = "",
            extras = emptyList()
        )

        val outputResult = processManagerService.runCommandForShareWithEnv2(
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

        assertTrue(outputResult.success)
        assertEquals("[\"one\",\"two\"]", outputResult.exportedOutput)

        val noOutputResult = processManagerService.runCommandForShareWithEnv2(
            command.copy(command = "true"),
            command.exec,
            "true",
            command.path,
            commandExtraInputs = emptyList(),
            rawInput = "",
            processId = processId,
            jobType = JobType.STANDALONE,
            usePython = false
        )

        assertTrue(noOutputResult.success)
        assertEquals(null, noOutputResult.exportedOutput)
    }

    @Test
    fun `cron execution emits typed lifecycle events with exported output`() = runTest {
        val fixture = createProcessManagerService("cron-lifecycle-events")
        val events = mutableListOf<ViewModelEvent>()
        mainDispatcherRule.scheduler.advanceUntilIdle()
        val eventCollector = backgroundScope.launch(
            context = UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED
        ) {
            fixture.mainViewModel.eventFlow.take(2).toList(events)
        }

        val command = CommandModel(
            id = "cron-widget-output",
            type = CommandType.CRON,
            name = "Cron widget output",
            path = "",
            command = "export OUTPUT=42",
            exec = "",
            extras = emptyList()
        )
        val processId = 61548

        val result = fixture.service.runCommandForShareWithEnv2(
            command,
            command.exec,
            command.command,
            command.path,
            commandExtraInputs = emptyList(),
            rawInput = "",
            processId = processId,
            jobType = JobType.CRON,
            usePython = false
        )
        mainDispatcherRule.scheduler.advanceUntilIdle()
        advanceUntilIdle()

        assertTrue(result.success)
        assertEquals(2, events.size)
        val started = events[0] as ViewModelEvent.CommandStarted
        val completed = events[1] as ViewModelEvent.CommandCompleted
        assertEquals(JobType.CRON, started.jobType)
        assertEquals(JobType.CRON, completed.jobType)
        assertEquals("42", completed.exportedOutput)
        eventCollector.cancel()
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
