package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandCreationModel
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
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


        //Another strategy but for now.
        //TODO: Make this the new strategy for all


        val mapType = object : TypeToken<Map<String, CommandModel>>() {}.type

        val sharesData: Map<String, CommandModel> = Gson().fromJson(shareCommands, mapType)
        val cronData: Map<String, CommandModel> = Gson().fromJson(cronCommands, mapType)
        val observerData: Map<String, CommandModel> = Gson().fromJson(observerCommands, mapType)

        val (commandModel, commandType) = when {
            sharesData[key] != null -> Pair(sharesData[key]!!, CommandType.SHARE)
            cronData[key] != null -> Pair(cronData[key]!!, CommandType.CRON)
            observerData[key] != null -> Pair(observerData[key]!!, CommandType.FILE_OBSERVER)
            else -> throw ViewModelError.CommandNotFound
        }

        delay(500L)

        Timber.d("commandType: $commandType")

        return commandModel.copy(type = commandType, name = key)
    }
}
