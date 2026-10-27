package com.autopi

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TermuxHomeWorkingDirectoryTest {
    @Test
    fun `home aliases match HOME and PWD while custom working directories are preserved`() {
        val context = mockk<Context>()
        val files = File("/data/user/0/com.autopi/files")
        every { context.filesDir } returns files
        val home = File(files, "home").absolutePath
        val environment = object : TermuxShellEnvironment() {
            // Keep Android environment collection outside this host test.
            override fun getEnvironment(context: Context, isFailSafe: Boolean) =
                hashMapOf("HOME" to home)
        }
        mockkStatic(ShellEnvironmentUtils::class)
        try {
            every { ShellEnvironmentUtils.createHomeDir(any()) } just Runs
            for ((requested, expected) in listOf(
                null to home,
                "" to home,
                TermuxConstants.TERMUX_HOME_DIR_PATH to home,
                "${TermuxConstants.TERMUX_HOME_DIR_PATH}/projects" to "$home/projects",
                "${TermuxConstants.TERMUX_HOME_DIR_PATH}-other" to "${TermuxConstants.TERMUX_HOME_DIR_PATH}-other",
                "/sdcard/projects" to "/sdcard/projects",
            )) {
                val command = ExecutionCommand().apply {
                    workingDirectory = requested
                    setShellCommandShellEnvironment = false
                }
                val values = environment.setupShellCommandEnvironment(context, command)
                assertEquals(expected, command.workingDirectory)
                assertEquals(expected, values["PWD"])
                assertEquals(home, values["HOME"])
            }
        } finally {
            unmockkStatic(ShellEnvironmentUtils::class)
        }
    }
}
