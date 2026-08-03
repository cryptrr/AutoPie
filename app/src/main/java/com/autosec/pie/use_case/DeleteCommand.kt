package com.autopi.use_case

import androidx.compose.runtime.MutableState
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.domain.ViewModelError
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import timber.log.Timber

class DeleteCommand(private val jsonService: JsonService) {
    suspend operator fun invoke(key: String, commandName: MutableState<String>, oldCommandName: MutableState<String>,type: MutableState<String>) {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        val commands = jsonService.readCommandsConfig()
        if (commands == null) {
            throw ViewModelError.CommandConfigUnavailable
        }
        commands.remove(commandName.value)


        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        jsonService.writeCommandsConfig(gson.toJson(commands))
    }

}
