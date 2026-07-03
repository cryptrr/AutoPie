package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.ScriptFlags
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.utils.Utils
import com.google.gson.GsonBuilder

class ToggleCommandDebugMode(private val jsonService: JsonService) {
    suspend operator fun invoke(command: CommandModel, enabled: Boolean): CommandModel {
        val shareCommands = jsonService.readSharesConfig()
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()

        if (shareCommands == null || observerCommands == null || cronCommands == null) {
            throw ViewModelError.ConfigUnavailable
        }

        val commandObject = when (command.type) {
            CommandType.SHARE -> shareCommands.getAsJsonObject(command.name)
            CommandType.FILE_OBSERVER -> observerCommands.getAsJsonObject(command.name)
            CommandType.CRON -> cronCommands.getAsJsonObject(command.name)
            null -> shareCommands.getAsJsonObject(command.name)
                ?: observerCommands.getAsJsonObject(command.name)
                ?: cronCommands.getAsJsonObject(command.name)
        } ?: throw ViewModelError.CommandNotFound

        val updatedCommand = Utils.setScriptHeader(command.command, ScriptFlags.INTERACTIVE, enabled)
        commandObject.addProperty("command", updatedCommand)

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        when (command.type) {
            CommandType.SHARE -> jsonService.writeSharesConfig(gson.toJson(shareCommands))
            CommandType.FILE_OBSERVER -> jsonService.writeObserversConfig(gson.toJson(observerCommands))
            CommandType.CRON -> jsonService.writeCronConfig(gson.toJson(cronCommands))
            null -> when {
                shareCommands.has(command.name) -> jsonService.writeSharesConfig(gson.toJson(shareCommands))
                observerCommands.has(command.name) -> jsonService.writeObserversConfig(gson.toJson(observerCommands))
                cronCommands.has(command.name) -> jsonService.writeCronConfig(gson.toJson(cronCommands))
            }
        }

        return command.copy(command = updatedCommand)
    }
}
