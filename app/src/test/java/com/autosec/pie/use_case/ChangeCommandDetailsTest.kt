package com.autopi.use_case

import androidx.compose.runtime.mutableStateOf
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.services.InternalConfigService
import com.autopi.autopieapp.data.services.JsonService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun formEditSynchronizesInternalConfigWithEditedDefault() = runTest {
        val jsonService = MemoryJsonService(
            JsonParser.parseString(
                """{"Download":{"id":"local.download","extras":[{"id":"folder","name":"FOLDER","type":"STRING","default":"old","flags":["--internal-config"]}]}}"""
            ).asJsonObject
        )
        val internalConfigService = mockk<InternalConfigService>(relaxed = true)
        val editedExtra = CommandExtra(
            id = "folder",
            name = "FOLDER",
            type = "STRING",
            default = "new",
            flags = listOf(ExtraFlags.INTERNAL_CONFIG.value)
        )

        ChangeCommandDetails(
            jsonService = jsonService,
            internalConfigService = internalConfigService
        )(
            key = "Download",
            commandExtras = mutableStateOf(listOf(editedExtra)),
            oldCommandName = mutableStateOf("Download"),
            selectors = mutableStateOf(""),
            commandName = mutableStateOf("Download"),
            directory = mutableStateOf(""),
            execFile = mutableStateOf(""),
            command = mutableStateOf("echo"),
            type = mutableStateOf("SHARE"),
            cronInterval = mutableStateOf("")
        )

        verify { internalConfigService.sync("local.download", editedExtra) }
        val storedExtra = jsonService.commands.getAsJsonObject("Download")
            .getAsJsonArray("extras")[0].asJsonObject
        assertEquals("new", storedExtra.get("default").asString)
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
