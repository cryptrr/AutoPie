package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.services.JsonService
import com.autopi.use_case.GetCommandsList
import com.autopi.utils.getCommandExec
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandExecConfigTest {
    @Test
    fun `reported JSON preserves newline through config loading and package discovery`() {
        val jsonService = mockk<JsonService>()
        every { jsonService.readCommandsConfig() } returns JsonParser.parseString(
            """
            {
              "My recipe": {
                "id": "local.my-recipe",
                "type": "SHARE",
                "command": "#@INTERACTIVE\ncd ~/storage/shared/"
              }
            }
            """.trimIndent()
        ).asJsonObject

        val recipes = GetCommandsList(jsonService)()

        assertEquals(1, recipes.size)
        assertEquals(listOf("#@INTERACTIVE", "cd ~/storage/shared/"), recipes.single().command.lines())
        assertEquals(listOf("cd"), recipes.mapNotNull { getCommandExec(it) })
    }
}
