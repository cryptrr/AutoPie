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
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandModelList
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.services.JSONService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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

    val commandTypeOptions = listOf("Share", "Observer")

    val selectors = mutableStateOf("")


    var selectedCommandType by mutableStateOf("")

    val isValidCommand by derivedStateOf { execFile.value.isNotBlank() && commandName.value.isNotBlank() }

    val formErrorsCount = mutableIntStateOf(0)

    val commandExtras = mutableStateOf<List<CommandExtra>>(emptyList())


    suspend fun getCommandDetails(key: String) {

        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {

            Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")
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

            val selectorsFormatted = try {
                val arr = commandDetails.get("selectors").asJsonArray
                arr.joinToString(",")
            } catch (e: Exception) {
                ""
            }

            delay(500L)

            //Another strategy but for now.

            val mapType = object : TypeToken<Map<String, CommandModel>>() {}.type

            val data: Map<String, CommandModel> = Gson().fromJson(shareCommands, mapType)

            val commandModel = data[key]

            Timber.d("DATA: ${data}")


            withContext(Dispatchers.Main) {
                oldCommandName.value = key
                commandName.value = key
                type.value = commandType
                directory.value = commandDetails.get("path").asString
                execFile.value = commandDetails.get("exec").asString
                command.value = commandDetails.get("command").asString
                deleteSource.value = commandDetails.get("deleteSourceFile").asBoolean
                selectors.value = selectorsFormatted
                selectedCommandType = commandType

                Timber.d("Extras: ${commandModel?.extras}")

                commandModel?.let{
                    commandExtras.value = it.extras ?: emptyList()
                }

                isLoading.value = false
            }
        }
    }


    fun changeCommandDetails(key: String) {

        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        //isLoading.value = true

        //Validate extras if exists.
        val validationError = if (commandExtras.value.isEmpty()) {
            false
        } else {
            commandExtras.value.any { it.name.isBlank() }  || commandExtras.value.any { it.default.isBlank() }
        }

        Timber.d("${commandExtras.value.any { it.name.isBlank() }}")
        Timber.d("${commandExtras.value.any { it.default.isBlank() }}")

//        if (validationError) {
//            main.showError(ViewModelError.Unknown)
//            return
//        }
//
//        Timber.d("NO validation error")

        viewModelScope.launch(Dispatchers.IO) {

            Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

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

            val selectorsJson = if (selectors.value.isNotBlank()) {
                val jsonArray = JsonArray()

                selectors.value.split(",").map { string ->
                    jsonArray.add(JsonParser.parseString(string.trim()))
                }

                jsonArray
            } else {
                JsonArray()
            }

            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()


            Timber.d("commandObject: $commandObject")

            //Key didn't change
            if (oldCommandName.value == commandName.value) {

                Timber.d("${oldCommandName.value} == ${commandName.value}")

                commandObject.addProperty("path", directory.value)
                commandObject.addProperty("exec", execFile.value)
                commandObject.addProperty("command", command.value)
                commandObject.addProperty("deleteSourceFile", deleteSource.value)

                when (type.value) {
                    "SHARE" -> {
                        if(commandExtras.value.isNotEmpty()){
                            commandObject.add("extras", gson.toJsonTree(commandExtras.value))
                        }else{
                            commandObject.remove("extras")
                        }

                        shareCommands.add(commandName.value, commandObject)
                        //shareCommands.remove(oldCommandName.value)
                    }

                    "FILE_OBSERVER" -> {
                        commandObject.add("selectors", selectorsJson)

                        observerCommands.add(commandName.value, commandObject)
                        //observerCommands.remove(oldCommandName.value)
                    }
                }


            } else {

                Timber.d("Command key changed")

                commandObject.addProperty("path", directory.value)
                commandObject.addProperty("exec", execFile.value)
                commandObject.addProperty("command", command.value)
                commandObject.addProperty("deleteSourceFile", deleteSource.value)

                when (type.value) {
                    "SHARE" -> {
                        if(commandExtras.value.isNotEmpty()){
                            commandObject.add("extras", gson.toJsonTree(commandExtras.value))
                        }else{
                            commandObject.remove("extras")
                        }


                        shareCommands.add(commandName.value, commandObject)
                        shareCommands.remove(oldCommandName.value)
                    }

                    "FILE_OBSERVER" -> {
                        commandObject.add("selectors", selectorsJson)


                        observerCommands.add(commandName.value, commandObject)
                        observerCommands.remove(oldCommandName.value)
                    }
                }
            }



            when (type.value) {
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


        viewModelScope.launch(Dispatchers.IO) {

            Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

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

            when (type.value) {
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

            when (type.value) {
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

    fun addCommandExtra(commandExtra: CommandExtra, key: String) {

        if (commandExtras.value.any { it.id == key }) {
            commandExtras.value = commandExtras.value.toMutableList().also {
                val index = it.indexOfFirst { it.id == key }

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

    private fun clear() {
        command.value = ""
        execFile.value = ""
        commandName.value = ""
        selectors.value = ""
        directory.value = "${Environment.getExternalStorageDirectory().absolutePath}/"
        deleteSource.value = false
        selectedCommandType = "SHARE"
    }


}