package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

class GetShareCommands(private val jsonService: JsonService) {
    suspend operator fun invoke(): List<CommandModel>{
        Timber.d("GetShareCommands useCase")
        val sharesConfig = jsonService.readSharesConfig()

        if(sharesConfig == null){
            Timber.d("Shares file not available")
            throw ViewModelError.ShareConfigUnavailable
        }

        val mapType = object : TypeToken<Map<String, CommandModel>>() {}.type

        val sharesData: Map<String, CommandModel> = Gson().fromJson(sharesConfig, mapType)

        val commandsData = sharesData.entries.toMutableList().map { it.value.copy(type = CommandType.SHARE, name = it.key) }

        return commandsData
    }
}