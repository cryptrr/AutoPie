package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandStep
import com.autopi.autopieapp.data.CommandType
import com.autopi.utils.getCommandExec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandExecTest {
    @Test
    fun `interactive share recipe identifies cd`() {
        val script = "#@INTERACTIVE\ncd ~/storage/shared/"
        val recipe = CommandModel(id = "local.my-recipe", type = CommandType.SHARE, command = script)

        assertEquals("cd", getCommandExec(script))
        assertEquals("cd", getCommandExec(recipe))
    }

    @Test
    fun `commands without an executable do not crash package discovery`() {
        val scripts = listOf(
            "",
            "   \n\t",
            "#@INTERACTIVE",
            "#@INTERACTIVE\n# comment",
            "#@INTERACTIVE\\ncd ~/storage/shared/",
            "  # comment\n  #@INTERACTIVE\n",
        )

        for (script in scripts) {
            assertNull(script, getCommandExec(script))
            assertNull(script, getCommandExec(CommandModel(command = script)))
        }
    }

    @Test
    fun `blank lines and indented comments are skipped`() {
        val script = "#@INTERACTIVE\n\n  # comment\n\tcd\t~/storage/shared/"

        assertEquals("cd", getCommandExec(script))
        assertEquals("cd", getCommandExec(CommandModel(command = script)))
    }

    @Test
    fun `header only first stage does not crash package discovery`() {
        val recipe = CommandModel(
            multiStage = true,
            steps = listOf(CommandStep(command = "#@INTERACTIVE")),
        )

        assertNull(getCommandExec(recipe))
        assertEquals("cd", getCommandExec(recipe.copy(
            steps = listOf(CommandStep(command = "#@INTERACTIVE\ncd ~/storage/shared/")),
        )))
    }

    @Test
    fun `explicit exec and interpreter labels are preserved`() {
        assertEquals("custom", getCommandExec(CommandModel(exec = "custom", command = "#@INTERACTIVE")))
        for ((script, label) in listOf("#@SHELL" to "Shell", "#@PYTHON" to "Python")) {
            assertEquals(label, getCommandExec(script))
            assertEquals(label, getCommandExec(CommandModel(command = script)))
        }
    }
}
