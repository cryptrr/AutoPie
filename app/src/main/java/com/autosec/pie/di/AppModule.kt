package com.autosec.pie.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.autosec.pie.core.DefaultDispatchers
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.apiService.ApiService
import com.autosec.pie.autopieapp.data.apiService.ApiServiceImpl
import com.autosec.pie.autopieapp.data.apiService.AutoSecHTTPClient
import com.autosec.pie.autopieapp.data.apiService.HTTPClientService
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import com.autosec.pie.autopieapp.data.dbService.CommandHistoryDao
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.CronService
import com.autosec.pie.autopieapp.data.services.JSONServiceImpl
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CommandHistoryViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.EditCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.OutputViewerViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import timber.log.Timber

val appModule = module {
    single<DispatcherProvider> { DefaultDispatchers() }
    single<AppDatabase> {
        return@single try {
            Room.databaseBuilder(
                get(),
                AppDatabase::class.java, "autopie-db"
            ).fallbackToDestructiveMigration(true).build()
        }catch (e: Exception){
            Timber.e(e)
            throw e
        }
    }

    single<MainViewModel> { MainViewModel(get(), get(), get()) }
    single<ProcessManagerService> { ProcessManagerService(get(), get(), get()) }
    viewModel<ShareReceiverViewModel> { ShareReceiverViewModel(get())}
    viewModel<OutputViewerViewModel> { OutputViewerViewModel(get())}
    viewModel<CommandHistoryViewModel> { CommandHistoryViewModel(get())}

    viewModel<CloudCommandsViewModel> { CloudCommandsViewModel() }
    viewModel<CloudPackagesViewModel> { CloudPackagesViewModel() }

    single<HTTPClientService> { AutoSecHTTPClient() }

    single<JsonService> { JSONServiceImpl() }
    single<CronService> { CronService(get()) }

    single<ApiService> { ApiServiceImpl(get()) }
    viewModel<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
    viewModel<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
    viewModel<CreateCommandViewModel> { CreateCommandViewModel(get()) }
    viewModel<EditCommandViewModel> { EditCommandViewModel(get(), get()) }
    single<AppPreferences> { AppPreferences(get()) }
    single<AutoPieNotification> { AutoPieNotification(get()) }
}



