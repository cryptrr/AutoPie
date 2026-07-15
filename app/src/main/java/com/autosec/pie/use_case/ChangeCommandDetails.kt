package com.autopi.use_case

import androidx.compose.runtime.MutableState
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.SECRET_VALUE_PLACEHOLDER
import com.autopi.autopieapp.data.isSecretExtra
import com.autopi.autopieapp.data.secretKey
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.autopieapp.data.withoutStoredSecretDefault
import com.autopi.autopieapp.domain.ViewModelError
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import timber.log.Timber

class ChangeCommandDetails(
    private val jsonService: JsonService,
    private val secretsService: SecretsService? = null
) {
    suspend operator fun invoke(key: String, commandExtras: MutableState<List<CommandExtra>>, oldCommandName: MutableState<String>, selectors: MutableState<String>, commandName: MutableState<String>, directory: MutableState<String>, execFile: MutableState<String>, command: MutableState<String>, type: MutableState<String>, cronInterval: MutableState<String>) {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        //isLoading.value = true

        //Validate extras if exists.
        val validationError = if (commandExtras.value.isEmpty()) {
            false
        } else {
            commandExtras.value.any { it.name.isBlank() } ||
                commandExtras.value.any { !it.isSecretExtra() && it.default.isBlank() }
        }

        Timber.d("${commandExtras.value.any { it.name.isBlank() }}")
        Timber.d("${commandExtras.value.any { it.default.isBlank() }}")


        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        val shareCommands = jsonService.readSharesConfig()
        if (shareCommands == null) {
            throw ViewModelError.ConfigUnavailable
        }

        val commandObject = shareCommands.getAsJsonObject(oldCommandName.value)
            ?: shareCommands.getAsJsonObject(key)
            ?: throw ViewModelError.CommandNotFound

        val selectorsJson = if (selectors.value.isNotBlank()) {
            val jsonArray = JsonArray()

            selectors.value.split(",").map { string ->
                jsonArray.add(JsonParser.parseString(string.trim()))
            }

            jsonArray
        } else {
            JsonArray()
        }

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        storeSecretExtras(commandName.value, commandExtras.value, oldCommandName.value)
        val configExtras = commandExtras.value.map { it.withoutStoredSecretDefault() }


        Timber.d("commandObject: $commandObject")

        commandObject.addProperty("path", directory.value)
        commandObject.addProperty("exec", execFile.value)
        commandObject.addProperty("command", command.value)
        commandObject.addProperty("type", type.value)
        if (configExtras.isNotEmpty()) {
            commandObject.add("extras", gson.toJsonTree(configExtras))
        } else {
            commandObject.remove("extras")
        }

        when (type.value) {
            "FILE_OBSERVER" -> {
                commandObject.add("selectors", selectorsJson)
                commandObject.remove("cronInterval")
            }
            "CRON" -> {
                commandObject.addProperty("cronInterval", cronInterval.value)
                commandObject.remove("selectors")
            }
            else -> {
                commandObject.remove("selectors")
                commandObject.remove("cronInterval")
            }
        }

        if (oldCommandName.value != commandName.value) {
            shareCommands.remove(oldCommandName.value)
        }
        shareCommands.add(commandName.value, commandObject)
        jsonService.writeSharesConfig(gson.toJson(shareCommands))

    }

    private fun storeSecretExtras(commandId: String, extras: List<CommandExtra>, previousCommandId: String) {
        val service = secretsService ?: return
        extras.filter { it.isSecretExtra() }.forEach { extra ->
            val newKey = extra.secretKey(commandId)
            val oldKey = extra.secretKey(previousCommandId)
            val submittedValue = extra.default.takeUnless { it == SECRET_VALUE_PLACEHOLDER }.orEmpty()
            val value = submittedValue.ifBlank {
                if (oldKey != newKey) service.get(oldKey).orEmpty() else service.get(newKey).orEmpty()
            }

            if (value.isNotEmpty()) {
                service.set(newKey, value)
                if (oldKey != newKey) service.delete(oldKey)
            }
        }
    }

}
