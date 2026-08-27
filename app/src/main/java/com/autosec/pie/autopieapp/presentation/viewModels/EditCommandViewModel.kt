package com.autopi.autopieapp.presentation.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autopi.core.DispatcherProvider
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.use_case.AutoPieUseCases
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import timber.log.Timber


class EditCommandViewModel(application: Application, private val jsonService: JsonService) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    private val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)


    val oldCommandName = mutableStateOf("")
    val commandName = mutableStateOf("")
    val execFile = mutableStateOf("")
    val command = mutableStateOf("")
    val directory = mutableStateOf("")
    val type = mutableStateOf("")

    val isLoading = mutableStateOf(true)

    val commandTypeOptions = listOf("Share", "Observer")

    val selectors = mutableStateOf("")
    val cronInterval = mutableStateOf("")
    val rawJson = mutableStateOf("")

    var selectedEditorModeIndex by mutableIntStateOf(0)
    val editorModeOptions = listOf("Form", "Raw JSON")
    val isRawJsonMode: Boolean
        get() = selectedEditorModeIndex == 1


    var selectedCommandType by mutableStateOf("")

    val isValidCommand by derivedStateOf {
        if (isRawJsonMode) rawJson.value.isNotBlank() else commandName.value.isNotBlank()
    }

    val formErrorsCount = mutableIntStateOf(0)

    val commandExtras = mutableStateOf<List<CommandExtra>>(emptyList())


    suspend fun getCommandDetails(key: String) {

        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        isLoading.value = true

        viewModelScope.launch(dispatchers.io) {

            useCases.getCommandDetails(key).let{ commandModel ->
                val rawCommand = jsonService.readCommandsConfig()?.get(key)
                    ?: throw ViewModelError.CommandNotFound
                val rawCommandWrapper = JsonObject().apply {
                    add(key, rawCommand.deepCopy())
                }
                val prettyRawCommand = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()
                    .toJson(rawCommandWrapper)
                withContext(dispatchers.main) {
                    oldCommandName.value = key
                    commandName.value = key
                    //TODO: Careful
                    type.value = commandModel.type.toString()
                    directory.value = commandModel.path
                    execFile.value = commandModel.exec
                    command.value = commandModel.command
                    selectors.value = commandModel.selectors?.joinToString(",") ?: ""
                    cronInterval.value = commandModel.cronInterval ?: ""
                    rawJson.value = prettyRawCommand
                    selectedEditorModeIndex = if (commandModel.multiStage == true) 1 else 0

                    selectedCommandType = commandModel.type.toString()


                    commandExtras.value = commandModel.extras ?: emptyList()


                    isLoading.value = false
                }
            }



        }
    }


    fun changeCommandDetails(key: String, onSuccess: () -> Unit = {}) {

        viewModelScope.launch(dispatchers.io){
            try {
                if (isRawJsonMode) {
                    useCases.changeCommandDetails.fromRawJson(key, rawJson.value)
                } else {
                    useCases.changeCommandDetails(key, commandExtras, oldCommandName, selectors, commandName, directory, execFile, command, type, cronInterval)
                }
                main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                main.dispatchEvent(ViewModelEvent.CommandsConfigChanged)
                withContext(dispatchers.main) { onSuccess() }

            }catch (e: ViewModelError){
                main.showError(e)
            }catch (e: Exception){
                Timber.e(e)
            }
        }

    }

    fun deleteCommand(key: String) {

        viewModelScope.launch(dispatchers.io) {
            try {
                useCases.deleteCommand(key, commandName, oldCommandName, type)
            }catch (e: ViewModelError){
                main.showError(e)
            }catch (e: Exception){
                Timber.e(e)
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
            commandExtras.value =
                commandExtras.value.toMutableList().also { it.add(0, commandExtra) }
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
        selectedCommandType = "SHARE"
    }
}
