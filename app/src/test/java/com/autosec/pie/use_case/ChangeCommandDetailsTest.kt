package com.autopi.use_case

import androidx.compose.runtime.mutableStateOf
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.services.JsonService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCommandDetailsTest {

    @Test
    fun formEditStoresFileObserverSelectorsAsStrings() = runTest {
        val jsonService = MemoryJsonService(
            JsonParser.parseString(
                """{"Auto Convert Screenshots":{"path":"Pictures/Screenshots","exec":"bash","command":"convert"}}"""
            ).asJsonObject
        )

        ChangeCommandDetails(jsonService)(
            key = "Auto Convert Screenshots",
            commandExtras = mutableStateOf(emptyList<CommandExtra>()),
            oldCommandName = mutableStateOf("Auto Convert Screenshots"),
            selectors = mutableStateOf("^.*\\.png$, ^Screenshot.*$"),
            commandName = mutableStateOf("Auto Convert Screenshots"),
            directory = mutableStateOf("Pictures/Screenshots"),
            execFile = mutableStateOf("bash"),
            command = mutableStateOf("convert"),
            type = mutableStateOf("FILE_OBSERVER"),
            cronInterval = mutableStateOf("")
        )

        val selectors = jsonService.commands
            .getAsJsonObject("Auto Convert Screenshots")
            .getAsJsonArray("selectors")
        assertEquals("^.*\\.png$", selectors[0].asString)
        assertEquals("^Screenshot.*$", selectors[1].asString)
    }

    private class MemoryJsonService(initialCommands: JsonObject) : JsonService {
        var commands = initialCommands

        override fun readCommandsConfig(): JsonObject = commands

        override fun writeCommandsConfig(jsonString: String) {
            commands = JsonParser.parseString(jsonString).asJsonObject
        }

        override fun readRepoList(path: String): JsonObject = JsonObject()
    }
}
