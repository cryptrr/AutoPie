package com.autopi.use_case

import androidx.compose.runtime.MutableState
import com.autopi.autopieapp.data.CommandExtra
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
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()


        if (shareCommands == null || observerCommands == null || cronCommands == null) {
            throw ViewModelError.ConfigUnavailable
        }


        var commandType = ""

        val commandObject = shareCommands.getAsJsonObject(oldCommandName.value)
            .also { if (it != null) commandType = "SHARE" } ?: observerCommands.getAsJsonObject(
            key
        ).also { if (it != null) commandType = "FILE_OBSERVER" }
        ?: cronCommands.getAsJsonObject(
            key
        ).also { if (it != null) commandType = "CRON" }

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

        //Key didn't change
        if (oldCommandName.value == commandName.value) {

            Timber.d("${oldCommandName.value} == ${commandName.value}")

            commandObject.addProperty("path", directory.value)
            commandObject.addProperty("exec", execFile.value)
            commandObject.addProperty("command", command.value)

            when (type.value) {
                "SHARE" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }

                    shareCommands.add(commandName.value, commandObject)
                    //shareCommands.remove(oldCommandName.value)
                }

                "FILE_OBSERVER" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }

                    commandObject.add("selectors", selectorsJson)

                    observerCommands.add(commandName.value, commandObject)
                    //observerCommands.remove(oldCommandName.value)
                }

                "CRON" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }

                    commandObject.addProperty("cronInterval", cronInterval.value)

                    cronCommands.add(commandName.value, commandObject)
                    //observerCommands.remove(oldCommandName.value)
                }
            }


        } else {

            Timber.d("Command key changed")

            commandObject.addProperty("path", directory.value)
            commandObject.addProperty("exec", execFile.value)
            commandObject.addProperty("command", command.value)

            when (type.value) {
                "SHARE" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }


                    shareCommands.add(commandName.value, commandObject)
                    shareCommands.remove(oldCommandName.value)
                }

                "FILE_OBSERVER" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }

                    commandObject.add("selectors", selectorsJson)


                    observerCommands.add(commandName.value, commandObject)
                    observerCommands.remove(oldCommandName.value)
                }

                "CRON" -> {
                    if (configExtras.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(configExtras))
                    } else {
                        commandObject.remove("extras")
                    }

                    commandObject.addProperty("cronInterval", cronInterval.value)


                    cronCommands.add(commandName.value, commandObject)
                    cronCommands.remove(oldCommandName.value)
                }
            }
        }



        when (type.value) {
            "SHARE" -> {
                val modifiedJsonContent = gson.toJson(shareCommands)

                jsonService.writeSharesConfig(modifiedJsonContent)

            }

            "FILE_OBSERVER" -> {
                val modifiedJsonContent = gson.toJson(observerCommands)

                jsonService.writeObserversConfig(modifiedJsonContent)
            }

            "CRON" -> {
                val modifiedJsonContent = gson.toJson(cronCommands)

                jsonService.writeCronConfig(modifiedJsonContent)
            }
        }


    }

    private fun storeSecretExtras(commandId: String, extras: List<CommandExtra>, previousCommandId: String) {
        val service = secretsService ?: return
        extras.filter { it.isSecretExtra() }.forEach { extra ->
            val newKey = extra.secretKey(commandId)
            val oldKey = extra.secretKey(previousCommandId)
            val value = extra.default.ifBlank {
                if (oldKey != newKey) service.get(oldKey).orEmpty() else service.get(newKey).orEmpty()
            }

            if (value.isNotEmpty()) {
                service.set(newKey, value)
                if (oldKey != newKey) service.delete(oldKey)
            }
        }
    }

}
