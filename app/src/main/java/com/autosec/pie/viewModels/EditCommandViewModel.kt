package com.autosec.pie.viewModels

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.services.JSONService
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import timber.log.Timber


class EditCommandViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)


    val oldCommandName = mutableStateOf("")
    val commandName = mutableStateOf("")
    val execFile = mutableStateOf("")
    val command = mutableStateOf("")
    val directory = mutableStateOf("")
    val type = mutableStateOf("")
    val deleteSource = mutableStateOf(false)

    val isLoading = mutableStateOf(true)


    suspend fun getCommandDetails(key: String) {

        Timber.tag("ThreadCheck").d( "Running on: ${Thread.currentThread().name}")

        isLoading.value = true

        viewModelScope.launch(Dispatchers.IO){

            Timber.tag("ThreadCheck").d( "Running on: ${Thread.currentThread().name}")
            val shareCommands = JSONService.readSharesConfig()
            val observerCommands = JSONService.readObserversConfig()

            if (shareCommands == null || observerCommands == null) {
                return@launch
            }

            var commandType = ""

            val commandDetails = shareCommands.getAsJsonObject(key)
                .also { if (it != null) commandType = "SHARE" } ?: observerCommands.getAsJsonObject(
                key
            ).also { if (it != null) commandType = "FILE_OBSERVER" }


            Timber.d("CommandDetails: $commandDetails")

            if (commandDetails == null) {
                return@launch
            }

            delay(500L)

            withContext(Dispatchers.Main){
                oldCommandName.value = key
                commandName.value = key
                type.value = commandType
                directory.value = commandDetails.get("path").asString
                execFile.value = commandDetails.get("exec").asString
                command.value = commandDetails.get("command").asString
                deleteSource.value = commandDetails.get("deleteSourceFile").asBoolean
                isLoading.value = false
            }
        }
    }


   fun changeCommandDetails(key: String) {

        Timber.tag("ThreadCheck").d( "Running on: ${Thread.currentThread().name}")

        //isLoading.value = true

        viewModelScope.launch(Dispatchers.IO){

            Timber.tag("ThreadCheck").d( "Running on: ${Thread.currentThread().name}")

            val shareCommands = JSONService.readSharesConfig()
            val observerCommands = JSONService.readObserversConfig()

            if (shareCommands == null || observerCommands == null) {
                return@launch
            }

            var commandType = ""

            val commandObject = shareCommands.getAsJsonObject(oldCommandName.value)
                .also { if (it != null) commandType = "SHARE" } ?: observerCommands.getAsJsonObject(
                key
            ).also { if (it != null) commandType = "FILE_OBSERVER" }


            Timber.d("commandObject: $commandObject")

            if (oldCommandName.value == commandName.value) {

                Timber.d("${oldCommandName.value} == ${commandName.value}")

                commandObject.addProperty("path", directory.value)
                commandObject.addProperty("exec", execFile.value)
                commandObject.addProperty("command", command.value)
                commandObject.addProperty("deleteSourceFile", deleteSource.value)

                when(type.value){
                    "SHARE" -> {
                        shareCommands.add(commandName.value, commandObject)
                        //shareCommands.remove(oldCommandName.value)
                    }

                    "FILE_OBSERVER" -> {
                        observerCommands.add(commandName.value, commandObject)
                        //observerCommands.remove(oldCommandName.value)
                    }
                }


            }else{

                Timber.d("Command key changed")

                commandObject.addProperty("path", directory.value)
                commandObject.addProperty("exec", execFile.value)
                commandObject.addProperty("command", command.value)
                commandObject.addProperty("deleteSourceFile", deleteSource.value)

                when(type.value){
                    "SHARE" -> {
                        shareCommands.add(commandName.value, commandObject)
                        shareCommands.remove(oldCommandName.value)
                    }

                    "FILE_OBSERVER" -> {
                        observerCommands.add(commandName.value, commandObject)
                        observerCommands.remove(oldCommandName.value)
                    }
                }
            }


            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

            when(type.value){
                "SHARE" -> {
                    val modifiedJsonContent = gson.toJson(shareCommands)

                    JSONService.writeSharesConfig(modifiedJsonContent)

                }

                "FILE_OBSERVER" -> {
                    val modifiedJsonContent = gson.toJson(observerCommands)

                    JSONService.writeObserversConfig(modifiedJsonContent)


                }
            }




        }

    }

    fun deleteCommand(key: String) {


        viewModelScope.launch(Dispatchers.IO){

            Timber.tag("ThreadCheck").d( "Running on: ${Thread.currentThread().name}")

            val shareCommands = JSONService.readSharesConfig()
            val observerCommands = JSONService.readObserversConfig()

            if (shareCommands == null || observerCommands == null) {
                return@launch
            }

            var commandType = ""

            val commandObject = shareCommands.getAsJsonObject(oldCommandName.value)
                .also { if (it != null) commandType = "SHARE" } ?: observerCommands.getAsJsonObject(
                key
            ).also { if (it != null) commandType = "FILE_OBSERVER" }


            Timber.d("commandObject: $commandObject")

            when(type.value){
                "SHARE" -> {
                    shareCommands.remove(commandName.value)
                    //shareCommands.remove(oldCommandName.value)
                }

                "FILE_OBSERVER" -> {
                    observerCommands.remove(commandName.value)
                    //observerCommands.remove(oldCommandName.value)
                }
            }


            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

            when(type.value){
                "SHARE" -> {
                    val modifiedJsonContent = gson.toJson(shareCommands)

                    JSONService.writeSharesConfig(modifiedJsonContent)

                }

                "FILE_OBSERVER" -> {
                    val modifiedJsonContent = gson.toJson(observerCommands)

                    JSONService.writeObserversConfig(modifiedJsonContent)


                }
            }




        }

    }


}