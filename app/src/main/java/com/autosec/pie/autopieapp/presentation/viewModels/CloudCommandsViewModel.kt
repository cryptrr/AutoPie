package com.autopi.autopieapp.presentation.viewModels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopi.autopieapp.data.AutoPieConstants
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.apiService.ApiService
import com.autopi.autopieapp.data.services.GithubApiService
import com.autopi.autopieapp.data.services.AutoPieCoreService
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.autopi.autopieapp.domain.model.CloudCommandsListDto
import com.autopi.use_case.CloudCommandDocumentation
import com.autopi.core.Result
import com.autopi.core.asResult
import com.autopi.autopieapp.domain.model.GenericResponseDTO
import com.autopi.core.DispatcherProvider
import com.autopi.use_case.AutoPieUseCases
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
    var installedCommandVersions = MutableStateFlow<Map<String, String>>(emptyMap())


    var selectedICommandTypeIndex by  mutableIntStateOf(0)
    val commandTypeOptions = listOf("All", "Share", "Observers")

    val selectedCommand = mutableStateOf<CloudCommandModel?>(null)

    val searchCommandQuery = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val installInProgress = mutableStateOf(false)
    val detailsLoading = mutableStateOf(false)
    val selectedCommandDocumentation = mutableStateOf<CloudCommandDocumentation?>(null)
    private var loadedDocumentationCommandId: String? = null

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
                val installedVersions = getInstalledCommandVersions()
                useCases.getRepoCommandsList("${application.filesDir.absolutePath}/repolist.json").let { newCommands ->
                    withContext(dispatchers.main){
                        val sortedCommands = sortCloudCommandsForCatalog(newCommands, installedVersions)
                        installedCommandVersions.update { installedVersions }

                        fullListOfCommands.update {
                            sortedCommands
                        }

                        filteredListOfCommands.update {
                            sortedCommands
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

                withContext(dispatchers.main) {
                    isLoading.value = false
                }

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
            val trimmedQuery = query.trim()
            val filteredCommands = fullListOfCommands.value.filter {
                it.id.contains(trimmedQuery, ignoreCase = true) ||
                    it.name.contains(trimmedQuery, ignoreCase = true) ||
                    it.namespace.contains(trimmedQuery, ignoreCase = true) ||
                    it.status.contains(trimmedQuery, ignoreCase = true) ||
                    it.summary.contains(trimmedQuery, ignoreCase = true) ||
                    it.version.contains(trimmedQuery, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(trimmedQuery, ignoreCase = true) }
            }
            sortCloudCommandsForCatalog(filteredCommands, installedCommandVersions.value)
        }

    }

    fun installSelectedCommand(onInstalled: () -> Unit = {}) {
        val command = selectedCommand.value ?: return
        if (installInProgress.value) return

        installInProgress.value = true
        viewModelScope.launch(dispatchers.io) {
            try {
                useCases.installCloudCommand(
                    command.id,
                    selectedCommandDocumentation.value
                        ?.takeIf { loadedDocumentationCommandId == command.id }
                        ?.manifestYaml
                )
                withContext(dispatchers.main) {
                    installInProgress.value = false
                    installedCommandVersions.update { it + (command.id to command.version) }
                    fullListOfCommands.update {
                        sortCloudCommandsForCatalog(it, installedCommandVersions.value)
                    }
                    searchInCommands(searchCommandQuery.value)
                    main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                    main.dispatchEvent(ViewModelEvent.SharesConfigChanged)
                    onInstalled()
                }
            } catch (error: Exception) {
                Timber.e(error)
                withContext(dispatchers.main) {
                    installInProgress.value = false
                    main.showError(error as? ViewModelError ?: ViewModelError.Unknown)
                }
            }
        }
    }

    fun loadSelectedCommandDocumentation() {
        val command = selectedCommand.value ?: return
        if (loadedDocumentationCommandId == command.id && selectedCommandDocumentation.value != null) return
        if (detailsLoading.value) return

        detailsLoading.value = true
        selectedCommandDocumentation.value = null
        viewModelScope.launch(dispatchers.io) {
            try {
                val docs = useCases.getCloudCommandDocumentation(command.id)
                withContext(dispatchers.main) {
                    loadedDocumentationCommandId = command.id
                    selectedCommandDocumentation.value = docs
                    detailsLoading.value = false
                }
            } catch (error: Exception) {
                Timber.e(error)
                withContext(dispatchers.main) {
                    loadedDocumentationCommandId = null
                    selectedCommandDocumentation.value = null
                    detailsLoading.value = false
                    main.showError(error as? ViewModelError ?: ViewModelError.Unknown)
                }
            }
        }
    }

    private suspend fun getInstalledCommandVersions(): Map<String, String> =
        try {
            useCases.getShareCommands()
                .filter { it.id.isNotBlank() }
                .associate { it.id to it.version }
        } catch (error: Exception) {
            Timber.w(error, "Unable to read installed share command versions")
            emptyMap()
        }




}

internal fun isCloudCommandUpdateAvailable(catalogVersion: String, installedVersion: String): Boolean {
    if (catalogVersion.isBlank()) return false
    val comparableInstalledVersion = installedVersion.ifBlank { "0" }
    return GithubApiService.compareVersions(catalogVersion, comparableInstalledVersion) > 0
}

internal fun sortCloudCommandsForCatalog(
    commands: List<CloudCommandModel>,
    installedVersions: Map<String, String>
): List<CloudCommandModel> =
    commands.sortedWith(
        compareByDescending<CloudCommandModel> { command ->
            installedVersions[command.id]?.let { installedVersion ->
                isCloudCommandUpdateAvailable(command.version, installedVersion)
            } == true
        }.thenBy { it.name.lowercase() }
    )
