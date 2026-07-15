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
        if (shareCommands == null) throw ViewModelError.ShareConfigUnavailable

        val gson = Gson()
        val sharesData = gson.fromJsonObjectEntries(shareCommands, CommandModel::class.java).values
        val commandModel = sharesData[key] ?: throw ViewModelError.CommandNotFound

        delay(500L)

        val commandType = commandModel.type ?: CommandType.SHARE

        return commandModel.copy(
            id = commandModel.id.ifBlank { key },
            type = commandType,
            name = key
        )
    }
}
