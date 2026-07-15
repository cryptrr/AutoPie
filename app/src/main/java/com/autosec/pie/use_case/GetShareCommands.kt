package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.fromJsonObjectEntries
import com.google.gson.Gson
import timber.log.Timber

class GetShareCommands(private val jsonService: JsonService) {
    suspend operator fun invoke(onCommandsSkipped: (List<String>) -> Unit = {}): List<CommandModel>{
        Timber.d("GetShareCommands useCase")
        val commandsConfig = jsonService.readCommandsConfig()

        if(commandsConfig == null){
            Timber.d("Commands file not available")
            throw ViewModelError.CommandConfigUnavailable
        }

        val parsedCommands = Gson().fromJsonObjectEntries(commandsConfig, CommandModel::class.java)
        if (parsedCommands.skippedKeys.isNotEmpty()) {
            val skippedCommands = parsedCommands.skippedKeys.map { "Share: $it" }
            Timber.w("Skipped incompatible commands: $skippedCommands")
            onCommandsSkipped(skippedCommands)
        }

        val commandsData = parsedCommands.values.map {
            it.value.copy(
                id = it.value.id.ifBlank { it.key },
                type = it.value.type ?: CommandType.SHARE,
                name = it.key
            )
        }.filter { it.type == CommandType.SHARE }

        return commandsData
    }
}
