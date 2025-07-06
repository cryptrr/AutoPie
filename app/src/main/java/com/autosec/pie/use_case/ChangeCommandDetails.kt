package com.autosec.pie.use_case

import androidx.compose.runtime.MutableState
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import timber.log.Timber

class ChangeCommandDetails(private val jsonService: JsonService) {
    suspend operator fun invoke(key: String, commandExtras: MutableState<List<CommandExtra>>, oldCommandName: MutableState<String>, selectors: MutableState<String>, commandName: MutableState<String>, directory: MutableState<String>, execFile: MutableState<String>, command: MutableState<String>, deleteSource: MutableState<Boolean>, type: MutableState<String>, cronInterval: MutableState<String>) {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        //isLoading.value = true

        //Validate extras if exists.
        val validationError = if (commandExtras.value.isEmpty()) {
            false
        } else {
            commandExtras.value.any { it.name.isBlank() } || commandExtras.value.any { it.default.isBlank() }
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


        Timber.d("commandObject: $commandObject")

        //Key didn't change
        if (oldCommandName.value == commandName.value) {

            Timber.d("${oldCommandName.value} == ${commandName.value}")

            commandObject.addProperty("path", directory.value)
            commandObject.addProperty("exec", execFile.value)
            commandObject.addProperty("command", command.value)
            commandObject.addProperty("deleteSourceFile", deleteSource.value)

            when (type.value) {
                "SHARE" -> {
                    if (commandExtras.value.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(commandExtras.value))
                    } else {
                        commandObject.remove("extras")
                    }

                    shareCommands.add(commandName.value, commandObject)
                    //shareCommands.remove(oldCommandName.value)
                }

                "FILE_OBSERVER" -> {
                    if (commandExtras.value.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(commandExtras.value))
                    } else {
                        commandObject.remove("extras")
                    }

                    commandObject.add("selectors", selectorsJson)

                    observerCommands.add(commandName.value, commandObject)
                    //observerCommands.remove(oldCommandName.value)
                }

                "CRON" -> {
                    if (commandExtras.value.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(commandExtras.value))
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
            commandObject.addProperty("deleteSourceFile", deleteSource.value)

            when (type.value) {
                "SHARE" -> {
                    if (commandExtras.value.isNotEmpty()) {
                        commandObject.add("extras", gson.toJsonTree(commandExtras.value))
                    } else {
                        commandObject.remove("extras")
                    }


                    shareCommands.add(commandName.value, commandObject)
                    shareCommands.remove(oldCommandName.value)
                }

                "FILE_OBSERVER" -> {
                    commandObject.add("selectors", selectorsJson)


                    observerCommands.add(commandName.value, commandObject)
                    observerCommands.remove(oldCommandName.value)
                }

                "CRON" -> {
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

}