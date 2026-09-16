package com.autopi

import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autopi.utils.Shell
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShellProcessGroupTest {

    private val shells = mutableListOf<Shell>()
    private val executors = mutableListOf<ExecutorService>()
    private val temporaryFiles = mutableListOf<File>()

    @After
    fun cleanUpProcesses() {
        // Keep a failed assertion from leaving a native process behind on the test device.
        temporaryFiles.forEach { file ->
            runCatching { file.readText().trim().toInt() }
                .getOrNull()
                ?.let(::forceKillProcess)
        }
        shells.forEach { shell ->
            shell.processGroupId?.let(::forceKillProcessGroup)
            runCatching { shell.interrupt() }
        }
        executors.forEach { executor -> executor.shutdownNow() }
        temporaryFiles.forEach(File::delete)
    }

    @Test
    fun interruptTerminatesNestedForegroundCommand() {
        val shell = newIsolatedShell()
        val commandPidFile = newPidFile("foreground")
        val executor = newExecutor()
        val script =
            "printf '%s' \"\$\$\" > ${commandPidFile.absolutePath.shellQuote()}; " +
                "exec /system/bin/sleep 60"

        val result = executor.submitShellCommand(
            shell,
            "/system/bin/sh -c ${script.shellQuote()}"
        )

        val commandPid = awaitPid(commandPidFile)
        val sessionId = requireNotNull(shell.sessionId)
        assertTrue("Nested command did not start", processExists(commandPid))
        assertTrue("Isolated shell did not start", processExists(sessionId))

        shell.interrupt()
        result.get(COMMAND_RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertTrue("Nested command survived Shell.interrupt()", awaitProcessGone(commandPid))
        assertTrue("Shell session leader survived Shell.interrupt()", awaitProcessGone(sessionId))
        assertTrue("Shell process group survived Shell.interrupt()", awaitProcessGroupGone(sessionId))
    }

    @Test
    fun shutdownTerminatesBackgroundChild() {
        val shell = newIsolatedShell()
        val childPidFile = newPidFile("background")
        val command =
            "/system/bin/sleep 60 >/dev/null 2>&1 & child=\$!; " +
                "printf '%s' \"\$child\" > ${childPidFile.absolutePath.shellQuote()}"

        val result = shell.run(command)
        assertTrue("Background command failed to launch", result.isSuccess)

        val childPid = awaitPid(childPidFile)
        val sessionId = requireNotNull(shell.sessionId)
        assertTrue("Background child did not start", processExists(childPid))
        assertTrue("Isolated shell did not start", processExists(sessionId))

        shell.shutdown()

        assertTrue("Background child survived Shell.shutdown()", awaitProcessGone(childPid))
        assertTrue("Shell session leader survived Shell.shutdown()", awaitProcessGone(sessionId))
        assertTrue("Shell process group survived Shell.shutdown()", awaitProcessGroupGone(sessionId))
    }

    private fun newIsolatedShell(): Shell =
        Shell("/system/bin/sh", isolateProcessGroup = true).also { shell ->
            shells += shell
            assertNotNull("Shell did not record its isolated session id", shell.sessionId)
        }

    private fun newExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor().also(executors::add)

    private fun newPidFile(label: String): File {
        val cacheDirectory =
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return File(cacheDirectory, "shell-process-group-$label-${System.nanoTime()}.pid")
            .also(temporaryFiles::add)
    }

    private fun ExecutorService.submitShellCommand(shell: Shell, command: String): Future<Shell.Command.Result> =
        submit<Shell.Command.Result> {
            shell.run(command) {
                timeout = Shell.Timeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                notify = false
            }
        }

    private fun awaitPid(file: File): Int {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val pid = runCatching { file.readText().trim().toInt() }.getOrNull()
            if (pid != null && pid > 0) return pid
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Process pid was not written to ${file.absolutePath}")
    }

    private fun awaitProcessGone(pid: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!processExists(pid)) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return !processExists(pid)
    }

    private fun awaitProcessGroupGone(groupId: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + PROCESS_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!processGroupExists(groupId)) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return !processGroupExists(groupId)
    }

    private fun processExists(pid: Int): Boolean = File("/proc/$pid").exists()

    private fun processGroupExists(groupId: Int): Boolean = try {
        Os.kill(-groupId, 0)
        true
    } catch (error: ErrnoException) {
        error.errno != OsConstants.ESRCH
    }

    private fun forceKillProcess(pid: Int) {
        try {
            Os.kill(pid, OsConstants.SIGKILL)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ESRCH) throw error
        }
    }

    private fun forceKillProcessGroup(groupId: Int) {
        try {
            Os.kill(-groupId, OsConstants.SIGKILL)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ESRCH) throw error
        }
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\"'\"'")}'"

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 15L
        const val COMMAND_RESULT_TIMEOUT_SECONDS = 5L
        const val PROCESS_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
