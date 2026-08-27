package com.autopi.autopieapp.presentation.viewModels

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autopi.core.DispatcherProvider
import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.use_case.AutoPieUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val rawJson = mutableStateOf("")

    var selectedCreationModeIndex by mutableIntStateOf(0)
    val creationModeOptions = listOf("Form", "Raw JSON")
    val isRawJsonMode: Boolean
        get() = selectedCreationModeIndex == 1

    var selectedICommandTypeIndex by mutableIntStateOf(0)
    val commandTypeOptions = listOf("Share", "Observer", "Cron")

    var selectedCommandType by mutableStateOf("SHARE")

    val isValidCommand by derivedStateOf {
        if (isRawJsonMode) rawJson.value.isNotBlank() else commandName.value.isNotBlank()
    }

    val commandExtras = mutableStateOf<List<CommandExtra>>(emptyList())



    fun createNewCommand(onSuccess: () -> Unit = {}) {
        viewModelScope.launch(dispatchers.io) {

            try {
                if (isRawJsonMode) {
                    useCases.createCommand.fromRawJson(rawJson.value)
                } else {
                    val newCommand = CommandCreationModel(
                        selectedCommandType = selectedCommandType,
                        commandName = commandName.value,
                        directory = directory.value,
                        command = command.value,
                        isValidCommand = isValidCommand,
                        commandExtras = commandExtras.value,
                        selectors = selectors.value,
                        cronInterval = cronInterval.value
                    )
                    useCases.createCommand(newCommand)
                }
                main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                main.dispatchEvent(ViewModelEvent.CommandsConfigChanged)
                withContext(dispatchers.main) {
                    clear()
                    onSuccess()
                }
            }catch (e: Exception){
                when(e){
                    is ViewModelError -> main.showError(e)
                    else -> Timber.e(e)
                }
            }
        }
    }

    fun cloneCommand(command: CommandModel) {
        viewModelScope.launch(dispatchers.io) {



            try {
                val clonedCommand = CommandCreationModel(
                    selectedCommandType = command.type.toString(),
                    commandName = "${command.name}:COPY",
                    directory = command.path,
                    command = command.command,
                    isValidCommand = true,
                    exec = command.exec,
                    commandExtras = command.extras ?: emptyList(),
                    selectors = "",
                    cronInterval = ""
                )

                useCases.createCommand(clonedCommand).let{
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

    fun toggleCommandDebugMode(command: CommandModel, enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val updatedCommand = useCases.toggleCommandDebugMode(command, enabled)
                main.currentSelectedCommand.value = updatedCommand
                main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
            } catch (e: Exception) {
                when (e) {
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
        rawJson.value = ""
        selectedCreationModeIndex = 0
        selectedICommandTypeIndex = 0
        selectedCommandType = "SHARE"
    }


}
