package com.autosec.pie.di

import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.use_case.CreateCommand
import com.autosec.pie.use_case.GetCommandDetails
import com.autosec.pie.use_case.GetCommandsList
import com.autosec.pie.use_case.GetShareCommands
import org.koin.dsl.module

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