package com.autosec.pie.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.services.JsonService
import com.autosec.pie.use_case.AutoPieUseCases
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
    private val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)

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

        if(!main.storageManagerPermissionGranted){
            main.showError(ViewModelError.StoragePermissionDenied)
            return
        }

        delay(500L)
        viewModelScope.launch(dispatchers.io){

            try {
                useCases.getCommandsList().let {
                    withContext(dispatchers.main){
                        fullListOfCommands = it.sortedBy { it.name }

                        filteredListOfCommands = it.sortedBy { it.name }

                        isLoading.value = false
                    }
                }
            }catch (e: Exception){
                when(e){
                    is ViewModelError.ShareConfigUnavailable -> main.showError(ViewModelError.ShareConfigUnavailable)
                    is ViewModelError.CronConfigUnavailable -> main.showError(ViewModelError.CronConfigUnavailable)
                    is ViewModelError.ObserverConfigUnavailable -> main.showError(ViewModelError.ObserverConfigUnavailable)
                    is ViewModelError.InvalidShareConfig -> main.showError(ViewModelError.InvalidShareConfig)
                    is ViewModelError.InvalidObserverConfig -> main.showError(ViewModelError.InvalidObserverConfig)
                    is ViewModelError.InvalidCronConfig -> main.showError(ViewModelError.InvalidCronConfig)
                }
            }
        }
    }



    fun searchInCommands(query: String){

        filteredListOfCommands = fullListOfCommands.filter { it.name.contains(query.trim(), ignoreCase = true) || it.command.contains(query.trim(), ignoreCase = true) || it.exec.contains(query.trim(), ignoreCase = true) || it.type.toString().contains(query.trim(), ignoreCase = true) }

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