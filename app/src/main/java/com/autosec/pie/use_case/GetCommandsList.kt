package com.autopi.use_case

import android.os.Environment
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
        val observersConfig = jsonService.readObserversConfig()
        val cronConfig = jsonService.readCronConfig()

        if(sharesConfig == null){
            Timber.d("Shares file not available")
            throw ViewModelError.ShareConfigUnavailable
        }

        if(observersConfig == null){
            Timber.d("Observers file not available")
            throw ViewModelError.ObserverConfigUnavailable
        }
        if(cronConfig == null){
            Timber.d("Cron file not available")
            throw ViewModelError.CronConfigUnavailable
        }

        val gson = Gson()
        val sharesData = gson.fromJsonObjectEntries(sharesConfig, CommandModel::class.java)
        val cronData = gson.fromJsonObjectEntries(cronConfig, CommandModel::class.java)
        val observerData = gson.fromJsonObjectEntries(observersConfig, CommandModel::class.java)

        val skippedCommands =
            sharesData.skippedKeys.map { "Share: $it" } +
                cronData.skippedKeys.map { "Cron: $it" } +
                observerData.skippedKeys.map { "Observer: $it" }

        if (skippedCommands.isNotEmpty()) {
            Timber.w("Skipped incompatible commands: $skippedCommands")
            onCommandsSkipped(skippedCommands)
        }

        val commandsData = sharesData.values.map { it.value.copy(type = CommandType.SHARE, name = it.key) } +
                cronData.values.map { it.value.copy(type = CommandType.CRON, name = it.key) } +
                observerData.values.map { it.value.copy(type = CommandType.FILE_OBSERVER, name = it.key) }

        return commandsData

    }
}
