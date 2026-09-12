package com.autopi.di

import com.autopi.core.DispatcherProvider
import com.autopi.core.TestDispatchers
import com.autopi.autopieapp.data.apiService.ApiService
import com.autopi.autopieapp.data.apiService.ApiServiceImpl
import com.autopi.autopieapp.data.apiService.AutoSecHTTPClient
import com.autopi.autopieapp.data.apiService.HTTPClientService
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.data.services.CronService
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autopi.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autopi.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.EditCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autopi.use_case.AutoPieUseCases
import com.autopi.use_case.GetCommandsList
import com.google.gson.JsonParser
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

fun mockMainViewModel(): MainViewModel {
    return mockk(relaxed = true) {
        every { storageManagerPermissionGranted } returns true
        every { showError(any()) } just Runs
        every { eventFlow } returns MutableSharedFlow()
    }
}

val useCaseModule = module {
    single<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getRepoCommandsList = mockk(relaxed = true),
            getShareCommands = mockk(relaxed = true),
            createCommand = mockk(relaxed = true),
            getCommandDetails = mockk(relaxed = true),
            runCommand = mockk(relaxed = true),
            runCommandForDirectory = mockk(relaxed = true),
            runCommandForUrl = mockk(relaxed = true),
            runCommandForFiles = mockk(relaxed = true),
            runCommandForText = mockk(relaxed = true),
            runStandaloneCommand = mockk(relaxed = true),
            runCronCommand = mockk(relaxed = true),
            changeCommandDetails = mockk(relaxed = true),
            deleteCommand = mockk(relaxed = true),
            addCommandToHistory = mockk(relaxed = true),
            getHistoryOfCommand = mockk(relaxed = true),
            getLatestUsedPackages = mockk(relaxed = true),
            getUserTags = mockk(relaxed = true),
            addUserTag = mockk(relaxed = true),
            deleteUserTag = mockk(relaxed = true),
            getInstalledPackages = mockk(relaxed = true),
            runInteractiveCommand = mockk(relaxed = true),
            toggleCommandDebugMode = mockk(relaxed = true),
            storeCommandExtraInputs = mockk(relaxed = true),
            installCloudCommand = mockk(relaxed = true),
            getCloudCommandDocumentation = mockk(relaxed = true)
        )
    }
}



fun getTestModule(dispatcher: TestDispatchers): Module {
    return module {

        single<DispatcherProvider> { dispatcher }

        single<JsonService> {
            mockk {
                every { readCommandsConfig() } returns JsonParser.parseString(
                    """
                    {
                      "Extract Audio": { "id": "extract-audio", "type": "SHARE" },
                      "RSYNC Sync Folder": { "id": "rsync-sync-folder", "type": "SHARE" }
                    }
                    """.trimIndent()
                ).asJsonObject
            }
        }
        single<MainViewModel> { mockMainViewModel() }
        single<SecretsService> { SecretsService(get()) }
        viewModel<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
        viewModel<CloudCommandsViewModel> { CloudCommandsViewModel(get()) }
        viewModel<CloudPackagesViewModel> { CloudPackagesViewModel() }

        single<HTTPClientService> { AutoSecHTTPClient() }

        
        single<CronService> { CronService(get()) }



        single<ApiService> { ApiServiceImpl(get()) }
        viewModel<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
        viewModel<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
        viewModel<CreateCommandViewModel> { CreateCommandViewModel(get()) }
        viewModel<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
        single<AppPreferences> { AppPreferences(get()) }
        single<AutoPieConfigPathProvider> { AutoPieConfigPathProvider(get(), get()) }
        single<AutoPieNotification> { AutoPieNotification(get(), get()) }
    }
}
