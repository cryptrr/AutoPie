package com.autosec.pie.viewModels


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.apiService.ApiService
import com.autosec.pie.domain.model.CloudCommandModel
import com.autosec.pie.domain.model.CloudCommandsListDto
import com.autosec.pie.core.Result
import com.autosec.pie.core.asResult
import com.autosec.pie.domain.model.CloudPackageListDTO
import com.autosec.pie.domain.model.CloudPackageModel
import com.autosec.pie.domain.model.GenericResponseDTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class CloudPackagesViewModel() : ViewModel(), KoinComponent {

    val main: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    private val  _stateFlow = MutableStateFlow<Result<GenericResponseDTO<CloudPackageListDTO>>>(Result.None)
    val stateFlow = _stateFlow.asSharedFlow()

    var selectedPackage = mutableStateOf<CloudPackageModel?>(null)
    var searchQuery = mutableStateOf<String>("")

    val isLoading = mutableStateOf(true)


    var cloudPackagesList by mutableStateOf<List<CloudPackageModel>>(emptyList())
    var currentCursor by mutableStateOf("")
    var hasNext by mutableStateOf(false)

    val apiService by inject<ApiService>()

    fun getPackages() {
        viewModelScope.launch {
            apiService.getPackages(searchQuery.value).asResult().collectLatest {
                _stateFlow.value = it
                when(it){
                    is Result.Success -> {
                        cloudPackagesList = it.data.data.items
                        currentCursor = it.data.data.cursor
                        hasNext = it.data.data.hasNext

                        Timber.d ("commands list size : ${it.data.data.items.size}" )

                    }
                    else -> {}
                }
            }

        }
    }

    fun getMorePackages() {
        Timber.d("Getting more blocked of user ")
        currentCursor?.let{cursor ->
            if(hasNext){
                viewModelScope.launch {
                    apiService.getMorePackages(searchQuery.value,cursor).asResult().collectLatest {
                        when(it){
                            is Result.Success -> {
                                cloudPackagesList =  cloudPackagesList + it.data.data.items
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



}