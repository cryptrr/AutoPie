package com.autosec.pie.di

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
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

val testModule = module {


    single<TestCoroutineScheduler> {  TestCoroutineScheduler() }


    single<DispatcherProvider> { TestDispatchers(get()) }

    single<MainViewModel> { MainViewModel(get()) }
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

fun getTestModule(scheduler: TestCoroutineScheduler): Module {
    return module {


        single<TestCoroutineScheduler> { scheduler }


        single<DispatcherProvider> { TestDispatchers(get()) }

        single<MainViewModel> { MainViewModel(get()) }
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