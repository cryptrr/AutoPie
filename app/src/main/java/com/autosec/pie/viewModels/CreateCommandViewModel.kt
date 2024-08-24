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
import com.autosec.pie.services.JSONService
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CreateCommandViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val commandName = mutableStateOf("")


    val execFile = mutableStateOf("")
    val command = mutableStateOf("")
    val selectors = mutableStateOf("")
    val directory = mutableStateOf("${Environment.getExternalStorageDirectory().absolutePath}/")
    val deleteSource = mutableStateOf(false)

    var selectedICommandTypeIndex by mutableIntStateOf(0)
    val commandTypeOptions = listOf("Share", "Observer")

    var selectedCommandType by mutableStateOf("SHARE")

    val isValidCommand by derivedStateOf { execFile.value.isNotBlank() && commandName.value.isNotBlank() }


    fun createNewCommand() {
        viewModelScope.launch(Dispatchers.IO) {

            Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

            val shareCommands = JSONService.readSharesConfig()
            val observerCommands = JSONService.readObserversConfig()

            if (shareCommands == null || observerCommands == null) {
                return@launch
            }

            val commandObject = JsonObject()


            commandObject.addProperty("path", directory.value)
            commandObject.addProperty("exec", execFile.value)
            commandObject.addProperty("command", command.value)
            commandObject.addProperty("deleteSourceFile", deleteSource.value)

            when (selectedCommandType) {
                "SHARE" -> {
                    shareCommands.add(commandName.value, commandObject)
                }
                "FILE_OBSERVER" -> {
                    observerCommands.add(commandName.value, commandObject)
                }
            }

            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

            when (selectedCommandType) {
                "SHARE" -> {
                    val modifiedJsonContent = gson.toJson(shareCommands)

                    JSONService.writeSharesConfig(modifiedJsonContent)
                }
                "FILE_OBSERVER" -> {
                    val modifiedJsonContent = gson.toJson(observerCommands)

                    JSONService.writeObserversConfig(modifiedJsonContent)
                }
            }

            delay(1500L)
            clear()
        }

    }

    private fun clear(){
        command.value = ""
        execFile.value = ""
        commandName.value = ""
        selectors.value = ""
        directory.value = "${Environment.getExternalStorageDirectory().absolutePath}/"
        deleteSource.value = false
        selectedICommandTypeIndex = 0
        selectedCommandType = "SHARE"
    }


}