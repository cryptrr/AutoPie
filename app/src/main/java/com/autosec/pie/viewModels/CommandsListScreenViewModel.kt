package com.autosec.pie.viewModels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.services.JSONService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CommandsListScreenViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    var fullListOfCommands by mutableStateOf<List<CommandModel>>(emptyList())
    var filteredListOfCommands by mutableStateOf<List<CommandModel>>(emptyList())

    var selectedICommandTypeIndex by  mutableIntStateOf(0)
    val commandTypeOptions = listOf("All", "Share", "Observers")

    val searchCommandQuery = mutableStateOf("")
    val isLoading = mutableStateOf(false)


    init {
        viewModelScope.launch {
            getCommandsList()

            main.eventFlow.collect{
                when(it){
                    is ViewModelEvent.RefreshCommandsList -> getCommandsList()
                    else -> {}
                }
            }
        }
    }


    suspend fun getCommandsList(){
        isLoading.value = true
        delay(500L)
        viewModelScope.launch(Dispatchers.IO){
            val sharesConfig = JSONService.readSharesConfig()
            val observersConfig = JSONService.readObserversConfig()
            val cronConfig = JSONService.readCronConfig()

            if(sharesConfig == null){
                Timber.d("Observers file not available")
                main.sharesConfigAvailable = false
                return@launch
            }else{
                main.schedulerConfigAvailable = true
            }

            if(observersConfig == null){
                Timber.d("Observers file not available")
                main.sharesConfigAvailable = false
                return@launch
            }else{
                main.schedulerConfigAvailable = true
            }
            if(cronConfig == null){
                Timber.d("Cron file not available")
                main.sharesConfigAvailable = false
                return@launch
            }else{
                main.schedulerConfigAvailable = true
            }

            val tempList = mutableListOf<CommandModel>()

            for (entry in sharesConfig.entrySet()) {
                val key = entry.key
                val value = entry.value.asJsonObject
                // Process the key-value pair

                val directoryPath = value.get("path").asString
                val exec = value.get("exec").asString
                val command = value.get("command").asString
                val deleteSource = value.get("deleteSourceFile").asBoolean

                val extrasJsonArray = value.getAsJsonArray("extras")

                val extrasListType = object : TypeToken<List<CommandExtra>>() {}.type

                val extras: List<CommandExtra> = try{
                    Gson().fromJson(extrasJsonArray, extrasListType)
                }catch(e: Exception){
                    emptyList()
                }

                val shareObject = CommandModel(
                    name = key,
                    path = directoryPath,
                    command = command,
                    exec = exec,
                    deleteSourceFile = deleteSource,
                    type = CommandType.SHARE,
                    extras = extras
                )

                tempList.add(shareObject)
            }

            for (entry in observersConfig.entrySet()) {
                val key = entry.key
                val value = entry.value.asJsonObject
                // Process the key-value pair

                val directoryPath = value.get("path").asString
                val exec = value.get("exec").asString
                val command = value.get("command").asString
                val deleteSource = value.get("deleteSourceFile").asBoolean

                val extrasJsonArray = value.getAsJsonArray("extras")

                val extrasListType = object : TypeToken<List<CommandExtra>>() {}.type

                val extras: List<CommandExtra> = try{
                    Gson().fromJson(extrasJsonArray, extrasListType)
                }catch(e: Exception){
                    emptyList()
                }

                val shareObject = CommandModel(
                    name = key,
                    path = directoryPath,
                    command = command,
                    exec = exec,
                    deleteSourceFile = deleteSource,
                    type = CommandType.FILE_OBSERVER,
                    extras = extras
                )

                tempList.add(shareObject)
            }

            for (entry in cronConfig.entrySet()) {
                val key = entry.key
                val value = entry.value.asJsonObject
                // Process the key-value pair

                val directoryPath = value.get("path").asString
                val exec = value.get("exec").asString
                val command = value.get("command").asString
                val deleteSource = value.get("deleteSourceFile").asBoolean

                val cronObject = CommandModel(
                    name = key,
                    path = directoryPath,
                    command = command,
                    exec = exec,
                    deleteSourceFile = deleteSource,
                    type = CommandType.CRON
                )

                tempList.add(cronObject)
            }

            withContext(Dispatchers.Main){
                fullListOfCommands = tempList.sortedBy { it.name }

                filteredListOfCommands = tempList.sortedBy { it.name }

                isLoading.value = false
            }


        }
    }



    fun searchInCommands(query: String){

        filteredListOfCommands = fullListOfCommands.filter { it.name.contains(query, ignoreCase = true) || it.command.contains(query, ignoreCase = true) || it.exec.contains(query, ignoreCase = true) || it.type.toString().contains(query, ignoreCase = true) }

    }

    fun filterOnlyShareCommands(){

        filteredListOfCommands = fullListOfCommands.filter { it.type == CommandType.SHARE }

    }

    fun filterOnlyObserverCommands(){

        filteredListOfCommands = fullListOfCommands.filter { it.type == CommandType.FILE_OBSERVER }

    }

    fun noFilter(){

        filteredListOfCommands = fullListOfCommands

    }



}