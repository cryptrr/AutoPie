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
        val steps = jsonService.shares.getAsJsonObject("Pipeline").getAsJsonArray("steps")
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
        val step = jsonService.shares.getAsJsonObject("Pipeline").getAsJsonArray("steps")[1].asJsonObject
        assertEquals("publish", step["id"].asString)
        assertEquals("new-value", step.getAsJsonArray("extras")[0].asJsonObject["default"].asString)
    }

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

    private class MemoryJsonService(initialShares: JsonObject) : JsonService {
        var shares: JsonObject = initialShares

        override fun readSharesConfig(): JsonObject = shares
        override fun readObserversConfig(): JsonObject = JsonObject()
        override fun readCronConfig(): JsonObject = JsonObject()
        override fun writeSharesConfig(jsonString: String) {
            shares = JsonParser.parseString(jsonString).asJsonObject
        }
        override fun writeObserversConfig(jsonString: String) = Unit
        override fun writeCronConfig(jsonString: String) = Unit
        override fun readRepoList(path: String): JsonObject = JsonObject()
    }
}
