package com.autopi.di

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
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

