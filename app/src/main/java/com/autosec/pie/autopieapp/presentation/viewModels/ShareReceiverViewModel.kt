package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.isValidUrl
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class ShareReceiverViewModel(private val application1: Application) : ViewModel() {

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
    }

    val main: MainViewModel by inject(MainViewModel::class.java)
    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)

    private val appPreferences: AppPreferences by inject(AppPreferences::class.java)

    var shareItemsResult = MutableStateFlow<List<CommandModel>>(emptyList())
    var filteredShareItemsResult = MutableStateFlow<List<CommandModel>>(emptyList())
    val mostUsedPackages = MutableStateFlow<List<String>>(emptyList())


    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    val currentExtrasDetails = mutableStateOf<Triple<Boolean, CommandModel, ShareInputs>?>(null)

    init {
        try {
            viewModelScope.launch {
                getSharesConfig()

                main.eventFlow.collect{
                    when(it){
                        is ViewModelEvent.SharesConfigChanged -> {
                            Timber.d("Share config changed: Restarting")
                            getSharesConfig()
                        }
                        else -> {}
                    }
                }
            }
        }catch (e:Exception){
            Timber.e(e)
        }
    }

    fun search(query: String) {

        Timber.d("Searching ${query}")

        filteredShareItemsResult.update {
            shareItemsResult.value.filter { it.name.contains(query.trim(), ignoreCase = true) || it.command.contains(query.trim(), ignoreCase = true) || it.exec.contains(query.trim(), ignoreCase = true) || it.type.toString().contains(query.trim(), ignoreCase = true) }
        }

    }


    fun getSharesConfig() {

        Timber.d("GetSharesConfig")

        if(!main.storageManagerPermissionGranted){
            main.showError(ViewModelError.StoragePermissionDenied)
            return
        }


        viewModelScope.launch(dispatchers.io){
            try {
                useCases.getShareCommands().let{ newCommands ->
                    shareItemsResult.update { newCommands }
                    filteredShareItemsResult.update { newCommands }
                    main.shareReceiverSearchQuery.value.let {
                        if(it.isNotEmpty()){
                            search(it)
                        }
                    }
                    mostUsedPackages.update { getFrequentPackages(shareItemsResult.value) }
                }
            }catch (e: Exception){
                when(e){
                    is FileNotFoundException -> {}
                    is ViewModelError.ShareConfigUnavailable -> main.showError(ViewModelError.ShareConfigUnavailable)
                    is ViewModelError.InvalidShareConfig -> main.showError(ViewModelError.InvalidShareConfig)
                    else -> main.showError(ViewModelError.Unknown)
                }
            }
        }
    }

    fun getFrequentPackages(input: List<CommandModel>): List<String>{
        val frequencyMap = input.map{it.exec}.groupingBy { it }.eachCount()
        val packages = frequencyMap.entries.sortedByDescending { it.value }.map { it.key }.take(7)

        return packages
    }


    fun runShareCommand(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int)  {


        Timber.d(item.toString())
        Timber.d(currentLink.toString())

        viewModelScope.launch(dispatchers.io){
            try {
                useCases.runShareCommand(item, currentLink, fileUris, commandExtraInputs, processId).catch { e ->

                    main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))
                    Timber.e(e)

                }.collect{ receipt ->
                    if (receipt.success) {
                        Timber.d("Process Success".uppercase())
                        autoPieNotification.sendNotification("Command Success", "${item.name} ${receipt.jobKey}",item, receipt.output)

                    } else {
                        Timber.d("Process FAILED".uppercase())
                        autoPieNotification.sendNotification("Command Failed", "${item.name} ${receipt.jobKey}",item, receipt.output)
                    }

                    main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))

                }
            }catch (e: Exception){
                Timber.e(e)
            }
        }

        viewModelScope.launch {
            delay(900L)
            currentExtrasDetails.value = null
        }
    }

    fun selectCommandFromDirectActivity(commandId: String, activity: Activity?): Boolean{
        try {
            val command = shareItemsResult.value.find { it.name == commandId }

            if(command == null){
                Timber.d("Command not found: $commandId")
                return false
            }

            if (command.extras?.isNotEmpty() == true) {
                Timber.d("Opening Extras sheet for $commandId")
                currentExtrasDetails.value =
                    Triple(true, command, ShareInputs(null, null))
            }else{
                onCommandClick(command, emptyList(), null) {
                    viewModelScope.launch {
                        delay(1000L)
                        currentExtrasDetails.value = null
                        activity?.finish()
                    }
                }
            }
            return true
        }catch (e: Exception){
            Timber.e(e)
            return false
        }
    }


    fun onCommandClick(card: CommandModel, fileUris: List<String>, currentLink: String?, onComplete: () -> Unit){
        viewModelScope.launch {
            try {
                val commandJson = Gson().toJson(card)
                val fileUrisJson = Gson().toJson(fileUris)

                val intent = Intent(application1, ForegroundService::class.java).apply {
                    putExtra("command", commandJson)
                    putExtra("currentLink", currentLink)
                    putExtra("fileUris", fileUrisJson)
                }

                startForegroundService(application1, intent)

                onComplete()

            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }


}