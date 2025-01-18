package com.autosec.pie.autopieapp.presentation.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.apiService.ApiService
import com.autosec.pie.autopieapp.domain.model.CloudCommandModel
import com.autosec.pie.autopieapp.domain.model.CloudCommandsListDto
import com.autosec.pie.core.Result
import com.autosec.pie.core.asResult
import com.autosec.pie.autopieapp.domain.model.GenericResponseDTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CloudCommandsViewModel() : ViewModel(), KoinComponent {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    private val  _stateFlow = MutableStateFlow<Result<GenericResponseDTO<CloudCommandsListDto>>>(Result.None)
    val stateFlow = _stateFlow.asSharedFlow()

    var selectedCommand = mutableStateOf<CloudCommandModel?>(null)
    var searchQuery = mutableStateOf<String>("")

    val isLoading = mutableStateOf(true)


    var cloudCommandsList by mutableStateOf<List<CloudCommandModel>>(emptyList())
    var currentCursor by mutableStateOf("")
    var hasNext by mutableStateOf(false)

    val apiService by inject<ApiService>()

    fun getCloudCommands() {
        viewModelScope.launch {
            apiService.getCloudCommandsList().asResult().collectLatest {
                _stateFlow.value = it
                when(it){
                    is Result.Success -> {
                        cloudCommandsList = it.data.data.items
                        currentCursor = it.data.data.cursor
                        hasNext = it.data.data.hasNext

                        Timber.d ("commands list size : ${it.data.data.items.size}" )

                    }
                    else -> {}
                }
            }

        }
    }

    fun getMoreCloudCommands() {
        Timber.d("Getting more blocked of user ")
        currentCursor?.let{cursor ->
            if(hasNext){
                viewModelScope.launch {
                    apiService.getMoreCloudCommandsList(cursor).asResult().collectLatest {
                        when(it){
                            is Result.Success -> {
                                cloudCommandsList =  cloudCommandsList + it.data.data.items
                                currentCursor = it.data.data.cursor
                                hasNext = it.data.data.hasNext

                                Timber.d ("Load more commands size : ${it.data.data.items.size}")


                            }
                            else -> {}
                        }
                    }

                }
            }
        }
    }

    fun searchCloudCommands() {
        viewModelScope.launch {
            apiService.searchCloudCommands(searchQuery.value).asResult().collectLatest {
                //_stateFlow.value = it
                when(it){
                    is Result.Success -> {
                        cloudCommandsList = it.data.data.items
                        currentCursor = it.data.data.cursor
                        hasNext = it.data.data.hasNext

                        Timber.d ("commands list size : ${it.data.data.items.size}" )

                    }
                    else -> {}
                }
            }

        }
    }


}