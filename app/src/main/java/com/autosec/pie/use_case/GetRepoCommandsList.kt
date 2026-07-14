package com.autopi.use_case

import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GetRepoCommandsList(private val jsonService: JsonService) {
    operator fun invoke(path: String): List<CloudCommandModel>{
        val repoList = jsonService.readRepoList(path) ?: throw ViewModelError.CommandRepoUnavailable
        val gson = Gson()

        repoList.getAsJsonObject("commands")?.let { commands ->
            return commands.entrySet().map { (id, jsonElement) ->
                val command = gson.fromJson(jsonElement, CloudCommandModel::class.java)
                command.copy(
                    id = id,
                    type = CommandType.SHARE,
                    name = command.name.ifBlank { id },
                    command = "",
                    description = command.summary
                )
            }
        }

        val mapType = object : TypeToken<Map<String, CloudCommandModel>>() {}.type

        val repoData: Map<String, CloudCommandModel> = gson.fromJson(repoList, mapType)

        val commandsData = repoData.entries.toMutableList().map { (id, command) ->
            command.copy(type = CommandType.SHARE, id = id.ifBlank { command.id }, name = command.name.ifBlank { id })
        }

        return commandsData

    }
}
