package com.autosec.pie.di

import com.autosec.pie.core.DefaultDispatchers
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.data.apiService.ApiService
import com.autosec.pie.data.apiService.ApiServiceImpl
import com.autosec.pie.data.apiService.AutoSecHTTPClient
import com.autosec.pie.data.apiService.HTTPClientService
import com.autosec.pie.data.preferences.AppPreferences
import com.autosec.pie.notifications.AutoPieNotification
import com.autosec.pie.services.CronService
import com.autosec.pie.services.FakeJSONService
import com.autosec.pie.services.JSONServiceImpl
import com.autosec.pie.services.JsonService
import com.autosec.pie.viewModels.CloudCommandsViewModel
import com.autosec.pie.viewModels.CloudPackagesViewModel
import com.autosec.pie.viewModels.CommandsListScreenViewModel
import com.autosec.pie.viewModels.CreateCommandViewModel
import com.autosec.pie.viewModels.EditCommandViewModel
import com.autosec.pie.viewModels.InstalledPackagesViewModel
import com.autosec.pie.viewModels.MainViewModel
import com.autosec.pie.viewModels.ShareReceiverViewModel
import org.koin.dsl.module

val appModule = module {
    single<DispatcherProvider> { DefaultDispatchers() }

    single<MainViewModel> { MainViewModel(get()) }
    single<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
    single<CloudCommandsViewModel> { CloudCommandsViewModel() }
    single<CloudPackagesViewModel> { CloudPackagesViewModel() }

    single<HTTPClientService> { AutoSecHTTPClient() }

    single<JsonService> { JSONServiceImpl() }
    single<CronService> { CronService(get()) }

    single<ApiService> { ApiServiceImpl(get()) }
    single<CommandsListScreenViewModel> { CommandsListScreenViewModel(get(), get()) }
    single<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
    single<CreateCommandViewModel> { CreateCommandViewModel(get(), get()) }
    single<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
    single<AppPreferences> { AppPreferences(get()) }
    single<AutoPieNotification> { AutoPieNotification(get()) }
}



