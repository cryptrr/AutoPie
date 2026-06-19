package com.autopi.use_case

import android.os.Environment
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Flow

class GetRepoCommandsList(private val jsonService: JsonService) {
    operator fun invoke(path: String): List<CloudCommandModel>{
        val repoList = jsonService.readRepoList(path)

        val mapType = object : TypeToken<Map<String, CloudCommandModel>>() {}.type

        val repoData: Map<String, CloudCommandModel> = Gson().fromJson(repoList, mapType)


        val commandsData = repoData.entries.toMutableList().map { it.value.copy(type = CommandType.SHARE, name = it.key) }

        return commandsData

    }
}