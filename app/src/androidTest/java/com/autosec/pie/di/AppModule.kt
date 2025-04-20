package com.autosec.pie.di

import android.app.Application
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.core.TestDispatchers
import com.autosec.pie.autopieapp.data.apiService.ApiService
import com.autosec.pie.autopieapp.data.apiService.ApiServiceImpl
import com.autosec.pie.autopieapp.data.apiService.AutoSecHTTPClient
import com.autosec.pie.autopieapp.data.apiService.HTTPClientService
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.CronService
import com.autosec.pie.autopieapp.data.services.FakeJSONService
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
import org.koin.core.module.Module
import org.koin.dsl.module

fun mockMainViewModel(app: Application): MainViewModel {
    return spyk(MainViewModel(app)) {
        every { storageManagerPermissionGranted } returns true
        every { showError(any()) } just Runs
        every { eventFlow } returns MutableSharedFlow()
    }
}

val useCaseModule = module {
    factory<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getShareCommands = GetShareCommands(get()),
            createCommand = CreateCommand(get()),
            getCommandDetails = GetCommandDetails(get())
        )
    }
}

val testModule = module {


    single<TestCoroutineScheduler> {  TestCoroutineScheduler() }


    single<DispatcherProvider> { TestDispatchers(get()) }

    single<MainViewModel> { mockMainViewModel(get()) }
    single<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
    single<CloudCommandsViewModel> { CloudCommandsViewModel() }
    single<CloudPackagesViewModel> { CloudPackagesViewModel() }

    single<HTTPClientService> { AutoSecHTTPClient() }


    single<JsonService> { FakeJSONService() }

    single<CronService> { CronService(get()) }



    single<ApiService> { ApiServiceImpl(get()) }
    single<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
    single<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
    single<CreateCommandViewModel> { CreateCommandViewModel(get()) }
    single<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
    single<AppPreferences> { AppPreferences(get()) }
    single<AutoPieNotification> { AutoPieNotification(get()) }
}

fun getTestModule(dispatcher: TestDispatchers): Module {
    return module {

        single<DispatcherProvider> { dispatcher }

        single<MainViewModel> { mockMainViewModel(get()) }
        single<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
        single<CloudCommandsViewModel> { CloudCommandsViewModel() }
        single<CloudPackagesViewModel> { CloudPackagesViewModel() }

        single<HTTPClientService> { AutoSecHTTPClient() }


        single<JsonService> { FakeJSONService() }

        single<CronService> { CronService(get()) }



        single<ApiService> { ApiServiceImpl(get()) }
        single<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
        single<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
        single<CreateCommandViewModel> { CreateCommandViewModel(get()) }
        single<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
        single<AppPreferences> { AppPreferences(get()) }
        single<AutoPieNotification> { AutoPieNotification(get()) }
    }
}