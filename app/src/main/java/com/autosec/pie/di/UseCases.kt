package com.autosec.pie.di

import com.autosec.pie.use_case.AddCommandToHistory
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.use_case.ChangeCommandDetails
import com.autosec.pie.use_case.CreateCommand
import com.autosec.pie.use_case.DeleteCommand
import com.autosec.pie.use_case.GetCommandDetails
import com.autosec.pie.use_case.GetCommandsList
import com.autosec.pie.use_case.GetHistoryOfCommand
import com.autosec.pie.use_case.GetLatestUsedPackages
import com.autosec.pie.use_case.GetShareCommands
import com.autosec.pie.use_case.RunShareCommand
import com.autosec.pie.use_case.RunShareCommandForDirectory
import com.autosec.pie.use_case.RunShareCommandForFiles
import com.autosec.pie.use_case.RunShareCommandForText
import com.autosec.pie.use_case.RunShareCommandForUrl
import com.autosec.pie.use_case.RunStandaloneCommand
import org.koin.dsl.module

val useCaseModule = module {
    single<AutoPieUseCases> {
        AutoPieUseCases(
            getCommandsList = GetCommandsList(get()),
            getShareCommands = GetShareCommands(get()),
            createCommand = CreateCommand(get()),
            getCommandDetails = GetCommandDetails(get()),
            runShareCommand = RunShareCommand(),
            runShareCommandForUrl = RunShareCommandForUrl(get()),
            runShareCommandForFiles = RunShareCommandForFiles(get()),
            runShareCommandForDirectory = RunShareCommandForDirectory(get()),
            runShareCommandForText = RunShareCommandForText(get()),
            runStandaloneCommand =  RunStandaloneCommand(get()),
            changeCommandDetails = ChangeCommandDetails(get()),
            deleteCommand = DeleteCommand(get()),
            addCommandToHistory = AddCommandToHistory(get()),
            getHistoryOfCommand = GetHistoryOfCommand(get()),
            getLatestUsedPackages = GetLatestUsedPackages(get())
        )
    }
}