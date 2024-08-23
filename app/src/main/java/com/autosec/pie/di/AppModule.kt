package com.autosec.pie.di

import com.autosec.pie.data.preferences.AppPreferences
import com.autosec.pie.viewModels.CommandsListScreenViewModel
import com.autosec.pie.viewModels.CreateCommandViewModel
import com.autosec.pie.viewModels.EditCommandViewModel
import com.autosec.pie.viewModels.InstalledPackagesViewModel
import com.autosec.pie.viewModels.MainViewModel
import com.autosec.pie.viewModels.ShareReceiverViewModel
import org.koin.dsl.module

val appModule = module {
    single<MainViewModel> { MainViewModel(get()) }
    single<ShareReceiverViewModel> { ShareReceiverViewModel(get()) }
    single<CommandsListScreenViewModel> { CommandsListScreenViewModel(get()) }
    single<InstalledPackagesViewModel> { InstalledPackagesViewModel(get()) }
    single<CreateCommandViewModel> { CreateCommandViewModel(get()) }
    single<EditCommandViewModel> { EditCommandViewModel(get()) }
    single<AppPreferences> { AppPreferences(get()) }

}
