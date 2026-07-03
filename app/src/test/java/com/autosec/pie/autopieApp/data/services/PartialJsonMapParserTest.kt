package com.autopi.autopieApp.data.services

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.services.fromJsonObjectEntries
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialJsonMapParserTest {
    @Test
    fun `incompatible command is skipped while compatible command remains`() {
        val config = JsonParser.parseString(
            """
            {
              "Compatible": {
                "path": "",
                "command": "echo ok",
                "selectableOptions": []
              },
              "Requires newer app": {
                "path": "",
                "command": "echo new",
                "extras": [{
                  "id": "1",
                  "type": "SELECTABLE",
                  "selectableOptions": {"Friendly label": "--raw-value"}
                }]
              }
            }
            """.trimIndent()
        ).asJsonObject

        val result = Gson().fromJsonObjectEntries(config, CommandModel::class.java)

        assertEquals(listOf("Compatible"), result.values.keys.toList())
        assertEquals(listOf("Requires newer app"), result.skippedKeys)
        assertTrue(result.values.getValue("Compatible").command == "echo ok")
    }
}
