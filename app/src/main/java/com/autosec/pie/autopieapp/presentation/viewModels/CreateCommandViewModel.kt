package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandCreationModel
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.use_case.AutoPieUseCases
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CreateCommandViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)
    private val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)



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
        viewModelScope.launch(dispatchers.io) {

            try {
                val newCommand = CommandCreationModel(
                    selectedCommandType = selectedCommandType,
                    commandName = commandName.value,
                    directory = directory.value,
                    command = command.value,
                    deleteSourceFile = deleteSource.value,
                    isValidCommand = isValidCommand,
                    exec = execFile.value,
                    commandExtras = commandExtras.value,
                    selectors = selectors.value,
                    cronInterval = cronInterval.value
                )

                useCases.createCommand(newCommand).let{
                    delay(1500L)
                    clear()
                }
            }catch (e: Exception){
                when(e){
                    is ViewModelError -> main.showError(e)
                    else -> Timber.e(e)
                }
            }
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