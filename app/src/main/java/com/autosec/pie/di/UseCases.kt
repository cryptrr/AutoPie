package com.autopi.di

import com.autopi.use_case.AddCommandToHistory
import com.autopi.use_case.AddUserTag
import com.autopi.use_case.AutoPieUseCases
import com.autopi.use_case.ChangeCommandDetails
import com.autopi.use_case.CreateCommand
import com.autopi.use_case.DeleteCommand
import com.autopi.use_case.DeleteUserTag
import com.autopi.use_case.GetCommandDetails
import com.autopi.use_case.GetCommandsList
import com.autopi.use_case.GetHistoryOfCommand
import com.autopi.use_case.GetInstalledPackages
import com.autopi.use_case.GetLatestUsedPackages
import com.autopi.use_case.GetRepoCommandsList
import com.autopi.use_case.GetShareCommands
import com.autopi.use_case.GetUserTags
import com.autopi.use_case.RunCommand
import com.autopi.use_case.RunCommandForDirectory
import com.autopi.use_case.RunCommandForFiles
import com.autopi.use_case.RunCommandForText
import com.autopi.use_case.RunCommandForUrl
import com.autopi.use_case.RunInteractiveCommand
import com.autopi.use_case.RunStandaloneCommand
import com.autopi.use_case.ToggleCommandDebugMode
import org.koin.dsl.module

val useCaseModule = module {
    single<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getShareCommands = GetShareCommands(get()),
            createCommand = CreateCommand(get(), get()),
            getCommandDetails = GetCommandDetails(get()),
            runCommand = RunCommand(),
            runCommandForUrl = RunCommandForUrl(get()),
            runCommandForFiles = RunCommandForFiles(get()),
            runCommandForDirectory = RunCommandForDirectory(get()),
            runCommandForText = RunCommandForText(get()),
            runStandaloneCommand =  RunStandaloneCommand(get()),
            changeCommandDetails = ChangeCommandDetails(get(), get()),
            deleteCommand = DeleteCommand(get()),
            addCommandToHistory = AddCommandToHistory(get()),
            getHistoryOfCommand = GetHistoryOfCommand(get()),
            getLatestUsedPackages = GetLatestUsedPackages(get()),
            getUserTags = GetUserTags(get()),
            addUserTag = AddUserTag(get()),
            deleteUserTag = DeleteUserTag(get()),
            getInstalledPackages = GetInstalledPackages(get()),
            getRepoCommandsList = GetRepoCommandsList(get()),
            runInteractiveCommand = RunInteractiveCommand(get()),
            toggleCommandDebugMode = ToggleCommandDebugMode(get())
        )
    }
}
