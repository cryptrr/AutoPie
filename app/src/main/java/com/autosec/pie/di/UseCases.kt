package com.autosec.pie.di

import com.autosec.pie.use_case.AddCommandToHistory
import com.autosec.pie.use_case.AddUserTag
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.use_case.ChangeCommandDetails
import com.autosec.pie.use_case.CreateCommand
import com.autosec.pie.use_case.DeleteCommand
import com.autosec.pie.use_case.DeleteUserTag
import com.autosec.pie.use_case.GetCommandDetails
import com.autosec.pie.use_case.GetCommandsList
import com.autosec.pie.use_case.GetHistoryOfCommand
import com.autosec.pie.use_case.GetLatestUsedPackages
import com.autosec.pie.use_case.GetShareCommands
import com.autosec.pie.use_case.GetUserTags
import com.autosec.pie.use_case.RunCommand
import com.autosec.pie.use_case.RunCommandForDirectory
import com.autosec.pie.use_case.RunCommandForFiles
import com.autosec.pie.use_case.RunCommandForText
import com.autosec.pie.use_case.RunCommandForUrl
import com.autosec.pie.use_case.RunStandaloneCommand
import org.koin.dsl.module

val useCaseModule = module {
    single<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getShareCommands = GetShareCommands(get()),
            createCommand = CreateCommand(get()),
            getCommandDetails = GetCommandDetails(get()),
            runCommand = RunCommand(),
            runCommandForUrl = RunCommandForUrl(get()),
            runCommandForFiles = RunCommandForFiles(get()),
            runCommandForDirectory = RunCommandForDirectory(get()),
            runCommandForText = RunCommandForText(get()),
            runStandaloneCommand =  RunStandaloneCommand(get()),
            changeCommandDetails = ChangeCommandDetails(get()),
            deleteCommand = DeleteCommand(get()),
            addCommandToHistory = AddCommandToHistory(get()),
            getHistoryOfCommand = GetHistoryOfCommand(get()),
            getLatestUsedPackages = GetLatestUsedPackages(get()),
            getUserTags = GetUserTags(get()),
            addUserTag = AddUserTag(get()),
            deleteUserTag = DeleteUserTag(get())
        )
    }
}