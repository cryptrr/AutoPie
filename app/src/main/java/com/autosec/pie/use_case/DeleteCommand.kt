package com.autosec.pie.use_case

import androidx.compose.runtime.MutableState
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import timber.log.Timber

class DeleteCommand(private val jsonService: JsonService) {
    suspend operator fun invoke(key: String, commandName: MutableState<String>, oldCommandName: MutableState<String>,type: MutableState<String>) {
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


        Timber.d("commandObject: $commandObject")

        when (type.value) {
            "SHARE" -> {
                shareCommands.remove(commandName.value)
                //shareCommands.remove(oldCommandName.value)
            }

            "FILE_OBSERVER" -> {
                observerCommands.remove(commandName.value)
                //observerCommands.remove(oldCommandName.value)
            }
            "CRON" -> {
                cronCommands.remove(commandName.value)
                //observerCommands.remove(oldCommandName.value)
            }
        }


        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

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