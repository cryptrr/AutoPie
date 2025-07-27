package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.use_case.AutoPieUseCases
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CommandsListScreenViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)
    private val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)

    var fullListOfCommands = MutableStateFlow<List<CommandModel>>(emptyList())
    var fullListOfCommandsShared = fullListOfCommands.asSharedFlow()
    var filteredListOfCommands = MutableStateFlow<List<CommandModel>>(emptyList())

    val mostUsedPackages = MutableStateFlow<List<String>>(emptyList())

    var selectedICommandTypeIndex by  mutableIntStateOf(0)
    val commandTypeOptions = listOf("All", "Share", "Observers")

    val searchCommandQuery = mutableStateOf("")
    val isLoading = mutableStateOf(false)


    init {
        viewModelScope.launch {
            getCommandsList()

            main.eventFlow.collect{
                when(it){
                    is ViewModelEvent.RefreshCommandsList -> {
                        getCommandsList()
                    }
                    else -> {}
                }
            }
        }
    }


    fun getCommandsList(){
        isLoading.value = true

        if(!main.storageManagerPermissionGranted){
            main.showError(ViewModelError.StoragePermissionDenied)
            Timber.e(ViewModelError.StoragePermissionDenied)
            return
        }

        viewModelScope.launch(dispatchers.io){

            delay(500L)

            try {
                useCases.getCommandsList().let { newCommands ->
                    withContext(dispatchers.main){
                        fullListOfCommands.update {
                            newCommands.sortedBy { it.name }
                        }

                        filteredListOfCommands.update {
                            newCommands.sortedBy { it.name }
                        }

                        if(searchCommandQuery.value.isNotEmpty()){
                            searchInCommands(searchCommandQuery.value)
                        }

                        mostUsedPackages.update { getFrequentPackages(fullListOfCommands.value) }

                        isLoading.value = false
                    }
                }
            }catch (e: Exception){
                Timber.e(e)

                when(e){
                    is java.io.FileNotFoundException -> {}
                    is ViewModelError.InvalidShareConfig -> main.showError(ViewModelError.InvalidShareConfig)
                    is ViewModelError.InvalidObserverConfig -> main.showError(ViewModelError.InvalidObserverConfig)
                    is ViewModelError.InvalidCronConfig -> main.showError(ViewModelError.InvalidCronConfig)
                }
            }
        }
    }



    fun searchInCommands(query: String){

        filteredListOfCommands.update {
            fullListOfCommands.value.filter { it.name.contains(query.trim(), ignoreCase = true) || it.command.contains(query.trim(), ignoreCase = true) || it.exec.contains(query.trim(), ignoreCase = true) || it.type.toString().contains(query.trim(), ignoreCase = true) }
        }

    }

    fun getFrequentPackages(input: List<CommandModel>): List<String>{
        val frequencyMap = input.map{it.exec}.groupingBy { it }.eachCount()
        val packages = frequencyMap.entries.sortedByDescending { it.value }.map { it.key }.take(7)

        return packages
    }

    fun filterOnlyShareCommands(){

        filteredListOfCommands.update {
            it.filter { it.type == CommandType.SHARE }
        }

    }

    fun filterOnlyObserverCommands(){

        filteredListOfCommands.update {
            it.filter { it.type == CommandType.FILE_OBSERVER }
        }

    }

    fun noFilter(){

        filteredListOfCommands.update {
            fullListOfCommands.value
        }
    }



}