package com.autosec.pie.use_case

data class AutoPieUseCases (
    val getCommandsList: GetCommandsList,
    val getShareCommands: GetShareCommands,
    val createCommand: CreateCommand,
    val getCommandDetails: GetCommandDetails,
    val runShareCommand: RunShareCommand,
    val runShareCommandForDirectory: RunShareCommandForDirectory,
    val runShareCommandForUrl : RunShareCommandForUrl,
    val runShareCommandForFiles: RunShareCommandForFiles
)