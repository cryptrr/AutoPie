package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Flow

class GetCommandsList(private val jsonService: JsonService) {
    operator fun invoke(): List<CommandModel>{
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

        val mapType = object : TypeToken<Map<String, CommandModel>>() {}.type

        val sharesData: Map<String, CommandModel> = Gson().fromJson(sharesConfig, mapType)
        val cronData: Map<String, CommandModel> = Gson().fromJson(cronConfig, mapType)
        val observerData: Map<String, CommandModel> = Gson().fromJson(observersConfig, mapType)

        val commandsData = sharesData.entries.toMutableList().map { it.value.copy(type = CommandType.SHARE, name = it.key) } +
                cronData.entries.toMutableList().map { it.value.copy(type = CommandType.CRON, name = it.key) } +
                observerData.entries.toMutableList().map { it.value.copy(type = CommandType.FILE_OBSERVER, name = it.key) }

        return commandsData

    }
}