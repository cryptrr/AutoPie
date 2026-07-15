package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.fromJsonObjectEntries
import com.google.gson.Gson
import timber.log.Timber

class GetCommandsList(private val jsonService: JsonService) {
    operator fun invoke(onCommandsSkipped: (List<String>) -> Unit = {}): List<CommandModel>{
        val sharesConfig = jsonService.readSharesConfig()
        if(sharesConfig == null){
            Timber.d("Shares file not available")
            throw ViewModelError.ShareConfigUnavailable
        }

        val gson = Gson()
        val sharesData = gson.fromJsonObjectEntries(sharesConfig, CommandModel::class.java)

        val skippedCommands = sharesData.skippedKeys.map { "Share: $it" }

        if (skippedCommands.isNotEmpty()) {
            Timber.w("Skipped incompatible commands: $skippedCommands")
            onCommandsSkipped(skippedCommands)
        }

        val commandsData = sharesData.values.map {
            it.value.copy(
                id = it.value.id.ifBlank { it.key },
                type = it.value.type ?: CommandType.SHARE,
                name = it.key
            )
        }

        return commandsData

    }
}
