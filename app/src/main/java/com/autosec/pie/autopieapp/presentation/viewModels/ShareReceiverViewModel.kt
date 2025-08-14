package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.utils.Utils
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

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
    val mostUsedPackages: StateFlow<List<String>> =
        getFrequentPackages(shareItemsResult)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10000), emptyList())

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    val currentExtrasDetails = mutableStateOf<Triple<Boolean, CommandModel, ShareInputs>?>(null)
    val commandNotFound = mutableStateOf<Boolean?>(false)

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
                    //setFrequentPackages(shareItemsResult.value)
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

    fun getFrequentPackages(
        inputFlow: StateFlow<List<CommandModel>>
    ): Flow<List<String>> {

        val latestUsedFlow = useCases.getLatestUsedPackages(3).flowOn(dispatchers.io)

        val userTagsFlow = useCases.getUserTags()
            .flowOn(dispatchers.io)

        return combine(
            inputFlow,
            latestUsedFlow,
            userTagsFlow
        ) { input, latestUsed, userTags ->
            val frequencyMap = input.map { it.exec }
                .groupingBy { it }
                .eachCount()

            val packages = frequencyMap.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(7)

            LinkedHashSet(
                (packages - latestUsed.toSet()) + latestUsed + userTags
            ).toList()
        }
    }


    fun runShareCommand(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int)  {


        Timber.d(item.toString())
        Timber.d(currentLink.toString())


        viewModelScope.launch(dispatchers.io){
            try {

                val logsFile = Utils.getFileWithPrefix(application1.cacheDir.absolutePath, processId.toString()) ?: File(application1.cacheDir, "dummy")

                useCases.runCommand(item, currentLink, fileUris, commandExtraInputs, processId).catch { e ->

                    main.dispatchEvent(ViewModelEvent.CommandFailed(processId, item, logsFile.absolutePath))
                    Timber.e(e)

                }.collect{ receipt ->
                    if (receipt.success) {
                        Timber.d("Process Success".uppercase())
                        autoPieNotification.sendNotification("Command Success", "${item.name} ${receipt.jobKey}",item, receipt.output)
                        main.dispatchEvent(ViewModelEvent.CommandCompleted(processId, item, logsFile.absolutePath))
                    } else {
                        Timber.d("Process FAILED".uppercase())
                        autoPieNotification.sendNotification("Command Failed", "${item.name} ${receipt.jobKey}",item, receipt.output)
                        main.dispatchEvent(ViewModelEvent.CommandFailed(processId, item, logsFile.absolutePath))
                    }

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

    fun selectCommandFromDirectActivity(commandId: String, input: String?,callerType: String, activity: Activity?): Boolean{
        try {
            val command = shareItemsResult.value.find { it.name == commandId }

            if(command == null){
                Timber.d("Command not found: $commandId")
                commandNotFound.value = true
                return false
            }

            commandNotFound.value = null

            if (command.extras?.isNotEmpty() == true) {
                Timber.d("Opening Extras sheet for $commandId")
                currentExtrasDetails.value =
                    Triple(true, command, ShareInputs(input, null))
            }
            else if(callerType == "EXTERNAL_APP"){
                currentExtrasDetails.value =
                    Triple(true, command, ShareInputs(input, null))
            }
            else{
                onCommandClick(command, emptyList(), input) {
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

    fun onCommandClickWithExtras(command: CommandModel,currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput>, processId: Int? = null){
        viewModelScope.launch {

            try {

                val gson = Gson()
                val commandJson = gson.toJson(command)
                val fileUrisJson = gson.toJson(fileUris)

                val commandExtraInputsJson = gson.toJson(commandExtraInputs)

                //Timber.d(" fileUrisJson: $fileUrisJson \n commandExtraInputsJson: $commandExtraInputsJson \n extraInputList: $extraInputList \n extraInput: $extraInput \n fileUris: $fileUris")

                val intent = Intent(application1, ForegroundService::class.java).apply {
                    putExtra("command", commandJson)
                    putExtra("currentLink", currentLink)
                    putExtra("fileUris", fileUrisJson)
                    putExtra("commandExtraInputs", commandExtraInputsJson)
                    putExtra("processId", processId)
                }


                startForegroundService(application1, intent)

            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }


}