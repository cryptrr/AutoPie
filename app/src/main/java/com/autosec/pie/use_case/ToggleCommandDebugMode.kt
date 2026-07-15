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
        if (shareCommands == null) {
            throw ViewModelError.ConfigUnavailable
        }

        val commandObject = shareCommands.getAsJsonObject(command.name)
            ?: throw ViewModelError.CommandNotFound

        val updatedCommand = Utils.setScriptHeader(command.command, ScriptFlags.INTERACTIVE, enabled)
        commandObject.addProperty("command", updatedCommand)

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        jsonService.writeSharesConfig(gson.toJson(shareCommands))

        return command.copy(command = updatedCommand)
    }
}
