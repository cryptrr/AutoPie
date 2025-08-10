package com.autosec.pie.di

import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.core.TestDispatchers
import com.autosec.pie.autopieapp.data.apiService.ApiService
import com.autosec.pie.autopieapp.data.apiService.ApiServiceImpl
import com.autosec.pie.autopieapp.data.apiService.AutoSecHTTPClient
import com.autosec.pie.autopieapp.data.apiService.HTTPClientService
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.CronService
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.EditCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.use_case.CreateCommand
import com.autosec.pie.use_case.GetCommandDetails
import com.autosec.pie.use_case.GetCommandsList
import com.autosec.pie.use_case.GetShareCommands
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