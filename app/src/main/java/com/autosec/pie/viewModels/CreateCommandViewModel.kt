package com.autosec.pie.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.services.JsonService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CreateCommandViewModel(application: Application, private val jsonService: JsonService) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val commandName = mutableStateOf("")


    val execFile = mutableStateOf("")
    val command = mutableStateOf("")
    val selectors = mutableStateOf("")
    val cronInterval = mutableStateOf("")
    val directory = mutableStateOf("")
    val deleteSource = mutableStateOf(false)

    var selectedICommandTypeIndex by mutableIntStateOf(0)
    val commandTypeOptions = listOf("Share", "Observer", "Cron")

    var selectedCommandType by mutableStateOf("SHARE")

    val isValidCommand by derivedStateOf { execFile.value.isNotBlank() && commandName.value.isNotBlank() }

    val commandExtras = mutableStateOf<List<CommandExtra>>(emptyList())



    fun createNewCommand() {
        viewModelScope.launch(Dispatchers.IO) {

            Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

            val shareCommands = jsonService.readSharesConfig()
            val observerCommands = jsonService.readObserversConfig()
            val cronCommands = jsonService.readCronConfig()

            if (shareCommands == null || observerCommands == null || cronCommands == null) {
                return@launch
            }

            val commandObject = JsonObject()


            commandObject.addProperty("path", directory.value)
            commandObject.addProperty("exec", execFile.value)
            commandObject.addProperty("command", command.value)
            commandObject.addProperty("deleteSourceFile", deleteSource.value)

            val selectorsJson = if(selectors.value.isNotBlank()){
                val jsonArray = JsonArray()

                selectors.value.split(",").map { string ->
                    jsonArray.add(string)
                }

                jsonArray

            }else{
                JsonArray()
            }

            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

            when (selectedCommandType) {
                "SHARE" -> {
                    if(commandExtras.value.isNotEmpty()){
                        commandObject.add("extras", Gson().toJsonTree(commandExtras.value))
                    }else{
                        commandObject.remove("extras")
                    }

                    shareCommands.add(commandName.value, commandObject)
                }
                "FILE_OBSERVER" -> {

                    if(commandExtras.value.isNotEmpty()){
                        commandObject.add("extras", Gson().toJsonTree(commandExtras.value))
                    }else{
                        commandObject.remove("extras")
                    }

                    commandObject.add("selectors", selectorsJson)

                    observerCommands.add(commandName.value, commandObject)
                }
                "CRON" -> {

                    if(commandExtras.value.isNotEmpty()){
                        commandObject.add("extras", Gson().toJsonTree(commandExtras.value))
                    }else{
                        commandObject.remove("extras")
                    }

                    commandObject.addProperty("cronInterval", cronInterval.value)

                    cronCommands.add(commandName.value, commandObject)
                }
            }

            when (selectedCommandType) {
                "SHARE" -> {

                    val modifiedJsonContent = gson.toJson(shareCommands)

                    jsonService.writeSharesConfig(modifiedJsonContent)
                }
                "FILE_OBSERVER" -> {
                    val modifiedJsonContent = gson.toJson(observerCommands)

                    jsonService.writeObserversConfig(modifiedJsonContent)
                }
                "CRON" -> {
                    val modifiedJsonContent = gson.toJson(cronCommands)

                    jsonService.writeCronConfig(modifiedJsonContent)
                }
            }

            delay(1500L)
            clear()
        }

    }

    fun addCommandExtra(commandExtra: CommandExtra) {

        if (commandExtras.value.any { it.id == commandExtra.id }) {
            commandExtras.value = commandExtras.value.toMutableList().also {
                val index = it.indexOfFirst { it.id == commandExtra.id }

                it.set(index, commandExtra)
            }
        } else {
            commandExtras.value += commandExtra
        }

        Timber.d(commandExtras.toString())


    }

    fun removeCommandExtra(key: String) {
        Timber.d("Removing item at $key")
        commandExtras.value = commandExtras.value.filter { it.id != key }
        Timber.d(commandExtras.toString())
    }

    private fun clear(){
        command.value = ""
        execFile.value = ""
        commandName.value = ""
        selectors.value = ""
        directory.value = ""
        deleteSource.value = false
        selectedICommandTypeIndex = 0
        selectedCommandType = "SHARE"
    }


}