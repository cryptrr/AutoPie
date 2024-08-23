package com.autosec.pie.viewModels

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.services.JSONService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import timber.log.Timber


class EditCommandViewModel(application: Application) : AndroidViewModel(application) {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    init {
        Timber.d(this.toString())
    }

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


}