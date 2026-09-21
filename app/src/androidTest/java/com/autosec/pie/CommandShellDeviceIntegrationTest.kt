package com.autopi

import android.app.Application
import android.os.Environment
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandStep
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.firstStepOrSelf
import com.autopi.autopieapp.data.nextStepOrNull
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DefaultDispatchers
import com.autopi.use_case.RunStandaloneCommand
import com.autopi.utils.Shell
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Runs the production standalone use case and process manager against the target app's installed
 * Termux bootstrap. No host shell, system-shell substitution, or mocked filesystem is used.
 * Install the debug app and open its embedded terminal once before running this class.
 */
@RunWith(AndroidJUnit4::class)
class CommandShellDeviceIntegrationTest {
    private lateinit var application: Application
    private lateinit var root: File
    private lateinit var main: MainViewModel
    private lateinit var service: ProcessManagerService
    private lateinit var prefix: File
    private val processIds = mutableSetOf<Int>()
    private val processId = (UUID.randomUUID().hashCode() and 0x3fffffff) + 100000
    private val commandId = "device-shell-test-${UUID.randomUUID()}"

    @Before
    fun setUp() {
        application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        prefix = File(application.filesDir, "usr")
        val bash = File(prefix, "bin/bash")
        assertTrue(
            "Termux bootstrap missing or not executable: $bash. Open ${application.packageName}'s " +
                "embedded terminal and finish bootstrap installation before running device tests.",
            bash.isFile && bash.canExecute()
        )
        val externalCache = requireNotNull(application.externalCacheDir) { "App external cache is unavailable" }
        root = File(externalCache, commandId).apply { check(mkdirs()) }
        val preferences = AppPreferences(application)
        val paths = AutoPieConfigPathProvider(application, preferences)
        main = MainViewModel(application, preferences, paths, DefaultDispatchers())
        service = ProcessManagerService(
            main, DefaultDispatchers(), application, paths,
            shellTimeout = Shell.Timeout(10, TimeUnit.SECONDS),
            autoPieNotification = mockk(relaxed = true),
            internalConfigService = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() = runBlocking {
        try {
            if (::service.isInitialized) processIds.forEach(service::stopShell)
        } finally {
            if (::main.isInitialized) withContext(Dispatchers.Main) { main.viewModelScope.cancel() }
            if (::root.isInitialized) root.deleteRecursively()
            if (::application.isInitialized) processIds.forEach { id ->
                listOf("sh", "py", "log", "output").forEach { extension ->
                    File(application.cacheDir, "$id.$extension").delete()
                }
            }
        }
    }

    private fun relativePath(directory: File) =
        directory.relativeTo(Environment.getExternalStorageDirectory()).path

    private fun command(script: String) = CommandModel(
        id = commandId, name = commandId, type = CommandType.SHARE,
        path = relativePath(root), exec = "", command = script, extras = emptyList()
    )

    private suspend fun execute(command: CommandModel) = withContext(Dispatchers.IO) {
        processIds.add(processId)
        RunStandaloneCommand(service)(command, processId = processId).single()
    }

    @Test
    fun simpleCommandUsesInstalledTermuxBashAndCapturesBothStreams() = runBlocking {
        val result = execute(command("""
            printf 'device-stdout\n'
            printf 'device-stderr\n' >&2
            printf '%s\n' "${'$'}BASH" "${'$'}PREFIX" "${'$'}ANDROID_PACKAGE_NAME" > runtime.txt
            export OUTPUT='device success'
        """.trimIndent()))

        assertTrue(result.output, result.success)
        assertFalse(result.partial)
        assertEquals(processId, result.processId)
        assertEquals(JobType.STANDALONE, result.jobType)
        assertEquals("device success", result.exportedOutput)
        val runtime = File(root, "runtime.txt").readLines()
        assertEquals(File(prefix, "bin/bash").canonicalPath, File(runtime[0]).canonicalPath)
        assertEquals(prefix.absolutePath, runtime[1])
        assertEquals(application.packageName, runtime[2])
        val lines = File(application.cacheDir, "$processId.log").readLines()
        assertTrue(lines.contains("device-stdout"))
        assertTrue(lines.contains("device-stderr"))
        assertNull(service.getShellEnvironmentVariable(processId, "PREFIX"))
    }

    @Test
    fun pipelineUsesBootstrapToolsAndWritesToDirectoryWithSpacesAndQuotes() = runBlocking {
        val directory = File(root, "working directory's files").apply { check(mkdir()) }
        val result = execute(command("""
            command -v sort > sort-path.txt
            command -v tr > tr-path.txt
            printf '%s\n' gamma alpha beta | sort | tr '[:lower:]' '[:upper:]' > 'result file.txt'
            export OUTPUT="${'$'}(cat 'result file.txt')"
        """.trimIndent()).copy(path = relativePath(directory)))

        assertTrue(result.output, result.success)
        assertEquals("ALPHA\nBETA\nGAMMA", result.exportedOutput)
        assertEquals("ALPHA\nBETA\nGAMMA\n", File(directory, "result file.txt").readText())
        for (tool in listOf("sort", "tr")) {
            assertEquals(File(prefix, "bin/$tool").absolutePath, File(directory, "$tool-path.txt").readText().trim())
        }
    }

    @Test
    fun threeStagesPreserveVariablesFunctionsAndOutputAcrossWorkingDirectories() = runBlocking {
        val secondDirectory = File(root, "second stage's files").apply { check(mkdir()) }
        val first = command("false").copy(multiStage = true, steps = listOf(
            CommandStep(path = relativePath(root), command = """
                counter=40
                decorate() { printf '<%s>' "${'$'}1"; }
                export OUTPUT="first stage's value"
                printf 'stage-one\n'
            """.trimIndent()),
            CommandStep(path = relativePath(secondDirectory), command = """
                counter=${'$'}((counter + 2))
                export OUTPUT="${'$'}(decorate "${'$'}INPUT"):${'$'}counter"
                printf '%s' "${'$'}OUTPUT" > intermediate.txt
                printf 'stage-two\n'
            """.trimIndent()),
            CommandStep(path = relativePath(root), command = """
                printf '%s' "${'$'}INPUT" > final.txt
                export OUTPUT="finished: ${'$'}INPUT"
                printf 'stage-three\n'
            """.trimIndent())
        )).firstStepOrSelf()

        val firstResult = execute(first)
        assertTrue(firstResult.output, firstResult.success)
        assertTrue(firstResult.partial)
        assertEquals("first stage's value", firstResult.exportedOutput)
        val second = requireNotNull(first.nextStepOrNull())
        val secondResult = execute(second)
        assertTrue(secondResult.output, secondResult.success)
        assertTrue(secondResult.partial)
        assertEquals("<first stage's value>:42", secondResult.exportedOutput)
        assertEquals(secondResult.exportedOutput, File(secondDirectory, "intermediate.txt").readText())
        val third = requireNotNull(second.nextStepOrNull())
        val thirdResult = execute(third)
        assertTrue(thirdResult.output, thirdResult.success)
        assertFalse(thirdResult.partial)
        assertEquals("finished: <first stage's value>:42", thirdResult.exportedOutput)
        assertEquals(secondResult.exportedOutput, File(root, "final.txt").readText())
        assertNull(third.nextStepOrNull())
        val lines = File(application.cacheDir, "$processId.log").readLines()
        assertEquals(listOf("stage-one", "stage-two", "stage-three"), lines.filter { it.startsWith("stage-") })
        // The foreground service owns final multistage cleanup; exercise its stop operation here.
        service.stopShell(processId)
        assertNull(service.getShellEnvironmentVariable(processId, "PREFIX"))
    }

    @Test
    fun failedStageClearsStaleOutputAndFreshRunSucceedsAfterCleanup() = runBlocking {
        val first = command("").copy(multiStage = true, steps = listOf(
            CommandStep(path = relativePath(root), command = "export RETAINED=old; export OUTPUT=previous"),
            CommandStep(path = relativePath(root), command = "printf 'device-failure\n' >&2; false"),
            CommandStep(path = relativePath(root), command = "true")
        )).firstStepOrSelf()
        val start = execute(first)
        assertTrue(start.output, start.success)
        assertTrue(start.partial)
        val failure = execute(requireNotNull(first.nextStepOrNull()))
        assertFalse(failure.success)
        assertFalse(failure.partial)
        assertNull(failure.exportedOutput)
        assertTrue(failure.output.contains("device-failure"))
        assertFalse(File(application.cacheDir, "$processId.output").exists())
        service.stopShell(processId)
        assertNull(service.getShellEnvironmentVariable(processId, "PREFIX"))
        val retry = execute(command("export OUTPUT=\"${'$'}{RETAINED-unset}:fresh\""))
        assertTrue(retry.output, retry.success)
        assertEquals("unset:fresh", retry.exportedOutput)
    }

    @Test
    fun stdinIsClosedAndNextStageDoesNotReuseStaleOutput() = runBlocking {
        val first = command("").copy(multiStage = true, steps = listOf(
            CommandStep(path = relativePath(root), command = "if read -r value; then false; else export OUTPUT=eof; fi"),
            CommandStep(path = relativePath(root), command = "printf '%s' \"${'$'}INPUT\" > received.txt")
        )).firstStepOrSelf()
        val start = execute(first)
        assertTrue(start.output, start.success)
        assertTrue(start.partial)
        assertEquals("eof", start.exportedOutput)
        val end = execute(requireNotNull(first.nextStepOrNull()))
        assertTrue(end.output, end.success)
        assertFalse(end.partial)
        assertEquals("eof", File(root, "received.txt").readText())
        assertNull(end.exportedOutput)
        assertFalse(File(application.cacheDir, "$processId.output").exists())
    }
}
