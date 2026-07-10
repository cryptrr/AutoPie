package com.autopi.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.autopi.core.DefaultDispatchers
import com.autopi.core.DispatcherProvider
import com.autopi.autopieapp.data.apiService.ApiService
import com.autopi.autopieapp.data.apiService.ApiServiceImpl
import com.autopi.autopieapp.data.apiService.AutoSecHTTPClient
import com.autopi.autopieapp.data.apiService.HTTPClientService
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.dbService.AppDatabase
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.data.services.CronService
import com.autopi.autopieapp.data.services.JSONServiceImpl
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autopi.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.CommandHistoryViewModel
import com.autopi.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autopi.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.EditCommandViewModel
import com.autopi.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.autopieapp.presentation.viewModels.OutputViewerViewModel
import com.autopi.autopieapp.presentation.viewModels.ShareReceiverViewModel
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

    single<MainViewModel> { MainViewModel(get(), get(), get(), get()) }
    single<ProcessManagerService> { ProcessManagerService(get(), get(), get(), get()) }
    single<SecretsService> { SecretsService(get()) }
    viewModel<ShareReceiverViewModel> { ShareReceiverViewModel(get())}
    viewModel<OutputViewerViewModel> { OutputViewerViewModel(get())}
    viewModel<CommandHistoryViewModel> { CommandHistoryViewModel(get())}

    viewModel<CloudCommandsViewModel> { CloudCommandsViewModel(get()) }
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
    single<AutoPieConfigPathProvider> { AutoPieConfigPathProvider(get(), get()) }
    single<AutoPieNotification> { AutoPieNotification(get(), get()) }
}
