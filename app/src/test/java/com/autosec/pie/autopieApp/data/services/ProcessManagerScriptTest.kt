package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
            isShellScript = false,
            hasInputFiles = true
        )

        assertEquals(
            """
                rm -f '/tmp/autopie test-cache/42.output'
                set -x
                readarray -t INPUT_FILES_ARR <<< "${'$'}INPUT_FILES"
                python '/tmp/autopie test-cache/42.py'
                command_status=${'$'}?
                set +x
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    umask 077
                    printf '%s' "${'$'}OUTPUT" > '/tmp/autopie test-cache/42.output'
                fi
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
            isShellScript = false,
            hasInputFiles = false
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
            isShellScript = true,
            hasInputFiles = false
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
            isShellScript = false,
            hasInputFiles = false
        )

        assertEquals(
            """
                rm -f '/tmp/101.output'
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    export INPUT="${'$'}OUTPUT"
                    unset OUTPUT
                fi
                set -x
                echo value
                step_status=${'$'}?
                set +x
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    umask 077
                    printf '%s' "${'$'}OUTPUT" > '/tmp/101.output'
                fi
                return "${'$'}step_status"
            """.trimIndent() + "\n",
            plan.shellScript
        )
    }

    @Test
    fun shellExportCommandsQuoteEveryValueAsLiteralData() {
        assertEquals(
            """
                export INPUT='one two'
                export TITLE='Bob '"'"'quoted'"'"' it'
                export EXISTING='"already quoted"'
                export SUBSTITUTION='$(printf AUTOPIE_AUDIT_MARKER >&2)'
            """.trimIndent(),
            linkedMapOf(
                "INPUT" to "one two",
                "TITLE" to "Bob 'quoted' it",
                "EXISTING" to "\"already quoted\"",
                "SUBSTITUTION" to "$(printf AUTOPIE_AUDIT_MARKER >&2)"
            ).toShellExportCommands()
        )
    }

    @Test
    fun shellExportCommandsRoundTripShellSyntaxWithoutExecutingIt() {
        val values = listOf(
            "\"double quoted\"",
            "embedded ' single quote",
            "$(printf AUTOPIE_AUDIT_MARKER >&2)",
            "`printf AUTOPIE_AUDIT_MARKER >&2`",
            "first line\nsecond line"
        )

        values.forEach { value ->
            val script = mapOf("VALUE" to value).toShellExportCommands() +
                "\nprintf '%s' \"\$VALUE\""
            val process = ProcessBuilder("bash", "-c", script).start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            assertEquals(0, process.waitFor())
            assertEquals(value, output)
            assertEquals("", error)
        }
    }

    @Test
    fun shellExportCommandsRejectInvalidVariableNames() {
        assertThrows(IllegalArgumentException::class.java) {
            mapOf("SAFE; printf AUTOPIE_AUDIT_MARKER >&2" to "value")
                .toShellExportCommands()
        }
    }
}
