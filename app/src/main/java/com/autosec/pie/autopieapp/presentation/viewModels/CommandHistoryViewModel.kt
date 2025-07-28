package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.use_case.AutoPieUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import kotlin.getValue

class CommandHistoryViewModel(private val application1: Application) : ViewModel() {

    val main: MainViewModel by inject(MainViewModel::class.java)
    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)

    var commandsHistoryResult = MutableStateFlow<List<CommandHistoryEntity>>(emptyList())
    var commandDetails = MutableStateFlow<CommandModel?>(null)


    fun getCommandHistory(name: String) {

        Timber.v("GetCommandHistory")

        viewModelScope.launch(dispatchers.io){
            try {
                useCases.getHistoryOfCommand(name).collectLatest{ history ->
                    commandsHistoryResult.update { history }
                }
            }catch (e: Exception){
                Timber.e(e)
                main.showError(ViewModelError.Unknown)
            }
        }
    }

    fun getCommand(id: String) {

        Timber.v("getCommand")

        viewModelScope.launch(dispatchers.io){
            try {
                useCases.getCommandDetails(id).let{ command ->
                    commandDetails.update { command }
                }
            }catch (e: Exception){
                Timber.e(e)
                main.showError(ViewModelError.Unknown)
            }
        }
    }
}




