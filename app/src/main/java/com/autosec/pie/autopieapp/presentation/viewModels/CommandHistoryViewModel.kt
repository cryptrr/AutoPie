package com.autopi.autopieapp.presentation.viewModels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopi.autopieapp.data.CommandHistoryEntity
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.core.DispatcherProvider
import com.autopi.use_case.AutoPieUseCases
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




