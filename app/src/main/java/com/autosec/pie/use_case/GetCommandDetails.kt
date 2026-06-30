package com.autopi.use_case

import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.fromJsonObjectEntries
import com.google.gson.Gson
import kotlinx.coroutines.delay
import timber.log.Timber

class GetCommandDetails(private val jsonService: JsonService) {
    suspend operator fun invoke(key: String) : CommandModel {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")
        val shareCommands = jsonService.readSharesConfig()
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()

        if (shareCommands == null) throw ViewModelError.ShareConfigUnavailable
        if (observerCommands == null) throw ViewModelError.ObserverConfigUnavailable
        if (cronCommands == null) throw ViewModelError.CronConfigUnavailable




        val gson = Gson()
        val sharesData = gson.fromJsonObjectEntries(shareCommands, CommandModel::class.java).values
        val cronData = gson.fromJsonObjectEntries(cronCommands, CommandModel::class.java).values
        val observerData = gson.fromJsonObjectEntries(observerCommands, CommandModel::class.java).values

        val (commandModel, commandType) = when {
            sharesData[key] != null -> Pair(sharesData[key]!!, CommandType.SHARE)
            cronData[key] != null -> Pair(cronData[key]!!, CommandType.CRON)
            observerData[key] != null -> Pair(observerData[key]!!, CommandType.FILE_OBSERVER)
            else -> throw ViewModelError.CommandNotFound
        }

        delay(500L)

        Timber.d("commandType: $commandType")

        return commandModel.copy(
            id = commandModel.id.ifBlank { key },
            type = commandType,
            name = key
        )
    }
}
