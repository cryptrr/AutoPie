package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessManagerScriptTest {

    @Test
    fun multiStagePreambleConsumesOutputIntoInputBeforeTracing() {
        assertEquals(
            """
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    export INPUT="${'$'}OUTPUT"
                    unset OUTPUT
                fi
                set -x
            """.trimIndent() + "\n",
            commandScriptPreamble(multiStage = true)
        )
    }

    @Test
    fun regularCommandPreambleOnlyEnablesTracing() {
        assertEquals("set -x\n", commandScriptPreamble(multiStage = false))
    }

    @Test
    fun shareCommandScriptRunsPythonHeaderCommandFromTempFileInsideWrapper() {
        val cacheDir = File("/tmp/autopie test-cache")
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Python command",
            command = """
                #@PYTHON
                #@INTERACTIVE
                print("hello")
            """.trimIndent(),
            exec = "ignored"
        )

        val plan = buildCommandScript(
            commandObject = command,
            exec = command.exec,
            command = command.command,
            processId = 42,
            cacheDir = cacheDir,
            usePython = true,
            isShellScript = false
        )

        assertEquals(
            """
                set -x
                readarray -t INPUT_FILES_ARR <<< "${'$'}INPUT_FILES"
                python '/tmp/autopie test-cache/42.py'
                command_status=${'$'}?
                set +x
                exit "${'$'}command_status"
            """.trimIndent() + "\n",
            plan.shellScript
        )
        assertEquals("""print("hello")""", plan.pythonScript)
        assertEquals("python '/tmp/autopie test-cache/42.py'", plan.fullCommand)
    }

    @Test
    fun shareCommandScriptUsesPythonPackageCommandWhenPythonIsEnabledWithoutHeader() {
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Package command",
            command = "--flag value",
            exec = "/data/user/0/com.autopi/files/bin/tool"
        )

        val plan = buildCommandScript(
            commandObject = command,
            exec = command.exec,
            command = command.command,
            processId = 99,
            cacheDir = File("/tmp"),
            usePython = true,
            isShellScript = false
        )

        assertEquals(
            "python /data/user/0/com.autopi/files/bin/tool --flag value",
            plan.fullCommand
        )
        assertEquals(null, plan.pythonScript)
    }

    @Test
    fun shareCommandScriptUsesBashForLegacyShellScriptBranch() {
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Shell command",
            command = "/storage/emulated/0/scripts/do thing.sh",
            exec = ""
        )

        val plan = buildCommandScript(
            commandObject = command,
            exec = command.exec,
            command = command.command,
            processId = 100,
            cacheDir = File("/tmp"),
            usePython = false,
            isShellScript = true
        )

        assertEquals(
            "bash /storage/emulated/0/scripts/do thing.sh",
            plan.fullCommand
        )
    }

    @Test
    fun multistageShareCommandScriptReturnsStepStatusInsteadOfExitingShell() {
        val command = CommandModel(
            type = CommandType.SHARE,
            name = "Multistage command",
            command = "echo value",
            exec = "",
            multiStage = true
        )

        val plan = buildCommandScript(
            commandObject = command,
            exec = command.exec,
            command = command.command,
            processId = 101,
            cacheDir = File("/tmp"),
            usePython = false,
            isShellScript = false
        )

        assertEquals(
            """
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    export INPUT="${'$'}OUTPUT"
                    unset OUTPUT
                fi
                set -x
                readarray -t INPUT_FILES_ARR <<< "${'$'}INPUT_FILES"
                echo value
                step_status=${'$'}?
                set +x
                return "${'$'}step_status"
            """.trimIndent() + "\n",
            plan.shellScript
        )
    }

    @Test
    fun shellExportCommandsQuoteValuesUnlessAlreadyQuoted() {
        assertEquals(
            """
                export INPUT='one two'
                export TITLE='Bob '"'"'quoted'"'"' it'
                export EXISTING="already quoted"
            """.trimIndent(),
            linkedMapOf(
                "INPUT" to "one two",
                "TITLE" to "Bob 'quoted' it",
                "EXISTING" to "\"already quoted\""
            ).toShellExportCommands()
        )
    }
}
