package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.AutoPieConstants
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.apiService.ApiService
import com.autosec.pie.autopieapp.data.services.AutoPieCoreService
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.domain.model.CloudCommandModel
import com.autosec.pie.autopieapp.domain.model.CloudCommandsListDto
import com.autosec.pie.core.Result
import com.autosec.pie.core.asResult
import com.autosec.pie.autopieapp.domain.model.GenericResponseDTO
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.use_case.AutoPieUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import kotlin.getValue

class CloudCommandsViewModel(private val application: Application) : ViewModel(), KoinComponent {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)
    private val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)
    val processManagerService: ProcessManagerService by KoinJavaComponent.inject(
        ProcessManagerService::class.java)

    var fullListOfCommands = MutableStateFlow<List<CloudCommandModel>>(emptyList())
    var fullListOfCommandsShared = fullListOfCommands.asSharedFlow()
    var filteredListOfCommands = MutableStateFlow<List<CloudCommandModel>>(emptyList())


    var selectedICommandTypeIndex by  mutableIntStateOf(0)
    val commandTypeOptions = listOf("All", "Share", "Observers")

    val selectedCommand = mutableStateOf<CloudCommandModel?>(null)

    val searchCommandQuery = mutableStateOf("")
    val isLoading = mutableStateOf(false)

    init {
        viewModelScope.launch {
            getCommandsList()
        }
    }



    fun getCommandsList(){
        isLoading.value = true
        Timber.d("Getting Repo Commands List")

        viewModelScope.launch(dispatchers.io){

            delay(500L)

            try {
                useCases.getRepoCommandsList("${application.filesDir.absolutePath}/repolist.json").let { newCommands ->
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

                        //mostUsedPackages.update { getFrequentPackages(fullListOfCommands.value) }
                        //setFrequentPackages(fullListOfCommands.value)


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
            fullListOfCommands.value.filter { it.name.contains(query.trim(), ignoreCase = true) || it.command.contains(query.trim(), ignoreCase = true) || it.type.toString().contains(query.trim(), ignoreCase = true) }
        }

    }




}