package com.autopi.di

import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autopi.core.DispatcherProvider
import com.autopi.core.TestDispatchers
import com.autopi.autopieapp.data.apiService.ApiService
import com.autopi.autopieapp.data.apiService.ApiServiceImpl
import com.autopi.autopieapp.data.apiService.AutoSecHTTPClient
import com.autopi.autopieapp.data.apiService.HTTPClientService
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.data.services.CronService
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autopi.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autopi.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.EditCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autopi.use_case.AutoPieUseCases
import com.autopi.use_case.CreateCommand
import com.autopi.use_case.GetCommandDetails
import com.autopi.use_case.GetCommandsList
import com.autopi.use_case.GetShareCommands
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

fun mockMainViewModel(app: Application): MainViewModel {
    return spyk(MainViewModel(
        app,
        appPreferences = TODO(),
        dispatchers = TODO()
    )) {
        every { storageManagerPermissionGranted } returns true
        every { showError(any()) } just Runs
        every { eventFlow } returns MutableSharedFlow()
    }
}

val useCaseModule = module {
    single<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getShareCommands = GetShareCommands(get()),
            createCommand = CreateCommand(get()),
            getCommandDetails = GetCommandDetails(get()),
            runCommand = TODO(),
            runCommandForDirectory = TODO(),
            runCommandForUrl = TODO(),
            runCommandForFiles = TODO(),
            runCommandForText = TODO(),
            runStandaloneCommand = TODO(),
            changeCommandDetails = TODO(),
            deleteCommand = TODO(),
            addCommandToHistory = TODO(),
            getHistoryOfCommand = TODO(),
            getLatestUsedPackages = TODO()
        )
    }
}



fun getTestModule(dispatcher: TestDispatchers): Module {
    return module {

        single<DispatcherProvider> { dispatcher }

        single<MainViewModel> { mockMainViewModel(get()) }
        viewModel<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
        viewModel<CloudCommandsViewModel> { CloudCommandsViewModel() }
        viewModel<CloudPackagesViewModel> { CloudPackagesViewModel() }

        single<HTTPClientService> { AutoSecHTTPClient() }

        
        single<CronService> { CronService(get()) }



        single<ApiService> { ApiServiceImpl(get()) }
        viewModel<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
        viewModel<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
        viewModel<CreateCommandViewModel> { CreateCommandViewModel(get()) }
        viewModel<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
        single<AppPreferences> { AppPreferences(get()) }
        single<AutoPieNotification> { AutoPieNotification(get()) }
    }
}