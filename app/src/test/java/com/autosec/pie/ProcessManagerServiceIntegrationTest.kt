package com.autopi

import android.app.Application
import android.os.Environment
import androidx.lifecycle.viewModelScope
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandStep
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.InputParsedData
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.nextStepOrNull
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DefaultDispatchers
import com.autopi.utils.Shell
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Host integration tests: service, view-model events, generated scripts, files and Bash are real.
 * Android storage/notification boundaries are mocked; no emulator or Termux installation is needed.
 * Requires /bin/bash, like ProcessManagerTests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessManagerServiceIntegrationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var service: ProcessManagerService
    private lateinit var main: MainViewModel
    private lateinit var cache: File
    private lateinit var workingDirectory: File
    private val notification = mockk<AutoPieNotification>(relaxed = true)
    private val processIds = mutableSetOf<Int>()

    @Before
    fun setUp() {
        cache = temporaryFolder.newFolder("cache with spaces")
        workingDirectory = temporaryFolder.newFolder("working directory's files")
        val files = temporaryFolder.newFolder("files")
        val bin = File(files, "usr/bin").apply { mkdirs() }
        Files.createSymbolicLink(File(bin, "bash").toPath(), File("/bin/bash").toPath())
        val application = mockk<Application>(relaxed = true)
        every { application.filesDir } returns files
        every { application.cacheDir } returns cache
        every { application.packageName } returns "com.autopi.test"
        every { application.getString(any()) } returns "Test string"
        val preferences = mockk<AppPreferences>(relaxed = true)
        every { preferences.getStringSync(any()) } returns "Preferences"
        every { preferences.getString(any()) } returns flowOf("Preferences")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns temporaryFolder.root
        val paths = AutoPieConfigPathProvider(application, preferences)
        main = MainViewModel(application, preferences, paths, DefaultDispatchers())
        service = ProcessManagerService(
            main, DefaultDispatchers(), application, paths,
            shellTimeout = Shell.Timeout(5, TimeUnit.SECONDS),
            secretsService = mockk(relaxed = true),
            autoPieNotification = notification,
            internalConfigService = mockk(relaxed = true)
        )
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        try {
            if (::service.isInitialized) processIds.forEach(service::stopShell)
        } finally {
            if (::main.isInitialized) main.viewModelScope.cancel()
            unmockkStatic(Environment::class)
        }
    }

    private fun command(script: String) = CommandModel(
        id = "integration-command", type = CommandType.SHARE, name = "Integration command",
        path = workingDirectory.absolutePath, exec = "", command = script, extras = emptyList()
    )

    private suspend fun execute(
        command: CommandModel,
        processId: Int = 71001,
        jobType: JobType = JobType.STANDALONE,
        inputs: List<InputParsedData> = emptyList(),
        extras: List<CommandExtraInput> = emptyList()
    ) = withContext(Dispatchers.IO) {
        processIds.add(processId)
        service.runCommandForShareWithEnv2(
            command, command.exec, command.command, command.path,
            inputParsedData = inputs, commandExtraInputs = extras, rawInput = "original input",
            processId = processId, jobType = jobType, usePython = false
        )
    }

    private fun TestScope.collectEvents(): MutableList<ViewModelEvent> {
        val events = mutableListOf<ViewModelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            main.eventFlow.collect { events.add(it) }
        }
        return events
    }

    @Test
    fun `successful execution writes both streams and emits complete lifecycle metadata`() = runTest {
        val events = collectEvents()
        val command = command("printf 'stdout-message\\n'; printf 'stderr-message\\n' >&2; export OUTPUT=42")
        val result = execute(command, jobType = JobType.TEXT)
        mainDispatcherRule.scheduler.advanceUntilIdle()

        assertTrue(result.success)
        assertFalse(result.partial)
        assertEquals(71001, result.processId)
        assertEquals("42", result.exportedOutput)
        assertTrue(result.output.contains("stdout-message"))
        assertTrue(result.output.contains("stderr-message"))
        val log = File(cache, "71001.log")
        assertTrue(log.readLines().contains("stdout-message"))
        assertTrue(log.readLines().contains("stderr-message"))
        assertEquals(listOf(
            ViewModelEvent.CommandStarted(71001, command, log.absolutePath, "original input", JobType.TEXT),
            ViewModelEvent.CommandCompleted(71001, command, log.absolutePath, JobType.TEXT, exportedOutput = "42")
        ), events)
        assertEquals(listOf(71001), service.processIds)
        assertEquals(listOf(71001), service.successProcessIds)
        assertTrue(service.failedProcessIds.isEmpty())
        assertNull(service.getShellEnvironmentVariable(71001, "PREFIX"))
    }

    @Test
    fun `nonzero exit reports failure with diagnostics and removes the shell`() = runTest {
        val events = collectEvents()
        val command = command("printf 'failure-diagnostic\\n' >&2; false")
        val result = execute(command)
        mainDispatcherRule.scheduler.advanceUntilIdle()

        assertFalse(result.success)
        assertFalse(result.partial)
        assertTrue(result.output.contains("failure-diagnostic"))
        assertEquals(listOf(
            ViewModelEvent.CommandStarted(71001, command, File(cache, "71001.log").absolutePath, "original input", JobType.STANDALONE),
            ViewModelEvent.CommandFailed(71001, command, File(cache, "71001.log").absolutePath, JobType.STANDALONE)
        ), events)
        assertEquals(listOf(71001), service.failedProcessIds)
        assertTrue(service.successProcessIds.isEmpty())
        assertNull(service.getShellEnvironmentVariable(71001, "PREFIX"))
        // Reusing the id after failure must start a usable shell.
        assertTrue(execute(command("true")).success)
    }

    @Test
    fun `working directory and input values survive spaces quotes and shell metacharacters`() = runTest {
        val value = "O'Brien says \"hello\"; \$(touch injected); `touch injected-too`\nsecond line"
        val result = execute(
            command("printf '%s' \"\$PAYLOAD\" > received.txt; pwd > directory.txt"),
            inputs = listOf(InputParsedData(name = "PAYLOAD", value = value))
        )

        assertTrue(result.success)
        assertEquals(value, File(workingDirectory, "received.txt").readText())
        val reportedDirectory = File(File(workingDirectory, "directory.txt").readText().trim())
        assertEquals(workingDirectory.canonicalFile, reportedDirectory.canonicalFile)
        assertFalse(File(workingDirectory, "injected").exists())
        assertFalse(File(workingDirectory, "injected-too").exists())
    }

    @Test
    fun `extra defaults and explicit overrides reach the executed command`() = runTest {
        val command = command("export OUTPUT=\"\$CHOICE\"").copy(
            extras = listOf(CommandExtra(id = "choice", name = "CHOICE", type = "STRING", default = "default value"))
        )
        assertEquals("default value", execute(command).exportedOutput)
        val override = CommandExtraInput(
            name = "CHOICE", default = "default value", value = "user's choice", type = "STRING",
            defaultBoolean = false, id = "choice", description = ""
        )
        assertEquals("user's choice", execute(command, extras = listOf(override)).exportedOutput)
    }

    @Test
    fun `shell environments are isolated and stopped process ids start fresh`() = runTest {
        processIds.addAll(listOf(71001, 71002))
        assertTrue(service.setShellEnvironmentVariable(71001, "TEST_VALUE", "first"))
        assertTrue(service.setShellEnvironmentVariable(71002, "TEST_VALUE", "second"))
        service.createShell(71001)
        assertEquals("first", service.getShellEnvironmentVariable(71001, "TEST_VALUE"))
        assertEquals("second", service.getShellEnvironmentVariable(71002, "TEST_VALUE"))
        service.stopShell(71001)
        service.stopShell(71001)
        assertNull(service.getShellEnvironmentVariable(71001, "TEST_VALUE"))
        service.createShell(71001)
        assertNull(service.getShellEnvironmentVariable(71001, "TEST_VALUE"))
        assertEquals("second", service.getShellEnvironmentVariable(71002, "TEST_VALUE"))
    }

    @Test
    fun `environment API distinguishes empty from unset and rejects injected names`() = runTest {
        processIds.add(71001)
        val literal = "a'b \"c\" \$(printf injected)\nsecond line"
        assertTrue(service.setShellEnvironmentVariables(71001, mapOf("TEST_LITERAL" to literal, "TEST_EMPTY" to "")))
        assertEquals(literal, service.getShellEnvironmentVariable(71001, "TEST_LITERAL"))
        assertEquals("", service.getShellEnvironmentVariable(71001, "TEST_EMPTY"))
        assertNull(service.getShellEnvironmentVariable(71001, "TEST_MISSING"))
        assertFalse(service.setShellEnvironmentVariable(71001, "BAD; export INJECTED", "yes"))
        assertNull(service.getShellEnvironmentVariable(71001, "TEST_LITERAL}; export INJECTED=yes; #"))
        assertNull(service.getShellEnvironmentVariable(71001, "INJECTED"))
        assertEquals(literal, service.getShellEnvironmentVariable(71001, "TEST_LITERAL"))
    }

    @Test
    fun `multistage workflow passes output to input and does not retain stale output`() = runTest {
        val events = collectEvents()
        val first = command("export OUTPUT='first stage'").copy(
            multiStage = true,
            steps = listOf(
                CommandStep(command = "export OUTPUT='first stage'", path = workingDirectory.absolutePath),
                CommandStep(command = "printf '%s' \"\$INPUT\" > transferred.txt", path = workingDirectory.absolutePath)
            )
        )
        val firstResult = execute(first)
        assertTrue(firstResult.success)
        assertTrue(firstResult.partial)
        assertEquals("first stage", firstResult.exportedOutput)
        val secondResult = execute(first.nextStepOrNull()!!)
        mainDispatcherRule.scheduler.advanceUntilIdle()

        assertTrue(secondResult.success)
        assertFalse(secondResult.partial)
        assertNull(secondResult.exportedOutput)
        assertEquals("first stage", File(workingDirectory, "transferred.txt").readText())
        assertFalse(File(cache, "71001.output").exists())
        val completed = events.filterIsInstance<ViewModelEvent.CommandCompleted>()
        assertEquals(listOf(true, false), completed.map { it.partial })
        assertEquals(listOf("first stage", null), completed.map { it.exportedOutput })
    }

    @Test
    fun `last structured output wins unless command explicitly exports output`() = runTest {
        val script = """
            printf '%s\n' '#@AUTOPIE not-json'
            printf '%s\n' '#@AUTOPIE {"type":"output","value":"first"}'
            printf '%s\n' '#@AUTOPIE {"type":"output","value":{"count":2}}'
        """.trimIndent()
        val structured = execute(command(script))
        assertTrue(structured.success)
        assertEquals("{\"count\":2}", structured.exportedOutput)
        val exported = execute(command("$script\nexport OUTPUT=explicit"))
        assertTrue(exported.success)
        assertEquals("explicit", exported.exportedOutput)
    }

    @Test
    fun `progress updates reach foreground notifications but cron stays silent`() = runTest {
        val command = command("printf '%s\\n' '#@AUTOPIE {\"type\":\"progress\",\"value\":37}'")
        assertTrue(execute(command).success)
        verify(exactly = 1) {
            notification.sendBroadcastNotification(
                contentTitle = command.name, contentText = "original input", command = command,
                processId = 71001, logFile = File(cache, "71001.log").absolutePath,
                totalProgress = 100, currentProgress = 37
            )
        }
        assertTrue(execute(command, processId = 71002, jobType = JobType.CRON).success)
        verify(exactly = 0) {
            notification.sendBroadcastNotification(
                contentTitle = any(), contentText = any(), command = any(), processId = 71002,
                logFile = any(), totalProgress = any(), currentProgress = any()
            )
        }
    }

    @Test
    fun `cancel process removes only the selected shell and emits stopped event`() = runTest {
        val events = collectEvents()
        processIds.addAll(listOf(71001, 71002))
        service.setShellEnvironmentVariable(71001, "TEST_VALUE", "first")
        service.setShellEnvironmentVariable(71002, "TEST_VALUE", "second")
        main.dispatchEvent(ViewModelEvent.CancelProcess(71001))
        mainDispatcherRule.scheduler.advanceUntilIdle()
        awaitShellRemoval(71001)
        awaitStoppedEvents(events, 1)

        assertEquals(listOf(71001), events.filterIsInstance<ViewModelEvent.CommandStoppedByUser>().map { it.processId })
        assertEquals("second", service.getShellEnvironmentVariable(71002, "TEST_VALUE"))
        assertTrue(service.successProcessIds.isEmpty())
        assertTrue(service.failedProcessIds.isEmpty())
    }

    @Test
    fun `cancel all removes every registered shell and emits one stopped event each`() = runTest {
        val events = collectEvents()
        processIds.addAll(listOf(71001, 71002))
        processIds.forEach(service::createShell)
        main.dispatchEvent(ViewModelEvent.CancelAllProcesses)
        mainDispatcherRule.scheduler.advanceUntilIdle()
        processIds.forEach { awaitShellRemoval(it) }
        awaitStoppedEvents(events, 2)

        val stopped = events.filterIsInstance<ViewModelEvent.CommandStoppedByUser>()
        assertEquals(2, stopped.size)
        assertEquals(processIds, stopped.map { it.processId }.toSet())
    }

    @Test
    fun `cancel interrupts an active command without waiting for its shell timeout`() = runTest {
        val events = collectEvents()
        // Source a builtin loop so the host test owns only one process. Android process-group
        // termination of child processes requires device coverage.
        val command = command("set +x; printf ready > ready.txt; while :; do :; done").copy(multiStage = true)
        val running = async { execute(command) }
        try {
            withContext(Dispatchers.IO) {
                withTimeout(3_000) {
                    while (!File(workingDirectory, "ready.txt").exists()) delay(10)
                }
            }
            assertFalse(running.isCompleted)
            main.dispatchEvent(ViewModelEvent.CancelProcess(71001))
            mainDispatcherRule.scheduler.advanceUntilIdle()
            val result = withContext(Dispatchers.Default) {
                // The service timeout is five seconds; cancellation must unblock sooner.
                withTimeout(3_000) { running.await() }
            }
            awaitStoppedEvents(events, 1)

            assertFalse(result.success)
            assertFalse(result.partial)
            assertNull(service.getShellEnvironmentVariable(71001, "PREFIX"))
            assertEquals(listOf(71001), events.filterIsInstance<ViewModelEvent.CommandStoppedByUser>().map { it.processId })
            assertTrue(events.none { it is ViewModelEvent.CommandCompleted })
        } finally {
            service.stopShell(71001)
            running.cancel()
        }
    }

    // Cancellation runs on the real IO dispatcher. Poll with a real-time bound, not virtual sleeps.
    private suspend fun awaitShellRemoval(processId: Int) = withContext(Dispatchers.IO) {
        withTimeout(5_000) {
            while (service.getShellEnvironmentVariable(processId, "PREFIX") != null) delay(10)
        }
    }

    private suspend fun awaitStoppedEvents(events: List<ViewModelEvent>, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            // Keep scheduler advancement and event-list access on the test thread.
            mainDispatcherRule.scheduler.advanceUntilIdle()
            if (events.filterIsInstance<ViewModelEvent.CommandStoppedByUser>().size == count) return
            assertTrue("Timed out waiting for $count stopped events: $events", System.nanoTime() < deadline)
            withContext(Dispatchers.IO) { delay(10) }
        }
    }
}
