package com.autopi.use_case

import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.SECRET_VALUE_PLACEHOLDER
import com.autopi.autopieapp.data.isSecretExtra
import com.autopi.autopieapp.data.secretKey
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.withoutStoredSecretDefault
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import timber.log.Timber

class CreateCommand(
    private val jsonService: JsonService,
    private val secretsService: SecretsService? = null
) {
    suspend operator fun invoke(newCommand: CommandCreationModel) {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        val shareCommands = jsonService.readSharesConfig()

        if (shareCommands == null) throw ViewModelError.ShareConfigUnavailable


        val commandObject = JsonObject()


        commandObject.addProperty("path", newCommand.directory)
        commandObject.addProperty("exec", newCommand.exec)
        commandObject.addProperty("command", newCommand.command)
        commandObject.addProperty("type", newCommand.selectedCommandType)

        val selectorsJson = if(newCommand.selectors.isNotBlank()){
            val jsonArray = JsonArray()

            newCommand.selectors.split(",").map { string ->
                jsonArray.add(string)
            }

            jsonArray

        }else{
            JsonArray()
        }

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        storeSecretExtras(newCommand.commandName, newCommand.commandExtras)
        val configExtras = newCommand.commandExtras.map { it.withoutStoredSecretDefault() }

        if(configExtras.isNotEmpty()){
            commandObject.add("extras", Gson().toJsonTree(configExtras))
        }

        when (newCommand.selectedCommandType) {
            "FILE_OBSERVER" -> {
                commandObject.add("selectors", selectorsJson)
            }
            "CRON" -> {
                commandObject.addProperty("cronInterval", newCommand.cronInterval)
            }
        }

        shareCommands.add(newCommand.commandName, commandObject)
        jsonService.writeSharesConfig(gson.toJson(shareCommands))
    }

    private fun storeSecretExtras(commandId: String, extras: List<CommandExtra>) {
        val service = secretsService ?: return
        extras.filter { it.isSecretExtra() }.forEach { extra ->
            if (extra.default != SECRET_VALUE_PLACEHOLDER) {
                service.set(extra.secretKey(commandId), extra.default)
            }
        }
    }
}
