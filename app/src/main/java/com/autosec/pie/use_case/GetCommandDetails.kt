package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandCreationModel
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import timber.log.Timber

class GetCommandDetails(private val jsonService: JsonService) {
    suspend operator fun invoke(key: String) : Triple<JsonObject, CommandModel?, Pair<String, String>> {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")
        val shareCommands = jsonService.readSharesConfig()
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()

        if (shareCommands == null) throw ViewModelError.ShareConfigUnavailable
        if (observerCommands == null) throw ViewModelError.ObserverConfigUnavailable
        if (cronCommands == null) throw ViewModelError.CronConfigUnavailable

        var commandType = ""

        //TODO: Need to change this abomination
        val commandDetails =
            shareCommands.getAsJsonObject(key).also { if (it != null) commandType = "SHARE" }
                ?: observerCommands.getAsJsonObject(
                    key
                ).also { if (it != null) commandType = "FILE_OBSERVER" }
                ?: cronCommands.getAsJsonObject(
                    key
                ).also { if (it != null) commandType = "CRON" }


        Timber.d("CommandDetails: $commandDetails")

        if (commandDetails == null) {
            throw ViewModelError.CommandNotFound
        }

        val selectorsFormatted = try {
            val arr = commandDetails.get("selectors").asJsonArray
            arr.joinToString(",")
        } catch (e: Exception) {
            ""
        }

        delay(500L)


        //TODO: Make this the new strategy
        //Another strategy but for now.

        val mapType = object : TypeToken<Map<String, CommandModel>>() {}.type

        val sharesData: Map<String, CommandModel> = Gson().fromJson(shareCommands, mapType)
        val cronData: Map<String, CommandModel> = Gson().fromJson(cronCommands, mapType)
        val observerData: Map<String, CommandModel> = Gson().fromJson(observerCommands, mapType)

        val commandModel = sharesData[key] ?: cronData[key] ?: observerData[key]

        return Triple(commandDetails, commandModel, Pair(commandType, selectorsFormatted))
    }
}
