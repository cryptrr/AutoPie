package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandStep
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.firstStepOrSelf
import com.autopi.autopieapp.data.nextStepOrNull
import com.autopi.autopieapp.data.services.JsonService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCommandExtraInputsTest {

    private val internalExtra = CommandExtra(
        id = "token",
        name = "TOKEN",
        type = "STRING",
        flags = listOf(ExtraFlags.INTERNAL_CONFIG.value)
    )

    @Test
    fun updatesOnlyFallbackIdentifiedMultistageStep() {
        val jsonService = MemoryJsonService(multistageConfig())
        val command = pipeline().firstStepOrSelf().nextStepOrNull()!!

        val result = StoreCommandExtraInputs(jsonService)(command, listOf(input("new-value")))

        assertTrue(result.updatedConfig)
        assertEquals("Pipeline.1", command.id)
        val steps = jsonService.commands.getAsJsonObject("Pipeline").getAsJsonArray("steps")
        assertEquals("first", steps[0].asJsonObject.getAsJsonArray("extras")[0].asJsonObject["default"].asString)
        assertEquals("new-value", steps[1].asJsonObject.getAsJsonArray("extras")[0].asJsonObject["default"].asString)
        assertFalse(steps[1].asJsonObject.has("id"))
    }

    @Test
    fun explicitStepIdIsNamespacedWithoutChangingStoredId() {
        val jsonService = MemoryJsonService(multistageConfig(secondStepId = "publish"))
        val command = pipeline(secondStepId = "publish").firstStepOrSelf().nextStepOrNull()!!

        StoreCommandExtraInputs(jsonService)(command, listOf(input("new-value")))

        assertEquals("Pipeline.publish", command.id)
        val step = jsonService.commands.getAsJsonObject("Pipeline").getAsJsonArray("steps")[1].asJsonObject
        assertEquals("publish", step["id"].asString)
        assertEquals("new-value", step.getAsJsonArray("extras")[0].asJsonObject["default"].asString)
    }

    @Test
    fun selectableInternalConfigValueIsPersistedAsDefault() {
        assertSelectableInternalConfigValueIsPersisted("SELECTABLE")
    }

    @Test
    fun flatSelectableInternalConfigValueIsPersistedAsDefault() {
        assertSelectableInternalConfigValueIsPersisted("SELECTABLE_FLAT")
    }

    @Test
    fun multiSelectableInternalConfigValueIsPersistedAsDefault() {
        assertStringDefaultIsPersisted("MULTI_SELECTABLE", "first\nsecond")
    }

    @Test
    fun emptyMultiSelectableInternalConfigValueIsPersistedAsDefault() {
        assertStringDefaultIsPersisted("MULTI_SELECTABLE_FLAT", "")
    }

    @Test
    fun booleanInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("BOOLEAN", value = "false", expected = false)
    }

    @Test
    fun enabledFlagInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("FLAG", value = "--force", expected = true)
    }

    @Test
    fun disabledFlagInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("FLAG", value = "", expected = false)
    }

    private fun assertSelectableInternalConfigValueIsPersisted(type: String) {
        val extra = internalExtra.copy(
            type = type,
            default = "first",
            selectableOptions = linkedMapOf("First" to "first", "Second" to "second")
        )
        val config = JsonParser.parseString(
            """{"Selector":{"extras":[{"id":"token","name":"TOKEN","type":"$type","default":"first","flags":["--internal-config"],"selectableOptions":{"First":"first","Second":"second"}}]}}"""
        ).asJsonObject
        val jsonService = MemoryJsonService(config)
        val command = CommandModel(
            id = "Selector",
            name = "Selector",
            type = CommandType.SHARE,
            extras = listOf(extra)
        )
        val input = CommandExtraInput(
            name = extra.name,
            default = extra.default,
            value = "second",
            type = extra.type,
            defaultBoolean = extra.defaultBoolean,
            id = extra.id,
            description = extra.description
        )

        val result = StoreCommandExtraInputs(jsonService)(command, listOf(input))

        assertTrue(result.updatedConfig)
        assertEquals("second", result.command.extras!![0].default)
        val storedExtra = jsonService.commands
            .getAsJsonObject("Selector")
            .getAsJsonArray("extras")[0]
            .asJsonObject
        assertEquals("second", storedExtra["default"].asString)
    }

    private fun assertStringDefaultIsPersisted(type: String, value: String) {
        val extra = internalExtra.copy(type = type, default = "old")
        val jsonService = MemoryJsonService(singleExtraConfig(extra))
        val command = singleExtraCommand(extra)

        val result = StoreCommandExtraInputs(jsonService)(command, listOf(input(extra, value)))

        assertTrue(result.updatedConfig)
        assertEquals(value, result.command.extras!![0].default)
        assertEquals(value, storedExtra(jsonService)["default"].asString)
    }

    private fun assertBooleanDefaultIsPersisted(type: String, value: String, expected: Boolean) {
        val extra = internalExtra.copy(type = type, default = "--force", defaultBoolean = !expected)
        val jsonService = MemoryJsonService(singleExtraConfig(extra))
        val command = singleExtraCommand(extra)

        val result = StoreCommandExtraInputs(jsonService)(command, listOf(input(extra, value)))

        assertTrue(result.updatedConfig)
        assertEquals(expected, result.command.extras!![0].defaultBoolean)
        assertEquals(expected, storedExtra(jsonService)["defaultBoolean"].asBoolean)
        assertEquals("--force", storedExtra(jsonService)["default"].asString)
    }

    private fun singleExtraCommand(extra: CommandExtra) = CommandModel(
        id = "Single",
        name = "Single",
        type = CommandType.SHARE,
        extras = listOf(extra)
    )

    private fun singleExtraConfig(extra: CommandExtra): JsonObject = JsonObject().apply {
        add("Single", JsonObject().apply {
            add("extras", com.google.gson.Gson().toJsonTree(listOf(extra)))
        })
    }

    private fun storedExtra(jsonService: MemoryJsonService) = jsonService.commands
        .getAsJsonObject("Single")
        .getAsJsonArray("extras")[0]
        .asJsonObject

    private fun pipeline(secondStepId: String = "") = CommandModel(
        id = "Pipeline",
        name = "Pipeline",
        type = CommandType.SHARE,
        multiStage = true,
        steps = listOf(
            CommandStep(command = "first", extras = listOf(internalExtra.copy(default = "first"))),
            CommandStep(id = secondStepId, command = "second", extras = listOf(internalExtra.copy(default = "second")))
        )
    )

    private fun input(value: String) = CommandExtraInput(
        name = internalExtra.name,
        default = "second",
        value = value,
        type = internalExtra.type,
        defaultBoolean = true,
        id = internalExtra.id,
        description = ""
    )

    private fun input(extra: CommandExtra, value: String) = CommandExtraInput(
        name = extra.name,
        default = extra.default,
        value = value,
        type = extra.type,
        defaultBoolean = extra.defaultBoolean,
        id = extra.id,
        description = extra.description
    )

    private fun multistageConfig(secondStepId: String? = null): JsonObject {
        val id = secondStepId?.let { "\"id\": \"$it\"," }.orEmpty()
        return JsonParser.parseString(
            """
                {
                  "Pipeline": {
                    "multiStage": true,
                    "steps": [
                      {"command": "first", "extras": [{"id":"token","name":"TOKEN","type":"STRING","default":"first","flags":["--internal-config"]}]},
                      {$id "command": "second", "extras": [{"id":"token","name":"TOKEN","type":"STRING","default":"second","flags":["--internal-config"]}]}
                    ]
                  }
                }
            """.trimIndent()
        ).asJsonObject
    }

    private class MemoryJsonService(initialCommands: JsonObject) : JsonService {
        var commands: JsonObject = initialCommands

        override fun readCommandsConfig(): JsonObject = commands
        override fun writeCommandsConfig(jsonString: String) {
            commands = JsonParser.parseString(jsonString).asJsonObject
        }
        override fun readRepoList(path: String): JsonObject = JsonObject()
    }
}
